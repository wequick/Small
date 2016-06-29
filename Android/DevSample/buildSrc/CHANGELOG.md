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