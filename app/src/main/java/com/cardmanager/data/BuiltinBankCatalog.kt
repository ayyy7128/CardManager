package com.cardmanager.data

import android.content.Context
import org.json.JSONObject
import java.text.Collator
import java.util.Locale

data class BuiltinBank(
    val name: String,
    val english: String,
    val section: String,
    val sortKey: String,
    val logoAsset: String,
    val aliases: List<String>
) {
    val searchText: String = buildList {
        add(name)
        add(english)
        addAll(aliases)
    }.joinToString(" ").lowercase()
}

object BuiltinBankCatalog {
    private const val CATALOG_ASSET = "banks/catalog.json"

    @Volatile
    private var cachedBanks: List<BuiltinBank>? = null

    private val commonAliases = mapOf(
        "工商银行" to "中国工商银行",
        "工商" to "中国工商银行",
        "工行" to "中国工商银行",
        "建设银行" to "中国建设银行",
        "建设" to "中国建设银行",
        "建行" to "中国建设银行",
        "农业银行" to "中国农业银行",
        "农业" to "中国农业银行",
        "农行" to "中国农业银行",
        "中行" to "中国银行",
        "邮政储蓄银行" to "中国邮政储蓄银行",
        "邮储银行" to "中国邮政储蓄银行",
        "中国邮储银行" to "中国邮政储蓄银行",
        "邮储" to "中国邮政储蓄银行",
        "交行" to "交通银行",
        "招行" to "招商银行",
        "中信" to "中信银行",
        "中国光大银行" to "光大银行",
        "光大" to "光大银行",
        "广东发展银行" to "广发银行",
        "广发" to "广发银行",
        "上海浦东发展银行" to "浦发银行",
        "浦发" to "浦发银行",
        "兴业" to "兴业银行",
        "中国民生银行" to "民生银行",
        "民生" to "民生银行",
        "华夏" to "华夏银行",
        "平安" to "平安银行",
        "浙商" to "浙商银行",
        "渤海" to "渤海银行",
        "天星银行" to "象象银行",
        "天星銀行" to "象象银行",
        "Airstar Bank" to "象象银行",
        "汇丰银行" to "汇丰银行 (中国)",
        "汇丰中国" to "汇丰银行 (中国)",
        "香港汇丰" to "香港上海滙豐銀行",
        "香港上海汇丰银行" to "香港上海滙豐銀行"
    )

    fun banks(context: Context): List<BuiltinBank> {
        cachedBanks?.let { return it }
        return synchronized(this) {
            cachedBanks ?: load(context.applicationContext).also { cachedBanks = it }
        }
    }

    fun find(context: Context, bankName: String): BuiltinBank? {
        val normalized = normalize(bankName)
        if (normalized.isBlank()) return null
        val allBanks = banks(context)
        allBanks.firstOrNull { bank ->
            normalize(bank.name) == normalized ||
                normalize(bank.english) == normalized ||
                bank.aliases.any { normalize(it) == normalized }
        }?.let { return it }
        val canonicalName = commonAliases.entries
            .firstOrNull { normalize(it.key) == normalized }
            ?.value
            ?: return null
        return allBanks.firstOrNull { it.name == canonicalName }
    }

    fun logoAsset(context: Context, bankName: String): String =
        find(context, bankName)?.logoAsset.orEmpty()

    private fun load(context: Context): List<BuiltinBank> {
        val json = context.assets.open(CATALOG_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array = JSONObject(json).getJSONArray("banks")
        val loaded = buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val aliasesJson = item.optJSONArray("aliases")
                val aliases = buildList {
                    if (aliasesJson != null) {
                        for (aliasIndex in 0 until aliasesJson.length()) {
                            add(aliasesJson.getString(aliasIndex))
                        }
                    }
                }
                add(
                    BuiltinBank(
                        name = item.getString("name"),
                        english = item.optString("english"),
                        section = item.optString("section", "#"),
                        sortKey = item.optString("sortKey"),
                        logoAsset = item.optString("logo"),
                        aliases = aliases
                    )
                )
            }
        }
        val nameCollator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE)
        return loaded.sortedWith { left, right ->
            val sectionComparison = left.section.compareTo(right.section)
            if (sectionComparison != 0) sectionComparison else nameCollator.compare(left.name, right.name)
        }
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[\\s()（）·._-]+"), "")
}
