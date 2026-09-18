# ChunianSU（KernelSU 非官方定制分支）

应用名：ChunianSU。安装包名：`me.weishu.chuniansu`。
更新仓库：`chunian-nb/ChunianSU`。
固定上游源码：`tiann/KernelSU@9b25a0f2c409f237d1495f3be1a3926bbd6a284c`。

## 范围和状态

本定制只增加**可恢复的桌面入口隐私功能**，不提供软件包枚举过滤、进程隐藏、Root 检测规避、系统应用管理隐藏或签名校验绕过。
系统应用管理、MT 管理器及具有相应权限的进程工具仍可能查到本应用。源码中的 `me.weishu.kernelsu` namespace、JNI 类名、引擎名称及上游作者署名有意保留；它们不等于安装包名。

此源码包编写时未完成 Gradle/NDK 编译、GitHub Actions 实际运行或真机测试。`tools/chuniansu/check.py` 是结构检查，不是编译器。
当前固定上游要求 Android 12/API 31 或以上；具体内核功能与设备、KMI 和当前启用的 KernelSU 实现有关。

## 新增设置

Miuix 与 Material 设置页均增加“隐身模式（仅隐藏桌面入口）”。默认关闭，首次安装正常显示图标。
只切换 `${applicationId}.LauncherAlias` 的启用状态，不禁用应用或真正的 MainActivity。
开关状态直接读取 PackageManager，而不是用偏好设置反复重设，允许外部恢复命令生效。
隐藏前必须确认已在本机测试拨号入口并保存恢复命令。

系统标准拨号代码为 **`*#*#888#*#*`**。`*#888#` 不是本实现支持的标准 Android secret-code 格式。
请先正常打开一次应用，再在仍保留桌面图标的情况下，用系统拨号器输入标准代码。
支持时会自动触发，无需点击拨出。没有反应时不要点击呼叫，也不要开启入口隐藏。
不同厂商拨号器、系统后台启动策略、应用被强行停止的状态可能导致无法唤起。
本实现既不读取通话记录，也不拦截普通呼叫；拨号码不是密码，隐藏图标不提供访问控制。

## 恢复入口

以下命令只改变本应用的入口。先准备能工作的 ADB shell 或已获得 Root 的 Termux，不要等图标隐藏后才授权。

### 已获 Root 的 Termux：恢复图标

```sh
su -c 'pm enable me.weishu.chuniansu/me.weishu.chuniansu.LauncherAlias'
```

### 已获 Root 的 Termux：直接打开真实界面

```sh
su -c 'am start -n me.weishu.chuniansu/me.weishu.kernelsu.ui.MainActivity'
```

### 已连接且授权的 ADB：恢复图标

```sh
adb shell pm enable me.weishu.chuniansu/me.weishu.chuniansu.LauncherAlias
```

### 已连接且授权的 ADB：直接打开真实界面

```sh
adb shell am start -n me.weishu.chuniansu/me.weishu.kernelsu.ui.MainActivity
```

左侧是安装包名，MainActivity 右侧仍是源码 namespace，这是有意设计，不是漏改。
图标恢复可能需要桌面刷新。不要用 `pm disable-user` 禁用整个包，也不需要清数据或卸载。
工作资料/多用户场景需在对应 Android 用户中处理，本包没有对此进行设备验证。

## 签名与内核兼容性：必须阅读

管理器 APK 有自己的安装签名；KernelSU 内核也校验管理器证书。只改包名并重签 APK，不会让已运行的原版内核自动接受新管理器。

本分支的 `chuniansu.yml` 从你固定的 JKS 导出**公开证书**长度和 SHA-256，传给上游的 `expected_size2` / `expected_hash2`。
它让**本次重新编译的 LKM**额外信任你自己的证书，同时保留上游既有签名校验。它不会改变设备上已经运行的内核。

要正常管理 Root，设备必须运行与你的证书、设备 KMI 及实现相匹配的 KernelSU 内核/LKM。
仅安装本包或构建成功，均不等于已经完成内核迁移。设备信息未知时没有通用的安全刷写命令。
先保留当前可用管理器、原厂 boot/init_boot 备份与可恢复启动方式，再根据设备情况迁移。
不要通过删除签名校验来解决“不受信任管理器”。

