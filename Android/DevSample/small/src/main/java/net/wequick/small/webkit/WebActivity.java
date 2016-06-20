/*
 * Copyright 2015-present wequick.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package net.wequick.small.webkit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import net.wequick.small.Small;

import java.util.HashMap;

/**
 * This class is the target activity launched by {@link net.wequick.small.WebBundleLauncher}.
 *
 * <p>The content view of this class is a WebView with an url passed by the launcher.
 *
 * <p>For the ability of initialize native navigation bar by html content, we use the
 * {@link android.support.v7.app.AppCompatActivity} to provide a common style action bar.
 *
 * @see WebView
 */
public class WebActivity extends AppCompatActivity {

    private static SparseArray<CharSequence> sUrlTitles;

    private WebView mWebView;
    private String mUrl;
    private boolean mCanSetTitle = true;
    private boolean mFullscreen = false;
    private boolean mHasInitMenu = false;
    private Menu mOptionsMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int theme = Small.getWebActivityTheme();
        if (theme != 0) setTheme(theme);

        boolean fullscreen = false;
        CharSequence queryTitle;
        Uri uri = Small.getUri(this);
        if (uri != null) {
            String param = uri.getQueryParameter("_fullscreen");
            if (param != null) {
                fullscreen = param.equals("1");
            }
            queryTitle = uri.getQueryParameter("_title");
            if (queryTitle != null) {
                super.setTitle(queryTitle);
                mCanSetTitle = false;
            }
        }

        // Init actionBar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if (fullscreen) {
                actionBar.hide();
            } else {
                actionBar.show();
                Activity parent = getParent();
                if (parent == null) { // If is created by a LocalActivityManager, parent is not null
                    actionBar.setDisplayHomeAsUpEnabled(true);
                }
            }
        }
        mFullscreen = fullscreen;

        // Initialize content wrapper
        RelativeLayout wrapper = new RelativeLayout(this);
        wrapper.setGravity(Gravity.CENTER);
        setContentView(wrapper);

        // Initialize webView
        mWebView = new WebView(this);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        wrapper.addView(mWebView, 0, layoutParams);

        // Try to load title from cache
        mUrl = getIntent().getStringExtra("url");
        if (mCanSetTitle) {
            CharSequence title = getCacheTitle(mUrl);
            if (title != null) {
                super.setTitle(title);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mWebView.loadUrl(mUrl);
    }

    @Override
    public void setTitle(CharSequence title) {
        if (!mCanSetTitle)
            return;

        cacheTitle(mUrl, title);
        super.setTitle(title);
    }

    private void cacheTitle(String url, CharSequence title) {
        if (sUrlTitles == null) {
            sUrlTitles = new SparseArray<CharSequence>();
        }
        sUrlTitles.put(url.hashCode(), title);
    }

    private CharSequence getCacheTitle(String url) {
        if (sUrlTitles == null) return null;
        return sUrlTitles.get(url.hashCode());
    }

    // TODO: Simulate html window's onpageshow and onpagehide event
//    @Override
//    protected void onRestart() {
//        super.onRestart();
//        // Simulate window.onpageshow
//        mWebView.loadJs("window.onpageshow();");
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        // Simulate window.onpagehide
//        mWebView.loadJs("window.onpagehide();");
//    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        String url = getIntent().getStringExtra("url");
//        WebViewPool.getInstance().free(url);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        if (mWebView != null) {
            initMenu(mWebView.getMetaContents());
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent upIntent = NavUtils.getParentActivityIntent(this);
            if (upIntent == null) {
                finish();
            } else {
                if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                    // This activity is NOT part of this app's task, so create a new task
                    // when navigating up, with a synthesized back stack.
                    TaskStackBuilder.create(this)
                            // Add all of this activity's parents to the back stack
                            .addNextIntentWithParentStack(upIntent)
                                    // Navigate up to the closest parent
                            .startActivities();
                } else {
                    // This activity is part of this app's task, so simply
                    // navigate up to the logical parent activity.
                    NavUtils.navigateUpTo(this, upIntent);
                }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void initMenu(HashMap<String, HashMap<String, String>> metaContents) {
        if (metaContents == null) return;
        if (mOptionsMenu == null) return;
        if (mHasInitMenu) return;

        // Init right action item
        HashMap<String, String> content = metaContents.get("right");
        if (content != null) {
            addMenuItem(content, false);
        }
        // Init right more item
        content = metaContents.get("more");
        if (content != null) {
            addMenuItem(content, true);
        }

        mHasInitMenu = true;
    }

    private void addMenuItem(HashMap<String, String> content, boolean isMore) {
        MenuItem menuItem;
        do {
            String type = content.get("type");
            // Text without icon
            if (type == null) {
                String title = content.get("title");
                menuItem = mOptionsMenu.add(0, 0, 0, title);
                break;
            }

            Drawable icon = null; // TODO: Export interface to get user defined icon
            if (icon != null) {
                menuItem = mOptionsMenu.add(0, 0, 0, null).setIcon(icon);
                break;
            }

            // Android system icon
            int iconRes = android.R.drawable.ic_menu_info_details;
            if (type.equals("share")) {
                iconRes = android.R.drawable.ic_menu_share;
            } else if (type.equals("add")) {
                iconRes = android.R.drawable.ic_menu_add;
            } else if (type.equals("more")) {
                iconRes = android.R.drawable.ic_menu_more;
            } // TODO: Add more

            menuItem = mOptionsMenu.add(0, 0, 0, null).setIcon(iconRes);
        } while (false);

        int showType = isMore ? MenuItemCompat.SHOW_AS_ACTION_NEVER :
                MenuItemCompat.SHOW_AS_ACTION_ALWAYS;
        MenuItemCompat.setShowAsAction(menuItem, showType);
        final String onclick = content.get("onclick");
        if (onclick != null) {
            menuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    if (mWebView != null) {
                        mWebView.loadJs(onclick);
                    }
                    return true;
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Small.REQUEST_CODE_DEFAULT) {
            if (data != null) {
//                String ret = data.getStringExtra(Small.EXTRAS_KEY_RET);
//                mWebView.loadJs("if(!!onresult)onresult('" + ret + "')");
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void finish() {
        if (mWebView == null) {
            super.finish();
            return;
        }

        mWebView.close(new WebView.OnResultListener() {
            @Override
            public void onResult(String ret) {
                if (!ret.equals("false")) {
                    finish(ret);
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (mFullscreen) {
            return;
        }
        super.onBackPressed();
    }

    public void finish(String ret) {
        // Set result and finish
        Intent intent = new Intent();
        intent.putExtra(Small.EXTRAS_KEY_RET, ret);
        setResult(RESULT_OK, intent);
        super.finish();
    }
}
