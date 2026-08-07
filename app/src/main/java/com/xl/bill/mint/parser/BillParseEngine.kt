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
        "付款成功", "支付成功", "发出"
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

    private data class MoneyCandidate(val amountFen: Long, val lineIndex: Int)

    // ------------------------------------------------------------------
    // 通知场景入口
    // ------------------------------------------------------------------

    fun parse(
        pkg: String,
        title: String?,
        text: String?,
        occurredAt: Long = System.currentTimeMillis(),
        notificationKey: String? = null
    ): ParsedBill? {
        val channel = PaymentApps.channelOf(pkg) ?: return null
        val t = title?.trim().orEmpty()
        val b = text?.trim().orEmpty()
        if (t.isEmpty() && b.isEmpty()) return null

        val amountFen = extractAmountFen("$t\n$b") ?: return null
        if (amountFen <= 0 || amountFen > MAX_AMOUNT_FEN) return null

        val type = detectType(t, b)
        val merchant = extractMerchant(channel, t, b)

        return ParsedBill(
            channel = channel,
            amount = amountFen,
            type = type,
            merchant = merchant,
            rawTitle = t.ifEmpty { null },
            rawText = b.ifEmpty { null },
            occurredAt = occurredAt,
            notificationKey = notificationKey
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
    ): ParsedBill? {
        val channel = PaymentApps.channelOf(pkg) ?: return null
        val b = text?.trim().orEmpty()
        if (b.isEmpty() || !isTransferScene(b)) return null
        // 余额/汇总类页面（零钱余额、全部账单、浮窗等）整页金额非单笔交易，直接放弃
        if (BALANCE_PAGE_WORDS.any { b.contains(it) }) return null

        val lines = b.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val amountFen = extractSceneAmount(lines) ?: return null
        if (amountFen <= 0 || amountFen > MAX_AMOUNT_FEN) return null

        val type = detectType("", b)
        val merchant = extractMerchant(channel, "", b)

        return ParsedBill(
            channel = channel,
            amount = amountFen,
            type = type,
            merchant = merchant,
            rawTitle = null,
            rawText = b.ifEmpty { null },
            occurredAt = occurredAt,
            notificationKey = notificationKey
        )
    }

    /** 场景门禁：页面文本是否命中转账/支付动作词 */
    fun isTransferScene(text: String?): Boolean {
        val s = text ?: return false
        return TRANSFER_SCENE_WORDS.any { s.contains(it) } ||
            INCOME_STRONG_RE.any { it.containsMatchIn(s) }
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

    /** 提取金额（元）→ 分；先做全角归一化，再匹配 ¥/￥ 前缀与「元」后缀，去除千分位逗号与空格 */
    fun extractAmountFen(raw: String): Long? {
        val normalized = normalizeFullWidth(raw)
        val m = AMOUNT_SYMBOL_RE.find(normalized) ?: AMOUNT_YUAN_RE.find(normalized) ?: return null
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

    private val TO_MERCHANT_RE = Regex("向([\\u4e00-\\u9fa5A-Za-z0-9·]{1,24}?)(?:付款|转账|支付)")
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

    /** 短信商户正则：覆盖「商户：XX」「商户名称：XX」格式（银行消费短信常见） */
    private val SMS_MERCHANT_RE = Regex("商户(?:名称)?[:：]([\\u4e00-\\u9fa5A-Za-z0-9·]{1,24})")

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
        receivedAt: Long = System.currentTimeMillis()
    ): ParsedBill? {
        val b = body.trim()
        if (b.isEmpty()) return null
        if (SMS_BLOCK_WORDS.any { b.contains(it) }) return null
        // 余额守卫：纯余额播报（无交易词）不记账
        if (b.contains("余额") && SMS_TRADE_WORDS.none { b.contains(it) }) return null

        val amountFen = extractAmountFen(b) ?: return null
        if (amountFen <= 0 || amountFen > MAX_AMOUNT_FEN) return null

        val type = detectType("", b)
        val merchant = SMS_MERCHANT_RE.find(b)?.groupValues?.get(1)?.trim()
            ?: extractMerchant(Channel.SMS, "", b)

        return ParsedBill(
            channel = Channel.SMS,
            amount = amountFen,
            type = type,
            merchant = merchant,
            rawTitle = sender?.takeIf { it.isNotBlank() },
            rawText = b,
            occurredAt = receivedAt,
            notificationKey = "sms-$sender-$receivedAt"
        )
    }
}
