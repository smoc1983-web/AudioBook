from pathlib import Path
p=Path(__file__).with_name('PlaybackService.kt')
s=p.read_text()
a=s.index('    private fun startTrack(')
b=s.index('    @Suppress("DEPRECATION")\n    private fun requestFocus()',a)
block=s[a:b]
cut=block.index('        try {\n            val player = MediaPlayer()')
setup=block[:cut]
player=block[cut:]
bridge='''        if(isNetworkAudio(item.src)) {
            // Restored/widget playlists may still have their original URLs.
            // Resolve existing downloads on IO before preparing any network audio.
            persistState()
            publish()
            queueWorker.execute {
                val books=try {scanLibrary(this)}catch(_:Exception){emptyList<LibraryBook>()}
                val resolved=preferDownloadedTracks(bookTitle,playlist,books)
                handler.post {
                    if(stopped||token!=generation)return@post
                    if(resolved!=playlist) {
                        playlist=resolved
                        persistPlaylist()
                    }
                    prepareTrack(playlist.getOrNull(index) ?: return@post,token)
                }
            }
        } else prepareTrack(item,token)
    }

    private fun prepareTrack(item:ServiceTrack,token:Long) {
'''
# Capture request inputs before leaving Main; a stale scan never reads a later request.
bridge=bridge.replace('            queueWorker.execute {','            val requestedTitle=bookTitle\n            val requestedTracks=playlist\n            queueWorker.execute {',1)
bridge=bridge.replace('preferDownloadedTracks(bookTitle,playlist,books)','preferDownloadedTracks(requestedTitle,requestedTracks,books)',1)
s=s[:a]+setup+bridge+player+s[b:]
p.write_text(s)
