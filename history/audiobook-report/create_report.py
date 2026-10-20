from pathlib import Path
from zipfile import ZipFile
import hashlib
import re
from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

workspace=Path('/workspace')
source=workspace/'AudioBook-4.0-Work-History.txt'
report=workspace/'AudioBook-4.0-History.txt'
word=workspace/'AudioBook-4.0-History.docx'
text=source.read_text(encoding='utf-8')
archives=[
    'AudioBook-service-update.zip','AudioBook-search-cycle.zip','AudioBook-library-sync.zip',
    'AudioBook-3.0-redesign.zip','AudioBook-3.0-compile-fix.zip','AudioBook-covers-swipes.zip',
    'AudioBook-4.0-update.zip','AudioBook-4.0-genres-series.zip','AudioBook-4.0-external-genres.zip',
    'AudioBook-4.0-stability.zip','AudioBook-4.0-diagnostics-2.zip','AudioBook-4.0-azbyka.zip',
    'AudioBook-4.0-ANR-fix.zip','AudioBook-4.0-Azbyka-direct-fix.zip','AudioBook-4.0-Startup-recovery.zip',
    'AudioBook-4.0-LogSender-Compose-fix.zip','AudioBook-4.0-Current-crash-report.zip',
    'AudioBook-4.0-Shake-timer-fix.zip','AudioBook-4.0-Search-recovery.zip',
]
rows=[]
for name in archives:
    with ZipFile(workspace/name) as z:
        contents=[n for n in z.namelist() if n.endswith(('.kt','.xml','.gradle','.kts'))]
    rows.append((name,contents))
appendix='\n\n'+ '\n\n'.join(name+'\n'+ '\n'.join('- '+n for n in contents) for name,contents in rows)
checks=[
    ('MainActivity.kt — последнее исправление поиска',workspace/'audiobook-search-recovery/MainActivity.kt'),
    ('PlaybackService.kt — последний сервис с продлением таймера',workspace/'audiobook-shake-fix/PlaybackService.kt'),
    ('app/src/debug/AndroidManifest.xml — исключение LogSender',workspace/'audiobook-logsender-fix/app/src/debug/AndroidManifest.xml'),
]
appendix+='\n\nКонтрольные суммы актуальных файлов (SHA-256)\n\n'
for label,path in checks:
    appendix+=label+'\n'+hashlib.sha256(path.read_bytes()).hexdigest()+'\n\n'
appendix+='Контрольные суммы относятся к подготовленным здесь файлам. Они не доказывают, что именно эти файлы установлены на телефоне.\n'
full=text.rstrip()+appendix
# BOM improves compatibility with Windows Notepad and importing Cyrillic text.
report.write_text(full,encoding='utf-8-sig')

doc=Document()
sec=doc.sections[0]
sec.page_width=Cm(21)
sec.page_height=Cm(29.7)
sec.top_margin=Cm(1.8)
sec.bottom_margin=Cm(1.8)
sec.left_margin=Cm(2)
sec.right_margin=Cm(1.8)
sec.header_distance=Cm(.8)
sec.footer_distance=Cm(.8)
normal=doc.styles['Normal']
normal.font.name='Calibri'
normal.font.size=Pt(11)
normal.paragraph_format.space_after=Pt(5)
normal.paragraph_format.line_spacing=1.08
for style_name,size in [('Title',23),('Heading 1',15),('Heading 2',12)]:
    style=doc.styles[style_name]
    style.font.name='Calibri'
    style.font.size=Pt(size)
    style.font.color.rgb=RGBColor.from_string('143857')
    style.paragraph_format.keep_with_next=True
    style.paragraph_format.space_before=Pt(12)
    style.paragraph_format.space_after=Pt(7)
for style_name in ['Normal','Title','Heading 1','Heading 2','List Bullet']:
    prop=doc.styles[style_name].element.get_or_add_rPr()
    lang=OxmlElement('w:lang');lang.set(qn('w:val'),'ru-RU');prop.append(lang)

header=sec.header.paragraphs[0]
header.text='AudioBook 4.0  |  История разработки и исправлений'
header.style='Normal'
for run in header.runs:
    run.font.size=Pt(9)
    run.font.color.rgb=RGBColor.from_string('687582')
footer=sec.footer.paragraphs[0]
footer.alignment=2
footer.add_run('13.10.2026  ·  Страница ')
for name in ['PAGE','NUMPAGES']:
    field=OxmlElement('w:fldSimple');field.set(qn('w:instr'),name)
    footer._p.append(field)
    if name=='PAGE':footer.add_run(' из ')
for run in footer.runs:run.font.size=Pt(9)

doc.core_properties.title='AudioBook 4.0 — история совместной разработки и исправлений'
doc.core_properties.subject='Функции, изменения исходников, ошибки, проверки и незавершённые требования'
doc.core_properties.author='Совместная работа пользователя и Codex'
doc.core_properties.keywords='AudioBook, Android, Kotlin, PlaybackService, поиск, Азбука, история изменений'

expected_heading=1
headings=[]
archive_set=set(archives)
for i,line in enumerate(full.splitlines()):
    if not line.strip():continue
    if i==0:
        doc.add_paragraph(line,'Title')
        continue
    match=re.match(r'^(\d+)\. ',line)
    if match and int(match.group(1))==expected_heading:
        doc.add_paragraph(line,'Heading 1')
        headings.append(line)
        expected_heading+=1
    elif line=='Приложение. Содержимое архивов':
        doc.add_page_break()
        doc.add_paragraph(line,'Heading 1')
    elif line=='Контрольные суммы актуальных файлов (SHA-256)':
        doc.add_paragraph(line,'Heading 1')
    elif line in archive_set:
        doc.add_paragraph(line,'Heading 2')
    elif line.startswith('- '):
        doc.add_paragraph(line[2:],'List Bullet')
    elif line.startswith('  ') or re.fullmatch('[0-9a-f]{64}',line):
        p=doc.add_paragraph(line)
        for run in p.runs:
            run.font.name='Consolas';run.font.size=Pt(9)
        p.paragraph_format.space_after=Pt(2)
    else:
        doc.add_paragraph(line)
assert len(headings)==33,headings

doc.save(word)
with ZipFile(word) as z:
    assert z.testzip() is None
    assert 'word/document.xml' in z.namelist()
reloaded=Document(word)
visible='\n'.join(p.text for p in reloaded.paragraphs)
for required in ['JSON-резервная копия','Method too large','ExperimentalComposeUiApi','File.listFiles',
                 '89128580680','4.0-search-window-1','45 секунд','1,8 g','app/src/debug/AndroidManifest.xml']+archives:
    assert required in visible,required
# Same report, including annex and checksums, in both formats.
expected=[line[2:] if line.startswith('- ') else line for line in full.splitlines() if line.strip()]
actual=[p.text for p in reloaded.paragraphs if p.text.strip()]
assert expected==actual,'TXT/DOCX content mismatch'
assert report.read_text(encoding='utf-8-sig')==full
print('Created two matching reports; sections:',len(headings),'archives:',len(rows),'words:',len(full.split()))
for path in [report,word]:print(str(path),path.stat().st_size,'bytes')
