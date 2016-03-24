## 0.5.0 (2016-03-24)

Bugfixes:

  - 修正**应用后台被杀**重启插件_非标准模式Activity_闪退问题 (#60)
  - 修正`baseUri`未定义时闪退问题

## 0.4.2 (2016-03-05)

Bugfixes:

  - 修正**应用后台被杀**重启插件_标准模式Activity_闪退问题 (#38)
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