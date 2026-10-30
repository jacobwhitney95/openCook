/*
 *  openCook
 *  Copyright (C) 2026 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.food.opencook.ui.recipes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.em
import com.food.opencook.util.DurationFormat

private const val TIMER_ICON = "timer"

/**
 * One numbered step with its cooking times as links — "10 Minuten" gets a small timer icon
 * and an underline, and a tap hands it to the clock app ([startClockTimer]). Everything a
 * timer needs beyond that (sound, +1 min, several at once, the notification) is the clock's.
 */
@Composable
internal fun StepText(number: Int, text: String, onTimer: (seconds: Int) -> Unit) {
    val times = remember(text) { DurationFormat.findIn(text) }
    val currentOnTimer by rememberUpdatedState(onTimer)
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(number, text, times, linkColor) {
        buildAnnotatedString {
            append("$number. ")
            var from = 0
            times.forEach { time ->
                append(text.substring(from, time.range.first))
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "timer",
                        styles = TextLinkStyles(
                            SpanStyle(
                                color = linkColor,
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                    ) { currentOnTimer(time.seconds) },
                ) {
                    appendInlineContent(TIMER_ICON, "⏱")
                    // Word joiner: the icon must not end a line with its time on the next.
                    append('⁠')
                    append(text.substring(time.range))
                }
                from = time.range.last + 1
            }
            append(text.substring(from))
        }
    }
    val inline = remember(linkColor) {
        mapOf(
            TIMER_ICON to InlineTextContent(Placeholder(1.1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = linkColor, modifier = Modifier)
            },
        )
    }
    Text(annotated, inlineContent = inline, style = MaterialTheme.typography.bodyLarge)
}

/** Open the clock app's list of running timers — where one is stopped or extended. */
internal fun showClockTimers(context: Context) {
    try {
        context.startActivity(Intent(AlarmClock.ACTION_SHOW_TIMERS))
    } catch (_: ActivityNotFoundException) {
        // The timer is running either way; there is just no screen to show it on.
    }
}

/**
 * Start a countdown in the phone's clock app without leaving the recipe ([AlarmClock.EXTRA_SKIP_UI]).
 * Without [seconds] the clock opens its own "new timer" screen instead, to type a time in.
 * False when no installed app takes timers — the caller says so instead of crashing.
 */
internal fun startClockTimer(context: Context, seconds: Int?, label: String): Boolean = try {
    val intent = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_MESSAGE, label)
    if (seconds != null) {
        intent.putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
    }
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}
