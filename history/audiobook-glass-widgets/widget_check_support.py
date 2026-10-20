from pathlib import Path
import xml.etree.ElementTree as ET
ANDROID='{http://schemas.android.com/apk/res/android}'
WIDGETS={'com.audiobookdownloader.'+name for name in ['AbCompactWidget','AbCoverWidget','AbPlayerWidget','AbWidgetConfigureActivity']}
def remove_widgets(root):
 app=root.find('application')
 if app is not None:
  for child in list(app):
   if child.get(ANDROID+'name') in WIDGETS:app.remove(child)
 return root
def node_tree(node):return node.tag,tuple(sorted(node.attrib.items())),tuple(node_tree(c) for c in node)
def baseline_bytes(path):
 path=Path(path)
 if path.name=='PlaybackService.kt':
  s=path.read_text()
  s='\n'.join(line for line in s.split('\n') if 'AbGlassWidgets.publish(' not in line and '.putInt("widget_track_count",playlist.size)' not in line)
  return s.replace(',"widget_track_count"','').encode()
 if path.name=='AndroidManifest.xml':return repr(node_tree(remove_widgets(ET.parse(path).getroot()))).encode()
 return path.read_bytes()
