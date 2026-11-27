package com.audiobookdownloader

import androidx.compose.foundation.Canvas
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.os.Build
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONObject
import android.media.MediaMetadataRetriever
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke

// Settings screen and its local Compose helpers. Extracted without behavioral changes.
internal enum class AbSettingsSection(val label:String,val subtitle:String,val icon:AbIcon) {
    HOME("Настройки","",AbIcon.SETTINGS),
    PLAYER_HOME("Меню плеера","",AbIcon.PLAY),
    SPEED("Скорость","Темп прослушивания",AbIcon.PLAY),
    EQUALIZER("Эквалайзер","Профиль и усиление",AbIcon.EQUALIZER),
    TIMER("Таймер сна","Остановка по времени",AbIcon.TIMER),
    SHAKE("Встряхивание","Перезапустить таймер",AbIcon.SYNC),
    REWIND("После паузы","Откат позиции",AbIcon.PREVIOUS),
    APPEARANCE("Оформление","Тема и фон",AbIcon.SETTINGS),
    CONTINUATIONS("Продолжения","Новинки циклов в коллекциях",AbIcon.BOOKS),
    WIDGETS("Виджеты","Главный экран Android",AbIcon.COLLECTIONS),
    SUPPORT("Поддержка","Реквизиты разработчика",AbIcon.HEART),
    BACKUP("Резервная копия","Коллекции, позиции и аудиофайлы",AbIcon.BOOKS),
    STORAGE("Память устройства","Размер книг и очистка",AbIcon.DOWNLOAD),
    UPDATES("Обновления","Версия и установка",AbIcon.SYNC),
    DIAGNOSTICS("Диагностика","События и сбои",AbIcon.SYNC),
    PLAYER_TOOLS("Панель плеера","Кнопки, время главы и книги",AbIcon.PLAY)
}

