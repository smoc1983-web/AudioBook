package com.audiobookdownloader

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha

// A real window attachment is required by WebView's renderer. The parsing
// viewport belongs to the activity, outside Compose and scrolling containers.
private class AbBrowserHost(context:Context,private val viewportWidth:Int,
    private val viewportHeight:Int):android.view.ViewGroup(context) {
    init {
        clipChildren=true
        clipToPadding=true
        alpha=0f
        importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isFocusable=false
        isFocusableInTouchMode=false
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        for(i in 0 until childCount) getChildAt(i).measure(
            android.view.View.MeasureSpec.makeMeasureSpec(viewportWidth,android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(viewportHeight,android.view.View.MeasureSpec.EXACTLY))
        setMeasuredDimension(1,1)
    }
    override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int) {
        for(i in 0 until childCount) getChildAt(i).layout(0,0,viewportWidth,viewportHeight)
    }
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean=false
}

internal fun abCreateBrowser(context: Context,failed:(String)->Unit,
    setup:WebView.()->Unit):WebView? {
    var browser:WebView?=null
    fun failure(error:Throwable):WebView? {
        AbCrashReport.record(context,error,"Создание браузера")
        browser?.let{web ->try{AbWebViews.release(web)}catch(_:RuntimeException){}catch(_:LinkageError){}}
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            failed("Не удалось открыть браузер источника. Ошибка сохранена в отчёте.")
        }
        return null
    }
    return try {
        val web=WebView(context)
        browser=web
        AbWebViews.register(web)
        val metrics=context.resources.displayMetrics
        val width=metrics.widthPixels.coerceAtLeast(320)
        val height=(760*metrics.density).toInt().coerceAtLeast(640)
        AbWebViews.attach(web,width,height)
        web.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height,android.view.View.MeasureSpec.EXACTLY))
        web.layout(0,0,width,height)
        web.onResume()
        web.setup()
        // Source pages only need HTTPS/HTML. Device files are opened by native player/SAF code.
        web.settings.apply {
            allowFileAccess=false
            allowContentAccess=false
            allowFileAccessFromFileURLs=false
            allowUniversalAccessFromFileURLs=false
            mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        AbCrashReport.install(context)
        web
    }catch(error:RuntimeException){failure(error)}
    catch(error:LinkageError){failure(error)}
}

@Composable
internal fun AbParsingBrowser(onFailure:(String)->Unit,setup:WebView.()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val failure by androidx.compose.runtime.rememberUpdatedState(onFailure)
    val alive=remember{booleanArrayOf(true)}
    DisposableEffect(context) {
        alive[0]=true
        val web=abCreateBrowser(context,{if(alive[0])failure(it)},setup)
        onDispose {
            alive[0]=false
            web?.let{AbWebViews.release(it)}
        }
    }
}


internal object AbWebViews {
    private class Entry(val owner:Context) {
        var released=false; var rendererGone=false; var destroyed=false
        var cancel:(()->Unit)?=null
        var host:AbBrowserHost?=null
    }
    private val entries=java.util.WeakHashMap<WebView,Entry>()
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    private fun owner(context: Context):Context {
        var current=context
        while(current is android.content.ContextWrapper && current !is android.app.Activity) {
            val base=current.baseContext
            if(base===current) break
            current=base
        }
        return current
    }
    fun register(web: WebView) { entries[web]=Entry(owner(web.context)) }
    fun attach(web:WebView,width:Int,height:Int) {
        val entry=checkNotNull(entries[web])
        val activity=entry.owner as? android.app.Activity
            ?: error("Для браузера требуется открытый экран приложения")
        val content=activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            ?: error("Не найдено окно браузера")
        val host=AbBrowserHost(activity,width,height)
        entry.host=host
        web.importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        web.isFocusable=false
        web.isFocusableInTouchMode=false
        host.addView(web,android.view.ViewGroup.LayoutParams(width,height))
        content.addView(host,android.widget.FrameLayout.LayoutParams(1,1,
            android.view.Gravity.BOTTOM or android.view.Gravity.END))
    }
    fun onClose(web: WebView,cancel:()->Unit) { entries[web]?.cancel=cancel }
    fun available(web: WebView):Boolean {
        val entry=entries[web] ?: return false
        return !entry.released && !entry.rendererGone && !entry.destroyed
    }
    fun closeAll(context: Context) {
        // Compose's recomposer can already be paused by ON_STOP. Cancel work
        // synchronously; the activity changes the browser generation on start.
        val activity=owner(context)
        entries.keys.toList().filter {entries[it]?.owner===activity}.forEach {web ->
            entries[web]?.cancel?.invoke()
            release(web)
        }
    }
    fun rendererGone(web: WebView) { entries[web]?.rendererGone=true }
    fun release(web: WebView) {
        val entry=entries[web] ?: return
        if(entry.released) return
        entry.released=true
        entry.cancel=null
        if(!entry.rendererGone) {
            web.stopLoading()
            web.onPause()
        }
        // This client also handles a shared renderer dying during disposal.
        web.webViewClient=AbBookWebViewClient { }
        main.post {
            // Only our native host is detached. Compose retains ownership of
            // all its views, so disposal cannot empty an AndroidViewHolder.
            entry.host?.let {host ->
                (host.parent as? android.view.ViewGroup)?.removeView(host)
                host.removeView(web)
            }
            entry.host=null
            if(!web.isAttachedToWindow) destroy(web,entry)
            else web.addOnAttachStateChangeListener(object:android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view:android.view.View) { }
                override fun onViewDetachedFromWindow(view:android.view.View) {
                    web.removeOnAttachStateChangeListener(this)
                    main.post { destroy(web,entry) }
                }
            })
        }
    }
    private fun destroy(web: WebView,entry: Entry) {
        if(entry.destroyed) return
        entry.destroyed=true
        entries.remove(web)
        web.destroy()
    }
}

internal open class AbBookWebViewClient(private val failed:()->Unit):WebViewClient() {
    override fun onRenderProcessGone(view: WebView?,detail: android.webkit.RenderProcessGoneDetail?):Boolean {
        if(view!=null) AbWebViews.rendererGone(view)
        try { failed() } finally { if(view!=null) AbWebViews.release(view) }
        // Android otherwise terminates the app when any affected WebView does
        // not handle renderer loss. The next request creates a fresh browser.
        return true
    }
}
