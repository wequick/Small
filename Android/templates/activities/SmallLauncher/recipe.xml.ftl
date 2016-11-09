<?xml version="1.0"?>
<recipe>
    <dependency mavenUrl="com.android.support:support-v4:${buildApi}.+" />

<#if copyGradleSupported>
    <!-- overwrite root build.gradle -->
    <instantiate from="root/root-full-build.gradle.ftl"
                   to="${escapeXmlAttribute(topOut)}/build.gradle" />
<#else>
    <!-- FIXME: merge root build.gradle, but seems to cannot be successfully applied
         the buildscript dependencies, is this a bug of Android Studio? -->
    <merge from="root/root-part-build.gradle.ftl"
             to="${escapeXmlAttribute(topOut)}/build.gradle" />
</#if>

    <!-- overwrite AndroidManifest.xml -->
    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <!-- overwrite colors.xml -->
    <merge from="root/res/values/colors.xml"
             to="${escapeXmlAttribute(resOut)}/values/colors.xml" />
    <!-- overwrite styles.xml -->
    <merge from="root/res/values/styles.xml.ftl"
              to="${escapeXmlAttribute(resOut)}/values/styles.xml" />

    <!-- generate LaunchActivity.java -->
    <instantiate from="root/src/app_package/LaunchActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />

    <!-- generate SmallApp.java -->
    <instantiate from="root/src/app_package/SmallApp.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/SmallApp.java" />

    <!-- generate bundle.json -->
    <instantiate from="root/assets/bundle.json.ftl"
                   to="${escapeXmlAttribute(assetsOut)}/bundle.json" />

    <!-- copy drawables -->
    <copy from="root/res/drawable/splash_layers.xml"
            to="${escapeXmlAttribute(resOut)}/drawable/splash_layers.xml" />
    <copy from="root/res/drawable-xhdpi/ic_small.png"
            to="${escapeXmlAttribute(resOut)}/drawable-xhdpi/ic_small.png" />
    <copy from="root/res/drawable-xhdpi/text_copyright.png"
            to="${escapeXmlAttribute(resOut)}/drawable-xhdpi/text_copyright.png" />
    <copy from="root/res/drawable-xhdpi/text_loading.png"
            to="${escapeXmlAttribute(resOut)}/drawable-xhdpi/text_loading.png" />

    <!-- open the root build.gradle -->
    <open file="${escapeXmlAttribute(topOut)}/build.gradle" />
</recipe>
