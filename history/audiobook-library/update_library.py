from pathlib import Path
import re
root=Path('/workspace/audiobook-library')
p=root/'MainActivity.kt'
s=p.read_text()
s=s.replace('val files: List<File>\n)', 'val files: List<File>,\n    val folderUri: String = "",\n    val coverUri: String = ""\n)',1)
start=s.index('class MainActivity : ComponentActivity() {')
end=s.index('\nfun Modifier.appCard(',start)
s=s[:start]+'''class MainActivity : ComponentActivity() {
    private var libraryAccessVersion by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // One request: simultaneous dialogs previously prevented the audio request from completing.
        if (Build.VERSION.SDK_INT >= 23) {
            val permissions = mutableListOf<String>()
            libraryAudioPermission()?.let {
                if (checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    permissions.add(it)
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), 1002)
        }
        setContent { AudioBookApp(libraryAccessVersion) }
    }

    override fun onResume() {
        super.onResume()
        // Also refresh when returning from Android settings or the folder picker.
        libraryAccessVersion++
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        libraryAccessVersion++
    }
}
''' +s[end:]
s=s.replace('fun AudioBookApp() {', 'fun AudioBookApp(libraryAccessVersion: Int = 0) {',1)
old='''    var libraryBooks by remember {
        mutableStateOf(scanLibrary())
    }'''
new='''    val librarySync = rememberLibrarySync(libraryAccessVersion, appPage)
    var libraryBooks by librarySync.books'''
assert old in s
s=s.replace(old,new,1)
s,n=re.subn(r'libraryBooks\s*=\s*scanLibrary\(\)', 'librarySync.requestSync()',s)
assert n==7,n
s=s.replace('files.map { ServiceTrack(it.nameWithoutExtension, it.absolutePath) }',
            'files.map { ServiceTrack(it.nameWithoutExtension, libraryTrackSource(it)) }',1)
s=s.replace('playingFiles = state.tracks.map { File(it.src) }',
            'playingFiles = state.tracks.map { libraryFileForTrack(state.bookTitle, it) }',1)
s=s.replace('''                        retriever.setDataSource(
                            file.absolutePath
                        )''','''                        val source = libraryTrackSource(file)
                        if (source.startsWith("content://")) retriever.setDataSource(context, Uri.parse(source))
                        else retriever.setDataSource(source)''',1)
old='''            Text(
                text =
                    "Найдено книг: ${libraryBooks.size}",'''
pos=s.index(old)
end=s.index('''            Spacer(''',pos)
s=s[:end]+'''            LibrarySyncControls(
                syncing = librarySync.isSyncing,
                message = librarySync.message,
                onSync = { librarySync.requestSync() },
                textColor = gray,
                buttonColor = green
            )

'''+s[end:]
s=s.replace('"Скачанных аудиокниг пока нет."','if (librarySync.isSyncing) "Ищу скачанные книги…" else "Скачанные книги не найдены. Проверьте доступ или выберите папку библиотеки выше."',1)
old='''                            coverFile.exists() &&
                            coverFile.length() > 0L'''
assert old in s
s=s.replace(old,'''                            libraryBook.coverUri.isNotBlank() ||
                            (coverFile.exists() && coverFile.length() > 0L)''',1)
old='''                                    file =
                                        coverFile'''
assert old in s
s=s.replace(old,'''                                    file = coverFile,
                                    uri = libraryBook.coverUri''',1)
s=s.replace('''                            continueCover.exists() &&
                            continueCover.length() > 0L''','''                            continueBook.coverUri.isNotBlank() ||
                            (continueCover.exists() && continueCover.length() > 0L)''',1)
s=s.replace('''                                    file =
                                        continueCover''','''                                    file = continueCover,
                                    uri = continueBook.coverUri''',1)
s=s.replace('''                    bookTitle =
                        playerBookTitle
                )''','''                    bookTitle = playerBookTitle,
                    localCoverUri = activePlayerBook?.coverUri.orEmpty()
                )''',1)
s=s.replace('''                                            deleteLibraryBook(
                                                libraryBook
                                            )''','''                                            deleteLibraryBook(
                                                libraryBook, context
                                            )''',1)
start=s.index('@Suppress("DEPRECATION")\nfun scanLibrary():')
end=s.index('\nfun trackSortNumber(',start)
s=s[:start]+(root/'library-helpers.txt').read_text()+s[end:]
s=s.replace('''fun deleteLibraryBook(
    book: LibraryBook
): Boolean {''','''fun deleteLibraryBook(
    book: LibraryBook,
    context: Context? = null
): Boolean {''',1)
pos=s.index('fun deleteLibraryBook(')
start=s.index('''    return try {''',pos)
s=s[:start]+s[start:].replace('''    return try {

        if (''','''    return try {
        if (book.folderUri.isNotBlank()) {
            return context != null && android.provider.DocumentsContract.deleteDocument(
                context.contentResolver, Uri.parse(book.folderUri)
            )
        }
        // A hidden scoped-storage folder is not proof that its audio has been deleted.
        if (!book.folder.exists() && book.files.any { it is LibraryAudioFile }) return false

        if (''',1)
# SAF cover in the player.
s=s.replace('''    bookTitle: String = ""
) {

    var bitmap by remember(''','''    bookTitle: String = "",
    localCoverUri: String = ""
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var bitmap by remember(''',1)
start=s.index('fun FullPlayerCover(')
end=s.index('\nfun ',start+10)
part=s[start:end]
part=part.replace('''        localFile?.absolutePath,''','''        localCoverUri,
        localFile?.absolutePath,''')
part=part.replace('''        bitmap =
            if (''','''        bitmap =
            if (localCoverUri.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    try { context.contentResolver.openInputStream(Uri.parse(localCoverUri))?.use {
                        BitmapFactory.decodeStream(it)
                    } } catch (_: Exception) { null }
                }
            } else if (''',1)
s=s[:start]+part+s[end:]
# Existing library cover loading was on the UI thread. Read SAF/regular files in IO.
start=s.index('@Composable\nfun BookCover(')
end=s.index('\n/*',start)
s=s[:start]+'''@Composable
fun BookCover(file: File, uri: String = "") {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(file.absolutePath, uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file.absolutePath, uri, file.lastModified()) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                if (uri.isNotBlank()) context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it)
                } else BitmapFactory.decodeFile(file.absolutePath)
            } catch (_: Exception) { null }
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(330.dp),
        factory = { ImageView(it).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        } },
        update = { it.setImageBitmap(bitmap) }
    )
}
''' +s[end:]
p.write_text(s)
p=root/'PlaybackService.kt'
s=p.read_text().replace('val books = scanLibrary()', 'val books = scanLibrary(this)',1)
s=s.replace('nextBook.files.map { ServiceTrack(it.nameWithoutExtension, it.absolutePath) }',
            'nextBook.files.map { ServiceTrack(it.nameWithoutExtension, libraryTrackSource(it)) }',1)
p.write_text(s)
