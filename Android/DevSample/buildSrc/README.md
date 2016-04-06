gradle-small-plugin是一个gradle插件，用来打包安卓组件包。

## 使用方法

1. 在root工程的build.gradle中添加依赖：

  ```gradle
  buildscript {
      repositories {
          jcenter()
          mavenLocal()
      }
      dependencies {
          classpath 'com.android.tools.build:gradle:1.5.0'
          classpath 'net.wequick.tools.build:gradle-small:0.4.1'
      }
  }

  allprojects {
      repositories {
          jcenter()
          mavenLocal()
      }
  }
  ```

2. 应用工程配置插件

  ```gradle
  apply plugin: 'net.wequick.small'
  ```

## 原理

gradle-small-plugin内部注册了5个插件：

名称 | ID | 类
----|----|----
工程配置插件 | **net.wequick.small** | _net.wequick.gradle.**RootPlugin**_
宿主配置插件 | **net.wequick.small.host** | _net.wequick.gradle.**HostPlugin**_
App组件打包插件 | **net.wequick.small.application** | _net.wequick.gradle.**AppPlugin**_
Library组件打包插件 | **net.wequick.small.library** | _net.wequick.gradle.**LibraryPlugin**_
资源组件打包插件 | **net.wequick.small.asset** | _net.wequick.gradle.**AssetPlugin**_

假设工程目录结构为：

```
Sample (Root)
  |-- app (Host)
  |    `-- build.gradle
  |-- app.home
  |    `-- build.gradle
  |-- lib.utils
  |    `-- build.gradle
  |-- web.about
  |    `-- build.gradle
  `-- build.gradle 
```

当在Root的build.gradle中配置`aply plugin 'net.wequick.small`时，将会遍历其所有的子模块(subprojects)：

* 对 `app` 模块应用 _**HostPlugin**_
* 对 `app.*` 模块应用 _**AppPlugin**_
* 对 `lib.*` 模块应用 _**LibraryPlugin**_
* 对 `[others].*` 模块应用 _**AssetPlugin**_

### HostPlugin

用于配置宿主：

1. 增加jni libs目录**smallLibs**

	- 该目录用以存放其它组件包生成的*.so文件；
	- 宿主运行时将自动复制其下*.so文件至应用程序缓存。
  
2. 复制release签名配置到debug模式

	- 各组件编译时使用宿主的release签名，以使得release版本的宿主可以通过比对签名来校验插件。为此在调试运行宿主的时候也使用release签名，避免调试时签名校验失败而中断。
	
3. 增加**buildLib** task

	- 宿主程序作为一个壳，通过该task预编译生成宿主的jar文件，供其它组件引用以便打包时分离。
	
### AppPlugin

用于App组件打包：

1. 分离宿主、公共库的类与资源

	- 移除宿主的主题资源(AppCompat)，将插件中的Theme.AppCompat等资源id替换为宿主程序对应的id。（主题的递归应用在Native层的ResourcesType.cpp中实现，无法在Java层做动态替换，为此我们需要在编译阶段，提前做好主题的资源id映射）
	- 移除其它公共资源
	- 移除公共类
	
2. 分配资源id

	- 为保证整合在一起的程序资源id不冲突，对组件包分配 [0x03, 0x7e] 之间的package id。

### LibraryPlugin

用于公共库组件打包：

1. 在编译App组件包时，使用`com.android.library`模式
2. 在编译自己时，切换为`com.android.application`模式，并按App组件打包
3. 增加**buildLib** task，生成jar包，供其它组件引用以便打包时分离

### AssetPlugin

用于资源组件打包：

1. 复制assets目录下文件
2. 生成二进制AndroidManifest.xml
	- 携带versionCode以供插件版本比对
	- 使插件可以通过PackageManager的getPackageArchiveInfo方法获取签名信息
3. 签名组件包

此类打包忽略所有java文件，直接将assets目录中的文件进行压缩打包。

aar-small内部内置了[WebBundleLauncher][1](网页资源组件包加载器)用来加载`web.*`的网页组件。

基于这个架构，您可以扩展自己的自定义组件，比如扩展支持Markdown组件：

1. 新建`md.*`模块，该模块的`src/main/assets`目录中添加`index.md`文件
2. 新建`MdBundleLauncher`类来加载`md.*`模块

[1]: https://github.com/wequick/Small/blob/master/Android/aar-small/src/main/java/net/wequick/small/WebBundleLauncher.java
