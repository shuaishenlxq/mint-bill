package com.xl.bill.mint.parser

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.text.iterator

/**
 * 账单解析引擎（纯 Kotlin，JVM 可单测）。
 *
 * 两条入口：
 * - [parse]：通知场景（NLS）。全文找第一个合法金额 → 判定方向 → 提取商户。
 * - [parseAccessibilityScene]：无障碍场景。页面文本须命中转账/支付动作词（场景门禁），
 *   金额按「动作词就近 + 唯一值优先」策略选择，避免聊天页多个转账气泡一起误记。
 *
 * 解析失败（无金额、金额非法）返回 null，由上层丢弃。
 */
object BillParseEngine {

    /** 单笔金额上限：1 亿元（分） */
    const val MAX_AMOUNT_FEN = 10_000_000_000L

    private val AMOUNT_SYMBOL_RE = Regex("[¥￥]\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val AMOUNT_YUAN_RE = Regex("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元")

    /** 银行短信「人民币-14.79」格式：无 ¥ 符号、无「元」后缀，金额可带负号（表示扣款方向，取绝对值） */
    private val AMOUNT_RMB_RE = Regex("人民币\\s*[-－]?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")

    /** 强收入词（微信转账/红包场景，命中必为收入） */
    private val INCOME_STRONG_WORDS = listOf(
        "请收款", "收款成功", "已收钱", "收到转账", "收到红包", "向你转账", "转账给你",
        "你领取了红包", "红包已领取", "红包已被领取"
    )

    /** 强收入间隔正则（覆盖「收到一笔转账」「收到一个大红包」等带量词表达） */
    private val INCOME_STRONG_RE = listOf(
        Regex("收到.{0,8}?转账"),
        Regex("收到.{0,8}?红包"),
        Regex("向你.{0,8}?转账")
    )

    /** 强支出词（命中必为支出；放在收入词之前，覆盖「你发出的红包」等歧义） */
    private val EXPENSE_STRONG_WORDS = listOf(
        "你发出的红包", "已被接收", "转给", "转账给", "已转出", "转账成功",
        "付款成功", "支付成功", "已支付", "发出"
    )

    /** 通用收入关键词 */
    private val INCOME_WORDS = listOf(
        "收入", "到账", "收款", "转入", "入账", "存入", "收益", "利息", "分红",
        "退款", "退回", "返现", "红包", "工资", "报销", "退款到账"
    )

    private val EXPENSE_WORDS = listOf(
        "付款", "支出", "消费", "支取", "扣款", "转账给", "向", "支付", "购买",
        "取款", "转出", "代扣", "缴费", "付款给", "支出(消费)"
    )

    /** 通用性标题：出现这些词的标题不是商户名 */
    private val GENERIC_TITLE_WORDS = listOf(
        "支付", "凭证", "收款", "到账", "入账", "转账", "银行", "信使", "支付宝",
        "微信", "红包", "余额", "退款", "账单"
    )

    /** 商户行排除词（防止把「转账详情/备注/金额」等当昵称） */
    private val MERCHANT_EXCLUDE_WORDS = listOf(
        "详情", "备注", "留言", "金额", "确认", "转账", "红包", "零钱",
        "已收", "已付", "收款", "到账", "凭证", "支付", "成功", "存入", "退回",
        "余额", "明细", "账单", "浮窗", "钱包", "全部"
    )

    /**
     * 转账/支付动作词（无障碍场景门禁 + 多金额就近锚点）。
     * 仅收录「动作」词，绝不收录页面导航名（零钱明细/全部账单/转账记录/浮窗）——
     * 否则「零钱余额页/明细页」整页金额会被批量误记（曾把 ¥4065.33 余额记成收入）。
     */
    private val TRANSFER_SCENE_WORDS =
        (INCOME_STRONG_WORDS + EXPENSE_STRONG_WORDS + listOf(
            "立即收款", "确认收款", "向商家付款", "收钱", "交易成功"
        )).distinct()

    /** 余额/汇总类页面特征词：命中视为非单笔交易页面，不记账 */
    private val BALANCE_PAGE_WORDS = listOf(
        "零钱余额", "余额", "全部账单", "零钱明细", "账单明细", "浮窗", "钱包余额"
    )

    /** 广告/营销/保险类强词：命中且无交易动作词 → 拦截（防止「补齐住院保障更安心1元」误记账） */
    private val AD_BLOCK_WORDS = listOf(
        "投保", "保费", "理赔", "领取保障", "保障", "安心", "住院", "重疾", "意外险",
        "免费", "赠送", "立减", "优惠券", "抽奖", "中奖", "秒杀", "限时", "领券",
        "福利", "广告", "推广", "营销"
    )

    private data class MoneyCandidate(val amountFen: Long, val lineIndex: Int)

    // ------------------------------------------------------------------
    // 通知场景入口
    // ------------------------------------------------------------------

    fun parse(
        pkg: String,
        title: String?,
        text: String?,
        occurredAt: Long = System.currentTimeMillis(),
        notificationKey: String? = null,
        blockedWords: List<String> = emptyList(),
        customGroups: List<CustomMatchGroup> = emptyList()
    ): ParsedBill? = parseWithReason(pkg, title, text, occurredAt, notificationKey, blockedWords, customGroups).bill

    /**
     * 带拒绝原因的解析入口（诊断日志用）。行为与 [parse] 完全一致，
     * 仅把每个 return null 点替换为带 [ParseRejectReason] 的 [ParseOutcome]。
     */
    fun parseWithReason(
        pkg: String,
        title: String?,
        text: String?,
        occurredAt: Long = System.currentTimeMillis(),
        notificationKey: String? = null,
        blockedWords: List<String> = emptyList(),
        customGroups: List<CustomMatchGroup> = emptyList()
    ): ParseOutcome {
        val channel = PaymentApps.channelOf(pkg)
            ?: return ParseOutcome(null, ParseRejectReason.UNSUPPORTED_PACKAGE)
        val t = title?.trim().orEmpty()
        val b = text?.trim().orEmpty()
        if (t.isEmpty() && b.isEmpty()) return ParseOutcome(null, ParseRejectReason.EMPTY_TEXT)
        val combined = "$t\n$b"
        // 广告门禁：命中广告词且无交易动作词（支付成功/扣款/到账等）→ 非交易通知，直接拒绝。
        // 自定义关键词兜底：系统预设词未命中时，自定义组全中 → 视为用户确认的真实账单，放行
        if (isAdNotification(combined, blockedWords) &&
            !matchCustomGroup(combined, customGroups, CustomKeywordScope.NOTIFICATION)
        ) {
            return ParseOutcome(null, ParseRejectReason.AD_BLOCKED)
        }

        val amountFen = extractAmountFen(combined) ?: return ParseOutcome(null, ParseRejectReason.NO_AMOUNT)
        if (amountFen <= 0 || amountFen > MAX_AMOUNT_FEN) {
            return ParseOutcome(null, ParseRejectReason.AMOUNT_OUT_OF_RANGE)
        }

        val type = detectType(t, b)
        val merchant = extractMerchant(channel, t, b)

        return ParseOutcome(
            ParsedBill(
                channel = channel,
                amount = amountFen,
                type = type,
                merchant = merchant,
                rawTitle = t.ifEmpty { null },
                rawText = b.ifEmpty { null },
                occurredAt = occurredAt,
                notificationKey = notificationKey
            )
        )
    }

    // ------------------------------------------------------------------
    // 无障碍场景入口
    // ------------------------------------------------------------------

    /**
     * 无障碍页面解析：场景门禁 + 多金额就近选择。
     * 仅当页面文本命中转账/支付动作词时才解析，否则返回 null（聊天页滚动不记账）。
     */
    fun parseAccessibilityScene(
        pkg: String,
        text: String?,
        occurredAt: Long = System.currentTimeMillis(),
        notificationKey: String? = null
    ): ParsedBill? = parseAccessibilityWithReason(pkg, text, occurredAt, notificationKey).bill

    /** 带拒绝原因的无障碍解析入口，行为与 [parseAccessibilityScene] 一致。 */
    fun parseAccessibilityWithReason(
        pkg: String,
        text: String?,
        occurredAt: Long = System.currentTimeMillis(),
        notificationKey: String? = null
    ): ParseOutcome {
        val channel = PaymentApps.channelOf(pkg)
            ?: return ParseOutcome(null, ParseRejectReason.UNSUPPORTED_PACKAGE)
        val b = text?.trim().orEmpty()
        if (b.isEmpty()) return ParseOutcome(null, ParseRejectReason.EMPTY_TEXT)
        if (!isTransferScene(b)) return ParseOutcome(null, ParseRejectReason.NOT_TRADE_SCENE)
        // 余额/汇总类页面（零钱余额、全部账单、浮窗等）整页金额非单笔交易，直接放弃
        if (BALANCE_PAGE_WORDS.any { b.contains(it) }) return ParseOutcome(null, ParseRejectReason.BALANCE_PAGE)

        val lines = b.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ParseOutcome(null, ParseRejectReason.NO_AMOUNT)

        val amountFen = extractSceneAmount(lines) ?: return ParseOutcome(null, ParseRejectReason.NO_AMOUNT)
        if (amountFen <= 0 || amountFen > MAX_AMOUNT_FEN) {
            return ParseOutcome(null, ParseRejectReason.AMOUNT_OUT_OF_RANGE)
        }

        val type = detectType("", b)
        val merchant = extractMerchant(channel, "", b)

        return ParseOutcome(
            ParsedBill(
                channel = channel,
                amount = amountFen,
                type = type,
                merchant = merchant,
                rawTitle = null,
                rawText = b.ifEmpty { null },
                occurredAt = occurredAt,
                notificationKey = notificationKey
            )
        )
    }

    /** 场景门禁：页面文本是否命中转账/支付动作词 */
    fun isTransferScene(text: String?): Boolean {
        val s = text ?: return false
        return TRANSFER_SCENE_WORDS.any { s.contains(it) } ||
            INCOME_STRONG_RE.any { it.containsMatchIn(s) }
    }

    /**
     * 广告判定：命中广告/营销词 且 不含任何交易动作词（支付成功/扣款/到账/转账等）→ 拦截。
     * 双条件避免误伤真实账单：真实支付通知必然含交易动作词（如「XX医院支付成功 5000元」）。
     * @param customWords 设置页自定义过滤词（解析时与内置词表合并）
     */
    fun isAdNotification(combined: String, customWords: List<String> = emptyList()): Boolean {
        if (TRADE_GUARD_WORDS.any { combined.contains(it) }) return false
        return AD_BLOCK_WORDS.any { combined.contains(it) } || customWords.any { combined.contains(it) }
    }

    /**
     * 多金额选择：
     * - 去重后唯一值 = 1 → 直接用（详情页同金额重复展示不误判）；
     * - 唯一值 ≥ 2 → 取「动作词行距离最近」的金额，距离 > 3 行视为歧义返回 null（保守优先）。
     */
    fun extractSceneAmount(lines: List<String>): Long? {
        val candidates = lines.mapIndexedNotNull { index, line ->
            extractAmountFen(line)?.let { MoneyCandidate(it, index) }
        }
        if (candidates.isEmpty()) return null

        val uniqueAmounts = candidates.map { it.amountFen }.distinct()
        if (uniqueAmounts.size == 1) return uniqueAmounts[0]

        val sceneIndex = lines.indexOfFirst { isTransferScene(it) }
        if (sceneIndex < 0) return null

        val nearest = candidates.minByOrNull { abs(it.lineIndex - sceneIndex) } ?: return null
        return if (abs(nearest.lineIndex - sceneIndex) > 3) null else nearest.amountFen
    }

    // ------------------------------------------------------------------
    // 金额 / 方向 / 商户
    // ------------------------------------------------------------------

    /** 提取金额（元）→ 分；先做全角归一化，再匹配 ¥/￥ 前缀、「元」后缀与「人民币」锚点，去除千分位逗号与空格 */
    fun extractAmountFen(raw: String): Long? {
        val normalized = normalizeFullWidth(raw)
        val m = AMOUNT_SYMBOL_RE.find(normalized) ?: AMOUNT_YUAN_RE.find(normalized)
            ?: AMOUNT_RMB_RE.find(normalized) ?: return null
        val digits = m.groupValues[1].replace(",", "").replace(" ", "")
        val v = digits.toDoubleOrNull() ?: return null
        if (v <= 0.0) return null
        return (v * 100).roundToLong()
    }

    /**
     * 判定收支方向。优先级：强支出 → 强收入 → 通用收入 → 通用支出 → 默认支出。
     * 强支出放最前是为了覆盖「你发出的红包」这类同时含收入词的歧义场景；
     * 「向你转账」在强收入词中，先于通用支出词「向」命中，避免误判为支出。
     */
    fun detectType(title: String, text: String): Int {
        val expenseStrong = EXPENSE_STRONG_WORDS.any { title.contains(it) || text.contains(it) }
        if (expenseStrong) return ParsedBill.TYPE_EXPENSE

        val combined = "$title\n$text"
        val incomeStrong = INCOME_STRONG_WORDS.any { combined.contains(it) } ||
            INCOME_STRONG_RE.any { it.containsMatchIn(combined) }
        if (incomeStrong) return ParsedBill.TYPE_INCOME

        val incomeHit = INCOME_WORDS.any { combined.contains(it) }
        if (incomeHit) return ParsedBill.TYPE_INCOME

        val expenseHit = EXPENSE_WORDS.any { combined.contains(it) }
        return if (expenseHit) ParsedBill.TYPE_EXPENSE else ParsedBill.TYPE_EXPENSE
    }

    /** 提取商户名：标题 → 「XX向你转账」优先（避免「向你转账」被「向XX」抢答为「你」）→ 其余正则 → 微信按行取商户行 */
    fun extractMerchant(channel: Channel, title: String, text: String): String? {
        if (title.isNotBlank() && !isGenericTitle(title) && title.length <= 24) {
            return title
        }
        TRANSFER_FROM_RE.find(text)?.let { return it.groupValues[1].trim() }
        FROM_MERCHANT_RE.find(text)?.let { return it.groupValues[1].trim() }
        TRANSFER_TO_RE.find(text)?.let { return it.groupValues[1].trim() }
        TO_MERCHANT_RE.find(text)?.let { return it.groupValues[1].trim() }
        TRANSFER_GIVE_RE.find(text)?.let { return it.groupValues[1].trim() }
        if (channel == Channel.WECHAT) {
            val line = text.lineSequence()
                .map { it.trim() }
                .firstOrNull { isMerchantLine(it) }
            if (!line.isNullOrBlank()) return line.take(24)
        }
        return null
    }

    private fun isGenericTitle(title: String): Boolean =
        GENERIC_TITLE_WORDS.any { title.contains(it) }

    private fun isMerchantLine(line: String): Boolean {
        if (line.isEmpty() || line.length > 24) return false
        if (line.any { it.isDigit() }) return false
        if (line.contains("¥") || line.contains("￥") || line.contains("元")) return false
        if (MERCHANT_EXCLUDE_WORDS.any { line.contains(it) }) return false
        if (INCOME_WORDS.any { line.contains(it) } || EXPENSE_WORDS.any { line.contains(it) }) return false
        if (line.contains("：") || line.contains(":")) return false
        return true
    }

    /** 全角 → 半角归一化：全角数字/逗号/句点/全角空格 */
    private fun normalizeFullWidth(raw: String): String = buildString(raw.length) {
        for (c in raw) {
            append(
                when (c) {
                    in '０'..'９' -> (c - '０' + '0'.code).toChar()
                    '，' -> ','
                    '．' -> '.'
                    '\u3000' -> ' '
                    else -> c
                }
            )
        }
    }

    private val TO_MERCHANT_RE = Regex("向([\\u4e00-\\u9fa5A-Za-z0-9·]{1,24}?)(?:完成|付款|转账|支付)")
    private val FROM_MERCHANT_RE = Regex("([\\u4e00-\\u9fa5A-Za-z0-9·]{1,24}?)向您付款")
    private val TRANSFER_TO_RE = Regex("向([\\u4e00-\\u9fa5A-Za-z0-9·]{1,24}?)转账")
    private val TRANSFER_FROM_RE = Regex("([\\u4e00-\\u9fa5A-Za-z0-9·]{1,24}?)向你转账")
    private val TRANSFER_GIVE_RE = Regex("转给([\\u4e00-\\u9fa5A-Za-z0-9·]{1,24}?)")

    // ------------------------------------------------------------------
    // 短信场景入口
    // ------------------------------------------------------------------

    /** 含敏感词的短信直接拒绝（验证码/登录等与交易无关） */
    private val SMS_BLOCK_WORDS = listOf(
        "验证码", "校验码", "动态码", "登录", "密码", "登录密码", "找回密码",
        "注册", "绑定", "解绑", "退订", "回复", "积分兑换"
    )

    /** 交易收支词（余额守卫用：含「余额」且不含任何交易词 → 视为余额播报类短信，拒绝） */
    private val SMS_TRADE_WORDS =
        EXPENSE_STRONG_WORDS + EXPENSE_WORDS + INCOME_STRONG_WORDS + INCOME_WORDS +
            listOf("交易", "支付", "消费", "扣款", "到账", "收款", "退款", "转入", "转出", "支出", "收入")

    /**
     * 交易动作守卫词：真实账单通知必然命中其一（支付成功/扣款/到账/转账…），命中则放行广告词。
     * 只用强动作词——「支付」等泛词会命中微信通知标题「微信支付」，导致广告拦截失效（曾把广告全部放行）。
     */
    private val TRADE_GUARD_WORDS =
        (EXPENSE_STRONG_WORDS + INCOME_STRONG_WORDS + listOf(
            "扣款", "到账", "收款", "退款", "转入", "转出", "支出", "收入",
            "交易成功", "转账成功"
        )).distinct()

    /** 短信商户正则：覆盖「商户：XX」「商户名称：XX」格式（银行消费短信常见） */
    private val SMS_MERCHANT_RE = Regex("商户(?:名称)?[:：]([\\u4e00-\\u9fa5A-Za-z0-9·]{1,24})")

    /**
     * 银行短信特征词：命中 → 该短信账单归入「银行卡」渠道（显示/筛选/统计为银行）。
     * 仅影响渠道归类；notificationKey 仍 `sms-$sender-$ts`、rawTitle 仍 sender。
     */
    val BANK_SMS_KEYWORDS = listOf(
        "银行", "农行", "工行", "建行", "中行", "交行", "招行", "邮储", "光大",
        "中信", "民生", "浦发", "平安", "广发", "兴业", "华夏", "借记卡", "储蓄卡", "信用卡"
    )

    // ------------------------------------------------------------------
    // 自定义匹配关键词（设置页配置：系统预设词未命中时兜底放行）
    // ------------------------------------------------------------------

    /** 自定义关键词作用范围（与设置页下拉选项一致） */
    object CustomKeywordScope {
        const val SMS = "sms"
        const val NOTIFICATION = "notification"
        const val ALL = "all"
    }

    /** 一组自定义关键词：组内全部关键词都出现在内容中（AND）才算命中该组 */
    data class CustomMatchGroup(val keywords: List<String>, val scope: String)

    /**
     * 自定义组匹配：任一组的「作用范围匹配 + 组内全部关键词命中」→ true。
     * @param channelScope 当前数据源范围（短信入口传 [CustomKeywordScope.SMS]，通知入口传 NOTIFICATION）
     */
    fun matchCustomGroup(
        combined: String,
        groups: List<CustomMatchGroup>,
        channelScope: String
    ): Boolean = groups.any { g ->
        (g.scope == CustomKeywordScope.ALL || g.scope == channelScope) &&
            g.keywords.isNotEmpty() &&
            g.keywords.all { combined.contains(it) }
    }

    /**
     * 短信场景入口：任意短信正文，含合法金额 + 通过方向判定才解析。
     * 守卫（防误记）：
     * 1. [SMS_BLOCK_WORDS] 命中（验证码/登录等）直接拒绝；
     * 2. 余额守卫：正文含「余额」但无任何交易收支词（如「您的余额为 ¥950.00」）→ 拒绝，
     *    「消费10元，余额950元」含消费词 → 放行并取首个金额。
     *
     * @param sender 发件人号码（放入 rawTitle，不作为商户候选）
     * @param body   短信正文（单行拼接）
     * @param receivedAt 短信到达时间（PDU timestampMillis）
     */
    fun parseSms(
        sender: String?,
        body: String,
        receivedAt: Long = System.currentTimeMillis(),
        blockedWords: List<String> = emptyList(),
        customGroups: List<CustomMatchGroup> = emptyList()
    ): ParsedBill? = parseSmsWithReason(sender, body, receivedAt, blockedWords, customGroups).bill

    /** 带拒绝原因的短信解析入口，行为与 [parseSms] 一致。 */
    fun parseSmsWithReason(
        sender: String?,
        body: String,
        receivedAt: Long = System.currentTimeMillis(),
        blockedWords: List<String> = emptyList(),
        customGroups: List<CustomMatchGroup> = emptyList()
    ): ParseOutcome {
        val b = body.trim()
        if (b.isEmpty()) return ParseOutcome(null, ParseRejectReason.EMPTY_TEXT)
        // 硬拦截：验证码/登录/退订类与交易无关，自定义关键词不可覆盖（安全优先）
        if (SMS_BLOCK_WORDS.any { b.contains(it) }) return ParseOutcome(null, ParseRejectReason.SMS_BLOCKED_WORD)
        val customHit = matchCustomGroup(b, customGroups, CustomKeywordScope.SMS)
        // 广告门禁：营销/保险类短信（无交易动作词）拒绝；自定义组全中则视为用户确认的真实账单，放行
        if (isAdNotification(b, blockedWords) && !customHit) return ParseOutcome(null, ParseRejectReason.AD_BLOCKED)
        // 余额守卫：纯余额播报（无交易词）不记账；自定义组全中则放行
        if (b.contains("余额") && SMS_TRADE_WORDS.none { b.contains(it) } && !customHit) {
            return ParseOutcome(null, ParseRejectReason.BALANCE_ONLY_SMS)
        }

        val amountFen = extractAmountFen(b) ?: return ParseOutcome(null, ParseRejectReason.NO_AMOUNT)
        if (amountFen <= 0 || amountFen > MAX_AMOUNT_FEN) {
            return ParseOutcome(null, ParseRejectReason.AMOUNT_OUT_OF_RANGE)
        }

        val type = detectType("", b)
        val merchant = SMS_MERCHANT_RE.find(b)?.groupValues?.get(1)?.trim()
            ?: extractMerchant(Channel.SMS, "", b)
        // 短信归银行：正文含银行特征词（银行名/借记卡等）→ 渠道记「银行卡」
        val channel = if (BANK_SMS_KEYWORDS.any { b.contains(it) }) Channel.BANK else Channel.SMS

        return ParseOutcome(
            ParsedBill(
                channel = channel,
                amount = amountFen,
                type = type,
                merchant = merchant,
                rawTitle = sender?.takeIf { it.isNotBlank() },
                rawText = b,
                occurredAt = receivedAt,
                notificationKey = "sms-$sender-$receivedAt"
            )
        )
    }
}
