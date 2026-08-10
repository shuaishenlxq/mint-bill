# 薄荷记账 MintBill 🌿

一款安卓原生**自动记账** App：支付宝 / 微信 / 银行 App / 短信发生收入或支出时自动记录账单，App 不打开也能记账。提供报表统计、目标存款、账单导入导出与桌面小组件，UI 采用「薄荷清新 + 毛玻璃 + 圆润」风格，支持深浅双主题。

> **技术栈**：Kotlin + Jetpack Compose + Material 3 + Room + SQLCipher + WorkManager + Glance
> **兼容**：minSdk 31（Android 12）/ targetSdk 35 · 单模块、手动 DI、无重依赖

---

## 一、功能特性

| 模块 | 能力 |
| --- | --- |
| **自动记账** | 通知监听（主）+ 无障碍（兜底）+ 短信（补充），四渠道自动解析金额 / 收支方向 / 商户并归类落库 |
| **防重防误** | 三层去重（跨来源互斥 → 通知 key → 数据库唯一索引）+ 广告 / 营销通知过滤 |
| **手动记账** | 快捷记一笔、账单修改分类 / 编辑备注 / 删除 |
| **报表统计** | 收支总览、分类占比、大额 TOP10、近 12 个月趋势（Compose 自绘图表，零第三方依赖） |
| **目标存款** | 设置初始存款与月度目标，按「月收入 − 月支出」累计净结余，支持自定义起始日 |
| **账单管理** | 全量账单页：时间范围、分类、渠道筛选 + 金额 / 时间排序 |
| **数据迁移** | 微信 Excel / 支付宝 CSV 批量导入（真实格式解析）；JSON 导出 / 导入；蓝牙换机传输 |
| **安全加固** | SQLCipher 整库加密 + Keystore 密钥托管 + 生物识别应用锁 + 崩溃自愈降级 |
| **桌面小组件** | 今日收支结余总览（无记录回退自然周），明暗双色毛玻璃卡片，2x2 / 4x2 自适应 |
| **后台保活** | 前台服务 + AlarmManager 心跳 + WorkManager + 开机广播，进程被清后自动拉活记账 |

---

## 二、自动记账原理

| 数据源 | 角色 | 说明 |
| --- | --- | --- |
| **通知监听** `NotificationListenerService` | 主 | 捕获支付宝 / 微信 / 银行 App 的支付通知，解析金额、收支方向、商户名，自动归类并落库 |
| **无障碍服务** `AccessibilityService` | 兜底 | 某支付 App 通知被关闭时，读取支付页面上的金额文本完成记账 |
| **短信** `SmsReceiver` | 补充 | 解析含金额 + 收支关键词的交易短信（银行卡余额变动、验证码通知等）自动记账 |

- 仅处理白名单包名（支付宝 `com.eg.android.AlipayGphone`、微信 `com.tencent.mm`、工商 / 建设 / 农业 / 招商等主流银行与云闪付），**不读取**聊天、相册等任何隐私内容。
- 解析引擎 `parser/BillParseEngine` 为纯 Kotlin，容错千分位 / 空格 / 货币符号，配套 JVM 单测；解析链路经 `service/BillRecordPipeline` 统一入库。
- **三层去重**：跨来源指纹（通知 vs 无障碍 60 秒互斥）→ 通知 key（`pkg|通知key`，短信为 `sms-$sender-$ts`）→ 数据库唯一索引，杜绝重复记账。
- **广告过滤**：内置保险 / 营销黑词表，支持在设置页追加自定义过滤词，营销通知直接丢弃。

---

## 三、报表统计与存款目标

- **首页**：本月支出 / 收入 / 结余总览卡（毛玻璃 + 收支占比环形图）+ 最近账单 + 目标存款进度卡
- **统计页**（支持月份切换，收支 / 存款双 Tab）：
  - 收入 / 支出占比（环形图 + 图例）
  - 分类占比（支出 / 收入，环形图 + 关键词自动归类）
  - 大额支出 TOP10 / 大额收入 TOP10
  - 近 12 个月收支趋势柱状图
  - 存款视图：月收入 / 月支出 / 月结余 + 累计存款走势
