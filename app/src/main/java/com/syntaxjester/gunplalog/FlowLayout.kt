package com.syntaxjester.gunplalog

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * 极简流式布局：子 View 从左到右排列，超出宽度自动换行。
 * 用于编辑弹层里 18 个规格胶囊的多行排布。
 */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private val gapH = dp(8)
    private val gapV = dp(8)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val avail = width - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            child.measure(
                MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            if (x > 0 && x + child.measuredWidth > avail) {
                x = 0
                y += rowH + gapV
                rowH = 0
            }
            x += child.measuredWidth + gapH
            if (child.measuredHeight > rowH) rowH = child.measuredHeight
        }

        val h = paddingTop + paddingBottom + y + rowH
        setMeasuredDimension(width, resolveSize(h, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val avail = (r - l) - paddingLeft - paddingRight
        var x = paddingLeft
        var y = paddingTop
        var rowH = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            if (x > paddingLeft && x + child.measuredWidth > paddingLeft + avail) {
                x = paddingLeft
                y += rowH + gapV
                rowH = 0
            }
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            x += child.measuredWidth + gapH
            if (child.measuredHeight > rowH) rowH = child.measuredHeight
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
