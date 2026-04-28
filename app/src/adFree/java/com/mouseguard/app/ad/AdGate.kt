package com.mouseguard.app.ad

import android.app.Activity
import android.view.ViewGroup

object AdGate {

    /** 何もしない */
    fun init(activity: Activity) {}

    /** 広告なし版では何もしない */
    fun loadBanner(activity: Activity, container: ViewGroup) {}
}
