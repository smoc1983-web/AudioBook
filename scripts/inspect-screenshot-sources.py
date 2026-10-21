"""Public source inspection: anonymous HTML/search forms, no APK publication or full audio downloads."""
import concurrent.futures, http.cookiejar, urllib.request, urllib.parse, socket, ipaddress, json,re,time
from pathlib import Path
from html.parser import HTMLParser
OUT=Path('screenshot-source-evidence');OUT.mkdir(exist_ok=True)
SOURCES=[('knigavuhe','https://knigavuhe.org/'),('izibuk','https://izib.uk/'),('lis10book','https://lis10book.com/'),('uknig','https://uknig.com/'),('poleknig','https://poleknig.com/'),('audioknigivse','https://audioknigivse.ru/'),('otrub','https://otrub.in/'),('akniga','https://akniga.org/'),('slushkinvsem','https://slushkinvsem.ru/'),('audioboo','https://audioboo.org/'),('audioknigi-pro','https://audioknigi.pro/'),('baza-knig','https://baza-knig.info/'),('baza-rip','https://baza-knig.rip/')]
class Page(HTMLParser):
 def __init__(self):super().__init__();self.links=[];self.forms=[];self.scripts=[];self.form=None;self.anchor=None
 def handle_starttag(self,tag,attrs):
  a=dict(attrs)
  if tag=='a' and a.get('href'):self.anchor={'href':a['href'],'text':''};self.links.append(self.anchor)
  if tag=='script' and a.get('src'):self.scripts.append(a['src'])
  if tag=='form':self.form={'action':a.get('action',''),'method':a.get('method','get'),'fields':[]};self.forms.append(self.form)
  if tag=='input' and self.form is not None:self.form['fields'].append({k:a.get(k,'') for k in ['name','type','value','placeholder']})
 def handle_endtag(self,tag):
  if tag=='form':self.form=None
  if tag=='a':self.anchor=None
 def handle_data(self,d):
  if self.anchor is not None:self.anchor['text']+=d
class PublicRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,req,fp,code,msg,headers,newurl):
  check(newurl);return super().redirect_request(req,fp,code,msg,headers,newurl)
def check(url):
 u=urllib.parse.urlsplit(url)
 if u.scheme not in ['https','http'] or u.username:raise ValueError('Invalid URL')
 ips=socket.getaddrinfo(u.hostname,u.port or (443 if u.scheme=='https' else 80),type=socket.SOCK_STREAM)
 if not ips or any(not ipaddress.ip_address(x[4][0]).is_global for x in ips):raise ValueError('Non-public destination')
def fetch(opener,url,name,data=None):
 start=time.monotonic();r={'url':url};check(url)
 try:
  req=urllib.request.Request(url,data=data,headers={'User-Agent':'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36','Referer':url,'Accept':'text/html,application/json;q=0.9,*/*;q=0.5'})
  with opener.open(req,timeout=18) as f:
   b=f.read(2500000);r.update(status=f.status,final=f.url,type=f.headers.get_content_type(),bytes=len(b));text=b.decode(f.headers.get_content_charset() or 'utf-8',errors='replace')
   (OUT/(name+'.html')).write_text(text);p=Page();p.feed(text);r.update(forms=p.forms,links=p.links[:350],scripts=p.scripts)
 except Exception as e:r['error']=str(e)[:220]
 r['seconds']=round(time.monotonic()-start,2);return r

def inspect(item):
 key,url=item;opener=urllib.request.build_opener(PublicRedirect(),urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
 try:home=fetch(opener,url,key+'-home')
 except Exception as e:return key,{'home':{'url':url,'error':str(e)}}
 result={'home':home};forms=home.get('forms',[])
 for form in forms:
  fields=form['fields'];q=next((x for x in fields if x['name'] in ['q','story','query','search','search_string','s'] and x['type'] not in ['hidden','checkbox']),None)
  if not q:continue
  address=urllib.parse.urljoin(home.get('final',url),form['action'] or home.get('final',url));target=urllib.parse.urlsplit(address)
  if target.hostname!=urllib.parse.urlsplit(home.get('final',url)).hostname:continue
  args={x['name']:x['value'] for x in fields if x['name'] and x['type']=='hidden'};args[q['name']]='Гоголь';payload=urllib.parse.urlencode(args).encode()
  try:result['search']=fetch(opener,address if form['method'].lower()=='post' else address+('&' if '?' in address else '?')+payload.decode(),key+'-search',payload if form['method'].lower()=='post' else None)
  except Exception as e:result['search']={'error':str(e)}
  break
 return key,result
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:results=dict(pool.map(inspect,SOURCES))
(OUT/'report.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
for k,v in results.items():print(k,json.dumps({kind:{x:y for x,y in val.items() if x not in ['links','forms','scripts']} for kind,val in v.items()},ensure_ascii=False))
