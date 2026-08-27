package com.syntaxjester.gunplalog

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 统一的筛选胶囊工厂。
 * 用纯 TextView + 选择器 drawable 实现，避免 Material Chip 在深色头部上
 * 选中/未选中配色被主题覆盖导致「白底白字」看不清的问题。
 */
object Pills {

    /** 蓝色头部上的胶囊：未选中半透明白底白字，选中实心白底蓝字 */
    fun header(ctx: Context, label: String): TextView =
        build(ctx, label, R.drawable.pill_header_bg, R.color.pill_header_text)

    /** 白色底上的胶囊（编辑弹层）：未选中浅灰底深字，选中实心蓝底白字 */
    fun sheet(ctx: Context, label: String): TextView =
        build(ctx, label, R.drawable.pill_sheet_bg, R.color.pill_sheet_text)

    private fun build(ctx: Context, label: String, bgRes: Int, textColorRes: Int): TextView {
        val d = ctx.resources.displayMetrics.density
        val padH = (14 * d).toInt()
        val padV = (8 * d).toInt()
        return TextView(ctx).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(padH, padV, padH, padV)
            setBackgroundResource(bgRes)
            setTextColor(androidx.core.content.ContextCompat.getColorStateList(ctx, textColorRes))
            isClickable = true
            isFocusable = true
            includeFontPadding = false
        }
    }

    /** 水平单行容器用的间距参数 */
    fun rowParams(ctx: Context, marginEndDp: Int = 8): LinearLayout.LayoutParams {
        val d = ctx.resources.displayMetrics.density
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = (marginEndDp * d).toInt() }
    }

    /** 在一组胶囊里单选 */
    fun select(container: ViewGroup, selected: View) {
        for (i in 0 until container.childCount) {
            val c = container.getChildAt(i)
            c.isSelected = (c === selected)
        }
    }
}
