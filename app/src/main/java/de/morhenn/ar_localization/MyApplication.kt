package de.morhenn.ar_localization

import android.app.Application
import de.morhenn.ar_localization.utils.FileLog

class MyApplication : Application() {

    companion object {
        @JvmField
        var initialized = false
        const val TAG = "ArLocalizationApp"
    }

    override fun onCreate() {
        super.onCreate()
        if (!initialized) {
            initialized = true
            //initialize all static utils

            FileLog.init(applicationContext, true)
            Thread.setDefaultUncaughtExceptionHandler { _, e -> FileLog.fatal(e) }
        }
    }
}