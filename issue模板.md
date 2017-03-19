- [x] 我已阅读并理解 **[贡献指南](https://github.com/wequick/Small/blob/master/贡献指南.md)**，严格遵循其约定。

# 错误报告

## 你做了什么？

我在插件模块中使用了`DataBinding`. 我尝试了[...]方法，但结果是[...]

## 你期望的结果是什么？

Small能够正确编译数据绑定，并正确运行。

## 实际结果是什么？

Small发生编译报错，报错信息如下：
...(--stacktrace堆栈，需包含net.wequick.gradle包名下的错误)

Small发送运行报错，报错信息如下：
...

## Small环境

### Compile-time

```
  gradle-small plugin : 1.0.0-alpha2 (project)
            small aar : 1.1.0-beta5 (project)
          gradle core : 2.10
       android plugin : 2.0.0
                   OS : Mac OS X 10.12 (x86_64)
```

### Bundles

|  type  |       name       |   PP   |          file           |    size    |
|--------|------------------|--------|-------------------------|------------|
|  host  |  app             |        |                         |            |
|  app   |  app.main        |  0x77  |  *_main.so (x86)        |  10.9 KB   |
|  app   |  app.mine        |  0x16  |  *_mine.so (x86)        |  35.5 KB   |
|  app   |  app.detail      |  0x67  |  *_detail.so (x86)      |  6.6 KB    |
|  app   |  app.home        |  0x70  |  *_home.so (x86)        |  10.4 KB   |
|  lib   |  lib.afterutils  |  0x45  |  *_afterutils.so (x86)  |  21.2 KB   |
|  lib   |  lib.analytics   |  0x76  |  *_analytics.so (x86)   |  125.7 KB  |
|  lib   |  lib.utils       |  0x73  |  *_utils.so (x86)       |  46 KB     |
|  lib   |  lib.style       |  0x79  |  *_style.so (x86)       |  44.9 KB   |
|  web   |  web.about       |        |  *_about.so (x86)       |  24.3 KB   |

（注：编译时错误只需本行以上内容，运行时错误补充本行以下内容）
### Runtime

```
  Device : Samsung Nexus S
     SDK : Android 7.0
     ABI : armeabi-v7a
```
