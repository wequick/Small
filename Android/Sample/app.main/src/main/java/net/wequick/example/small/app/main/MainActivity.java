package net.wequick.example.small.app.main;

import android.content.SharedPreferences;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;

import net.wequick.example.lib.analytics.AnalyticsManager;
import net.wequick.small.Small;
import net.wequick.example.small.lib.utils.UIUtils;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;
    private TabLayout mTabLayout;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    private static String[] sUris = new String[] {"home", "mine", "stub"};
    private static String[] sTitles = new String[] {"Home", "Mine", "Stub"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // 统计Small加载时间
        SharedPreferences sp = this.getSharedPreferences("profile", 0);
        long setUpStart = sp.getLong("setUpStart", 0);
        long setUpFinish = sp.getLong("setUpFinish", 0);
        long setUpTime = setUpFinish - setUpStart;

        float t = setUpTime / 1000000.0f;
        System.out.println("## Small setUp in " + t + " ms.");
        String key = Small.getIsNewHostApp() ? "setUpFirst" : "setUpNext";
        AnalyticsManager.traceTime(this, key, (int)t);

        // 统计加载完成到启动首个Activity时间
        t = (System.nanoTime() - setUpFinish) / 1000000.0f;
        System.out.println("## Small start first activity in " + t + " ms.");
        AnalyticsManager.traceTime(this, "startFirst", (int)t);


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        mTabLayout = (TabLayout) findViewById(R.id.tabs);
        mTabLayout.setupWithViewPager(mViewPager);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Small.openUri("https://github.com/wequick/Small/issues", MainActivity.this);
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            UIUtils.alert(MainActivity.this, "Hello!");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentStatePagerAdapter {

        private final FragmentManager mFragmentManager;
        private SparseArray<Fragment> mLazyFragments;
        private ArrayList<Integer> mLoadedPositions;

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
            mFragmentManager = fm;
            mLazyFragments = new SparseArray<>();
            mLoadedPositions = new ArrayList<>();
        }

        private void loadItem(final int position) {
            if (mLoadedPositions.contains(position)) {
                return;
            }
            mLoadedPositions.add(position);

            final String uri = sUris[position];
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final Fragment realFragment = Small.createObject("fragment-v4", uri, MainActivity.this);
                    if (realFragment == null) {
                        return;
                    }

                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Replace with the real fragment
                            System.out.println("## Lazy load fragment '" + uri + "'");
                            Fragment stubFragment = mLazyFragments.get(position);
                            mFragmentManager.beginTransaction().remove(stubFragment)
                                    .commit();
                            mLazyFragments.put(position, realFragment);
                            notifyDataSetChanged();
                        }
                    });
                }
            }).start();
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a StubFragment (defined as a static inner class below).
            Fragment fragment = mLazyFragments.get(position);
            if (fragment == null) {
                fragment = StubFragment.newInstance(position, sUris[position]);
                mLazyFragments.put(position, fragment);
            }
            
            // Load the real fragment
            loadItem(position);
            
            return fragment;
        }

        @Override
        public int getCount() {
            return sTitles.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return sTitles[position];
        }

        @Override
        public int getItemPosition(Object object) {
            if (object instanceof StubFragment) {
                // recall getItem
                return POSITION_NONE;
            }
            return POSITION_UNCHANGED;
        }
    }

    /**
     * A stub fragment containing a loading view.
     */
    public static class StubFragment extends Fragment {
        
        int position;
        String uri;

        /**
         * Returns a new instance of this fragment for the given position and uri.
         */
        public static StubFragment newInstance(int position, String uri) {
            StubFragment fragment = new StubFragment();
            fragment.position = position;
            fragment.uri = uri;
            return fragment;
        }

        public StubFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            TextView textView = (TextView) rootView.findViewById(R.id.section_label);
            textView.setText(getString(R.string.section_format, position, uri));
            return rootView;
        }
    }
}
