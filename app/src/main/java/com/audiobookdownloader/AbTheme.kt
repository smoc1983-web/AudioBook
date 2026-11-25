package com.audiobookdownloader

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke

internal data class AbTheme(
    val id: Int, val name: String, val light: Boolean,
    val background: Color, val panel: Color, val field: Color, val accent: Color,
    val text: Color, val muted: Color, val border: Color,
    val primary: Color, val onAccent: Color, val reflection: Color, val image: Int,
    val flat:Boolean=false,val dim:Float=0f,val cardOpacity:Float=-1f
) {
    val error: Color get()=if(light) Color(0xFFB12D45) else Color(0xFFFFB4BE)
}

// IDs belong exclusively to the new collection, in the order of the approved previews.
internal val AbGlassThemes = listOf(
    AbTheme(1,"Перламутр",true,
        Color(0xFFF4EEE6),Color(0xF0F5EFE8),Color(0xF5F9F5F0),Color(0xFF4D42B8),
        Color(0xFF202836),Color(0xFF474F5C),Color(0xFFBCB9C5),
        Color(0xFF5B32D2),Color.White,Color(0xFF8DDED7),R.drawable.ab_glass_pearl),
    AbTheme(2,"Ледяная ночь",false,
        Color(0xFF061522),Color(0xE0103151),Color(0xE6184266),Color(0xFF67E3F4),
        Color(0xFFF5FAFF),Color(0xFFC7D8E5),Color(0xFF476578),
        Color(0xFF006B9D),Color.White,Color(0xFF81F0DE),R.drawable.ab_glass_night),
    AbTheme(3,"Сиреневые сумерки",false,
        Color(0xFF190F2D),Color(0xE0301854),Color(0xE63F216A),Color(0xFFD8BBFF),
        Color(0xFFFCF7FF),Color(0xFFD3C6E3),Color(0xFF716083),
        Color(0xFF6533CA),Color.White,Color(0xFFE3A2E9),R.drawable.ab_glass_lilac),
    AbTheme(4,"Янтарь",false,
        Color(0xFF21150F),Color(0xE0452917),Color(0xE64F3019),Color(0xFFFFD08C),
        Color(0xFFFFF9F0),Color(0xFFE0CDBC),Color(0xFF80664B),
        Color(0xFF965408),Color.White,Color(0xFFFFD5A2),R.drawable.ab_glass_amber),
    AbTheme(5,"Изумруд",false,
        Color(0xFF061E1B),Color(0xE00B3E35),Color(0xE60B493E),Color(0xFF7AE5C1),
        Color(0xFFF2FFF9),Color(0xFFC8E3D6),Color(0xFF477B6B),
        Color(0xFF00745B),Color.White,Color(0xFFAAEEDB),R.drawable.ab_glass_emerald),
    AbTheme(6,"Чёрный обсидиан",false,
        Color(0xFF030405),Color(0xF00C1014),Color(0xF013181D),Color(0xFFD4E2ED),
        Color(0xFFF7F9FC),Color(0xFFBCC4CD),Color(0xFF59636C),
        Color(0xFF353F49),Color.White,Color(0xFF9CADBC),R.drawable.ab_glass_obsidian),
    AbTheme(7,"Чёрный оникс",false,
        Color(0xFF050403),Color(0xF012100D),Color(0xF01B1712),Color(0xFFFFD49B),
        Color(0xFFFFFAF2),Color(0xFFD0C3B1),Color(0xFF695640),
        Color(0xFF624017),Color.White,Color(0xFFD5AA70),R.drawable.ab_glass_onyx),
    AbTheme(8,"Чёрный лёд",false,
        Color(0xFF020406),Color(0xF0091016),Color(0xF0101B23),Color(0xFF92E4FF),
        Color(0xFFF3FAFF),Color(0xFFB8CBD8),Color(0xFF466271),
        Color(0xFF17435A),Color.White,Color(0xFF77BCD7),R.drawable.ab_glass_ice),
    AbTheme(9,"Чёрный аметист",false,
        Color(0xFF050307),Color(0xF0100C17),Color(0xF01A1223),Color(0xFFD9B5FF),
        Color(0xFFFDF7FF),Color(0xFFCABBD9),Color(0xFF614A75),
        Color(0xFF472460),Color.White,Color(0xFFB591D9),R.drawable.ab_glass_amethyst),
    AbTheme(10,"Графит · без свечения",false,
        Color(0xFF101214),Color(0xFF1D2226),Color(0xFF252C31),Color(0xFF81E0C0),
        Color(0xFFF4F7F9),Color(0xFFCED6DC),Color(0xFF56646C),
        Color(0xFF215947),Color.White,Color(0xFF81E0C0),0,flat=true)
)

internal fun abTheme(skin: Int): AbTheme = AbGlassThemes.firstOrNull {it.id==skin} ?: AbGlassThemes[1]

