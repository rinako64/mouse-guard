package com.mouseguard.app.data

import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Period { TODAY, WEEK, MONTH }

data class BarBucket(val label: String, val count: Int)

data class TrendChip(val text: String, val isImprovement: Boolean)

data class ReportData(
    val periodLabel: String,
    val totalLabelText: String,
    val totalCount: Int,
    val trendChip: TrendChip?,
    val monitoringHours: Int,
    val monitoringMinutes: Int,
    val bars: List<BarBucket>,
    val streakDays: Int,
    val longestKeepHours: Int,
    val longestKeepMinutes: Int,
    val pokaroMessage: String,
)

object ReportStats {
    private val HOUR_BUCKETS = listOf(7, 9, 11, 13, 15, 17, 19, 21)
    private const val BUCKET_SPAN_HOURS = 2

    fun compute(
        period: Period,
        events: List<PokanEvent>,
        sessions: List<MonitoringSession>,
        currentSessionStart: Long?,
        now: Long,
    ): ReportData {
        val (curStart, curEnd) = periodBounds(period, now)
        val (prevStart, prevEnd) = previousBounds(period, now)

        val curEvents = events.filter { it.openMs in curStart until curEnd }
        val prevEvents = events.filter { it.openMs in prevStart until prevEnd }

        val effectiveSessions = sessions + (currentSessionStart?.let {
            listOf(MonitoringSession(it, now))
        } ?: emptyList())

        val monitoringMs = effectiveSessions.sumOf { s ->
            val a = s.startMs.coerceAtLeast(curStart)
            val b = s.endMs.coerceAtMost(curEnd)
            (b - a).coerceAtLeast(0L)
        }

        val bars = HOUR_BUCKETS.map { h ->
            val count = curEvents.count { ev ->
                val cal = Calendar.getInstance().apply { timeInMillis = ev.openMs }
                val hr = cal.get(Calendar.HOUR_OF_DAY)
                hr in h until (h + BUCKET_SPAN_HOURS)
            }
            BarBucket(label = h.toString(), count = count)
        }

        val streakDays = computeStreakDays(effectiveSessions, now)
        val longestKeep = computeLongestKeep(events, effectiveSessions)
        val keepHours = (longestKeep / 3_600_000L).toInt()
        val keepMinutes = ((longestKeep % 3_600_000L) / 60_000L).toInt()

        val totalLabel = when (period) {
            Period.TODAY -> "今日のお口ぽかん"
            Period.WEEK -> "今週のお口ぽかん合計"
            Period.MONTH -> "今月のお口ぽかん合計"
        }

        val trend = computeTrend(period, curEvents.size, prevEvents.size)
        val message = buildPokaroMessage(period, curEvents.size, prevEvents.size, curStart)

        return ReportData(
            periodLabel = formatPeriodLabel(period, now),
            totalLabelText = totalLabel,
            totalCount = curEvents.size,
            trendChip = trend,
            monitoringHours = (monitoringMs / 3_600_000L).toInt(),
            monitoringMinutes = ((monitoringMs % 3_600_000L) / 60_000L).toInt(),
            bars = bars,
            streakDays = streakDays,
            longestKeepHours = keepHours,
            longestKeepMinutes = keepMinutes,
            pokaroMessage = message,
        )
    }

