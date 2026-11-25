# University-ToolBox（高校百宝箱）

University-ToolBox 是一款面向高校师生的开源 Android 多功能工具箱应用，集成了统一工具入口、应用授权管理与多种学业及日常实用工具。

## 核心功能

- **工具箱首页与自适应导航**：支持按分类筛选、关键词与拼音首字母即时检索、置顶收藏、最近使用记录以及系统桌面快捷方式，适配手机单栏与平板及折叠屏双栏显示。
- **授权引导与管理**：支持本机设备码查看与复制、激活口令粘贴、二维码扫码激活与深度链接唤起，实时展示当前授权等级、有效期及可用范围。
- **学习通课程助手**：支持多账号绑定与独立管理、课程同步、活动监控与签到记录查询。
- **GPA 计算器**：支持北大 4.0、标准 4.0、WES 4.0、4.3 制与 5.0 制等多种绩点算法换算，提供课程成绩增删编辑与目标绩点反推规划。
- **条码与二维码工作台**：支持相机实时扫码、相册选图解码与文本解析，结构化展示网页链接安全提示、Wi-Fi 配置、联系人、地理位置及 JSON 内容，支持敏感字段默认隐藏与历史记录管理。
- **全局设置中心**：支持浅色/深色/跟随系统主题切换、动态取色、简体中文与英文应用内切换、权限状态查看、缓存清理、诊断日志预览与导出，以及应用更新检查。

## 环境要求

- **JDK**：JDK 21
- **Android SDK**：Android SDK Platform 36（Android 16）、Build-Tools `36.0.0`
- **运行设备**：Android 8.0（API 26）及以上版本的 Android 设备或模拟器

## 从源码编译与安装

1. 克隆项目仓库并进入根目录：

   ```bash
   git clone https://github.com/onprs/University-ToolBox.git
   cd University-ToolBox
   ```

2. 在项目根目录新建 `local.properties`，填写本机 Android SDK 路径：

   ```properties
   sdk.dir=/path/to/your/Android/Sdk
   ```

3. 构建调试安装包：

   ```bash
   ./gradlew assembleDebug
   ```

4. 安装到已连接的设备或模拟器：

   ```bash
   ./gradlew installDebug
   ```

   构建产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 基础使用说明

1. 首次启动应用会进入授权激活引导页，可输入或扫描有效激活码，也可通过 `universitytoolbox://unlock?key=...` 深度链接填入激活口令。
2. 使用从源码编译的调试版本体验功能时，可输入以下调试口令解锁对应授权等级：
   - `DEV-FREE-UNLOCK`（免费版，可使用 GPA 计算器等基础工具）
   - `DEV-STD-UNLOCK`（标准版，额外解锁学习通课程助手与条码工作台）
   - `DEV-PRO-UNLOCK`（专业版）
   - `DEV-FULL-UNLOCK`（完整版）
3. 解锁后可在首页浏览或检索工具、置顶常用工具，并可在右上角进入设置页切换深色模式、动态取色或应用语言。

## 开源协议

本项目采用 [MIT License](LICENSE) 开源许可。

## 相关文档

- 参与贡献：[CONTRIBUTING.md](CONTRIBUTING.md)
- 开发文档：[docs/architecture-and-development.md](docs/architecture-and-development.md)
