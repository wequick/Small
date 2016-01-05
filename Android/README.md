# Small Android

* aar-small 核心库，用于加载组件
* gradle-small-plugin 组件编译插件，用于打包组件
* Sample 示例工程

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

## License
Apache License 2.0

[anim-bB]: http://code.wequick.net/anims/small-android-build-bundle.gif
