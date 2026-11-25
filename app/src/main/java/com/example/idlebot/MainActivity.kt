package com.example.idlebot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        l.addView(TextView(this).apply {
            text = "1. Tap the button below and enable IdleBot under Accessibility.\n" +
                "2. Open the game on the Business Mode screen.\n" +
                "3. Tap the floating START button to run, tap it again to stop."
            textSize = 16f
        })
        l.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        setContentView(l)
    }
}
