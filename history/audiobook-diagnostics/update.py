from pathlib import Path
import shutil

root=Path(__file__).parent
base=Path('/workspace/audiobook-stability')
s=(base/'MainActivity.kt').read_text()
start=s.index('private object AbCrashReport {')
end=s.index('    private fun readSystem(context:',start)
s=s[:start]+(root/'crash-handler.txt').read_text()+'\n'+s[end:]
old='''        super.onCreate(savedInstanceState)
        // One request:'''
assert s.count(old)==1
s=s.replace(old,'''        super.onCreate(savedInstanceState)
        AbCrashReport.install(this)
        // One request:''')
old='''        super.onResume()
        // Also refresh'''
assert s.count(old)==1
s=s.replace(old,'''        super.onResume()
        AbCrashReport.install(this)
        // Also refresh''')
old='''        if(saved!=null && (requested>0 || saved.first!=seen)) report=saved'''
assert s.count(old)==1
s=s.replace(old,'''        if(saved!=null && (requested>0 ||
            (saved.first!=seen && !AbCrashReport.historical(context,saved.first)))) report=saved''')
old='''        Text("Сохранена причина предыдущего сбоя. Скопируйте отчёт и пришлите его вместе с описанием действий перед вылетом.",'''
assert s.count(old)==1
s=s.replace(old,'''        Text(if(AbCrashReport.historical(context,saved.first))
            "Это запись события до первого запуска этой версии диагностики. Она доступна для сравнения с новым сбоем."
            else "Сохранён отчёт о сбое. Скопируйте его и пришлите вместе с описанием действий перед вылетом.",''')
(root/'MainActivity.kt').write_text(s)
for name in ['PlaybackService.kt','external-genres.txt','audiobook-4.0.init.gradle','check.py']:
    shutil.copy2(base/name,root/name)
print('Generated diagnostics update:',len(s.splitlines()),'lines')
