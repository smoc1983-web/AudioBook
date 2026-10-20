from pathlib import Path
p=Path(__file__).with_name('MainActivity.kt')
s=p.read_text()
a=s.index('    fun openBookRef(\n')
b=s.index('    fun updateGenreResults()',a)
old=s[a:b]
local_start=old.index('            val savedTrack =')
local_end=old.index('        } else if (ref.url.isNotBlank())')
local_body=old[local_start:local_end]
helper='''    fun openDownloadedBook(libraryBook:LibraryBook) {
        bookOpenId++
        resumeAfterLoad=false
        currentUrl="";loadId++
        unifiedQuery="";unifiedSearchId++
        cycleLoading=false;cycleRequestId++
        genrePending=emptySet();genreExternalLoading=false;genreRequestId++;genreExternalId++
'''+local_body+'''    }

'''
# This helper is before website functions, so both site cards and refs can use it.
insert=s.index('    fun openWebsiteBook(')
s=s[:insert]+helper+s[insert:]
a=s.index('    fun openBookRef(\n')
b=s.index('    fun updateGenreResults()',a)
remote=old[local_end:]
remote=remote.replace('        } else if (ref.url.isNotBlank())','        } else if (ref.url.isNotBlank())',1)
replacement='''    fun openBookRef(ref:BookRef) {
        val id=++bookOpenId
        val libraryBook=findDownloadedBook(libraryBooks,ref.title)
        if(libraryBook==null && librarySync.isSyncing) {
            status="Проверяю скачанные главы…"
            modernWork.launch {
                androidx.compose.runtime.snapshotFlow{librarySync.isSyncing}.first{!it}
                if(id==bookOpenId)openBookRef(ref)
            }
            return
        }
        if(libraryBook!=null) {
            openDownloadedBook(libraryBook)
'''+remote
s=s[:a]+replacement+s[b:]
needle='''    fun openWebsiteBook(book: BookLink, sourceUrl: String = book.url) {
'''
s=s.replace(needle,needle+'''        bookOpenId++
        findDownloadedBook(libraryBooks,book.title)?.let {openDownloadedBook(it);return}
''',1)
s=s.replace('    fun openWebsiteCycle(cycle: BookCycle) {','    fun openWebsiteCycle(cycle: BookCycle) {\n        bookOpenId++',1)
s=s.replace('    fun startCommonBookSearch() {','    fun startCommonBookSearch() {\n        bookOpenId++',1)
s=s.replace('    fun showSearchHome() {','    fun showSearchHome() {\n        bookOpenId++',1)
s=s.replace('4.0-search-window-1','4.0-offline-audio-1')
p.write_text(s)
