# 模组扫描翻译支持库（Mod Scan Translation Support Library）

> 异步扫描 mods 文件夹 Jar 提取语言文本；联网拉取 CFPA 社区人工汉化；AI 翻译本地缓存 + 实时机翻兜底；按 modID 识别模组家族术语库与旁系参考库；目标语言完全由用户手动选择，不读取 IP、不按地理位置自动切换语种。

## 项目简介

本模组是一个 Minecraft NeoForge 底层翻译支持库，旨在为随启动器加载进游戏的模组文本提供统一的翻译注入能力。模组启动后会：

1. **异步扫描** `mods/` 目录下所有 Jar，提取每个模组的源语言词条（默认 `en_us.json`）；
2. 按 **modID 匹配家族术语库**（而非模组显示名称），加载对应的家族术语与旁系参考库；
3. 联网拉取 **CFPA 社区人工汉化**（最高优先级）；
4. 命中本地 **AI 翻译缓存**（次优先级）；
5. 未命中则调用 **实时 AI 机翻**（兜底，后续阶段接入）；
6. 按全局优先级合并翻译后，**注入游戏 I18n 系统**，刷新游戏内词条。

词条优先级：**CFPA 人工汉化 > AI 本地缓存翻译 > 实时 AI 机翻**。

### 设计要点

- 模组识别依靠 **modID 匹配家族表**，而非模组显示名称；
- 目标语言 **完全由用户在独立设置界面手动选择**，程序不读取 IP、不按地理位置自动切换语种；
- 翻译注入发生在原版/其他模组语言文件加载**之后**，防止被覆盖；
- core 核心层为**纯 Java、加载器无关**代码，NeoForge 适配层只调用 core 公开 API。

## 环境要求

| 项目 | 要求 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.250 或更高兼容版本 |
| Java | 21（Mojang 在 1.21 中向终端用户分发 Java 21） |
| 加载器 | FML `@Mod`（javafml） |

## 安装方法

### 玩家安装

1. 安装 Minecraft 1.21.1 对应的 NeoForge 21.1.250+；
2. 从 [Releases](#) 下载 `mod_scan_trans_lib-1.0.0.jar`；
3. 将 jar 放入 `.minecraft/mods/` 目录；
4. 启动游戏，模组会自动在 `config/` 目录生成 `mod_scan_trans_lib.toml` 配置文件。

### 开发者构建

```bash
# 克隆仓库
git clone <repo-url>
cd mod-scan-trans-lib

# 使用 Gradle 构建（需 Java 21）
./gradlew build

# 产物位于
build/libs/mod_scan_trans_lib-1.0.0.jar
```

> 若在受限网络环境构建，需为 Gradle 配置 HTTP/HTTPS 代理，并禁用 configuration cache：
> ```bash
> gradle build --no-daemon --no-configuration-cache \
>   -Dhttp.proxyHost=<host> -Dhttp.proxyPort=<port> \
>   -Dhttps.proxyHost=<host> -Dhttps.proxyPort=<port> \
>   -Dhttps.protocols=TLSv1.2,TLSv1.3
> ```

## 配置说明

模组提供**独立设置界面**，可通过以下方式打开：

- **快捷键**：游戏内按 `]`（右方括号）
- **模组列表**：在模组列表中点击本模组的「Config」按钮

### 6 个配置开关 + 1 个语言下拉

| # | 配置项 | 默认值 | 说明 |
|---|---|---|---|
| 1 | 模组主开关 | 开 | 总闸，关闭后跳过翻译注入，等效禁用本模组效果 |
| 2 | CFPA 联网汉化 | 开 | 最高优先级，关闭则不联网拉取 CFPA 词条 |
| 3 | AI 翻译 | 开 | 关闭则不调用实时 AI 机翻，但仍可使用本地缓存 |
| 4 | 家族术语参考 | 开 | 按 modID 匹配家族术语库；旁系模组永久启用旁系参考库，不受此开关控制 |
| 5 | 异步扫描 | 开 | 后台扫描 mods 文件夹，不阻塞主线程 |
| 6 | 启动时清理缓存 | 关 | 设为开则启动后清理一次 AI 翻译缓存 |
| - | 目标语言 | zh_CN | 手动选择，可选：简体中文 / English / Português / ภาษาไทย |

### 操作按钮

- **保存并重载**：同步更新 core 配置 + 写入 toml + 触发翻译重载
- **清理 AI 缓存**：立即清空本地 AI 翻译缓存（不影响当前已注入的词条）
- **取消**：丢弃改动，返回上级界面

### 配置文件

配置文件位于 `config/mod_scan_trans_lib.toml`，可直接编辑文本，重启游戏后生效。界面修改会实时写回该文件。

## 项目结构

```
src/main/java/com/modscantrans/
├── core/                    # 核心层（纯 Java，加载器无关）
│   ├── scanner/             #   模组 Jar 扫描
│   ├── cfpa/                #   CFPA 社区汉化
│   ├── family/              #   家族术语库
│   ├── ai/                  #   AI 翻译
│   ├── cache/               #   翻译缓存
│   ├── i18n/                #   翻译服务（合并优先级）
│   ├── TransLibConfig.java  #   运行配置
│   └── TargetLanguage.java  #   目标语言
├── neoforge/                # NeoForge 适配层
│   ├── config/              #   toml 配置定义
│   ├── event/               #   翻译注入器（资源重载事件）
│   ├── gui/                 #   独立设置界面
│   ├── client/              #   快捷键 + 客户端事件
│   └── ModScanTransLib.java #   @Mod 主入口
└── fabric/ forge/           # 预留其他加载器适配层
```

## 真机测试

实机测试清单见 [RELEASING.md](RELEASING.md)，包含 A-G 共 7 大类验证项。

## 赞助说明

本项目为开源软件，永久免费。如果本项目对你有帮助，欢迎赞助支持持续开发：

- **爱发电**：https://afdian.net/（搜索「模组扫描翻译支持库」）
- **GitHub Sponsors**：https://github.com/sponsors/（搜索本项目）

你的支持是项目维护与新功能开发的动力。

## 许可证

本项目基于 **MIT License** 开源。

```
MIT License

Copyright (c) 2026 Mod Scan Trans Lib Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 反馈与问题

- **问题追踪**：https://github.com/mod-scan-trans-lib/mod-scan-trans-lib/issues
- **主页**：https://github.com/mod-scan-trans-lib/mod-scan-trans-lib
