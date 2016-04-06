package net.wequick.small.util;

import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.PatternMatcher;
import android.util.AttributeSet;
import android.util.Log;

import net.wequick.small.Small;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * This class consists exclusively of methods that operate on external bundle.
 *
 * It's a lite edition of <a href="https://github.com/android/platform_frameworks_base/blob/gingerbread-release/core%2Fjava%2Fandroid%2Fcontent%2Fpm%2FPackageParser.java">PackageParser.java</a>
 * which focus on:
 * <ul>
 *     <li>package.versionCode</li>
 *     <li>package.versionName</li>
 *     <li>package.theme</li>
 *     <li>activities</li>
 * </ul>
 *
 * Furthermore, this class will also collect the <b>&lt;intent-filter&gt;</b> information for each activity.
 */
public class BundleParser {

    private static final String TAG = "BundleParser";

    /* com.android.internal.R.styleable.* on
     * https://github.com/android/platform_frameworks_base/blob/gingerbread-release/core%2Fres%2Fres%2Fvalues%2Fpublic.xml
     */
    private static final class R {
        public static final class styleable {
            // manifest
            public static final int[] AndroidManifest = {0x0101021b, 0x0101021c};
            public static final int AndroidManifest_versionCode = 0;
            public static final int AndroidManifest_versionName = 1;
            // application
            public static int[] AndroidManifestApplication = {0x01010000, 0x01010003};
            public static int AndroidManifestApplication_theme = 0;
            public static int AndroidManifestApplication_name = 1;
            // activity
            public static int[] AndroidManifestActivity = {
                    0x01010000, 0x01010001, 0x01010002, 0x01010003,
                    0x0101001d, 0x0101001e, 0x0101022b
            };
            public static int AndroidManifestActivity_theme = 0;
            public static int AndroidManifestActivity_label = 1;
            public static int AndroidManifestActivity_icon = 2;
            public static int AndroidManifestActivity_name = 3;
            public static int AndroidManifestActivity_launchMode = 4;
            public static int AndroidManifestActivity_screenOrientation = 5;
            public static int AndroidManifestActivity_windowSoftInputMode = 6;
            // data (for intent-filter)
            public static int[] AndroidManifestData = {
                    0x01010026, 0x01010027, 0x01010028, 0x01010029,
                    0x0101002a, 0x0101002b, 0x0101002c
            };
            public static int AndroidManifestData_mimeType = 0;
            public static int AndroidManifestData_scheme = 1;
            public static int AndroidManifestData_host = 2;
            public static int AndroidManifestData_port = 3;
            public static int AndroidManifestData_path = 4;
            public static int AndroidManifestData_pathPrefix = 5;
            public static int AndroidManifestData_pathPattern = 6;
        }
    }

    private String mArchiveSourcePath;
    private String mPackageName;
    private WeakReference<byte[]> mReadBuffer;
    private PackageInfo mPackageInfo;
    private XmlResourceParser parser;
    private Resources res;
    private ConcurrentHashMap<String, List<IntentFilter>> mIntentFilters;

    public BundleParser(File sourceFile, String packageName) {
        mArchiveSourcePath = sourceFile.getPath();
        mPackageName = packageName;
    }

    public static BundleParser parsePackage(File sourceFile, String packageName) {
        if (sourceFile == null || !sourceFile.exists()) return null;

        BundleParser bp = new BundleParser(sourceFile, packageName);
        if (!bp.parsePackage()) return null;

        return bp;
    }

    public boolean parsePackage() {
        AssetManager assmgr = null;
        boolean assetError = true;
        try {
            assmgr = ReflectAccelerator.newAssetManager();
            int cookie = ReflectAccelerator.addAssetPath(assmgr, mArchiveSourcePath);
            if(cookie != 0) {
                parser = assmgr.openXmlResourceParser(cookie, "AndroidManifest.xml");
                assetError = false;
            } else {
                Log.w(TAG, "Failed adding asset path:"+mArchiveSourcePath);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to read AndroidManifest.xml of "
                    + mArchiveSourcePath, e);
        }
        if (assetError) {
            if (assmgr != null) assmgr.close();
            return false;
        }

        res = new Resources(assmgr, Small.getContext().getResources().getDisplayMetrics(), null);
        return parsePackage(res, parser);
    }

