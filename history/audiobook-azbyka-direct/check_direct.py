from pathlib import Path
import re
root=Path(__file__).parent
s=(root/'MainActivity.kt').read_text()
old=Path('/workspace/audiobook-anr-fix/MainActivity.kt').read_text()
unified=s[s.index('fun UnifiedBookSearch'):s.index('class BookCycleTask')]
assert 'AbAzbykaSearchTask(web,query)' in unified
assert 'AbPublicSearchTask' not in unified and 'site:azbyka' not in unified
assert 'AbBookSources.searchOrder.firstOrNull' in unified
assert 'Открыть Азбуку веры →' in s
assert 'AbAzbykaPages.load(this,currentUrl,modernWork' in s
assert 'AbAzbykaPages.load(web,home,work' in s
assert 'AbAzbykaPages.load(web,candidate.url,work' in s
assert 'AbAzbykaPages.load(web,address,work' in s
fetch=s[s.index('private object AbAzbykaPages'):s.index('private class AbAzbykaSearchTask')]
assert 'suspend fun text' in fetch and 'withContext(Dispatchers.IO)' in fetch
assert 'withContext(Dispatchers.Default){staticHtml(html)}' in fetch
assert 'scheme=="https"' in fetch and 'instanceFollowRedirects=false' in fetch
assert 'LIMIT=3*1024*1024' in fetch and 'TTL=6*60*60*1000L' in fetch
assert '32L*1024*1024' in fetch and 'ensureActive()' in fetch
assert 'blockNetworkLoads=true' in fetch and 'loadDataWithBaseURL' in fetch
assert not re.search(r'\.proceed\(|TrustAll|setDefaultHostnameVerifier|SSLContext\.getInstance',fetch)
assert 'web.settings.blockNetworkLoads=false' in s[s.index('private class AbPublicSearchTask'):s.index('private object AbPublicMetadata')]
search=s[s.index('private class AbAzbykaSearchTask'):s.index('private object AbBookSources')]
assert 'pending.add(AbBookSources.SITEMAP)' in search
assert 'visited.size>12' in search and '90_000L' in search
assert 'abMatchesBookQuery(it,query)' in search
assert 'job.cancel();super.close()' in search
assert 'REVISION="4.0-azbyka-direct-1"' in s
assert (root/'PlaybackService.kt').read_bytes()==Path('/workspace/audiobook-anr-fix/PlaybackService.kt').read_bytes()
print('PASS: direct Azbyka search/open/genre paths, bounded IO fetch/cache, cancellation and diagnostic revision')

# Keep the previous ANR structural assertions; source scripts intentionally changed.
anr=Path('/workspace/audiobook-anr-fix/check_anr.py').read_text()
exec(anr[:anr.index('# No source parsers changed')],{'__file__':str(root/'check_anr.py')})
old_js=Path('/workspace/audiobook-anr-fix/js-check')
expected={'azbyka-catalog.js','azbyka-page.js','catalog-template.js','catalog.js',
          'genre-template.js','analyzer.js','external-page.js'}
for file in old_js.glob('*.js'):
    if file.name not in expected:assert (root/'js-check'/file.name).read_bytes()==file.read_bytes(),file.name
print('PASS: ANR checks retained; unrelated source scripts and PlaybackService.kt unchanged')
