package net.wequick.small;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.PatternMatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.util.TypedValue;

import net.wequick.small.util.JNIUtils;
import net.wequick.small.util.ReflectAccelerator;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

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
            public static int[] AndroidManifestApplication = {
                    0x01010000, 0x01010001, 0x01010003, 0x010102d3
            };
            public static int AndroidManifestApplication_theme = 0;
            public static int AndroidManifestApplication_label = 1; // for ABIs (Depreciated)
            public static int AndroidManifestApplication_name = 2;
            public static int AndroidManifestApplication_hardwareAccelerated = 3;
            // activity
            public static int[] AndroidManifestActivity = {
                    0x01010000, 0x01010001, 0x01010002, 0x01010003,
                    0x0101001d, 0x0101001e, 0x0101022b, 0x010102d3
            };
            public static int AndroidManifestActivity_theme = 0;
            public static int AndroidManifestActivity_label = 1;
            public static int AndroidManifestActivity_icon = 2;
            public static int AndroidManifestActivity_name = 3;
            public static int AndroidManifestActivity_launchMode = 4;
            public static int AndroidManifestActivity_screenOrientation = 5;
            public static int AndroidManifestActivity_windowSoftInputMode = 6;
            public static int AndroidManifestActivity_hardwareAccelerated = 7;
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

    private static byte[][] sHostCerts;

    private String mArchiveSourcePath;
    private String mPackageName;
    private WeakReference<byte[]> mReadBuffer;
    private PackageInfo mPackageInfo;
    private XmlResourceParser parser;
    private Resources res;
    private ConcurrentHashMap<String, List<IntentFilter>> mIntentFilters;
    private boolean mNonResources;
    private boolean mUsesHardwareAccelerated;
    private String mLibDir;
    private String mLauncherActivityName;

    private Context mContext;
    private ZipFile mZipFile;

    public BundleParser(File sourceFile, String packageName) {
        mArchiveSourcePath = sourceFile.getPath();
        mPackageName = packageName;
        mContext = Small.getContext();
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
            if (assmgr == null) return false;

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

        res = new Resources(assmgr, mContext.getResources().getDisplayMetrics(), null);
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

            // After gradle-small 0.9.0, we roll out
            // `The Small exclusive flags`
            //  F    F    F    F    F    F    F    F
            // 1111 1111 1111 1111 1111 1111 1111 1111
            // ^^^^ ^^^^ ^^^^ ^^^^ ^^^^
            //       ABI Flags (20)
            //                          ^
            //                 nonResources Flag (1)
            //                           ^^^ ^^^^ ^^^^
            //                     platformBuildVersionCode (11) => MAX=0x7FF=4095
            int flags = parser.getAttributeIntValue(null, "platformBuildVersionCode", 0);
            int abiFlags = (flags & 0xFFFFF000) >> 12;
            mNonResources = (flags & 0x800) != 0;

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
                    ApplicationInfo host = mContext.getApplicationInfo();
                    ApplicationInfo app = new ApplicationInfo(host);

                    sa = res.obtainAttributes(attrs,
                            R.styleable.AndroidManifestApplication);

                    String name = sa.getString(
                            R.styleable.AndroidManifestApplication_name);
                    if (name != null) {
                        app.className = name.intern();
                    } else {
                        app.className = null;
                    }

                    // Get the label value which used as ABI flags.
                    // This is depreciated, we read it from the `platformBuildVersionCode` instead.
                    // TODO: Remove this if the gradle-small 0.9.0 or above being widely used.
                    if (abiFlags == 0) {
                        TypedValue label = new TypedValue();
                        if (sa.getValue(R.styleable.AndroidManifestApplication_label, label)) {
                            if (label.type == TypedValue.TYPE_STRING) {
                                abiFlags = Integer.parseInt(label.string.toString());
                            } else {
                                abiFlags = label.data;
                            }
                        }
                        if (abiFlags != 0) {
                            throw new RuntimeException("Please recompile " + mPackageName
                                    + " use gradle-small 0.9.0 or above");
                        }
                    }

                    app.theme = sa.getResourceId(
                            R.styleable.AndroidManifestApplication_theme, 0);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        mUsesHardwareAccelerated = sa.getBoolean(
                                R.styleable.AndroidManifestApplication_hardwareAccelerated,
                                host.targetSdkVersion >= Build.VERSION_CODES.ICE_CREAM_SANDWICH);
                    }

                    mPackageInfo.applicationInfo = app;
                    break;
                }
            }

            if (abiFlags != 0) {
                String abi = JNIUtils.getExtractABI(abiFlags, Bundle.is64bit());
                if (abi != null) {
                    mLibDir = "lib/" + abi + "/";
                }
            }

            sa.recycle();
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
                ai.packageName = ai.applicationInfo.packageName;

                TypedArray sa = res.obtainAttributes(attrs,
                        R.styleable.AndroidManifestActivity);
                String name = sa.getString(R.styleable.AndroidManifestActivity_name);
                if (name != null) {
                    ai.name = ai.targetActivity = buildClassName(mPackageName, name);
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    boolean hardwareAccelerated = sa.getBoolean(
                            R.styleable.AndroidManifestActivity_hardwareAccelerated,
                            mUsesHardwareAccelerated);
                    if (hardwareAccelerated) {
                        ai.flags |= ActivityInfo.FLAG_HARDWARE_ACCELERATED;
                    }
                }

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
                            if (intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                                mLauncherActivityName = ai.name;
                            }
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

    public boolean verifyAndExtract(Bundle bundle, BundleExtractor extractor) {
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

        if (sHostCerts == null) {
            // Collect host certificates
            PackageManager pm = mContext.getPackageManager();
            try {
                Signature[] ss = pm.getPackageInfo(mContext.getPackageName(),
                        PackageManager.GET_SIGNATURES).signatures;
                if (ss != null) {
                    int N = ss.length;
                    sHostCerts = new byte[N][];
                    for (int i = 0; i < N; i++) {
                        sHostCerts[i] = ss[i].toByteArray();
                    }
                }
            } catch (PackageManager.NameNotFoundException ignored) {

            }
        }

        byte[][] hostCerts = sHostCerts;
        CrcVerifier crcVerifier = new CrcVerifier(mContext, bundle.getPackageName(), hostCerts);

        try {
            JarFile jarFile = new JarFile(mArchiveSourcePath);

            Enumeration entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry je = (JarEntry)entries.nextElement();
                if (je.isDirectory()) continue;

                String name = je.getName();
                if (name.startsWith("META-INF/")) continue;

                if (mLibDir != null && name.startsWith("lib/") && !name.startsWith(mLibDir)) {
                    // Ignore unused ABIs
                    continue;
                }

                // Verify CRC first
                int hash = name.hashCode();
                int crc = crcVerifier.getObscuredCrc(je.getCrc());
                if (crcVerifier.verifyCrc(hash, crc)) {
                    continue;
                }

                // Verify certificates
                Certificate[] localCerts = loadCertificates(jarFile, je,
                        readBuffer);

                if (localCerts == null) {
                    Log.e(TAG, "Package " + mPackageName
                            + " has no certificates at entry "
                            + name + "; ignoring!");
                    crcVerifier.close();
                    jarFile.close();
                    return false;
                } else {
                    // Ensure all certificates match.
                    for (int i=0; i<hostCerts.length; i++) {
                        boolean found = false;
                        for (int j=0; j<localCerts.length; j++) {
                            if (hostCerts[i] != null &&
                                    Arrays.equals(hostCerts[i], localCerts[j].getEncoded())) {
                                found = true;
                                break;
                            }
                        }
                        if (!found || hostCerts.length != localCerts.length) {
                            Log.e(TAG, "Package " + mPackageName
                                    + " has mismatched certificates at entry "
                                    + name + "; ignoring!");
                            crcVerifier.close();
                            jarFile.close();
                            return false;
                        }
                    }
                }

                // Extract file if needed
                File extractFile = extractor.getExtractFile(bundle, name);
                if (extractFile != null) {
                    if (mZipFile == null) {
                        mZipFile = new ZipFile(mArchiveSourcePath);
                    }
                    postExtractFile(mZipFile, je, extractFile);
                }

                // Record the new crc
                crcVerifier.recordCrc(hash, crc);
            }

            postSaveCrcs(crcVerifier);
            jarFile.close();

            synchronized (this.getClass()) {
                mReadBuffer = readBufferRef;
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

    private void postSaveCrcs(final CrcVerifier crcVerifier) {
        Bundle.postIO(new Runnable() {
            @Override
            public void run() {
                crcVerifier.saveCrcs();
            }
        });
    }

    private void postExtractFile(final ZipFile zipFile, final JarEntry je, final File extractFile) {
        Bundle.postIO(new Runnable() {
            @Override
            public void run() {
                RandomAccessFile out = null;
                try {
                    File dir = extractFile.getParentFile();
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    if (!extractFile.exists()) {
                        if (!extractFile.createNewFile()) {
                            throw new RuntimeException("Failed to create file: " + extractFile);
                        }
                    }
                    InputStream is = zipFile.getInputStream(je);
                    out = new RandomAccessFile(extractFile, "rw");
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer, 0, buffer.length)) != -1) {
                        out.write(buffer, 0, len);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
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

    public String getLibraryDirectory() {
        return mLibDir;
    }

    public String getDefaultActivityName() {
        if (mPackageInfo == null || mPackageInfo.activities == null) return null;
        if (mLauncherActivityName != null) return mLauncherActivityName;
        return mPackageInfo.activities[0].name;
    }

    /**
     * This method tells whether the bundle has `resources.arsc` entry, note that
     * it doesn't make sense until your bundle was built by `gradle-small` 0.9.0 or above.
     * @return <tt>true</tt> if doesn't have any resources
     */
    public boolean isNonResources() {
        return mNonResources;
    }

    protected void close() {
        if (mZipFile != null) {
            try {
                mZipFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mReadBuffer = null;
    }

    /**
     * Class to verify and save the crc of each bundle entry.
     * The SCRC (Small CRC) file format:
     * +--------------+
     * | Magic Number | 5343 5243
     * | Entry Count  |
     * | Entry #1     | each entry follows hash(int) and crc(int)
     * | Entry ...    |
     * | Entry #N     |
     * +--------------+
     */
    private static final class CrcVerifier {

        private static final String CRC_EXTENSION = ".scrc";
        private static final byte[] MAGIC_NUMBER = new byte[]{ 0x53, 0x43, 0x52, 0x43 }; // SCRC
        private static final int HEADER_SIZE = 8;
        private static final int ENTRY_SIZE = 8;
        private static final int CRC_OFFSET = 4;

        private RandomAccessFile mCrcFile;
        private int mSavedCrcCount;
        private SparseIntArray mSavedCrcs;
        private SparseIntArray mSavedCrcIndexes;
        private SparseIntArray mVerifiedCrcs;
        private SparseIntArray mUpdatedCrcs;
        private SparseIntArray mInsertedCrcs;
        private SparseIntArray mDeletedCrcIndexes;
        private int mObscureOffset;

        CrcVerifier(Context context, String packageName, byte[][] certs) {
            try {
                File crcPath = context.getFileStreamPath(CRC_EXTENSION);
                if (!crcPath.exists()) {
                    crcPath.mkdir();
                }
                File crcFile = new File(crcPath, packageName + CRC_EXTENSION);
                boolean exists = crcFile.exists();
                if (!exists) {
                    crcFile.createNewFile();
                }

                // Initialize certs
                mObscureOffset = Arrays.hashCode(certs[0]);

                // Parse the file
                mCrcFile = new RandomAccessFile(crcFile, "rw");
                if (exists) {
                    byte[] magic = new byte[MAGIC_NUMBER.length];
                    mCrcFile.read(magic);
                    if (!Arrays.equals(magic, MAGIC_NUMBER)) {
                        return;
                    }

                    mSavedCrcCount = mCrcFile.readInt();
                    if (mSavedCrcCount == 0) return;

                    mSavedCrcs = new SparseIntArray(mSavedCrcCount);
                    mSavedCrcIndexes = new SparseIntArray(mSavedCrcCount);
                    mDeletedCrcIndexes = new SparseIntArray(mSavedCrcCount);
                    for (int i = 0; i < mSavedCrcCount; i++) {
                        int hash = mCrcFile.readInt();
                        int crc = mCrcFile.readInt();
                        mSavedCrcs.put(hash, crc);
                        mSavedCrcIndexes.put(hash, i);
                        mDeletedCrcIndexes.put(hash, i);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private boolean verifyCrc(int hash, int crc) {
            int savedCrc = (mSavedCrcs == null) ? 0 : mSavedCrcs.get(hash);
            // Record the current crc
            if (mVerifiedCrcs == null) {
                mVerifiedCrcs = new SparseIntArray();
            }
            mVerifiedCrcs.put(hash, crc);
            return (savedCrc == crc);
        }

        private void recordCrc(int hash, int crc) {
            int savedIndex = -1;
            if (mSavedCrcIndexes != null) {
                savedIndex = mSavedCrcIndexes.get(hash, -1);
            }
            if (savedIndex >= 0) {
                // If we had saved, update it
                if (mUpdatedCrcs == null) {
                    mUpdatedCrcs = new SparseIntArray();
                }
                mUpdatedCrcs.put(savedIndex, crc);
            } else {
                // Otherwise, insert a new one
                if (mInsertedCrcs == null) {
                    mInsertedCrcs = new SparseIntArray();
                }
                mInsertedCrcs.put(hash, crc);
            }
        }

        private void saveCrcs() {
            if (mVerifiedCrcs == null) return;

            try {
                int i;
                int N = mVerifiedCrcs.size();
                if (mSavedCrcs == null || mSavedCrcs.size() > N) {
                    // If first created or something deleted, we should rewrite all the data
                    if (mSavedCrcs == null) {
                        mCrcFile.seek(0);
                        mCrcFile.write(MAGIC_NUMBER);
                    } else {
                        mCrcFile.seek(MAGIC_NUMBER.length);
                    }
                    mCrcFile.writeInt(N);
                    for (i = 0; i < N; i++) {
                        mCrcFile.writeInt(mVerifiedCrcs.keyAt(i));
                        mCrcFile.writeInt(mVerifiedCrcs.valueAt(i));
                    }
                    mCrcFile.setLength(HEADER_SIZE + N * ENTRY_SIZE);
                } else {
                    // Otherwise, we can update the crc in specify offset faster
                    if (mUpdatedCrcs != null) {
                        N = mUpdatedCrcs.size();
                        for (i = 0; i < N; i++) {
                            int offset = mUpdatedCrcs.keyAt(i);
                            int crc = mUpdatedCrcs.valueAt(i);
                            mCrcFile.seek(HEADER_SIZE + offset * ENTRY_SIZE + CRC_OFFSET);
                            mCrcFile.writeInt(crc);
                        }
                    }
                    if (mInsertedCrcs != null) {
                        N = mInsertedCrcs.size();
                        mCrcFile.seek(MAGIC_NUMBER.length);
                        mCrcFile.writeInt(mSavedCrcCount + N); // update the entry size

                        long len = mCrcFile.length();
                        mCrcFile.seek(len);
                        long addedSize = 0;
                        for (i = 0; i < N; i++) {
                            mCrcFile.writeInt(mInsertedCrcs.keyAt(i));
                            mCrcFile.writeInt(mInsertedCrcs.valueAt(i));
                            addedSize += ENTRY_SIZE;
                        }
                        mCrcFile.setLength(len + addedSize);
                    }
                }

                mCrcFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void close() {
            if (mCrcFile != null) {
                try {
                    mCrcFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private int getObscuredCrc(long crc) {
            return (int)((crc & 0xFFFFFFFFL) + mObscureOffset);
        }
    }
}
