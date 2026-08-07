# 薄荷记账 🌿

一款安卓原生自动记账 App：支付宝 / 微信 / 银行 App 发生收入或支出时**自动记录账单**，App 不打开也能记账，并提供报表统计与大盘分析。UI 采用「薄荷清新 + 毛玻璃 + 圆润 + 可爱」风格，支持深浅双主题。

> 技术栈：Kotlin + Jetpack Compose + Material 3 + Room + WorkManager · minSdk 31（Android 12）/ targetSdk 35 · 单模块，无 Hilt 无重依赖。

---

## 一、自动记账原理

| 数据源 | 角色 | 说明 |
| --- | --- | --- |
| **通知监听** `NotificationListenerService` | 主 | 捕获支付宝/微信/银行的支付通知，解析金额、收支方向、商户名，自动归类并落库 |
| **无障碍服务** `AccessibilityService` | 兜底 | 某支付 App 通知被关闭时，读取支付页面上的金额文本完成记账 |

- 仅处理白名单包名（支付宝 `com.eg.android.AlipayGphone`、微信 `com.tencent.mm` 等、主流银行 App），**不读取**聊天、相册等任何隐私内容。
- 解析引擎为纯 Kotlin（`parser/BillParseEngine`），规则容错千分位/空格，配套 JVM 单测。
- 三层去重：跨来源指纹（通知 vs 无障碍 2 分钟互斥）→ 通知 key → DB 唯一索引，杜绝重复记账。

## 二、后台服务保活/拉活

App 被清后台后**只拉活记账服务，不拉起界面**。保活栈：

1. **前台服务**（`BillForegroundService`，`dataSync` 类型）+ 低优先级常驻通知 + `START_STICKY`
2. **AlarmManager 心跳**：`setAndAllowWhileIdle` 15 分钟轮询，进程被杀后由系统拉起进程并尝试重建服务
3. **WorkManager** 周期任务补充存活检查
4. **开机广播** `BootReceiver` 开机自动拉活
5. **通知监听服务由系统绑定**：只要用户开启「通知使用权」，即使进程被清理，新支付通知到达时系统也会自动唤起它完成记账

> ⚠️ **诚实边界**：Android 12+ 无法做到 100% 保活。以下场景记账会中断：用户 **Force Stop** 应用、开启**超级省电**、未加入**厂商自启动白名单**、系统更新重置权限。请在 App 内「设置 → 权限与保活」完成引导。

## 三、报表统计

- 首页：本月支出 / 收入 / 结余 总览卡（毛玻璃 + 收支占比环形图）+ 最近账单
- 报表页（支持月份切换）：
  - 收入 / 支出占比（环形图 + 图例）
  - 分类占比（支出 / 收入，环形图 + 关键词自动归类）
  - 大额支出 TOP10 / 大额收入 TOP10
  - 近 12 个月收支趋势柱状图
- 全部图表为 Compose 自绘（Canvas / 布局），**零第三方图表依赖**
- 自动记账难免误记：账单支持点击修改分类、编辑备注、删除；也可 FAB 手动记账

## 四、构建运行

1. 环境：Android Studio **Ladybug 及以上**、JDK 17、Android SDK 35（Android Studio 首次打开会自动下载）
2. 打开工程根目录 `bill_log/`，等待 Gradle 同步完成（首次约几分钟）
3. `Build > Make Project` 或 `./gradlew :app:assembleDebug`
4. 安装到 Android 12+ 手机，按首次引导开启：
   - **通知使用权**（记账主数据源，必开）
   - **无障碍服务**（兜底，推荐）
   - **电池白名单 + 自启动**（保活，推荐）

运行解析单测：`./gradlew :app:testDebugUnitTest`

## 五、工程结构

```
app/src/main/java/com/example/bill/
├── MainActivity.kt / OnboardingActivity.kt / BillApplication.kt
├── data/db/        Room 四表（账单/分类/渠道/设置）+ DAO + AppDatabase
├── data/repo/      TransactionRepository / SettingsRepository
├── di/             ServiceLocator（手动 DI）
├── parser/         BillParseEngine、PaymentApps 白名单、CategoryMatcher、Deduplicator
├── service/        BillNotificationListenerService、BillAccessibilityService、
│                   BillForegroundService、BillRecordPipeline（记账管线）
├── receiver/       BootReceiver、HeartbeatReceiver、HeartbeatWorker
├── util/           KeepAliveHelper、HeartbeatScheduler、NotificationHelper、
│                   PermissionChecker、RomGuideHelper、MoneyFormatter、StatisticsCalculator
└── ui/
    ├── theme/      薄荷明暗双套配色 / 圆体 Typography / 大圆角 Shapes
    ├── components/ GlassCard（毛玻璃）、Mascot（吉祥物）、RingChart/BarChart/LineChart、
    │               OverviewCard、BillCard、EmptyState、CategoryPicker
    ├── screens/    Dashboard / Statistics / Settings + AddBillSheet / BillDetailSheet
    └── viewmodel/  DashboardViewModel / StatisticsViewModel / SettingsViewModel
```

## 六、可定制项

| 项 | 位置 | 说明 |
| --- | --- | --- |
| 分类规则 | `assets/categories.json` | 新增分类/关键词即可扩展自动归类 |
| 支付渠道白名单 | `parser/PaymentApps.kt` | 追加 App 包名即可支持更多支付/银行 App |
| 配色 | `ui/theme/Color.kt` | 明暗两套薄荷色系 |
| 圆体字体 | `ui/theme/Type.kt` | 将圆体放入 `res/font/rounded.ttf` 并替换 `FontFamily` |

## 七、已知限制

- 保活受系统/厂商限制，无法 100% 存活（见上文第二节）
- 银行 App 通知模板差异大，个别模板可能解析失败或归类不准，可手动纠错
- `Room.fallbackToDestructiveMigration()`：MVP 阶段 schema 变更会清空数据，正式发布前应改为 Migration
