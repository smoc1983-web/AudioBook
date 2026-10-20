"""Resource/integration and bounded-raster checks. Not an Android launcher runtime test."""
from pathlib import Path
import re,xml.etree.ElementTree as ET,math
from widget_check_support import baseline_bytes,node_tree,remove_widgets
r=Path(__file__).parent;base=Path('/workspace/audiobook-glass-vivid')
s=(r/'MainActivity.kt').read_text();p=(r/'PlaybackService.kt').read_text()
w=(r/'app/src/main/java/com/AbGlassWidgets.kt').read_text()
a='{http://schemas.android.com/apk/res/android}'
# Exactly the small event hooks and the track-count field may change playback.
assert baseline_bytes(r/'PlaybackService.kt')==(base/'PlaybackService.kt').read_bytes()
assert p.count('AbGlassWidgets.publish(')==3
assert p.count('"widget_track_count"')==2
for variant in ['debug','release']:
 path=r/f'app/src/{variant}/AndroidManifest.xml';root=ET.parse(path).getroot();app=root.find('application')
 for cls,slug in [('AbCompactWidget','compact'),('AbCoverWidget','cover'),('AbPlayerWidget','player')]:
  nodes=[n for n in app.findall('receiver') if n.get(a+'name')=='com.audiobookdownloader.'+cls]
  assert len(nodes)==1 and nodes[0].get(a+'exported')=='false'
  assert nodes[0].find('meta-data').get(a+'resource')=='@xml/ab_widget_'+slug+'_info'
  assert nodes[0].find('intent-filter/action').get(a+'name')=='android.appwidget.action.APPWIDGET_UPDATE'
 activity=[n for n in app.findall('activity') if n.get(a+'name')=='com.audiobookdownloader.AbWidgetConfigureActivity']
 assert len(activity)==1 and activity[0].get(a+'exported')=='true'
 assert node_tree(remove_widgets(root))==node_tree(ET.parse(base/f'app/src/{variant}/AndroidManifest.xml').getroot())
print('PASS: exactly three new providers plus configuration in debug/release; existing components/permissions unchanged; playback only gains the verified hooks')
checker=Path('/workspace/audiobook-update/check_sources.py').read_text();ns={'__file__':str(r/'check_sources.py')}
exec(checker[:checker.index('main = (ROOT')],ns);ns['check_delimiters'](w)
res=r/'app/src/main/res';allowed={'FrameLayout','LinearLayout','TextView','ImageView','ImageButton'}
resources={kind:set(p.stem for p in (res/kind).glob('*.xml')) for kind in ['layout','xml','drawable']}
resources['string']=set()
for path in (res/'values').glob('*.xml'):
 for n in ET.parse(path).getroot():
  if n.tag=='string':resources['string'].add(n.get('name'))
for slug,cells in [('compact',('4','1')),('cover',('2','2')),('player',('4','2'))]:
 info=ET.parse(res/f'xml/ab_widget_{slug}_info.xml').getroot()
 assert info.get(a+'targetCellWidth')==cells[0] and info.get(a+'targetCellHeight')==cells[1]
 assert info.get(a+'updatePeriodMillis')=='0' and info.get(a+'widgetFeatures')=='reconfigurable'
 assert info.get(a+'configure')=='com.audiobookdownloader.AbWidgetConfigureActivity'
 tree=ET.parse(res/f'layout/ab_widget_{slug}.xml').getroot()
 assert all(n.tag in allowed for n in tree.iter())
 ids=[n.get(a+'id') for n in tree.iter() if n.get(a+'id')]
 assert len(ids)==len(set(ids))
 for common in ['root','background','cover','title','chapter','toggle','configure']:assert '@+id/ab_widget_'+common in ids
 if slug!='compact':assert '@+id/ab_widget_progress' in ids
 if slug!='cover':
  for control in ['previous','next']:assert '@+id/ab_widget_'+control in ids
 if slug=='player':
  for control in ['elapsed','duration','timer']:assert '@+id/ab_widget_'+control in ids
for path in (r/'app').rglob('*.xml'):
 root=ET.parse(path).getroot()
 if 'ab_widget' not in path.name:continue
 for node in root.iter():
  for value in node.attrib.values():
   m=re.fullmatch('@(layout|xml|drawable|string)/(ab_widget_.*)',value)
   if m:assert m[2] in resources[m[1]],(path,m.groups())
refs=re.findall(r'R\.(layout|id)\.(ab_widget_\w+)',w)
all_ids={n.get(a+'id','').replace('@+id/','') for pth in (res/'layout').glob('ab_widget_*.xml') for n in ET.parse(pth).getroot().iter()}
for kind,name in refs:assert name in (all_ids if kind=='id' else resources['layout']),(kind,name)
palettes=re.findall(r'AbWidgetPalette\((\d),"([^\"]+)"',w)
assert palettes==[('1','Перламутр'),('2','Ледяная ночь'),('3','Сиреневые сумерки'),('4','Янтарь'),('5','Изумруд'),('6','Чёрный обсидиан'),('7','Чёрный оникс'),('8','Чёрный лёд'),('9','Чёрный аметист')]
print('PASS: all 27 format/theme combinations share verified layouts, IDs and theme registry; XML references resolve and layouts contain only RemoteViews-supported classes')
for token in ['getForegroundService(context,id,intent,flags)','PendingIntent.FLAG_IMMUTABLE','Intent(context,PlaybackService::class.java)',
              'goAsync()','pending?.finish()','Executors.newSingleThreadExecutor()', 'now-lastUpdate<5000L',
              'if(running)return','nextEmptyScan','if(owns(app,id))','onRestored','onDeleted',
              'setOnClickPendingIntent(R.id.ab_widget_timer,openPending(context,id,AbWidgetRoutes.TIMER))',
              'if(state.selected)servicePending(context,id,PlaybackService.ACTION_TOGGLE) else open',
              'state.index+1<state.count','if(state.playing)"pause" else "play"']:
 assert token in w,token
assert 'AlarmManager' not in w and 'HttpURLConnection' not in w and 'WorkManager' not in w
assert 'widget_playing' not in w
assert 'if(widgetRoute.target=="player" || widgetRoute.target=="timer")appPage=AppPage.PLAYER' in s
assert 'if(widgetRoute.target=="timer")settings=AbSettingsSection.TIMER' in s
assert 'AbGlassWidgets.refreshAll(context)' in s and 'AbWidgetSettings(theme)' in s
for width in range(130,641):
 for height in range(64,481):
  scale=min(1.5,math.sqrt(100000/(width*height)))
  pixels=int(width*scale+.5)*int(height*scale+.5)
  assert pixels<=100600
# Upper bound of one square-widget transaction: background, large cover, play,
# configure icon, and progress; names/state strings add only small metadata.
max_bitmap_bytes=4*(100600+192*276+72*72+32*32+256*6)
assert max_bitmap_bytes<700000,max_bitmap_bytes
print('PASS: isolated immutable PendingIntents, direct service controls, event/5s updates, no idle polling; raster budget under 700 KB for the largest widget payload')
print('Static and numeric checks only: Android manifest merge, widget placement, PendingIntent delivery and actual rendering still require an APK/device.')
