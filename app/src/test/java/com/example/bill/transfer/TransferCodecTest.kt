package com.xl.bill.mint.transfer

import com.xl.bill.mint.data.db.AccountEntity
import com.xl.bill.mint.data.db.CategoryEntity
import com.xl.bill.mint.data.db.SettingEntity
import com.xl.bill.mint.data.db.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * TransferCodec 纯 JVM 单测：
 * 四表 round-trip、中文/emoji/null 字段、非法输入、版本不兼容、
 * id 引用一致性、万级流水性能。
 */
class TransferCodecTest {

    private fun sampleSnapshot(): DbSnapshot = DbSnapshot(
        formatVersion = TransferCodec.FORMAT_VERSION,
        exportedAt = 1754300000000L,
        appVersion = "1.0.0",
        deviceInfo = DeviceInfo(model = "Pixel 7", sdk = 35),
        categories = listOf(
            CategoryEntity(id = 1, name = "餐饮", icon = "🍜", type = 0, keywords = "外卖,餐厅", sort = 1),
            CategoryEntity(id = 2, name = "工资", icon = "💰", type = 1, keywords = "", sort = 0, isCustom = true)
        ),
        accounts = listOf(
            AccountEntity(id = 1, name = "支付宝", packageName = "com.eg.android.AlipayGphone"),
            AccountEntity(id = 2, name = "手动记账", packageName = null)
        ),
        transactions = listOf(
            TransactionEntity(
                id = 1, channel = "wechat", rawTitle = "微信支付凭证", rawText = null,
                amount = 2350, type = 0, categoryId = 1, accountId = 1,
                merchant = "瑞幸咖啡(深圳湾店)", occurredAt = 1754200000000L,
                notificationKey = "wechat|123", note = "拿铁 😋", createdAt = 1754200001000L
            ),
            TransactionEntity(
                id = 2, channel = "manual", rawTitle = null, rawText = null,
                amount = 2000000, type = 1, categoryId = 2, accountId = 2,
                merchant = null, occurredAt = 1754100000000L,
                notificationKey = null, note = null, createdAt = 1754100001000L
            )
        ),
        settings = listOf(
            SettingEntity(key = "channel_on_wechat", value = "true"),
            SettingEntity(key = "channel_on_alipay", value = "false")
        ),
        preferences = mapOf(
            "auto_record_enabled" to true,
            "first_launch_done" to true,
            "transfer_hint_enabled" to false,
            "app_lock_enabled" to true
        )
    )

    @Test
    fun roundTripPreservesAllFields() {
        val json = TransferCodec.encode(sampleSnapshot())
        val decoded = TransferCodec.decode(json)

        assertEquals(TransferCodec.FORMAT_VERSION, decoded.formatVersion)
        assertEquals(1754300000000L, decoded.exportedAt)
        assertEquals("1.0.0", decoded.appVersion)
        assertEquals(DeviceInfo("Pixel 7", 35), decoded.deviceInfo)

        assertEquals(sampleSnapshot().categories, decoded.categories)
        assertEquals(sampleSnapshot().accounts, decoded.accounts)
        assertEquals(sampleSnapshot().transactions, decoded.transactions)
        assertEquals(sampleSnapshot().settings, decoded.settings)
        assertEquals(sampleSnapshot().preferences, decoded.preferences)
    }

    @Test
    fun preferencesRoundTripPreservesFlags() {
        val snap = sampleSnapshot().copy(
            preferences = mapOf("auto_record_enabled" to false, "app_lock_enabled" to true)
        )
        val decoded = TransferCodec.decode(TransferCodec.encode(snap))
        assertEquals(snap.preferences, decoded.preferences)
    }

    @Test
    fun legacyV1WithoutPreferencesDecodesEmpty() {
        // 旧版蓝牙传输文件（formatVersion=1）没有 preferences 字段：解码为空 map，不抛错
        val json = """{"formatVersion":1,"exportedAt":0,"appVersion":"","categories":[],"accounts":[],"transactions":[],"settings":[]}"""
        val decoded = TransferCodec.decode(json)
        assertTrue(decoded.preferences.isEmpty())
    }

    @Test
    fun v2DecodesPreferences() {
        val json = """{"formatVersion":2,"exportedAt":0,"appVersion":"","categories":[],"accounts":[],"transactions":[],"settings":[],
            "preferences":{"auto_record_enabled":true,"app_lock_enabled":false}}"""
        val decoded = TransferCodec.decode(json)
        assertEquals(
            mapOf("auto_record_enabled" to true, "app_lock_enabled" to false),
            decoded.preferences
        )
    }

    @Test
    fun decodeLegacyCategoryWithoutIsCustomDefaultsToPreset() {
        // 旧版导出文件没有 isCustom 字段：应解码为 false（预置），保证向后兼容
        val json = """{"formatVersion":1,"exportedAt":0,"appVersion":"","accounts":[],"transactions":[],
            "categories":[{"id":1,"name":"餐饮","icon":"🍜","type":0,"keywords":"外卖","sort":0}],
            "settings":[]}"""
        val decoded = TransferCodec.decode(json)
        val cat = decoded.categories.single()
        assertEquals(1L, cat.id)
        assertEquals(false, cat.isCustom)
    }

