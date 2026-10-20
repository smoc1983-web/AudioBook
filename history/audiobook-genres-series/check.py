from pathlib import Path
import re,json
root=Path(__file__).parent
s=(root/'MainActivity.kt').read_text()
checker=Path('/workspace/audiobook-update/check_sources.py').read_text()
ns={'__file__':str(root/'check_sources.py')}
exec(checker[:checker.index('main = (ROOT')],ns)
ns['check_delimiters'](s)
print('Kotlin delimiter/string check passed (not compilation)')
assert (root/'PlaybackService.kt').read_bytes()==Path('/workspace/audiobook-4.0/PlaybackService.kt').read_bytes()
assert '@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)\n@Composable\nprivate fun AbDialog' in s
assert 'MediaPlayer()' not in s
assert '89128580680' in s and 'AB_RELEASE_NAME="4.0"' in s
assert s.endswith((root/'catalog-metadata.txt').read_text())
imports=re.findall(r'^import .+$',s,re.M)
assert len(imports)==len(set(imports))
print('Service, version, support details and existing compile-error opt-in preserved')

# Extract the actual JS bodies and the literal adapter tables from Kotlin.
block=s[s.index('private object AbSourceClassifier'):s.index('private fun abCatalogClassifierJs')]
common={name:json.loads('['+values+']') for name,values in re.findall(r'"([^"]+)" to listOf\(([^\n]+)\)',block[:block.index('fun aliases')])}
adapters={site:dict(common) for site in ['Bookinfo','Книгоблуд']}
for site,name,values in re.findall(r'"([^"]+)" to "([^"]+)" -> listOf\(([^\n]+)\)',block):
    adapters[site][name]=adapters[site][name]+json.loads('['+values+']')
def body(name):
    start=s.index('fun '+name+'(')
    begin=s.index('"""',start)+len('"""')
    end=s.index('""".trimIndent()',begin)
    return s[begin:end].replace("${'$'}",'$').strip()
classifier=body('abCatalogClassifierJs').replace('$adapterJson',json.dumps(adapters,ensure_ascii=False))
catalog=body('bookCatalogJs').replace('$classifierScript',classifier).replace('$safeCatalogUrl','__ROOT__').replace('$seriesContext','__CYCLE__')
genre=body('abGenreLinkJs').replace('$aliases',json.dumps(adapters['Bookinfo']['Фэнтези'],ensure_ascii=False))
out=root/'js-check'
out.mkdir(exist_ok=True)
(out/'classifier.js').write_text(classifier)
(out/'catalog-template.js').write_text(catalog)
(out/'catalog.js').write_text(catalog.replace('__ROOT__','""').replace('__CYCLE__','null'))
(out/'genre-link.js').write_text(genre)
genre_catalog=body('abGenreCatalogJs').replace('$script',catalog.rstrip(';')).replace('$classifier',classifier).replace('$selected','__GENRE__')
(out/'genre-template.js').write_text(genre_catalog)
analyzer=body('pageAnalyzerJs').replace('$catalogScript',catalog.replace('__ROOT__','""').replace('__CYCLE__','null').rstrip(';'))
(out/'analyzer.js').write_text(analyzer)
(out/'next.js').write_text(body('clickNextCatalogPageJs'))
assert not re.search(r'\$[a-zA-Z_]',classifier+catalog+genre_catalog+analyzer)
print('Extracted actual JavaScript classifier, catalogue, category browser and audio analyzer')
