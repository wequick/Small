package net.wequick.example.small

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Window

import net.wequick.small.Small

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class LaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

        val sp = this@LaunchActivity.getSharedPreferences("profile", 0)
        val se = sp.edit()
        val tStart = System.nanoTime()
        se.putLong("setUpStart", tStart)
        Small.setUp(this@LaunchActivity) {
            val tEnd = System.nanoTime()
            se.putLong("setUpFinish", tEnd).apply()
            val offset = tEnd - tStart
            if (offset < MIN_INTRO_DISPLAY_TIME) {
                // 这个延迟仅为了让 "Small Logo" 显示足够的时间, 实际应用中不需要
                window.decorView.postDelayed({
                    Small.openUri("main", this@LaunchActivity)
                    finish()
                }, (MIN_INTRO_DISPLAY_TIME - offset) / 1000000)
            } else {
                Small.openUri("main", this@LaunchActivity)
                finish()
            }
        }
    }

    companion object {

        private val MIN_INTRO_DISPLAY_TIME: Long = 1000000000 // mμs -> 1.0s
    }
}
