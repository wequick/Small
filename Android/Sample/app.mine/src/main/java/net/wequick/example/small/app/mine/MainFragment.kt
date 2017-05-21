package net.wequick.example.small.app.mine

import android.app.Activity
import android.content.Intent
import android.support.annotation.Keep
import android.support.v4.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

import com.example.hellojni.HelloPluginJni
import com.example.mylib.Greet
import net.wequick.example.small.lib.utils.UIUtils

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Created by galen on 15/11/12.
 */
@Keep
class MainFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater!!.inflate(R.layout.fragment_main, container, false)
        val tvSection = rootView.findViewById(R.id.section_label) as TextView
        tvSection.setText(R.string.hello)
        tvSection.setTextColor(resources.getColor(R.color.colorAccent))

        var button = rootView.findViewById(R.id.inter_start_button) as Button
        button.setOnClickListener {
            val intent = Intent(this@MainFragment.context, PickerActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_COLOR)
        }

        button = rootView.findViewById(R.id.call_system_button) as Button
        button.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK,
                    android.provider.ContactsContract.Contacts.CONTENT_URI)
            startActivityForResult(intent, REQUEST_CODE_CONTACTS)
        }

        try {
            var `is` = resources.assets.open("greet.txt")
            var br = BufferedReader(InputStreamReader(`is`))
            var greet = br.readLine()
            `is`.close()

            val tvAssets = rootView.findViewById(R.id.assets_label) as TextView
            tvAssets.text = "assets/greet.txt: " + greet

            `is` = resources.openRawResource(R.raw.greet)
            br = BufferedReader(InputStreamReader(`is`))
            greet = br.readLine()
            `is`.close()

            val tvRaw = rootView.findViewById(R.id.raw_label) as TextView
            tvRaw.text = "res/raw/greet.txt: " + greet

            `is` = resources.openRawResource(R.raw.mq_new_message)
            println("### " + `is`.available())
            `is`.close()


        } catch (e: IOException) {
            e.printStackTrace()
        }

        // TODO: Following will crash, try to fix it
        //        getResources().openRawResourceFd(R.raw.greet);

        val tvLib = rootView.findViewById(R.id.lib_label) as TextView
        tvLib.text = Greet.hello()

        val tvJni = rootView.findViewById(R.id.jni_label) as TextView
        tvJni.text = HelloPluginJni.stringFromJNI()

        button = rootView.findViewById(R.id.play_video_button) as Button
        button.setOnClickListener {
            val intent = Intent(this@MainFragment.context, VideoActivity::class.java)
            startActivity(intent)
        }

        return rootView
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            REQUEST_CODE_COLOR -> {
                val button = view!!.findViewById(R.id.inter_start_button) as Button
                button.text = getText(R.string.inter_start).toString() + ": " + data!!.getStringExtra("color")
            }
            REQUEST_CODE_CONTACTS -> UIUtils.showToast(context, "contact: " + data!!)
        }
    }

    companion object {

        private val REQUEST_CODE_COLOR = 1000
        private val REQUEST_CODE_CONTACTS = 1001
    }
}
