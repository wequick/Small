package net.wequick.example.small.appok_if_stub

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem

class TransitionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transition)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_right_in, R.anim.slide_right_out)
    }
}
