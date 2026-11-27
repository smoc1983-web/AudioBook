from pathlib import Path
import re,json
root=Path(__file__).parent
s=(root/'MainActivity.kt').read_text()
from tests.kotlin_delimiters import check_delimiters
kotlin=root.parent/'app/src/main/java/com/audiobookdownloader'
ui_root=(kotlin/'AudioBookRoot.kt').read_text()
for source in (s,ui_root,(root/'PlaybackService.kt').read_text()):
    check_delimiters(source)
    imports=re.findall(r'^import .+$',source,re.M)
    assert len(imports)==len(set(imports))
assert '@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)\n@Composable\ninternal fun AbDialog' in (kotlin/'AbSharedControls.kt').read_text()
assert all('MediaPlayer()' not in source for source in (s,ui_root))
assert 'AB_PRODUCT_NAME=' in (kotlin/'AbHomeNavigation.kt').read_text()
assert 'internal object AbBookSources' in (kotlin/'AbSourceRegistry.kt').read_text()
print('Kotlin structure checked for activity, root and playback service (not compilation)')

# Extract actual JS bodies and literal adapter tables from Kotlin.
genres=(kotlin/'AbGenreCatalog.kt').read_text()
block=genres[genres.index('object AbSourceClassifier'):genres.index('fun abCatalogClassifierJs')]
common={name:json.loads('['+values+']') for name,values in re.findall(r'"([^"]+)" to listOf\(([^\n]+)\)',block[:block.index('fun aliases')])}
adapters={site:dict(common) for site in ['Bookinfo','Книгоблуд','Азбука веры','Audiokniga-online.ru']}
for site,name,values in re.findall(r'"([^"]+)" to "([^"]+)" -> listOf\(([^\n]+)\)',block):
    adapters[site][name]=adapters[site][name]+json.loads('['+values+']')
script_sources=[s,genres]+[(kotlin/name).read_text() for name in ('AbCatalogScripts.kt','AbAzbykaScripts.kt','AbOnlineSource.kt','AbPublicCatalogSource.kt')]
def body(name):
    source=next(src for src in script_sources if 'fun '+name+'(' in src)
    start=source.index('fun '+name+'(')
    begin=source.index('"""',start)+len('"""')
    end=source.index('""".trimIndent()',begin)
    return source[begin:end].replace("${'$'}",'$').strip()
extra_src=(root.parent/'app/src/main/java/com/audiobookdownloader/AdditionalBookSources.kt').read_text()
config=extra_src.split('AB_EXTRA_SOURCE_JSON = """',1)[1].split('"""',1)[0]
extra=extra_src.split('    return """',1)[1].split('""".trimIndent()',1)[0].strip().replace("${'$'}",'$').replace('$config',config)
normalizer=body('abGenreNormalizerJs')
script_pool=body('abOnlineScriptsJs')
online_site=body('abOnlineSiteJs').replace('$scriptPool',script_pool)
online_player=body('abOnlinePlayerJs').replace('$scriptPool',script_pool)
player_reader=body('abPlayerReaderJs').replace('$onlineReader',online_player).replace('$extraAdapter',extra)
classifier=body('abCatalogClassifierJs').replace('$adapterJson',json.dumps(adapters,ensure_ascii=False)).replace('$genreNormal',normalizer)
az_catalog=body('abAzbykaCatalogJs')
az_page=body('abAzbykaPageJs').replace('__AB_AZ_CATALOG__',az_catalog.rstrip(';'))
catalog=body('bookCatalogJs').replace('$azbykaCatalog',az_catalog).replace('$classifierScript',classifier).replace('$onlineAdapter',online_site).replace('$extraAdapter',extra).replace('$safeCatalogUrl','__ROOT__').replace('$seriesContext','__CYCLE__')
genre_source=body('abGenreLinkJs').replace('$genreNormal',normalizer)
genre=genre_source.replace('$aliases',json.dumps(adapters['Bookinfo']['Фэнтези'],ensure_ascii=False))
out=root/'js-check'
out.mkdir(exist_ok=True)
(out/'classifier.js').write_text(classifier)
(out/'genre-normalizer.js').write_text(normalizer)
(out/'genre-link-template.js').write_text(genre_source.replace('$aliases','__ALIASES__'))
(out/'genre-adapters.json').write_text(json.dumps(adapters,ensure_ascii=False))
(out/'catalog-template.js').write_text(catalog)
(out/'catalog.js').write_text(catalog.replace('__ROOT__','""').replace('__CYCLE__','null'))
(out/'genre-link.js').write_text(genre)
genre_catalog=body('abGenreCatalogJs').replace('$script',catalog.rstrip(';')).replace('$classifier',classifier).replace('$selected','__GENRE__').replace('$nativeCategory','false')
(out/'genre-template.js').write_text(genre_catalog)
(out/'native-genre-template.js').write_text(genre_catalog.replace('nativeCategory=false','nativeCategory=true'))
(out/'native-directory.js').write_text(body('abNativeGenreDirectoryJs'))
analyzer=body('pageAnalyzerJs').replace('$readerScript',player_reader).replace('$onlineAdapter',online_site).replace('$extraAdapter',extra).replace('$azbykaPage',az_page).replace('$catalogScript',catalog.replace('__ROOT__','""').replace('__CYCLE__','null').rstrip(';'))
(out/'analyzer.js').write_text(analyzer)
(out/'player-reader.js').write_text(player_reader)
(out/'next.js').write_text(body('clickNextCatalogPageJs'))
assert not re.search(r'\$[a-zA-Z_]',classifier+catalog+genre_catalog+analyzer)
print('Extracted actual JavaScript classifier, catalogue, category browser and audio analyzer')

