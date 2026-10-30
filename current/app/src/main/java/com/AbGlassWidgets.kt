package com.audiobookdownloader

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.RemoteViews
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class AbWidgetRoute(val serial: Long = 0, val target: String = "")

internal object AbWidgetRoutes {
    const val PLAYER = BuildConfig.APPLICATION_ID + ".widget.OPEN_PLAYER"
    const val TIMER = BuildConfig.APPLICATION_ID + ".widget.OPEN_TIMER"
    private val counter = AtomicLong()
    private val mutable = MutableStateFlow(AbWidgetRoute())
    val requests = mutable.asStateFlow()
    fun isWidgetIntent(intent: Intent?) = intent?.action==PLAYER || intent?.action==TIMER
    fun accept(intent: Intent?) {
        val target = when (intent?.action) { PLAYER -> "player"; TIMER -> "timer"; else -> "" }
        mutable.value = AbWidgetRoute(counter.incrementAndGet(), target)
    }
}

class AbCompactWidget : AbGlassWidgetProvider()
class AbCoverWidget : AbGlassWidgetProvider()
class AbPlayerWidget : AbGlassWidgetProvider()

open class AbGlassWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        AbGlassWidgets.update(context, ids) { pending?.finish() }
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        val pending = goAsync()
        AbGlassWidgets.update(context, intArrayOf(id)) { pending?.finish() }
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        val editor = AbGlassWidgets.preferences(context).edit()
        ids.forEach { editor.remove("theme_$it") }
        editor.apply()
    }
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        val prefs = AbGlassWidgets.preferences(context)
        val editor = prefs.edit()
        oldIds.zip(newIds.toList()).forEach { (old, new) ->
            editor.putInt("theme_$new", prefs.getInt("theme_$old", 0)).remove("theme_$old")
        }
        editor.apply()
        // AppWidgetProvider dispatches onUpdate after onRestored in this broadcast.
    }
}

private data class AbWidgetPalette(val id: Int, val name: String, val panel: Int,
    val primary: Int, val accent: Int, val reflection: Int, val text: Int, val muted: Int)

private val abWidgetPalettes = listOf(
    AbWidgetPalette(1,"Перламутр",0xF5F5EFE8.toInt(),0xFF5B32D2.toInt(),0xFF4D42B8.toInt(),0xFF8DDED7.toInt(),0xFF202836.toInt(),0xFF474F5C.toInt()),
    AbWidgetPalette(2,"Ледяная ночь",0xEE103151.toInt(),0xFF006B9D.toInt(),0xFF67E3F4.toInt(),0xFF81F0DE.toInt(),0xFFF5FAFF.toInt(),0xFFC7D8E5.toInt()),
    AbWidgetPalette(3,"Сиреневые сумерки",0xEE301854.toInt(),0xFF6533CA.toInt(),0xFFD8BBFF.toInt(),0xFFE3A2E9.toInt(),0xFFFCF7FF.toInt(),0xFFD3C6E3.toInt()),
    AbWidgetPalette(4,"Янтарь",0xEE452917.toInt(),0xFF965408.toInt(),0xFFFFD08C.toInt(),0xFFFFD5A2.toInt(),0xFFFFF9F0.toInt(),0xFFE0CDBC.toInt()),
    AbWidgetPalette(5,"Изумруд",0xEE0B3E35.toInt(),0xFF00745B.toInt(),0xFF7AE5C1.toInt(),0xFFAAEEDB.toInt(),0xFFF2FFF9.toInt(),0xFFC8E3D6.toInt()),
    AbWidgetPalette(6,"Чёрный обсидиан",0xF00C1014.toInt(),0xFF353F49.toInt(),0xFFD4E2ED.toInt(),0xFF9CADBC.toInt(),0xFFF7F9FC.toInt(),0xFFBCC4CD.toInt()),
    AbWidgetPalette(7,"Чёрный оникс",0xF012100D.toInt(),0xFF624017.toInt(),0xFFFFD49B.toInt(),0xFFD5AA70.toInt(),0xFFFFFAF2.toInt(),0xFFD0C3B1.toInt()),
    AbWidgetPalette(8,"Чёрный лёд",0xF0091016.toInt(),0xFF17435A.toInt(),0xFF92E4FF.toInt(),0xFF77BCD7.toInt(),0xFFF3FAFF.toInt(),0xFFB8CBD8.toInt()),
    AbWidgetPalette(9,"Чёрный аметист",0xF0100C17.toInt(),0xFF472460.toInt(),0xFFD9B5FF.toInt(),0xFFB591D9.toInt(),0xFFFDF7FF.toInt(),0xFFCABBD9.toInt()),
    AbWidgetPalette(10,"Графит · без свечения",0xFF1D2226.toInt(),0xFF215947.toInt(),0xFF81E0C0.toInt(),0xFF81E0C0.toInt(),0xFFF4F7F9.toInt(),0xFFCED6DC.toInt())
)

