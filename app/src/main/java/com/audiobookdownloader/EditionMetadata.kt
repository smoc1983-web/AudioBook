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
    fun local(p:SharedPreferences,book:LibraryBook)=book.editionId.ifBlank {
        ChapterIdentity.edition(book.title,p.getString("book_local_edition_"+safeFileName(book.title),"").orEmpty())
    }
    private fun strings(rows:JSONArray):List<String> = (0 until rows.length()).map {i->
        require(rows.get(i) is String);rows.getString(i)
    }
    fun read(p:SharedPreferences,book:String,id:String):Record = try {
        val raw=p.getString(PREFIX+id,null)
        if(raw!=null) {
            require(raw.length<=4*1024*1024)
            val o=JSONObject(raw)
            require(o.getInt("schema")==1 && o.getString("edition")==id && o.getString("bookId")==LibraryRecordRules.bookId(id))
            val titles=strings(o.getJSONArray("titles"));val revision=o.getString("revision")
            val total=o.getInt("total");require(total>=titles.size)
            require(revision.isEmpty()&&titles.isEmpty() || LibraryRecordRules.provesCatalogue(id,titles,revision))
            val source=o.optString("sourceUrl")
            require(source.isBlank()||ChapterIdentity.edition(book,source)==id)
            Record(id,total=total,titles=titles,revision=revision,sourceUrl=source,completed=o.getBoolean("completed"),finished=strings(o.getJSONArray("finished")))
        } else legacy(p,book,id)
    }catch(_:Exception){Record(id,valid=false)}
    private fun legacy(p:SharedPreferences,book:String,id:String):Record {
        val suffix=safeFileName(book)
        val raw=p.getString("book_chapter_titles_"+suffix,"[]").orEmpty()
        val titles=try{strings(JSONArray(raw))}catch(_:Exception){emptyList()}
        val revision=p.getString("book_edition_revision_"+suffix,"").orEmpty()
        val proven=LibraryRecordRules.provesCatalogue(id,titles,revision)
        val unknownLocal=id==ChapterIdentity.edition(book,"")&&p.getString(bookUrlKey(book),"").isNullOrBlank()
        if(!proven&&!unknownLocal)return Record(id)
        val finished=try{strings(JSONArray(p.getString("book_completed_chapters_"+suffix,"[]")))}catch(_:Exception){emptyList()}
        val source=listOf(p.getString(bookUrlKey(book),"").orEmpty(),p.getString("book_local_edition_"+suffix,"").orEmpty()).firstOrNull{it.isNotBlank()&&ChapterIdentity.edition(book,it)==id}.orEmpty()
        return Record(id,sourceUrl=source,total=maxOf(p.getInt(bookTotalKey(book),0),if(proven)titles.size else 0),
            titles=if(proven)titles else emptyList(),revision=if(proven)revision else "",
            completed=p.getBoolean("book_completed_"+suffix,false),finished=finished)
    }
    @Synchronized fun change(p:SharedPreferences,book:String,id:String,update:(Record)->Record):Boolean {
        val old=read(p,book,id)
        if(!old.valid) {
            p.edit().putString(NOTICE,"Метаданные издания повреждены. Исходная запись сохранена; восстановите её из резервной копии. Файлы и позиция не удалены.").apply()
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
