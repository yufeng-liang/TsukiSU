# 运行时换包名（方案 A）设计文档

日期：2026-11-21
状态：已实施（2026-11-22）。T1/T2 已提交，核心与界面全部落盘并通过单测 + 桌面复核；真机安装验证待补（本机无设备）。
前置 spike：已完成并通过（见第 3 节）

## 0. 一页结论

**可行。** 在真实 39MB 的 manager APK 上，把包名从 23 字符改成 30 字符，全程走通：

| 环节 | 结论 |
| --- | --- |
| 改写 AndroidManifest.xml 的包名 | 可行，AXML 字符串池就地替换，aapt2 正常解析 |
| 重打包不重压缩 | 可行，449 ms（PC），对齐零破坏 |
| 重签 v2 | 可行，但**自实现**：apksig 9.x 的部分路径依赖 `java.nio.file`（API 26+），本工程 minSdk 24 用不了 |
| 内核认主 | **需要一次性登记**，不能靠包名 |

**唯一的架构性代价**：改名后的 App 不再匹配内核烧入的包名，必须走仓库已有的「动态管理器」白名单（包名 + 证书哈希 + uid）把新包登记成合法管理器。这条通道在仓库里三件套齐全（内核扩展 + ksud 命令 + 管理器 UI），不是新造的机制。

**必须先修的既有隐患**：源码里有 7 处 `BuildConfig.APPLICATION_ID`，它是**编译期常量**，改名后仍然返回旧包名。不修则 FileProvider 分享、备份、日志上报、主题图片、自我升级校验、ksud 卸载全部走错包名。

---

## 1. 目标与非目标

### 目标
- 在管理器 App 内一键把自己的安装包改成任意合法包名，装出一个「看起来完全不像 KernelSU 管理器」的 App。
- 改名后的 App 仍然是合法的管理器：能取 root、能管模块、内核功能全部可用。
- 全程不需要电脑，不需要重新编译。

### 非目标
- 不改 `namespace = "com.sukisu.ultra"`（源码命名空间 / R 类）。它与安装包名无关，改了要重编译。
- 不动 `manager/randomizer`（那是构建期改源码的方案 B）。
- 不动 `manager-package.properties` 单一真源（运行时换名不写回真源）。
- 不追求「改名后保留原有 App 数据」——Android 的私有目录就是包名，改名等于换 App。

---

## 2. 侦察结论（事实清单）

### 2.1 包名单一真源与四个消费方

真源：`manager-package.properties`
```
KSU_MANAGER_PACKAGE_NAME=com.yufeng.sukisu.ultra
KSU_MANAGER_PACKAGE_NAME_PR=com.yufeng.sukisu.ultra.pr
```

四个消费方（任何「只改一半」的问题都出在这里）：

1. `manager/app/build.gradle.kts:104` 读真源 → `:187` `applicationId`。`:100` 的注释明确写着 `namespace` 是源码命名空间，与安装包名无关。
2. `kernel/manager-package.mk:37` → 内核宏 `KSU_PACKAGE_NAME`，经 `kernel/Kbuild:248` 注入。
3. `userspace/ksud/build.rs:292` → `userspace/ksud/src/defs.rs:77` `DEFAULT_PACKAGE_NAME`。
4. `scripts/check_manager_package.py` CI 一致性校验。

**这四个都在编译期。运行时换包名必须绕开它们，而不是修改它们。**

### 2.2 认主机制与失败模式

`kernel/manager/throne_tracker.c:36` `crown_manager()` 用
`strncmp(np->package, KSU_PACKAGE_NAME, KSU_MAX_PACKAGE_NAME) == 0` 判定。

失败模式很关键：包名不匹配 → 内核永远认不出管理器 → App 能装、能启动、**但所有内核功能是灰的**，且日志和 UI 都不会提示「包名不对」。用户只会觉得「root 没了」。

### 2.3 内核已有的三条认主通道

| 通道 | 位置 | 依据 | 能否用于改名后的包 |
| --- | --- | --- | --- |
| A 包名 | `throne_tracker.c:36` | `KSU_PACKAGE_NAME` | 不能（改名就是要不匹配） |
| B 签名兜底 | `kernel/extension/dynamic_sig_fallback.c`、`kernel/manager/apk_sign.c:328` `is_manager_apk()` | v2 签名第一张证书的**字节长度 + SHA-256** 匹配 `EXPECTED_SIZE/HASH`（槽 1）或 `EXPECTED_SIZE2/HASH2`（槽 2） | 能，但要求重签用的证书**恰好是内核烧入的那张** |
| C 动态管理器 | `kernel/extension/dynamic_manager.c` | 白名单条目：包名 + 证书大小 + 证书哈希 + uid，且 `verified` | 能，且与证书是谁签的无关 |

