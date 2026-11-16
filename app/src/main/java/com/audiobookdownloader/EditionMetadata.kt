package com.audiobookdownloader

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Additive migration. Legacy title fields are read with proof and never overwritten here. */
internal object EditionMetadata {
    const val PREFIX="book_identity_v3_"
    const val NOTICE="library_identity_notice"
    data class Record(val edition:String,val bookId:String=LibraryRecordRules.bookId(edition),
        val total:Int=0,val titles:List<String> = emptyList(),val revision:String="",
        val sourceUrl:String="",val completed:Boolean=false,val finished:List<String> = emptyList(),val valid:Boolean=true)
    fun current(p:SharedPreferences,book:String)=ChapterIdentity.edition(book,p.getString(bookUrlKey(book),"").orEmpty())
    fun local(p:SharedPreferences,book:LibraryBook)=if(book.identityAmbiguous)ChapterIdentity.edition("","library:"+book.folderUri.ifBlank{book.folder.absolutePath}) else book.editionId.ifBlank {
        ChapterIdentity.edition(book.title,p.getString("book_local_edition_"+safeFileName(book.title),"").orEmpty())
    }
    private fun strings(rows:JSONArray):List<String> = (0 until rows.length()).map {i->
        require(rows.get(i) is String);rows.getString(i)
    }
    private data class Cached(val owner:SharedPreferences,val fingerprint:List<Any?>,val value:Record,val cost:Int)
    private val cache=java.util.LinkedHashMap<String,Cached>(16,.75f,true)
    private var cachedChars=0
    private const val MAX_CACHE_CHARS=2*1024*1024
    @Synchronized private fun cached(p:SharedPreferences,book:String,id:String,fingerprint:List<Any?>,build:()->Record):Record {
        val key=id+"\u0000"+book
        cache[key]?.takeIf{it.owner===p&&it.fingerprint==fingerprint}?.let{return it.value}
        val result=try{build()}catch(_:Exception){Record(id,valid=false)}
        cache.remove(key)?.let{cachedChars-=it.cost}
        val cost=fingerprint.sumOf{if(it is String)it.length else 8}
        if(cost<=MAX_CACHE_CHARS) {
            cache[key]=Cached(p,fingerprint,result,cost);cachedChars+=cost
            while(cache.size>64||cachedChars>MAX_CACHE_CHARS) {
                val first=cache.entries.iterator();val old=first.next();cachedChars-=old.value.cost;first.remove()
            }
        }
        return result
    }
    fun read(p:SharedPreferences,book:String,id:String):Record = try {
        val raw=p.getString(PREFIX+id,null)
        if(raw!=null)cached(p,book,id,listOf(raw)){decode(raw,book,id)}
        else {
            val fields=legacyFields(p,book)
            cached(p,book,id,fields){legacy(book,id,fields)}
        }
    }catch(_:Exception){Record(id,valid=false)}
    private fun legacyFields(p:SharedPreferences,book:String):List<Any?> {
        val suffix=safeFileName(book)
        return listOf(p.getString("book_chapter_titles_"+suffix,"[]").orEmpty(),
            p.getString("book_edition_revision_"+suffix,"").orEmpty(),p.getInt(bookTotalKey(book),0),
            p.getBoolean("book_completed_"+suffix,false),p.getString("book_completed_chapters_"+suffix,"[]").orEmpty(),
            p.getString(bookUrlKey(book),"").orEmpty(),p.getString("book_local_edition_"+suffix,"").orEmpty())
    }
    // Cold migration only: never scan the preference map on every progress render.
    private fun legacyAlias(p:SharedPreferences,id:String,proofRevision:String?):Record? {
        val records=p.all.keys.filter{it.startsWith("book_edition_revision_")}.mapNotNull {key->
            if(proofRevision!=null && p.getString(key,null)!=proofRevision)return@mapNotNull null
            val alias=key.removePrefix("book_edition_revision_")
            if(proofRevision==null) {
                val sources=listOf(p.getString(bookUrlKey(alias),"").orEmpty(),p.getString("book_local_edition_"+alias,"").orEmpty())
                if(sources.none{it.isNotBlank()&&ChapterIdentity.edition(alias,it)==id})return@mapNotNull null
            }
            val value=try{legacy(alias,id,legacyFields(p,alias))}catch(_:Exception){return@mapNotNull null}
            value.takeIf{it.titles.isNotEmpty()&&LibraryRecordRules.provesCatalogue(id,it.titles,it.revision)}
        }
        if(records.isEmpty())return null
        val forms=records.map{it.copy(sourceUrl="")}.distinct()
        return if(forms.size==1)records.first() else Record(id,valid=false)
    }
    private fun decode(raw:String,book:String,id:String):Record {
        require(raw.length<=4*1024*1024)
        val o=JSONObject(raw);val bookId=LibraryRecordRules.bookId(id)
        require(o.getInt("schema")==1 && o.getString("edition")==id && o.getString("bookId")==bookId)
        val titles=strings(o.getJSONArray("titles"));val revision=o.getString("revision")
        val total=o.getInt("total");require(total>=titles.size)
        require(revision.isEmpty()&&titles.isEmpty() || LibraryRecordRules.provesCatalogue(id,titles,revision))
        val source=o.optString("sourceUrl");require(source.isBlank()||ChapterIdentity.edition(book,source)==id)
        return Record(id,bookId=bookId,total=total,titles=titles,revision=revision,sourceUrl=source,
            completed=o.getBoolean("completed"),finished=strings(o.getJSONArray("finished")))
    }
    private fun legacy(book:String,id:String,fields:List<Any?>):Record {
        val titles=try{strings(JSONArray(fields[0] as String))}catch(_:Exception){emptyList()}
        val revision=fields[1] as String;val total=fields[2] as Int;val completed=fields[3] as Boolean
        val proven=LibraryRecordRules.provesCatalogue(id,titles,revision)
        val page=fields[5] as String;val local=fields[6] as String
        val unknownLocal=id==ChapterIdentity.edition(book,"")&&page.isBlank()
        if(!proven&&!unknownLocal)return Record(id)
        val finished=try{strings(JSONArray(fields[4] as String))}catch(_:Exception){emptyList()}
        val source=listOf(page,local).firstOrNull{it.isNotBlank()&&ChapterIdentity.edition(book,it)==id}.orEmpty()
        return Record(id,sourceUrl=source,total=maxOf(total,if(proven)titles.size else 0),
            titles=if(proven)titles else emptyList(),revision=if(proven)revision else "",completed=completed,finished=finished)
    }
    @Synchronized fun change(p:SharedPreferences,book:String,id:String,proofRevision:String?=null,update:(Record)->Record):Boolean {
        val current=read(p,book,id)
        val old=if(current.valid&&current.titles.isEmpty()&&p.getString(PREFIX+id,null)==null)legacyAlias(p,id,proofRevision) ?: current else current
        if(!old.valid) {
            p.edit().putString(NOTICE,"Метаданные издания повреждены или неоднозначны. Исходные записи сохранены; восстановите её из резервной копии. Файлы и позиция не удалены.").apply()
            return false
        }
        val next=update(old);require(next.edition==id&&next.bookId==old.bookId)
        val raw=JSONObject().put("schema",1).put("edition",id).put("bookId",next.bookId).put("title",book)
            .put("total",next.total).put("titles",JSONArray(next.titles)).put("revision",next.revision)
            .put("sourceUrl",next.sourceUrl).put("completed",next.completed).put("finished",JSONArray(next.finished)).toString()
        if(raw==p.getString(PREFIX+id,null))return true
        val ok=p.edit().putString(PREFIX+id,raw).commit()
        if(!ok)p.edit().putString(NOTICE,"Не удалось сохранить метаданные издания. Проверьте свободное место; прежние поля и файлы сохранены.").apply()
        return ok
    }
}
