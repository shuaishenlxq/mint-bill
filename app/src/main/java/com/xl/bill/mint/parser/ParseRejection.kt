package com.xl.bill.mint.parser

/**
 * 解析拒绝原因（诊断日志用）：与 BillParseEngine 各拒绝分支一一对应。
 * 纯 Kotlin，JVM 可单测。
 */
enum class ParseRejectReason {
    /** 包名不在支付类白名单（channelOf = null） */
    UNSUPPORTED_PACKAGE,

    /** 标题与正文全空 */
    EMPTY_TEXT,

    /** 命中广告/营销过滤词且无交易动作守卫词 */
    AD_BLOCKED,

    /** 无合法金额 */
    NO_AMOUNT,

    /** 金额越界（<=0 或 > 1 亿元） */
    AMOUNT_OUT_OF_RANGE,

    /** 无障碍场景：页面文本未命中转账/支付动作词 */
    NOT_TRADE_SCENE,

    /** 无障碍场景：余额/汇总类页面（非单笔交易） */
    BALANCE_PAGE,

    /** 短信：命中硬拦截词（验证码/登录/退订等） */
    SMS_BLOCKED_WORD,

    /** 短信：含「余额」但无任何交易收支词（余额播报类） */
    BALANCE_ONLY_SMS
}

/**
 * 带拒绝原因的解析结果。[bill] 非空即解析成功（此时 reason 为 null）；
 * 解析失败时 bill=null 且 reason 指明具体拒绝分支。
 */
data class ParseOutcome(
    val bill: ParsedBill?,
    val reason: ParseRejectReason? = null
)
