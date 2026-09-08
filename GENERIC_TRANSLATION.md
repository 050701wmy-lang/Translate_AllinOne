# 通用翻译覆盖（Minecraft 26.2 / Fabric）

在 AIO 现有结构化 Component 翻译链路上增加通用覆盖，无需同时安装 SimpleTranslate。

## 使用

打开 `/taio` → **其他翻译**，启用总开关并按需打开以下功能。新增开关默认关闭，已有配置可直接加载。

| 功能 | 覆盖范围 |
| --- | --- |
| 通用界面翻译 | 原版 Screen 与 Mod Screen 的 Component、String、FormattedText、FormattedCharSequence，以及 ActiveTextCollector / RenderingTextCollector 路径 |
| Tab 页眉与页脚 | 翻译后重新换行，保留原版列表布局 |
| 玩家自定义显示名 | 始终保持原文，包括昵称、前缀和样式；Tab 页眉/页脚仍可翻译 |
| Boss 血条 | 在测量宽度和绘制前替换名称，不修改血条数据 |
| 标题与副标题 | 保留显示时长、缩放和淡入淡出 |
| ActionBar | 绘制时替换文本，WynnCraft 对话优先走原专项链路 |
| HoverEvent 悬浮文本 | 聊天、书本和 Mod 界面的 ShowText；保留 ShowItem / ShowEntity 现有处理 |

共用 **其他翻译** 的目标语言、Provider / 模型路由、并发与批处理设置、翻译键、查看原文模式和刷新缓存键。默认快捷键模式为“按住翻译”：需要先绑定按键，或将快捷键模式改为禁用快捷键以持续显示译文。

## 专项优先与数据边界

- 保留 WynnCraft、聊天输入 AI 助手、多 Provider 路由、字典及原有专项缓存。通用功能复用 AIO 的请求排队、结构校验、动态数值占位符和失败回退机制。
- 聊天屏幕、书本阅读/编辑屏幕、告示牌编辑屏幕和 AIO 自身配置界面不进入通用 Screen 链路。它们的专项翻译和 ShowText 悬浮翻译仍可独立工作。
- 容器物品提示在内部作用域中绘制，通用 UI 收集器不再处理已交给物品模块的文本。输入框内容沿用已有输入作用域保护。
- ShowText 使用独立悬浮文本作用域，防止物品和 UI 模块再次提交同一内容；AIO 聊天“查看原文”悬浮框始终保留原文。
- HUD 不覆盖游戏持有的原始 Component。请求未完成或失败时显示原文，后续帧可读取译文，切换原文模式不会丢失原文。
- 新增 `player_list`、`boss_bar`、`title`、`action_bar`、`hover_text` 五个 Component 路由及独立持久化缓存文件；复用现有 `screen_ui` 缓存。缓存统计可在“缓存备份”界面查看，生命周期保存与备份沿用 AIO 注册表。

## 兼容范围

通用界面翻译允许已有的 Provider 输出格式降级：JSON Schema 不受支持时尝试 JSON Object，再尝试普通响应。降级后仍执行相同的 JSON 解析、ID 和占位符校验，并按端点/模型记忆能力，避免容器标题（例如 Seeds）反复提交不受支持的格式。

受保护占位符若同时命中通用规则和私有槽规则，只按文本中的实际出现次数计数，避免一个地点图标被错误计为两个而拒绝正常译文。缓存语义标记 `private_token_counting=distinct-occurrences-v2` 隔离旧规则生成的结果。

这是渲染入口覆盖，不是 OCR。调用 Minecraft 文本绘制 API 的 Mod Screen 自动获得收集能力；已有 Odin、Athen、Elementa、Talium 等适配保持原状。完全自绘且绕过这些入口的 GPU / 图片文字仍需专项适配。无 Screen 的任意 Mod HUD 不属于通用 Screen 作用域。

HUD 与 ShowText 会跳过纯数值、命令、URL、超过 2048 字符的文本及纯图标内容。与可读文字混合的资源包图标使用受保护占位符，由本地还原；计分板也使用同一保护方式，避免带图标的地点名整段漏译。通用 Screen 继续沿用已有的装饰字形保护和每屏请求预算。Tab 玩家显示名始终保留原文，旧配置中的名字翻译开关不再生效。

## 验证

### 按钮悬浮提示

Minecraft `Tooltip.toCharSequence` 每次读取原始 Component 查询通用界面译文，避免按钮复用首次英文换行缓存而无法显示异步结果。译文在内部作用域重新换行并标记已处理，保留原文切换；适用于 Skyblocker QuickNav 的 Skills、Collections、Armor Sets 等按钮。使用通用界面翻译开关和 Provider。

### 服务器聊天格式

聊天以原消息的样式段重建译文，保留前导空白、项目符号、装饰字形、字体、颜色、粗体、交互信息及显式换行。单一文字段可修复模型返回的 `{sN}` 标记；多个文字段丢失、重复或保护占位符损坏时回退原文，不写入坏译文缓存。流式预览使用同一校验和样式恢复逻辑。NPC 前缀保持原文，即使它与正文位于同一 Component 中。

