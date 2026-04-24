package com.mouseguard.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mouseguard.app.R

class ReportActivity : AppCompatActivity() {

    private data class BarDatum(val hour: Int, val count: Int)

    private val weekData = listOf(
        BarDatum(7, 0),
        BarDatum(9, 1),
        BarDatum(11, 0),
        BarDatum(13, 2),
        BarDatum(15, 0),
        BarDatum(17, 1),
        BarDatum(19, 3),
        BarDatum(21, 0),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        renderBarChart()

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

        listOf(R.id.segToday, R.id.segMonth).forEach { id ->
            findViewById<View>(id).setOnClickListener {
                Toast.makeText(this, "この切替はまもなく対応します", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderBarChart() {
        val host = findViewById<LinearLayout>(R.id.chartBars)
        host.removeAllViews()

        val maxCount = 4
        val peak = weekData.maxOf { it.count }

        val ink = ContextCompat.getColor(this, R.color.ink)
        val inkSub = 0x8C111111.toInt()

        for (d in weekData) {
            val isPeak = d.count == peak && d.count > 0
            val isEmpty = d.count == 0

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

            // Value label (peak only; placeholder for alignment otherwise)
            val valLabel = TextView(this).apply {
                text = if (isPeak) "${d.count}${getString(R.string.report_count_unit)}" else " "
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

            // Bar
            val barHeightDp = if (isEmpty) 6 else ((d.count.toFloat() / maxCount) * 74f).toInt()
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

            // Hour tick
            val tick = TextView(this).apply {
                text = d.hour.toString()
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
            host.addView(column)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
