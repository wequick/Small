// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        jcenter()
<#if mavenUrl != "mavenCentral">
        maven {
            url '${mavenUrl}'
        }
</#if>
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:${gradlePluginVersion}'
        classpath 'net.wequick.tools.build:gradle-small:${smallPluginVersion}'

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        jcenter()
<#if mavenUrl != "mavenCentral">
        maven {
            url '${mavenUrl}'
        }
</#if>
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}

apply plugin: 'net.wequick.small'

small {
    // Whether build all the bundles to host assets.
    buildToAssets = false

    // The compiling `net.wequick.small:small` aar version.
    aarVersion = "${smallAarVersion}"

    // The project-wide Android version configurations
    android {
        compileSdkVersion = <#if buildApiString?matches("^\\d+$")>${buildApiString}<#else>'${buildApiString}'</#if>
        buildToolsVersion = "${buildToolsVersion}"
        supportVersion = "${buildApi}.+" // replace this with an explicit value
    }
}
