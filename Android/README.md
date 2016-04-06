# Small Android

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

## 开始Small之旅

### 1. Create Project
File->New->New Project...

#### 1.1 Configure your new project

假设宿主包名为`com.example.mysmall`

1. 设置**Application name**为`MySmall`
2. 修改**Company Domain**为`mysmall.example.com`

  > 这步是个技巧，在Step3新建Module时将会自动带上该前缀
  
3. 修正**Package name**为`com.example.mysmall`

![New small project][anim-new-prj]

#### 1.2 Add an activity to mobile

这步推荐使用**Fullscreen Activity**，作为启动界面再好不过。
在配置Activity界面，建议把**Activity Name**改为**LaunchActivity**（使名符其实）。

### 2. Configure Small

修改Project的build.gradle

#### 2.1 加入Small编译库

```groovy
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:1.3.0'
        classpath 'net.wequick.tools.build:gradle-small:0.4.1'
    }
}

...

apply plugin: 'net.wequick.small'
```

#### 2.2 配置Small DSL （可选）

目前只有一个属性`aarVersion`，表示Small aar的代码库版本。如果没有设置，默认为`gradle-small`的版本。

```groovy
small {
    aarVersion = '0.7.0'
}
```

> 最新的版本号可以在[Bintray][bintray]上看到。

### 3. Create Module

File->New->Module来创建插件模块，需要满足：

1. 模块名形如：`app.*`, `lib.*`或者`web.*`
2. 包名包含：`.app.`, `.lib.`或者`.web.`

  > 为什么要这样？因为Small会根据包名对插件进行归类，特殊的域名空间如：“.app.” 会让这变得容易。

对`lib.*`模块选择**Android Library**，其他模块选择**Phone & Tablet Module**。

创建一个插件模块，比如`app.main`：

1. 修改**Application/Library name**为`App.main`
2. 修改**Package name**为`com.example.mysmall.app.main`

  ![New small module][anim-new-md]
  
### 4. Configure UI route

右键`app`模块->New->Folder->Assets Folder，新建`assets`目录，

右键`assets`目录->New->File，新建`bundle.json`文件，加入：

```json
{
  "version": "1.0.0",
  "bundles": [
    {
      "uri": "main",
      "pkg": "com.example.mysmall.app.main"
    }
  ]
}
```

### 5. Setup Small

#### 5.1 配置签名

切换到`Project`目录树，右键`MySmall`，新建`sign`目录，添加`release.jks`签名文件。

在`app`模块的`build.gradle`中增加签名配置（密码改成自己的）：

```groovy
signingConfigs {
    release {
        storeFile file('../sign/release.jks')
        storePassword "5mall@ndro!d"
        keyAlias "small"
        keyPassword "5mall@ndro!d"
    }
}
buildTypes {
    release {
        signingConfig signingConfigs.release
    }
}
```

#### 5.2 配置基础依赖

在`app`模块增加共享的依赖库，比如：

```groovy
compile 'com.android.support:design:23.1.1'
```

#### 5.3 加载插件

在`app`模块的`LaunchActivity`重载`onStart`方法：

```java
@Override
protected void onStart() {
    super.onStart();
    Small.setBaseUri("http://example.com/");
    Small.setUp(this, new net.wequick.small.Small.OnCompleteListener() {

        @Override
        public void onComplete() {
            Small.openUri("main", LaunchActivity.this);
        }
    });
}
```

### 6. Compile Small

1. Build libraries (准备基础库)
  > [./]gradlew buildLib -q (-q是安静模式，可以让输出更好看，也可以不加)
  	
  ![Build libraries][anim-bL]
  	
2. Build bundles (打包所有组件)
  > [./]gradlew buildBundle -q (-q是安静模式，可以让输出更好看，也可以不加)
  	
  ![Build bundles][anim-bB]
  
> 这两步，如果你喜欢，也可以在**Gradle**任务导航里运行<br/>
> ![Small tasks][ic-root-tasks]
  
> 单独编译一个组件可以使用 [./]gradlew -p web.about assembleRelease<br/>
> 或者<br/>
> ![Sub tasks][ic-sub-tasks]

### 7. Run Small

在工具栏![Run small][ic-run]，选择**app**模块，运行。

## Examples

* 使用者模式[Sample](Sample)
* 开发者模式[DevSample](DevSample)

## 加入我们

我们鼓励大家成为**Small**的开发者，并享受开源协作的乐趣。

1. 提交[Bug](https://github.com/wequick/Small/issues)并协助我们确认修复。
2. 提交[PR](https://github.com/wequick/Small/pulls)来完善文档、修复bug、完成待实现功能或者讨论中的建议。
3. 在QQ群或[Gitter][gitter]参与讨论，提供建议。
4. 在[Bintray][bintray]上给我们的maven五星好评。

> 更多细节请参考[开源贡献指南](https://guides.github.com/activities/contributing-to-open-source/)。

#### TODO List

  - [x] 下载插件
  - [ ] 热更新（现在需要重启生效）
  - [x] [终极分离与去除警告](https://github.com/wequick/Small/issues/11)
  - [ ] [加速生成AndroidManifest.xml](https://github.com/wequick/Small/issues/12)
  - [ ] [支持混淆](https://github.com/wequick/Small/issues/85)

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

当你决定这么做时，希望你已经下载了源码并成功运行。并且关注<br/>
&nbsp;&nbsp;&nbsp;&nbsp;_“如何**从Small中学到东西**以及**为Small做点什么**，促进**共同成长**。”_<br/>
而非Small能为你做什么。

<a target="_blank" href="http://shang.qq.com/wpa/qunwpa?idkey=d9b57f150084ba4b30c73d0a2b480e30c99b8718bf16bb7739af740f7d1e21f3"><img border="0" src="http://pub.idqqimg.com/wpa/images/group.png" alt="快客 - Small Android" title="快客 - Small Android"></a> 

> 验证填写你是从何得知Small的，如qq, weibo, InfoQ, csdn, 朋友推荐, github搜索。<br/> 
进群改备注：如_“福州-GalenLin”_。<br/>
QQ群链接无法使用的手动加 **374601844**

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

[anim-new-prj]: http://code.wequick.net/assets/anims/small-new-project.gif
[anim-new-md]: http://code.wequick.net/assets/anims/small-new-module.gif
[ic-new-act]: http://code.wequick.net/assets/images/small-new-activity.png
[ic-new-act2]: http://code.wequick.net/assets/images/small-new-activity-step2.png
[bintray]: https://bintray.com/galenlin/maven
[gitter]: https://gitter.im/wequick/Small
[ic-run]: http://code.wequick.net/assets/images/small-run.png