通道 B 的实际参数（`kernel/Kbuild:240-241`）：
```
KSU_EXPECTED_SIZE ?= 0x35c            # 860 字节
KSU_EXPECTED_HASH ?= 947ae944f3de4ed4c21a7e4f7953ecf351bfa2b36239da37a34111ad29993eef
```

本机 `H:\sukisu\sukisu-keystore\sukisu-custom.jks` 的证书是 **745 字节 / `2a7ec59c...`**，与槽 1 默认值**不一致**。`kernel/Kbuild:291` 的注释说明 fork 自己的证书应当走**槽 2**，由 CI 的 `secrets.KEYSTORE` 填进去。

所以通道 B 是否可用，取决于**用户设备上那套内核编译时槽 2 填的是哪张证书**。这是一个运行期无法自证的量，方案不能依赖它。

### 2.4 阻断点：7 处 `BuildConfig.APPLICATION_ID`

这是本次侦察最重要的发现。`BuildConfig.APPLICATION_ID` 是 `applicationId` 的编译期常量，改名后依旧返回旧包名。

| 文件:行 | 用途 | 不改的后果 |
| --- | --- | --- |
| `ai/host/AiThemeController.kt:371` | `"${BuildConfig.APPLICATION_ID}.fileprovider"` | 主题图片分享崩溃 |
| `ai/host/AiThemeImageStore.kt:109` | 同上 | 同上 |
| `ui/component/material/SendLogBottomSheet.kt:152` | 同上 | 日志上报崩溃 |
| `ui/component/miuix/SendLogDialog.kt:123` | 同上 | 日志上报崩溃 |
| `ui/screen/settings/backup/BackupScreen.kt:75` | 同上 | 备份导出崩溃 |
| `data/repository/CiManagerUpdater.kt:279` | `candidate.packageName != BuildConfig.APPLICATION_ID` | 拒绝安装自己的更新 |
| `ui/util/KsuCli.kt:382` | `uninstall --package-name ${BuildConfig.APPLICATION_ID}` | ksud 去卸载一个不存在的包名 |

对照：仓库里另外三处**已经写对了**，用的是运行时包名，可作为修正范本：
- `data/column/ColumnStepRunnerImpl.kt:187` → `"${ksuApp.packageName}.fileprovider"`
- `ui/util/DownloadDestination.kt:82` → `"${context.packageName}.fileprovider"`
- `ui/util/ExternalCropper.kt:63` → `"${context.packageName}.fileprovider"`

> 这 7 处本身就是既有隐患：将来若走构建期改名（方案 B）、或做多包名分发，同样会炸。修正与本次功能正交，建议独立提交。

### 2.5 ksud 侧写死包名的位置

| 文件:行 | 内容 |
| --- | --- |
| `userspace/ksud/src/late_load.rs:186` | force-stop `package_name` |
| `userspace/ksud/src/late_load.rs:192` | 启动组件 `"{package_name}/com.sukisu.nova.ui.MainActivity"` |
| `userspace/ksud/src/utils.rs:301` | uninstall |
| `userspace/ksud/src/boot_patch.rs:143` | `/data/user_de/{user_id}/{DEFAULT_PACKAGE_NAME}/boot_backup` |
| `userspace/ksud/src/cli.rs:78-79, 116-117, 294, 1485` | CLI 默认值 |

注意 `late_load.rs:192` 的组件名写的是 `com.sukisu.nova.ui.MainActivity`，与当前命名空间 `com.sukisu.ultra` 不一致，需与真实清单核对（可能已失效）。

---

## 3. Spike 结果

