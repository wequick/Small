## 1.1.0-beta4 (2016-12-12)

Bugfixes:

  - 修正无法正确引用 `app+stub` 模块中的代码和资源的问题 (#364, #371)

## 1.1.0-beta3 (2016-11-10)

Features:

  - 自动为宿主添加 `BuildConfig.LOAD_FROM_ASSETS`, 值等于 `small.buildToAssets`

## 1.1.0-beta2 (2016-11-10)

Features:

  - 支持打包插件到宿主Assets, 在根 `build.gradle` 中开启:

    ```
    small {
        buildToAssets = true
    }
    ```

Bugfixes:

  - 对于 `app.*` 模块, 移除 `Stub` 模块manifest中可能存在的ContentProvider, 避免命名冲突而无法单独运行
  - 当插件没有资源时, 移除整个 `generated/source/r/release` 目录, 避免将第三方 `R.class` 打包到插件中

## 1.1.0-beta1 (2016-11-04)

Features:

  - 使用`gradlew small`可打印更多有用的编译信息以方便提issue
  - 支持Stub模块(宿主分身), 该模块会被打包到宿主, 其他模块可自由引用其中的类与资源

    声明一个分身模块, 你可以:
    - 模块名以 `app+` 开头
    - 或者在 根`build.gradle` 里声明 `bundles ('stub', ['any1', 'any2'])`

  - 支持统一配置android环境, 以避免由于环境不同可能导致的资源不匹配问题(AppCompat):

    ```
    small {
      android {
        compileSdkVersion = 23       // 编译sdk版本
        buildToolsVersion = "23.0.3" // 编译工具版本
        supportVersion = "23.4.0"    // Support包版本
      }
    }
    ```

Bugfixes:

  - 兼容 Gradle 3.0, 使用 `JANSI` 完成控制台颜色输出 (#326)
  - 修正 `AssetPlugin` 中对 `android.jar` 的引用路径

## 1.0.0-alpha2 (2016-10-11)

Bugfixes:

  - 修正在Android Studio 2.2上出现的 `assembleRelease` 无法找到问题 (#315)

## 1.0.0-alpha1 (2016-10-09)

Features:

  - 支持Android Gradle 2.2.0 (#167, #315, #323, #327)

## 1.0.0-beta9 (2016-08-25)

Bugfixes:

  - 解决 `lib.*` 相互依赖可能导致的资源重复问题 (#279)
  - 修正上个版本未能正确收集嵌套引用 `lib.*` 依赖的问题

## 1.0.0-beta8 (2016-08-22)

Bugfixes:

  - 修正第三方库引用AppCompat资源导致的R字段找不到问题 (#271)
  - 修正嵌套引用的 `lib.*` 模块与 `app.*` 模块的 `manifest application` 冲突定义的问题

## 1.0.0-beta7 (2016-08-11)

Bugfixes:

  - 修正`buildLib`后再单独运行插件时可能出现的`transform`失败问题
  - 修正单独运行插件再`buildBundle`时, 会将`lib.*`中的JNI携带到`app.*`的问题

## 1.0.0-beta6 (2016-08-09)

Bugfixes:

  - 修正插件间继承主题无法生效导致的闪退问题 (#249)

Other:

  - 友好化"不支持删除公共资源"的错误提示

## 1.0.0-beta5 (2016-08-02)

Bugfixes:

  - 修正手动设置模块`packageId`后可能导致的错误冲突提示 (#213)

Other:

  - 美化`gradlew small`输出, 增加文件名与插件大小显示

## 1.0.0-beta4 (2016-07-29)

Bugfixes:

  - 修正第三方aar的`R$id`类找不到的问题 (#230)

## 1.0.0-beta3 (2016-07-23)

Bugfixes:

  - 修正`Windows`系统下不能正确分离`resources.ap_`的问题

## 1.0.0-beta2 (2016-07-22)

Bugfixes:

  - 修正可能出现的`xx-D.txt`找不到导致无法编译的问题

## 1.0.0-beta1 (2016-07-21)

Features:

  - 取消模块名`lib.xx`限制, 可在`build.gradle`里通过`bundles ('lib', [moduleA, moduleB])`来配置
  - 取消模块包名`*.app.*`限制, 可在`bundle.json`里通过`type`字段来配置 (`*.app.*`, `*.appXX`形式的包名无需配置, 可被自动识别)
  - 增加`gradlew small`任务来显示**Small**环境变量

Performance:

  - 避免在编译`lib.A:aR`时触发构建其他`lib.*`模块的`buildLib`任务
  - 确保在插件没有资源时能够删除其`resources.arsc`文件来减少插件大小
  - 当插件没有资源时, 跳过`资源分离`等操作, 使编译加速
  - 避免分离字符串资源时可能产生的重复数据
  - 避免不同的`variant`重复调用`preBuild`任务

Bugfixes:

  - 修正普通aar模块未生成`R.java`导致的类找不到问题 (#194)
  - 修正`lib.*`模块下的`libs/*.jar`中的类找不到问题 (#177)
  - 修正`lib.*`模块下的`assets`等目录被重复编译进`app`模块的问题 (#199)
  - 修正误改资源压缩格式导致的`raw`下音频文件无法播放的问题 (#215, #172, #220)
  - 修正解析字符串结构错误导致的资源无法找到问题 (a049596)

Other:

  - 兼容JDK 1.7

## 0.9.0 (2016-06-29)

Features:

  - 支持插件混淆 (#85, #158)

Performance:

  - 动态添加classpath，避免javac task重复运行

Bugfixes:

  - 修正当`app.A`依赖`lib.B`且二者manifest都定义了`<application>`时，`processManifest`失败的问题
  - 在manifest的`platformBuildVersionCode`里添加`无资源标记` (#62, #139)
  - 修正`processDebugManifest`时`<application>`标签未闭合的问题 (@tcking)

Other:

  - 导入`Android Plugin`相关类，提高代码可读性

## 0.8.0 (2016-05-18)

Bugfixes:

  - 修正`app.*`将其依赖的`lib.xx`中的`*.so`文件也打包进去的问题 (#126)
  - 修正设置了`ndk.abiFilters`的插件在打包时无法正确标记ABI的问题 (#132)
  - 修正包含`.so`文件且manifest的`<application>`标签没有任何属性的插件打包时无法正确标记ABI的问题 (#131)

## 0.7.0 (2016-05-05)

Features:

  - 插件模块支持依赖本地aar模块

Bugfixes:

  - 修正`lib.*`插件在`buildLib`时漏分离`support-annotations.jar`
  - 修正`lib.*`插件相互依赖时可能出现的类找不到问题 (#117)
  - 修正_非强制分离_模式下深层次依赖的R.java未被正确处理的问题 (#81, #114 @peacepassion)
  - 修正在执行`buildBundle`时，`lib.*`插件可能出现的`找不到Small类`错误
  - 修正在执行`buildBundle`时，`lib.*`插件可能出现的`transformNative_libsWithSyncJniLibsForRelease`任务异常

## 0.6.0 (2016-04-21)

Features:

  - 支持`app.*`在代码中直接引用`lib.*`的资源`R.xx`
  - 支持不设置宿主签名时可以正常调试运行

## 0.5.0 (2016-04-12)

Features:

  - 对携带JNI的插件包自动打上其所支持ABI的版本标记
  - 插件包支持`productFlavor`多渠道设置 (@peacepassion, #95)

Bugfixes:

  - 修正插件manifest编译后中文注释乱码
  - 修正`非强制分离`模式未正确保留深度依赖的jar问题
  - 修正_仅修改插件代码_编译时出现无法正常分离依赖库代码的问题
  - 修正`string-array`字符串数据未被正确编译进插件问题 (#98)
  - 修正首次编译`buildLib`时出现的AAR无法找到问题 (#100)

## 0.4.1 (2016-04-06)

Bugfixes:

  - 修正部分API不兼容gradle2.9以下版本的问题

## 0.4.0 (2016-04-05)

Features:

  - 支持buildToolsRevision`24.0.0 rc2`
  - 支持对深度依赖(transitive)的第三方库进行分离 (#81)
  - `Small DSL`增加`strictSplictResources`属性，允许对插件强制加入第三方资源 (#81)

Performance:

  - 分离插件manifest的`android:icon`与`android:label` (#11)

Bugfixes:

  - 修正插件定义的字符串大于128字节时打包错误的问题 (#92)
  - 修正`dumpTable`输出错误
  - 修正`resetPackage`的参数错误


## 0.3.5 (2016-03-24)

Features:

  - 相互依赖的`lib.*`支持根据依赖顺序进行编译 (#65)
  - `app.*`模块允许在代码中引用`lib.*`资源 (#63)

Bugfixes:

  - 修正插件代码中无法使用`support-annotation`注解的错误 (#63)
  - 修正`gradlew`不加任何task跑时出现崩溃的错误 (#54, @JLLK)

## 0.3.4 (2016-03-01)

Features:

  - `lib.*`支持固定公共资源id (增加资源时保持原有资源id不变)

Bugfixes:

  - 修正插件内资源排序错误

## 0.3.3 (2016-03-01)

Features:

  - 插件资源支持`string-array`, `integer-array`

## 0.3.2 (2016-02-29)

Bugfixes:

  - 修正`app.*`资源id分配错误

## 0.3.1 (2016-02-29)

Bugfixes:

  - 修正自定义styleable中使用`android:xx`报错 (#50)
  - 修正自定义styleable添加attr报错 (#51)

## 0.3.0 (2016-02-25)

Features:

  - 支持宿主多渠道打包

## 0.2.1 (2016-02-23)

Bugfixes:

  - 移除_测试断言_造成的异常退出

## 0.2.0 (2016-02-19)

Performance:

  - 移除无效log输出

Features:

  - 支持在`lib.*`模块中定义主题
  - 支持在插件中定义`styleable`属性


## 0.1.5 (2016-02-17)

Performance:

  - 优化Package ID自动生成算法

## 0.1.4 (2016-02-17)

Features:

  - 支持在gradle.properties或build.gradle自定义插件Package Id

## 0.1.3 (2016-02-04)

Bugfixes:

  - 解决HTC手机占用Package Id 0x03问题 (#31 from @george5613)

## 0.1.2 (2016-01-28)

Bugfixes:

  - 修正插件resources.arsc过大导致的崩溃 (#21, @wu4321)
  - 修正DevSample执行`gradlew :app.*:aR`失败 (#18, @wu4321)
  - 修正编译的web组件包无法在API16以下运行 (#13, @wu4321)