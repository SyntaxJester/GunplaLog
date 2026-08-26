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

    @Volatile
    private var prev: String? = null

    fun file(ctx: Context): File =
        File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, FILE)

    /** 记录本次启动之前的旧报告，然后开新的一页 */
    fun begin(ctx: Context) {
        prev = try {
            file(ctx).takeIf { it.exists() }?.readText()
        } catch (_: Exception) {
            null
        }
        try {
            file(ctx).writeText(
                "=== launch "
                        + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                        + " ===\n"
            )
        } catch (_: Exception) {
        }
    }

    fun step(ctx: Context, msg: String) {
        try {
            file(ctx).appendText(msg + "\n")
        } catch (_: Exception) {
        }
    }

    fun logThrowable(ctx: Context, t: Throwable) {
        try {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            file(ctx).appendText("\n---- CRASH ----\n").appendText(sw.toString()).appendText("\n")
        } catch (_: Exception) {
        }
    }

    fun prevReport(): String? = prev?.takeIf { it.isNotBlank() }

    fun install(ctx: Context) {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                logThrowable(ctx, e)
                step(ctx, "uncaught on thread " + t.name)
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
