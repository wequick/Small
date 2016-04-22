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
    <!-- overwrite app build.gradle -->
    <merge from="root/app-build.gradle.ftl"
             to="${escapeXmlAttribute(topOut)}/app/build.gradle" />

    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <merge from="root/res/values/attrs.xml"
             to="${escapeXmlAttribute(resOut)}/values/attrs.xml" />
    <merge from="root/res/values/colors.xml"
             to="${escapeXmlAttribute(resOut)}/values/colors.xml" />
    <merge from="root/res/values/styles.xml.ftl"
              to="${escapeXmlAttribute(resOut)}/values/styles.xml" />
    <instantiate from="root/res/layout/activity_launch.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />

    <merge from="root/res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <instantiate from="root/src/app_package/LaunchActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />

    <instantiate from="root/src/app_package/HostApp.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/HostApp.java" />

    <instantiate from="root/assets/bundle.json.ftl"
                   to="${escapeXmlAttribute(assetsOut)}/bundle.json" />

    <open file="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />
</recipe>
