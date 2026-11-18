package com.audiobookdownloader

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract as Docs
import java.io.File

internal data class LibraryRootsScan(val books:List<LibraryBook>,val issues:Map<String,String>,val unavailable:Boolean)

/** Read-only provider traversal: selected directory and its immediate book subdirectories, as before. */
internal fun scanLibraryRoots(c:Context,snapshot:LibraryRootsState):LibraryRootsScan {
    if(!snapshot.valid)return LibraryRootsScan(emptyList(),emptyMap(),true)
    val books=linkedMapOf<String,LibraryBook>()
    val issues=linkedMapOf<String,String>()
    val origins=LibraryRootOrigins(c)
    for(raw in snapshot.roots) {
        val key=LibraryRootRules.treeKey(raw)
        try {
            val tree=Uri.parse(raw)
            val document=Docs.buildDocumentUriUsingTree(tree,Docs.getTreeDocumentId(tree))
            val name=c.contentResolver.query(document,arrayOf(Docs.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use {
                if(it.moveToFirst())it.getString(0) else null
            } ?: error("Unavailable directory")
            val children=libraryDocuments(c,tree,document)
            fun add(uri:Uri,title:String,items:List<LibraryDocument>) {
                val folder=libraryDocumentFolder(c,tree,uri)
                val files=items.filter{!it.isDirectory&&LibraryRecoveryRules.isAudio(it.name)&&it.size!=0L}
                    .map{LibraryAudioFile(File(folder,it.name).path,it.uri.toString(),it.size)}
                if(files.isNotEmpty())mergeLibraryBook(books,LibraryBook(title,folder,sortedLibraryFiles(files),uri.toString(),
                    items.filter{!it.isDirectory&&it.name.substringAfterLast('.',"").lowercase() in setOf("jpg","jpeg","png","webp")}
                        .sortedWith(compareBy<LibraryDocument>{if(it.name.substringBeforeLast('.').lowercase() in setOf("cover","folder","front","обложка"))0 else 1}.thenBy{it.name.lowercase()})
                        .firstOrNull()?.uri?.toString().orEmpty(),identityOrigin=origins.origin(uri)))
            }
            add(document,name,children)
            children.filter{it.isDirectory}.forEach {child->
                try{add(child.uri,child.name,libraryDocuments(c,tree,child.uri))}
                catch(_:Exception){issues[key]="Часть книг недоступна. Проверьте доступ к папке."}
            }
        }catch(_:Exception){issues[key]="Папка недоступна. Подключите носитель или выберите её снова."}
    }
    val saved=origins.save()
    if(!saved)snapshot.roots.forEach{issues[LibraryRootRules.treeKey(it)]="Не удалось сохранить сведения о папке. Проверьте свободное место."}
    return LibraryRootsScan(books.values.filter{saved||origins.persisted(it.identityOrigin)},issues,!snapshot.saved||issues.isNotEmpty())
}