聊天缓存键升级为 `chat-format-v3`，旧格式译文不会继续命中，无需手动清空其他专项缓存。聊天前缀识别兼容内嵌 `§` 颜色码；正文和入服提示中可从当前玩家列表识别的名字会在提取数字前保护并本地还原，避免昵称中的数字被拆开翻译。自动换行仍由译文宽度和聊天窗口宽度决定，不保证与英文逐像素对齐。回归测试覆盖区域发现提示、字体与悬停信息、私用字形、数字、玩家名、NPC 前缀和不完整流式标记。

样式段校验失败时，使用同一聊天 Provider 进行一次编号 JSON 文字段恢复请求，复用原占位符校验并本地还原样式；失败后直接恢复原始 Component。恢复请求不要求 Provider 支持 JSON Schema。动画、流式与最终更新统一在客户端线程读取当前聊天行，完成后取消活动状态，避免排队回调引用已被替换的行，或旧请求覆盖新请求。

装饰图标提取可能将一个样式段拆成多个文字片段。提交模型前为重复编号分配独立 ID，并映射回原 Style，避免合法消息被误判为重复段。回归用例包含 Foraging Fortune 提示中的 U+E054 图标、颜色码及数值箭头，覆盖普通翻译和编号 JSON 恢复路径。

OpenAI 兼容流收到 `[DONE]` 即结束消费，不再等待服务器关闭连接。流式超时和取消先中断读取线程，再关闭响应流，避免关闭操作等待读取锁而无法中断。测试用本地 SSE 服务保持连接开放，以及持有读取锁的停流场景验证请求能退出；继续沿用原有两分钟请求期限。

### Skyblocker / SkyHanni 物品提示兼容

Skyblocker 的价格、获取日期和博物馆状态使用 `GridComponent.Contents`。该自定义载荷不提供普通文本访问，也禁止序列化。适配器读取其列 Component，复用 AIO 物品翻译路由逐列翻译，再重建原列组，保留对齐、颜色和动态值。兼容代码通过反射识别公开 API，不要求安装 Skyblocker；未知 API 或待处理结果保留原文。

独立英文日期列（例如 `February 8, 2025`）在中文目标语言下使用严格日期解析、本地显示为 `2025年2月8日`，保留日期文字样式，不提交模型或读取旧日期译文缓存。无效日期及暂未支持的其他目标语言保留原日期，避免年月日错位。

普通物品提示的翻译延迟到最终 Component 列表提交，覆盖 SkyHanni 在 `AbstractContainerScreen.extractTooltip` 调用点追加的行，避免提前翻译后被覆盖或再次翻译。Wynnmod、Wynntils 和 REI 的专项提示仍由原入口处理。

这些提示使用 **物品翻译 → 物品描述/lore** 开关、物品目标语言、模型与快捷键，不需要启用 HoverEvent 翻译。参考公开接口：[Skyblocker GridComponent](https://github.com/SkyblockerMod/Skyblocker/blob/main/src/main/java/de/hysky/skyblocker/utils/render/text/GridComponent.java)、[SkyHanni 容器提示入口](https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/mixins/transformers/MixinAbstractContainerScreen.java)。

运行 `gradlew.bat build`。新增测试覆盖：异步原文回退与后续译文、开关与快捷键门控、原文悬浮标记、嵌套作用域异常恢复、缓存隔离，以及实际 Minecraft 26.2 字节码中的 Tab、BossBar、标题、ActionBar、HoverEvent 和容器提示入口。

实际游戏联调可在本地测试世界中通过 `/title`、`/bossbar` 和含 ShowText 的 `/tellraw` 验证标题、血条与悬浮文本；通过服务器 Tab 页眉/页脚验证换行；再检查打开容器、编辑输入框、切换原文模式与 WynnCraft 对话时没有重复请求。字节码测试不能代替与其他 Mod 同时加载的游戏联调，也没有调用真实付费 Provider 进行测试。

参考覆盖范围：[SimpleTranslate](https://github.com/baokaixina/SimpleTranslate)。实现保留 AIO 的架构，没有引入 SimpleTranslate 的运行时依赖。

## Hypixel / SkyBlock messages

The Hypixel settings page contains the independent NPC and server-message auto-translation switches. General dictionary selection remains on the Dictionary page, and external scoreboard compatibility remains on the Scoreboard page. Existing NPC and dictionary settings are retained. The server-message switch defaults to off; both message switches work with general chat translation disabled. They share chat output language, provider, formatting and request handling. Server messages use the existing chat output cache; NPC dialogue retains its dedicated cache.

Server-message recognition requires a Hypixel address and a SKYBLOCK sidebar title. Player chat prefixes, NPC dialogue, bracketed mod messages, Profile IDs, transfer notices and non-text separator lines are excluded. Regression examples include bank interest, skill levels, rewards and rare drops. The external scoreboard compatibility control remains on the general scoreboard page for non-Hypixel uses.
