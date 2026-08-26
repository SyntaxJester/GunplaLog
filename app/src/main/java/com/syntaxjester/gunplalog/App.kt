package com.syntaxjester.gunplalog

import android.app.Application
import android.content.Context

class App : Application() {

    override fun attachBaseContext(base: Context) {
        CrashGuard.install(this)
        super.attachBaseContext(base)
        CrashGuard.begin(this)
        CrashGuard.step(this, "[1] app.attachBaseContext ok")
    }

    override fun onCreate() {
        super.onCreate()
        CrashGuard.step(this, "[2] app.onCreate ok")
    }
}
