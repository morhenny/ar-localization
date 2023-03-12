package de.morhenn.ar_localization

import android.app.Application

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
        }
    }
}