package com.xl.bill.mint.ui.filter

/**
 * 账单列表筛选条件（null = 该条件不限制）。
 * 实现 Serializable 以便作为导航参数被 rememberSaveable 保存（进程/旋转不丢）。
 *
 * @param type       收支类型：null=全部, 0=支出, 1=收入
 * @param channel    支付渠道：null=全部, alipay/wechat/bank/manual
 * @param range      时间范围（毫秒半开区间 [start, end)）：null=不限
 * @param sort       排序方式：null=时间倒序（默认，与旧数据兼容）
 * @param categoryId 分类：null=全部
 */
data class BillFilters(
    val type: Int? = null,
    val channel: String? = null,
    val range: Pair<Long, Long>? = null,
    val sort: BillSort? = null,
    val categoryId: Long? = null
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 2L
    }
}

/** 全部账单列表排序方式；[sortMode] 为 TransactionDao.getFiltered 的排序参数 */
enum class BillSort(val sortMode: Int) {
    /** 时间倒序（默认，与历史行为一致） */
    TIME_DESC(0),
    /** 金额从低到高 */
    AMOUNT_ASC(1),
    /** 金额从高到低 */
    AMOUNT_DESC(2),
    /** 时间正序（最早在前） */
    TIME_ASC(3)
}

/** 首页「最近账单」作用域：最近（近 3 天 20 条）/ 全部（跳转全部账单列表页） */
enum class ScopeMode { RECENT, ALL }

/** 下拉选项 */
data class SelectOption<T>(val label: String, val value: T)

/** 时间范围弹窗快捷模式 */
enum class TimeRangePreset { NONE, TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, CUSTOM }

/** 首页最近账单上限 */
const val RECENT_LIMIT = 20

/** 全部账单页每页条数（滚动加载） */
const val PAGE_SIZE = 30

/** 渠道取值常量（与 TransactionEntity.channel 一致） */
object BillChannels {
    const val WECHAT = "wechat"
    const val ALIPAY = "alipay"
    const val BANK = "bank"
    const val SMS = "sms"
    const val MANUAL = "manual"
}

/** 收支类型下拉选项（label 由调用方注入字符串资源） */
fun typeOptions(
    expenseLabel: String,
    incomeLabel: String,
    allLabel: String
): List<SelectOption<Int?>> = listOf(
    SelectOption(expenseLabel, _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity.Companion.TYPE_EXPENSE),
    SelectOption(incomeLabel, _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity.Companion.TYPE_INCOME),
    SelectOption(allLabel, null)
)

/** 支付渠道下拉选项（label 由调用方注入字符串资源） */
fun channelOptions(
    wechatLabel: String,
    alipayLabel: String,
    bankLabel: String,
    smsLabel: String,
    manualLabel: String,
    allLabel: String
): List<SelectOption<String?>> = listOf(
    SelectOption(wechatLabel, BillChannels.WECHAT),
    SelectOption(alipayLabel, BillChannels.ALIPAY),
    SelectOption(bankLabel, BillChannels.BANK),
    SelectOption(smsLabel, BillChannels.SMS),
    SelectOption(manualLabel, BillChannels.MANUAL),
    SelectOption(allLabel, null)
)

/** 排序方式下拉选项（label 由调用方注入字符串资源）；顺序：时间最新 / 时间最早 / 金额从高到低 / 金额从低到高 */
fun sortOptions(
    latestLabel: String,
    oldestLabel: String,
    amountDescLabel: String,
    amountAscLabel: String
): List<SelectOption<BillSort>> = listOf(
    SelectOption(latestLabel, BillSort.TIME_DESC),
    SelectOption(oldestLabel, BillSort.TIME_ASC),
    SelectOption(amountDescLabel, BillSort.AMOUNT_DESC),
    SelectOption(amountAscLabel, BillSort.AMOUNT_ASC)
)
