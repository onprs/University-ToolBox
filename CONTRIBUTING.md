# 参与贡献与环境构建指南

本文档面向 **University-ToolBox** 的公开社区贡献者与项目维护者。公开社区环境与项目维护环境在依赖和构建前提上相互独立，请按各自对应的章节操作。

---

## 一、公开社区开发者环境

外部贡献者无需任何私有仓库权限即可完成全部功能开发、测试与调试构建。

### 1.1 前置要求

- JDK 21
- Android SDK Platform 36 与 Build-Tools `36.0.0`
- 在仓库根目录创建 `local.properties` 并配置本机 `sdk.dir`（该文件已纳入 `.gitignore`，严禁提交）

### 1.2 本地验证命令

提交 Pull Request 前，请确保纯开源模式下的全部静态检查、单元测试与构建通过：

```bash
./gradlew -Ptoolbox.privateLicenseDir=none checkCoreContracts ktlintCheck detekt testDebugUnitTest assembleDebug --no-daemon
```

> 公开仓库的 GitHub Actions CI（`.github/workflows/ci.yml`）会在无任何私有 Secrets 的隔离环境下执行上述检查。

### 1.3 基于 `:core:license-stub` 新增工具模块

公开仓库通过 `:core:tool-api` 与 `core-contracts`（`common` / `license-api`）实现工具模块零侵入自注册，新增工具请遵循以下标准步骤：

1. **拆分 `api` 与 `impl` 模块**：
   - 在 `feature/<tool-name>/api/` 定义实现 `androidx.navigation3.runtime.NavKey` 的 `@Serializable` 路由键与全局唯一 `TOOL_ID` 常量，应用 `universitytoolbox.jvm.library` 或基础库配置及序列化插件。
   - 在 `feature/<tool-name>/impl/` 应用 `universitytoolbox.android.feature` 插件，按需依赖 `libs.universitytoolbox.core.common`、`libs.universitytoolbox.core.license.api`、`:core:tool-api`、`:core:designsystem`、`:core:ui` 等基础模块。严禁横向依赖其他 `:feature:*:impl` 模块。
2. **注册模块与宿主装配**：
   - 在 `settings.gradle.kts` 的 `plannedModules` 中加入 `:feature:<tool-name>:api` 与 `:feature:<tool-name>:impl`。
   - 在 `app/build.gradle.kts` 中引入该工具的 `api` 与 `impl` 依赖。
3. **提供 Hilt 多重绑定**：
   - 在 `:feature:<tool-name>:impl` 的 `@Module @InstallIn(SingletonComponent::class)` 中使用 `@Provides @IntoSet` 提供 `ToolDescriptor`（声明 `id`、`titleRes`、`descriptionRes`、`category`、`requiredTier`、`entryKey` 与 `searchKeywords`）。
   - 使用 `@Provides @IntoSet` 提供 `EntryProviderInstaller`，将路由入口注册到 `EntryProviderScope<NavKey>`。
4. **复用公共基础设施**：
   - 协程调度器（`@IoDispatcher` 等）、`TimeProvider` 与 `LicenseStorage` 由 `:core:datastore` 统一提供，请直接注入，严禁在工具模块或 `:app` 重复绑定。
   - 网络请求请注入 `@RootHttpClient OkHttpClient` 并调用 `newBuilder()` 派生隔离客户端。
   - 敏感凭据存储请使用 `:core:security` 提供的 `AeadCryptoEngine` 或 `EncryptedDataStoreFactory`，日志输出须经过 `Redactor` 脱敏。
5. **多语言资源与测试**：
   - 同步维护 `res/values/strings.xml`（英文）与 `res/values-zh/strings.xml`（简体中文）；UI 文案仅面向最终用户，禁止写入面向开发者的调试说明文本。
   - 为纯业务逻辑与 ViewModel 编写本地单元测试，并在提交前跑通 `ktlintCheck`、`detekt` 与 `testDebugUnitTest`。

### 1.4 合规与安全提交规范

- 严格遵守 MIT 净室实现规范，严禁拷贝任何传染性开源协议代码或引入非兼容第三方依赖。
- 严禁提交真实账号、手机号、密码、密钥文件（`*.keystore` / `*.jks`）、`.env`、`local.properties` 或维护者本机绝对路径及本地代理配置。
- 提交前请检查代码风格（`ktlintCheck`）与静态分析规则（`detekt`，配置文件位于 `config/detekt/detekt.yml`）。

---

## 二、项目维护者联合构建环境（仅供维护者）

本节仅适用于拥有闭源授权引擎仓库访问权限的项目维护者与正式发布流水线。

### 2.1 额外环境要求

- 在公开环境依赖基础上，额外需要安装 Android NDK（如 `28.2.13676358`）与 CMake `3.28.3`，并在 `local.properties` 中按需补充 `cmake.dir`。
- 拥有私有仓库 `Onprs-Encryption` 的只读或维护权限。

### 2.2 挂载私有引擎联合编译

`settings.gradle.kts` 会自动按以下顺序探测私有仓库目录并执行 Gradle Composite Build 依赖替换：

1. 通过 Gradle 属性 `-Ptoolbox.privateLicenseDir=<path>` 或环境变量 `TOOLBOX_PRIVATE_LICENSE_DIR` 显式指定的路径（传入 `none` / `disabled` / `false` 可强制禁用）；
2. 当前仓库内的 `private/Onprs-Encryption`（已被 `.gitignore` 忽略）；
3. 同级父目录下的 `../Onprs-Encryption`。

当成功挂载 `Onprs-Encryption` 后：

- `:app` 自动切换为依赖 `io.github.onprs.encryption:license-engine-android`，构建调试包验证原生层链接：

  ```bash
  ./gradlew checkCoreContracts testDebugUnitTest assembleDebug ktlintCheck detekt --no-daemon
  ```

- `:app` 的 Release 任务配置了构建期安全门禁，缺失私有仓库时会主动阻断打包。维护者在设置发布签名环境变量（`ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`）后，可执行：

  ```bash
  ./gradlew checkCoreContracts testReleaseUnitTest assembleRelease --no-daemon
  ```

### 2.3 自动化发布流水线

正式发版通过推送 `v*` Git Tag 触发 `.github/workflows/release.yml`：

1. 使用仓库受保护的 Deploy Key（`secrets.LICENSE_REPO_SSH_KEY`）将 `Onprs-Encryption` 检出至 `private/Onprs-Encryption`；
2. 解码发布签名证书并构建已签名的 Release APK；
3. 计算 APK 的 SHA-256 摘要并生成 `update-manifest.json`，连同 APK 一并上传至 GitHub Release。
