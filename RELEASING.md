# 真机测试清单（RELEASING）

本文件用于在本地 Minecraft NeoForge 1.21.1 客户端中对 `mod_scan_trans_lib-1.0.0.jar` 进行实机验证。
按 A → G 顺序逐项执行，每项通过后勾选 `[x]`。全部通过即可发布。

---

## 测试前置准备

- [ ] 安装 Minecraft 1.21.1 + NeoForge 21.1.250（或更高兼容版本）
- [ ] 将 `build/libs/mod_scan_trans_lib-1.0.0.jar` 放入 `.minecraft/mods/` 目录
- [ ] 启动游戏，确认模组列表中可见「Mod Scan Translation Support Library」
- [ ] 确认 `config/mod_scan_trans_lib.toml` 已生成（首次启动后自动生成）

---

## A. 界面打开

- [ ] A1. 游戏内按快捷键 `]`（右方括号）→ 设置界面弹出
- [ ] A2. 模组列表中点本模组的「Config」按钮 → 设置界面弹出
- [ ] A3. 界面显示：6 个开关 + 1 个语言下拉 + 3 个按钮（保存并重载 / 清理 AI 缓存 / 取消）
- [ ] A4. 第 1 个开关是「模组主开关」，位于最顶部

## B. 配置持久化（检查 `config/mod_scan_trans_lib.toml`）

- [ ] B1. 改任一开关 → 点「保存并重载」→ toml 文件中对应字段更新
- [ ] B2. 改语言下拉 → 保存 → toml 中 `targetLanguage` 更新
- [ ] B3. 重启游戏 → 界面显示的值与 toml 一致

## C. core 配置同步

- [ ] C1. 关 CFPA → 保存 → 日志中 `common setup` 行显示 `CFPA=false`
- [ ] C2. 关 AI → 保存 → core `TransLibConfig.isAiEnabled()` 返回 false
- [ ] C3. 改家族术语 / 异步扫描 → 保存 → core 配置同步更新

## D. 翻译重载

- [ ] D1. 点「保存并重载」→ 游戏内已加载的模组词条被刷新
- [ ] D2. 关「模组主开关」→ 保存 → 日志出现「模组主开关已关闭，跳过翻译注入」
- [ ] D3. 切目标语言（如 zh_CN ↔ en_US）→ 保存 → 词条按新语言刷新

## E. 语言选择约束

- [ ] E1. 语言下拉只显示 4 项：简体中文 / English / Português / ภาษาไทย
- [ ] E2. 全程无 IP 读取、无地理位置查询（代码无 `InetAddress` / `GeoIP` 调用）

## F. 缓存操作

- [ ] F1. 点「清理 AI 缓存」按钮 → 日志显示缓存已清空
- [ ] F2. 缓存清理不影响当前已注入的词条（下次重载才重新生成）

## G. 不修改 core（约束验证）

- [ ] G1. `git diff --name-only` 确认无 `src/main/java/com/modscantrans/core/` 路径下文件被修改
- [ ] G2. `modEnabled` 和 `clearCacheOnBoot` 只在 NeoForge 层，不在 core `TransLibConfig` 中

---

## 配置项速查表

| # | 开关名 | toml 键 | 默认值 | 同步到 core | 说明 |
|---|---|---|---|---|---|
| 1 | 模组主开关 | `modEnabled` | true | 否（NeoForge 独有） | 总闸，关闭后跳过翻译注入 |
| 2 | CFPA 联网汉化 | `cfpaEnabled` | true | 是 | 最高优先级人工汉化 |
| 3 | AI 翻译 | `aiEnabled` | true | 是 | 实时机翻 + 本地缓存 |
| 4 | 家族术语参考 | `familyGlossaryEnabled` | true | 是 | 按 modID 匹配家族术语 |
| 5 | 异步扫描 | `asyncScanEnabled` | true | 是 | 后台扫描 mods 文件夹 |
| 6 | 启动时清理缓存 | `clearCacheOnBoot` | false | 否（NeoForge 独有） | 一次性触发 |
| - | 目标语言（下拉） | `targetLanguage` | zh_CN | 是 | 手动选择，不读 IP |

---

## 词条优先级

CFPA 人工汉化 > AI 本地缓存翻译 > 实时 AI 机翻（兜底）

## 已知限制

- 沙箱构建环境无法进行实机测试，本清单需在本地 Minecraft 客户端执行
- 实时 AI 翻译当前使用 `NoopAiTranslator`（占位），后续阶段接入真实 AI 服务
- 翻译注入通过反射替换 `ClientLanguage` 词条表，不同 MC 版本字段名可能变化（已按类型查找兼容）
