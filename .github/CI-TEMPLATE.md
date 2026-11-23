# 自托管 Forgejo Runner 工作流模板

适用于本实例（`git.zako.shirkneko.top:20004`）上的仓库。核心约束只有一个：
job 容器访问不到 github.com，所以**不要用任何 marketplace action**，改用镜像里预装
的工具 + `/ci` 卷里预置的 action。

镜像 `sukisu-ci:<tag>` 已含：JDK 21、Android SDK 37 / build-tools 37、
NDK 27.2 与 29、Gradle、Rust nightly、cargo-ndk、cmake、python3、curl、jq、git。

`/ci` 卷已预置（每台 runner 各一份，需一次性执行，命令见 `E:\other\forgejo-ci\README.md` 与 `X:\other\forgejo-ci\README.md`）：

| 路径 | 内容 |
|---|---|
| `/ci/tools/actions/upload-artifact` | actions/upload-artifact v3（本地副本） |
| `/ci/tools/actions/download-artifact` | actions/download-artifact v3（本地副本） |
| `/ci/tools/node` | Node（ddk 这类没有 node 的镜像用） |
| `/ci/tools/arm-gnu-toolchain-*` | kpimg 用的 aarch64-none-elf 工具链 |
| `/ci/tools/gradle`、`/ci/tools/cargo` | 构建缓存 |
| `/ci/repo-cache/<owner>/<repo>.git` | 裸镜像，checkout 用 |

## 完整示例

```yaml
name: CI

on:
  workflow_dispatch:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    env:
      GRADLE_USER_HOME: /ci/tools/gradle
      CARGO_HOME: /ci/tools/cargo
      RUSTUP_HOME: /usr/local/rustup
      RUSTUP_TOOLCHAIN: nightly-2026-09-23
      ANDROID_SDK_ROOT: /opt/android-sdk
      ANDROID_HOME: /opt/android-sdk
      ANDROID_NDK_HOME: /opt/android-sdk/ndk/27.2.12479018
      MAKEFLAGS: "-j6"
      CARGO_BUILD_JOBS: "6"

    steps:
      # ---------------------------------------------------------------- 1. 代码
      # 从 /ci 的裸镜像取，不走网络。令牌只在 clone 那一行出现，必须关掉 xtrace。
      - name: Checkout
        env:
          TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          set -eux
          remote="https://git.zako.shirkneko.top:20004"
          mirror="/ci/repo-cache/${GITHUB_REPOSITORY}.git"
          mkdir -p "$(dirname "$mirror")"
          if [ ! -d "$mirror" ]; then
            set +x
            git -c "http.${remote}/.extraHeader=Authorization: token ${TOKEN}" \
              clone -q --mirror "${remote}/${GITHUB_REPOSITORY}.git" "$mirror"
            set -x
          else
            git --git-dir="$mirror" fetch -q --prune origin \
              "+refs/heads/*:refs/heads/*" "+refs/tags/*:refs/tags/*"
          fi
          git clone -q --shared --no-checkout "$mirror" .
          git checkout -q "${GITHUB_SHA}"

      # ------------------------------------------------- 2. node + 本地 action
      # 每个用到 upload/download-artifact 的 job 都要有这一步：
      #  - act 用 job 容器 PATH 里的 node 跑 JS action
      #  - 把预置的 action 拷进 workspace，uses 才能用相对路径
      - name: Provide node and the action sources
        run: |
          set -eux
          found=
          for dir in /opt/acttoolcache/node/*/x64/bin /ci/tools/node/bin; do
            if [ -x "$dir/node" ]; then
              echo "$dir" >> "$GITHUB_PATH"
              found=1
              break
            fi
          done
          [ -n "$found" ] || { echo "node not found (tool cache and /ci/tools/node)" >&2; exit 1; }
          mkdir -p "$GITHUB_WORKSPACE/.ci-actions"
          for action in upload-artifact download-artifact; do
            rm -rf "$GITHUB_WORKSPACE/.ci-actions/$action"
            cp -a "/ci/tools/actions/$action" "$GITHUB_WORKSPACE/.ci-actions/$action"
          done

      - name: Check toolchain
        run: |
          set -eux
          java -version
          gradle --version | head -3
          test -d "$ANDROID_NDK_HOME"

      # ---------------------------------------------------------------- 3. 构建
      - name: Build
        run: |
          set -eux
          gradle --build-cache assembleRelease \
            -Porg.gradle.parallel=true \
            -Dorg.gradle.jvmargs=-Xmx2048m

      # ------------------------------------------------------ 4. 上传 artifact
      # 步骤一定要写 name，否则 UI 里显示成 ./.ci-actions/upload-artifact。
      # 传单个文件时 path 指向文件，artifact 里就是文件名（act 取父目录为根）。
      # 传目录时 path 指向目录，artifact 里是该目录的内容。
      - name: Upload the release APK
        uses: ./.ci-actions/upload-artifact
        with:
          name: app-release
          path: app/build/outputs/apk/release/*.apk
          if-no-files-found: error
```

