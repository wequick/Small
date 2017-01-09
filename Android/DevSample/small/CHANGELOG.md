## 1.1.0-alpha1 (2017-01-09)

Bugfixes:

  - 修正 MiUI8 资源无法合并的问题 (@xufan)

## 1.1.0-beta9 (2016-11-17)

Bugfixes:

  - 取消使用 `SetUpActivity`, 以避免应用重启后无法传递 `savedInstanceState` 到Activity.

## 1.1.0-beta8 (2016-11-15)

Bugfixes:

  - 修正指定了 `android:process` 的Activity/Service启动时闪退的问题 (#354, #355)

## 1.1.0-beta7 (2016-11-10)

Features:

  - 取消使用 `SetUpProvider`, 改在宿主Application构造方法中调用 `Small.preSetUp` 来支持ContentProvider (#253)
  - `Small.openUri` 增加返回值, false表示打开失败
  - 支持从宿主Assets中加载插件: `Small.setLoadFromAssets(true)`

## 1.1.0-beta6 (2016-11-04)

Features:

  - 支持通过 `TaskStackBuilder` 来透明的创建通知 (与原有代码一致)
  - 导出 `Small.wrapIntent` 以支持自定义 `PendingIntent` 的插件化封装
  - 支持在宿主注册插件 `ContentProvider`, 而在插件中实现该类 (#253)

## 1.1.0-beta5 (2016-08-17)

Bugfixes:

  - 确保后台升级时能够杀死应用程序相关进程
  - 避免 `Small.setUp` 重复调用时可能引起的 `pre-verify` 错误
  - 修正当 `query` 被url编码后无法正确匹配 `uri` 的问题 (#222)

Other:

  - 引入 `Small.isFirstSetUp` 方法来判断是否首次启动

## 1.1.0-beta4 (2016-08-09)

Bugfixes:

  - 修正无法正常启用硬件加速的问题 (#258)
  - 修正特殊软件如`360卫士极客版`造成的插件无法启动问题 (#245)
  - 修正当`baseUri`未设置时, `getIntentOfUri`方法空指针异常 (#246)

Other:

  - 插件fragment路由支持不带`.`前缀 (#236)

## 1.1.0-beta3 (2016-08-01)

Bugfixes:

  - 修正在`5.0`以上系统可能出现的`getPooledStringForCookie`数组越界问题

## 1.1.0-beta2 (2016-07-29)

Bugfixes:

  - 修正 _singleTask_ 与 _singleTop_ launchMode匹配错误的问题 (#193, #231)

## 1.1.0-beta1 (2016-07-21)

Features:

  - `bundle.json`支持宿主路由配置 (pkg不配置)
  - `bundle.json`支持自定义插件`type`
  - 支持将插件manifest的Launcher作为默认路由Activity
  - 使用`Instant Run`方式修改宿主资源`mAssets`来完成资源合并, 兼容Xposed (#190)

Bugfixes:

  - [重要] 修正插件application在异常重启后无法触发`onCreate`的问题

Other:

  - `Bundle`类导出`versionCode`与`versionName`的获取API

## 1.0.0 (2016-06-29)

Performance:

  - 并发加载插件以提高首次加载速度
  - 使用CRC校验提高二次加载速度
  - 释放中间变量以优化内存

Bugfixes:

  - 创建插件application操作移至UI线程 (#173)
  - 修正不包含资源的插件被`addAssetPath`后在4.4以下出现的闪退问题 (#62, #139)

## 1.0.0-beta2 (2016-05-19)

Bugfixes:

  - 修正small包误编译进appcompat/R.class导致的无法运行问题

## 1.0.0-beta1 (2016-05-18)

Features:

  - 支持新增插件 (#72)
  - 标记升级时，支持在进入后台后自动重启应用，完成静默升级

Performance:

  - 重构各反射方法至`private static Vxx`私有类下
  - 增加`addAssetPaths`方法以减少`addAssetPath`反射次数
  - 减少dex相关`expandArray`反射次数

Bugfixes:

  - 修正插件`application`的`onCreate`调用时机 (#136)
  - 修正`Nubia`机型可能出现的闪退 (#135)

## 0.9.0 (2016-04-21)

Performance:

  - 改进资源hook，一步到位，无需每次在创建插件Activity时反射修改其Resources与应用主题
  - 改进插件ActivityInfo hook，增加targetActivity属性以利用系统源码自动回复真身
  - 将bundle.json解析放入线程，移除部分同步I/O操作，提高首次启动速度

Bugfixes:

  - 修正插件ABI 32位与64位冲突问题

Others:

  - 最小支持版本升至2.3，API 9
  - 重构部分接口以支持ProGuard

## 0.8.0 (2016-04-12)

Features:

  - 支持插件携带.so文件 (#79)
  - 支持插件Activity透明 (#94)

Performance:

  - 优化部分代码，支持宿主混淆 (#85)

Bugfixes:

  - 修正`rules`搜寻Activity逻辑错误

## 0.7.0 (2016-04-05)

Features:

  - 支持插件`Activity`通过隐式`action`进行调用 (#89)

Performance:

  - 插件签名解析加速 (#90)
  - 插件manifest解析加速 (#91)

Bugfixes:

  - `bundle.json`定义的`rules`允许缺省`Activity`后缀 (#77)
  - 修正`Activity`直接使用`getAssets()`方法时无法正确取得assets资源 (#80)
  - 支持插件`Activity`的`screenOrientation`属性 (#86)
  - 修正打开远程网页的崩溃问题

## 0.6.0 (2016-03-25)

Features:

  - 支持`AppCompat 23.2+`

Bugfixes:

  - 修正`bundle.json`未定义`uri`属性时闪退问题

## 0.5.0 (2016-03-24)

Bugfixes:

  - 修正**应用后台被杀后**，重启插件_**非标准模式**_Activity出现的闪退问题 (#60)
  - 修正`baseUri`未定义时闪退问题

## 0.4.2 (2016-03-05)

Bugfixes:

  - 修正**应用后台被杀后**，重启插件_**标准模式**_Activity出现的闪退问题 (#38)
  - 修正插件补丁包过期可能导致的程序异常 (#39)

## 0.4.1 (2016-02-19)

Features:

  - Small启动时自动调用插件`Application`的`onCreate`方法

Bugfixes:

  - 修正`bundle.json` - `rules`规则无法正确生效问题

## 0.4.0 (2016-02-17)

Features:

  - 向下兼容至Android 2.2(API8)

## 0.3.0 (2016-02-04)

Refactors:

  - 插件清单文件更名为`bundle.json` (原`bundles.json`)
  - 优化`Small.setUp`API，减少监听回调

Bugfixes:

  - 修正不包含任何插件时`ApkBundleLauncher`出现的空指针异常

## 0.2.0 (2016-01-27)

Features:

  - 支持插件下载更新

Bugfixes:

  - 修正`bundle.json` - `rules`规则映射错误 (#19)
  - 修正`applicationContext().getResources()`无法找到资源问题
  - 修正API16以下无法找到资源问题
  - 修正网页组件传递参数错误导致的崩溃问题 (#6)