    private boolean parsePackage(Resources res, XmlResourceParser parser) {
        AttributeSet attrs = parser;
        mPackageInfo = new PackageInfo();

        try {
            int type;
            while ((type=parser.next()) != XmlResourceParser.START_TAG
                    && type != XmlResourceParser.END_DOCUMENT) ;

            // <manifest ...
            mPackageInfo.packageName = parser.getAttributeValue(null, "package").intern();
            TypedArray sa = res.obtainAttributes(attrs,
                    R.styleable.AndroidManifest);
            mPackageInfo.versionCode = sa.getInteger(
                    R.styleable.AndroidManifest_versionCode, 0);
            String versionName = sa.getString(
                    R.styleable.AndroidManifest_versionName);
            if (versionName != null) {
                mPackageInfo.versionName = versionName.intern();
            }

            // <application ...
            while ((type=parser.next()) != XmlResourceParser.END_DOCUMENT) {
                if (type == XmlResourceParser.TEXT) {
                    continue;
                }

                String tagName = parser.getName();
                if (tagName.equals("application")) {
                    ApplicationInfo app = new ApplicationInfo();
                    sa = res.obtainAttributes(attrs,
                            R.styleable.AndroidManifestApplication);

                    String name = sa.getString(
                            R.styleable.AndroidManifestApplication_name);
                    if (name != null) {
                        app.name = app.className = name.intern();
                    }
                    app.theme = sa.getResourceId(
                            R.styleable.AndroidManifestApplication_theme, 0);
                    mPackageInfo.applicationInfo = app;
                    break;
                }
            }

            sa.recycle();
            collectCertificates();
            return true;
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean collectActivities() {
        if (mPackageInfo == null || mPackageInfo.applicationInfo == null) return false;
        AttributeSet attrs = parser;

        int type;
        try {
            List<ActivityInfo> activities = new ArrayList<ActivityInfo>();
            while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT) {
                if (type != XmlResourceParser.START_TAG) {
                    continue;
                }

                String tagName = parser.getName();
                if (!tagName.equals("activity")) continue;

                // <activity ...
                ActivityInfo ai = new ActivityInfo();
                ai.applicationInfo = mPackageInfo.applicationInfo;

                TypedArray sa = res.obtainAttributes(attrs,
                        R.styleable.AndroidManifestActivity);
                String name = sa.getString(R.styleable.AndroidManifestActivity_name);
                if (name != null) {
                    ai.name = buildClassName(mPackageName, name);
                }
                ai.labelRes = sa.getResourceId(R.styleable.AndroidManifestActivity_label, 0);
                ai.icon = sa.getResourceId(R.styleable.AndroidManifestActivity_icon, 0);
                ai.theme = sa.getResourceId(R.styleable.AndroidManifestActivity_theme, 0);
                ai.launchMode = sa.getInteger(R.styleable.AndroidManifestActivity_launchMode, 0);
                //noinspection ResourceType
                ai.screenOrientation = sa.getInt(
                        R.styleable.AndroidManifestActivity_screenOrientation,
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                ai.softInputMode = sa.getInteger(
                        R.styleable.AndroidManifestActivity_windowSoftInputMode, 0);
                activities.add(ai);

                sa.recycle();

                // <intent-filter ...
                List<IntentFilter> intents = new ArrayList<IntentFilter>();
                int outerDepth = parser.getDepth();
                while ((type=parser.next()) != XmlResourceParser.END_DOCUMENT
                        && (type != XmlResourceParser.END_TAG
                        || parser.getDepth() > outerDepth)) {
                    if (type == XmlResourceParser.END_TAG || type == XmlResourceParser.TEXT) {
                        continue;
                    }

                    if (parser.getName().equals("intent-filter")) {
                        IntentFilter intent = new IntentFilter();
                        
                        parseIntent(res, parser, attrs, true, true, intent);

                        if (intent.countActions() == 0) {
                            Log.w(TAG, "No actions in intent filter at "
                                    + mArchiveSourcePath + " "
                                    + parser.getPositionDescription());
                        } else {
                            intents.add(intent);
                        }
                    }
                }

                if (intents.size() > 0) {
                    if (mIntentFilters == null) {
                        mIntentFilters = new ConcurrentHashMap<String, List<IntentFilter>>();
                    }
                    mIntentFilters.put(ai.name, intents);
                }
            }

            int N = activities.size();
            if (N > 0) {
                mPackageInfo.activities = new ActivityInfo[N];
                mPackageInfo.activities = activities.toArray(mPackageInfo.activities);
            }
            return true;
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean collectCertificates() {
        WeakReference<byte[]> readBufferRef;
        byte[] readBuffer = null;
        synchronized (this.getClass()) {
            readBufferRef = mReadBuffer;
            if (readBufferRef != null) {
                mReadBuffer = null;
                readBuffer = readBufferRef.get();
            }
            if (readBuffer == null) {
                readBuffer = new byte[8192];
                readBufferRef = new WeakReference<byte[]>(readBuffer);
            }
        }

        try {
            JarFile jarFile = new JarFile(mArchiveSourcePath);

            Certificate[] certs = null;


            Enumeration entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry je = (JarEntry)entries.nextElement();
                if (je.isDirectory()) continue;
                if (je.getName().startsWith("META-INF/")) continue;
                Certificate[] localCerts = loadCertificates(jarFile, je,
                        readBuffer);
                if (false) {
                    Log.i(TAG, "File " + mArchiveSourcePath + " entry " + je.getName()
                            + ": certs=" + certs + " ("
                            + (certs != null ? certs.length : 0) + ")");
                }
                if (localCerts == null) {
                    Log.e(TAG, "Package " + mPackageName
                            + " has no certificates at entry "
                            + je.getName() + "; ignoring!");
                    jarFile.close();
                    return false;
                } else if (certs == null) {
                    certs = localCerts;
                } else {
                    // Ensure all certificates match.
                    for (int i=0; i<certs.length; i++) {
                        boolean found = false;
                        for (int j=0; j<localCerts.length; j++) {
                            if (certs[i] != null &&
                                    certs[i].equals(localCerts[j])) {
                                found = true;
                                break;
                            }
                        }
                        if (!found || certs.length != localCerts.length) {
                            Log.e(TAG, "Package " + mPackageName
                                    + " has mismatched certificates at entry "
                                    + je.getName() + "; ignoring!");
                            jarFile.close();
                            return false;
                        }
                    }
                }
            }

            jarFile.close();

            synchronized (this.getClass()) {
                mReadBuffer = readBufferRef;
            }

            if (certs != null && certs.length > 0) {
                final int N = certs.length;
                mPackageInfo.signatures = new Signature[certs.length];
                for (int i=0; i<N; i++) {
                    mPackageInfo.signatures[i] = new Signature(
                            certs[i].getEncoded());
                }
            } else {
                Log.e(TAG, "Package " + mPackageName
                        + " has no certificates; ignoring!");
                return false;
            }
        } catch (CertificateEncodingException e) {
            Log.w(TAG, "Exception reading " + mArchiveSourcePath, e);
            return false;
        } catch (IOException e) {
            Log.w(TAG, "Exception reading " + mArchiveSourcePath, e);
            return false;
        } catch (RuntimeException e) {
            Log.w(TAG, "Exception reading " + mArchiveSourcePath, e);
            return false;
        }
        return true;
    }


    private static final String ANDROID_RESOURCES
            = "http://schemas.android.com/apk/res/android";

    private boolean parseIntent(Resources res, XmlResourceParser parser, AttributeSet attrs,
                                boolean allowGlobs, boolean allowAutoVerify, IntentFilter outInfo)
            throws XmlPullParserException, IOException {
        TypedArray sa;
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && (type != XmlResourceParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlResourceParser.END_TAG || type == XmlResourceParser.TEXT) {
                continue;
            }

            String nodeName = parser.getName();
            if (nodeName.equals("action")) {
                String value = attrs.getAttributeValue(
                        ANDROID_RESOURCES, "name");
                if (value == null || value.length() == 0) {
                    return false;
                }
                skipCurrentTag(parser);

                outInfo.addAction(value);
            } else if (nodeName.equals("category")) {
                String value = attrs.getAttributeValue(
                        ANDROID_RESOURCES, "name");
                if (value == null || value.length() == 0) {
                    return false;
                }
                skipCurrentTag(parser);

                outInfo.addCategory(value);

            } else if (nodeName.equals("data")) {
                sa = res.obtainAttributes(attrs,
                        R.styleable.AndroidManifestData);

                String str = sa.getString(
                        R.styleable.AndroidManifestData_mimeType);
                if (str != null) {
                    try {
                        outInfo.addDataType(str);
                    } catch (IntentFilter.MalformedMimeTypeException e) {
                        sa.recycle();
                        return false;
                    }
                }

                str = sa.getString(
                        R.styleable.AndroidManifestData_scheme);
                if (str != null) {
                    outInfo.addDataScheme(str);
                }

                String host = sa.getString(
                        R.styleable.AndroidManifestData_host);
                String port = sa.getString(
                        R.styleable.AndroidManifestData_port);
                if (host != null) {
                    outInfo.addDataAuthority(host, port);
                }

                str = sa.getString(
                        R.styleable.AndroidManifestData_path);
                if (str != null) {
                    outInfo.addDataPath(str, PatternMatcher.PATTERN_LITERAL);
                }

                str = sa.getString(
                        R.styleable.AndroidManifestData_pathPrefix);
                if (str != null) {
                    outInfo.addDataPath(str, PatternMatcher.PATTERN_PREFIX);
                }

                str = sa.getString(
                        R.styleable.AndroidManifestData_pathPattern);
                if (str != null) {
                    if (!allowGlobs) {
                        return false;
                    }
                    outInfo.addDataPath(str, PatternMatcher.PATTERN_SIMPLE_GLOB);
                }

                sa.recycle();
                skipCurrentTag(parser);
            } else {
                return false;
            }
        }

        return true;
    }

    private static void skipCurrentTag(XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type=parser.next()) != XmlResourceParser.END_DOCUMENT
                && (type != XmlResourceParser.END_TAG
                || parser.getDepth() > outerDepth)) {
        }
    }

