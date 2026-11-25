package com.audiobookdownloader

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * Rebuildable local search metadata only. One record per configured source/book URL;
 * work/narration grouping remains the responsibility of AbSearchPresentation.
 * No audio files, signed media URLs, library progress or personal collections.
 * All callers must use an IO dispatcher.
 */
internal class AbCatalogIndex(context:Context, name:String=DB_NAME):
    SQLiteOpenHelper(context.applicationContext,name,null,1) {

    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE editions (
            id INTEGER PRIMARY KEY, address TEXT NOT NULL UNIQUE, site TEXT NOT NULL,
            title_key TEXT NOT NULL, observed INTEGER NOT NULL, payload TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX editions_site_title ON editions(site,title_key)")
        db.execSQL("CREATE INDEX editions_observed ON editions(observed)")
        db.execSQL("CREATE VIRTUAL TABLE edition_terms USING fts4(title,author,narrator,series,genres,tokenize=unicode61)")
        db.execSQL("CREATE TABLE catalog_meta (name TEXT PRIMARY KEY,value TEXT NOT NULL)")
    }

    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
        error("Unsupported catalog migration: $oldVersion -> $newVersion")
    }

    private val policy by lazy {ContinuationPolicy(AB_EXTRA_SOURCE_JSON)}
    private fun norm(s:String)=SearchBookIndex.normal(s).take(2048)
    private fun titleKey(s:String)=SearchBookIndex.titleKey(cleanBookTitle(s)).take(2048)

    @Synchronized fun record(books:List<BookLink>,observed:Long=System.currentTimeMillis(),
        now:Long=System.currentTimeMillis(),checkActive:()->Unit={}) {
        if(books.isEmpty()||now<0||observed<0||observed>now||now-observed>MAX_AGE)return
        val db=writableDatabase
        db.beginTransaction()
        try {
            for(book in books.take(600)) {
                checkActive()
                if(book.title.isBlank()||book.title.length>512)continue
                for(source in bookSources(book).take(24)) {
                    checkActive()
                    if(source.url.length>2048)continue
                    val address=policy.canonical(source.url)
                    if(address.isBlank()||!policy.book(source.url))continue
                    val site=bookSiteName(source.url)
                    if(site.isBlank()||site=="Открыть")continue
                    // Store the single observed audio-edition page, not unrelated variants or a search query.
                    val edition=book.copy(url=source.url,coverUrl=source.coverUrl.ifBlank{book.coverUrl},
                        sources=listOf(source.copy(site=site)),searchQuery="",catalogState="")
                    val payload=AbSearchCache.encode(edition).toString()
                    if(payload.toByteArray(Charsets.UTF_8).size>MAX_PAYLOAD)continue
                    val previous=db.rawQuery("SELECT id,observed FROM editions WHERE address=?",arrayOf(address)).use {
                        if(it.moveToFirst())it.getLong(0) to it.getLong(1) else null
                    }
                    if(previous!=null&&previous.second>observed)continue
                    val values=ContentValues().apply{
                        put("address",address);put("site",site);put("title_key",titleKey(edition.title))
                        put("observed",observed);put("payload",payload)
                    }
                    val id=if(previous==null)db.insertOrThrow("editions",null,values) else {
                        db.update("editions",values,"id=?",arrayOf(previous.first.toString()))
                        db.delete("edition_terms","docid=?",arrayOf(previous.first.toString()))
                        previous.first
                    }
                    db.insertOrThrow("edition_terms",null,ContentValues().apply{
                        put("docid",id);put("title",titleKey(edition.title));put("author",norm(edition.author))
                        put("narrator",norm(SearchEditionIdentity.narrator(edition.title,edition.narrator)))
                        put("series",norm(edition.cycles.joinToString(" "){it.title}))
                        put("genres",norm(edition.genres.joinToString(" ")))
                    })
                }
            }
            // The index is disposable and bounded independently of the user's saved library.
            val stale="SELECT id FROM editions WHERE observed<? OR id IN "+
                "(SELECT id FROM editions ORDER BY observed DESC,id DESC LIMIT -1 OFFSET $MAX_ROWS)"
            val args=arrayOf((now-MAX_AGE).toString())
            // Select the same IDs before changing either table.
            db.execSQL("DELETE FROM edition_terms WHERE docid IN ($stale)",args)
            db.execSQL("DELETE FROM editions WHERE id IN ($stale)",args)
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }

    /** Metadata matches only: availability must be freshly checked before playback. */
    @Synchronized fun find(query:String,sites:Set<String>,limit:Int=100,
        now:Long=System.currentTimeMillis()):List<BookLink> {
        val tokens=SearchBookIndex.titleKey(query.take(180)).split(' ').filter{it.isNotBlank()}.distinct().take(16)
        val allowed=sites.filter{it.isNotBlank()}.take(64)
        if(tokens.isEmpty()||allowed.isEmpty()||now<0)return emptyList()
        // Normalization leaves only letters/numbers. FTS operators cannot be supplied by a query.
        val numeric=tokens.any{word->word.any{it.isDigit()}}
        val match=tokens.joinToString(" AND "){if(!numeric&&it.length>=3)"$it*" else it}
        val slots=allowed.joinToString(","){"?"}
        val args=listOf(match,(now-MAX_AGE).toString(),now.toString())+allowed+
            listOf(titleKey(query),limit.coerceIn(1,600).toString())
        return readableDatabase.rawQuery(
            """SELECT e.payload FROM edition_terms JOIN editions e ON e.id=edition_terms.docid
                WHERE edition_terms MATCH ? AND e.observed>=? AND e.observed<=? AND e.site IN ($slots)
                ORDER BY CASE WHEN e.title_key=? THEN 0 ELSE 1 END,e.observed DESC,e.id ASC LIMIT ?""",
            args.toTypedArray()).use {cursor->
            buildList {while(cursor.moveToNext()){
                val book=try{AbSearchCache.decode(JSONObject(cursor.getString(0)))}catch(_:Exception){null}
                if(book!=null)add(book)
            }}
        }
    }

    @Synchronized fun imported():Boolean=readableDatabase.rawQuery(
        "SELECT value FROM catalog_meta WHERE name='json_v2_import'",null).use{it.moveToFirst()}

    @Synchronized fun markImported() {
        writableDatabase.insertWithOnConflict("catalog_meta",null,ContentValues().apply{
            put("name","json_v2_import");put("value","1")
        },SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        const val DB_NAME="search_catalog_v1.db"
        const val MAX_AGE=90L*86_400_000L
        private const val MAX_ROWS=20000
        private const val MAX_PAYLOAD=64*1024
        private var instance:AbCatalogIndex?=null
        @Synchronized fun get(context:Context):AbCatalogIndex =
            instance ?: AbCatalogIndex(context).also{instance=it}
    }
}
