# 翻译链路排查：2026-09-13

本轮没有添加词条。先构造失败测试，再修复可复现的链路问题。未进行实际游戏内验证。

## 确认并修复

1. **NPC 合法译文被样式校验拒绝。** 整句翻译需要把同一种颜色拆到高亮词前后时，公共响应校验把样式标签当作不得重复的数据令牌，客户端还原又单独拒绝重复 ID。新增复现测试先失败；修复后两层均允许同一源样式最多拆为四段，仍要求所有源样式存在、标签平衡且不嵌套。数字和图标令牌仍严格守恒，原文回退机制保留。
2. **整句缓存被界面身份拆开。** 同一带样式的句子经 Boss 栏、原版计分板、第三方计分板生成不同路由或布局元数据，原共享层仍把这些差异放入缓存身份。三入口复现测试先失败；修复后统一 HUD 请求身份，本地仍按各自样式还原。服务器、语言、提供商路由、保护令牌及样式标签结构仍参与隔离。

这不是任意两个界面的语义统一：带额外“Objective:”前缀、不同措辞或不同标签分段的文本仍可能产生不同请求。先前 Talk-to 模板仍在；不能将该模板的成功当作所有任务文本同步的证明。

## 批量离线检查

使用本机 Skyblocker 的 items.min.json，经生产 TooltipTextMatcherSupport 规则检查：

- 物品 6,136 个，原始文本 65,266 行。
- 可读文本 50,873 行；空行 14,382 行；纯数字或符号 11 行。
- 被过滤掉的含英文原始文本：0 行。

报告：`build/reports/translation-audit/item-eligibility.txt`。
这是原始物品文本的入口检查，不是翻译准确率统计，也不覆盖其他模组追加的组件、网络回复或最终渲染。

复查命令（PowerShell，路径替换为实际仓库文件）：

```powershell
$env:TAIO_AUDIT_ITEMS = 'E:\Minecraft\.minecraft\versions\Skyblock\skyblock-repo-cache\items.min.json'
.\gradlew.bat :test --tests '*TooltipCorpusAuditTest' --console=plain
```

## 仍需运行时证据

- Cooldown 的原始文本在现有过滤、词典和生产处理测试中均能翻译，尚未复现游戏最终漏译的来源，不能声称已查明根因。
- 最终组件提示框入口已加入 `[translation-audit]` 本地日志：记录仍含拉丁字母的输出、组件类型、itemOwner 状态及独立输入快照。不可见控制字符显式转义；每次启动最多记录 128 种输出，避免逐帧刷屏。不调用 API、不上传日志。
- 这些记录只是待排查样本：NPC 名称、模组名和待翻译文本也会出现，不应一律当成错误。输入与输出可能经过段落换行，日志不假定行号一一对应。
- 此采样仅覆盖 Component 提示框入口。若漏译文字完全没有相应记录，需要继续检查直接构建 ClientTooltipComponent 的渲染路径。

游戏目录目前安装的是上轮 final-tooltip-objective-fix 包。本轮构建为 `build/libs/translate-all-in-one-4.6.2+mc26.2-pipeline-audit-fix.jar`，没有自动改动游戏安装目录。