- 全部图表为 Compose 自绘（Canvas / 布局），**零第三方图表依赖**
- 自动记账难免误记：账单支持点击修改分类、编辑备注、删除；也可通过 FAB 手动记账

---

## 四、数据安全

- **整库加密**：Room + SQLCipher 4.5.4（AES-256），开启 WAL 日志模式；数据库文件对外不可直接读取。
- **密钥托管**：32 字节随机口令经 Android Keystore（AES-256-GCM）包裹后落盘，密钥不落明文。
- **无缝迁移**：旧版明文库首次启动自动备份 → 事务迁移至加密库 → 删除明文（幂等）。
- **崩溃自愈**：加密初始化失败时备份旧库文件并降级为明文新库，**保证 App 可启动不裸崩**；所有失败路径写入 `crash.log`。
- **应用锁**：Biometric 生物识别（指纹 / 人脸 / 设备密码）锁应用。
- **隐私底线**：`allowBackup=false` + `data_extraction_rules`，禁止系统云备份外泄账单数据。

---

## 五、导入导出与换机

| 方向 | 说明 |
| --- | --- |
| **微信 Excel 导入** | 解析真实微信账单 xlsx（时间序列号 / 金额浮点 / 34 位交易单号等格式坑已处理），XmlPullParser 流式解析，真机 / JVM 行为一致 |
| **支付宝 CSV 导入** | 解析真实支付宝 CSV（GB18030 + CRLF），自动过滤余额宝转入 / 收益 / 退款等中性记录 |
| **JSON 导出 / 导入** | 全量账单导出为 JSON v1；导入支持 `REPLACE`（清空重导）与 `SKIP`（仅补新）两种策略 |
| **蓝牙换机** | 无网络直连传输（SPP 协议 + 长度前缀 + JSON 分块），新手机一键接管旧账单 |

导入统一走记账管线，自动归类、自动查重（按渠道交易单号 / 通知 key），历史数据不会重复入账。

---

## 六、后台保活 / 拉活

App 被清后台后**只拉活记账服务，不拉起界面**。保活栈：

1. **前台服务**（`BillForegroundService`，`dataSync` 类型）+ 低优先级常驻通知 + `START_STICKY`
2. **AlarmManager 心跳**：`setAndAllowWhileIdle` 15 分钟轮询，进程被杀后由系统拉起并尝试重建服务
3. **WorkManager** 周期任务补充存活检查
4. **开机广播** `BootReceiver` 开机自动拉活
5. **通知监听服务由系统绑定**：只要用户开启「通知使用权」，即使进程被清理，新支付通知到达时系统也会自动唤起它完成记账

> ⚠️ **诚实边界**：Android 12+ 无法做到 100% 保活。以下场景记账会中断：用户 **Force Stop** 应用、开启**超级省电**、未加入**厂商自启动白名单**、系统更新重置权限。请在 App 内「设置 → 权限与保活」完成引导。

---

## 七、构建运行

1. **环境**：Android Studio Ladybug 及以上、JDK 17、Android SDK 35（Android Studio 首次打开会自动下载）
2. 打开工程根目录，等待 Gradle 同步完成（首次约几分钟）
3. 编译：`./gradlew :app:assembleDebug`
4. 运行 JVM 单测：`./gradlew :app:testDebugUnitTest`
5. 安装到 Android 12+ 手机，按首次引导开启：
   - **通知使用权**（记账主数据源，必开）
   - **无障碍服务**（兜底，推荐）
   - **短信权限**（短信记账，按需）
   - **电池白名单 + 自启动**（保活，推荐）

> 体积优化：release 构建开启 R8 混淆 + 资源裁剪；仅打包 `arm64-v8a`、裁剪语言资源至中英文、native so 走 deflate 压缩。

---

## 八、工程结构

