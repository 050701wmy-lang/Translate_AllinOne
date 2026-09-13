# SkyBlock 社区字典整合

来源：当前项目 `dictionary/skb_items_zh.json`，项目随附说明将该文件列为额外下载的 SkyBlock 物品词库，下载地址为 https://github.com/alexeys1/Translate_AllinOne/releases/download/3.0.0/dictionary.zip 。本次使用本地版本，未验证其是否为最新发布版本。

这是社区汉化整合，不是 Hypixel 官方中文语言包。译文包含社区用语和未完全汉化的文本，未逐条人工审校。

原始基础：用户游戏目录里的 `skyblock_items_zh.json`。原有键值全部保留；补充社区中缺失的词条，按照加载器的大小写、空白和引号规则去重；将数字占位符双层花括号恢复为单层；排除空译文及原译文占位符不对应的新增项。

`merge-report.json` 记录原文件 SHA-256、数量、保留的冲突和跳过的词条，供后续校对。`skyblock_items_zh.json` 为可直接使用的整合结果。
