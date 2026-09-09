package com.syntaxjester.gunplalog

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 仿「玩物赏志」的周日历组件。
 *
 * 功能：
 * - 显示一周七天的日期卡片
 * - 选中日期蓝色圆圈背景白字
 * - 有收藏的日期下方小灰点
 * - 点击日期回调 listener
 */
class CalendarWeekView(context: Context) {

    private val container: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    var listener: ((Calendar) -> Unit)? = null
    private val selected: Calendar = Calendar.getInstance()
    private var weekStart: Calendar = Calendar.getInstance()

    init {
        setWeekStart(selected)
        render()
    }

    fun getContainer(): LinearLayout = container

    fun refreshMarks() {
        render()
    }

    var markedDates: Set<String> = emptySet()

    fun today() {
        val now = Calendar.getInstance()
        setWeekStart(now)
        setSelectedDay(now)
        render()
        listener?.invoke(selected.clone() as Calendar)
    }

    fun prevWeek() {
        weekStart.add(Calendar.DAY_OF_YEAR, -7)
        render()
    }

    fun nextWeek() {
        weekStart.add(Calendar.DAY_OF_YEAR, 7)
        render()
    }

    fun setSelectedDay(sel: Calendar) {
        selected.time = sel.time
        setWeekStart(selected)
        render()
    }

    fun getMonthTitle(): String {
        val sdf = SimpleDateFormat("yyyy年M月", Locale.CHINESE)
        return sdf.format(weekStart.time)
    }

    private fun setWeekStart(cal: Calendar) {
        weekStart = cal.clone() as Calendar
        weekStart.set(Calendar.DAY_OF_WEEK, weekStart.firstDayOfWeek)
        weekStart.set(Calendar.HOUR_OF_DAY, 0)
        weekStart.set(Calendar.MINUTE, 0)
        weekStart.set(Calendar.SECOND, 0)
        weekStart.set(Calendar.MILLISECOND, 0)
    }

    private fun render() {
        container.removeAllViews()
        val dayLabels = arrayOf("一", "二", "三", "四", "五", "六", "日")
        val inflater = LayoutInflater.from(container.context)

        for (i in 0 until 7) {
            val dayCal = (weekStart.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, i)
            }
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dayCal.time)
            val isToday = isSameDay(dayCal, Calendar.getInstance())
            val isSelected = isSameDay(dayCal, selected)

            val v = inflater.inflate(R.layout.item_calendar_day, container, false)
            val tvWeek = v.findViewById<TextView>(R.id.tvWeekday)
            val tvDay = v.findViewById<TextView>(R.id.tvDay)
            val vDot = v.findViewById<View>(R.id.vDot)

            // 星期文字
            tvWeek.text = dayLabels[(dayCal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7]
            tvWeek.setTextColor(if (isSelected) Color.parseColor("#3C7BF2") else Color.parseColor("#999999"))

            // 日期数字
            tvDay.text = dayCal.get(Calendar.DAY_OF_MONTH).toString()
            v.isSelected = isSelected

            // 今日小圆点
            if (isToday && markedDates.contains(dateKey)) {
                vDot.visibility = View.VISIBLE
                vDot.setBackgroundResource(R.drawable.bg_cal_today)
            } else if (markedDates.contains(dateKey)) {
                vDot.visibility = View.VISIBLE
                vDot.setBackgroundResource(R.drawable.bg_cal_dot)
            } else {
                vDot.visibility = View.INVISIBLE
            }

            // 点击
            v.setOnClickListener {
                setSelectedDay(dayCal)
                render()
                listener?.invoke(dayCal.clone() as Calendar)
            }

            container.addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            })
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
