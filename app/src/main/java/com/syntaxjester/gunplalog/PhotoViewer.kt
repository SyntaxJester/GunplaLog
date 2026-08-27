package com.syntaxjester.gunplalog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView

/** 点击缩略图后的全屏大图预览 */
object PhotoViewer {

    fun show(ctx: Context, photo: String?) {
        val bmp = Photos.decode(ctx, photo, 1600) ?: return
        val iv = ImageView(ctx).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { gravity = Gravity.CENTER }
        }
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#EE000000"))
            addView(iv)
        }
        val dlg = Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dlg.setContentView(root)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        root.setOnClickListener { dlg.dismiss() }
        iv.setOnClickListener { dlg.dismiss() }
        dlg.show()
    }
}
