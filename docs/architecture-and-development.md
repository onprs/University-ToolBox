# 架构与开发技术指南

> 本文档面向 **University-ToolBox** 的研发贡献者与项目维护者。公开社区开发与维护者闭源联合构建的环境前提已按章节明确划分。

---

## 一、公开仓库模块化架构

公开主仓库采用“**应用宿主装配 + 克制公共底座 + 自治业务工具**”的分层结构：

```text
University-ToolBox/
├── app/                     # 宿主外壳：Application、MainActivity、AppNavigationGate、DI 聚合、资源与混淆规则
├── benchmark/               # 性能基准测试与 Baseline Profile 采集模块
├── build-logic/convention/  # Gradle Convention Plugins（Android/Compose/Hilt/Room/Detekt/Ktlint）
├── core-contracts/          # 叶子契约构建（includeBuild 挂载 core/common 与 core/license-api，消除跨仓循环）
├── core/
│   ├── common/              # AppResult/DomainError 统一错误模型、Dispatcher 限定符、TimeProvider、Redactor
│   ├── license-api/         # 授权接口契约：LicenseEngine、LicenseState、LicenseTier、LicenseCoordinator
│   ├── license-stub/        # 仅面向开源 Debug 构建的调试桩实现（不包含正式签名算法）
│   ├── tool-api/            # 工具发现契约：ToolDescriptor、ToolRegistry、EntryProviderInstaller
│   ├── designsystem/        # Material 3 主题、配色/排版/间距 Token 与通用基础组件
│   ├── ui/                  # 通用状态页（Loading/Empty/Error）、LockedToolCard、DevStubBanner 等
│   ├── network/             # 全局唯一 @RootHttpClient OkHttpClient、Lenient Json 与可信网络时间拦截器
│   ├── datastore/           # CoreBindingsModule、UserPreferencesRepository、EncryptedLicenseStorage
│   ├── security/            # Google Tink AES256-GCM AEAD 引擎与 EncryptedDataStoreFactory
│   ├── database/            # 宿主元数据 Room 数据库（收藏、最近使用、脱敏审计记录）
│   ├── scanner/             # CameraX + ML Kit 条码扫描封装
│   └── update/              # UpdateManifest、RemoteGovernanceRepository 与 PackageInstaller 自更新引擎
└── feature/
    ├── unlock/              # 授权激活与详情管理页面、Deep Link 口令解析
    ├── home/                # 自适应双栏工具箱首页、拼音首字母检索、动态 App Shortcuts 发布器
    ├── settings/            # 外观与语言切换、权限核验看板、缓存清理、诊断日志导出与更新页
    ├── chaoxing/{api,impl}/ # 学习通课程与签到助手（自治 ChaoxingDatabase、隔离会话容器）
    ├── gpa/{api,impl}/      # GPA 多标准计算与目标规划工具（FREE 级）
    └── qrcode-studio/{api,impl}/ # 条码与二维码结构检视工作台（STANDARD 级）
```

### 1.1 全局依赖注入单一绑定原则

为避免 Hilt 编译期重复绑定冲突，核心基础设施由以下模块唯一提供，`:app` 与 `:feature:*` 仅注入消费：

| 绑定类型 | 唯一提供模块 | 说明 |
| :--- | :--- | :--- |
| `@IoDispatcher` / `@DefaultDispatcher` / `@MainDispatcher` / `@ApplicationScope` / `DispatcherProvider` / `TimeProvider` | `:core:datastore` (`CoreBindingsModule`) | 全应用共享协程调度与时间源 |
| `LicenseStorage` / `UserPreferencesRepository` | `:core:datastore` (`DataStoreModule`) | 基于 Tink 加密存储授权元数据 |
| `@RootHttpClient OkHttpClient` / `Json` | `:core:network` (`NetworkModule`) | 共享连接池并采集 HTTPS `Date` 头同步防回拨水位 |
| `AeadCryptoEngine` | `:core:security` (`SecurityModule`) | 绑定 Android Keystore 的 Tink AEAD 实例 |
| `ToolboxDatabase` / `ToolboxMetadataRepository` | `:core:database` (`DatabaseModule`) | 宿主轻量元数据持久层 |
| `RemoteGovernanceRepository` / `AppUpdateManager` | `:core:update` (`UpdateModule`) | 远程熔断、吊销列表同步与流式自更新 |
| `LicenseEngine` | `:core:license-stub`（Debug 无私仓）或闭源引擎 | 通过构建期探测二选一注入 |
| `ToolRegistry` / `@Multibinds Set<ToolDescriptor>` / `@Multibinds Set<EntryProviderInstaller>` | `:app` (`AppModule`) | 汇聚各业务工具的注册描述符与路由 |

### 1.2 宿主启动与路由门禁（`AppNavigationGate`）

