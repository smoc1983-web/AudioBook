package com.audiobookdownloader

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset

internal const val AB_PRODUCT_NAME=BuildConfig.PRODUCT_NAME

@Composable
internal fun AbModernApp(data: AbScreenData, actions: AbActions, sync: LibrarySyncState,
    prefs: android.content.SharedPreferences,foreground: Boolean,browserGeneration: Int,automaticCatalogs: Boolean) {
    val theme=remember(data.skin,data.version) {abTheme(data.skin).copy(
        dim=prefs.getFloat("appearance_dim",0f).coerceIn(0f,.8f),
        cardOpacity=prefs.getFloat("appearance_card_opacity",-1f).let{if(it<0)-1f else it.coerceIn(.65f,1f)})}
    val baseDensity=androidx.compose.ui.platform.LocalDensity.current
    val textScale=prefs.getFloat("appearance_text_scale",1f).coerceIn(.85f,1.5f)
    val readableDensity=remember(baseDensity,textScale){androidx.compose.ui.unit.Density(baseDensity.density,baseDensity.fontScale*textScale)}
    val activity=androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    LaunchedEffect(theme.light,activity) {
        activity?.let {
            val controller=androidx.core.view.WindowCompat.getInsetsController(it.window,it.window.decorView)
            controller.isAppearanceLightStatusBars=theme.light
            controller.isAppearanceLightNavigationBars=theme.light
        }
    }
    var settings by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<AbSettingsSection?>(null) }
    val widgetRoute by AbWidgetRoutes.requests.collectAsState()
    LaunchedEffect(widgetRoute.serial) {
        if(widgetRoute.target=="timer")settings=AbSettingsSection.TIMER
        else if(widgetRoute.target=="player" || widgetRoute.target=="library" || widgetRoute.target=="imported")settings=null
    }
    var downloads by remember { mutableStateOf(false) }
    var libraryAccess by remember { mutableStateOf(false) }
    LaunchedEffect(widgetRoute.serial){if(widgetRoute.target=="library"){downloads=false;libraryAccess=false}}
    var menuBook by remember { mutableStateOf<BookRef?>(null) }
    var shelfBook by remember { mutableStateOf<BookRef?>(null) }
    var catalogue by remember { mutableStateOf<Map<String,SiteSearchResult>>(emptyMap()) }
    var crashReportRequest by remember {mutableStateOf(0)}
    var catalogueRequested by remember(automaticCatalogs) {mutableStateOf(automaticCatalogs)}
    var catalogueReady by remember(browserGeneration) {mutableStateOf(false)}
    LaunchedEffect(foreground,browserGeneration,catalogueRequested) {
        catalogueReady=false
        if(foreground && catalogueRequested) {
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            delay(1500L)
            catalogueReady=true
        }
    }
    LaunchedEffect(data.page,data.searchActive,data.searching) {
        AbCrashReport.screen="${data.page} · поиск=${data.searchActive} · загрузка=${data.searching}"
    }
    val housekeepingContext=androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(data.searching,data.searchScreenId,data.page) {
        if(!data.searching)AbLibraryHousekeeping.request(housekeepingContext)
    }
    // The loader is independent of lazy-list item visibility and scrolling.
    if(foreground && catalogueReady && catalogueRequested && data.page==AppPage.SEARCH && !data.searchActive &&
        data.onlineBook==null && data.results.isEmpty() && !data.searching && !data.cycleBrowsing) {
        key(browserGeneration,data.searchSource) {AbDiscoveryLoaders(catalogue,
            if(data.searchSource.isBlank())AbBookSources.enabled(prefs) else listOf(data.searchSource)) {site,result ->
            result.books.forEach {abRememberCatalog(prefs,it)}
            catalogue=catalogue+(site to result)
        }}
    }
    val state=data.playback
    var searchMiniHidden by androidx.compose.runtime.saveable.rememberSaveable(
        state.bookTitle,state.sourceType,state.tracks.firstOrNull()?.editionId.orEmpty()) {mutableStateOf(false)}
    LaunchedEffect(data.page,state.hasSelection) {
        if(data.page==AppPage.PLAYER||!state.hasSelection)searchMiniHidden=false
    }
    val pages=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    androidx.activity.compose.BackHandler(enabled=data.page!=AppPage.SEARCH || data.searchActive) {
        if(data.page==AppPage.SEARCH) actions.backSearch() else actions.navigate(AppPage.SEARCH)
    }
    val textStyle=androidx.compose.material3.LocalTextStyle.current
    val readableTextStyle=remember(theme.id,textStyle) {if(theme.flat)textStyle.copy(shadow=null) else textStyle.copy(shadow=androidx.compose.ui.graphics.Shadow(
        color=if(theme.light) Color.White.copy(alpha=.60f) else Color.Black.copy(alpha=.78f),
        offset=Offset(0f,1f),blurRadius=3f))}
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides readableDensity,
        LocalAbBookCardContext provides BookCardContext(actions.openRef,actions.downloadEdition,sync.books.value,data.downloadedIndex,data.downloads,data.version),
        LocalAbCoverDataVersion provides data.version,
        LocalAbVisible provides foreground,
        androidx.compose.material3.LocalTextStyle provides readableTextStyle) {
    Box(Modifier.fillMaxSize().background(theme.background)) {
        AbThemeBackdrop(theme,data.backgroundUri,Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            AbPageTurn(data.page,actions.navigate,Modifier.weight(1f).fillMaxWidth(),
                sheetColor=if(theme.light)Color(0xffe3edf1) else Color(0xffa7c4d0)) { shownPage ->
                Box(Modifier.fillMaxSize().background(theme.background)) {
                    AbThemeBackdrop(theme,data.backgroundUri,Modifier.fillMaxSize())
                    Column(Modifier.fillMaxSize()) {
            if(shownPage!=AppPage.PLAYER) {
                Row(Modifier.fillMaxWidth().padding(start=20.dp,end=10.dp,top=8.dp,bottom=4.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable(onClick=actions.home),verticalAlignment=Alignment.CenterVertically) {
                        Text(AB_PRODUCT_NAME,color=theme.text,fontSize=23.sp,fontWeight=FontWeight.Bold)
                    }
                    Box {
                        val activeDownloads=data.downloads.count{it.state==TrackState.DOWNLOADING}
                        AbIconButton(AbIcon.DOWNLOAD,"Загрузки · $activeDownloads",theme,active=activeDownloads>0,onClick={downloads=true})
                        if(activeDownloads>0)Text("$activeDownloads",color=theme.accent,fontSize=10.sp,fontWeight=FontWeight.Bold,
                            modifier=Modifier.align(Alignment.TopEnd).padding(end=3.dp,top=2.dp))
                    }

                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                pages.SaveableStateProvider(shownPage.name) {
                when(shownPage) {
                    AppPage.SEARCH -> AbSearchScreen(data,actions,sync.books.value,prefs,theme,
                        onMenu={menuBook=it},catalogue=catalogue,catalogueRequested=catalogueRequested,
                        onLoadCatalogue={catalogue=emptyMap();catalogueRequested=true})
                    AppPage.LIBRARY -> AbLibraryScreen(data,actions,sync,prefs,theme,
                        onMenu={menuBook=it},onAccess={libraryAccess=true})
                    AppPage.COLLECTIONS -> AbCollectionsScreen(data,actions,sync.books.value,prefs,theme)
                    AppPage.PLAYER -> AbPlayerScreen(data,actions,sync.books.value,prefs,theme,
                        onSettings={settings=it},onMenu={menuBook=it},onShelf={shelfBook=it})
                }
                }
            }
            if(state.hasSelection && shownPage!=AppPage.PLAYER) {
                if(shownPage==AppPage.SEARCH) {
                    if(!searchMiniHidden)AbSwipeMiniPlayer(onDismiss={searchMiniHidden=true}) {
                        AbMiniPlayer(data,actions,sync.books.value,prefs,theme)
                    }
                } else AbMiniPlayer(data,actions,sync.books.value,prefs,theme)
            }
                    }
                }
            }
            AbBottomNavigation(data.page,theme,actions.navigate){settings=if(data.page==AppPage.PLAYER) AbSettingsSection.PLAYER_HOME else AbSettingsSection.HOME}
        }
    }
    settings?.let { section -> AbSettingsDialog(data,prefs,theme,actions,sync,section,onCrashReport={
        crashReportRequest++
    }) { settings=null } }
    AbCrashReports(theme,crashReportRequest)
    if(downloads) AbPersistentQueueDialog(theme,foreground) { downloads=false }
    if(libraryAccess) AbDialog("Библиотека на устройстве",theme,{libraryAccess=false}) {
        Text("Найдём сохранившиеся главы и подключим папку после переустановки.",color=theme.muted,fontSize=14.sp)
        LibrarySyncControls(sync.isSyncing,sync.message,{sync.requestSync()},theme.muted,theme.accent,sync.rootIssues)
        AbButton(if(data.coverRepairRunning) "Восстанавливаем обложки…" else "Восстановить обложки",theme,
            enabled=!data.coverRepairRunning && !sync.isSyncing && sync.books.value.isNotEmpty(),onClick=actions.repairCovers)
        if(data.coverRepairStatus.isNotBlank()) Text(data.coverRepairStatus,color=theme.muted,fontSize=13.sp,lineHeight=20.sp)
    }
    menuBook?.let { ref ->
        AbBookMenu(ref,abFindLocal(sync.books.value,ref.title),prefs,theme,actions,
            version=data.version,index=data.downloadedIndex,downloads=data.downloads,
            onShelf={menuBook=null;shelfBook=ref},dismiss={menuBook=null})
    }
    shelfBook?.let { ref -> AbShelfPicker(ref,prefs,theme,data.version) { shelfBook=null } }
    }
}
