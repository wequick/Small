package net.wequick.example.small.app.detail

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View

/**
 * Created by galen on 16/4/11.
 */
class SubActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val v = LayoutInflater.from(this).inflate(R.layout.activity_sub, null)
        setContentView(v)
        v.setOnClickListener { finish() }
    }
}
