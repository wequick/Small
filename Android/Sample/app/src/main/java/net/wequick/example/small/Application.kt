package net.wequick.example.small

import android.app.ProgressDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast

import net.wequick.small.Small
import net.wequick.small.webkit.WebView
import net.wequick.small.webkit.WebViewClient

/**
 * Created by galen on 15/11/3.
 */
class Application : android.app.Application() {
    init {
        // This should be the very first of the application lifecycle.
        // It's also ahead of the installing of content providers by what we can avoid
        // the ClassNotFound exception on if the provider is unimplemented in the host.
        Small.preSetUp(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Optional
        Small.setBaseUri("http://code.wequick.net/small-sample/")
        Small.setWebViewClient(MyWebViewClient())
        Small.setLoadFromAssets(BuildConfig.LOAD_FROM_ASSETS)
    }

    private class MyWebViewClient : WebViewClient() {

        private var mBar: ProgressBar? = null

        override fun onPageStarted(context: Context, view: WebView, url: String, favicon: Bitmap) {

        }

        override fun onPageFinished(context: Context, view: WebView, url: String) {

        }

        override fun onReceivedError(context: Context, view: WebView, errorCode: Int, description: String, failingUrl: String) {

        }

        override fun onProgressChanged(context: Context, view: WebView, newProgress: Int) {
            super.onProgressChanged(context, view, newProgress)

            val parent = view.parent as ViewGroup
            if (mBar == null) {
                mBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
                val lp = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        5)
                parent.addView(mBar, lp)
            }

            if (newProgress == 100) {
                Thread(Runnable {
                    var progress = mBar!!.progress
                    while (progress <= 100) {
                        try {
                            Thread.sleep(1)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }

                        mBar!!.progress = progress++
                        mBar!!.postInvalidate()
                    }

                    parent.postDelayed({
                        parent.removeView(mBar)
                        mBar = null
                    }, 300)
                }).start()
            } else {
                mBar!!.progress = newProgress
            }
        }
    }
}
