package com.xl.bill.mint.parser

/**
 * 记账渠道与支付类 App 包名白名单（纯 Kotlin，可在 JVM 单测）。
 */
enum class Channel(val displayName: String) {
    ALIPAY("支付宝"),
    WECHAT("微信"),
    BANK("银行卡"),
    SMS("短信")
}

object PaymentApps {

    private val ALIPAY_PKGS = setOf(
        "com.eg.android.AlipayGphone",
        "com.eg.android.AlipayGphoneRC"
    )

    private val WECHAT_PKGS = setOf(
        "com.tencent.mm",
        "com.tencent.wepay"
    )

    private val BANK_PKGS = setOf(
        "com.icbc",                              // 工商银行
        "com.chinamworld.main",                  // 建设银行
        "com.android.bankabc",                   // 农业银行
        "com.bankcomm.Bankcomm",                 // 交通银行
        "com.cmbchina.ccd.pluto.cmbActivity",    // 招商银行
        "cmb.pb",                                // 掌上生活
        "com.spdbccc.app",                       // 浦发银行
        "com.pingan.pinganwallet",               // 平安口袋银行
        "com.unionpay",                          // 云闪付
        "com.cgbchina.xpt",                      // 广发
        "com.citiccard.mobilebank",              // 中信
        "com.evergrande.wubaibank",              // 恒丰? 保留通用银行壳
        "com.boc.bocrmb"                         // 中国银行
    )

    fun channelOf(pkg: String): Channel? = when {
        pkg in ALIPAY_PKGS -> Channel.ALIPAY
        pkg in WECHAT_PKGS -> Channel.WECHAT
        pkg in BANK_PKGS -> Channel.BANK
        else -> null
    }

    fun isSupported(pkg: String): Boolean = channelOf(pkg) != null

    /** 设置页展示的记账渠道 */
    val ALL_CHANNELS: List<Channel> = listOf(Channel.ALIPAY, Channel.WECHAT, Channel.BANK, Channel.SMS)
}