internal fun abReadGlassSkin(prefs: android.content.SharedPreferences): Int {
    if(prefs.contains("glass_theme"))return prefs.getInt("glass_theme",2).takeIf {it in 1..10} ?: 2
    // Preserve light/dark preference when retiring the old theme collection.
    return when(prefs.getInt("app_skin",1)) {4->1;2,5->5;3->3;else->2}
}

/** Bright edge lighting stays away from the central text area; brushes are cached per size. */
internal fun Modifier.abGlass(theme: AbTheme, radius: Int = 18, field: Boolean = false,
    selected: Boolean = false, primary: Boolean = false, raised: Boolean = false,
    opaque: Boolean = false): Modifier {
    val shape=RoundedCornerShape(radius.dp)
    if(theme.flat) {
        val base=if(primary)theme.primary else if(field)theme.field else theme.panel
        return this.clip(shape).background(base.copy(alpha=if(opaque)1f else if(theme.cardOpacity>=0)theme.cardOpacity else 1f))
            .border(1.dp,if(selected)theme.accent else theme.border.copy(alpha=.45f),shape)
    }
    val black=theme.id>=6
    return this.shadow(if(raised) 7.dp else 0.dp,shape,clip=false).drawWithCache {
        val corner=CornerRadius(radius.dp.toPx())
        val halo=theme.accent
        onDrawBehind {
            if(size.width>0f && size.height>0f) {
                drawRoundRect(halo.copy(alpha=if(black) {if(selected || primary).10f else .025f} else if(selected || primary).18f else .10f),
                    cornerRadius=corner,style=Stroke(8.dp.toPx()))
                drawRoundRect(halo.copy(alpha=if(black) {if(selected || primary).22f else .06f} else if(selected || primary).30f else .18f),
                    cornerRadius=corner,style=Stroke(4.dp.toPx()))
            }
        }
    }.clip(shape).drawWithCache {
        val base=if(primary)theme.primary else if(field)theme.field else theme.panel
        val fill=if(opaque)base.copy(alpha=1f) else if(theme.cardOpacity>=0)base.copy(alpha=theme.cardOpacity) else base
        val surface=Brush.verticalGradient(listOf(fill,
            fill.copy(alpha=if(opaque)1f else (fill.alpha-.025f).coerceAtLeast(if(theme.cardOpacity<0).86f else .60f)),fill))
        val sheen=Brush.verticalGradient(
            0f to Color.White.copy(alpha=if(black).12f else if(primary).30f else if(theme.light).60f else .25f),
            .30f to Color.Transparent,.70f to Color.Transparent,
            1f to theme.reflection.copy(alpha=if(black).08f else if(primary).28f else .24f))
        val edge=Brush.linearGradient(listOf(
            Color.White.copy(alpha=if(black).50f else .96f),theme.accent.copy(alpha=if(selected).95f else if(black).30f else .60f),
            theme.reflection.copy(alpha=if(black).42f else .96f)))
        val glow=Brush.radialGradient(listOf(theme.reflection.copy(alpha=if(black) {if(primary || selected).22f else .08f} else if(primary || selected).72f else .48f),Color.Transparent),
            center=Offset(size.width*1.04f,size.height*.70f),radius=maxOf(size.width*.24f,size.height*.75f,1f))
        val light=Brush.radialGradient(listOf(Color.White.copy(alpha=if(theme.light).60f else if(black).08f else .20f),Color.Transparent),
            center=Offset(-size.width*.08f,0f),radius=maxOf(size.width*.33f,1f))
        val stroke=1.4.dp.toPx()
        val corner=(radius.dp.toPx()-stroke/2).coerceAtLeast(0f)
        onDrawBehind {
            drawRect(surface)
            drawRect(sheen)
            drawRect(glow)
            drawRect(light)
            if(size.width>stroke && size.height>stroke) drawRoundRect(edge,
                topLeft=Offset(stroke/2,stroke/2),size=Size(size.width-stroke,size.height-stroke),
                cornerRadius=CornerRadius(corner,corner),style=Stroke(stroke))
        }
    }
}

@Composable
internal fun AbThemeBackdrop(theme: AbTheme, uri: String?, modifier: Modifier = Modifier) {
    val custom=uri!=null
    Box(modifier.background(theme.background)) {
        if(theme.image!=0 || uri!=null)AbBackgroundImage(theme.image,uri,Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            theme.background.copy(alpha=if(custom).38f else .20f),
            theme.background.copy(alpha=if(custom).24f else .06f),
            theme.background.copy(alpha=if(custom).42f else .24f)))))
        if(theme.dim>0f)Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=theme.dim)))
    }
}

@Composable
private fun AbBackgroundImage(resource: Int,uri: String?,modifier: Modifier) {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val address=uri ?: "android.resource://${context.packageName}/$resource"
    val request="background:$address"
    var bitmap by remember(request) {mutableStateOf(AbCoverStore.peek(request))}
    LaunchedEffect(request) {
        bitmap=withContext(Dispatchers.IO) {
            AbCoverStore.load(context,request,address,"","","",1280)
        }
    }
    bitmap?.let {Image(it.asImageBitmap(),null,contentScale=ContentScale.Crop,modifier=modifier)}
}