@Composable
private fun AbSettingsTiles(theme:AbTheme,sections:List<AbSettingsSection>,onSelect:(AbSettingsSection)->Unit) {
    sections.chunked(2).forEach {row ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            row.forEach {section ->
                Column(Modifier.weight(1f).heightIn(min=92.dp).abGlass(theme,16)
                    .semantics{contentDescription=section.label+". "+section.subtitle;role=androidx.compose.ui.semantics.Role.Button}
                    .clickable{onSelect(section)}.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    AbIcon(section.icon,theme.accent)
                    Text(section.label,color=theme.text,fontSize=13.sp,fontWeight=FontWeight.Medium)
                    Text(section.subtitle,color=theme.muted,fontSize=10.sp)
                }
            }
            if(row.size==1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun AbSettingsDialog(data: AbScreenData,prefs: android.content.SharedPreferences,theme: AbTheme,
    actions: AbActions,sync:LibrarySyncState,initialSection:AbSettingsSection,onCrashReport:()->Unit,dismiss:()->Unit) {
    var section by androidx.compose.runtime.saveable.rememberSaveable(initialSection) {mutableStateOf(initialSection)}
    val playerSections=listOf(AbSettingsSection.EQUALIZER,AbSettingsSection.SPEED,AbSettingsSection.TIMER,
        AbSettingsSection.REWIND,AbSettingsSection.SHAKE,AbSettingsSection.PLAYER_TOOLS)
    val generalSections=listOf(AbSettingsSection.APPEARANCE,AbSettingsSection.WIDGETS,AbSettingsSection.SUPPORT,
        AbSettingsSection.BACKUP,AbSettingsSection.STORAGE,AbSettingsSection.UPDATES,AbSettingsSection.DIAGNOSTICS)
    fun back() {
        when {
            section in playerSections -> section=AbSettingsSection.PLAYER_HOME
            section in generalSections -> section=AbSettingsSection.HOME
            else -> dismiss()
        }
    }
    androidx.activity.compose.BackHandler(enabled=section!=AbSettingsSection.HOME && section!=AbSettingsSection.PLAYER_HOME) {back()}
    val context=androidx.compose.ui.platform.LocalContext.current
    val title=data.playback.bookTitle
    val eq=loadEqSettings(prefs,title)
    val perBook=hasBookEq(prefs,title)
    fun updateInt(key: String,value: Int) {prefs.edit().putInt(key,value).apply();PlaybackService.refreshSettings(context)}
    fun updateFlag(key: String,value: Boolean) {prefs.edit().putBoolean(key,value).apply();PlaybackService.refreshSettings(context)}
    fun setEq(value: EqSettings) {
        saveEqSettings(prefs,if(perBook && title.isNotBlank()) eqBookKey(title) else EQ_GLOBAL_KEY,value)
        PlaybackService.refreshSettings(context)
    }
    key(section) { AbDialog(section.label,theme,dismiss) {
        if(section==AbSettingsSection.HOME) {
            Text("Выберите блок настроек",color=theme.muted,fontSize=13.sp)
            AbButton("Меню плеера",theme){section=AbSettingsSection.PLAYER_HOME}
            AbSettingsTiles(theme,generalSections){section=it}
            AbButton("Telegram-канал ↗",theme) {
                try {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://t.me/+IryIQ5fkmOszZWRi")))}
                catch(_:android.content.ActivityNotFoundException) {
                    Toast.makeText(context,"Не удалось открыть Telegram-ссылку",Toast.LENGTH_LONG).show()
                }
            }
        } else if(section==AbSettingsSection.PLAYER_HOME) {
            AbButton("Все настройки",theme){section=AbSettingsSection.HOME}
            AbSettingsTiles(theme,playerSections){section=it}
            AbButton("Добавить свою книгу",theme){dismiss();context.startActivity(Intent(context,SharedAudioImportActivity::class.java).putExtra("open_import_in_player",true))}
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clip(RoundedCornerShape(12.dp))
                .clickable{back()}.padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                AbIcon(AbIcon.BACK,theme.accent,Modifier.size(18.dp))
                Text(when {section in playerSections->"Меню плеера";section in generalSections->"Все настройки";else->"Назад"},
                    color=theme.accent,fontSize=13.sp,modifier=Modifier.padding(start=10.dp))
            }
        }

        if(section==AbSettingsSection.BACKUP)AbBackupPanel(theme){sync.requestSync()}
        if(section==AbSettingsSection.STORAGE)AbStoragePanel(sync.books.value,prefs,theme,actions.deleteBook)
        if(section==AbSettingsSection.UPDATES)AbButton("Проверить обновления",theme){context.startActivity(Intent(context,PreviewUpdatesActivity::class.java))}
        if(section==AbSettingsSection.PLAYER_TOOLS)AbPlayerToolSettings(prefs,theme)
        if(section==AbSettingsSection.SPEED) {
            AbHeading("Скорость воспроизведения",theme)
            val speed=PlaybackOptions.rate(if(data.playback.hasPlayer) data.playback.speed else
                prefs.getFloat("book_speed_"+safeFileName(title),prefs.getFloat("playback_speed",1f)))
            var fine by remember(prefs){mutableStateOf(prefs.getBoolean("speed_fine_step",true))}
            AbSpeedControls(speed,fine,theme,{value->
                val editor=prefs.edit().putFloat("playback_speed",value)
                if(title.isNotBlank())editor.putFloat("book_speed_"+safeFileName(title),value)
                editor.apply();PlaybackService.refreshSettings(context)
            },{fine=it;updateFlag("speed_fine_step",it)})
        }
        if(section==AbSettingsSection.EQUALIZER) {
            AbHeading("Эквалайзер",theme)
            var editableEq by remember(title,perBook){mutableStateOf(eq)}
            fun update(value:EqSettings){editableEq=value;setEq(value)}
            AbOptions(listOf("Выкл","Речь","Бас","Чёткость","Ручной"),editableEq.preset,theme){update(editableEq.copy(preset=it))}
            if(editableEq.preset==4)listOf("Низкие","Нижняя середина","Середина","Верхняя середина","Высокие").forEachIndexed{i,label->
                val bands=editableEq.custom.takeIf{it.size==5}?:List(5){0}
                Text(label+" · "+bands[i]+" дБ",color=theme.text,fontSize=12.sp)
                androidx.compose.material3.Slider(value=bands[i].toFloat(),onValueChange={v->
                    update(editableEq.copy(custom=bands.mapIndexed{j,old->if(i==j)v.toInt() else old}))
                },valueRange=-6f..6f,steps=11)
            }
            AbOptions(listOf("0 дБ","+3 дБ","+6 дБ","+9 дБ"),listOf(0,3,6,9).indexOf(editableEq.gainDb),theme) {
                update(editableEq.copy(gainDb=listOf(0,3,6,9)[it]))
            }
            AbOptions(listOf("Для всех книг","Только эта"),if(perBook) 1 else 0,theme) {index ->
                if(index==1 && title.isBlank()) Toast.makeText(context,"Сначала включите книгу",Toast.LENGTH_SHORT).show()
                else {
                    if(index==1) saveEqSettings(prefs,eqBookKey(title),editableEq)
                    else prefs.edit().remove(eqBookKey(title)).apply()
                    PlaybackService.refreshSettings(context)
                }
            }
            if(data.playback.hasPlayer && !data.playback.eqAvailable)
                Text("Эквалайзер недоступен для текущей аудиосессии",color=theme.muted,fontSize=12.sp)
        }
        if(section==AbSettingsSection.TIMER) {
            AbHeading("Таймер сна",theme)
            Text("Плавное затихание перед остановкой",color=theme.text,fontSize=14.sp)
            AbOptions(listOf("Выкл","10 с","30 с","60 с"),listOf(0,10,30,60).indexOf(prefs.getInt("sleep_fade_seconds",10)),theme){updateInt("sleep_fade_seconds",listOf(0,10,30,60)[it])}
            AbOptions(listOf("Закладка перед сном","Без закладки"),if(prefs.getBoolean("sleep_auto_bookmark",true))0 else 1,theme){updateFlag("sleep_auto_bookmark",it==0)}
            Text(if(data.playback.sleepSeconds>0) "Осталось "+formatSleepTime(data.playback.sleepSeconds)
                else if(data.playback.sleepChapterTarget.isNotBlank()) "До конца текущей главы" else "Выключен",
                color=theme.muted,fontSize=13.sp)
            if(!data.playback.hasPlayer) Text("Включите книгу, чтобы установить таймер.",color=theme.muted,fontSize=12.sp)
            val presets=listOf(15,20,30,60)
            AbOptions(presets.map{"$it мин"}+listOf("Глава","Выкл"),-1,theme,
                enabled=data.playback.hasPlayer) {index ->
                when {
                    index<presets.size->PlaybackService.setSleepTimer(context,presets[index]*60)
                    index==presets.size->PlaybackService.setSleepTimer(context,0,true)
                    else->PlaybackService.setSleepTimer(context,0)
                }
            }
            AbTimerInput(data.playback.hasPlayer,theme){PlaybackService.setSleepTimer(context,it)}
            val previous=prefs.getInt("last_sleep_seconds",1200).coerceIn(1,86400)
            AbButton("Повторить: "+formatSleepTime(previous),theme,enabled=data.playback.hasPlayer) {
                PlaybackService.setSleepTimer(context,previous)
            }
            AbHeading("Ночной таймер по расписанию",theme)
            val intervals=listOf(21 to 5,22 to 6,23 to 7,0 to 8)
            val enabled=prefs.getBoolean("sleep_sched_enabled",false)
            val interval=intervals.indexOf(prefs.getInt("sleep_sched_from",22) to prefs.getInt("sleep_sched_to",6))
            AbOptions(listOf("Выкл","21–05","22–06","23–07","00–08"),if(enabled) interval+1 else 0,theme) {index ->
                if(index==0) updateFlag("sleep_sched_enabled",false)
                else {
                    prefs.edit().putBoolean("sleep_sched_enabled",true)
                        .putInt("sleep_sched_from",intervals[index-1].first).putInt("sleep_sched_to",intervals[index-1].second).apply()
                    PlaybackService.refreshSettings(context)
                }
            }
            AbOptions(listOf("15 мин","30 мин","45 мин","60 мин"),listOf(15,30,45,60).indexOf(prefs.getInt("sleep_sched_minutes",30)),theme) {
                updateInt("sleep_sched_minutes",listOf(15,30,45,60)[it])
            }
        }
        if(section==AbSettingsSection.REWIND) {
            AbHeading("Перемотка в плеере",theme)
            Text("Короткий шаг",color=theme.text,fontSize=14.sp)
            AbOptions(listOf("5 с","10 с","15 с","30 с"),listOf(5,10,15,30).indexOf(prefs.getInt("experience_seek_short",10)),theme){updateInt("experience_seek_short",listOf(5,10,15,30)[it])}
            Text("Длинный шаг",color=theme.text,fontSize=14.sp)
            AbOptions(listOf("30 с","1 мин","2 мин"),listOf(30,60,120).indexOf(prefs.getInt("experience_seek_long",60)),theme){updateInt("experience_seek_long",listOf(30,60,120)[it])}
            AbHeading("Откат после паузы",theme)
            Text("Вернуться немного назад после паузы длиннее 30 секунд",color=theme.muted,fontSize=12.sp)
            AbOptions(listOf("Выкл","5 с","10 с","30 с"),listOf(0,5,10,30).indexOf(prefs.getInt("rewind_after_pause_sec",5)),theme) {
                updateInt("rewind_after_pause_sec",listOf(0,5,10,30)[it])
            }
        }
        if(section==AbSettingsSection.SHAKE) {
            AbHeading("Продлить таймер встряхиванием",theme)
            Text("Во время воспроизведения с минутным таймером сделайте два отчётливых встряхивания. Отсчёт начнётся заново с выбранной длительности, в том числе при выключенном экране.",
                color=theme.muted,fontSize=12.sp)
            Text(if(data.playback.shakeListening) "Датчик движения включён"
                else "Датчик включается во время воспроизведения с минутным таймером.",color=theme.muted,fontSize=12.sp)
            AbOptions(listOf("Вкл","Выкл"),if(prefs.getBoolean("sleep_shake",true)) 0 else 1,theme){updateFlag("sleep_shake",it==0)}
        }
        if(section==AbSettingsSection.APPEARANCE) {
            AbHeading("Оформление",theme)
            Text("Выберите тему. «Графит» — без свечения и световых бликов.",color=theme.muted,fontSize=12.sp)
            AbReadabilityPanel(prefs,theme)
            AbThemePicker(data.skin,actions.skin)
            AbHeading("Фон",theme)
            Text(if(data.backgroundUri==null) "Стандартный фон · ${theme.name}" else "Выбран фон из галереи",
                color=theme.muted,fontSize=12.sp)
            Text("Можно менять тему и фон независимо. При выборе другой темы ваш фон сохранится.",
                color=theme.muted,fontSize=12.sp,lineHeight=18.sp)
            AbButton("Выбрать фон из галереи",theme,onClick=actions.chooseBackground)
            if(data.backgroundUri!=null) AbButton("Вернуть фон темы",theme,onClick=actions.clearBackground)
        }
        if(section==AbSettingsSection.CONTINUATIONS) {AbContinuationSettings(theme) {ref -> dismiss();actions.openRef(ref)}}
        if(section==AbSettingsSection.WIDGETS) {AbWidgetSettings(theme)}
        if(section==AbSettingsSection.SUPPORT) {
            AbHeading("Поддержать разработчика",theme)
            Text("Если приложение вам помогает, вы можете поддержать его развитие переводом по номеру телефона.",
                color=theme.muted,fontSize=13.sp,lineHeight=20.sp)
            Text("+79128580680",color=theme.text,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
            AbSupportBank.entries.forEach { bank ->
                AbButton(bank.title,theme){AbSupportBanks.open(context,bank)}
            }
            AbButton("Скопировать номер",theme) {
                val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Поддержка разработчика","+79128580680"))
                Toast.makeText(context,"Номер скопирован",Toast.LENGTH_SHORT).show()
            }
        }
        if(section==AbSettingsSection.HOME) {
            AbButton("Перенос и копия коллекций",theme){context.startActivity(Intent(context,CollectionTransferActivity::class.java))}
            AbButton("Обновления · "+BuildConfig.VERSION_NAME,theme) {
                context.startActivity(Intent(context,PreviewUpdatesActivity::class.java))
            }
        }
        if(section==AbSettingsSection.DIAGNOSTICS) {
            AbHeading("Диагностика",theme)
            AbButton("Отчёт после обновления или проверки (.txt)",theme,onClick=onCrashReport)
            Text("Включает версию, дату обновления, технические события, сравнение источников и статистику последних поисков по каждому сайту. Поиск без VPN и с VPN сопоставляется без передачи названия книги. Отправка вручную через Android.",color=theme.muted,fontSize=12.sp)
        }
        Text(BuildConfig.PRODUCT_NAME,color=theme.muted,fontSize=12.sp)
    } }
}

@Composable
private fun AbThemePicker(selected: Int, onSelect: (Int)->Unit) {
    AbGlassThemes.forEach { candidate ->
        val active=selected==candidate.id
        Row(Modifier.fillMaxWidth().abGlass(candidate,16,selected=active,opaque=true)
            .semantics {this.selected=active;role=androidx.compose.ui.semantics.Role.RadioButton;
                contentDescription=candidate.name+if(active) ", выбрана" else ", выбрать тему"}
            .clickable {onSelect(candidate.id)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(
                listOf(candidate.background,candidate.primary,candidate.reflection)))) {
                Box(Modifier.align(Alignment.Center).size(31.dp,21.dp).abGlass(candidate,6,field=true))
                Box(Modifier.align(Alignment.BottomEnd).padding(5.dp).size(10.dp).clip(CircleShape).background(candidate.accent))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(candidate.name,color=candidate.text,fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=2)
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    listOf(candidate.accent,candidate.reflection,candidate.primary).forEach { color ->
                        Box(Modifier.size(11.dp).clip(CircleShape).background(color))
                    }
                }
            }
            Box(Modifier.size(26.dp).clip(CircleShape)
                .background(if(active)candidate.primary else candidate.field)
                .border(1.dp,if(active)candidate.accent else candidate.border,CircleShape),
                contentAlignment=Alignment.Center) {
                if(active)AbIcon(AbIcon.CHECK,candidate.onAccent,Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun AbWidgetSettings(theme: AbTheme) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var revision by remember {mutableStateOf(0)}
    var installed by remember {mutableStateOf<List<Pair<Int,String>>>(emptyList())}
    LaunchedEffect(revision) {
        installed=withContext(Dispatchers.IO) {
            val manager=android.appwidget.AppWidgetManager.getInstance(context)
            AbGlassWidgets.installed(context).map {id ->
                id to (manager.getAppWidgetInfo(id)?.loadLabel(context.packageManager) ?: "Audiobook")
            }
        }
    }
    Text("Добавьте плеер на главный экран Android. У каждого виджета своя тема или выбор «Как в приложении».",
        color=theme.muted,fontSize=13.sp,lineHeight=19.sp)
    listOf("Компактный · 4×1","Обложка · 2×2","Плеер · 4×2").forEachIndexed {index,label ->
        AbWidgetCataloguePreview(index,theme)
        AbButton("Добавить: $label",theme){AbGlassWidgets.requestPin(context,index)}
    }
    Text("Также можно зажать пустое место на главном экране Android → Виджеты → Audiobook.",color=theme.muted,fontSize=12.sp)
    if(installed.isNotEmpty())AbHeading("Добавленные виджеты",theme)
    installed.forEach {(id,label) ->
        AbButton("$label · №$id",theme) {
            try {context.startActivity(AbGlassWidgets.configureIntent(context,id))}
            catch(_:Exception){Toast.makeText(context,"Не удалось открыть настройку виджета",Toast.LENGTH_SHORT).show()}
        }
    }
    AbButton("Обновить список",theme){revision++}
}

@Composable
private fun AbWidgetCataloguePreview(kind: Int,theme: AbTheme) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var preview by remember(kind,theme) {mutableStateOf<android.widget.RemoteViews?>(null)}
    DisposableEffect(kind,theme) {
        var active=true
        val main=android.os.Handler(android.os.Looper.getMainLooper())
        AbGlassWidgets.previewKindAsync(context,kind) {views -> main.post {if(active)preview=views}}
        onDispose {active=false}
    }
    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory={android.widget.FrameLayout(it)},
            modifier=(if(kind==1)Modifier.width(190.dp) else Modifier.fillMaxWidth())
                .height(if(kind==0)80.dp else 200.dp)
                .semantics {contentDescription="Предпросмотр: "+listOf("Компактный","Обложка","Плеер")[kind]},
            update={host ->
                host.removeAllViews()
                preview?.let {views ->
                    host.addView(views.apply(context,host),android.widget.FrameLayout.LayoutParams(-1,-1))
                    host.addView(android.view.View(context).apply {isClickable=true},android.widget.FrameLayout.LayoutParams(-1,-1))
                }
            })
    }
}
