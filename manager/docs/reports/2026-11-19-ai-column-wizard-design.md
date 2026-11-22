# AI 助手接入「隐藏环境」专栏 —— 设计定稿

> 2026-11-19 · 三轮 grilling 后的定稿。目标：把商店专栏「隐藏环境必刷」的多步向导接进 AI 助手，
> 让用户用对话就能选档位、跑步骤、重启、回滚、看红项。

## 1. 问题边界

专栏 `hide-env`（app/src/main/java/com/sukisu/ultra/data/model/RepoColumn.kt:12）是「按功能盘点的模块组合」，
三个专题：`pass-bank`（日用银行级，2 个重启点）、`pass-integrity`（谷歌三绿）、`self-check`（环境自检，只读）。

现状：AI 只能 `open_app_page(module_repo)` 导航 + 装单个模块，驱动不了 ColumnWizardScreen。

## 2. 决策台账（Q1–Q16）

| # | 决策 | 结论 |
|---|---|---|
| Q1 | 状态源 | **C 双轨**：账本（ColumnProgressStore）是唯一真相，AI 与向导是两个驱动端 |
| Q2 | 分级映射 | 专题即档位：pass-bank = 日用银行级，pass-integrity = 谷歌三绿 |
| Q3 | 重启后接续 | **B 被动接续**：用户回会话说「继续」才读账本，不主动弹窗 |
| Q4 | 会话持久 | 自动通过（会话本身已跨重启持久） |
| Q5 | 授权 | **B 分级**：读静默 / 装模块留确认 / 重启与回滚每次确认 |
| Q6 | 春秋检测 | 第三方 app，AI 不代读，靠用户带回报告 |
| Q7 | 范围 | 三专题一次做完 |
| Q8 | 档位留痕 | **C 双写**：ask_user 问一次（对话留痕）+ 账本写 selected_tier |
| Q9 | 执行引擎 | **B 抽无 UI 服务**：ColumnAutomationService，向导 VM 与 AI 共用同一批执行器与账本 |
| Q10 | 重启确认 | **B 确认卡即执行**：确认回调里先写 pending_resume 再 su -c reboot，不照搬 10 秒倒计时 |
| Q11 | 外部 app 步骤 | **A**：标 NEEDS_USER + ask_user 问结果 + 导航辅助，绝不代开代填 |
| Q12 | 红项两线 | **C**：向导 12 项只读探针结构化注入 + 春秋报告经 search_detector_answers 匹配，AI 汇合出清单 |
| Q13 | 工具拆分 | **C** 两个：column_wizard（读）+ column_wizard_run（写/重启/回滚） |
| Q14 | 导航 | **B**：新增 page_id module_column、column_wizard |
| Q15 | 进度 UI | **A** 纯文本播报（不做消息卡，向导页已有完整可视化） |
| Q16 | 验证 | **B** 单测 + 模拟器冒烟 |

## 3. 分层

```
AI 模型
  │  column_wizard / column_wizard_run（ai 模块，AiTools.kt）
  ▼
AiColumnService（ai-api 契约，AiHost.column）
  ▼
AiColumnController（app/ai/host，把结果格式化成模型可读文本）
  ▼
ColumnAutomationService（app/data/column，无 UI 执行内核）
  │  ColumnStepRunnerImpl / ColumnSelfCheckRunnerImpl /
  │  ColumnRollbackStore / ColumnProgressStore
  ▼
设备（root shell、模块安装、keybox 写入）

UI 向导 ColumnWizardViewModel ──同一批执行器与账本──▶ 设备
```

**关键**：不搬 ViewModel 的状态机（那是 UI 编排），只把「执行一步 + 落账 + 回滚 + 待续跑」抽成无 UI 服务。
两个驱动端共享 ColumnStepRunner 与 ColumnProgressStore，账本仍是单源（兑现 Q1=C）。

## 4. 授权矩阵

