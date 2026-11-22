# scripts/ — 手动执行的工具箱

这里只放**人手动跑**的脚本。CI 会自动执行的脚本一律在 `.github/scripts/`，
两边不混放：改动本目录下任何文件都不会影响构建。

按运行位置分两个子目录。

## dev/ — 本地开发机

| 文件 | 用途 | 语言 |
| --- | --- | --- |
| `setup_cargo_config.py` | 从 `ANDROID_NDK_ROOT` 生成 `.cargo/config.toml`，模板见 `.cargo/config.example.toml` | Python 3 |
| `setup-rust-build.ps1` | Windows 下导出 NDK 交叉编译环境变量（`CC_*` / `CXX_*` / `CARGO_TARGET_*_LINKER`），与 `.github/scripts/setup-rust-build.sh` 等价 | PowerShell |
| `generate_material_icons.ps1` | 裁剪 material-icons-extended，生成体积更小的 `manager/app/libs/material-icons-custom.jar` | PowerShell |
| `allowlist.bt` | bpftrace 脚本：打印内核侧 `root_profile` 结构布局，排查 allowlist 字段偏移 | bpftrace |

```bash
# 示例：生成 .cargo/config.toml
python3 scripts/dev/setup_cargo_config.py

# 示例：Windows 上配置 arm64 交叉编译（需先设 ANDROID_NDK_HOME）
powershell -ExecutionPolicy Bypass -File scripts/dev/setup-rust-build.ps1 aarch64-linux-android 21
```

## host/ — runner host / 构建机

| 文件 | 用途 | 运行位置 |
| --- | --- | --- |
| `seed-ci-tools.sh` | 把 `.github/scripts/ci/ci-checkout.sh` 安装到 `/ci/tools/bin/ci-checkout`。漏跑时该 host 上**所有 job 在第一步 Checkout 就失败**（exit 127，2 秒收场） | runner host（Docker named volume 或 bind mount 都支持） |
| `artifact-server.sh` | 用 Docker 把 `/ci/runs` 只读发布到 LAN:18080，供另一台 runner 跨主机取产物 | runner host，**不在 job 内** |
| `seed-cargo-cache.sh` | 预热 `/ci/tools/cargo`：克隆 BuiltZygisk 的 `zygiskd` 并 `cargo fetch`。BuiltZygisk 依赖变化后要重跑，否则 builtzygisk job 的离线构建会缺 crate | runner host，**不在 job 内**（容器需要能访问 crates.io） |
| `prepare-ddk-x64.sh` | 为 8 个 KMI 生成 x86_64 的 DDK kdir，供 DDK 镜像与 LKM 构建使用 | `/opt/ddk` 所在的构建机 |

```bash
# Docker 部署（本仓库就是这种：forgejo-ci 是 named volume）
CI_TOOLS_VOLUME=forgejo-ci bash scripts/host/seed-ci-tools.sh

# runner 的 job 用 host 目录挂载时，直接跑即可
bash scripts/host/seed-ci-tools.sh

# 预热 builtzygisk 的 crate 缓存；/ci 由 Windows 目录挂载时给 CI_ROOT
CI_ROOT='X:/other/forgejo-ci' bash scripts/host/seed-cargo-cache.sh
```

三个种子脚本都是幂等的，可重复执行。`prepare-ddk-x64.sh` 依赖 `/opt/ddk/src`、
`/opt/ddk/clang`、`/opt/ddk/rust` 已就位。

产物传输的完整链路（`/ci` → EasyTier 对端 → CNB → Forgejo Artifacts）见
`.github/CI.md` 的 "Artifact transport" 一节。
