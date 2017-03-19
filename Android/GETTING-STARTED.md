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
        classpath 'net.wequick.tools.build:gradle-small:1.2.0-beta1'
    }
}

...

apply plugin: 'net.wequick.small'
```

#### 2.2 配置Small DSL （可选）

目前只有一个属性`aarVersion`，表示Small aar的代码库版本。如果没有设置，默认为`gradle-small`的版本。

```groovy
small {
    aarVersion = '1.2.0-beta1'
}
```

注意上述代码要放在`apply plugin: 'net.wequick.small'`下，否则编译报错

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

### 5.3 配置Application

在`app`模块中新建`MyApplication`重载`onCreate`方法：

```java
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Small.preSetUp(this);
    }
}
```

修改`app`模块中新建`AndroidManifest`中`<application>`节点：
```xml
<application
        android:name="MyApplication"
        ...
        >
```

#### 5.4 加载插件

在`app`模块的`LaunchActivity`重载`onStart`方法：

```java
@Override
protected void onStart() {
    super.onStart();
    Small.setBaseUri("http://example.com/");
    Small.setUp(this, new net.wequick.small.Small.OnCompleteListener() {

        @Override
        public void onComplete() {
            Small.openUri("main", LaunchActivity.this);//启动默认的Activity，参考wiki中的UI route启动其他Activity
        }
    });
}
```

[anim-new-prj]: http://code.wequick.net/assets/anims/small-new-project.gif
[anim-new-md]: http://code.wequick.net/assets/anims/small-new-module.gif
[bintray]: https://bintray.com/galenlin/maven
