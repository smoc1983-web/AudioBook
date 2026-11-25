package com.audiobookdownloader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiobookdownloader.BookRankingRules.Book
import com.audiobookdownloader.BookRankingRules.Feed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val rankingDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Composable
internal fun AbBookRankings(repository: BookRankings, theme: AbTheme, visible: Boolean, search: (Book) -> Unit) {
    var source by rememberSaveable { mutableStateOf(0) }
    var period by rememberSaveable { mutableStateOf(BookRankingPeriod.WEEK) }
    val feed = when {
        source == 0 -> period.authorFeed
        period == BookRankingPeriod.WEEK -> Feed.LITRES_WEEK
        else -> Feed.LITRES_BESTSELLERS
    }
    var retry by remember(feed, period) { mutableStateOf(0) }
    var chart by remember(feed, period) { mutableStateOf(BookRankingView()) }
    LaunchedEffect(repository, feed, period, retry, visible) {
        if (visible) repository.chart(feed, period, retry > 0).collect { chart = it }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AbHeading("Топ книг", theme)
        AbChips(BookRankingPeriod.entries.map { it.label }, period.ordinal, theme) {
            period = BookRankingPeriod.entries[it]
        }
        AbChips(listOf("Author.Today", "Литрес"), source, theme) { source = it }

        val description = when (feed) {
            Feed.LITRES_WEEK -> "Недельная подборка аудиокниг Литрес"
            Feed.LITRES_BESTSELLERS -> "Аудиокниги по местам в списке Литрес · ${chart.observedDays} из ${period.days} дней наблюдений"
            else -> "По популярности на Author.Today"
        }
        Text(description, color = theme.muted, fontSize = 12.sp)
        chart.latestDay?.let {
            Text("Последние данные от ${LocalDate.ofEpochDay(it).format(rankingDateFormat)}",
                color = theme.muted, fontSize = 12.sp)
        }

        if (chart.books.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(chart.books, key = { _, book -> book.url }) { index, book ->
                AbRankingCard(index + 1, book, theme, visible) { search(book) }
            }
        }

        if (chart.loading) Text("Проверяем рейтинг…", color = theme.muted, fontSize = 12.sp)
        if (chart.unavailable) {
            Text(if (chart.books.isEmpty()) "Рейтинг источника сейчас недоступен."
                else "Обновить рейтинг не удалось.", color = theme.muted, fontSize = 12.sp)
            AbButton("Повторить", theme) { retry++ }
        }
    }
}

@Composable
private fun AbRankingCard(rank: Int, book: Book, theme: AbTheme, visible: Boolean, open: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val edge = CoverRequestRules.edge(with(LocalDensity.current) { 150.dp.roundToPx() }, false)
    val coverUrl = CoverRequestRules.sharper(book.cover, edge)
    val imageKey = "book-ranking:$edge:$coverUrl"
    var cover by remember(imageKey) { mutableStateOf(AbCoverStore.peek(imageKey)) }
    LaunchedEffect(imageKey, visible) {
        if (visible && book.cover.isNotEmpty() && cover == null) {
            cover = withContext(Dispatchers.IO) {
                AbCoverStore.load(context, imageKey, "", "", book.cover, "", edge)
            }
        }
    }
    Column(
        Modifier.width(124.dp).abGlass(theme, 15).clickable(
            role = Role.Button, onClickLabel = "Найти книгу и её цикл", onClick = open
        ).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(rank.toString(), color = theme.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().aspectRatio(.72f).clip(RoundedCornerShape(10.dp))
            .background(theme.field), contentAlignment = Alignment.Center) {
            val image = cover
            if (image != null) Image(image.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit,
                filterQuality=androidx.compose.ui.graphics.FilterQuality.Medium)
            else Text(book.title.take(1).uppercase(), color = theme.muted, fontSize = 22.sp)
        }
        Text(book.title, color = theme.text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (book.author.isNotEmpty()) Text(book.author, color = theme.muted, fontSize = 12.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