    @Test
    fun roundTripEmptyTables() {
        val empty = DbSnapshot(
            formatVersion = TransferCodec.FORMAT_VERSION,
            exportedAt = 0L, appVersion = "", deviceInfo = null,
            categories = emptyList(), accounts = emptyList(),
            transactions = emptyList(), settings = emptyList()
        )
        val decoded = TransferCodec.decode(TransferCodec.encode(empty))
        assertTrue(decoded.categories.isEmpty())
        assertTrue(decoded.accounts.isEmpty())
        assertTrue(decoded.transactions.isEmpty())
        assertTrue(decoded.settings.isEmpty())
        assertNull(decoded.deviceInfo)
    }

    @Test
    fun roundTripUnicodeAndEmoji() {
        val tx = TransactionEntity(
            id = 9, channel = "bank", rawTitle = "银行转账-中国银行",
            rawText = "您尾号1234的账户于08月01日09:30转入¥1,234.56",
            amount = 123456, type = 1, categoryId = 2, accountId = 3,
            merchant = "🏦 中国银行", occurredAt = 1754000000000L,
            notificationKey = "bank|test-特殊字符 ～！@#", note = "备注：💸 测试\n换行",
            createdAt = 1754000001000L
        )
        val decoded = TransferCodec.decode(
            TransferCodec.encode(
                sampleSnapshot().copy(transactions = listOf(tx))
            )
        )
        assertEquals(tx, decoded.transactions[0])
    }

    @Test
    fun decodeInvalidJsonThrows() {
        try {
            TransferCodec.decode("not json at all {")
            fail("should throw")
        } catch (e: TransferException) {
            assertTrue(e.message!!.contains("有效"))
        }
    }

    @Test
    fun decodeWrongFormatVersionThrows() {
        // FORMAT_VERSION 已升到 2，且 v1/v2 均被接受，故用不支持的版本号验证拒绝逻辑
        val json = TransferCodec.encode(sampleSnapshot())
            .replace("\"formatVersion\":2", "\"formatVersion\":99")
        try {
            TransferCodec.decode(json)
            fail("should throw")
        } catch (e: TransferException) {
            assertTrue(e.message!!.contains("版本不兼容"))
        }
    }

    @Test
    fun v2FormatVersionIsAccepted() {
        val json = TransferCodec.encode(sampleSnapshot())
        val decoded = TransferCodec.decode(json)
        assertEquals(TransferCodec.FORMAT_VERSION, decoded.formatVersion)
    }

    @Test
    fun decodeMissingRequiredTransactionFieldThrows() {
        // 手写 JSON：流水缺少 amount 必填字段（避免 replace 产生重复键，org.json 对重复键会抛 Duplicate key）
        val json = """
            {"formatVersion":1,"exportedAt":0,"appVersion":"","categories":[],"accounts":[],
             "transactions":[{"id":1,"channel":"manual","type":0,"categoryId":1,"accountId":1,"occurredAt":1754000000000}],
             "settings":[]}
        """.trimIndent()
        try {
            TransferCodec.decode(json)
            fail("should throw")
        } catch (e: TransferException) {
            assertTrue(e.message!!.contains("缺少必填字段"))
        }
    }

    @Test
    fun decodeDuplicateKeyRejected() {
        // org.json 20240303 对重复键抛 Duplicate key，解码器应将其视为无效文件
        val json = """{"formatVersion":1,"exportedAt":0,"appVersion":"","categories":[],"accounts":[],
            "transactions":[{"id":1,"channel":"manual","amount":100,"type":0,"type":0,"categoryId":1,"accountId":1,"occurredAt":1}],
            "settings":[]}"""
        try {
            TransferCodec.decode(json)
            fail("should throw")
        } catch (e: TransferException) {
            assertTrue(e.message!!.contains("有效"))
        }
    }

    @Test
    fun missingOptionalArraysDecodeAsEmpty() {
        val json = """{"formatVersion":1,"exportedAt":0,"appVersion":"","transactions":[]}"""
        val decoded = TransferCodec.decode(json)
        assertTrue(decoded.categories.isEmpty())
        assertTrue(decoded.accounts.isEmpty())
        assertTrue(decoded.settings.isEmpty())
        assertTrue(decoded.transactions.isEmpty())
    }

    @Test
    fun referencesPreservedAfterRoundTrip() {
        val decoded = TransferCodec.decode(TransferCodec.encode(sampleSnapshot()))
        val tx = decoded.transactions[0]
        val categoryIds = decoded.categories.map { it.id }
        val accountIds = decoded.accounts.map { it.id }
        assertTrue(tx.categoryId in categoryIds)
        assertTrue(tx.accountId in accountIds)
    }

    @Test
    fun performanceTenThousandTransactions() {
        val big = sampleSnapshot().copy(
            transactions = (1..10_000).map { i ->
                TransactionEntity(
                    id = i.toLong(), channel = "manual", amount = i * 10L, type = 0,
                    categoryId = 1, accountId = 1, occurredAt = 1754000000000L + i,
                    notificationKey = "perf|$i", createdAt = 1754000000000L + i
                )
            }
        )
        val start = System.currentTimeMillis()
        val json = TransferCodec.encode(big)
        val decoded = TransferCodec.decode(json)
        val elapsed = System.currentTimeMillis() - start
        assertEquals(10_000, decoded.transactions.size)
        assertTrue("万级流水往返应 < 2s，实际 ${elapsed}ms", elapsed < 2000)
    }
}
