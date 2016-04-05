# Small iOS

## Examples

* 使用者模式[Sample](Sample)

  > 需要先编译Pods

* 开发者模式[DevSample](DevSample)

  > 需要去除并行编译模式：Edit Scheme...->Build->Build Options-> [ ] Parallelize Build
  
> 各个组件需要签名后才支持代码级别更新。示例中更新例子为xib内容更新。<br/>
> 在没有设置签名之前，请在模拟器上跑示例。

## 加入我们

我们鼓励大家成为**Small**的开发者，并享受开源协作的乐趣。

1. 提交[Bug](https://github.com/wequick/Small/issues)并协助我们确认修复。
2. 提交[PR](https://github.com/wequick/Small/pulls)来完善文档、修复bug、完成待实现功能或者讨论中的建议。
3. 在QQ群或[Gitter][gitter]参与讨论，提供建议。

> 更多细节请参考[开源贡献指南](https://guides.github.com/activities/contributing-to-open-source/)。

#### TODO List

  - [ ] Sample支持CocoaPods
  - [ ] 设置签名脚本（现在需要对每个组件Project手动设置签名）
  - [ ] 热更新（现在需要重启生效）
  - [ ] Xcode Template for creating new `Small Bundle`
  - [ ] Bundle Slicing

## 文档
[Wiki/iOS](https://github.com/wequick/small/wiki/iOS)

## 联系我们

当你决定这么做时，希望你已经下载了源码并成功运行。并且关注<br/>
&nbsp;&nbsp;&nbsp;&nbsp;_“如何**从Small中学到东西**以及**为Small做点什么**，促进**共同成长**。”_<br/>

<a target="_blank" href="http://shang.qq.com/wpa/qunwpa?idkey=5c5f09c489554eda469a22b05be1c00cb5770799d291c76d260283ad32a80b73"><img border="0" src="http://pub.idqqimg.com/wpa/images/group.png" alt="快客 - Small iOS" title="快客 - Small iOS"></a> 

> 验证填写你是从何得知Small的，如qq, weibo, InfoQ, csdn, 朋友推荐, github搜索。<br/> 
进群改备注：如_“福州-GalenLin”_。<br/>
QQ群链接无法使用的手动加 **78374636**

## License
Apache License 2.0

[gitter]: https://gitter.im/wequick/Small
