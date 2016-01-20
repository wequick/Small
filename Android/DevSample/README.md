# DevSample

This is for Small developer.

## Getting Started

### Step 1. Clone Small (下载源码)
    > cd [你要放Small的目录]
    > git clone https://github.com/wequick/Small.git

> 强烈建议使用git命令行，方便更新维护。Windows用户入口：[Git for Windows][git-win]<br/>
> 后续更新可以使用命令：git pull origin master

### Step 2. Import DevSample project (导入开发工程)
打开Android Studio，File->New->Import Project... 选择**DevSample**文件夹，导入。

![Small devsample][ic-devsample]

* DevSample `开发工程`
  * [buildSrc](buildSrc) `组件编译插件，用于打包组件`
  * [small](small) `核心库，用于加载组件`

> buildSrc在修改后会被自动编译。

注意编译单个组件的命令有所不同：

  > [./]gradlew :app.main:assembleRelease
    
> P.s. gradlew命令支持缩写，比如`assembleRelease`可以缩写为`aR`

[git-win]: http://git-scm.com/downloads
[ic-devsample]: http://code.wequick.net/assets/images/small-devsample.png
