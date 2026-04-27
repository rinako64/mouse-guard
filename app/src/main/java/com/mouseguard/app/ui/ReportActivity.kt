package com.mouseguard.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mouseguard.app.R
import com.mouseguard.app.data.BarBucket
import com.mouseguard.app.data.EventStore
import com.mouseguard.app.data.Period
import com.mouseguard.app.data.ReportData
import com.mouseguard.app.data.ReportStats
import com.mouseguard.app.service.FloatingCameraService

class ReportActivity : AppCompatActivity() {

    private var currentPeriod: Period = Period.WEEK

    private lateinit var segToday: TextView
    private lateinit var segWeek: TextView
    private lateinit var segMonth: TextView

    private lateinit var tvPeriodLabel: TextView
    private lateinit var tvTotalLabel: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvTrendChip: TextView
    private lateinit var tvMonitorHours: TextView
    private lateinit var tvMonitorMinutes: TextView
    private lateinit var tvStreakValue: TextView
    private lateinit var tvKeepHours: TextView
    private lateinit var tvKeepMinutes: TextView
    private lateinit var tvPokaroMsg: TextView
    private lateinit var chartBars: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        segToday = findViewById(R.id.segToday)
        segWeek = findViewById(R.id.segWeek)
        segMonth = findViewById(R.id.segMonth)

        tvPeriodLabel = findViewById(R.id.tvPeriodLabel)
        tvTotalLabel = findViewById(R.id.tvTotalLabel)
        tvTotalCount = findViewById(R.id.tvTotalCount)
        tvTrendChip = findViewById(R.id.tvTrendChip)
        tvMonitorHours = findViewById(R.id.tvMonitorHours)
        tvMonitorMinutes = findViewById(R.id.tvMonitorMinutes)
        tvStreakValue = findViewById(R.id.tvStreakValue)
        tvKeepHours = findViewById(R.id.tvKeepHours)
        tvKeepMinutes = findViewById(R.id.tvKeepMinutes)
        tvPokaroMsg = findViewById(R.id.tvPokaroMsg)
        chartBars = findViewById(R.id.chartBars)

        segToday.setOnClickListener { selectPeriod(Period.TODAY) }
        segWeek.setOnClickListener { selectPeriod(Period.WEEK) }
        segMonth.setOnClickListener { selectPeriod(Period.MONTH) }

        findViewById<View>(R.id.tabHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }
        findViewById<View>(R.id.tabReport).setOnClickListener { /* current */ }
        findViewById<View>(R.id.tabGuide).setOnClickListener {
            startActivity(Intent(this, GuideActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }
        findViewById<View>(R.id.tabSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun selectPeriod(period: Period) {
        currentPeriod = period
        refresh()
    }

    private fun refresh() {
        applySegState(segToday, currentPeriod == Period.TODAY)
        applySegState(segWeek, currentPeriod == Period.WEEK)
        applySegState(segMonth, currentPeriod == Period.MONTH)

        val activeStart = if (FloatingCameraService.isRunning) {
            EventStore.currentSessionStart(this)
        } else null

        val data = ReportStats.compute(
            period = currentPeriod,
            events = EventStore.getEvents(this),
            sessions = EventStore.getSessions(this),
            currentSessionStart = activeStart,
            now = System.currentTimeMillis(),
        )
        bindData(data)
    }

    private fun applySegState(view: TextView, active: Boolean) {
        if (active) {
            view.setBackgroundResource(R.drawable.bg_seg_on)
            view.setTextColor(0xFFFFFFFF.toInt())
        } else {
            view.setBackgroundResource(0)
            view.setTextColor(ContextCompat.getColor(this, R.color.ink_sub))
        }
    }

    private fun bindData(data: ReportData) {
        tvPeriodLabel.text = data.periodLabel
        tvTotalLabel.text = data.totalLabelText
        tvTotalCount.text = data.totalCount.toString()

        val chip = data.trendChip
        if (chip == null) {
            tvTrendChip.visibility = View.GONE
        } else {
            tvTrendChip.visibility = View.VISIBLE
            tvTrendChip.text = chip.text
            tvTrendChip.setBackgroundResource(
                if (chip.isImprovement) R.drawable.bg_chip_mint else R.drawable.bg_chip_pink
            )
        }

        tvMonitorHours.text = data.monitoringHours.toString()
        tvMonitorMinutes.text = data.monitoringMinutes.toString()
        tvStreakValue.text = data.streakDays.toString()
        tvKeepHours.text = data.longestKeepHours.toString()
        tvKeepMinutes.text = data.longestKeepMinutes.toString()
        tvPokaroMsg.text = data.pokaroMessage

        renderBarChart(data.bars)
    }

    private fun renderBarChart(bars: List<BarBucket>) {
        chartBars.removeAllViews()

        val max = (bars.maxOfOrNull { it.count } ?: 0).coerceAtLeast(4)
        val peak = bars.maxOfOrNull { it.count } ?: 0

        val ink = ContextCompat.getColor(this, R.color.ink)
        val inkSub = 0x8C111111.toInt()

        for (b in bars) {
            val isPeak = b.count == peak && b.count > 0
            val isEmpty = b.count == 0

            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                val lp = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )
                lp.marginStart = dp(3)
                lp.marginEnd = dp(3)
                layoutParams = lp
            }

            val valLabel = TextView(this).apply {
                text = if (isPeak) "${b.count}${getString(R.string.report_count_unit)}" else " "
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(ink)
                typeface = androidx.core.content.res.ResourcesCompat
                    .getFont(this@ReportActivity, R.font.zen_maru_gothic)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                includeFontPadding = false
                if (!isPeak) visibility = View.INVISIBLE
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams = lp
            }

            val barHeightDp = if (isEmpty) 6 else ((b.count.toFloat() / max) * 74f).toInt().coerceAtLeast(8)
            val bar = View(this).apply {
                val bg = when {
                    isEmpty -> R.drawable.bg_bar_empty
                    isPeak -> R.drawable.bg_bar_pink
                    else -> R.drawable.bg_bar_black
                }
                setBackgroundResource(bg)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(barHeightDp)
                )
                lp.topMargin = dp(3)
                layoutParams = lp
            }

            val tick = TextView(this).apply {
                text = b.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(inkSub)
                typeface = androidx.core.content.res.ResourcesCompat
                    .getFont(this@ReportActivity, R.font.zen_maru_gothic)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                includeFontPadding = false
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp(4)
                layoutParams = lp
            }

            column.addView(valLabel)
            column.addView(bar)
            column.addView(tick)
            chartBars.addView(column)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
