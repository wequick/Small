package net.wequick.example.small.app.mine

import android.media.MediaPlayer
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.widget.VideoView

class VideoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val videoView = findViewById(R.id.video_view) as VideoView
        val uri = "android.resource://" + packageName + "/" + R.raw.fix_429
        videoView.setVideoURI(Uri.parse(uri))
        // Loop
        videoView.setOnPreparedListener { mp -> mp.isLooping = true }
        // Play
        videoView.start()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
