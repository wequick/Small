# Small Android

* aar-small 核心库，用于加载组件
* gradle-small-plugin 组件编译插件，用于打包组件
* Sample 示例工程

## Installation
### Step 1. Install gradle-small-plugin (安装编译插件)
    > cd Sample
    > ./gradlew -p ../gradle-small-plugin install (Mac OS)
    > gradlew -p ..\gradle-small-plugin install (Windows)
    
### Step 2. Import sample project (导入示例工程)
Import `Sample' by Android Studio.

### Step 3. Build libraries (准备基础库)
  	> [./]gradlew buildLib -q
  	
### Step 4. Build bundles (打包所有组件)
  	> [./]gradlew buildBundle -q

## Documentation
[Wiki](https://github.com/wequick/small/wiki/Android)

## License
Apache License 2.0