    private fun periodBounds(period: Period, now: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            firstDayOfWeek = Calendar.MONDAY
        }
        return when (period) {
            Period.TODAY -> {
                val start = startOfDay(cal)
                val end = start + 24L * 3600_000L
                start to end
            }
            Period.WEEK -> {
                val mondayCal = (cal.clone() as Calendar).apply {
                    val dow = get(Calendar.DAY_OF_WEEK)
                    val offset = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
                    add(Calendar.DAY_OF_YEAR, -offset)
                }
                val start = startOfDay(mondayCal)
                val end = start + 7L * 24L * 3600_000L
                start to end
            }
            Period.MONTH -> {
                val monthCal = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val start = startOfDay(monthCal)
                val nextMonth = (monthCal.clone() as Calendar).apply {
                    add(Calendar.MONTH, 1)
                }
                val end = startOfDay(nextMonth)
                start to end
            }
        }
    }

    private fun previousBounds(period: Period, now: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            firstDayOfWeek = Calendar.MONDAY
        }
        return when (period) {
            Period.TODAY -> {
                val today = startOfDay(cal)
                val yesterday = today - 24L * 3600_000L
                yesterday to today
            }
            Period.WEEK -> {
                val (curStart, _) = periodBounds(Period.WEEK, now)
                (curStart - 7L * 24L * 3600_000L) to curStart
            }
            Period.MONTH -> {
                val (curStart, _) = periodBounds(Period.MONTH, now)
                val prevMonthCal = Calendar.getInstance().apply {
                    timeInMillis = curStart
                    add(Calendar.MONTH, -1)
                }
                startOfDay(prevMonthCal) to curStart
            }
        }
    }

    private fun startOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun computeStreakDays(sessions: List<MonitoringSession>, now: Long): Int {
        if (sessions.isEmpty()) return 0
        val activeDays = sessions.map { dayKey(it.startMs) }.toHashSet()
        var streak = 0
        val cur = Calendar.getInstance().apply { timeInMillis = now }
        while (true) {
            val key = dayKey(cur.timeInMillis)
            if (activeDays.contains(key)) {
                streak++
                cur.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                if (streak == 0 && key == dayKey(now)) {
                    cur.add(Calendar.DAY_OF_YEAR, -1)
                    continue
                }
                break
            }
        }
        return streak
    }

    private fun dayKey(timeMs: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = timeMs }
        return c.get(Calendar.YEAR) * 10000L + c.get(Calendar.MONTH) * 100L + c.get(Calendar.DAY_OF_MONTH)
    }

    private fun computeLongestKeep(
        events: List<PokanEvent>,
        sessions: List<MonitoringSession>,
    ): Long {
        var longest = 0L
        for (s in sessions) {
            val sessionEvents = events.filter {
                it.openMs >= s.startMs && it.openMs < s.endMs
            }.sortedBy { it.openMs }

            var cursor = s.startMs
            for (ev in sessionEvents) {
                val gap = ev.openMs - cursor
                if (gap > longest) longest = gap
                cursor = ev.closeMs.coerceAtMost(s.endMs)
            }
            val tail = s.endMs - cursor
            if (tail > longest) longest = tail
        }
        return longest
    }

    private fun computeTrend(period: Period, current: Int, previous: Int): TrendChip? {
        val periodWord = when (period) {
            Period.TODAY -> "昨日"
            Period.WEEK -> "先週"
            Period.MONTH -> "先月"
        }
        if (current == 0 && previous == 0) {
            return null
        }
        if (previous == 0) {
            return TrendChip(text = "${periodWord}は記録なし", isImprovement = current == 0)
        }
        if (current == previous) {
            return TrendChip(text = "${periodWord}と同じ", isImprovement = true)
        }
        val ratio = (current - previous).toDouble() / previous
        val pct = abs(ratio * 100.0).roundToInt()
        return if (current < previous) {
            TrendChip(text = "${periodWord}より ${pct}% 減", isImprovement = true)
        } else {
            TrendChip(text = "${periodWord}より ${pct}% 増", isImprovement = false)
        }
    }

    private fun buildPokaroMessage(
        period: Period,
        current: Int,
        previous: Int,
        periodStart: Long,
    ): String {
        val variants = when {
            current == 0 -> messagesZero(period)
            previous == 0 -> messagesFirstTime(period)
            current < previous -> messagesImprovement(period)
            current == previous -> messagesSteady(period)
            else -> messagesRegression(period)
        }
        return pickVariant(variants, periodStart)
    }

    private fun pickVariant(variants: List<String>, periodStart: Long): String {
        if (variants.isEmpty()) return ""
        val day = periodStart / 86_400_000L
        val idx = ((day % variants.size) + variants.size) % variants.size
        return variants[idx.toInt()]
    }

    private fun messagesZero(period: Period): List<String> = when (period) {
        Period.TODAY -> listOf(
            "今日はぽかんゼロ！\nやったね 🌟",
            "ばっちり閉じてられたね 👏\nこの調子！",
            "今日も見守りおつかれさま ✨\nゆっくり過ごしてね",
        )
        Period.WEEK -> listOf(
            "今週はぽかんゼロ！\nすごい記録だね 🌟",
            "1週間ノーぽかん 🎉\nぽかろも誇らしいよ",
            "完璧な週だったね ✨\n来週もよろしくね",
        )
        Period.MONTH -> listOf(
            "今月はぽかんゼロ！\nすごい記録だね 🌟",
            "1ヶ月ノーぽかんなんて、すごすぎ ✨",
            "完璧な1ヶ月だったね 🏆",
        )
    }

    private fun messagesFirstTime(period: Period): List<String> = when (period) {
        Period.TODAY -> listOf(
            "今日が最初の記録だね！\nこれから一緒に見守ろう 🌱",
            "見守りはじめてくれてありがとう ✨\n少しずつ慣れていこうね",
            "はじめての1日、おつかれさま！\nまた明日も会おうね",
        )
        Period.WEEK -> listOf(
            "はじめての1週間、おつかれさま！\nここからスタートだよ 🌱",
            "今週の記録ができたよ ✨\n来週もよろしくね",
            "見守りを続けてくれてありがとう！\nまずは一歩、進めたね",
        )
        Period.MONTH -> listOf(
            "はじめての1ヶ月、おつかれさま！\nここからスタートだよ 🌱",
            "今月の記録ができたよ ✨\n来月もよろしくね",
            "見守りを続けてくれてありがとう！\nまずは一歩、進めたね",
        )
    }

    private fun messagesImprovement(period: Period): List<String> = when (period) {
        Period.TODAY -> listOf(
            "今日は昨日より上手にできたね 🌱\nこの調子！",
            "昨日より少なくなったよ ✨\nがんばってるね",
            "今日もよく頑張ったね！\n明日はもっといい記録になるかも 🌱",
        )
        Period.WEEK -> listOf(
            "今週は先週より良かったよ 🌱\nがんばったね",
            "今週もよく頑張ったね！\n来週はもっといい記録がでるかも 🌱",
            "右肩下がりで成長中 ✨\nこの調子！",
        )
        Period.MONTH -> listOf(
            "今月は先月より良かったよ 🌱\nがんばったね",
            "今月もよく頑張ったね！\n来月はもっといい記録がでるかも 🌱",
            "1ヶ月でしっかり成長したね ✨",
        )
    }

    private fun messagesSteady(period: Period): List<String> = when (period) {
        Period.TODAY -> listOf(
            "昨日と同じペースだね 🌿\nこの調子で続けよう",
            "今日も安定の見守り、おつかれさま",
            "コツコツ続けるのが大事だよ ✨",
        )
        Period.WEEK -> listOf(
            "先週と同じペースだね 🌿\nこの調子で続けよう",
            "安定の1週間、おつかれさま",
            "コツコツ続けるのが大事だよ ✨",
        )
        Period.MONTH -> listOf(
            "先月と同じペースだね 🌿\nこの調子で続けよう",
            "安定の1ヶ月、おつかれさま",
            "コツコツ続けるのが大事だよ ✨",
        )
    }

    private fun messagesRegression(period: Period): List<String> = when (period) {
        Period.TODAY -> listOf(
            "今日はちょっと多めだったね。\nまた明日がんばろう！",
            "疲れてた日だったかな？\nゆっくり休んで、また明日 🌱",
            "気にしないで、明日仕切り直し ✨",
        )
        Period.WEEK -> listOf(
            "今週はちょっと多めだったね。\nまた来週がんばろう！",
            "疲れがたまる週だったかな？\n来週は無理せず、また見守ろう 🌱",
            "波があるのは普通のこと 🌱\n来週も一緒にがんばろう",
        )
        Period.MONTH -> listOf(
            "今月はちょっと多めだったね。\nまた来月がんばろう！",
            "波がある月だったね。\n来月は気持ち新たに 🌱",
            "1ヶ月続けたこと自体がすごいよ ✨\n来月もよろしくね",
        )
    }

    private fun formatPeriodLabel(period: Period, now: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return when (period) {
            Period.TODAY -> {
                val month = cal.get(Calendar.MONTH) + 1
                val day = cal.get(Calendar.DAY_OF_MONTH)
                String.format(Locale.JAPAN, "%d月%d日", month, day)
            }
            Period.WEEK -> {
                val month = cal.get(Calendar.MONTH) + 1
                val weekOfMonth = cal.get(Calendar.WEEK_OF_MONTH)
                String.format(Locale.JAPAN, "%d月 第%d週", month, weekOfMonth)
            }
            Period.MONTH -> {
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH) + 1
                String.format(Locale.JAPAN, "%d年 %d月", year, month)
            }
        }
    }
}