private data class AbWidgetSnapshot(val title: String = "", val chapter: String = "", val index: Int = -1,
    val count: Int = 0, val position: Int = 0, val duration: Int = 0,
    val playing: Boolean = false, val loading: Boolean = false, val sleep: Int = 0,
    val chapterTimer: Boolean = false) {
    val selected: Boolean get() = title.isNotBlank() && index in 0 until count
    val transition: String get() = "$title|$chapter|$index|$count|$duration|$playing|$loading|${sleep>0}|$chapterTimer"
}

/** No independent service, alarms, network requests or polling while playback is paused. */
internal object AbGlassWidgets {
    private val worker = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private val requested = linkedSetOf<Int>()
    private val callbacks = mutableListOf<(Boolean)->Unit>()
    private var all = false
    private var running = false
    private var lastTransition = ""
    private var lastUpdate = 0L
    private var nextEmptyScan = 0L
    @Volatile private var latest: AbWidgetSnapshot? = null
    private val components = listOf(AbCompactWidget::class.java, AbCoverWidget::class.java, AbPlayerWidget::class.java, AudioBookWidget::class.java)

    fun preferences(context: Context) = context.applicationContext.getSharedPreferences("audiobook_glass_widgets",Context.MODE_PRIVATE)
    fun owns(context: Context, id: Int): Boolean = try {
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider
        provider != null && components.any { provider == ComponentName(context,it) }
    } catch (_: Exception) { false }

