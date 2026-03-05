package com.mirrornaturallook

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Splash launches immediately into MirrorActivity.
 * No logo, no delay — user sees mirror as fast as possible.
 */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MirrorActivity::class.java))
        finish()
    }
}
