package com.audiobookdownloader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

@Composable
internal fun AbSearchFilterDialog(
    narrator: String, seriesOnly: Boolean, localOnly: Boolean, hidePreviews: Boolean,
    theme: AbTheme, dismiss: () -> Unit, author:String="",genre:String="",exact:Boolean=false, apply: (String, Boolean, Boolean, Boolean,String,String,Boolean) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(narrator) }
    var series by rememberSaveable { mutableStateOf(seriesOnly) }
    var local by rememberSaveable { mutableStateOf(localOnly) }
    var hideDemo by rememberSaveable { mutableStateOf(hidePreviews) }

    var authorName by rememberSaveable{mutableStateOf(author)}
    var genreName by rememberSaveable{mutableStateOf(genre)}
    var exactName by rememberSaveable{mutableStateOf(exact)}
    AbDialog("Фильтры поиска", theme, dismiss) {
        AbTextField(authorName,"Автор",theme,{authorName=it.take(100)})
        AbTextField(genreName,"Жанр",theme,{genreName=it.take(100)})
        SearchFilterOption("Точное название книги или цикла",exactName,theme){exactName=it}
        Text("Чтец", color = theme.text, fontSize = 13.sp)
        AbTextField(name, "Имя или фамилия чтеца", theme, { name = it.take(100) })
        SearchFilterOption("Только книги из серий", series, theme) { series = it }
        SearchFilterOption("Есть главы этого издания на устройстве", local, theme) { local = it }
        SearchFilterOption("Скрывать демо и возможные короткие фрагменты", hideDemo, theme) { hideDemo = it }
        AbButton("Применить", theme, primary = true) { apply(name, series, local, hideDemo,authorName,genreName,exactName) }
        AbButton("Очистить фильтры", theme) {
            name = ""
            series = false
            local = false
            hideDemo = false
            authorName="";genreName="";exactName=false
        }
        AbButton("Отмена", theme, onClick = dismiss)
    }
}

@Composable
private fun SearchFilterOption(label: String, checked: Boolean, theme: AbTheme, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked, onCheckedChange = null, colors = CheckboxDefaults.colors(
            checkedColor = theme.accent, checkmarkColor = theme.onAccent, uncheckedColor = theme.muted
        ))
        Text(label, color = theme.text, fontSize = 13.sp)
    }
}

@Composable
internal fun AbSearchFilterDialog(narrator:String,seriesOnly:Boolean,localOnly:Boolean,hidePreviews:Boolean,theme:AbTheme,dismiss:()->Unit,apply:(String,Boolean,Boolean,Boolean)->Unit){
    AbSearchFilterDialog(narrator,seriesOnly,localOnly,hidePreviews,theme,dismiss,author="",genre="",exact=false){n,s,l,h,_,_,_->apply(n,s,l,h)}
}