```
app/src/main/java/com/xl/bill/mint/
├── BillApplication.kt / MainActivity.kt / OnboardingActivity.kt
├── billimport/     微信 Excel（XmlPullParser 流式）、支付宝 CSV（GB18030）导入
├── data/
│   ├── db/         Room 四表（账单/分类/账户/设置）+ DAO + AppDatabase（SQLCipher 加密迁移）
│   └── repo/       TransactionRepository / CategoryRepository / SettingsRepository
├── di/             ServiceLocator（手动 DI，无 Hilt）
├── parser/         BillParseEngine、PaymentApps 白名单、CategoryMatcher、Deduplicator、
│                   ExcelBillParser、AlipayCsvParser
├── receiver/       BootReceiver / HeartbeatReceiver / HeartbeatWorker / SmsReceiver
├── security/       KeyStoreManager（密钥托管）
├── service/        BillNotificationListenerService / BillAccessibilityService /
│                   BillForegroundService / BillRecordPipeline（记账管线）
├── transfer/       TransferCodec / ExportManager / ImportManager / BluetoothTransfer
├── util/           CrashLog / KeepAliveHelper / HeartbeatScheduler / NotificationHelper /
│                   PermissionChecker / RomGuideHelper / MoneyFormatter / StatisticsCalculator /
│                   SavingsCalculator / TimeRange / TimeUtil
├── widget/         BudgetWidget（Glance 桌面小组件）+ Receiver / RefreshWorker / Scheduler
└── ui/
    ├── theme/      薄荷明暗双套配色 / 圆体 Typography / 大圆角 Shapes
    ├── components/ GlassCard（毛玻璃）、RingChart/BarChart/LineChart、BillCard、CategoryPicker 等
    ├── filter/     BillFilters / BillSort（排序下推 SQL）
    ├── screens/    Dashboard / Statistics / AllBills / Settings + 各类 Sheet
    ├── viewmodel/  Dashboard / Statistics / AllBills / Settings / BillImport / Transfer
    └── components/ …（同上）
```

---

## 九、测试

解析、归类、统计、导入、传输等核心逻辑均为纯 Kotlin，可直接 JVM 单测：

- **解析**：`BillParseEngineTest`（通知）、`BillParseEngineSmsTest`（短信）、`ExcelBillParserTest`、`AlipayCsvParserTest`
- **归类**：`CategoryMatcherTest`、`CategoryRepositoryTest`
- **去重**：`DeduplicatorTest`、`CrossSourceDedupTest`
- **统计 / 存款**：`StatisticsCalculatorTest`、`SavingsCalculatorTest`
- **导入**：`XlsxWorkbookReaderTest`、`CsvReaderTest`（含 kxml2，与真机同一解析器）
- **其它**：`TransferCodecTest`、`MoneyFormatterTest`、`TimeRangeTest`、`BillSortTest`

运行：`./gradlew :app:testDebugUnitTest`

---

## 十、可定制项

| 项 | 位置 | 说明 |
| --- | --- | --- |
| 分类规则 | `assets/categories.json` | 新增分类 / 关键词即可扩展自动归类 |
| 支付渠道白名单 | `parser/PaymentApps.kt` | 追加 App 包名即可支持更多支付 / 银行 App |
| 广告过滤词 | 设置页「广告过滤」 | 内置黑词表 + 自定义追加 |
| 默认分类 | 设置页「默认分类」 | 配置无法自动归类时的支出 / 收入兜底分类 |
| 配色 | `ui/theme/Color.kt` | 明暗两套薄荷色系 |
| 字体 | `ui/theme/Type.kt` | 将圆体放入 `res/font/` 并替换 `FontFamily` |

---

## 十一、已知限制

- 保活受系统 / 厂商限制，无法 100% 存活（见上文第六节）
- 银行 App 通知模板差异大，个别模板可能解析失败或归类不准，可手动纠错
- 微信零钱支付无通知、无金额可解析，属于系统侧限制
- 短信晚到与 App 通知先记的反向顺序，超过 60 秒互斥窗口可能双记
- 应用分身（如微信 XSpace 用户空间）的通知机主收不到，无法记账
- 小组件明文渲染、蓝牙 / 导出 JSON 为明文传输，口令加密留待后续版本
- `fallbackToDestructiveMigration()`：极端 schema 变更会清空数据，正式发布前关键升级应改为显式 Migration（当前已内置 `MIGRATION_1_2`）

---

## 十二、协议

本项目为个人学习项目，仅供学习交流使用，请勿用于商业用途。
