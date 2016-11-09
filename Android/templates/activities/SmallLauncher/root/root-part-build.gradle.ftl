//
//
////-------------------------------------------------------------------
//// Small plugin
////
//// Cannot automatically merge the buildscript in Android Studio 2.0+,
//// uncomment this section to go into force.
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
//    aarVersion = "${smallAarVersion}"
//    android {
//        compileSdkVersion = <#if buildApiString?matches("^\\d+$")>${buildApiString}<#else>'${buildApiString}'</#if>
//        buildToolsVersion = "${buildToolsVersion}"
//        supportVersion = "${buildApi}.+" // replace this with an explicit value
//    }
//}