    fun publish(context: Context, state: ServicePlaybackState) {
        val track = state.tracks.getOrNull(state.trackIndex)
        val snapshot = AbWidgetSnapshot(state.bookTitle,track?.title.orEmpty(),state.trackIndex,state.tracks.size,
            state.positionMs,state.durationMs,state.isPlaying,state.isLoading,state.sleepSeconds,state.sleepChapterTarget.isNotBlank())
        latest = snapshot
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if(snapshot.transition==lastTransition && now-lastUpdate<5000L)return
            lastTransition=snapshot.transition;lastUpdate=now
            if(now<nextEmptyScan)return
        }
        update(context)
    }

    fun refreshAll(context: Context) { synchronized(lock) { nextEmptyScan=0L }; update(context) }

    fun update(context: Context, ids: IntArray? = null, done: ((Boolean)->Unit)? = null) {
        val app = context.applicationContext
        synchronized(lock) {
            if(ids==null) all=true else requested.addAll(ids.toList())
            if(done!=null)callbacks.add(done)
            if(running)return
            running=true
        }
        worker.execute {
            while(true) {
                val batch: Triple<Boolean,IntArray,List<(Boolean)->Unit>> = synchronized(lock) {
                    if(!all && requested.isEmpty() && callbacks.isEmpty()) {running=false;return@execute}
                    Triple(all,requested.toIntArray(),callbacks.toList()).also {
                        all=false;requested.clear();callbacks.clear()
                    }
                }
                var success=true
                try {
                    val manager=AppWidgetManager.getInstance(app)
                    val idsToUpdate=if(batch.first) installed(app) else batch.second
                    synchronized(lock) { nextEmptyScan=if(idsToUpdate.isEmpty())SystemClock.elapsedRealtime()+30000L else 0L }
                    val snapshot=snapshot(app)
                    idsToUpdate.forEach { id ->
                        try {
                            if(owns(app,id))manager.updateAppWidget(id,render(app,id,snapshot))
                            else if(!batch.first)success=false
                        } catch (_: Exception) {success=false}
                    }
                } catch (_: Exception) {success=false}
                catch (_: OutOfMemoryError) {success=false; AbWidgetArt.clear()}
                finally {batch.third.forEach {callback->try {callback(success)} catch (_:Exception) {}}}
            }
        }
    }

    fun installed(context: Context): IntArray = components.flatMap {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,it)).toList()
    }.distinct().toIntArray()

    fun configureIntent(context: Context, id: Int): Intent = Intent(context,AbWidgetConfigureActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setData(Uri.parse("audiobook://widget/$id/configure"))
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)

    fun requestPin(context: Context, kind: Int) {
        val manager=AppWidgetManager.getInstance(context)
        if(Build.VERSION.SDK_INT>=26 && manager.isRequestPinAppWidgetSupported) {
            try {
                val extras=Bundle().apply {putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW,previewKind(context,kind))}
                if(manager.requestPinAppWidget(ComponentName(context,components[kind.coerceIn(0,2)]),extras,null))return
            } catch (_: Exception) { }
        }
        Toast.makeText(context,"Зажмите пустое место на главном экране Android → Виджеты → Audiobook",Toast.LENGTH_LONG).show()
    }

    fun preview(context: Context, id: Int, theme: Int, done: (RemoteViews?)->Unit) {
        val app=context.applicationContext
        worker.execute {
            val views=try {render(app,id,snapshot(app),theme)} catch (_:Exception) {null} catch (_:OutOfMemoryError) {null}
            done(views)
        }
    }

    private fun snapshot(context: Context): AbWidgetSnapshot {
        // Static fields in the player are authoritative while its process is alive.
        val live=PlaybackService.state.value
        if(live.hasSelection || latest?.selected==false && live.bookTitle.isNotBlank()) {
            val track=live.tracks.getOrNull(live.trackIndex)
            return AbWidgetSnapshot(live.bookTitle,track?.title.orEmpty(),live.trackIndex,live.tracks.size,
                live.positionMs,live.durationMs,live.isPlaying,live.isLoading,live.sleepSeconds,live.sleepChapterTarget.isNotBlank())
        }
        val prefs=context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
        val title=prefs.getString("widget_book","").orEmpty()
        if(title.isBlank())return AbWidgetSnapshot()
        val count=prefs.getInt("widget_track_count",0).takeIf {it>0} ?: try {
            val raw=prefs.getString("widget_playlist","[]").orEmpty()
            if(raw.length<=2*1024*1024)JSONArray(raw).length() else 0
        } catch (_:Exception) {0}
        val resolvedCount=if(count>0)count else if(!prefs.getString("widget_media_src","").isNullOrBlank())1 else 0
        return AbWidgetSnapshot(title,prefs.getString("widget_track","").orEmpty(),
            prefs.getInt("widget_track_index",0).coerceIn(0,(resolvedCount-1).coerceAtLeast(0)),resolvedCount,
            prefs.getInt("widget_position",0).coerceAtLeast(0),prefs.getInt("widget_duration",0).coerceAtLeast(0))
        // A saved playlist is always shown paused after process death, never as still playing.
    }

    private fun render(context: Context, id: Int, state: AbWidgetSnapshot, themeOverride: Int? = null): RemoteViews {
        val manager=AppWidgetManager.getInstance(context)
        val provider=manager.getAppWidgetInfo(id)?.provider ?: throw IllegalArgumentException("Виджет удалён")
        val providerIndex=components.indexOfFirst {provider==ComponentName(context,it)}
        require(providerIndex>=0)
        val selected=themeOverride ?: preferences(context).getInt("theme_$id",0)
        return renderKind(context,providerIndex.coerceAtMost(2),id,state,selected,manager.getAppWidgetOptions(id))
    }

    /** Real RemoteViews, also used by Samsung's pin sheet and by the in-app catalogue. */
    fun previewKind(context: Context,kind: Int): RemoteViews = renderKind(context,kind.coerceIn(0,2),0,
        AbWidgetSnapshot("Ваша аудиокнига","Глава 1",0,12,206000,1084000),0,Bundle())

    fun previewKindAsync(context: Context,kind: Int,done: (RemoteViews?)->Unit) {
        val app=context.applicationContext
        worker.execute {done(try {previewKind(app,kind)}catch(_:Exception){null}catch(_:OutOfMemoryError){null})}
    }

    private fun renderKind(context: Context,kind: Int,id: Int,state: AbWidgetSnapshot,selected: Int,options: Bundle): RemoteViews {
        val appTheme=context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE).getInt("glass_theme",2)
        val palette=abWidgetPalettes.firstOrNull {it.id==(if(selected==0)appTheme else selected)} ?: abWidgetPalettes[1]
        val layout=when(kind) {0->R.layout.ab_widget_compact;1->R.layout.ab_widget_cover;else->R.layout.ab_widget_player}
        val defaultWidth=if(kind==1)170 else 340
        val defaultHeight=if(kind==0)72 else 190
        val width=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,defaultWidth).coerceIn(if(kind==1)130 else 250,640)
        val height=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,defaultHeight).coerceIn(if(kind==0)64 else 150,480)
        val views=RemoteViews(context.packageName,layout)
        views.setImageViewBitmap(R.id.ab_widget_background,AbWidgetArt.background(palette,width,height))
        views.setImageViewBitmap(R.id.ab_widget_cover,AbWidgetArt.cover(context,state.title,palette,kind==1))
        views.setTextViewText(R.id.ab_widget_title,if(state.selected)state.title else "Audiobook")
        val chapter=if(!state.selected)"Выберите книгу" else if(state.loading)"Загрузка главы…" else
            AbMediaPresentation.chapter(state.index,state.count,if(kind==0)"" else state.chapter)
        views.setTextViewText(R.id.ab_widget_chapter,chapter)
        views.setTextColor(R.id.ab_widget_title,palette.text)
        views.setTextColor(R.id.ab_widget_chapter,palette.muted)
        views.setContentDescription(R.id.ab_widget_root,if(state.selected)"${state.title}. $chapter" else "Audiobook. Выберите книгу в приложении")
        val open=openPending(context,id,AbWidgetRoutes.PLAYER)
        views.setOnClickPendingIntent(R.id.ab_widget_title,open)
        views.setOnClickPendingIntent(R.id.ab_widget_cover,open)
        views.setOnClickPendingIntent(R.id.ab_widget_chapter,open)
        views.setImageViewBitmap(R.id.ab_widget_configure,AbWidgetArt.icon("configure",palette,32,false))
        views.setOnClickPendingIntent(R.id.ab_widget_configure,PendingIntent.getActivity(context,id,
            configureIntent(context,id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        views.setImageViewBitmap(R.id.ab_widget_toggle,AbWidgetArt.icon(if(state.playing)"pause" else "play",palette,72,true))
        views.setContentDescription(R.id.ab_widget_toggle,if(state.playing)"Пауза" else if(state.selected)"Продолжить воспроизведение" else "Выбрать книгу")
        views.setOnClickPendingIntent(R.id.ab_widget_toggle,if(state.selected)servicePending(context,id,PlaybackService.ACTION_TOGGLE) else open)
        if(kind!=1) {
            for((view,action,icon,enabled) in listOf(
                WidgetControl(R.id.ab_widget_previous,PlaybackService.ACTION_PREVIOUS,"previous",state.selected && state.index>0),
                WidgetControl(R.id.ab_widget_next,PlaybackService.ACTION_NEXT,"next",state.selected && state.index+1<state.count))) {
                views.setImageViewBitmap(view,AbWidgetArt.icon(icon,palette,48,false))
                views.setBoolean(view,"setEnabled",enabled)
                views.setFloat(view,"setAlpha",if(enabled)1f else .35f)
                views.setOnClickPendingIntent(view,if(enabled)servicePending(context,id,action) else null)
            }
        }
        if(kind!=0) {
            val fraction=if(state.duration>0)state.position.toFloat()/state.duration else 0f
            views.setImageViewBitmap(R.id.ab_widget_progress,AbWidgetArt.progress(palette,fraction))
            views.setContentDescription(R.id.ab_widget_progress,if(state.duration>0)"Прослушано ${(fraction.coerceIn(0f,1f)*100).toInt()} процентов главы" else "Длительность главы пока неизвестна")
        }
        if(kind==2) {
            views.setTextViewText(R.id.ab_widget_elapsed,time(state.position))
            views.setTextViewText(R.id.ab_widget_duration,if(state.duration>0)time(state.duration) else "—:—")
            views.setTextColor(R.id.ab_widget_elapsed,palette.muted);views.setTextColor(R.id.ab_widget_duration,palette.muted)
            views.setImageViewBitmap(R.id.ab_widget_timer,AbWidgetArt.icon("timer",palette,48,state.sleep>0 || state.chapterTimer))
            views.setOnClickPendingIntent(R.id.ab_widget_timer,openPending(context,id,AbWidgetRoutes.TIMER))
            views.setContentDescription(R.id.ab_widget_timer,if(state.chapterTimer)"Таймер до конца главы. Открыть настройки" else
                if(state.sleep>0)"Таймер сна: ${time(state.sleep*1000)}. Открыть настройки" else "Открыть таймер сна")
        }
        return views
    }

    private data class WidgetControl(val view: Int,val action: String,val icon: String,val enabled: Boolean)
    private fun time(ms: Int): String {
        val seconds=ms.coerceAtLeast(0)/1000
        return if(seconds>=3600)String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
            else String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60)
    }
    private fun openPending(context: Context,id: Int,action: String): PendingIntent = PendingIntent.getActivity(context,id,
        Intent(context,MainActivity::class.java).setAction(action).setData(Uri.parse("audiobook://widget/$id/$action"))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun servicePending(context: Context,id: Int,action: String): PendingIntent {
        val intent=Intent(context,PlaybackService::class.java).setAction(action).setData(Uri.parse("audiobook://widget/$id/$action"))
        val flags=PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if(Build.VERSION.SDK_INT>=26)PendingIntent.getForegroundService(context,id,intent,flags)
            else PendingIntent.getService(context,id,intent,flags)
    }
}

/** Bounded raster sizes keep each RemoteViews transaction well below the Binder limit. */
private object AbWidgetArt {
    private val backgrounds=object:android.util.LruCache<String,Bitmap>(2*1024*1024) {
        override fun sizeOf(key: String,value: Bitmap)=value.byteCount
    }
    private val covers=object:android.util.LruCache<String,Bitmap>(512*1024) {
        override fun sizeOf(key: String,value: Bitmap)=value.byteCount
    }
    fun clear() {backgrounds.evictAll();covers.evictAll()}
    private fun alpha(color: Int,opacity: Float)=Color.argb((255*opacity).toInt().coerceIn(0,255),Color.red(color),Color.green(color),Color.blue(color))
    fun background(p: AbWidgetPalette,width: Int,height: Int): Bitmap {
        val key="${p.id}:$width:$height"
        backgrounds.get(key)?.let {return it}
        val scale=min(1.5f,sqrt(100000f/(width.toFloat()*height)))
        val w=(width*scale).roundToInt().coerceAtLeast(1);val h=(height*scale).roundToInt().coerceAtLeast(1)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        val gap=5f*scale;val rect=RectF(gap,gap,w-gap,h-gap);val radius=min(23f*scale,h*.35f)
        if(p.id==10) {
            paint.color=p.panel;canvas.drawRoundRect(rect,radius,radius,paint)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=scale;paint.color=alpha(p.accent,.35f)
            canvas.drawRoundRect(rect,radius,radius,paint);backgrounds.put(key,bitmap);return bitmap
        }
        paint.style=Paint.Style.STROKE;paint.strokeWidth=8f*scale;paint.color=alpha(p.accent,.18f)
        canvas.drawRoundRect(rect,radius,radius,paint)
        paint.strokeWidth=4f*scale;paint.color=alpha(p.accent,.28f);canvas.drawRoundRect(rect,radius,radius,paint)
        paint.style=Paint.Style.FILL
        paint.shader=LinearGradient(0f,0f,0f,h.toFloat(),intArrayOf(p.panel,alpha(p.panel,.92f),p.panel),null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect,radius,radius,paint)
        canvas.save();canvas.clipPath(Path().apply {addRoundRect(rect,radius,radius,Path.Direction.CW)})
        paint.shader=LinearGradient(0f,0f,0f,h.toFloat(),intArrayOf(alpha(Color.WHITE,.28f),Color.TRANSPARENT,Color.TRANSPARENT,alpha(p.reflection,.24f)),floatArrayOf(0f,.30f,.70f,1f),Shader.TileMode.CLAMP)
        canvas.drawRect(rect,paint)
        paint.shader=RadialGradient(w*1.03f,h*.7f,max(w*.23f,h*.7f),intArrayOf(alpha(p.reflection,.65f),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
        canvas.drawRect(rect,paint);canvas.restore()
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1.5f*scale
        paint.shader=LinearGradient(0f,0f,w.toFloat(),h.toFloat(),intArrayOf(alpha(Color.WHITE,.96f),alpha(p.accent,.70f),p.reflection),null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect,radius,radius,paint)
        backgrounds.put(key,bitmap);return bitmap
    }

    fun cover(context: Context,title: String,palette: AbWidgetPalette,large: Boolean): Bitmap {
        val file=AbBookArtwork.find(context,title)
        val targetWidth=if(large)192 else 128;val targetHeight=if(large)276 else 184
        val key="${file?.path}:${file?.lastModified()}:${file?.length()}:${palette.id}:$large"
        covers.get(key)?.let {return it}
        val bitmap=Bitmap.createBitmap(targetWidth,targetHeight,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val rect=RectF(0f,0f,targetWidth.toFloat(),targetHeight.toFloat())
        canvas.clipPath(Path().apply {addRoundRect(rect,12f,12f,Path.Direction.CW)})
        paint.color=palette.panel;canvas.drawRect(rect,paint)
        val source=if(file==null)null else try {
            val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            BitmapFactory.decodeFile(file.path,bounds)
            var sample=1;while(max(bounds.outWidth,bounds.outHeight)/sample>512)sample*=2
            BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply {inSampleSize=sample})
        } catch (_:Exception) {null} catch (_:OutOfMemoryError) {null}
        if(source!=null) {
            val scale=min(targetWidth.toFloat()/source.width,targetHeight.toFloat()/source.height)
            val w=source.width*scale;val h=source.height*scale
            canvas.drawBitmap(source,null,RectF((targetWidth-w)/2,(targetHeight-h)/2,(targetWidth+w)/2,(targetHeight+h)/2),paint)
            source.recycle()
        } else {
            canvas.scale(targetWidth/128f,targetHeight/184f)
            paint.color=palette.accent;paint.style=Paint.Style.STROKE;paint.strokeWidth=4f
            canvas.drawRoundRect(RectF(30f,50f,98f,131f),8f,8f,paint)
            canvas.drawLine(64f,51f,64f,130f,paint)
        }
        covers.put(key,bitmap);return bitmap
    }

    fun progress(p: AbWidgetPalette,value: Float): Bitmap {
        val bitmap=Bitmap.createBitmap(256,6,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color=alpha(p.accent,.22f);canvas.drawRoundRect(RectF(0f,0f,256f,6f),3f,3f,paint)
        paint.color=p.accent;canvas.drawRoundRect(RectF(0f,0f,256f*value.coerceIn(0f,1f),6f),3f,3f,paint)
        return bitmap
    }
    fun icon(name: String,p: AbWidgetPalette,edge: Int,circle: Boolean): Bitmap {
        val bitmap=Bitmap.createBitmap(edge,edge,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
        canvas.scale(edge/48f,edge/48f);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        if(circle) {
            paint.color=p.primary;paint.shader=if(p.id==10)null else RadialGradient(34f,12f,42f,intArrayOf(p.accent,p.primary),floatArrayOf(0f,1f),Shader.TileMode.CLAMP)
            canvas.drawCircle(24f,24f,22f,paint);paint.shader=null
            paint.style=Paint.Style.STROKE;paint.strokeWidth=1.2f;paint.color=p.reflection;canvas.drawCircle(24f,24f,22f,paint)
        }
        paint.shader=null;paint.style=Paint.Style.FILL;paint.color=if(circle)Color.WHITE else p.text
        when(name) {
            "play" -> canvas.drawPath(Path().apply {moveTo(19f,13f);lineTo(35f,24f);lineTo(19f,35f);close()},paint)
            "pause" -> {canvas.drawRect(17f,14f,22f,34f,paint);canvas.drawRect(27f,14f,32f,34f,paint)}
            "previous","next" -> {
                if(name=="previous"){canvas.translate(48f,0f);canvas.scale(-1f,1f)}
                canvas.drawPath(Path().apply {moveTo(14f,14f);lineTo(29f,24f);lineTo(14f,34f);close()},paint)
                canvas.drawRect(31f,14f,34f,34f,paint)
            }
            "timer" -> {canvas.drawCircle(24f,24f,12f,paint);paint.color=if(circle)p.primary else p.panel;canvas.drawCircle(30f,18f,11f,paint)}
            else -> {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;canvas.drawCircle(24f,24f,9f,paint);canvas.drawCircle(24f,24f,3f,paint)
                for(i in 0 until 8){canvas.save();canvas.rotate(i*45f,24f,24f);canvas.drawLine(24f,10f,24f,15f,paint);canvas.restore()}
            }
        }
        return bitmap
    }
}

/** The host may open this both when adding and when reconfiguring an existing widget. */
class AbWidgetConfigureActivity : Activity() {
    private var widgetId=AppWidgetManager.INVALID_APPWIDGET_ID
    private var chosen=0
    private var previewToken=0
    private lateinit var preview: FrameLayout
    private lateinit var message: TextView
    private lateinit var save: Button
    private fun dp(value: Int)=(value*resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        widgetId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId))
        if(!AbGlassWidgets.owns(this,widgetId)){finish();return}
        chosen=(savedInstanceState?.getInt("theme") ?: AbGlassWidgets.preferences(this).getInt("theme_$widgetId",0)).coerceIn(0,10)
        val body=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(16),dp(18),dp(16));setBackgroundColor(0xFF081522.toInt())}
        body.setOnApplyWindowInsetsListener {view,insets ->
            view.setPadding(dp(18),dp(16)+insets.systemWindowInsetTop,dp(18),dp(16)+insets.systemWindowInsetBottom);insets
        }
        body.addView(label("Виджет Audiobook",24))
        body.addView(label("Тема этого виджета",16))
        val provider=AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
        val square=provider==ComponentName(this,AbCoverWidget::class.java)
        val compact=provider==ComponentName(this,AbCompactWidget::class.java)
        preview=FrameLayout(this)
        preview.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        body.addView(preview,LinearLayout.LayoutParams(if(square)dp(190) else LinearLayout.LayoutParams.MATCH_PARENT,
            dp(if(compact)84 else 190)).apply {gravity=Gravity.CENTER_HORIZONTAL;topMargin=dp(12);bottomMargin=dp(12)})
        val choices=RadioGroup(this)
        (listOf("Как в приложении")+abWidgetPalettes.map {it.name}).forEachIndexed {index,name ->
            choices.addView(RadioButton(this).apply {
                id=index+1;text=name;textSize=16f;setTextColor(Color.WHITE);minHeight=dp(48)
            },RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT,RadioGroup.LayoutParams.WRAP_CONTENT))
        }
        choices.check(chosen.coerceIn(0,9)+1)
        choices.setOnCheckedChangeListener {_,checked -> chosen=(checked-1).coerceIn(0,9);refreshPreview()}
        body.addView(choices)
        body.addView(label("Нажатие на обложку открывает плеер. Кнопка луны в большом виджете открывает таймер сна.",13))
        message=label("",13);body.addView(message)
        save=Button(this).apply {text="Сохранить";setOnClickListener {saveWidget()}}
        body.addView(save)
        body.addView(Button(this).apply {text="Отмена";setOnClickListener {finish()}})
        setContentView(ScrollView(this).apply {isFillViewport=true;addView(body)})
        refreshPreview()
    }
    private fun label(value: String,size: Int)=TextView(this).apply {
        text=value;textSize=size.toFloat();setTextColor(Color.WHITE);setPadding(0,dp(5),0,dp(5))
    }
    private fun refreshPreview() {
        val token=++previewToken
        AbGlassWidgets.preview(this,widgetId,chosen) {views ->
            runOnUiThread {
                if(isFinishing || isDestroyed || token!=previewToken)return@runOnUiThread
                preview.removeAllViews()
                if(views!=null)try {
                    // Preview is visual only: do not activate playback/settings PendingIntents.
                    val view=views.apply(this,preview)
                    preview.addView(view,FrameLayout.LayoutParams(-1,-1))
                    preview.addView(View(this),FrameLayout.LayoutParams(-1,-1))
                    preview.getChildAt(1).isClickable=true
                } catch (_:Exception) {message.text="Предпросмотр недоступен; можно сохранить тему и проверить виджет на главном экране."}
            }
        }
    }
    private fun saveWidget() {
        if(!AbGlassWidgets.owns(this,widgetId)){finish();return}
        save.isEnabled=false;message.text="Обновляем виджет…"
        val prefs=AbGlassWidgets.preferences(this)
        val key="theme_$widgetId";val hadTheme=prefs.contains(key);val oldTheme=prefs.getInt(key,0)
        prefs.edit().putInt(key,chosen).apply()
        AbGlassWidgets.update(this,intArrayOf(widgetId)) {ok ->
            if(!ok) {
                val editor=prefs.edit()
                if(hadTheme)editor.putInt(key,oldTheme) else editor.remove(key)
                editor.apply()
            }
            runOnUiThread {
                if(isFinishing || isDestroyed)return@runOnUiThread
                if(ok){setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));finish()}
                else {save.isEnabled=true;message.text="Не удалось обновить виджет. Повторите сохранение."}
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {outState.putInt("theme",chosen);super.onSaveInstanceState(outState)}
    override fun onDestroy() {previewToken++;super.onDestroy()}
}
