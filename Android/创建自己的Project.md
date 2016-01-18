[toc]

##新建Project
新建Project取名UseOfSmall

##配置Project下的build.gradle
1. 在工程上按`F4`打开`ProjectStructure`，修改`Project`下的`Gradle version`为`2.9`
1. 在dependencies中增加一行`classpath 'net.wequick.tools.build:gradle-small:0.1.1'`
1. 在文件最后增加一端如下
 ```
 apply plugin: 'net.wequick.small'
 small {
     aarVersion = '0.1.2'
 }
 ```

##配置`app`module
1. 在已经创建好的`app`module中配置build.gradle
 在buildTypes下面（android下）添加如下代码
 ```
 // Filter locale configuration
 aaptOptions {
     additionalParameters '-c', 'zh-rCN'
 }

 // Signing
 signingConfigs {
     release {
         storeFile file('../sign/release.jks')
         storePassword "5mall@ndro!d"
         keyAlias "small"
         keyPassword "5mall@ndro!d"
     }
 }
 buildTypes {
     release {
         signingConfig signingConfigs.release
     }
 }
 ```
1. 复制签名文件
 将Sample工程目录下的sign文件夹拷贝至此工程
1. 重写Application.java
 此处新建一个App类继承Application，重写onCreate方法如下
 ```
 public class App extends Application{
     @Override
     public void onCreate() {
         super.onCreate();
         Small.setBaseUri("http://m.wequick.net/demo/");
     }
 }
 ```
 *此处如果报错检查Project下的build.gradle时候按上述配置，如果已经配置点击gradle按钮编译一下。*
1. 配置AndroidManifest.xml
 在<application>第一行添加`android:name=".App"`(*App为刚才重写的Application类*)

##新建插件module
1. 新建一个module命名为App.*(以`App.`开头，**注意有`.`**)
 此处的Applicatio name为App.Demo。
 自动填写的module name 为app.demo。**注意Module Name不要修改**
 手动修改包名为“主module的包名+插件module的ModuleName”。
1. 为了区别于上一个module，此处将`activity_main.xml`中的TextView文本改为“demo”并居中放置。

##配置app module
注意这次配置的是自动创建的app module
1. 新建assets文件夹（默认创建新的module时不带此文件夹）
1. 在assets下新建bundles.json文件（也可以从Smaple工程中拷过来），编写如下
 ```
 {
   "version": "1.0.0",
   "bundles": [
     {
       "uri": "demo",//这个是给app.demo起的别名，可以随便写
       "pkg": "com.xmh.useofsmall.app.demo"//这个是app.demo的包名，即build.gradle中的`applicationId`
     }
   ]
 }
 ```
1. 在app的MainActivity的onCreate中添加开启app.demo的代码
 ```
 Small.setUp(this, new net.wequick.small.Bundle.OnLoadListener() {
     @Override
     public void onStart(int bundleCount, int upgradeBundlesCount, long upgradeBundlesSize) {
 
     }
 
     @Override
     public void onProgress(int bundleIndex, String bundleName, long loadedSize, long bundleSize) {
 
     }
 
     @Override
     public void onComplete(Boolean success) {
         Small.openUri("demo",MainActivity.this);
         finish();
     }
 });
 ```


##build工程
这部分在终端中完成，AndroidStudio自带Terminal终端。如果找不到可以按快捷键`ctrl+shift+a`并输入`Terminal`打开。
1. 输入`gradlew buildLib`回车
1. 输入`gradlew buildBundle`回车
