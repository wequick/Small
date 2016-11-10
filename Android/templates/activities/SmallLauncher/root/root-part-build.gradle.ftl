//
//
////-------------------------------------------------------------------
//// Small plugin
////
//// Cannot automatically merge the buildscript in Android Studio 2.0+,
//// uncomment this section and sync the project to go into force.
////-------------------------------------------------------------------
//
//buildscript  {
//    dependencies {
//        classpath 'net.wequick.tools.build:gradle-small:${smallPluginVersion}'
//    }
//}
//
//apply plugin: 'net.wequick.small'
//
//small {
//    // Whether build all the bundles to host assets.
//    buildToAssets = false
//
//    // The compiling `net.wequick.small:small` aar version.
//    aarVersion = "${smallAarVersion}"
//
//    // The project-wide Android version configurations
//    android {
//        compileSdkVersion = <#if buildApiString?matches("^\\d+$")>${buildApiString}<#else>'${buildApiString}'</#if>
//        buildToolsVersion = "${buildToolsVersion}"
//        supportVersion = "${buildApi}.+" // replace this with an explicit value
//    }
//}