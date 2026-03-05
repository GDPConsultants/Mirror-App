package com.mirrornaturallook

import android.app.Application
import com.google.android.gms.ads.MobileAds

class MirrorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialise AdMob SDK on app start
        MobileAds.initialize(this) {}
    }
}
