package net.wequick.example.small.app.home

import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.annotation.Keep
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast

import net.wequick.small.Small
import net.wequick.example.small.lib.utils.UIUtils

import org.json.JSONArray
import org.json.JSONObject

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayList

/**
 * Created by galen on 15/11/16.
 */
@Keep
class MainFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater!!.inflate(R.layout.fragment_main, container, false)
        var button = rootView.findViewById(R.id.btnDetail) as Button
        button.setOnClickListener { Small.openUri("detail?from=app.home", context) }

        button = rootView.findViewById(R.id.btnSubDetail) as Button
        button.setOnClickListener { Small.openUri("detail/sub", context) }

        button = rootView.findViewById(R.id.btnAbout) as Button
        button.setOnClickListener { Small.openUri("about", context) }

        button = rootView.findViewById(R.id.btnLib) as Button
        button.setOnClickListener { UIUtils.showToast(context, "Hello World!") }

        button = rootView.findViewById(R.id.btnUpgrade) as Button
        button.setOnClickListener { checkUpgrade() }
        return rootView
    }

    private fun checkUpgrade() {
        UpgradeManager(context).checkUpgrade()
    }

    private class UpgradeManager(private val mContext: Context) {

        private class UpdateInfo {
            var packageName: String? = null
            var downloadUrl: String? = null
        }

        private class UpgradeInfo {
            var manifest: JSONObject? = null
            var updates: List<UpdateInfo>? = null
        }

        private interface OnResponseListener {
            fun onResponse(info: UpgradeInfo)
        }

        private interface OnUpgradeListener {
            fun onUpgrade(succeed: Boolean)
        }

        private class ResponseHandler(private val mListener: OnResponseListener) : Handler() {

            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    1 -> mListener.onResponse(msg.obj as UpgradeInfo)
                }
            }
        }

        private var mResponseHandler: ResponseHandler? = null
        private var mProgressDlg: ProgressDialog? = null

        fun checkUpgrade() {
            mProgressDlg = ProgressDialog.show(mContext, "Small", "Checking for updates...")
            requestUpgradeInfo(Small.getBundleVersions(), object : OnResponseListener {
                override fun onResponse(info: UpgradeInfo) {
                    mProgressDlg!!.setMessage("Upgrading...")
                    upgradeBundles(info,
                            object : OnUpgradeListener {
                                override fun onUpgrade(succeed: Boolean) {
                                    mProgressDlg!!.dismiss()
                                    mProgressDlg = null
                                    val text = if (succeed)
                                        "Upgrade Success! Switch to background and back to foreground to see changes."
                                    else
                                        "Upgrade Failed!"
                                    Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show()
                                }
                            })
                }
            })
        }

        /**

         * @param versions
         * *
         * @param listener
         */
        private fun requestUpgradeInfo(versions: Map<*, *>, listener: OnResponseListener) {
            System.out.println(versions) // this should be passed as HTTP parameters
            mResponseHandler = ResponseHandler(listener)
            object : Thread() {
                override fun run() {
                    try {
                        // Example HTTP request to get the upgrade bundles information.
                        // Json format see http://wequick.github.io/small/upgrade/bundles.json
                        val url = URL("http://wequick.github.io/small/upgrade/bundles.json")
                        val conn = url.openConnection() as HttpURLConnection
                        val sb = StringBuilder()
                        val `is` = conn.inputStream
                        val buffer = ByteArray(1024)
                        var length: Int
                        while (true) {
                            length = `is`.read(buffer)
                            if (length == -1) break

                            sb.append(String(buffer, 0, length))
                        }

                        // Parse json
                        val jo = JSONObject(sb.toString())
                        val mf = if (jo.has("manifest")) jo.getJSONObject("manifest") else null
                        val updates = jo.getJSONArray("updates")
                        val N = updates.length()
                        val infos = ArrayList<UpdateInfo>(N)
                        for (i in 0..N - 1) {
                            val o = updates.getJSONObject(i)
                            val info = UpdateInfo()
                            info.packageName = o.getString("pkg")
                            info.downloadUrl = o.getString("url")
                            infos.add(info)
                        }

                        // Post message
                        val ui = UpgradeInfo()
                        ui.manifest = mf
                        ui.updates = infos
                        Message.obtain(mResponseHandler, 1, ui).sendToTarget()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                }
            }.start()
        }

        private class DownloadHandler(private val mListener: OnUpgradeListener) : Handler() {

            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    1 -> mListener.onUpgrade(msg.obj as Boolean)
                }
            }
        }

        private var mHandler: DownloadHandler? = null

        private fun upgradeBundles(info: UpgradeInfo,
                                   listener: OnUpgradeListener) {
            // Just for example, you can do this by OkHttp or something.
            mHandler = DownloadHandler(listener)
            object : Thread() {
                override fun run() {
                    try {
                        // Update manifest
                        if (info.manifest != null) {
                            if (!Small.updateManifest(info.manifest, false)) {
                                Message.obtain(mHandler, 1, false).sendToTarget()
                                return
                            }
                        }
                        // Download bundles
                        val updates = info.updates
                        for (u in updates!!) {
                            // Get the patch file for downloading
                            val bundle = Small.getBundle(u.packageName)
                            val file = bundle.patchFile

                            // Download
                            val url = URL(u.downloadUrl)
                            val urlConn = url.openConnection() as HttpURLConnection
                            val `is` = urlConn.inputStream
                            val os = FileOutputStream(file)
                            val buffer = ByteArray(1024)
                            var length: Int
                            while (true) {
                                length = `is`.read(buffer)
                                if (length == -1) break

                                os.write(buffer, 0, length)
                            }
                            os.flush()
                            os.close()
                            `is`.close()

                            // Upgrade
                            bundle.upgrade()
                        }

                        Message.obtain(mHandler, 1, true).sendToTarget()
                    } catch (e: IOException) {
                        e.printStackTrace()
                        Message.obtain(mHandler, 1, false).sendToTarget()
                    }

                }
            }.start()
        }
    }
}
