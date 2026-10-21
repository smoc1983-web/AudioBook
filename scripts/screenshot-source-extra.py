from pathlib import Path
s=Path('scripts/inspect-screenshot-sources.py').read_text()
s=s[:s.index('with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:results=')]
targets=[('akniga-bundle','https://akniga.org/application/tmp/templates/cache/ak/20ddd5fd793b0d13cd4ebb5a40177449.js?v=610'),('knigavuhe-open','https://m.knigavuhe.org/book/solomennye-kukly/'),('baza-playlist','https://9giiu0g54k8c.redirectto.cc/s01/1/2/4/5/8/9/124589.pl.txt'),('pro-playlist','https://audioknigi.pro/engine/ajax/controller.php?mod=audio_secure_playlist&id=62903'),('pro-codec','https://audioknigi.pro/templates/default/javascript/ak-playlist-codec.js?v=1')]
exec(s)
r=[]
for name,url in targets:
 try:
  o=urllib.request.build_opener(PublicRedirect(),urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));v=fetch(o,url,name);r.append(v);print(name,v.get('status'),v.get('error'))
 except Exception as e:print(name,str(e))
(OUT/'extra-report.json').write_text(json.dumps(r,ensure_ascii=False,indent=2))
