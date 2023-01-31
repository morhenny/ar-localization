package de.morhenn.ar_localization.utils

import android.util.Log

object TimingUtil {

    private const val TAG = "TimingUtil"

    fun <T> runTimed(name: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        val result = block.invoke()
        val end = System.currentTimeMillis()
        val execTime = end - start
        Log.d(TAG, "$execTime ms exec for $name")
        return result
    }

    fun timeInterim(name: String, start: Long) {
        val end = System.currentTimeMillis()
        val execTime = end - start
        Log.d(TAG, ":$execTime ms interim for $name")
    }
}