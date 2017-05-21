package net.wequick.example.small.app.mine

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup

/**
 * Created by galen on 16/3/14.
 */
class PickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_picker)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val rg = findViewById(R.id.radioGroup1) as RadioGroup

        val btnPick = findViewById(R.id.pick_button) as Button
        btnPick.setOnClickListener {
            val data = Intent()
            val rb = findViewById(rg.checkedRadioButtonId) as RadioButton
            data.putExtra("color", rb.text)
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