| 动作 | 授权 | 说明 |
|---|---|---|
| 读专栏/专题/步骤/账本/自检 | 静默 | 纯只读，不触设备写入 |
| run_step（装模块、写文件、跑命令） | 需 allowModuleInstall + 每次确认卡 | 装模块是设备级改动 |
| reboot | 每次确认卡，allowRemember=false | 同 run_shell 策略：不可「始终允许」 |
| rollback | 每次确认卡，allowRemember=false | 会卸模块、还原文件 |
| select_tier | 静默（仅是偏好记录） | 只写 selected_tier，不动设备 |

## 5. 重启与接续时序

```
模型 → column_wizard_run(reboot, column_id, topic_id, step_id)
  → 确认卡「将重启设备，回来后我会接着跑」
  → 用户点允许
  → ColumnProgressStore.markPendingResume(columnId, topicId, stepId)   // 先写
  → stepRunner 执行 reboot 步骤（su -c reboot）
  → 进程被杀；账本里留下 pending_resume + bootCount
设备重启 → 用户回到会话说「继续」
  → column_wizard(action=status) 读 peekPendingResume：bootCount 增长 + 2h TTL 内
  → 报告「上次停在 step_id，下一步是 X」→ 继续 run_step
```

不做的事：不主动推送「设备回来了」，不在 App 启动时弹窗（Q3=B 被动）。

## 6. 工具契约

### column_wizard（读）

| action | 参数 | 返回 |
|---|---|---|
| list | — | 专栏 + 专题（id/title/tier/risk/步骤数/是否需要 keybox） |
| status | — | 账本最新条目 + pending_resume + 各专题 selected_tier |
| steps | column_id, topic_id | 步骤清单（id/title/kind/optional/confirm/hidden） |
| checks | — | 12 项自检结果（id/passed/detail） |

### column_wizard_run（写）

| action | 参数 | 行为 |
|---|---|---|
| run_step | column_id, topic_id, step_id, force | 跑一步；kind=reboot 会被拒绝并提示改用 reboot |
| reboot | column_id, topic_id, step_id | 写 pending_resume 后重启 |
| rollback | column_id, topic_id | 逆序撤销本次改动；失败条目保留在账本 |
| select_tier | column_id, topic_id, tier | 记录档位偏好（写 column_wizard prefs） |

needs_user=true 时模型必须停下等用户（外部 app 步骤、verify 类）。

## 7. 不做的事

- 不做聊天内进度卡（Q15=A）；进度看向导页，AI 只播报文字。
- 不代开第三方 app、不代填春秋报告。
- 不在 AI 侧做 pass-bank 全自动一键盘：一键执行仍在向导页；AI 逐步推进，每步可解释。
- 不改账本数据格式（ColumnProgressEntry 字段不动），档位另存同 prefs 的 selected_tier_columnId_topicId。

## 8. 实现排期

| 阶段 | 内容 | 产出 |
|---|---|---|
| P1 | ColumnAutomationService | 无 UI 执行内核（读专栏、跑步骤、重启、回滚、自检、档位） |
| P2 | ai-api AiColumnService + AiHost.column | 稳定契约 |
| P3 | AiColumnController | 模型可读文本 + 结果落账 |
| P4 | AiTools 两工具 + spec + 分发 | 模型可用 |
| P5 | AiFeatureCatalog 两个 page_id | 导航直达 |
| P6 | 单测（纯逻辑）+ 模拟器冒烟 | 验证 |

## 9. 验收

1. 说「隐藏环境」，AI 说清档位并 ask_user 让用户选。
2. 选日用银行级 → 逐步执行，装模块前有确认卡。
3. 到重启步骤 → 确认卡点允许 → 设备重启。
4. 回来后说「继续」→ AI 报出停在哪一步并接着跑。
5. 自检红项 → AI 给出修复清单；春秋报告贴进来能匹配到条目。
6. 说「回滚」→ 确认后逆序撤销，账本清空。