生成 JKS 后妥善离线备份，并一直复用。失去或换掉签名密钥会破坏后续覆盖安装和自定义内核的信任关系。
不要把 JKS、Base64 私钥、密码提交到公开仓库；Base64 只是编码，不是加密。

## GitHub Actions

使用 `.github/workflows/chuniansu.yml`，显示名 `Build ChunianSU`。它只能手动触发，不在 PR 代码上使用签名密钥。
四个 repository secrets：`KEYSTORE`（完整 JKS 的单行 Base64）、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

流水线：导出公开证书 → 编译带匹配信任信息的 LKM、ksuinit 和两种 ABI 的 ksud → Gradle 编译管理器 → 使用上游 repack_apk.py 注入守护程序并重签 → 检查最终 APK → 上传。
上游复用的第三方 Actions、DDK 容器和下载依赖仍是构建供应链的一部分；本包不宣称完全可复现构建。

只安装名为 **ChunianSU** 的最终 artifact 中的 APK。
`chuniansu-gradle` 是中间产物，不能代替完成 ksud 注入的最终 APK。
最终 artifact 同时包含 APK 的 SHA-256 和 `BUILD_INFO.json`。
CI 验证安装包名、应用标签、APK 签名证书、文件名版本号以及 arm64/x86_64 的 `libksud.so` 存在性。
这些检查不能证明特定设备的内核或拨号器兼容性，也不能代替完整运行测试。

GitHub 托管构建不是 KernelSU 官方签名或官方背书。

为绕过当前 `Kernel-SU/*` Rust 依赖仓库在公开 GitHub 上不可访问的问题，V2 仅把 4 个构建依赖的下载地址切到 ReSukiSU 的公开镜像，并保持 KernelSU `Cargo.lock` 中的原提交哈希不变：`adb_client`、`java-properties`、`ksu_props`、`rustix`。主项目仍固定基于 `tiann/KernelSU`，不是基于 ReSukiSU。

## 更新发布

更新检查读取：

```text
https://api.github.com/repos/chunian-nb/ChunianSU/releases/latest
```

公开仓库必须有一个不是草稿、不是预发布的 Release，并附上最终 APK。
仅有 Actions artifacts 不会被此更新检查发现。不要把 GitHub 私人 token 写进 APK 来读取私人发布。
保留生成的 APK 文件名。更新器只接受 `ChunianSU_` 前缀、`.apk` 后缀以及上游 `v(.+?)_(\d+)-` 版本字段格式。

应用显示版本由 `manager/gradle.properties` 的 `CHUNIAN_VERSION_NAME=v1.0.0` 指定。
安装版本号仍是 `30000 + git rev-list --count HEAD`。保持完整 Git 历史，每次新发布创建新的提交；仅重新编译同一提交不增加安装版本号。
后续版本复用相同 JKS，修改版本、提交、推送、重新运行，然后发布新的 Release。
建议 Release tag 使用 `chuniansu-v1.0.0` 这样的前缀，避免触发保留的上游通用发布流程。

## 本机验收清单

- 首次安装有图标，标签为 ChunianSU，安装包名正确。
- 在本机匹配内核下 Root 管理功能正常；仅看界面能打开不够。
- 隐藏前在系统拨号器实际测试 `*#*#888#*#*`。
- 取消确认或不勾选声明，图标仍保持可见。
- 隐藏后拨号能打开，关闭开关能恢复图标。
- 重新打开设置、重启手机、使用恢复命令后，开关状态与真实入口状态一致。
- 应用在系统应用管理中仍正常可见且可卸载。
- 第二次使用同一密钥并增加版本号后能覆盖安装；Release 更新路径能工作。

## 上游与许可

本项目基于 KernelSU；保留上游 LICENSE、源码作者和版权声明。分发定制版本时保留相应源码及许可证信息。
新增定制代码文件采用 GPL-3.0-only；上游各部分仍以其原有许可证为准。

源码参考：
- https://github.com/tiann/KernelSU/tree/9b25a0f2c409f237d1495f3be1a3926bbd6a284c
- https://developer.android.com/guide/topics/manifest/activity-alias-element
- https://developer.android.com/reference/android/telephony/TelephonyManager#ACTION_SECRET_CODE
- https://developer.android.com/build/configure-app-module
- https://developer.android.com/studio/publish/app-signing
