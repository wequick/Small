## 0.4.1 (2016-04-96)

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