- **`ToolboxApplication`**：
  - 禁用 `AndroidManifest.xml` 中 `androidx.work.WorkManagerInitializer` 的默认自动初始化，改为通过 `Configuration.Provider` 按需延迟装配 `HiltWorkerFactory`。
  - 实现 Coil 3 `SingletonImageLoader.Factory`，通过 `OkHttpNetworkFetcherFactory` 延迟复用 `@RootHttpClient`。
- **`MainActivity` 与 `AppNavigationGate`**：
  - 继承 `AppCompatActivity` 以配合 `AppCompatDelegate.setApplicationLocales` 应用内语言切换，同时支持 `installSplashScreen()` 与 `enableEdgeToEdge()`。
  - `AppNavigationGate` 作为纯 Kotlin 状态机监听 `LicenseCoordinator.state` 与 `RemoteGovernanceRepository.disabledTools`：
    - `Initializing`：显示启动过渡界面，暂存外部深链但不打开任何工具。
    - `Locked`：只装配 `UnlockRoute`，若深链携带激活口令则注入 `initialActivationCode`；若深链请求 `universitytoolbox://tool/<id>`，仅暂存待打开目标 ID 并拦截，绝不越权放行内部页面。
    - `Unlocked`：默认栈底为 `HomeNavKey`，挂载 `HomeRoute`、`SettingsRoute`、`UnlockRoute` 及全部 Hilt `@IntoSet` 注入的 `EntryProviderInstaller`。开启任意工具前均经 `LicenseCoordinator.evaluateAccess(descriptor, isRemoteDisabled)` 校验，放行时记录 `ToolShortcutPublisher.reportToolUsed`，受限时跳转 `UnlockNavKey(manageMode = true)` 或展示熔断提示。

### 1.3 安全存储、混淆与基准优化

- **云备份隔离**：`app/src/main/res/xml/backup_rules.xml` 与 `data_extraction_rules.xml` 显式排除 Tink 主密钥集偏好文件 `university_toolbox_tink_keyset_prefs.xml`、加密存储目录及业务凭据库，防止硬件绑定密钥跨设备备份失效或泄露。
- **R8 规则**：`app/proguard-rules.pro` 保留 JNI `native` 方法、闭源引擎桥接类、Kotlinx Serialization 序列化器、Room 实体、Tink Protobuf 与 ML Kit 相关类。
- **性能基准与 Baseline Profile**：
  - `:app` 集成 `androidx.profileinstaller` 与 `app/src/main/baseline-prof.txt`，覆盖应用启动、主题初始化、门禁校验与首页/解锁页渲染路径。
  - `:benchmark` 模块使用 `com.android.test` 声明 `StartupBenchmark`（`StartupTimingMetric`）与 `BaselineProfileGenerator`（`BaselineProfileRule`）。

---

## 二、公私双仓切换与 CI/CD 流水线（开发与维护环境对照）

### 2.1 公开开发与 PR 门禁（无私有仓库）

- 适用场景：外部贡献者本地开发及 `.github/workflows/ci.yml`。
- 当未探测到私有仓库（或传入 `-Ptoolbox.privateLicenseDir=none`）时，`gradle.extra["useProprietaryLicense"]` 为 `false`，`:app` 在 `debug` 变体装配 `:core:license-stub`。
- `ci.yml` 在 `ubuntu-latest` 上使用 JDK 21 与 Android SDK 36 执行：

  ```bash
  ./gradlew -Ptoolbox.privateLicenseDir=none checkCoreContracts ktlintCheck detekt testDebugUnitTest assembleDebug --no-daemon
  ```

### 2.2 维护者联合构建与 Release 发布（挂载私有引擎）

- 适用场景：项目维护者联调及推送 `v*` 标签触发 `.github/workflows/release.yml`。
- 当 `settings.gradle.kts` 在 `private/Onprs-Encryption`、`../Onprs-Encryption` 或 `-Ptoolbox.privateLicenseDir` 指定目录探测到私有仓库时，`gradle.extra["useProprietaryLicense"]` 为 `true`，Gradle 通过 Composite Build 将 `io.github.onprs.encryption:license-engine-android` 替换为闭源项目 `:license-engine-android`，并将原生动态库 `libonprs_license.so` 打包进 APK。由于契约模块已独立托管于叶子构建 `core-contracts/`，双仓互不形成循环依赖。
- `app/build.gradle.kts` 对名称包含 `Release` 的任务强制执行前置断言：若 `useProprietaryLicense` 不为 `true`，立即中止构建，杜绝在正式发布包中混入公开 Stub。
- `release.yml` 在 Ubuntu Runner 上完成私有引擎检出、NDK/CMake 原生编译、`testReleaseUnitTest` 与签名版 `assembleRelease`，并自动产出随附 SHA-256 摘要的 `update-manifest.json` 一并发布至 GitHub Release。
