package com.xl.bill.mint.transfer

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 导出快照：四张表的完整数据 + 元数据。
 * 纯 Kotlin 数据类（依赖 data.db 中的纯实体类），可 JVM 单测。
 */
data class DbSnapshot(
    val formatVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val deviceInfo: DeviceInfo?,
    val categories: List<com.xl.bill.mint.data.db.CategoryEntity>,
    val accounts: List<com.xl.bill.mint.data.db.AccountEntity>,
    val transactions: List<com.xl.bill.mint.data.db.TransactionEntity>,
    val settings: List<com.xl.bill.mint.data.db.SettingEntity>,
    /** DataStore 预置项（自动记账/首启/转账提示/应用锁）；v1 文件无此字段，解码为空 map */
    val preferences: Map<String, Boolean> = emptyMap()
)

/** 导出设备信息（仅用于展示/排查） */
data class DeviceInfo(
    val model: String,
    val sdk: Int
)

/** 导入/导出异常；message 为用户可读信息 */
class TransferException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * JSON 编解码器（formatVersion = 1）。
 *
 * 序列化规则：
 * - null 字段直接省略（不写 key），解码时缺失视为 null；
 * - 必填字段（如 transactions 的 amount/type/categoryId/accountId/occurredAt/channel）
 *   缺失时抛 [TransferException]，避免静默导入脏数据；
 * - 顶层 formatVersion 不兼容时直接拒绝。
 */
object TransferCodec {

    const val FORMAT_VERSION = 2

    // v1 为旧蓝牙传输文件（不含 preferences），仍允许导入；更低/更高版本拒绝
    private val ACCEPTED_VERSIONS = setOf(1, FORMAT_VERSION)

    // ---------- 编码 ----------

    fun encode(snapshot: DbSnapshot): String {
        val root = JSONObject()
        root.put("formatVersion", snapshot.formatVersion)
        root.put("exportedAt", snapshot.exportedAt)
        root.put("appVersion", snapshot.appVersion)
        snapshot.deviceInfo?.let {
            root.put("deviceInfo", JSONObject().apply {
                put("model", it.model)
                put("sdk", it.sdk)
            })
        }
        root.put("categories", encodeCategories(snapshot.categories))
        root.put("accounts", encodeAccounts(snapshot.accounts))
        root.put("transactions", encodeTransactions(snapshot.transactions))
        root.put("settings", encodeSettings(snapshot.settings))
        root.put("preferences", encodePreferences(snapshot.preferences))
        return root.toString()
    }

