## 框架对比

为方便列表，简写如下：
```
  DyLA  : Dynamic-load-apk          @singwhatiwanna, 百度
  DiLA  : Direct-Load-apk           @FinalLody
  APF   : Android-Plugin-Framework  @limpoxe
  ACDD  : ACDD                      @bunnyblue
  DyAPK : DynamicAPK                @TediWang, 携程
  DPG   : DroidPlugin               @cmzy, 360
```

* 功能

  \\                             | DyLA   | DiLA   | ACDD   | DyAPK  | DPG    | APF    | Small
  -------------------------------|--------|--------|--------|--------|--------|--------|--------
  加载非独立插件<sup>[1]</sup>     | ×      | x      | √      | √      | ×      | √      | √
  加载.so后缀插件                  | ×      | ×      | ! <sup>[2]</sup>     | ×      | ×      | ×      | √
  Activity生命周期                | √      | √      | √      | √      | √      | √      | √
  Service动态注册                 | ×      | ×      | √      | ×      | √      | √      | x <sup>[3]</sup>
  资源分包共享<sup>[4]</sup>      | ×      | ×      | ! <sup>[5]</sup> | ! <sup>[5]</sup> | ×      | ! <sup>[6]</sup>      | √
  公共插件打包共享<sup>[7]</sup>   | ×      | ×      | ×      | ×      | ×      | ×      | √
  支持AppCompat<sup>[8]</sup>    | ×      | ×      | ×      | ×      | ×      | ×      | √
  支持本地网页组件                 | ×      | ×      | ×      | ×      | ×      | ×      | √
  支持联调插件<sup>[9]</sup>      | ×      | x      | ×      | ×      | ×      | ×      | √
  
  > [1] 独立插件：一个完整的apk包，可以独立运行。比如从你的程序跑起淘宝、QQ，但这加载起来是要闹哪样？<br/>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;非独立插件：依赖于宿主，宿主是个壳，插件可使用其资源代码并分离之以最小化，这才是业务需要嘛。<br/>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-- _“所有不能加载非独立插件的插件化框架都是耍流氓”_。
  
  > [2] ACDD加载.so用了Native方法(libdexopt.so)，不是Java层，源码见[dexopt.cpp](https://github.com/bunnyblue/ACDD/blob/master/ACDDCore/jni/dexopt.cpp)。
  
  > [3] Service更新频度低，可预先注册在宿主的manifest中，如果没有很好的理由说服我，现不支持。
  
  > [4] 要实现宿主、各个插件资源可互相访问，需要对他们的资源进行分段处理以避免冲突。
  
  > [5] 这些框架修改aapt源码、重编、覆盖SDK Manager下载的aapt，我只想说_“杀(wan)鸡(de)焉(kai)用(xin)牛(jiu)刀(hao)”_。<br/>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Small使用gradle-small-plugin，在后期修改二进制文件，实现了_**PP**_段分区。
  
  > [6] 使用public-padding对资源id的_**TT**_段进行分区，分开了宿主和插件。但是插件之间无法分段。
  
  > [7] 除了宿主提供一些公共资源与代码外，我们仍需封装一些业务层面的公共库，这些库被其他插件所依赖。<br/>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;公共插件打包的目的就是可以单独更新公共库插件，并且相关插件不需要动到。
  
  > [8] AppCompat: Android Studio默认添加的主题包，Google主推的Metrial Design包也依赖于此。大势所趋。
  
  > [9] 联调插件：使用Android Studio调试![🐞][as-debug]宿主时，可直接在插件代码中添加断点调试。
  
* 透明度

  \\                             | ACDD   | DyAPK  | APF    | Small
  -------------------------------|--------|--------|--------|--------
  插件Activity代码无需修改       | √      | √      | √      | √
  插件引用外部资源无需修改name   | ×      | ×      | ×      | √
  插件模块无需修改build.gradle   | ×      | x      | ×      | √
  
  > 以上对比，纯属个人见解，如有不同意见请指出，谢谢。


[as-debug]: http://developer.android.com/images/tools/as-debugbutton.png