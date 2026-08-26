package com.syntaxjester.gunplalog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashGuard {

    private const val FILE = "crash_report.txt"

    fun file(ctx: Context): File =
        File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, FILE)

    fun install(ctx: Context) {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val txt = buildString {
                    append("time=").append(
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    ).append('\n')
                    append("thread=").append(t.name).append('\n')
                    append("device=").append(android.os.Build.MANUFACTURER).append(' ')
                        .append(android.os.Build.MODEL)
                        .append(" Android ").append(android.os.Build.VERSION.SDK_INT)
                        .append("\n\n")
                    append(sw.toString())
                }
                file(ctx).writeText(txt)
            } catch (_: Exception) {
            }
            @Suppress("DEPRECATION")
            old?.uncaughtException(t, e)
        }
    }

    fun read(ctx: Context): String? = try {
        file(ctx).takeIf { it.exists() && it.length() > 0 }?.readText()
    } catch (_: Exception) {
        null
    }

    fun clear(ctx: Context) {
        try {
            file(ctx).delete()
        } catch (_: Exception) {
        }
    }

    fun copy(ctx: Context, text: String) {
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("gunplalog_crash", text))
        } catch (_: Exception) {
        }
    }
}