    private fun encodePreferences(map: Map<String, Boolean>): JSONObject =
        JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }

    private fun encodeCategories(list: List<com.xl.bill.mint.data.db.CategoryEntity>): JSONArray =
        JSONArray().apply {
            list.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("icon", c.icon)
                    put("type", c.type)
                    put("keywords", c.keywords)
                    put("sort", c.sort)
                    put("isCustom", c.isCustom)
                })
            }
        }

    private fun encodeAccounts(list: List<com.xl.bill.mint.data.db.AccountEntity>): JSONArray =
        JSONArray().apply {
            list.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("name", a.name)
                    a.packageName?.let { put("packageName", it) }
                })
            }
        }

    private fun encodeTransactions(list: List<com.xl.bill.mint.data.db.TransactionEntity>): JSONArray =
        JSONArray().apply {
            list.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("channel", t.channel)
                    t.rawTitle?.let { put("rawTitle", it) }
                    t.rawText?.let { put("rawText", it) }
                    put("amount", t.amount)
                    put("type", t.type)
                    put("categoryId", t.categoryId)
                    put("accountId", t.accountId)
                    t.merchant?.let { put("merchant", it) }
                    put("occurredAt", t.occurredAt)
                    t.notificationKey?.let { put("notificationKey", it) }
                    t.note?.let { put("note", it) }
                    put("createdAt", t.createdAt)
                    put("source", t.source)
                })
            }
        }

    private fun encodeSettings(list: List<com.xl.bill.mint.data.db.SettingEntity>): JSONArray =
        JSONArray().apply {
            list.forEach { s ->
                put(JSONObject().apply {
                    put("key", s.key)
                    put("value", s.value)
                })
            }
        }

    // ---------- 解码 ----------

    fun decode(json: String): DbSnapshot {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw TransferException("文件不是有效的 MintBill 导出数据", e)
        }

        val formatVersion = root.optInt("formatVersion", -1)
        if (formatVersion !in ACCEPTED_VERSIONS) {
            throw TransferException(
                "导出数据版本不兼容（当前版本 $FORMAT_VERSION，文件版本 $formatVersion），请升级 App 后重试"
            )
        }

        val exportedAt = root.optLong("exportedAt", 0L)
        val appVersion = root.optString("appVersion", "")
        val deviceInfo = root.optJSONObject("deviceInfo")?.let {
            DeviceInfo(
                model = it.optString("model", ""),
                sdk = it.optInt("sdk", 0)
            )
        }

        return DbSnapshot(
            formatVersion = formatVersion,
            exportedAt = exportedAt,
            appVersion = appVersion,
            deviceInfo = deviceInfo,
            categories = decodeCategories(root.optJSONArray("categories") ?: JSONArray()),
            accounts = decodeAccounts(root.optJSONArray("accounts") ?: JSONArray()),
            transactions = decodeTransactions(root.optJSONArray("transactions") ?: JSONArray()),
            settings = decodeSettings(root.optJSONArray("settings") ?: JSONArray()),
            preferences = decodePreferences(root.optJSONObject("preferences"))
        )
    }

    private fun decodePreferences(obj: JSONObject?): Map<String, Boolean> {
        if (obj == null) return emptyMap()
        val map = mutableMapOf<String, Boolean>()
        obj.keys().forEach { key -> map[key] = obj.optBoolean(key, false) }
        return map
    }

    private fun decodeCategories(array: JSONArray): List<com.xl.bill.mint.data.db.CategoryEntity> =
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            _root_ide_package_.com.xl.bill.mint.data.db.CategoryEntity(
                id = o.optLong("id", 0L),
                name = o.getString("name"),
                icon = o.optString("icon", ""),
                type = o.optInt("type", 0),
                keywords = o.optString("keywords", ""),
                sort = o.optInt("sort", 0),
                isCustom = o.optBoolean("isCustom", false) // 旧导出文件无此字段时默认预置
            )
        }

    private fun decodeAccounts(array: JSONArray): List<com.xl.bill.mint.data.db.AccountEntity> =
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            _root_ide_package_.com.xl.bill.mint.data.db.AccountEntity(
                id = o.optLong("id", 0L),
                name = o.getString("name"),
                packageName = o.optNullableString("packageName")
            )
        }

    private fun decodeTransactions(array: JSONArray): List<com.xl.bill.mint.data.db.TransactionEntity> =
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            // 必填字段：缺失说明文件损坏或格式不符，直接拒绝
            if (!o.has("channel") || !o.has("amount") || !o.has("type") ||
                !o.has("categoryId") || !o.has("accountId") || !o.has("occurredAt")
            ) {
                throw TransferException("导出数据缺少必填字段（第 $i 条流水），导入已中止")
            }
            _root_ide_package_.com.xl.bill.mint.data.db.TransactionEntity(
                id = o.optLong("id", 0L),
                channel = o.getString("channel"),
                rawTitle = o.optNullableString("rawTitle"),
                rawText = o.optNullableString("rawText"),
                amount = o.getLong("amount"),
                type = o.getInt("type"),
                categoryId = o.getLong("categoryId"),
                accountId = o.getLong("accountId"),
                merchant = o.optNullableString("merchant"),
                occurredAt = o.getLong("occurredAt"),
                notificationKey = o.optNullableString("notificationKey"),
                note = o.optNullableString("note"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                source = o.optString("source", "notification") // v2 旧文件缺省回退
            )
        }

    private fun decodeSettings(array: JSONArray): List<com.xl.bill.mint.data.db.SettingEntity> =
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            _root_ide_package_.com.xl.bill.mint.data.db.SettingEntity(
                key = o.getString("key"),
                value = o.optString("value", "")
            )
        }

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}
