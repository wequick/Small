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
        classpath 'net.wequick.tools.build:gradle-small:0.1.2'
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

宿主配置插件做3件事：

1. 增加jni libs目录**smallLibs**

	- 该目录用以存放其它组件包生成的*.so文件；
	- 宿主运行时将自动复制其下*.so文件至应用程序缓存。
  
2. 复制release签名配置到debug模式

	- 各组件编译时使用宿主的release签名，以使得release版本的宿主可以通过比对签名来校验插件。为此在调试运行宿主的时候也使用release签名，避免调试时签名校验失败而中断。
	
3. 增加**buildLib** task

	- 宿主程序作为一个壳，通过该task预编译生成宿主的jar文件，供其它组件引用以便打包时分离。
	
### AppPlugin

App组件打包插件主要做2件事：

1. 分离宿主、公共库的类与资源

	- 移除宿主的主题资源(AppCompat)，将插件中的Theme.AppCompat等资源id替换为宿主程序对应的id。（主题的递归应用在Native层的ResourcesType.cpp中实现，无法在Java层做动态替换，为此我们需要在编译阶段，提前做好主题的资源id映射）
	- 移除其它公共资源
	- 移除公共类
	
2. 分配资源id

	- 为保证整合在一起的程序资源id不冲突，对组件包分配 [0x03, 0x7e] 之间的package id。
