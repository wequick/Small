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
  - 

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