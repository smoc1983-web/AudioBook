from pathlib import Path
r=Path('/workspace/audiobook-black-update');p=r/'MainActivity.kt';s=p.read_text()
def sub(a,b):
 global s
 assert s.count(a)==1,(a[:100],s.count(a))
 s=s.replace(a,b)
s=s.replace('4.0-glass-author-search-telegram-1','4.0-black-library-genres-1')
sub('skin.coerceIn(1,5)','skin.coerceIn(1,9)')
sub('takeIf {it in 1..5}','takeIf {it in 1..9}')
sub('Color(0xFF00745B),Color.White,Color(0xFFAAEEDB),R.drawable.ab_glass_emerald)', '''Color(0xFF00745B),Color.White,Color(0xFFAAEEDB),R.drawable.ab_glass_emerald),
    AbTheme(6,"Чёрный обсидиан",false,
        Color(0xFF030405),Color(0xF00C1014),Color(0xF013181D),Color(0xFFD4E2ED),
        Color(0xFFF7F9FC),Color(0xFFBCC4CD),Color(0xFF59636C),
        Color(0xFF353F49),Color.White,Color(0xFF9CADBC),R.drawable.ab_glass_obsidian),
    AbTheme(7,"Чёрный оникс",false,
        Color(0xFF050403),Color(0xF012100D),Color(0xF01B1712),Color(0xFFFFD49B),
        Color(0xFFFFFAF2),Color(0xFFD0C3B1),Color(0xFF695640),
        Color(0xFF624017),Color.White,Color(0xFFD5AA70),R.drawable.ab_glass_onyx),
    AbTheme(8,"Чёрный лёд",false,
        Color(0xFF020406),Color(0xF0091016),Color(0xF0101B23),Color(0xFF92E4FF),
        Color(0xFFF3FAFF),Color(0xFFB8CBD8),Color(0xFF466271),
        Color(0xFF17435A),Color.White,Color(0xFF77BCD7),R.drawable.ab_glass_ice),
    AbTheme(9,"Чёрный аметист",false,
        Color(0xFF050307),Color(0xF0100C17),Color(0xF01A1223),Color(0xFFD9B5FF),
        Color(0xFFFDF7FF),Color(0xFFCABBD9),Color(0xFF614A75),
        Color(0xFF472460),Color.White,Color(0xFFB591D9),R.drawable.ab_glass_amethyst)''')
# Retain the brighter rendering of the original five palettes.
sub('    val shape=RoundedCornerShape(radius.dp)\n    return this.shadow', '    val shape=RoundedCornerShape(radius.dp)\n    val black=theme.id>=6\n    return this.shadow')
s=s.replace('alpha=if(selected || primary).18f else .10f','alpha=if(black) {if(selected || primary).10f else .025f} else if(selected || primary).18f else .10f')
s=s.replace('alpha=if(selected || primary).30f else .18f','alpha=if(black) {if(selected || primary).22f else .06f} else if(selected || primary).30f else .18f')
s=s.replace('alpha=if(primary).30f else if(theme.light).60f else .25f','alpha=if(black).12f else if(primary).30f else if(theme.light).60f else .25f')
s=s.replace('alpha=if(primary).28f else .24f','alpha=if(black).08f else if(primary).28f else .24f')
s=s.replace('Color.White.copy(alpha=.96f),theme.accent.copy(alpha=if(selected).95f else .60f)', 'Color.White.copy(alpha=if(black).50f else .96f),theme.accent.copy(alpha=if(selected).95f else if(black).30f else .60f)')
s=s.replace('theme.reflection.copy(alpha=.96f)))','theme.reflection.copy(alpha=if(black).42f else .96f)))')
s=s.replace('alpha=if(primary || selected).72f else .48f','alpha=if(black) {if(primary || selected).22f else .08f} else if(primary || selected).72f else .48f')
s=s.replace('alpha=if(theme.light).60f else .20f','alpha=if(theme.light).60f else if(black).08f else .20f')
# Chapters: one centered control, no duplicate tab/toggle below it.
sub('''    fun toggleChapters() {tab=0;chaptersExpanded=!chaptersExpanded}''', '''    fun toggleChapters() {if(tab!=0){tab=0;chaptersExpanded=true}else chaptersExpanded=!chaptersExpanded}''')
needle='''                    Column(Modifier.weight(1f).heightIn(min=58.dp).clip(RoundedCornerShape(13.dp))'''
start=s.index(needle,s.index('private fun AbPlayerScreen(')) if 'private fun AbPlayerScreen(' in s else s.index(needle,s.index('fun toggleChapters()'))
s=s[:start]+'''                    AbPlayerAction(AbIcon.BOOKS,"Главы",theme,tab==0 && chaptersExpanded,Modifier.weight(1f)) {
                        toggleChapters()
                    }
'''+s[start:]
a=s.index('                AbChips(listOf("Главы","Закладки · ${marks.size}"')
b=s.index('\n            when(tab)',a)
s=s[:a]+'''                AbChips(listOf("Закладки · ${marks.size}","Очередь · ${queue.size}","Серия"),tab-1,theme){tab=it+1}
            }'''+s[b:]