## 跨 job 传产物

不要用 `/ci` 在 job 之间传文件：两台 runner 各有自己的 `/ci`，任务可能被分到不同机器。
产物统一交给 `common.sh`：

```yaml
    env:
      # 产物经 CNB 中转；没有 token 时只留在 /ci 并告警
      CNB_TOKEN: ${{ secrets.CNB_TOKEN }}
      # 制品仓只放 `ci-<run_id>`，与 release 仓分开，且只保留最近 5 次 run
      CNB_ARTIFACT_REPO: ${{ vars.CNB_ARTIFACT_REPO || 'shirkneko/sukisu-artifacts' }}

    steps:
      - name: Upload the build artifact
        run: |
          set -eux
          . .github/scripts/ci/common.sh
          ci_artifact_put app-release build/app-release.apk

      # 消费端（单名 / 全部）
      - name: Download the build artifacts
        run: |
          set -eux
          . .github/scripts/ci/common.sh
          ci_download_artifact app-release artifacts/app-release
          # 或者：ci_download_all_artifacts artifacts
```

查找顺序：`/ci` 卷 → EasyTier peer（18080）→ CNB（制品仓里 `ci-<run_id>` release
上的 `<name>.tar.gz`）→ Forgejo artifact API。CNB 是跨机器、跨 run 都能用的那一层，
大文件也走它，不要改回让每个 job 自己往 Forgejo 传。

制品仓（`CNB_ARTIFACT_REPO`）与 release 仓（`CNB_REPO`）分开：每次 run 一个
release 本身没错，但同一个仓库既放 CI 产物又放正式 release，数量会无限增长，旧仓库
就是这样被判成 release 滥用的。`ci_artifact_cnb_prune` 在每个 run 首次归档成功后
删掉第 6 个及更早的 `ci-<run_id>`（release 和 tag 一起删），上限由
`CI_ARTIFACT_KEEP_RUNS` 控制（默认 5）。所以只保证最近 5 次 run 能跨机器/跨 run
取回，更早的只剩产出它的那台机器的 `/ci`。

产物就留在 CNB 上，不再回传到 Forgejo：job summary 里的下载链接直接指
`https://cnb.cool/<artifact-repo>/-/releases/download/ci-<run_id>/<name>.tar.gz`，
`manager-ci`（main）/ `manager-dev`（dev）的 release body 也只是一张指向
`CNB_REPO` 的链接表（每次构建删除重建，旧 APK 不会残留，`published_at` 也随之前进），
分支各自发自己的 tag，互不覆盖。
Forgejo 上只保留 release 的标题和正文（管理器要解析版本号）。

约定：

- artifact 名用小写、`-` 分隔，带上组件和作用域，例如
  `sukisu-lkm-aarch64-android14-6.1`、`sukisu-ksud-aarch64-linux-android`、
  `sukisu-manager`；
- 下载目录统一 `artifacts/`，文件名即 artifact 内的相对路径；
- 新增产物不需要改任何工作流：`ci_artifact_put` 会归档到 CNB，summary 里的
  链接自动生效（在 job 里调 `ci_summary_artifacts <name>...`）。
- 跨 run 用 `ci_download_artifact NAME DEST RUN_ID`（Forgejo 自带 action 不支持跨 run）。

## Job Summary（渲染在 job 日志上方）

Forgejo 内置，不需要装任何东西：往 `$FORGEJO_STEP_SUMMARY` 指向的文件追加 Markdown
即可，内容会显示在该 job 的日志上方（`$GITHUB_STEP_SUMMARY` 也认；单步上限 1MiB）。

```yaml
      - name: Write the job summary
        run: |
          set -eux
          summary="${FORGEJO_STEP_SUMMARY:-${GITHUB_STEP_SUMMARY:-}}"
          [ -n "$summary" ] || exit 0
          site="${FORGEJO_PUBLIC_URL:-https://git.zako.shirkneko.top:20004}"
          run_url="${site}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
          {
            echo "## 构建产物"
            echo
            echo "| Artifact | 下载 |"
            echo "| --- | --- |"
            echo "| \`app-release\` | [下载](${run_url}/artifacts/app-release) |"
            echo
            echo "[运行详情](${run_url})"
          } >> "$summary"
```

SukiSU 里封装成了 `ci_summary_artifacts <name>...`（`common.sh`），用法：

