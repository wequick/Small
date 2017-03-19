# Small Android

**Small**插件化方案适用于将一个APK拆分为多个公共库插件、业务模块插件的场景。

不同插件方案基于这一场景的对比可以参考[COMPARISION.md](COMPARISION.md)。

## Quick Started

### 创建工程

1. 导入模板

    ```bash
    cd Android
    cp -r templates /Applications/Android\ Studio.app/Contents/plugins/android/lib/
    ```

2. 新建宿主工程

    File->New->New Project创建一个工程，在选择Activity时选择`@Small`模板：
    ![Small template][small-template]
    
    由于IDE的一个bug，无法合并`build.gradle`脚本，
    需要在根目录下的`build.gradle`脚本里，打开注释的语句：
    
    ```groovy
    buildscript  {
        dependencies {
            classpath 'net.wequick.tools.build:gradle-small:1.2.0-beta1'
        }
    }
    
    apply plugin: 'net.wequick.small'
    
    small {
        aarVersion = '1.2.0-beta1'
    }
    ```
    
    并将其中的版本修改为最新版。

3. 新建插件模块

    File->New->Module来创建插件模块，需要满足：
    
    1. 模块名形如：`app.*`, `lib.*`或者`web.*`
    2. 包名包含：`.app.`, `.lib.`或者`.web.`
    
      > 为什么要这样？因为Small会根据包名对插件进行归类，特殊的域名空间如：“.app.” 会让这变得容易。
    
    对`lib.*`模块选择**Android Library**，其他模块选择**Phone & Tablet Module**。
    
    创建一个插件模块，比如`app.main`：
    
    1. 修改**Application/Library name**为`App.main`
    2. 修改**Package name**为`com.example.mysmall.app.main`
    
      ![New small module][anim-new-md]
    
如要手动创建工程，请参考[GETTING-STARTED.md](GETTING-STARTED.md)

### 编译插件

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

### 运行

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

  - [x] 下载插件与更新（进入后台再恢复前台后生效）
  - [x] [终极分离与去除警告](https://github.com/wequick/Small/issues/11)
  - [ ] [加速生成AndroidManifest.xml](https://github.com/wequick/Small/issues/12)
  - [x] [支持混淆](https://github.com/wequick/Small/issues/85)
  - [ ] 插件依赖关系与按需加载
  - [ ] 单元测试

## 文档
[Wiki](https://github.com/wequick/small/wiki/Android)

## 常见问题

[FAQ](https://github.com/wequick/Small/wiki/Android-FAQ)

## 致谢

感谢以下网站收录本项目：

* [p.codekk.com](http://p.codekk.com) @[Trinea](https://github.com/Trinea)
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
[ic-sample]: http://code.wequick.net/assets/images/small-sample.png
[ic-devsample]: http://code.wequick.net/assets/images/small-devsample.png
[anim-bG]: http://code.wequick.net/anims/small/android-build-gradle.gif
[anim-bL]: http://code.wequick.net/anims/small/android-build-lib.gif
[anim-bB]: http://code.wequick.net/anims/small-android-build-bundle.gif
[ic-root-tasks]: http://code.wequick.net/images/small/root-gradle-tasks.png
[ic-sub-tasks]: http://code.wequick.net/images/small/sub-gradle-tasks.png

[ic-new-act]: http://code.wequick.net/assets/images/small-new-activity.png
[ic-new-act2]: http://code.wequick.net/assets/images/small-new-activity-step2.png
[bintray]: https://bintray.com/galenlin/maven
[gitter]: https://gitter.im/wequick/Small
[ic-run]: http://code.wequick.net/assets/images/small-run.png

[anim-new-md]: http://code.wequick.net/assets/anims/small-new-module.gif
[small-template]: http://code.wequick.net/assets/images/small-template.png
