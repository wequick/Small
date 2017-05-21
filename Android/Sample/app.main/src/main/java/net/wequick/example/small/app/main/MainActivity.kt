package net.wequick.example.small.app.main

import android.content.SharedPreferences
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.design.widget.TabLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import android.widget.TextView

import net.wequick.example.lib.analytics.AnalyticsManager
import net.wequick.small.Small
import net.wequick.example.small.lib.utils.UIUtils

import java.util.HashMap

class MainActivity : AppCompatActivity() {

    /**
     * The [android.support.v4.view.PagerAdapter] that will provide
     * fragments for each of the sections. We use a
     * [FragmentPagerAdapter] derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * [android.support.v4.app.FragmentStatePagerAdapter].
     */
    private var mSectionsPagerAdapter: SectionsPagerAdapter? = null
    private var mTabLayout: TabLayout? = null

    /**
     * The [ViewPager] that will host the section contents.
     */
    private var mViewPager: ViewPager? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        // 统计Small加载时间
        val sp = this.getSharedPreferences("profile", 0)
        val setUpStart = sp.getLong("setUpStart", 0)
        val setUpFinish = sp.getLong("setUpFinish", 0)
        val setUpTime = setUpFinish - setUpStart

        var t = setUpTime / 1000000.0f
        println("## Small setUp in $t ms.")
        val key = if (Small.getIsNewHostApp()) "setUpFirst" else "setUpNext"
        AnalyticsManager.traceTime(this, key, t.toInt())

        // 统计加载完成到启动首个Activity时间
        t = (System.nanoTime() - setUpFinish) / 1000000.0f
        println("## Small start first activity in $t ms.")
        AnalyticsManager.traceTime(this, "startFirst", t.toInt())


        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container) as ViewPager
        mViewPager!!.adapter = mSectionsPagerAdapter

        mTabLayout = findViewById(R.id.tabs) as TabLayout
        mTabLayout!!.setupWithViewPager(mViewPager)

        val fab = findViewById(R.id.fab) as FloatingActionButton
        fab.setOnClickListener {
            //                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
            //                        .setAction("Action", null).show();
            Small.openUri("https://github.com/wequick/Small/issues", this@MainActivity)
        }

    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId


        if (id == R.id.action_settings) {
            UIUtils.alert(this@MainActivity, "Hello!")
            return true
        }

        return super.onOptionsItemSelected(item)
    }


    /**
     * A [FragmentPagerAdapter] that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    inner class SectionsPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

        override fun getItem(position: Int): Fragment {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            var fragment: Fragment? = Small.createObject<Fragment>("fragment-v4", sUris[position], this@MainActivity)
            if (fragment == null) {
                fragment = PlaceholderFragment.newInstance(position + 1)
            }
            return fragment
        }

        override fun getCount(): Int {
            return sTitles.size
        }

        override fun getPageTitle(position: Int): CharSequence {
            return sTitles[position]
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    class PlaceholderFragment : Fragment() {

        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View? {
            val rootView = inflater!!.inflate(R.layout.fragment_main, container, false)
            val textView = rootView.findViewById(R.id.section_label) as TextView
            textView.text = getString(R.string.section_format, arguments.getInt(ARG_SECTION_NUMBER))
            return rootView
        }

        companion object {
            /**
             * The fragment argument representing the section number for this
             * fragment.
             */
            private val ARG_SECTION_NUMBER = "section_number"

            /**
             * Returns a new instance of this fragment for the given section
             * number.
             */
            fun newInstance(sectionNumber: Int): PlaceholderFragment {
                val fragment = PlaceholderFragment()
                val args = Bundle()
                args.putInt(ARG_SECTION_NUMBER, sectionNumber)
                fragment.arguments = args
                return fragment
            }
        }
    }

    companion object {

        private val sUris = arrayOf("home", "mine", "stub")
        private val sTitles = arrayOf("Home", "Mine", "Stub")
    }
}
