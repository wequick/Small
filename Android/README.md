# Small Android

* aar-small `核心库，用于加载组件`
* gradle-small-plugin `组件编译插件，用于打包组件`
* Sample `示例工程`

## 框架对比

```
  DyLA  : Dynamic-load-apk          @singwhatiwanna, 百度
  DiLA  : Direct-Load-apk           @melbcat
  APF   : Android-Plugin-Framework  @limpoxe
  ACDD  : ACDD                      @bunnyblue
  DyAPK : DynamicAPK                @TediWang, 携程
  DPG   : DroidPlugin               @cmzy, 360
```

* 功能

  \\                             | DyLA   | DiLA   | ACDD   | DyAPK  | DPG    | APF    | Small
  -------------------------------|--------|--------|--------|--------|--------|--------|--------
  加载非独立插件<sup>[1]</sup>   | ×      | x      | √      | √      | ×      | √      | √
  加载.so插件                    | ×      | ×      | ! <sup>[2]</sup>     | ×      | ×      | ×      | √
  Activity生命周期               | √      | √      | √      | √      | ×      | √      | √
  Service动态注册                | ×      | ×      | ×      | ×      | ×      | √      | x <sup>[3]</sup>
  资源分包共享<sup>[4]</sup>     | ×      | ×      | ! <sup>[5]</sup> | ! <sup>[5]</sup> | ×      | ! <sup>[6]</sup>      | √
  公共插件打包共享<sup>[7]</sup> | ×      | ×      | ×      | ×      | ×      | ×      | √
  支持AppCompat<sup>[8]</sup>    | ×      | ×      | ×      | ×      | ×      | ×      | √
  支持联调插件<sup>[9]</sup>     | ×      | x      | ×      | ×      | ×      | √      | √
  
  > [1] 独立插件：一个完整的apk包，可以独立运行。比如从你的程序跑起淘宝、QQ，但这加载起来是要闹哪样？<br/>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;非独立插件：依赖于宿主，宿主是个壳，插件可使用其资源代码并分离之以最小化，这才是业务需要嘛。<br/>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-- _“所有不能加载非独立插件的插件化框架都是耍流氓”_。
  
  > [2] ACDD加载.so用了Native方法(libdexopt.so)，不是Java层，源码似乎未共享。
  
  > [3] Service更新频度低，可预先注册在宿主的manifest中，暂不支持。
  
  > [4] 要实现宿主、各个插件资源可互相访问，需要对他们的资源进行分段处理以避免冲突。
  
  > [5] 这些框架修改aapt源码、重编、覆盖SDK Manager下载的aapt，我只想说_“杀(玩)鸡(得)焉(开)用(心)牛(就)刀(好)”_。<br/>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Small使用gradle-small-plugin，在后期修改二进制文件，实现了_**PP**_段分区。
  
  > [6] 使用public-padding对资源id的_**TT**_段进行分区，分开了宿主和插件。但是插件之间无法分段。
  
  > [7] 除了宿主提供一些公共资源与代码外，我们仍需封装一些业务层面的公共库，这些库被其他插件所依赖。<br/>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;公共插件打包的目的就是可以单独更新公共库插件，并且相关插件不需要动到。
  
  > [8] AppCompat: Android Studio默认添加的主题包，Google主推的Metrial Design包也依赖于此。不支持毋宁死。
  
  > [9] 联调插件：使用Android Studio调试时，可直接在插件代码中添加断点调试。


## Why Small?

[FAQ](https://github.com/wequick/Small/wiki/Android-FAQ)

## Installation
### Step 1. Install gradle-small-plugin (安装编译插件)
    > cd Sample
    > ./gradlew -p ../gradle-small-plugin install (Mac OS)
    > gradlew -p ..\gradle-small-plugin install (Windows)
    
### Step 2. Import sample project (导入示例工程)
Import `Sample' by Android Studio.

### Step 3. Build libraries (准备基础库)
  	> [./]gradlew buildLib -q (-q是安静模式，可以让输出更好看，也可以不加)
  	
### Step 4. Build bundles (打包所有组件)
  	> [./]gradlew buildBundle -q (-q是安静模式，可以让输出更好看，也可以不加)
  	
  ![Build bundle][anim-bB]

## Documentation
[Wiki](https://github.com/wequick/small/wiki/Android)

## Contact

<a target="_blank" href="http://shang.qq.com/wpa/qunwpa?idkey=d9b57f150084ba4b30c73d0a2b480e30c99b8718bf16bb7739af740f7d1e21f3"><img border="0" src="http://pub.idqqimg.com/wpa/images/group.png" alt="快客 - Small Android" title="快客 - Small Android"></a>

## License
Apache License 2.0

[anim-bB]: http://code.wequick.net/anims/small-android-build-bundle.gif
