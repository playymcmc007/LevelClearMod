# LevelClearMod

一个能帮助你存档断舍离的嚎模组！(Minecraft模组）

你是否有过整合包毕业后恋恋不舍不肯换新档的烦恼？你是否有过在玩服务器的时候出现大佬毕业导致服务器冷清还不换整合包的情况，那么这个模组你应该很需要！
## 当前支持版本

1.21.1 Fabric（先把代码调整好再考虑版本兼容性）

需要 Cloth Config Mod 和 Fabric API 作为前置

光这个模组就花了一周多的时间（ DeepSeek 卡死了），所以 Neoforge 暂时不考虑，等到 Fabric 的版本齐了再说

## 宣传视频

https://www.bilibili.com/video/BV15FA3eWES1/

## 这个模组做了什么？

众所周知，一个有着明确任务的整合包都有着至少一个最终毕业目标（无目标的水槽包除外），也可能是在游玩一个大型模组完成成就的路上，当安装这个模组的时候，配置好 config 文件，当你获得指定的物品或成就时，模组会将你强制调为旁观者模式且无法重新调回来，同时聊天框会显示指定的通关文本，告诉大家“这个存档已经通关毕业了”，因为无法修改模式，玩家就可以静静地观赏着自己的存档，如果仍然对旁观者的存档恋恋不舍，配置文件还可以配置销毁存档模式，只要你退出游戏，模组就会帮你把你所在的存档文件销毁，从此彻底和你的存档说拜拜。

## 多人游戏兼容性？

存档使用的代码是服务器相关，当多人游戏的时候通关文本只会显示第一个通关的人，存档的删除路径为绝对路径，如果存档位于服务器，那么服务器对应的存档文件正常情况下会和单人模式一样正常销毁

## 整合包兼容性？

安装到大型整合包中没有崩溃，模组正常运行

## 代码相关

整个模组逻辑由 DeepSeek 和 ChatGP T制作而成，技术含量极低，而且随着调试的进行，出现了大量屎山代码，删了会出 bug ，不删又显得意义不明，如果可以的话希望有大佬看到这个项目并提交更改

代码中的注释大多是 DeepSeek 的注释，我另外也加了一些注释

有关 Config 文件的迷之写法的注释是因为已经写到后期了屎山代码没法改，所以只能蹩脚地那么写，相关参数实际没有任何效果

模组根据 MIT 协议开源，修改的话只要标注我的名字和来源即可

（施工中，正在进行模组收尾）

## save 报错

日志中出现 save 报错是因为开启销毁存档功能后存档先被删除后再执行保存，文件被删除后就会保存失败，是正常现象

## 其他

模组的中文名没想好，英文名是游戏中结算画面“通关”的意思