产物在 `H:\sukisu\tools\pkgrename-spike\`（一次性验证脚本，不进产品代码）。

### 3.1 AXML 改写（`axml.py`）

做法：定位 `RES_STRING_POOL_TYPE(0x0001)` 块，解码全部字符串，替换，按**原顺序原数量**重新编码，回填块头 `size` 与文件头 `size`。

三个必须守住的不变量：
1. **字符串数量与顺序不可变**——XML 节点按索引引用字符串。
2. **编码风格与原池一致**——`UTF8_FLAG=0x100` 决定 UTF-8/UTF-16；UTF-8 串记录的是「UTF-16 码元数 + 字节数」两个变长长度。
3. **填充约定与原池一致**——每条串补齐到 4 字节还是 0 字节，各构建工具不同。

第 3 点用**逐字节自检**兜底：解析时用 `pad_unit = 4 → 0 → 2` 依次重建，与原始块比对，全部不符就拒绝改写。这保证「能改的一定是能被系统原样读回的」。

### 3.2 zip 字节手术（`rename_fast.py`）——性能关键

**不要重压缩整个 APK。** 完整解压 + 重压缩 39MB 在 PC 上要 2500 ms，手机上会放大一个量级。

正确形态：
1. 原文件 `[0, 签名块起点)` 这段字节**原样拷贝**——所有条目保持原始偏移，压缩流不动，`.so` / `resources.arsc` 的对齐也不会被破坏（`zipalign -c` 实测通过）；
2. 改写后的 `AndroidManifest.xml` 作为**新条目追加在末尾**，旧清单字节变成约 23KB 的死区；
3. 重写中央目录，只把 `AndroidManifest.xml` 的偏移 / 长度 / CRC 指到新条目；
4. 丢弃原 APK Signing Block（`APK Sig Block 42`，4096 字节），重签阶段重建。

代价与 APK 大小成正比、与压缩率无关：`449 / 458 / 481 ms`（PC，三次）。

### 3.3 重签

`apksigner` 用 `sukisu-custom.jks` 签 v2 + v3，`apksigner verify` 通过，证书 `2a7ec59c...` 与 `cert-check.der` 一致。

**App 内实现可直接用 `com.android.tools.build:apksig`**：本机 Gradle 缓存已有 `apksig:9.4.1`，纯 Java，无二进制依赖。

必须注意：重打包时**丢弃原 v1 签名文件**（`META-INF/*.SF|RSA|DSA|EC`、`MANIFEST.MF`）。它们是用旧密钥签的，留着会与新证书冲突。manager 的 `minSdk 24` 用 v2 足够，v1 可以关。

### 3.4 实测数据

| 指标 | 值 |
| --- | --- |
| 输入 | `TsukiSU_mod_41565-debug.apk`，39,615,255 字节 |
| 输出 | 39,624,336 字节（+9 KB） |
| 清单替换 | 5 处 + 独立字符串 1 处，共 6 处引用 |
| 清单长度 | 23544 → 23616 字节 |
| 重打包耗时 | 449–481 ms（PC，零重压缩） |
| 对照：全量重压缩 | 2481–2718 ms |
| zip 完整性 | `unzip -t` 无错误 |
| 对齐 | `zipalign -c -p -v 4` 通过 |
| 包名 | `com.test.hidden.sukisu.manager` |
| 签名 | v2 ✓ / v3 ✓，证书 `2a7ec59c...` |

### 3.5 尚未验证

| 项 | 原因 | 怎么补 |
| --- | --- | --- |
| 真机安装 | 无设备连接（`adb devices` 空） | 插上手机后 `adb install -r out/fast_signed.apk` |
| 模拟器安装 | 本机 AVD `Pixel_9_Pro_XL` 指向的系统镜像不存在，起不来 | 补 `system-images` 或换真机 |
| 设备内核槽 2 的证书 | 需要读设备上的内核构建参数 | 真机上 `dmesg \| grep "signature hash"` |
| 手机上的实际耗时 | 无设备 | 见上 |

APK 含 `arm64-v8a`，真机可直接装。

---

## 4. 方案设计

### 4.1 总体流程

```
[用户] 点「修改包名」→ 输入新包名（预填随机名）
   ↓
[1] 校验新包名（合法性 + 与当前不同 + 未占用）
   ↓
[2] 读取自身 APK（applicationInfo.sourceDir）
   ↓
[3] 生成/载入持久化密钥 → 证书 DER + 私钥
   ↓
[4] AXML 改写 → zip 字节手术 → cacheDir/new.apk
   ↓
[5] apksig 重签（v2 + v3）
   ↓
[6] 递交系统安装器（FileProvider + REQUEST_INSTALL_PACKAGES）
   ↓
[7] 安装成功后查新包 uid
   ↓
[8] dynamic_manager ADD(新包名, 证书大小, 证书SHA256, uid) + SET_ENABLED(true)
   ↓
[9] 内核 track_throne(false) 重扫 → 验签匹配 → 新包被认主
   ↓
[10] 提示用户：新管理器已就绪，可以卸载旧包
```

**步骤 6–9 之间是关键窗口**：新包已装但还没被认主。此时若用户直接卸载旧包，会短暂失去管理器。UI 必须明确提示「不要在中途卸载当前管理器」，并在步骤 8 成功后给出确认。

### 4.2 认主策略：走通道 C（动态管理器）

**推荐 C，不推荐 B。**

B 的问题不是技术问题，是权限面问题：通道 B 的语义是「任何用这张证书签过的 APK 都是管理器，不看包名」。要让 App 在设备上重签，私钥就必须在设备上（内置进 APK 或首次生成后存私有目录）。如果内置官方私钥，那任何拿到这个 APK 的人都能提取私钥、签出一个**会被内核自动加冕**的管理器 APK——这是真实的提权面，不能为了省一次登记打开它。

C 的优点：
- 授权粒度是「包名 + 证书 + uid」三元组，改名后依然精确，不会顺带授权别的包；
- 是仓库现成能力：内核 `kernel/extension/dynamic_manager.c`、ksud `userspace/ksud/src/dynamic_manager.rs` + `cli.rs:1210-1247`、管理器 UI `ui/screen/dynamicmanagers/` 三件套齐全；
- 任何 set 操作成功后内核自动调 `track_throne(false)`，不需要重启。

**关键细节（容易漏）**：
1. `kernel/extension/dynamic_manager.c:46` `dynamic_manager_enabled = false` —— **默认关闭**。流程里必须显式 `KSU_MANAGER_SET_ENABLED(1)`，否则登记了也不生效。
2. ADD 之后 `dynamic_manager_verified[i] = false`（`:227`），要等内核扫到该包、验签匹配后才置真（`:365`）。所以**先安装再 ADD** 才能立刻生效。
3. `cmd->uid` 由调用方提供，只校验 `!= KSU_INVALID_APPID`（`:200`）。旧管理器可以在新包装好后查到它的 uid 再登记——这正好解决 bootstrap。
4. 需要在真机上确认「谁能调 `KSU_MANAGER_ADD`」的权限门。若非当前管理器不可调，则必须由旧管理器完成登记（当前设计正是这样）。

**通道 B 不再参与**（2026-11-22 变更）：签名兜底的语义是「任何用这张证书签过的 APK 都是管理器」，要让它在设备上生效就得把对应私钥放进设备、甚至随包分发；而我们真正需要的只是「新包名的 APK 能装上 + 内核按 uid 授权」，这两件事都不依赖任何特定证书。详见 §4.3。

### 4.3 密钥策略：每台设备现场生成（2026-11-22 定稿）

**私钥不随 APK 分发。** 首次换名时在设备上生成一对 RSA 2048 密钥，并现场签一张自签名 X.509 证书：

- 生成：`LocalSigningKeyProvider.get(context)`（首次调用，必须在 IO 线程）；
- 证书：`X509SelfSigned.build(keyPair, cn)`，手写 DER（Android 平台只带 bouncycastle 的加密原语，没有 `bcpkix` 那套证书构造器，为一个自签证书引依赖不划算）；
- 存放：`filesDir/pkgrename-keys/signing-key.bin`，AES-GCM 加密，包装密钥是 Android Keystore 里**不可导出**的 AES-256（别名 `pkgrename_key_wrap`）；Keystore 不可用时退化为 App 私有目录明文（文件头版本号 0 区分）。

为什么自生成就够：**新包名是一次全新安装**，Android 只要求签名自洽，不要求与任何既有证书相同；内核那边走动态管理器白名单，按 uid 授权（`dynamic_manager.c:134-141`），条目里的证书哈希只做记录、不参与判定。所以官方证书带来的唯一「好处」是被签名兜底加冕，而那条路的代价是把管理器印章公开，收益与风险不成比例。

代价：改名后的包无法用原密钥升级（升级必须同签名），密钥文件随 App 数据一起丢；这一点在 §5 风险里列出。

与内核参数的对应关系：登记动态管理器时填的 `hash` 就是 `certSha256Hex`、`certSize` 就是证书 DER 长度；签名算法固定 RSA PKCS#1 v1.5 + SHA-256（算法 id `0x0103`），因为内置密钥是 RSA 2048。

### 4.4 包名合法性校验

与内核完全对齐（`dynamic_manager.c` `package_valid()`）：
- 首字符 `isalpha`
- 其余只允许 `alnum` / `_` / `.`
- 长度 1..`KSU_MANAGER_PACKAGE_LENGTH`(128)

另外 App 侧还要校验：
- 至少两段（含 `.`），且不是 `android`、`com.android` 等保留前缀；
- 与当前包名不同；
- 未被其他 App 占用（`PackageManager.getPackageInfo` 抛 `NameNotFoundException`）。

长度还要满足内核 `KSU_MAX_PACKAGE_NAME`(256) 与 `_Static_assert(sizeof(KSU_PACKAGE_NAME) < KSU_MAX_PACKAGE_NAME)`——但那是编译期的，运行时不受限，只需满足 128。

### 4.5 源码修正清单

**A. 7 处 `BuildConfig.APPLICATION_ID` → 运行时包名**

| 文件:行 | 改成 |
| --- | --- |
| `ai/host/AiThemeController.kt:371` | `"${context.packageName}.fileprovider"` |
| `ai/host/AiThemeImageStore.kt:109` | 同上 |
| `ui/component/material/SendLogBottomSheet.kt:152` | `"${context.packageName}.fileprovider"` |
| `ui/component/miuix/SendLogDialog.kt:123` | 同上 |
| `ui/screen/settings/backup/BackupScreen.kt:75` | `"${context.packageName}.fileprovider"` |
| `data/repository/CiManagerUpdater.kt:279` | `candidate.packageName != context.packageName` |
| `ui/util/KsuCli.kt:382` | `uninstall --package-name ${ksuApp.packageName}` |

**B. ksud 运行时包名真源**

新增一个运行时真源文件 `/data/adb/ksu/.manager_package`：
- 管理器每次启动时把自己的 `packageName` 写进去（`ksuApp.packageName`）；
- ksud 读取顺序：该文件 → 回退 `DEFAULT_PACKAGE_NAME`；
- 上面 2.5 节列出的 5 处改为读这个值。

这样 ksud 的写死点一次性全部解决，且升级/降级路径自然兼容。

**C. 不要动的地方**
- `manager-package.properties`：运行时换名不回写真源（真源是构建契约）。
- `namespace`：源码命名空间，改了要重编译。
- `kernel/*`：本次不需要任何内核改动——通道 C 是既有机制。

### 4.6 UI

实际实现（与本节原计划的差异已按实现订正）：
- 入口：设置页内独立一项「更换包名」，在 `SettingsSection.PACKAGE_RENAME` 锚点下（Material 用 `SettingsAnchor`，Miuix 用 `bringIntoViewRequester`）。
- **没有自由输入框**：只显示当前包名与随机目标包名，另有「换一个」重掷按钮。
- 前置提示两条：改名 = 全新安装、不继承数据（`pkg_rename_data_warning`）；旧包还装着时内核仍可能按包名把它认成管理器（`pkg_rename_warning`）。
- 执行中显示进度条 + 阶段文案（构建 / 安装 / 登记 / 完成），并拦截返回与侧滑。
- 完成后显示：「打开新管理器」、「查看已登记的管理器」（跳 `Route.DynamicManagers`）、「卸载旧APK」；「卸载旧APK」只在登记回读全部通过后出现。
- 「已登记的管理器」入口只在结果卡里，**不是**底部常驻；`ksud manager list` 也没有 verified 列。

字符串全部走 `stringResource`，按项目已有语言补齐。

### 4.7 失败与回滚

| 失败点 | 状态 | 处理 |
| --- | --- | --- |
| AXML 自检失败 | 未改动任何东西 | 报错退出，提示「当前 APK 的清单格式不受支持」 |
| 重签失败 | 只有 cacheDir 里的临时 APK | 清理临时文件 |
| 用户取消安装 | 同上 | 清理临时文件，白名单未动 |
| 安装成功但登记失败 | 新包在，但未认主 | **最高优先级告警**：明确告知「新 App 暂时不能管理内核，请回到当前管理器重试登记」，并提供一键重试 |
| ~~登记成功但 verified 未置真~~ | —— | **该行已作废**：内核里 `ksu_dynamic_manager_verify_apk()` 全仓无调用点，`verified` 恒为 false，证书哈希只做记录；授权只看 uid（见 §8.5） |
| 想撤回 | 白名单里有条目 | 提供「撤销登记」= `KSU_MANAGER_REMOVE(新包名)` |

旧 APK 通过 `sourceDir` 复制一份到私有目录保留（`PackageRenameCoordinator.backupOriginalApk()`）。**注意：目前只有备份，没有「恢复原名」的入口**；且该副本位于旧包私有目录，卸载旧包即随之删除。UI 文案不得承诺「可随时恢复原名」。

---

## 5. 风险

1. ~~签名兜底被滥用~~（已消除）：2026-11-22 起不再内置官方私钥，改为每台设备现场生成密钥，通道 B 不参与（§4.3）。**新增风险**：本机密钥文件随 App 数据丢失后，改名后的包无法再用同一签名升级，只能卸载重装；Keystore 不可用时密钥退化为私有目录明文，只有 root 可读。
2. **bootstrap 窗口**：步骤 6–9 之间新包未认主。UI 必须拦住「此时卸载旧包」。
3. **数据不迁移**：`/data/data/<包名>` 换名即换目录。模块数据在 `/data/adb/ksu/` 不受影响，但 App 设置、白名单本地缓存会丢。需要引导用备份功能导出/导入。
4. **白名单条目累积**：反复改名会占满 `KSU_MAX_DYNAMIC_MANAGERS`，需要在 UI 里可见可删。
5. **各厂商 ROM 的安装器行为**：部分 ROM 会对「同签名不同包名」的 APK 做额外提示。属于体验问题，不影响正确性。

---

## 6. 工作量拆分

| 任务 | 说明 |
| --- | --- |
| T1 修 7 处 `BuildConfig.APPLICATION_ID` | 独立，可先合入 |
| T2 ksud 运行时包名真源 | Rust + 管理器写文件 |
| T3 AXML 改写器（Kotlin 移植） | 从 `axml.py` 移植，含逐字节自检 |
| T4 zip 字节手术（Kotlin） | 核心，含签名块识别与中央目录重写 |
| T5 签名（自实现 v2） | 不引入 apksig：其部分路径需 API 26+；只实现内核会读的 v2 块 |
| T6 密钥生成与持久化 | |
| T7 认主登记流程 | 复用现有 `AiExtensionsController.addDynamicManager` 通路 |
| T8 UI + 字符串 | 两套主题（Material / Miuix） |
| T9 真机验证 | 需设备 |

---

## 7. 已决策（2026-11-21 确认）

1. **认主通道：只走 C（动态管理器白名单）。** 2026-11-21 曾定为 C+B 并接受「私钥随包分发」的代价；2026-11-22 用户明确要求「装了这个 App 的用户都能用一键换包名」，随包私钥在全量分发下等于公开管理器签名能力，因此改为**每台设备现场生成密钥**（§4.3），B 通道不参与、内置密钥资产已删除。
   - 主通道 C：动态管理器登记（包名 + 证书哈希 + uid），内核按 uid 授权，改名后即可用。
   - 用户不需要做任何额外操作：流程自动装新包、自动登记、自动打开总开关（原本关着时）。
2. **T1（7 处 `BuildConfig.APPLICATION_ID`）单独提交。** 与主功能正交，先合入。
3. **包名随机生成。** 形如 `com.<8位小写随机>.<8位小写随机>`，系统自动生成，不提供自由输入框（避免被用来伪装成他人的包名）。
4. **改名后提示卸载旧包。** 新包登记回读通过后，在结果卡显示「卸载旧APK」。旧 APK 副本保留在私有目录，但**恢复原名的入口尚未实现**，且副本随旧包卸载而消失。

## 8. 实现备注（2026-11-22）

### 8.1 落地文件

| 位置 | 内容 |
| --- | --- |
| `core/pkgrename/AxmlEditor.kt` | AXML 字符串池改写（重建后逐字节自检，不通过就拒绝改写） |
| `core/pkgrename/ApkRepackager.kt` | 零重压缩 zip 手术：原文件条目区原样拷贝，新清单追加在末尾，只改中央目录 |
| `core/pkgrename/ApkV2Signer.kt` | 自实现 v2 签名（不依赖 apksig，避开 `java.nio.file` 的 API 26 门槛） |
| `core/pkgrename/LocalSigningKey.kt` | 首次换名现场生成 RSA 2048，存 `filesDir/pkgrename-keys/signing-key.bin`（AES-GCM + Keystore 不可导出的包装密钥） |
| `core/pkgrename/X509SelfSigned.kt` | 手写 DER 的自签名 X.509 证书（平台没有 bcpkix 那套构造器） |
| `core/pkgrename/ManagerKeyProvider.kt` | 密钥门面：`get(context)` 委派本机密钥，`parse(pk8, certDer)` 留给单测 |
| `core/pkgrename/PackageNameUtils.kt` | 包名校验/随机生成；`ManifestPackageRewriter` 前缀替换与校验 |
| `core/pkgrename/PackageRenameEngine.kt` | 串起「读清单 → 改包名 → 重打包 → 重签」，产物落 `filesDir/pkgrename/` |
| `core/pkgrename/PackageRenameInstaller.kt` | PackageInstaller 会话提交 + 结果回调桥（`PkgRenameInstallReceiver` 也在此文件） |
| `core/pkgrename/PackageRenameCoordinator.kt` | 阶段编排：备份原 APK → 构建 → 安装 → 登记动态管理器（重试 5 次） |
| `ui/screen/pkgrename/PackageRename{Screen,Material,Miuix}.kt` | 设置页入口 + Material/Miuix 双套界面 |
| `ui/screen/pkgrename/PackageRenameController.kt` | 应用级状态单例（`SupervisorJob + Main.immediate`），页面退出不中断流程；`job?.isActive` 同步防重入 |
| `core/pkgrename/RenameJournal.kt` + `PackageRenameRecovery.kt` | 待登记记录（fsync 落盘）与启动续登记 |
| `res/values` + `values-zh-rCN/zh-rHK/zh-rTW` | 26 个 `pkg_rename_*` 键 × 4 语言 |

### 8.2 v2 签名器：三个必须做对的细节

1. **分块不跨段**。内容是三段各自独立的 DataSource：条目区、中央目录、结尾记录。每段自己按 1MB 切块，块摘要按段顺序拼起来再求总摘要。把三段当一条连续流切块会得到不同结果（实测 `de4dda66…` vs 正确值 `c0088f55…`）。
2. **参与摘要的结尾记录用「插块之前」的原样偏移**。apksig 就是这么算的（证据：拿 apksig 的 `computeOneMbChunkContentDigests` 当 oracle，喂 [条目区][中央目录][原样 EOCD] 才复现出 APK 里存的摘要）。写进输出文件的结尾记录才是改过偏移的新值。
3. **signedData 是 4 个长度前缀元素**：digests、certificates、additionalAttributes、空数组。末尾那个空 lp（4 个零字节）省掉会让 signedData 比 apksig 短 4 字节。另外签名块必须补「填充对」（id `0x42726577`）把整块补到 4096 整数倍，否则中央目录偏移错位、zipalign 报废。

块摘要格式：`SHA-256(0xa5 || uint32LE(块字节数) || 块内容)`；总摘要：`SHA-256(0x5a || uint32LE(块数) || 各块摘要)`。摘要记录的算法 id 用**签名算法** id `0x0103`（RSA PKCS#1 v1.5 + SHA-256），不是摘要算法 id `1`。

### 8.3 验证结果（桌面复核单测产物）

单测每跑一次都会用**新的随机包名 + 新的现场生成密钥**，复核时取最新时间戳那个文件。以 `signed-com.jebrntee.zyejqgsg.apk`（40,713,414 字节，输入为 `TsukiSU_mod_41567-debug.apk`）为例：

- `apksigner verify --verbose --print-certs`：`Verifies`，v2 = true，v1/v3/v3.1/v4 = false；证书 `CN=SukiSU Test`（App 里是 `CN=SukiSU Manager Local XXXXXXXX`）、SHA-256 `91415ec7…468c`、RSA 2048 —— 每次都不一样，正好证明签名来自本机生成而非固定密钥
- `aapt2 dump badging`：`package: name='com.jebrntee.zyejqgsg'`，versionCode 41567，minSdk 24 / targetSdk 37 保持不变
- `zipalign -c -p -v 4`：`Verification successful`（40MB 资源表条目仍对齐）
- 单元测试 `PackageRenamePipelineTest`：`BUILD SUCCESSFUL`（第三个用例受 `-PpkgRenameApk` 门控，不传则 `assumeTrue` 跳过）

### 8.4 已知残留与未验证项

- 6 个 dex 里各有 1 处旧包名字面量（`com.yufeng.sukisu.ultra`）。它们是编译期常量池残留，运行时不参与任何包名判定（7 处 `BuildConfig.APPLICATION_ID` 已改成运行时包名），也不影响 v2 签名。dex 内的字符串运行时无法改写，接受。
- 真机安装未验证：本机 `adb devices` 为空，模拟器 system-image 缺失（`H:/android/Sdk/system-images` 下没有 `system.img`）。安装链路（PackageInstaller 会话、未知来源权限、认主后提示卸载）只有代码级正确性，需要真机跑一遍。
- 旧 v1 签名残留不需要额外处理：`ApkRepackager` 在重建中央目录时就按 `V1_LEFTOVERS` 正则（`META-INF/MANIFEST.MF` 与 `META-INF/*.SF|*.RSA|*.DSA|*.EC`）跳过这些条目，并把 EOCD 的条目数改成剩余条数。产物里查不到这些条目，v1 因而完全不存在。

### 8.5 团队审查与修复（2026-11-22）

四路只读审查（核心二进制 / 流程与内核契约 / 界面与文案 / UI 改动复核）的结论与处置：

| 编号 | 问题 | 处置 |
| --- | --- | --- |
| P0-1 | 内核只按 **appid** 授权（`ksu_dynamic_manager_uid_registered`），而登记回读只看「包名 + 证书」，两者不等价 | 回读追加 `entry.uid != 0 && entry.uid != 旧包 appid` 断言；仍无法自证新包进程被授权（跨进程 uid 不可查），已在 §8.6 记为本轮未解 |
| P0-2 | `ksud manager add` 是「ioctl 写内核内存 → persist 写文件」两步，persist 失败时内核其实已生效，当成失败会诱导用户卸载旧包 | 失败后先 `isRegisteredInKernel()` 回读；条目在就再 add 一次补 persist，仍失败则明确报「内核已接受、状态文件未写入、先别卸载旧包」 |
| P0-3 | 「已装未登记」状态也渲染「卸载旧包」按钮 | `RegistrationFailed` 分支不再设 `previousPackage`；两套皮肤都要求 `unregisteredPackage == null` 才显示卸载 |
| P0-4 | 内置官方私钥是明文 asset，而内核验签不看包名，分发即公开「管理器签名能力」 | **已解决（2026-11-22，方案 D）**：不再分发任何私钥，改为每台设备现场生成 RSA 2048 + 自签证书（§4.3）。新包名是全新安装，签名只需自洽；内核授权走动态管理器按 uid，证书哈希只记录不判定。内置 `assets/pkgrename/` 两个文件已删除 |
| P1-1 | 打开动态管理器总开关会连带激活表内所有历史条目 | 未修。开关语义在内核侧，App 侧最多做「打开前确认」，见 §8.6 |
| P1-2 | 启动续登记（`RenameJournal`）静默失败、成功判据比主流程弱 | 已部分修：进入换包名页时 `PackageRenameController.refreshJournal()` 会把遗留记录摆成「已装未登记」卡片，用户可自行重试/撤销。仍缺：从不打开该页的用户看不到任何提示 |
| P1-3 | journal 写盘无 fsync，掉电会留下空文件 | 已修：`FileOutputStream.store` + `fd.sync()` |
| P1-4 | 安装回调里比对「回调包名 == 目标包名」是自证自答 | 已删，改注释说明身份靠 sessionId 路由 |
| 新 P0 | `NavigationBackHandler` 的 `isBackEnabled` 用反：空闲时吞返回、busy 时放行 | 已修：`isBackEnabled = state.busy`，顶栏返回也加 `if (!state.busy)` 守卫 |
| 新 P1 | 撤销 / 重试成功 / 重新开始都没清 journal，下次启动会被恢复流程重新登记 | 已修：三条路径都调 `RenameJournal.clear` |
| 新 P1 | 登记失败时`entries.isEmpty()` 才回滚总开关，别人有条目时开关被留在开启状态 | 已修：只要 `enabledByUs` 就恢复为关闭（即用户原状态） |
| P2 | Miuix「卸载旧APK」无危险色（Material 有） | 已修：改用 `TextButton(colors = ButtonDefaults.textButtonColors(textColor = colorScheme.error))`（`ArrowPreference` 的 `titleColor` 要的是 `BasicComponentColors`，不能直接给 `Color`） |
| P2 | 英文按钮 `Registered managers` 是名词短语 | 已改为 `View registered managers` |

**第二轮（rev-core，二进制核心）**：

| 问题 | 处置 |
| --- | --- |
| `AxmlEditor.listStrings` 逐块前进没有长度守卫，损坏清单会死循环 | 已修：`off + 8 > size` 或 `chunkSize <= 0` 或越界即抛带偏移的异常 |
| `ApkV2Signer.readLayout` 倒扫 `PK\x05\x06` 不校验注释长度，ZIP 注释里的假签名会被当真 | 已修：要求 `i + 22 + commentLen == scanLen`，并校验 `cdOffset + cdSize + 22 <= fileLen` |
| `readManifest` 只按 LFH 的 compSize 解压，不校验 CRC / 解压长度 / 名字 | 已修：LFH 名必须等于 `AndroidManifest.xml`、LFH 与 CD 的压缩长度必须一致、解压后用 CD 的 CRC32 与 uncompSize 交叉验证 |
| `inflateRaw` 截断时静默 break | 已修：结束必须 `Inflater.finished()`，否则抛「清单 deflate 流不完整」 |
| `PackageNameUtils.isValid` 用 Kotlin 的字符长度，内核用 `strnlen` 字节长度 | 已修：补 UTF-8 字节长度上限判断 |

未修的 P2（记在此处便于后续排期）：split APK 未支持（`splitSourceDirs` 非空时重打包只含 base）；临时文件用固定名且无进程内锁；签名后无自校验（不重新验签）；Zip64 诊断顺序与死代码；带 style 的字符串池直接拒绝；`verifyReplaced` 与 `isRewritable` 的白名单口径不完全一致；目标包名已安装未预检；CD/清单整段分配的尺寸上限。

### 8.6 本轮未解决、需要决策的事项

1. ~~P0-4 私钥分发边界~~（**已解决**，2026-11-22）：最终采用「每台设备现场生成密钥」，三条折中方案（A 构建期注入 / B 内核三因子 / C UI 明示）全部不再需要。附带收益是 B 通道彻底退出，内核侧无需任何改动。
2. **P1-1 总开关连带激活历史条目**：需要「打开前确认 + 列出将生效条目」的交互设计，属于新 UI 工作。
3. **P1-2 续登记可观测性（部分解决）**：页面内已有「已装未登记」卡片兜底；仍缺系统通知，覆盖「用户压根不打开换包名页」的情况。
4. **P0-1 的自证缺口**：跨进程无法查新包 uid 是否被内核认主；只能在新包进程内自证（`Process.myUid() % 100000` 与 `manager list` 比对），需要给新包加一次性自检逻辑。

---

## 附：spike 产物

```
H:\sukisu\tools\pkgrename-spike\
  axml.py            AXML 字符串池解析/重建（含逐字节自检）
  rename_pkg.py      全量重压缩版（存档对照，慢 5 倍）
  rename_fast.py     零重压缩版（推荐形态，449ms）
  dex_probe.py       dex/arsc 里包名残留的上下文提取
  spike_*.sh         验证脚本
  out/               验证产物（fast_signed.apk 可直接 adb install）
```
