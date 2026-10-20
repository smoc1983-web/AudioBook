#!/usr/bin/env python3
"""Rename launcher labels without replacing the project's main manifest.
Preview: python3 rename_launcher.py /path/to/project
Apply:   python3 rename_launcher.py /path/to/project --apply
Copy app/src/main/res/values/audiobook_branding.xml into the project first.
"""
from pathlib import Path
import argparse
import re
import shutil
import xml.etree.ElementTree as ET
from xml.parsers import expat

ANDROID='http://schemas.android.com/apk/res/android'
LABEL='@string/audiobook_product_name'
TAG=re.compile(rb'<(?:[^>"\']|"[^"]*"|\'[^\']*\')*>',re.S)
LABEL_ATTR=re.compile(rb'\sandroid:label\s*=\s*(?:"[^"]*"|\'[^\']*\')')

def launcher_names(raw):
    doc=ET.fromstring(raw)
    names={'MainActivity'}
    for app in doc.findall('application'):
        for node in app:
            if node.tag not in ('activity','activity-alias'):continue
            for intent in node.findall('intent-filter'):
                actions={n.get('{'+ANDROID+'}name') for n in intent.findall('action')}
                categories={n.get('{'+ANDROID+'}name') for n in intent.findall('category')}
                if 'android.intent.action.MAIN' in actions and categories.intersection({
                    'android.intent.category.LAUNCHER','android.intent.category.LEANBACK_LAUNCHER'}):
                    names.add(node.get('{'+ANDROID+'}name','').rsplit('.',1)[-1])
    return names

def renamed(raw,names):
    # Android's conventional prefix is required; fail rather than rewrite an unfamiliar namespace.
    if not re.search(rb'xmlns:android\s*=\s*["\']'+ANDROID.encode()+rb'["\']',raw):
        raise ValueError('Expected xmlns:android declaration; update this manifest manually.')
    parser=expat.ParserCreate(namespace_separator='|')
    spans=[]
    def start(tag,attrs):
        if tag=='application' or (tag in ('activity','activity-alias') and
                attrs.get(ANDROID+'|name','').rsplit('.',1)[-1] in names):
            offset=parser.CurrentByteIndex
            match=TAG.match(raw,offset)
            if match is None:raise ValueError('Cannot locate manifest tag')
            tag_bytes=match.group()
            attr=b' android:label="'+LABEL.encode()+b'"'
            if LABEL_ATTR.search(tag_bytes):new=LABEL_ATTR.sub(lambda _:attr,tag_bytes,count=1)
            else:
                end=-2 if tag_bytes.endswith(b'/>') else -1
                new=tag_bytes[:end]+attr+tag_bytes[end:]
            spans.append((offset,match.end(),new))
    parser.StartElementHandler=start
    parser.Parse(raw,True)
    for first,last,replacement in reversed(spans):raw=raw[:first]+replacement+raw[last:]
    ET.fromstring(raw) # reject invalid XML before writing anything
    return raw

def main():
    cli=argparse.ArgumentParser(description='Set Audiobook labels in app source manifests; preserve every other declaration.')
    cli.add_argument('project',type=Path,nargs='?',default=Path.cwd())
    cli.add_argument('--apply',action='store_true',help='Write the changes after creating backups')
    args=cli.parse_args()
    project=args.project.resolve()
    source=project/'app/src'
    if not (source/'main/AndroidManifest.xml').is_file():cli.error('app/src/main/AndroidManifest.xml not found')
    resource=source/'main/res/values/audiobook_branding.xml'
    if not resource.is_file():cli.error('Copy app/src/main/res/values/audiobook_branding.xml from the archive first')
    manifests=sorted(source.glob('*/AndroidManifest.xml'))
    originals={p:p.read_bytes() for p in manifests}
    names=set().union(*(launcher_names(raw) for raw in originals.values()))
    updates={p:renamed(raw,names) for p,raw in originals.items()}
    for path,raw in updates.items():
        if raw==originals[path]:continue
        print(('UPDATE ' if args.apply else 'PREVIEW ')+str(path.relative_to(project)))
        if args.apply:
            backup=path.with_name(path.name+'.before-audiobook-name')
            if not backup.exists():shutil.copy2(path,backup)
            temporary=path.with_name(path.name+'.audiobook-tmp')
            temporary.write_bytes(raw)
            temporary.replace(path)
    if not args.apply:print('Preview only. Add --apply to write; existing manifests are backed up first.')

if __name__=='__main__':main()
