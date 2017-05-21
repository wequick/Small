package net.wequick.example.small.app.detail

import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView

import net.wequick.small.Small

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            println("savedInstanceState: " + savedInstanceState)
        }

        setContentView(R.layout.activity_main)
        title = "Detail"
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val uri = Small.getUri(this)
        if (uri != null) {
            val from = uri.getQueryParameter("from")
            if (from != null) {
                val tvFrom = findViewById(R.id.tvFrom) as TextView
                tvFrom.text = "-- Greet from " + from
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("Hello", "Small")
        super.onSaveInstanceState(outState)
    }
}
