package com.xl.bill.mint.service

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 无障碍服务：自动记账的【兜底数据源】。
 *
 * 覆盖「微信给个人转账」这类通知无金额的场景——用户点开转账/红包详情页时，
 * 页面文本包含金额与动作词（请收款/已收钱/转账成功等），本服务读取后自动记账。
 *
 * 捕获策略（防误记/防重复）：
 * 1. 场景门禁：页面文本必须命中转账/支付动作词（isTransferScene），聊天页滚动不记账；
 * 2. 包名级 1500ms 短节流：仅抗事件风暴；
 * 3. 页面签名 30s 节流：同页（金额+动作词特征相同）重渲染只处理一次，防跨分钟重复；
 * 4. 金额多值策略在解析层完成（动作词就近选择）；
 * 5. 结果走统一管线（指纹去重 + key 去重 + DB 唯一索引）。
 */
class BillAccessibilityService : android.accessibilityservice.AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 包名 → 上次处理时间（短节流） */
    private val lastProcessedAt = HashMap<String, Long>()

    /** 页面签名 → 上次处理时间（30s 长节流） */
    private val lastPageSignatureAt = HashMap<Int, Long>()

    private val pkgThrottleMs = 1_500L
    private val pageThrottleMs = 30_000L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (!_root_ide_package_.com.xl.bill.mint.parser.PaymentApps.isSupported(pkg)) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        val now = System.currentTimeMillis()

        // 1) 包名级短节流：抗高频事件风暴
        val lastPkg = lastProcessedAt[pkg] ?: 0L
        if (now - lastPkg < pkgThrottleMs) return
        lastProcessedAt[pkg] = now

        val root = rootInActiveWindow ?: return
        val texts = ArrayList<String>(32)
        collectTexts(root, texts)
        root.recycle()

        val joined = texts.joinToString("\n")

        // 2) 场景门禁：无转账/支付动作词直接放弃（聊天页滚动不记账）
        if (!_root_ide_package_.com.xl.bill.mint.parser.BillParseEngine.isTransferScene(joined)) return

        // 3) 页面签名节流：同页重渲染（含跨分钟）只处理一次
        val signature = pageSignature(joined)
        val lastSig = lastPageSignatureAt[signature] ?: 0L
        if (now - lastSig < pageThrottleMs) return
        lastPageSignatureAt[signature] = now
        if (lastPageSignatureAt.size > 64) lastPageSignatureAt.clear()

        Log.d(TAG, "无障碍命中转账场景 pkg=$pkg 文本=$joined")
        scope.launch {
            BillRecordPipeline.processAccessibility(
                pkg = pkg,
                text = joined,
                occurredAt = now,
                notificationKey = "acc-$pkg-$now"
            )
        }
    }

    /** 稳定签名：仅取「金额行 + 场景词行」，排除时间戳等动态内容 */
    private fun pageSignature(joined: String): Int {
        val relevant = joined.lineSequence()
            .map { it.trim() }
            .filter {
                it.contains('¥') || it.contains('￥') || it.contains('元') ||
                    _root_ide_package_.com.xl.bill.mint.parser.BillParseEngine.isTransferScene(it)
            }
            .joinToString("|")
        return relevant.hashCode()
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) out.add(text)
        val contentDesc = node.contentDescription?.toString()
        if (!contentDesc.isNullOrBlank()) out.add(contentDesc)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTexts(child, out)
            child.recycle()
        }
    }

    override fun onInterrupt() {
        // 无需处理
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "MintBill"
    }
}
