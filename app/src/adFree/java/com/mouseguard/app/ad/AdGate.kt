package com.mouseguard.app.ad

import android.app.Activity

object AdGate {

    /** 何もしない */
    fun init(activity: Activity) {}

    /** 広告なし — 即座にコールバック */
    fun showIfNeeded(activity: Activity, onGranted: () -> Unit) {
        onGranted()
    }
}
