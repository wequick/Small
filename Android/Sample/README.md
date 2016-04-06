# Sample

This is for Small user.

## Getting Started

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

### Step 3. Compile Plugins(编译插件)
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

### Step 4. Run Small

在工具栏![Run small][ic-run]，选择**app**模块，运行。

## License
Apache License 2.0

[git-win]: http://git-scm.com/downloads
[as-run]: http://developer.android.com/images/tools/as-run.png
[ic-sample]: http://code.wequick.net/assets/images/small-sample.png

[anim-bL]: http://code.wequick.net/anims/small/android-build-lib.gif
[anim-bB]: http://code.wequick.net/anims/small-android-build-bundle.gif
[ic-root-tasks]: http://code.wequick.net/images/small/root-gradle-tasks.png
[ic-sub-tasks]: http://code.wequick.net/images/small/sub-gradle-tasks.png
[ic-run]: http://code.wequick.net/assets/images/small-run.png