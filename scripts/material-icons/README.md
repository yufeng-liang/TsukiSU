# material-icons-custom.jar

`manager/app/libs/material-icons-custom.jar` 是运行期实际打包的 Material 图标子集。

## 为什么需要它

`manager/ai` 把 `material-icons-extended` 声明为 `compileOnly`（见 `manager/ai/build.gradle.kts`），
编译时可以用它的 API，但不会把 30MB 的整包打进 APK。运行期由 `manager/app` 依赖这个
`material-icons-custom.jar` 提供真正用到的图标类，避免 `NoClassDefFoundError`。

## 新增一个图标

先从 Gradle 缓存找到 `material-icons-extended-release.aar`（版本与 `libs.versions.toml` 一致），
然后把需要的类追加进 jar（不要解压重建，保持已有条目不变）：

```bash
AAR=~/.gradle/caches/modules-2/files-2.1/androidx.compose.material/material-icons-extended-android/<version>/<hash>/material-icons-extended-release.aar
unzip -p "$AAR" classes.jar > /tmp/icons-extended.jar
mkdir -p /tmp/icon && cd /tmp/icon
unzip -o /tmp/icons-extended.jar "androidx/compose/material/icons/rounded/CloudDownloadKt.class"
cd - && jar uf manager/app/libs/material-icons-custom.jar -C /tmp/icon \
  androidx/compose/material/icons/rounded/CloudDownloadKt.class
```

也可以直接用 `scripts/material-icons/add-icon.sh rounded CloudDownload`。

## 已确认加入

- `rounded/CloudDownloadKt`（拉取模型按钮，2026-11）

注意：新增图标后要重新构建 `:app:assembleDebug`；只改 AI 模块的 `compileOnly` 依赖不会自动带上运行期类。
