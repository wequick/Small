package net.wequick.example.lib.analytics

import android.content.Context
import android.os.Build

import com.umeng.analytics.MobclickAgent

import java.io.BufferedReader
import java.io.File
import java.io.FileFilter
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.util.HashMap
import java.util.regex.Pattern

/**
 * Created by galen on 16/5/31.
 */
object AnalyticsManager {

    private var sDeviceInfo: MutableMap<String, String>? = null

    fun onEventValue(context: Context, key: String,
                     extensions: Map<String, String>,
                     count: Int) {
        MobclickAgent.onEventValue(context, key, extensions, count)
    }

    fun traceTime(context: Context, key: String, time: Int) {
        if (sDeviceInfo == null) {
            sDeviceInfo = HashMap<String, String>()
            sDeviceInfo!!.put("model", Build.MODEL)
            sDeviceInfo!!.put("os", Build.VERSION.RELEASE + "(SDK" + Build.VERSION.SDK_INT + ")")
            sDeviceInfo!!.put("cpu", cpuName!!)
            sDeviceInfo!!.put("cores", numCores.toString() + "")
        }
        onEventValue(context, key, sDeviceInfo!!, time)
    }

    private val cpuName: String?
        get() {
            try {
                val fr = FileReader("/proc/cpuinfo")
                val br = BufferedReader(fr)
                val text = br.readLine()
                val array = text.split(":\\s+".toRegex(), 2).toTypedArray()
                for (i in array.indices) {
                }
                return array[1]
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return null
        }

    private // Private Class to display only CPU devices in the directory listing
            // Check if filename is "cpu", followed by a single digit number
            // Get directory containing CPU info
            // Filter to only list the devices we care about
            // Return the number of cores (virtual CPU devices)
            // Default to return 1 core
    val numCores: Int
        get() {
            class CpuFilter : FileFilter {
                override fun accept(pathname: File): Boolean {
                    return Pattern.matches("cpu[0-9]", pathname.name)
                }
            }

            try {
                val dir = File("/sys/devices/system/cpu/")
                val files = dir.listFiles(CpuFilter())
                return files.size
            } catch (e: Exception) {
                return 1
            }

        }
}
