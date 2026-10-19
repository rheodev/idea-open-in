# Open In

IntelliJ IDEA 的 macOS 插件：在 Project 窗口原有 **Open In** 中，打开所选文件或目录到系统 Terminal、iTerm2、Ghostty，或用户配置的应用与命令。

## 支持范围

- IntelliJ IDEA **2026.2.x（build 262）**；开发与实测版本为 2026.2.3。
- macOS，本机文件系统，Project 窗口单选。
- Terminal、iTerm2、Ghostty 均固定创建新窗口，不在已有窗口内添加 Tab。Ghostty 要求 **1.3.0+**。不再使用 AppleScript，因此不会触发 macOS 自动化确认。
- 不处理远程开发路径、JAR 内文件、库节点和多选。不修改 IDEA 原有菜单项。

菜单中的 **Terminal (macOS)** 指系统 Terminal，区别于 IDEA 原有的内置终端入口。

## 安装

构建产物为 `build/distributions/idea-open-in-1.0.2.zip`。

在 IDEA 中打开 **Settings → Plugins → 齿轮 → Install Plugin from Disk…**，选择 ZIP；按 IDEA 提示完成安装。之后在 Project 窗口右键文件或目录，进入 **Open In**。

## 设置

打开 **Settings → Tools → Open In**：

- 每一条内置或用户新增应用都有独立的 Enabled 复选框。
- 停用仅隐藏插件菜单项，不删除配置；未安装与停用是不同状态，未安装不会改写 Enabled。
- 内置项可编辑、停用、排序，不可删除；用户新增项可编辑、停用、排序和删除。
- Add 添加 macOS Application 或 Command line；Edit 修改；Move Up / Move Down 调整顺序。
- Apply / OK 保存后，下次展开菜单立即生效，无需重启。
- 关闭设置而不应用、取消编辑不会发布草稿。IDEA 的 Reset 恢复已保存配置。
- Refresh Installed Status 在后台检查安装状态；未安装或接口版本不支持的项不会出现在菜单中。
- Test with Directory 使用所选项的未保存配置打开指定目录，不保存修改；测试目录同时作为选中路径、所在目录和项目根目录。

配置保存在 IDEA 用户配置目录的 `options/open-in.xml` 中，所有项目共用，不参与 Settings Sync。内置预设按稳定 ID 合并；升级追加新预设，不覆盖已保存的启停状态、排序和配置。

1.0.1 起移除了终端窗口/Tab 模式设置；旧配置中的 TAB 字段不再参与启动，启停状态、排序和应用定位仍然保留。

## 路径与参数

Target 可选：

- **Selected file or folder**：所选对象本身（自定义应用和 CLI）。
- **Containing directory**：选中目录本身，或文件的父目录。
- **Project root**：当前项目的本地根目录；不存在时动作不可用或明确报错。

终端默认使用 Containing directory，通过各终端自己的“在文件夹新建窗口”服务创建窗口。Terminal/iTerm2/Ghostty 不再通过 AppleScript 控制应用，因此不需要自动化授权，也不会产生反复确认。用户的 shell/Profile 启动命令仍可能影响会话行为。

自定义 macOS 应用可选择 `.app` 或填写 Bundle identifier。是否能打开文件、目录以及如何处理附加参数，取决于目标应用自身。

CLI 必须填写可执行文件的绝对路径。每行是一个独立参数，保留空格，不需要 shell 引号；空行传递空参数，最后的换行仅结束一行，空输入表示无参数。应用附加参数和 CLI 参数支持：

| 占位符 | 值 |
| --- | --- |
| `{path}` | Target 所选择的路径 |
| `{directory}` | 选中目录或选中文件的父目录 |
| `{projectRoot}` | 实际项目根目录；缺失时明确报错 |

参数单次替换后作为字面 argv 传递，不通过 `sh -c`；路径中的占位符文字不会被二次替换。CLI 工作目录为目标目录（目标是文件时为其父目录）。`~`、`$HOME`、通配符、管道和重定向不会自动展开。

## 自动化权限

终端启动不再控制其他应用，不需要自动化授权，因此 Settings 中也不再保留自动化入口。外部进程在后台执行，超时或失败会通过 Open In 通知报告；不会把进程请求成功等同于应用已经加载目标。

## 构建和开发

工程使用 Java **25**、Gradle Wrapper **9.3.1**、IntelliJ Platform Gradle Plugin **2.19.0**。IDEA 2026.2 SDK 本身使用 Java 25，不能用 Java 21 编译。

已有本机 IDEA 时，可以直接使用其 SDK 与自带 JBR：

```bash
export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
./gradlew test buildPlugin -PlocalIdePath="/Applications/IntelliJ IDEA.app"
./gradlew runIde -PlocalIdePath="/Applications/IntelliJ IDEA.app"
./gradlew verifyPlugin -PlocalIdePath="/Applications/IntelliJ IDEA.app"
```

没有本机 SDK 时，使用 Java 25 执行 `./gradlew buildPlugin`，Gradle 会下载目标 IDEA 2026.2.3。Plugin Verifier 默认验证当前构建所用平台；不能据此宣称兼容其他 build 分支。

开发沙箱默认位于 `~/.oi/<工程路径哈希>/open-in/…`，与日常 IDEA 配置隔离。短路径避免 macOS Unix socket 路径长度限制；可通过 `-PsandboxPath=/absolute/short/path` 覆盖。首次构建需要下载 Gradle 与 Maven 依赖。

## 结构

- `model/`：应用配置、路径模式、内置预设合并。
- `settings/`：应用级持久化服务与独立草稿快照。
- `actions/`：原有 RevealGroup 菜单扩展、Project 单选路径解析。
- `launch/`：应用发现缓存、原生服务启动、错误通知。
- `ui/`：原生 IDEA 配置表格和编辑对话框。
- `native/`：免 AppleScript 的 macOS 服务调用源码，随构建编译打包。
- `src/test/`：预设升级保留停用状态、顺序、配置及草稿隔离的回归覆盖。
