package com.cardmanager.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 解析卡片管家网页版导出的 JSON 文件。
 *
 * 导出格式（两种都支持）：
 *
 * 格式A（全量备份，网页版 exportData()）：
 * { "exportTime":"...", "appVersion":"...", "data": { D }, "userState": { US } }
 *   D.groups = [ { id, name, icon } ]
 *   D.cards  = [ { id, bank, tail, network, currency, role, note, groupId, noCard, status? } ]
 *   D.monthlyTasks / weeklyTasks / quarterlyTasks / ndaysTasks / onceTasks
 *   US.cc = { "cardId": { "status": "active/frozen/pending/cancelled" } }
 *   US.pig = [ { id, amount, desc, cardId, date, ts } ]
 *
 * 格式B（本 App 导出）：
 * { "groups":[...], "cards":[...], "monthlyTasks":[...], ..., "pig":[...] }
 */
object PwaImporter {

    data class ImportResult(
        val groups: List<CardGroup>,
        val cards: List<Card>,
        val tasks: List<Task>,
        val piggy: List<PiggyEntry>,
        val groupCount: Int = 0,
        val cardCount: Int = 0,
        val taskCount: Int = 0,
        val piggyCount: Int = 0
    )

    fun parse(rawJson: String, ctx: Context? = null): ImportResult {
        val root = JSONObject(rawJson)

        // ── 判断格式 A 还是 B ─────────────────────────────────
        val isFormatA = root.has("data") && root.get("data") is JSONObject
        val data: JSONObject = if (isFormatA) root.getJSONObject("data") else root
        val userState: JSONObject? = if (isFormatA && root.has("userState"))
            root.getJSONObject("userState") else null

        // ── 卡片状态表（格式A：从 userState.cc 读）────────────
        // cc = { "cardId": { "status": "..." } }
        val statusMap = mutableMapOf<String, String>()
        userState?.optJSONObject("cc")?.let { cc ->
            cc.keys().forEach { key ->
                val st = cc.optJSONObject(key)?.optString("status", "") ?: ""
                if (st.isNotEmpty()) statusMap[key] = st
            }
        }

        // ── Groups ────────────────────────────────────────────
        val groups = mutableListOf<CardGroup>()
        val seenGroupIds = mutableSetOf<String>()
        data.optJSONArray("groups")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id").ifEmpty { "g-${UUID.randomUUID()}" }
                if (seenGroupIds.contains(id)) continue
                seenGroupIds.add(id)
                groups.add(CardGroup(
                    id        = id,
                    name      = o.optString("name", "未命名分组"),
                    icon      = o.optString("icon", "💳"),
                    isOpen    = true,
                    sortOrder = o.optInt("order", i)
                ))
            }
        }

        // 如果一个分组都没有，创建默认分组
        val defaultGroupId: String
        if (groups.isEmpty()) {
            defaultGroupId = "g-default"
            groups.add(CardGroup(id = defaultGroupId, name = "我的卡片", icon = "💳"))
        } else {
            defaultGroupId = groups[0].id
        }

        // ── Cards ─────────────────────────────────────────────
        val cards = mutableListOf<Card>()
        data.optJSONArray("cards")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id").ifEmpty { "c-${UUID.randomUUID()}" }

                // 分组 ID（网页版用 groupId）
                val gid = o.optString("groupId").ifEmpty { o.optString("gid").ifEmpty { defaultGroupId } }
                val resolvedGid = if (seenGroupIds.contains(gid)) gid else defaultGroupId

                // 状态：优先 userState.cc，其次卡片本身的 status 字段
                val rawStatus = statusMap[id]
                    ?: o.optString("status", "active")
                val status = when (rawStatus) {
                    "active", "frozen", "pending", "cancelled" -> rawStatus
                    else -> "active"
                }

                // 网络（老版本可能有"Visa美元"等特殊值）
                val rawNetwork = if (o.isNull("network")) null else o.optString("network", "银联")
                val noCard = o.optBoolean("noCard", rawNetwork == null)
                val network = when {
                    rawNetwork == null -> "银联"
                    rawNetwork.contains("Visa") -> "Visa"
                    rawNetwork.contains("Master") -> "Mastercard"
                    rawNetwork.contains("银联") -> "银联"
                    rawNetwork.contains("AMEX") || rawNetwork.contains("运通") -> "AMEX"
                    rawNetwork.contains("JCB") -> "JCB"
                    else -> rawNetwork.ifEmpty { "银联" }
                }

                // 尝试从 userState.cc[id].icon 提取 base64 图片
                val b64Icon = statusMap.entries
                    .firstOrNull { false }?.value ?: ""  // placeholder, handled below
                val iconB64 = userState?.optJSONObject("cc")
                    ?.optJSONObject(id)?.optString("icon", "") ?: ""
                val imagePath = if (ctx != null && iconB64.isNotEmpty())
                    ImageStore.saveFromBase64(ctx, iconB64, id) else ""

                cards.add(Card(
                    id            = id,
                    groupId       = resolvedGid,
                    bank          = o.optString("bank", "未知银行"),
                    network       = network,
                    currency      = o.optString("currency", "CNY").ifEmpty { "CNY" },
                    tail          = o.optString("tail", ""),
                    role          = o.optString("role", ""),
                    note          = o.optString("note", ""),
                    status        = status,
                    isVirtual     = o.optBoolean("isVirtual", false),
                    noCard        = noCard,
                    logoEmoji     = o.optString("logo", "").ifEmpty { o.optString("logoEmoji", "") },
                    logoImagePath = imagePath,
                    sortOrder     = i,
                    imageOrientation = if (o.optString("imageOrientation", "horizontal") == "vertical") "vertical" else "horizontal",
                    creditLimit   = o.optDouble("creditLimit", 0.0).coerceAtLeast(0.0),
                    creditLimitsJson = o.optString("creditLimitsJson", ""),
                    billingDay    = o.optInt("billingDay", 0).takeIf { it in 1..31 } ?: 0,
                    repaymentDay  = o.optInt("repaymentDay", 0).takeIf { it in 1..31 } ?: 0,
                    sharedCreditLimitGroupId = o.optString("sharedCreditLimitGroupId", "").trim(),
                    sharedCreditLimitCurrency = if (
                        o.optString("sharedCreditLimitGroupId", "").isBlank()
                    ) "" else o.optString(
                        "sharedCreditLimitCurrency",
                        o.optString("currency", "CNY")
                    ).trim()
                ))
            }
        }

        // ── Tasks ─────────────────────────────────────────────
        val tasks = mutableListOf<Task>()

        fun monthsStr(o: JSONObject): String {
            return when {
                o.has("months") && o.get("months") is JSONArray -> {
                    val ma = o.getJSONArray("months")
                    (0 until ma.length()).map { ma.getInt(it) }.joinToString(",")
                }
                o.has("months") -> o.optString("months", "")
                else -> ""
            }
        }

        fun parseTask(o: JSONObject, freq: String) {
            tasks.add(Task(
                id        = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                name      = o.optString("name", "未命名任务"),
                freq      = freq,
                cardId    = o.optString("cardId", ""),
                isInvest  = o.optBoolean("isInvest", false),
                investAmount = o.optDouble("investAmount", 0.0),
                day       = o.optInt("day", 1),
                weekday   = o.optInt("weekday", 1),
                months    = monthsStr(o),
                ndays     = o.optInt("ndays", 7).coerceAtLeast(1),
                startDate = o.optString("startDate", ""),
                date      = o.optString("date", ""),
                holidays  = o.optString("holidays", "")
            ))
        }

        data.optJSONArray("monthlyTasks")?.let   { for (i in 0 until it.length()) parseTask(it.getJSONObject(i), "monthly") }
        data.optJSONArray("weeklyTasks")?.let    { for (i in 0 until it.length()) parseTask(it.getJSONObject(i), "weekly") }
        data.optJSONArray("quarterlyTasks")?.let { for (i in 0 until it.length()) parseTask(it.getJSONObject(i), "quarterly") }
        data.optJSONArray("ndaysTasks")?.let     { for (i in 0 until it.length()) parseTask(it.getJSONObject(i), "ndays") }
        data.optJSONArray("onceTasks")?.let      { for (i in 0 until it.length()) parseTask(it.getJSONObject(i), "once") }
        // 兼容 app 导出格式（"tasks" 数组）
        data.optJSONArray("tasks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                parseTask(o, o.optString("freq", "monthly"))
            }
        }

        // ── Piggy ─────────────────────────────────────────────
        // 格式A：userState.pig；格式B：data.pig
        val piggySource: JSONArray? =
            userState?.optJSONArray("pig") ?: data.optJSONArray("pig")
        val piggy = mutableListOf<PiggyEntry>()
        piggySource?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                piggy.add(PiggyEntry(
                    id        = o.optLong("id", System.currentTimeMillis() + i),
                    amount    = o.optDouble("amount", 0.0),
                    desc      = o.optString("desc", ""),
                    cardId    = o.optString("cardId", ""),
                    date      = o.optString("date", ""),
                    timestamp = o.optLong("ts", System.currentTimeMillis())
                ))
            }
        }

        return ImportResult(
            groups = groups, cards = cards, tasks = tasks, piggy = piggy,
            groupCount = groups.size, cardCount = cards.size,
            taskCount = tasks.size, piggyCount = piggy.size
        )
    }
}