    private Certificate[] loadCertificates(JarFile jarFile, JarEntry je,
                                           byte[] readBuffer) {
        try {
            // We must read the stream for the JarEntry to retrieve
            // its certificates.
            InputStream is = new BufferedInputStream(jarFile.getInputStream(je));
            while (is.read(readBuffer, 0, readBuffer.length) != -1) {
                // not using
            }
            is.close();
            return je != null ? je.getCertificates() : null;
        } catch (IOException e) {
            Log.w(TAG, "Exception reading " + je.getName() + " in "
                    + jarFile.getName(), e);
        } catch (RuntimeException e) {
            Log.w(TAG, "Exception reading " + je.getName() + " in "
                    + jarFile.getName(), e);
        }
        return null;
    }

    private static String buildClassName(String pkg, CharSequence clsSeq) {
        if (clsSeq == null || clsSeq.length() <= 0) {
            return null;
        }
        String cls = clsSeq.toString();
        char c = cls.charAt(0);
        if (c == '.') {
            return (pkg + cls).intern();
        }
        if (cls.indexOf('.') < 0) {
            StringBuilder b = new StringBuilder(pkg);
            b.append('.');
            b.append(cls);
            return b.toString().intern();
        }
        if (c >= 'a' && c <= 'z') {
            return cls.intern();
        }
        return null;
    }

    public PackageInfo getPackageInfo() {
        return mPackageInfo;
    }

    public String getSourcePath() {
        return mArchiveSourcePath;
    }

    public ConcurrentHashMap<String, List<IntentFilter>> getIntentFilters() {
        return mIntentFilters;
    }
}
