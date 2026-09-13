# 在线 SkyBlock 社区翻译整合

获取日期：2026-09-08。本次实际从 GitHub 下载以下仓库，不使用游戏翻译缓存。

1. [BingKKni/SkyBlockZH](https://github.com/BingKKni/SkyBlockZH)，提交 `8b9cbe5376c7680cbc2b211c0000d535b854da9a`。提取 `original_text/**/GUI_Item/*.json` 和 `_shared` 中可独立转换的中文条目。上游许可证为 GPL-3.0-only，完整源码与 LICENSE 保留于 `../upstream/SkyBlockZH`。作者说明大部分翻译由 AI 生成并由其游戏内检查，不代表每一条均已验证。

2. [ShoeBox-CX/skyblock-translation-data](https://github.com/ShoeBox-CX/skyblock-translation-data)，提交 `99d2431fcd0f0c01eafc997d2353cbc73cfccca4`。提取 `vocab.json` 与 `tooltips.json` 的静态中英词条。繁体字通过 Windows 简体转换转为简体，台湾地区用语未强行改写。公开仓库未附许可证，本次仅用于用户本地字典，未发布或向上游提交；公开可读不代表获得再次分发许可。

保留导入前所有原有键值；以大小写、空白、引号及颜色码归一化去重。新增译文冲突时保留先前结果，并在 `report.json` 记录。排除跨行续接、复杂捕获及源引擎专用模板；SkyBlockZH 仅转换明确标注为 number 的单个 `%s` 为 `{d1}`。每条新增结果在报告中附有来源路径。

本文件是社区译文整合，不是 Hypixel 官方语言包。上一轮本地附带词库的结果保留在基础字典中，本次新增数量单独统计。
