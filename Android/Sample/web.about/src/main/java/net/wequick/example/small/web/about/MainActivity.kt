package net.wequick.example.small.web.about

import android.support.v7.app.AppCompatActivity
import android.os.Bundle

import net.wequick.small.Small

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Small.preSetUp(application)
        Small.setUp(this, null)
        Small.openUri("file:///android_asset/index.html", this@MainActivity)
        finish()
    }
}
