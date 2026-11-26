package com.audiobookdownloader


/*
 * =============================================================
 * НАЗВАНИЯ
 * =============================================================
 */

fun cleanBookTitle(
    title: String
): String {

    var result =
        title.trim()

    result =
        result.replace(
            "Аудиокнига ",
            "",
            ignoreCase =
                true
        )

    val garbage =
        listOf(
            " заблудись бесплатно",
            " слушать онлайн бесплатно",
            " слушать бесплатно"
        )

    garbage.forEach { text ->

        val position =
            result.indexOf(
                text,
                ignoreCase =
                    true
            )

        if (
            position > 0
        ) {

            result =
                result.substring(
                    0,
                    position
                )
        }
    }

    val authorIndex =
        result.indexOf(
            " - автор",
            ignoreCase =
                true
        )

    if (
        authorIndex > 0
    ) {

        result =
            result.substring(
                0,
                authorIndex
            )
    }

    result =
        result.trim()

    if (
        result.isBlank()
    ) {

        return "Audiobook"
    }

    return result
}

fun safeFileName(
    value: String
): String {

    var result =
        value.trim()

    result =
        result.replace(
            Regex(
                """[\\/:*?"<>|]"""
            ),
            "_"
        )

    if (
        result.isBlank()
    ) {

        return "track"
    }

    return result
}

/*
 * =============================================================
 * ВРЕМЯ
 * =============================================================
 */

fun formatDuration(
    seconds: Int
): String {

    if (
        seconds <= 0
    ) {

        return "--:--"
    }

    val hours =
        seconds / 3600

    val minutes =
        (
            seconds % 3600
            ) / 60

    val secs =
        seconds % 60

    return if (
        hours > 0
    ) {

        String.format(
            "%d:%02d:%02d",
            hours,
            minutes,
            secs
        )

    } else {

        String.format(
            "%02d:%02d",
            minutes,
            secs
        )
    }
}

fun formatFileSize(
    bytes: Long
): String {

    if (bytes <= 0L) return "0 МБ"

    val mb =
        bytes.toDouble() /
            1024.0 /
            1024.0

    return if (mb >= 1024.0) {
        String.format(
            java.util.Locale.US,
            "%.2f ГБ",
            mb / 1024.0
        )
    } else {
        String.format(
            java.util.Locale.US,
            "%.1f МБ",
            mb
        )
    }
}

fun formatMillis(
    millis: Int
): String {

    if (
        millis < 0
    ) {

        return "00:00"
    }

    val totalSeconds =
        millis / 1000

    val hours =
        totalSeconds / 3600

    val minutes =
        (
            totalSeconds % 3600
            ) / 60

    val seconds =
        totalSeconds % 60

    return if (
        hours > 0
    ) {

        String.format(
            "%d:%02d:%02d",
            hours,
            minutes,
            seconds
        )

    } else {

        String.format(
            "%02d:%02d",
            minutes,
            seconds
        )
    }
}

fun formatSleepTime(
    seconds: Int
): String {

    val safe =
        seconds.coerceAtLeast(
            0
        )

    val hours =
        safe / 3600

    val minutes =
        (
            safe % 3600
            ) / 60

    val secs =
        safe % 60

    return if (
        hours > 0
    ) {

        String.format(
            "%d:%02d:%02d",
            hours,
            minutes,
            secs
        )

    } else {

        String.format(
            "%02d:%02d",
            minutes,
            secs
        )
    }
}