# Generalize existing explicit native category routing; preserve pinned source.
sub('private data class AbNativeGenre(val title:String,val url:String,val count:Int?=null)', 'private data class AbNativeGenre(val title:String,val url:String,val count:Int?=null,val site:String="Bookinfo")')
sub('''            genrePages=mapOf("Bookinfo" to AbGenrePage(native.url,native.url,nativeCategory=true))
            genrePending=setOf("Bookinfo");genreExternalLoading=false
            status="Читаем раздел «$name» · Bookinfo…"''','''            genrePages=mapOf(native.site to AbGenrePage(native.url,native.url,nativeCategory=true))
            genrePending=setOf(native.site);genreExternalLoading=false
            status="Читаем раздел «$name» · ${native.site}…"''')
# Unknown download IDs must not hide readable files left by earlier app versions.
sub('    val successful=mutableSetOf<Long>()','    val unfinished=mutableSetOf<Long>()')
sub('''                while(cursor.moveToNext())if(cursor.getInt(statusColumn)==DownloadManager.STATUS_SUCCESSFUL)
                    successful.add(cursor.getLong(idColumn))''','''                while(cursor.moveToNext())if(LibraryRecoveryRules.unfinished(cursor.getInt(statusColumn)))
                    unfinished.add(cursor.getLong(idColumn))''')
sub('''    // A non-empty MP3 can still be a running, paused or failed partial download.
    return jobs.filterKeys{it !in successful}.values.toSet()''','''    // Only a returned unfinished status excludes a file. Missing/expired jobs do not.
    return jobs.filterKeys{it in unfinished}.values.toSet()''')
# All supported audio containers in filesystem, MediaStore and linked folders.
s=s.replace('it.extension.equals("mp3", true) && it.length()', 'LibraryRecoveryRules.isAudio(it.name) && it.length()')
s=s.replace('!file.extension.equals("mp3", true)', '!LibraryRecoveryRules.isAudio(file.name)')
s=s.replace('it.name.endsWith(".mp3", true) && it.size', 'LibraryRecoveryRules.isAudio(it.name) && it.size')
s=s.replace('Проверьте, что MP3 находятся в папках книг внутри AudioBooks.','Проверьте папку Download/AudioBooks или выберите папку со старыми книгами.')
# Preserve persisted tree grants if the saved preference is absent, without changing grants.
sub('''    val treeText = context.getSharedPreferences("audiobook_downloader", Context.MODE_PRIVATE)
        .getString(LIBRARY_TREE_KEY, "").orEmpty()''','''    val treePrefs=context.getSharedPreferences("audiobook_downloader", Context.MODE_PRIVATE)
    val savedTree=treePrefs.getString(LIBRARY_TREE_KEY, "").orEmpty()
    val restoredTree=if(savedTree.isBlank()) try {
        context.contentResolver.persistedUriPermissions.filter{it.isReadPermission}.map{it.uri}.singleOrNull {uri ->
            val id=android.provider.DocumentsContract.getTreeDocumentId(uri)
            id.substringAfterLast('/').equals("AudioBooks",true)
        }?.toString().orEmpty()
    }catch(_:Exception){""} else ""
    val treeText=savedTree.ifBlank{restoredTree}
    if(savedTree.isBlank() && restoredTree.isNotBlank())treePrefs.edit().putString(LIBRARY_TREE_KEY,restoredTree).apply()''')
p.write_text(s)
# Match all widget theme selectors to the nine stable app IDs.
p=r/'app/src/main/java/com/AbGlassWidgets.kt';s=p.read_text()
needle='''    AbWidgetPalette(5,"Изумруд",0xEE0B3E35.toInt(),0xFF00745B.toInt(),0xFF7AE5C1.toInt(),0xFFAAEEDB.toInt(),0xFFF2FFF9.toInt(),0xFFC8E3D6.toInt())'''
assert s.count(needle)==1
s=s.replace(needle,needle+''',
    AbWidgetPalette(6,"Чёрный обсидиан",0xF00C1014.toInt(),0xFF353F49.toInt(),0xFFD4E2ED.toInt(),0xFF9CADBC.toInt(),0xFFF7F9FC.toInt(),0xFFBCC4CD.toInt()),
    AbWidgetPalette(7,"Чёрный оникс",0xF012100D.toInt(),0xFF624017.toInt(),0xFFFFD49B.toInt(),0xFFD5AA70.toInt(),0xFFFFFAF2.toInt(),0xFFD0C3B1.toInt()),
    AbWidgetPalette(8,"Чёрный лёд",0xF0091016.toInt(),0xFF17435A.toInt(),0xFF92E4FF.toInt(),0xFF77BCD7.toInt(),0xFFF3FAFF.toInt(),0xFFB8CBD8.toInt()),
    AbWidgetPalette(9,"Чёрный аметист",0xF0100C17.toInt(),0xFF472460.toInt(),0xFFD9B5FF.toInt(),0xFFB591D9.toInt(),0xFFFDF7FF.toInt(),0xFFCABBD9.toInt())''')
s=s.replace('.coerceIn(0,5)','.coerceIn(0,9)')
p.write_text(s)