(out/'public-classifier.js').write_text(body('abPublicGenreClassifierJs').replace('$adapterJson',json.dumps(adapters,ensure_ascii=False)))
(out/'public-search.js').write_text(body('abPublicSearchJs'))
(out/'external-page.js').write_text(body('abExternalBookPageJs').replace('$analyzer',analyzer.rstrip(';')))
assert 'AbExternalGenreSearch(name,genreQuery,genreExternalBatch' in ui_root
assert all(marker in (kotlin/'AbPublicCatalogSource.kt').read_text() for marker in ('job.cancel()', 'slots.acquire()'))
assert 'genreNeedsMore() && genreExternalLoading' in ui_root
print('Extracted external classifier, public search and book verification scripts')

(out/'azbyka-catalog.js').write_text(az_catalog)
(out/'azbyka-page.js').write_text(az_page)
(out/'azbyka-m3u-template.js').write_text(body('abAzbykaM3uJs'))
assert not re.search(r'\$[a-zA-Z_]',az_catalog+az_page)
print('Extracted Azbyka catalogue, page and M3U parser scripts')

(out/'online-site.js').write_text(online_site)
(out/'online-player.js').write_text(online_player)
(out/'online-search-template.js').write_text(body('abOnlineSearchJs').replace('$siteScript',online_site).replace('$safeQuery','__QUERY__'))
(out/'online-results-template.js').write_text(body('abOnlineSearchResultsJs').replace('$onlineDiagnostics',online_site).replace('$catalog',catalog.replace('__ROOT__','\"\"').replace('__CYCLE__','null').rstrip(';')).replace('$safeQuery','__QUERY__'))
(out/'online-playlist-template.js').write_text(body('abOnlinePlaylistJs').replace('$player',online_player).replace('$extra',extra).replace('$content','__CONTENT__').replace('$address','__BASE__'))
for path in out.glob('*.js'):
 assert not re.search(r'\$[a-zA-Z_]',path.read_text()),path.name
print('Extracted fourth-source route, search-form, player and external-playlist adapters')

(out/'online-scripts.js').write_text(script_pool)
(out/'online-prepare.js').write_text(body('abOnlinePreparePlayerJs').replace('$scripts',script_pool))

(out/'extra-source.js').write_text(extra)
(out/'extra-source-config.json').write_text(config)

(out/'extra-search-template.js').write_text(body('searchSubmitJs').replace('$extraAdapter',extra).replace('$safeQuery','__QUERY__'))