```yaml
      - name: Write the job summary
        run: |
          set -eux
          . .github/scripts/ci/common.sh
          ci_summary_artifacts "sukisu-ksud-${{ matrix.target }}"
```

artifact 下载地址规则：`/{owner}/{repo}/actions/runs/{run_id}/artifacts/{name}`。

## 校验工作流文件

官方提供的 pre-commit hook：

```yaml
# .pre-commit-config.yaml
repos:
  - repo: https://code.forgejo.org/forgejo/runner
    rev: v11.1.1
    hooks:
      - id: forgejo-runner-validate
```

注意它是 `language: golang`——需要本机装 Go 和 pre-commit，首次运行会把 runner
从源码编译一遍。不想装 Go 的话，用 CI 版等价方案（本仓库已有 `validate.yml`）：

```yaml
      - name: Validate the workflow files
        run: |
          set -eux
          test -x /ci/tools/bin/forgejo-runner
          /ci/tools/bin/forgejo-runner validate --directory .
```

校验器来自 `/ci/tools/bin/forgejo-runner`（种子命令见 runner 的 README）。

## 内核模块 job（ddk 镜像）

`ghcr.io/ylarod/ddk-min:<kmi>-<release>` 里没有 node，靠 `/ci/tools/node` 兜底；
同一份 “Provide node” 步骤即可，无需额外配置。

```yaml
  build-kernelsu-ko:
    runs-on: ubuntu-latest
    container:
      image: ghcr.io/ylarod/ddk-min:android14-6.1-20260828
      options: --privileged
    steps:
      - name: Checkout
        # 同上
      - name: Provide node and the action sources
        # 同上
      - name: Build kernelsu.ko
        run: make -C kernel O=out ARCH=arm64 ...
      - name: Upload the aarch64 module
        uses: ./.ci-actions/upload-artifact
        with:
          name: sukisu-lkm-aarch64-android14-6.1
          path: out/kernelsu.ko
```

## 发布到 release / 通用包（可选）

```yaml
      - name: Publish the release
        env:
          API_TOKEN: ${{ secrets.RELEASE_API_TOKEN || secrets.GITHUB_TOKEN }}
        run: |
          set -eu            # 注意：这里不要 -x，令牌会进日志
          site="${FORGEJO_PUBLIC_URL:-https://git.zako.shirkneko.top:20004}"
          api="${site}/api/v1/repos/${GITHUB_REPOSITORY}"
          curl -sS -f -X POST -H "Authorization: token ${API_TOKEN}" \
            -H 'Content-Type: application/json' \
            -d "$(jq -nc --arg tag "app-ci" --arg sha "$GITHUB_SHA" \
              '{tag_name:$tag, target_commitish:$sha, name:$tag, prerelease:true}')" \
            "$api/releases"
```

- release 资产对公网可下载，适合“固定下载地址”；
- 通用包（`/api/packages/<owner>/generic/<name>/<version>/<file>`）也能匿名下载，
  但 `write:package` 权限要单独配 `PACKAGE_TOKEN`。

## 容易踩的坑

| 问题 | 处理 |
|---|---|
| `actions/checkout@v4` 等直接失败 | 换成镜像内工具 + 本地 action |
| `./gradlew` 卡住 | 用 `gradle`，wrapper 会去 services.gradle.org 下载 |
| 子模块拉不下来 | `git submodule set-url` 改成实例上的镜像 |
| 步骤名显示 `./.ci-actions/...` | 给每个 `uses:` 步骤写 `name` |
| 上传 401 | 用对权限的令牌；内置 `GITHUB_TOKEN` 没有 package 权限 |
| 跨 runner 找不到产物 | 用 `ci_artifact_put`（经 CNB 中转），不要只写 `/ci` |
| 产物下载链接 404 | 链接指向 CNB 的 `ci-<run_id>`；先确认这个 job 的 `ci_artifact_put` 真的成功了。若是很久以前的 run，它的 CNB 副本已被 `ci_artifact_cnb_prune` 按保留策略删掉，只能在产出它的机器 `/ci` 上找 |
| 日志暴露令牌 | 令牌相关命令前后 `set +x` / `set -x` |
| 任务被分到哪台机器 | 标签相同的 runner 先到先得，没有负载均衡配置 |

## 可选：并发与资源

- 每台 runner 的 `runner.capacity`：`WSL 可用内存 ÷ 4.5G`，再用线程数校验；
- job 内部的并行度用 `MAKEFLAGS` / `CARGO_BUILD_JOBS` 控制；
- 大型缓存放 `/ci/tools/*`，`gc` 只清 `/ci/runs` 和 docker 悬空对象。
