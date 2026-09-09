/*
 * 写在文件头部方便排查
 * - CalendarWeekView：横向 7 天日历条，本周可换页
 * - 点击某天 → 选中 + 回调；今日有记录 → 小圆点
 */
package com.syntaxjester.gunplalog

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarWeekView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(ctx, attrs, defStyle) {

    private val row = LinearLayout(ctx).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_HORIZONTAL
    }
    private val weekFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var weekStart: Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }
    var selected: Calendar = Calendar.getInstance()
    var listener: ((Calendar) -> Unit)? = null

    /** 有记录的日期字符串集合（yyyy-MM-dd） */
    var markedDates: Set<String> = emptySet()

    init {
        addView(row)
        render()
        setSelected(selected)
    }

    /** 设置周起始（保持周内日期不变） */
    fun setWeekStart(cal: Calendar) {
        weekStart = cal.clone() as Calendar
        (weekStart as Calendar).set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        render()
    }

    fun setSelected(sel: Calendar) {
        selected = sel.clone() as Calendar
        highlight()
    }

    /** 上一周 / 下一周 */
    fun shift(weeks: Int) {
        weekStart.add(Calendar.WEEK_OF_YEAR, weeks)
        render()
    }

    /** 今天 */
    fun today() {
        val now = Calendar.getInstance()
        val cur = Calendar.getInstance().apply { time = now.time }
        setWeekStart(cur)
        setSelected(now)
        listener?.invoke(selected.clone() as Calendar)
    }

    private fun render() {
        row.removeAllViews()
        val days = arrayOf("一", "二", "三", "四", "五", "六", "日")
        for (i in 0 until 7) {
            val dayCal = (weekStart.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, i)
            }
            val v = LayoutInflater.from(context).inflate(
                R.layout.item_calendar_day, row, false
            )
            val tvWeek = v.findViewById<TextView>(R.id.tvWeekday)
            val tvDay = v.findViewById<TextView>(R.id.tvDay)
            val dot = v.findViewById<View>(R.id.vDot)

            tvWeek.text = days[(dayCal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7]
            tvDay.text = dayCal.get(Calendar.DAY_OF_MONTH).toString()
            val isToday = isSameDay(dayCal, Calendar.getInstance())
            dot.visibility = if (weekFmt.format(dayCal.time) in markedDates) View.VISIBLE else View.GONE
            dot.isSelected = isToday
            if (isToday) {
                dot.visibility = View.VISIBLE
                dot.isSelected = true
            }

            val selectedCal = selected.clone() as Calendar
            val isSelected = isSameDay(dayCal, selectedCal)
            v.isSelected = isSelected
            tvDay.isSelected = isSelected

            v.setOnClickListener {
                setSelected(dayCal)
                highlight()
                listener?.invoke(selected.clone() as Calendar)
            }
            row.addView(v, LayoutParams(0f, LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            })
        }
    }

    private fun highlight() {
        for (i in 0 until row.childCount) {
            val cal = (weekStart.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, i)
            }
            val v = row.getChildAt(i)
            v.isSelected = isSameDay(cal, selected)
            v.findViewById<TextView>(R.id.tvDay).isSelected = isSameDay(cal, selected)
        }
    }

    /** 在当前周范围内刷新标记点 */
    fun refreshMarks() {
        for (i in 0 until row.childCount) {
            val cal = (weekStart.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, i)
            }
            val dot = row.getChildAt(i).findViewById<View>(R.id.vDot)
            val hasRecord = weekFmt.format(cal.time) in markedDates
            val isToday = isSameDay(cal, Calendar.getInstance())
            dot.visibility = if (hasRecord || isToday) View.VISIBLE else View.GONE
            dot.isSelected = isToday
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    /** 格式化日期字符串给外部消费 */
    fun selectedDateKey(): String = weekFmt.format(selected.time)
}
