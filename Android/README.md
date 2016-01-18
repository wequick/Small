# Small Android

## 框架对比

为方便列表，简写如下：
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
  Service动态注册                | ×      | ×      | √      | ×      | ×      | √      | x <sup>[3]</sup>
  资源分包共享<sup>[4]</sup>     | ×      | ×      | ! <sup>[5]</sup> | ! <sup>[5]</sup> | ×      | ! <sup>[6]</sup>      | √
  公共插件打包共享<sup>[7]</sup> | ×      | ×      | ×      | ×      | ×      | ×      | √
  支持AppCompat<sup>[8]</sup>    | ×      | ×      | ×      | ×      | ×      | ×      | √
  支持本地网页组件               | ×      | ×      | ×      | ×      | ×      | ×      | √
  支持联调插件<sup>[9]</sup>     | ×      | x      | ×      | ×      | ×      | ×      | √
  
  > [1] 独立插件：一个完整的apk包，可以独立运行。比如从你的程序跑起淘宝、QQ，但这加载起来是要闹哪样？<br/>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;非独立插件：依赖于宿主，宿主是个壳，插件可使用其资源代码并分离之以最小化，这才是业务需要嘛。<br/>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-- _“所有不能加载非独立插件的插件化框架都是耍流氓”_。
  
  > [2] ACDD加载.so用了Native方法(libdexopt.so)，不是Java层，源码似乎未共享。
  
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

## 开始Small之旅

### Step 1. Clone Small (下载源码)
    > cd [你要放Small的目录]
    > git clone https://github.com/wequick/Small.git

> 强烈建议使用git命令行，方便更新维护。Windows用户入口：[Git for Windows][git-win]<br/>
> 后续更新可以使用命令：git pull origin master
  
### Step 2. Import Sample project (导入示例工程)
打开Android Studio，File->New->Import Project... 选择**Sample**文件夹，导入。

![Small sample][ic-sample]

* Sample `示例工程`
  * app `宿主工程`
  * app.\* `包含Activity/Fragment的组件`
  * lib.\* `公共库组件`
  * web.\* `本地网页组件`
  * sign `签名文件`

> 顺便说下，这些app.\*跟web.\*可以从工具栏的![▶️][as-run]按钮单独运行。<br/>
> 其中app.home无法单独运行是因为它只包含一个Fragment，没有Launcher Activity。

### Step 3. Build libraries (准备基础库)
  	> [./]gradlew buildLib -q (-q是安静模式，可以让输出更好看，也可以不加)
  	
  ![Build libraries][anim-bL]
  	
### Step 4. Build bundles (打包所有组件)
  	> [./]gradlew buildBundle -q (-q是安静模式，可以让输出更好看，也可以不加)
  	
  ![Build bundles][anim-bB]
  
> 步骤3跟4，如果你喜欢，也可以在**Gradle**任务导航里运行<br/>
> ![Small tasks][ic-root-tasks]
  
> 单独编译一个组件可以使用 [./]gradlew -p web.about assembleRelease<br/>
> 或者<br/>
> ![Sub tasks][ic-sub-tasks]

## 开发者模式

### Step 1. Import DevSample project (导入开发工程)
打开Android Studio，File->New->Import Project... 选择**DevSample**文件夹，导入。

![Small devsample][ic-devsample]

* DevSample `开发工程`
  * buildSrc `组件编译插件，用于打包组件`
  * small `核心库，用于加载组件`

> buildSrc在修改后会被自动编译。

其他步骤同上。除了编译单个组件的命令有所不同：

    > [./]gradlew :app.main:assembleRelease
    
> P.s. gradlew命令支持缩写，比如`assembleRelease`可以缩写为`aR`

## 加入我们

我们鼓励大家成为**Small**的开发者，并享受开源协作的乐趣。

1. 提交[Bug](https://github.com/wequick/Small/issues)并协助我们确认修复。
2. 提交[PR](https://github.com/wequick/Small/pulls)来完善文档、修复bug、完成待实现功能或者讨论中的建议。
3. 在QQ群参与讨论，提供建议。

#### 已知Issue
  * \#11 [终极分离与去除警告](https://github.com/wequick/Small/issues/11)
  * \#12 [加速生成AndroidManifest.xml](https://github.com/wequick/Small/issues/12)

> 更多细节请参考[开源贡献指南](https://guides.github.com/activities/contributing-to-open-source/)。

## 文档
[Wiki](https://github.com/wequick/small/wiki/Android)

## 常见问题

[FAQ](https://github.com/wequick/Small/wiki/Android-FAQ)

## 致谢

感谢以下网站收录本项目：

* [p.codekk.com](http://p.codekk.com) @[singwhatiwanna](https://github.com/singwhatiwanna)
* [androidweekly.cn](http://androidweekly.cn) @[inferjay](https://github.com/inferjay)
* [toutiao.io](http://toutiao.io) @[Juude](https://github.com/Juude)
* [gank.io](http://gank.io) @[daimajia](https://github.com/daimajia)

## 联系我们

<a target="_blank" href="http://shang.qq.com/wpa/qunwpa?idkey=d9b57f150084ba4b30c73d0a2b480e30c99b8718bf16bb7739af740f7d1e21f3"><img border="0" src="http://pub.idqqimg.com/wpa/images/group.png" alt="快客 - Small Android" title="快客 - Small Android"></a> 
[![Gitter](https://img.shields.io/gitter/room/nwjs/nw.js.svg)](https://gitter.im/wequick/SmallAndroid?utm_source=share-link&utm_medium=link&utm_campaign=share-link)

> QQ群链接无法使用的手动加 **374601844**，验证填写你是从何得知Small的，如qq, csdn, github, 朋友推荐。<br/> 进群改备注：如_“福州-GalenLin”_。

## License
Apache License 2.0

[git-win]: http://git-scm.com/downloads
[as-run]: http://developer.android.com/images/tools/as-run.png
[as-debug]: http://developer.android.com/images/tools/as-debugbutton.png
[ic-sample]: http://code.wequick.net/assets/images/small-sample.png
[ic-devsample]: http://code.wequick.net/assets/images/small-devsample.png
[anim-bG]: http://code.wequick.net/anims/small/android-build-gradle.gif
[anim-bL]: http://code.wequick.net/anims/small/android-build-lib.gif
[anim-bB]: http://code.wequick.net/anims/small-android-build-bundle.gif
[ic-root-tasks]: http://code.wequick.net/images/small/root-gradle-tasks.png
[ic-sub-tasks]: http://code.wequick.net/images/small/sub-gradle-tasks.png
