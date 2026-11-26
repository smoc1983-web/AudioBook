package com.audiobookdownloader

import androidx.compose.foundation.Canvas
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke

internal enum class AbIcon { SEARCH, BOOKS, PLAY, PAUSE, COLLECTIONS, SETTINGS, MORE, BACK,
    NEXT, PREVIOUS, DOWNLOAD, TIMER, BOOKMARK, HEART, PLUS, CLOSE, QUEUE, SYNC, CHECK, UP, DOWN, EQUALIZER }

/** Vector strokes avoid a dependency on material-icons-extended and keep every icon consistent. */
@Composable
internal fun AbIcon(icon: AbIcon, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(23.dp)) {
        val scale = size.minDimension / 24f
        val stroke = 1.7f * scale
        fun point(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x * scale, y * scale)
        fun line(x: Float, y: Float, xx: Float, yy: Float) =
            drawLine(color, point(x, y), point(xx, yy), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
        fun path(points: List<Pair<Float, Float>>, closed: Boolean = false, fill: Boolean = false) {
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(p.first * scale, p.second * scale)
                else path.lineTo(p.first * scale, p.second * scale)
            }
            if (closed) path.close()
            drawPath(path, color, style = if (fill) androidx.compose.ui.graphics.drawscope.Fill
                else androidx.compose.ui.graphics.drawscope.Stroke(stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        when (icon) {
            AbIcon.SEARCH -> {
                drawCircle(color, 7f * scale, point(10f, 10f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                line(15f, 15f, 21f, 21f)
            }
            AbIcon.PLAY -> path(listOf(8f to 4f, 20f to 12f, 8f to 20f), true, true)
            AbIcon.PAUSE -> { line(8f, 5f, 8f, 19f); line(16f, 5f, 16f, 19f) }
            AbIcon.NEXT, AbIcon.PREVIOUS -> {
                val flip = icon == AbIcon.PREVIOUS
                fun x(v: Float) = if (flip) 24f - v else v
                path(listOf(x(5f) to 5f, x(16f) to 12f, x(5f) to 19f), true, true)
                line(x(19f), 5f, x(19f), 19f)
            }
            AbIcon.BOOKS -> {
                path(listOf(12f to 5f, 3f to 3f, 3f to 19f, 12f to 21f, 21f to 19f, 21f to 3f, 12f to 5f))
                line(12f, 5f, 12f, 21f)
            }
            AbIcon.COLLECTIONS -> {
                for (x in listOf(3f, 14f)) for (y in listOf(3f, 14f)) {
                    drawRoundRect(color, point(x,y), androidx.compose.ui.geometry.Size(7f*scale,7f*scale),
                        androidx.compose.ui.geometry.CornerRadius(scale,scale),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                }
            }
            AbIcon.SETTINGS -> {
                drawCircle(color, 3f*scale, point(12f,12f), style=androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawCircle(color, 8f*scale, point(12f,12f), style=androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                for (i in 0..7) {
                    val a = i * Math.PI / 4
                    line((12+8*kotlin.math.cos(a)).toFloat(), (12+8*kotlin.math.sin(a)).toFloat(),
                        (12+11*kotlin.math.cos(a)).toFloat(), (12+11*kotlin.math.sin(a)).toFloat())
                }
            }
            AbIcon.MORE -> for (y in listOf(5f,12f,19f)) drawCircle(color,1.5f*scale,point(12f,y))
            AbIcon.BACK -> { line(16f,4f,8f,12f);line(8f,12f,16f,20f) }
            AbIcon.DOWNLOAD -> {
                line(12f,3f,12f,15f);line(7f,11f,12f,16f);line(12f,16f,17f,11f)
                path(listOf(4f to 17f,4f to 21f,20f to 21f,20f to 17f))
            }
            AbIcon.EQUALIZER -> {
                for ((x,y) in listOf(5f to 8f,12f to 16f,19f to 10f)) {
                    line(x,3f,x,y-2f);line(x,y+2f,x,21f)
                    drawCircle(color,2f*scale,point(x,y),style=androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                }
            }
            AbIcon.TIMER -> {
                drawCircle(color,8f*scale,point(12f,14f),style=androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                line(9f,2f,15f,2f);line(12f,2f,12f,6f);line(12f,9f,12f,14f);line(12f,14f,16f,16f)
            }
            AbIcon.BOOKMARK -> path(listOf(6f to 3f,18f to 3f,18f to 21f,12f to 17f,6f to 21f),true)
            AbIcon.HEART -> {
                val p=androidx.compose.ui.graphics.Path()
                p.moveTo(12f*scale,21f*scale)
                p.cubicTo(-7f*scale,8f*scale,5f*scale,-3f*scale,12f*scale,6f*scale)
                p.cubicTo(19f*scale,-3f*scale,31f*scale,8f*scale,12f*scale,21f*scale)
                drawPath(p,color,style=androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            }
            AbIcon.PLUS -> {line(12f,4f,12f,20f);line(4f,12f,20f,12f)}
            AbIcon.CLOSE -> {line(5f,5f,19f,19f);line(5f,19f,19f,5f)}
            AbIcon.QUEUE -> {line(3f,5f,21f,5f);line(3f,11f,15f,11f);line(3f,17f,11f,17f);line(19f,13f,19f,21f);line(15f,17f,23f,17f)}
            AbIcon.SYNC -> {
                drawArc(color,35f,285f,false,point(4f,4f),androidx.compose.ui.geometry.Size(16f*scale,16f*scale),
                    style=androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                path(listOf(15f to 2f,21f to 4f,20f to 10f))
            }
            AbIcon.CHECK -> path(listOf(4f to 12f,9f to 18f,21f to 5f))
            AbIcon.UP -> {line(12f,20f,12f,4f);line(5f,11f,12f,4f);line(12f,4f,19f,11f)}
            AbIcon.DOWN -> {line(12f,4f,12f,20f);line(5f,13f,12f,20f);line(12f,20f,19f,13f)}
        }
    }
}

@Composable
internal fun AbIconButton(icon: AbIcon, label: String, theme: AbTheme,
    modifier: Modifier = Modifier, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    Box(modifier.size(48.dp).then(if(active) Modifier.abGlass(theme,24,selected=true,primary=true)
            else Modifier.clip(RoundedCornerShape(16.dp)))
        .semantics { contentDescription = label; role = androidx.compose.ui.semantics.Role.Button }
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        AbIcon(icon, if (!enabled) theme.muted.copy(alpha=.4f) else if (active) theme.onAccent else theme.text)
    }
}

@Composable
internal fun AbButton(label: String, theme: AbTheme, modifier: Modifier = Modifier,
    primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().abGlass(theme,17,field=true,primary=primary,selected=primary)
        .alpha(if (enabled) 1f else .45f)
        .semantics { role = androidx.compose.ui.semantics.Role.Button }
        .clickable(enabled=enabled,onClick=onClick).padding(horizontal=16.dp,vertical=15.dp),
        contentAlignment=Alignment.Center) {
        Text(label, color=if(primary) theme.onAccent else theme.text,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
internal fun AbGenreAction(label: String, theme: AbTheme, icon: AbIcon, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=40.dp).abGlass(theme,12,field=true)
        .semantics { role=androidx.compose.ui.semantics.Role.Button }
        .clickable(onClick=onClick).padding(horizontal=12.dp,vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,color=theme.text,fontSize=12.sp,fontWeight=FontWeight.Medium,
            maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
        AbIcon(icon,theme.accent,Modifier.size(18.dp))
    }
}

@Composable
internal fun AbChips(labels: List<String>, selected: Int, theme: AbTheme, onSelect: (Int)->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        labels.forEachIndexed { index,label ->
            Text(label,color=if(index==selected) theme.onAccent else theme.text,fontSize=13.sp,
                fontWeight=FontWeight.Medium,modifier=Modifier.abGlass(theme,14,field=true,
                    primary=index==selected,selected=index==selected)
                    .semantics { this.selected = index == selected; role = androidx.compose.ui.semantics.Role.Tab }
                    .clickable { onSelect(index) }.padding(horizontal=17.dp,vertical=13.dp))
        }
    }
}

@Composable
internal fun AbHeading(title: String, theme: AbTheme, count: String = "", onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=theme.text,fontSize=19.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
        if(count.isNotBlank()) Text(count,color=theme.accent,fontSize=12.sp,
            modifier=if(onClick==null) Modifier else Modifier.clickable(onClick=onClick).padding(8.dp))
    }
}

@Composable
internal fun AbProgress(value: Float, theme: AbTheme, modifier: Modifier=Modifier) {
    Box(modifier.height(3.dp).clip(RoundedCornerShape(3.dp)).background(theme.border)) {
        if(value>0f) Box(Modifier.fillMaxWidth(value.coerceIn(0f,1f)).height(3.dp).background(theme.accent))
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun AbDialog(title: String, theme: AbTheme, dismiss: ()->Unit,
    footer: @Composable ColumnScope.()->Unit = {},
    content: @Composable ColumnScope.()->Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest=dismiss,
        properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxWidth().heightIn(max=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp*.90f)
            .padding(horizontal=15.dp,vertical=18.dp)
            .abGlass(theme,25,raised=true,opaque=true)
            .padding(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(title,color=theme.text,fontSize=21.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                AbIconButton(AbIcon.CLOSE,"Закрыть",theme,onClick=dismiss)
            }
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
            footer()
        }
    }
}

@Composable
internal fun AbTextField(value: String, hint: String, theme: AbTheme, onChange: (String)->Unit,
    submit: (() -> Unit)? = null, multiline:Boolean=false, numeric:Boolean=false) {
    Row(Modifier.fillMaxWidth().abGlass(theme,17,field=true)
        .padding(start=14.dp,end=if(submit==null)14.dp else 6.dp,
            top=if(submit==null)15.dp else 6.dp,bottom=if(submit==null)15.dp else 6.dp),
        verticalAlignment=Alignment.CenterVertically) {
        BasicTextField(value,onChange,singleLine=!multiline,maxLines=if(multiline)5 else 1,textStyle=TextStyle(color=theme.text,fontSize=15.sp),
            cursorBrush=androidx.compose.ui.graphics.SolidColor(theme.accent),
            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=if(numeric)
                androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text,imeAction=if(submit!=null)
                androidx.compose.ui.text.input.ImeAction.Search else androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={submit?.invoke()}),
            modifier=Modifier.weight(1f).semantics{contentDescription=hint},
            decorationBox={ field ->
                Box {
                    if(value.isEmpty()) Text(hint,color=theme.muted,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    field()
                }
            })
        if(submit!=null) Box(Modifier.padding(start=8.dp).heightIn(min=44.dp)
            .abGlass(theme,12,primary=true,selected=true)
            .semantics {contentDescription="Найти книгу";role=androidx.compose.ui.semantics.Role.Button}
            .clickable(onClick=submit).padding(horizontal=12.dp),contentAlignment=Alignment.Center) {
            Text("Найти",color=theme.onAccent,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun AbOptions(labels: List<String>,selected: Int,theme: AbTheme,enabled: Boolean=true,onSelect:(Int)->Unit) {
    labels.withIndex().toList().chunked(3).forEach {row ->
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            row.forEach {(index,label) ->
                Box(Modifier.weight(1f).abGlass(theme,13,field=true,selected=index==selected)
                    .alpha(if(enabled) 1f else .45f)
                    .semantics{this.selected=index==selected;role=androidx.compose.ui.semantics.Role.Button}
                    .clickable(enabled=enabled){onSelect(index)}.padding(horizontal=5.dp,vertical=13.dp),
                    contentAlignment=Alignment.Center) {
                    Text(label,color=if(index==selected) theme.accent else theme.text,fontSize=12.sp,
                        fontWeight=FontWeight.Medium,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            repeat(3-row.size){Spacer(Modifier.weight(1f))}
        }
    }
}
