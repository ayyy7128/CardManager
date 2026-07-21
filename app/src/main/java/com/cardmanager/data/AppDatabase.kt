package com.cardmanager.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

@Database(
    entities = [CardGroup::class, Card::class, Task::class, PiggyEntry::class, AssetPlan::class, AppSetting::class],
    version = 12,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): CardGroupDao
    abstract fun cardDao(): CardDao
    abstract fun taskDao(): TaskDao
    abstract fun piggyDao(): PiggyDao
    abstract fun assetPlanDao(): AssetPlanDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val M_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN logoImagePath TEXT NOT NULL DEFAULT ''")
            }
        }

        private val M_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN bankLogoPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cards ADD COLUMN cardTypeName TEXT NOT NULL DEFAULT ''")
            }
        }

        // v7: 添加卡类型字段
        private val M_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN cardCategory TEXT NOT NULL DEFAULT ''")
            }
        }

        // v8: 添加卡面方向字段
        private val M_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN imageOrientation TEXT NOT NULL DEFAULT 'horizontal'")
            }
        }

        private val M_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS asset_plans (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL DEFAULT '',
                        cardId TEXT NOT NULL DEFAULT '',
                        platform TEXT NOT NULL DEFAULT '',
                        category TEXT NOT NULL DEFAULT '',
                        code TEXT NOT NULL DEFAULT '',
                        currency TEXT NOT NULL DEFAULT 'CNY',
                        initialCapital REAL NOT NULL DEFAULT 0,
                        initialDate TEXT NOT NULL DEFAULT '',
                        ratePlansJson TEXT NOT NULL DEFAULT '',
                        pauseRangesJson TEXT NOT NULL DEFAULT '',
                        adjustmentsJson TEXT NOT NULL DEFAULT '',
                        overridesJson TEXT NOT NULL DEFAULT '',
                        cycleDays INTEGER NOT NULL DEFAULT 1,
                        monthlyDay INTEGER NOT NULL DEFAULT 0,
                        weeklyDay INTEGER NOT NULL DEFAULT 0,
                        skipMissingMonthlyDate INTEGER NOT NULL DEFAULT 0,
                        postponeNonTrading INTEGER NOT NULL DEFAULT 0,
                        includeFirstDay INTEGER NOT NULL DEFAULT 1,
                        status TEXT NOT NULL DEFAULT 'running',
                        frozenAmount REAL NOT NULL DEFAULT 0,
                        countInTotal INTEGER NOT NULL DEFAULT 1,
                        skipWeekends INTEGER NOT NULL DEFAULT 1,
                        orderIndex INTEGER NOT NULL DEFAULT 0,
                        startDate TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        private val M_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("asset_plans", "cardId")) {
                    db.execSQL("ALTER TABLE asset_plans ADD COLUMN cardId TEXT NOT NULL DEFAULT ''")
                }
                db.execSQL("""
                    INSERT OR IGNORE INTO asset_plans (
                        id, name, cardId, platform, category, code, currency,
                        initialCapital, initialDate, ratePlansJson, pauseRangesJson, adjustmentsJson, overridesJson,
                        cycleDays, monthlyDay, weeklyDay, skipMissingMonthlyDate, postponeNonTrading,
                        includeFirstDay, status, frozenAmount, countInTotal, skipWeekends, orderIndex, startDate
                    )
                    SELECT
                        'task_' || id,
                        name,
                        cardId,
                        '',
                        '',
                        '',
                        'CNY',
                        0,
                        COALESCE(NULLIF(startDate, ''), NULLIF(date, ''), date('now')),
                        '[{"startDate":"' || COALESCE(NULLIF(startDate, ''), NULLIF(date, ''), date('now')) || '","amount":' || ABS(investAmount) || '}]',
                        '[]',
                        '[]',
                        '[]',
                        CASE
                            WHEN freq = 'once' THEN 0
                            WHEN freq = 'weekly' THEN 7
                            WHEN freq = 'monthly' THEN 30
                            WHEN freq = 'ndays' THEN CASE WHEN ndays > 0 THEN ndays ELSE 1 END
                            ELSE 90
                        END,
                        CASE WHEN freq = 'monthly' THEN CASE WHEN day > 0 THEN day ELSE 1 END ELSE 0 END,
                        CASE WHEN freq = 'weekly' THEN CASE WHEN weekday BETWEEN 1 AND 7 THEN weekday ELSE 1 END ELSE 0 END,
                        0,
                        CASE WHEN holidays = 'allowNonTradingDays' THEN 0 ELSE 1 END,
                        1,
                        'running',
                        0,
                        1,
                        CASE WHEN holidays = 'allowNonTradingDays' THEN 0 ELSE 1 END,
                        CAST(strftime('%s','now') AS INTEGER) * 1000,
                        COALESCE(NULLIF(startDate, ''), NULLIF(date, ''), date('now'))
                    FROM tasks
                    WHERE isInvest = 1 AND ABS(investAmount) > 0
                """.trimIndent())
                db.execSQL("DELETE FROM tasks WHERE isInvest = 1")
            }
        }

        private val M_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("cards", "creditLimit")) {
                    db.execSQL("ALTER TABLE cards ADD COLUMN creditLimit REAL NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("cards", "billingDay")) {
                    db.execSQL("ALTER TABLE cards ADD COLUMN billingDay INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val M_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("cards", "repaymentDay")) {
                    db.execSQL("ALTER TABLE cards ADD COLUMN repaymentDay INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        // v6: 添加有效期字段
        private val M_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN expiryDate TEXT NOT NULL DEFAULT ''")
            }
        }

        // v5: 移除余额追踪字段（SQLite 不支持 DROP COLUMN，需重建表）
        private val M_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE cards_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        groupId TEXT NOT NULL,
                        bank TEXT NOT NULL,
                        network TEXT NOT NULL DEFAULT '银联',
                        currency TEXT NOT NULL DEFAULT 'CNY',
                        tail TEXT NOT NULL DEFAULT '',
                        role TEXT NOT NULL DEFAULT '',
                        note TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'active',
                        isVirtual INTEGER NOT NULL DEFAULT 0,
                        noCard INTEGER NOT NULL DEFAULT 0,
                        logoEmoji TEXT NOT NULL DEFAULT '',
                        logoImagePath TEXT NOT NULL DEFAULT '',
                        bankLogoPath TEXT NOT NULL DEFAULT '',
                        cardTypeName TEXT NOT NULL DEFAULT '',
                        sortOrder INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO cards_new
                        (id,groupId,bank,network,currency,tail,role,note,status,
                         isVirtual,noCard,logoEmoji,logoImagePath,bankLogoPath,cardTypeName,sortOrder)
                    SELECT
                        id,groupId,bank,network,currency,tail,role,note,status,
                        isVirtual,noCard,logoEmoji,logoImagePath,bankLogoPath,cardTypeName,sortOrder
                    FROM cards
                """.trimIndent())
                db.execSQL("DROP TABLE cards")
                db.execSQL("ALTER TABLE cards_new RENAME TO cards")
            }
        }

        private val M_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN balanceEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cards ADD COLUMN balance REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cards ADD COLUMN balanceDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cards ADD COLUMN balanceThreshold REAL NOT NULL DEFAULT 500")
                db.execSQL("ALTER TABLE tasks ADD COLUMN investAmount REAL NOT NULL DEFAULT 0")
            }
        }

        private fun SupportSQLiteDatabase.hasColumn(tableName: String, columnName: String): Boolean {
            query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) return true
                }
            }
            return false
        }

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "cardmanager.db")
                .addMigrations(M_1_2, M_2_3, M_3_4, M_4_5, M_5_6, M_6_7, M_7_8, M_8_9, M_9_10, M_10_11, M_11_12)
                .build().also { INSTANCE = it }
        }
    }
}

enum class ImportMode {
    MERGE,
    OVERWRITE
}

class AppRepository(private val db: AppDatabase, private val context: Context? = null) {
    private val encryptedBackupMagic = "CMBKENC2".toByteArray(Charsets.US_ASCII)
    private val legacyEncryptedBackupMagic = "CMBKENC1".toByteArray(Charsets.US_ASCII)
    private val passwordBackupFlag = 1
    private val maxBackupBytes = 128 * 1024 * 1024
    private val maxJsonBytes = 16 * 1024 * 1024
    private val maxImageBytes = 32 * 1024 * 1024
    private val maxFontBytes = 16 * 1024 * 1024
    private val maxExtractedBytes = 192 * 1024 * 1024
    private val exportedSettingKeys = setOf(
        "theme",
        "vaultCurrency",
        "preferHighRefreshRate",
        "cardsPerRowPortrait",
        "cardsPerRowLandscape",
        "ungroupedMode",
        "cardGalleryMode",
        "cardViewMode",
        "tabOrder",
        "visibleOptionalTabs",
        "tabVisibilityAllTabs",
        "visibleDataCharts",
        "dataChartOrder",
        "visibleDataOverview",
        "showCreditLimitOverview",
        "creditLimitGroupMode",
        "dataOverviewOrder",
        "custom_font_name"
    )

    val groups: Flow<List<CardGroup>> = db.groupDao().getAllGroups()
    val cards: Flow<List<Card>> = db.cardDao().getAllCards()
    val tasks: Flow<List<Task>> = db.taskDao().getAllTasks()
    val piggyEntries: Flow<List<PiggyEntry>> = db.piggyDao().getAllEntries()
    val assetPlans: Flow<List<AssetPlan>> = db.assetPlanDao().getAllPlans()

    suspend fun saveGroup(g: CardGroup) = db.groupDao().insert(g)
    suspend fun updateGroup(g: CardGroup) = db.groupDao().update(g)
    suspend fun deleteGroup(g: CardGroup) {
        val groupCards = db.groupDao().getCardsInGroupOnce(g.id)
        db.withTransaction {
            groupCards.forEach { card ->
                db.taskDao().deleteForCard(card.id)
                db.piggyDao().deleteForCard(card.id)
                db.assetPlanDao().unlinkCard(card.id)
                db.cardDao().delete(card)
            }
            db.groupDao().delete(g)
        }
        groupCards.forEach { deleteImagesIfOwned(it) }
    }

    suspend fun saveCard(c: Card) = db.cardDao().insert(c)
    suspend fun updateCard(c: Card) = db.cardDao().update(c)
    suspend fun deleteCard(c: Card) {
        db.withTransaction {
            db.taskDao().deleteForCard(c.id)
            db.piggyDao().deleteForCard(c.id)
            db.assetPlanDao().unlinkCard(c.id)
            db.cardDao().delete(c)
        }
        deleteImagesIfOwned(c)
    }
    suspend fun updateCardOrder(id: String, order: Int) = db.cardDao().updateSortOrder(id, order)

    suspend fun saveTask(t: Task) = db.taskDao().insert(t)
    suspend fun updateTask(t: Task) = db.taskDao().update(t)
    suspend fun deleteTask(t: Task) = db.taskDao().delete(t)
    suspend fun migrateLegacyInvestTasksToAssetPlans() {
        val investTasks = tasks.first().filter { it.isInvest }
        val legacy = investTasks.filter { kotlin.math.abs(it.investAmount) > 0.0 }
        if (investTasks.isEmpty()) return
        db.withTransaction {
            legacy.forEachIndexed { index, task ->
                db.assetPlanDao().insert(assetPlanFromTask(task, index))
                db.taskDao().delete(task)
            }
            investTasks.filter { kotlin.math.abs(it.investAmount) <= 0.0 }.forEach {
                db.taskDao().delete(it)
            }
        }
    }

    suspend fun savePiggy(e: PiggyEntry) = db.piggyDao().insert(e)
    suspend fun updatePiggy(e: PiggyEntry) = db.piggyDao().update(e)
    suspend fun deletePiggy(e: PiggyEntry) = db.piggyDao().delete(e)

    suspend fun saveAssetPlan(plan: AssetPlan) = db.assetPlanDao().insert(plan)
    suspend fun updateAssetPlan(plan: AssetPlan) = db.assetPlanDao().update(plan)
    suspend fun deleteAssetPlan(plan: AssetPlan) = db.assetPlanDao().delete(plan)

    suspend fun getSetting(key: String, default: String = "") = db.settingDao().get(key)?.value ?: default
    suspend fun setSetting(key: String, value: String) = db.settingDao().set(AppSetting(key, value))

    suspend fun exportBackup(ctx: android.content.Context, uri: android.net.Uri, password: String? = null) =
        exportEncryptedBackup(ctx, uri, password)

    suspend fun exportEncryptedBackup(ctx: android.content.Context, uri: android.net.Uri, password: String? = null) {
        val grps = groups.first(); val cds = cards.first()
        val tsks = tasks.first().filterNot { it.isInvest };  val pig = piggyEntries.first()
        val plans = assetPlans.first()
        val settings = db.settingDao().getAllOnce()
            .filter { it.key in exportedSettingKeys && it.key != "custom_font_path" }
            .distinctBy { it.key }
        val zipBytes = buildZipBytes(ctx, grps, cds, tsks, pig, plans, settings)
        val encryptedBytes = encryptBackup(zipBytes, password)

        val output = ctx.contentResolver.openOutputStream(uri)
            ?: throw Exception("无法写入备份文件")
        output.buffered().use { out ->
            out.write(encryptedBytes)
        }
    }

    private fun buildZipBytes(ctx: android.content.Context, grps: List<CardGroup>, cds: List<Card>,
                              tsks: List<Task>, pig: List<PiggyEntry>, plans: List<AssetPlan>,
                              settings: List<AppSetting>): ByteArray {
        val json = buildZipJson(grps, cds, tsks, pig, plans, settings)
        val buffer = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("data.json"))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val added = mutableSetOf<String>()
            fun addImg(path: String) {
                if (path.isEmpty()) return
                val f = java.io.File(path)
                if (f.exists() && f.name !in added) {
                    zip.putNextEntry(java.util.zip.ZipEntry("images/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    added += f.name
                }
            }
            cds.forEach { c -> addImg(c.logoImagePath); addImg(c.bankLogoPath) }
            val fontFile = java.io.File(ctx.filesDir, "custom_font.ttf")
            if (fontFile.exists()) {
                val prefs = ctx.getSharedPreferences("cm_font", android.content.Context.MODE_PRIVATE)
                zip.putNextEntry(java.util.zip.ZipEntry("fonts/custom_font.ttf"))
                fontFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                zip.putNextEntry(java.util.zip.ZipEntry("fonts/font_meta.txt"))
                zip.write(prefs.getString("font_name", fontFile.name)!!.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    private fun encryptBackup(plainBytes: ByteArray, password: String?): ByteArray {
        val pass = password?.takeIf { it.isNotBlank() }
            ?: throw Exception("请设置备份密码")
        val flags = passwordBackupFlag
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val key = passwordBackupKey(pass, salt)
        val header = java.io.ByteArrayOutputStream().use { out ->
            out.write(encryptedBackupMagic)
            out.write(flags)
            out.write(salt.size)
            out.write(salt)
            out.write(iv)
            out.toByteArray()
        }
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        val cipherBytes = cipher.doFinal(plainBytes)
        return java.io.ByteArrayOutputStream().use { out ->
            out.write(header)
            out.write(cipherBytes)
            out.toByteArray()
        }
    }

    private fun decryptBackup(encryptedBytes: ByteArray, password: String?): ByteArray {
        return when {
            hasMagic(encryptedBytes, encryptedBackupMagic) -> decryptCurrentBackup(encryptedBytes, password)
            hasMagic(encryptedBytes, legacyEncryptedBackupMagic) -> decryptLegacyBackup(encryptedBytes)
            else -> throw Exception("备份文件格式错误：不是卡片管家加密备份")
        }
    }

    private fun decryptCurrentBackup(encryptedBytes: ByteArray, password: String?): ByteArray {
        val headerBaseSize = encryptedBackupMagic.size + 2
        if (encryptedBytes.size <= headerBaseSize + 12) {
            throw Exception("备份文件格式错误：不是卡片管家加密备份")
        }
        val flags = encryptedBytes[encryptedBackupMagic.size].toInt() and 0xff
        val saltSize = encryptedBytes[encryptedBackupMagic.size + 1].toInt() and 0xff
        val saltStart = headerBaseSize
        val saltEnd = saltStart + saltSize
        val ivStart = saltEnd
        val ivEnd = ivStart + 12
        if (saltEnd > encryptedBytes.size || encryptedBytes.size <= ivEnd) {
            throw Exception("备份文件格式错误：不是卡片管家加密备份")
        }
        val salt = encryptedBytes.copyOfRange(saltStart, saltEnd)
        val iv = encryptedBytes.copyOfRange(ivStart, ivEnd)
        val cipherBytes = encryptedBytes.copyOfRange(ivEnd, encryptedBytes.size)
        val header = encryptedBytes.copyOfRange(0, ivEnd)
        val needsPassword = (flags and passwordBackupFlag) == passwordBackupFlag
        val key = if (needsPassword) {
            val pass = password?.takeIf { it.isNotBlank() }
                ?: throw Exception("此备份已设置密码，请输入备份密码")
            passwordBackupKey(pass, salt)
        } else {
            legacyAppBackupKey()
        }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        return runCatching { cipher.doFinal(cipherBytes) }
            .getOrElse { throw Exception("密码错误或备份文件已损坏", it) }
    }

    private fun decryptLegacyBackup(encryptedBytes: ByteArray): ByteArray {
        if (encryptedBytes.size <= legacyEncryptedBackupMagic.size + 12) {
            throw Exception("备份文件格式错误：不是卡片管家加密备份")
        }
        val ivStart = legacyEncryptedBackupMagic.size
        val ivEnd = ivStart + 12
        val iv = encryptedBytes.copyOfRange(ivStart, ivEnd)
        val cipherBytes = encryptedBytes.copyOfRange(ivEnd, encryptedBytes.size)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, legacyAppBackupKey(), javax.crypto.spec.GCMParameterSpec(128, iv))
        cipher.updateAAD(legacyEncryptedBackupMagic)
        return runCatching { cipher.doFinal(cipherBytes) }
            .getOrElse { throw Exception("备份文件已损坏", it) }
    }

    private fun isEncryptedBackup(bytes: ByteArray): Boolean =
        hasMagic(bytes, encryptedBackupMagic) || hasMagic(bytes, legacyEncryptedBackupMagic)

    private fun hasMagic(bytes: ByteArray, magic: ByteArray): Boolean =
        bytes.size >= magic.size && magic.indices.all { bytes[it] == magic[it] }

    private fun legacyAppBackupKey(): javax.crypto.SecretKey {
        val material = "CardManager.local.encrypted.backup.v1.ayyy.codex".toByteArray(Charsets.UTF_8)
        val keyBytes = java.security.MessageDigest.getInstance("SHA-256").digest(material)
        return javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
    }

    private fun passwordBackupKey(password: String, salt: ByteArray): javax.crypto.SecretKey {
        val material = "CardManager.local.encrypted.backup.v2.ayyy.codex:$password".toByteArray(Charsets.UTF_8)
        val keyBytes = pbkdf2HmacSha256(material, salt, 120_000, 32)
        material.fill(0)
        return javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
    }

    private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, keySizeBytes: Int): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(password, "HmacSHA256"))
        val hLen = mac.macLength
        val blockCount = (keySizeBytes + hLen - 1) / hLen
        val output = ByteArray(blockCount * hLen)
        val blockIndexBytes = ByteArray(4)
        var offset = 0
        for (blockIndex in 1..blockCount) {
            blockIndexBytes[0] = (blockIndex ushr 24).toByte()
            blockIndexBytes[1] = (blockIndex ushr 16).toByte()
            blockIndexBytes[2] = (blockIndex ushr 8).toByte()
            blockIndexBytes[3] = blockIndex.toByte()
            mac.update(salt)
            val first = mac.doFinal(blockIndexBytes)
            val block = first.copyOf()
            var previous = first
            repeat(iterations - 1) {
                previous = mac.doFinal(previous)
                for (i in block.indices) block[i] = (block[i].toInt() xor previous[i].toInt()).toByte()
            }
            System.arraycopy(block, 0, output, offset, hLen)
            offset += hLen
        }
        return output.copyOf(keySizeBytes)
    }

    private fun buildZipJson(grps: List<CardGroup>, cds: List<Card>,
                             tsks: List<Task>, pig: List<PiggyEntry>, plans: List<AssetPlan>,
                             settings: List<AppSetting>): String {
        fun esc(s: String) = jsonEscape(s)
        val sb = StringBuilder("{\"version\":2")
        sb.append(",\"groups\":[${grps.joinToString(",") { g ->
            "{\"id\":\"${g.id}\",\"name\":\"${esc(g.name)}\",\"icon\":\"${g.icon}\",\"isOpen\":${g.isOpen},\"order\":${g.sortOrder}}"
        }}]")
        sb.append(",\"cards\":[${cds.joinToString(",") { c ->
            val lf = if (c.logoImagePath.isNotEmpty()) java.io.File(c.logoImagePath).name else ""
            val bf = if (c.bankLogoPath.isNotEmpty()) java.io.File(c.bankLogoPath).name else ""
            "{\"id\":\"${c.id}\",\"gid\":\"${c.groupId}\",\"bank\":\"${esc(c.bank)}\"," +
            "\"network\":\"${c.network}\",\"currency\":\"${c.currency}\",\"tail\":\"${c.tail}\"," +
            "\"role\":\"${esc(c.role)}\",\"note\":\"${esc(c.note)}\",\"status\":\"${c.status}\"," +
            "\"isVirtual\":${c.isVirtual},\"noCard\":${c.noCard},\"logo\":\"${c.logoEmoji}\"," +
            "\"order\":${c.sortOrder},\"cardTypeName\":\"${esc(c.cardTypeName)}\"," +
            "\"logoFile\":\"${esc(lf)}\",\"bankLogoFile\":\"${esc(bf)}\",\"expiryDate\":\"${esc(c.expiryDate)}\"," +
            "\"cardCategory\":\"${esc(c.cardCategory)}\",\"imageOrientation\":\"${esc(c.imageOrientation)}\"," +
            "\"creditLimit\":${c.creditLimit},\"billingDay\":${c.billingDay},\"repaymentDay\":${c.repaymentDay}}"
        }}]")
        fun tj(t: Task) = "{\"id\":\"${t.id}\",\"name\":\"${esc(t.name)}\",\"freq\":\"${t.freq}\"," +
            "\"cardId\":\"${t.cardId}\",\"isInvest\":${t.isInvest},\"investAmount\":${t.investAmount}," +
            "\"day\":${t.day},\"weekday\":${t.weekday},\"months\":\"${t.months}\"," +
            "\"ndays\":${t.ndays},\"startDate\":\"${t.startDate}\",\"date\":\"${t.date}\"," +
            "\"holidays\":\"${esc(t.holidays)}\"}"
        val monthly = tsks.filter { it.freq=="monthly" }; val weekly = tsks.filter { it.freq=="weekly" }
        val quarterly = tsks.filter { it.freq=="quarterly" }; val ndays = tsks.filter { it.freq=="ndays" }
        val once = tsks.filter { it.freq=="once" }
        sb.append(",\"monthlyTasks\":[${monthly.joinToString(",") { tj(it) }}]")
        sb.append(",\"weeklyTasks\":[${weekly.joinToString(",") { tj(it) }}]")
        sb.append(",\"quarterlyTasks\":[${quarterly.joinToString(",") { tj(it) }}]")
        sb.append(",\"ndaysTasks\":[${ndays.joinToString(",") { tj(it) }}]")
        sb.append(",\"onceTasks\":[${once.joinToString(",") { tj(it) }}]")
        sb.append(",\"pig\":[${pig.joinToString(",") { e ->
            "{\"id\":${e.id},\"amount\":${e.amount},\"desc\":\"${esc(e.desc)}\"," +
            "\"cardId\":\"${e.cardId}\",\"date\":\"${e.date}\",\"ts\":${e.timestamp}}"
        }}]}")
        sb.insert(sb.length - 1, ",\"assetPlans\":[${plans.joinToString(",") { p ->
            "{\"id\":\"${esc(p.id)}\",\"name\":\"${esc(p.name)}\",\"cardId\":\"${esc(p.cardId)}\",\"platform\":\"${esc(p.platform)}\"," +
            "\"category\":\"${esc(p.category)}\",\"code\":\"${esc(p.code)}\",\"currency\":\"${esc(p.currency)}\"," +
            "\"initialCapital\":${p.initialCapital},\"initialDate\":\"${esc(p.initialDate)}\"," +
            "\"ratePlansJson\":\"${esc(p.ratePlansJson)}\",\"pauseRangesJson\":\"${esc(p.pauseRangesJson)}\"," +
            "\"adjustmentsJson\":\"${esc(p.adjustmentsJson)}\",\"overridesJson\":\"${esc(p.overridesJson)}\"," +
            "\"cycleDays\":${p.cycleDays},\"monthlyDay\":${p.monthlyDay},\"weeklyDay\":${p.weeklyDay}," +
            "\"skipMissingMonthlyDate\":${p.skipMissingMonthlyDate},\"postponeNonTrading\":${p.postponeNonTrading}," +
            "\"includeFirstDay\":${p.includeFirstDay},\"status\":\"${esc(p.status)}\",\"frozenAmount\":${p.frozenAmount}," +
            "\"countInTotal\":${p.countInTotal},\"skipWeekends\":${p.skipWeekends},\"orderIndex\":${p.orderIndex}," +
            "\"startDate\":\"${esc(p.startDate)}\"}"
        }}]")
        sb.insert(sb.length - 1, ",\"settings\":[${settings.joinToString(",") { s ->
            "{\"key\":\"${esc(s.key)}\",\"value\":\"${esc(s.value)}\"}"
        }}]")
        return sb.toString()
    }

    data class ImportSummary(val groupCount: Int, val cardCount: Int,
                             val taskCount: Int, val pigCount: Int, val assetPlanCount: Int = 0) {
        override fun toString() =
            "导入成功：${groupCount} 个分组、${cardCount} 张卡、${taskCount} 个任务、${pigCount} 条记录、${assetPlanCount} 个投资任务"
    }

    private data class ParsedBackup(
        val groups: List<CardGroup>,
        val cards: List<Card>,
        val tasks: List<Task>,
        val piggyEntries: List<PiggyEntry>,
        val assetPlans: List<AssetPlan>,
        val settings: List<AppSetting>
    )

    suspend fun importBackup(
        ctx: android.content.Context,
        uri: android.net.Uri,
        mode: ImportMode,
        password: String? = null
    ): ImportSummary {
        val imgDir = java.io.File(ctx.filesDir, "card_logos").also { it.mkdirs() }
        var jsonStr: String? = null
        val imageMap = mutableMapOf<String, ByteArray>()
        val importedFontFile = java.io.File(ctx.filesDir, "custom_font.ttf")
        var importedFontName: String? = null
        var importedFontBytes: ByteArray? = null
        var hasFontMeta = false
        var extractedBytes = 0L

        val rawBytes = ctx.contentResolver.openInputStream(uri)?.buffered()?.use {
            readBytesLimited(it, maxBackupBytes, "备份文件")
        }
            ?: throw Exception("无法读取备份文件")
        if (!isEncryptedBackup(rawBytes)) {
            throw Exception("请选择 .cmbak 加密备份文件")
        }
        val zipBytes = decryptBackup(rawBytes, password)

        java.io.ByteArrayInputStream(zipBytes).buffered().use { ins ->
            java.util.zip.ZipInputStream(ins).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        when {
                            entry.name == "data.json" -> {
                                if (jsonStr != null) throw Exception("备份文件格式错误：包含重复的 data.json")
                                val bytes = readBytesLimited(zip, maxJsonBytes, "data.json")
                                extractedBytes += bytes.size
                                jsonStr = bytes.toString(Charsets.UTF_8)
                            }
                            entry.name.startsWith("images/") -> {
                                val name = entry.name.removePrefix("images/")
                                val target = safeChildFile(imgDir, name)
                                    ?: throw Exception("备份文件包含无效图片路径")
                                if (target.name in imageMap) throw Exception("备份文件包含重复图片：${target.name}")
                                val bytes = readBytesLimited(zip, maxImageBytes, "图片 ${target.name}")
                                extractedBytes += bytes.size
                                imageMap[target.name] = bytes
                            }
                            entry.name == "fonts/custom_font.ttf" -> {
                                if (importedFontBytes != null) throw Exception("备份文件包含重复的自定义字体")
                                val bytes = readBytesLimited(zip, maxFontBytes, "自定义字体")
                                extractedBytes += bytes.size
                                importedFontBytes = bytes
                            }
                            entry.name == "fonts/font_meta.txt" -> {
                                if (hasFontMeta) throw Exception("备份文件包含重复的字体信息")
                                val bytes = readBytesLimited(zip, 16 * 1024, "字体信息")
                                extractedBytes += bytes.size
                                val fontName = bytes.toString(Charsets.UTF_8)
                                importedFontName = fontName
                                hasFontMeta = true
                            }
                        }
                        if (extractedBytes > maxExtractedBytes.toLong()) {
                            throw Exception("备份文件解压后过大")
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val root = org.json.JSONObject(
            jsonStr ?: throw Exception("备份文件格式错误：未找到 data.json"))
        val restoredFontName = importedFontName?.trim()?.takeIf { it.isNotBlank() }
        val hasImportedFontFile = importedFontBytes != null && restoredFontName != null
        val parsed = parseBackupPayload(
            root = root,
            imageDir = imgDir,
            imageNames = imageMap.keys,
            hasImportedFontFile = hasImportedFontFile,
            importedFontFile = importedFontFile,
            restoredFontName = restoredFontName
        )

        val stagingDir = java.io.File(ctx.cacheDir, "backup_import_${java.util.UUID.randomUUID()}")
        val stagingImages = java.io.File(stagingDir, "images").also { it.mkdirs() }
        val stagedFont = java.io.File(stagingDir, "custom_font.ttf")
        try {
            imageMap.forEach { (name, bytes) ->
                safeChildFile(stagingImages, name)?.writeBytes(bytes)
                    ?: throw Exception("备份文件包含无效图片路径")
            }
            importedFontBytes?.let { stagedFont.writeBytes(it) }

            val oldCards = if (mode == ImportMode.OVERWRITE) cards.first() else emptyList()
            db.withTransaction {
                if (mode == ImportMode.OVERWRITE) {
                    db.taskDao().deleteAll()
                    db.piggyDao().deleteAll()
                    db.assetPlanDao().deleteAll()
                    db.cardDao().deleteAll()
                    db.groupDao().deleteAll()
                }
                parsed.groups.forEach { db.groupDao().insert(it) }
                parsed.cards.forEach { db.cardDao().insert(it) }
                parsed.tasks.forEach { db.taskDao().insert(it) }
                parsed.piggyEntries.forEach { db.piggyDao().insert(it) }
                parsed.assetPlans.forEach { db.assetPlanDao().insert(it) }
                parsed.settings.forEach { db.settingDao().set(it) }
                if (mode == ImportMode.OVERWRITE && !hasImportedFontFile) {
                    db.settingDao().set(AppSetting("custom_font_path", ""))
                    db.settingDao().set(AppSetting("custom_font_name", ""))
                }
            }

            imageMap.keys.forEach { name ->
                val source = safeChildFile(stagingImages, name)
                    ?: throw Exception("备份文件包含无效图片路径")
                val target = safeChildFile(imgDir, name)
                    ?: throw Exception("备份文件包含无效图片路径")
                source.copyTo(target, overwrite = true)
            }
            if (hasImportedFontFile) {
                stagedFont.copyTo(importedFontFile, overwrite = true)
            } else if (mode == ImportMode.OVERWRITE) {
                importedFontFile.delete()
            }

            val importedPaths = parsed.cards
                .flatMap { listOf(it.logoImagePath, it.bankLogoPath) }
                .filter { it.isNotBlank() }
                .toSet()
            oldCards.flatMap { listOf(it.logoImagePath, it.bankLogoPath) }
                .filter { it.isNotBlank() && it !in importedPaths }
                .forEach { ImageStore.deleteOwned(ctx, it) }

            applyImportedPreferences(
                ctx = ctx,
                settings = parsed.settings,
                hasImportedFontFile = hasImportedFontFile,
                importedFontFile = importedFontFile,
                restoredFontName = restoredFontName,
                overwrite = mode == ImportMode.OVERWRITE
            )

            return ImportSummary(
                groupCount = parsed.groups.size,
                cardCount = parsed.cards.size,
                taskCount = parsed.tasks.size,
                pigCount = parsed.piggyEntries.size,
                assetPlanCount = parsed.assetPlans.size
            )
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun parseBackupPayload(
        root: org.json.JSONObject,
        imageDir: File,
        imageNames: Set<String>,
        hasImportedFontFile: Boolean,
        importedFontFile: File,
        restoredFontName: String?
    ): ParsedBackup {
        val parsedGroups = mutableListOf<CardGroup>()
        val groupsArray = root.optJSONArray("groups") ?: org.json.JSONArray()
        for (i in 0 until groupsArray.length()) {
            val item = groupsArray.getJSONObject(i)
            parsedGroups += CardGroup(
                id = item.getString("id"),
                name = item.getString("name"),
                icon = item.optString("icon", "💳"),
                isOpen = item.optBoolean("isOpen", true),
                sortOrder = item.optInt("order", i)
            )
        }

        val parsedCards = mutableListOf<Card>()
        val cardsArray = root.optJSONArray("cards") ?: org.json.JSONArray()
        for (i in 0 until cardsArray.length()) {
            val item = cardsArray.getJSONObject(i)
            val creditLimit = requireFinite(item.optDouble("creditLimit", 0.0), "信用卡额度")
            parsedCards += Card(
                id = item.getString("id"),
                groupId = item.getString("gid"),
                bank = item.getString("bank"),
                network = item.optString("network", "银联"),
                currency = item.optString("currency", "CNY"),
                tail = item.optString("tail", ""),
                role = item.optString("role", ""),
                note = item.optString("note", ""),
                status = item.optString("status", "active"),
                isVirtual = item.optBoolean("isVirtual", false),
                noCard = item.optBoolean("noCard", false),
                logoEmoji = item.optString("logo", ""),
                logoImagePath = importedImagePath(imageDir, item.optString("logoFile", ""), imageNames),
                bankLogoPath = importedImagePath(imageDir, item.optString("bankLogoFile", ""), imageNames),
                cardTypeName = item.optString("cardTypeName", ""),
                expiryDate = item.optString("expiryDate", ""),
                cardCategory = item.optString("cardCategory", ""),
                sortOrder = item.optInt("order", i),
                imageOrientation = normalizedOrientation(item.optString("imageOrientation", "horizontal")),
                creditLimit = creditLimit.coerceAtLeast(0.0),
                billingDay = item.optInt("billingDay", 0).takeIf { it in 1..31 } ?: 0,
                repaymentDay = item.optInt("repaymentDay", 0).takeIf { it in 1..31 } ?: 0
            )
        }

        val parsedTasks = mutableListOf<Task>()
        val legacyAssetPlans = mutableListOf<AssetPlan>()
        fun parseTask(item: org.json.JSONObject): Task {
            val investAmount = requireFinite(item.optDouble("investAmount", 0.0), "任务金额")
            return Task(
                id = item.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
                name = item.optString("name", "未命名"),
                freq = item.getString("freq"),
                cardId = item.optString("cardId", ""),
                isInvest = item.optBoolean("isInvest", false),
                investAmount = investAmount,
                day = item.optInt("day", 1),
                weekday = item.optInt("weekday", 1),
                months = item.optString("months", ""),
                ndays = item.optInt("ndays", 7).coerceAtLeast(1),
                startDate = item.optString("startDate", ""),
                date = item.optString("date", ""),
                holidays = item.optString("holidays", "")
            )
        }
        listOf("monthlyTasks", "weeklyTasks", "quarterlyTasks", "ndaysTasks", "onceTasks").forEach { key ->
            val array = root.optJSONArray(key) ?: return@forEach
            for (i in 0 until array.length()) {
                val task = parseTask(array.getJSONObject(i))
                if (task.isInvest && kotlin.math.abs(task.investAmount) > 0.0) {
                    legacyAssetPlans += assetPlanFromTask(task, i)
                } else if (!task.isInvest) {
                    parsedTasks += task
                }
            }
        }

        val parsedPiggyEntries = mutableListOf<PiggyEntry>()
        val piggyArray = root.optJSONArray("pig") ?: org.json.JSONArray()
        for (i in 0 until piggyArray.length()) {
            val item = piggyArray.getJSONObject(i)
            parsedPiggyEntries += PiggyEntry(
                id = item.getLong("id"),
                amount = requireFinite(item.getDouble("amount"), "小金库金额"),
                desc = item.optString("desc", ""),
                cardId = item.optString("cardId", ""),
                date = item.getString("date"),
                timestamp = item.optLong("ts", item.getLong("id"))
            )
        }

        val parsedAssetPlans = legacyAssetPlans.toMutableList()
        val assetArray = root.optJSONArray("assetPlans") ?: org.json.JSONArray()
        for (i in 0 until assetArray.length()) {
            val plan = parseAssetPlan(assetArray.getJSONObject(i), i)
            validateAssetPlan(plan)
            parsedAssetPlans += plan
        }

        ensureUniqueIds(parsedGroups, CardGroup::id, "分组")
        ensureUniqueIds(parsedCards, Card::id, "卡片")
        ensureUniqueIds(parsedTasks, Task::id, "任务")
        ensureUniqueIds(parsedPiggyEntries, { it.id.toString() }, "小金库记录")
        ensureUniqueIds(parsedAssetPlans, AssetPlan::id, "投资项目")

        val settingsByKey = linkedMapOf<String, AppSetting>()
        val settingsArray = root.optJSONArray("settings")
        if (settingsArray != null) {
            for (i in 0 until settingsArray.length()) {
                val item = settingsArray.optJSONObject(i) ?: continue
                val key = item.optString("key", "")
                if (key !in exportedSettingKeys || key == "custom_font_path") continue
                var value = item.optString("value", "")
                if (key == "custom_font_name") {
                    if (!hasImportedFontFile || restoredFontName == null) continue
                    value = restoredFontName
                }
                settingsByKey[key] = AppSetting(key, value)
            }
        }
        if ("tabVisibilityAllTabs" !in settingsByKey) {
            settingsByKey["visibleOptionalTabs"]?.let { legacySetting ->
                val legacyVisible = legacySetting.value.split(",")
                    .map { it.trim() }
                    .filter { it in setOf("calendar", "piggy") }
                    .toSet()
                val migratedVisible = listOf("cards", "calendar", "piggy", "data")
                    .filter { it in legacyVisible || it == "cards" || it == "data" }
                    .joinToString(",")
                settingsByKey["visibleOptionalTabs"] = AppSetting(
                    "visibleOptionalTabs",
                    migratedVisible
                )
                settingsByKey["tabVisibilityAllTabs"] = AppSetting(
                    "tabVisibilityAllTabs",
                    "true"
                )
            }
        }
        settingsByKey.putIfAbsent(
            "showCreditLimitOverview",
            AppSetting("showCreditLimitOverview", "true")
        )
        settingsByKey.putIfAbsent(
            "creditLimitGroupMode",
            AppSetting("creditLimitGroupMode", "card")
        )
        if (hasImportedFontFile && restoredFontName != null) {
            settingsByKey["custom_font_path"] = AppSetting("custom_font_path", importedFontFile.absolutePath)
            settingsByKey["custom_font_name"] = AppSetting("custom_font_name", restoredFontName)
        }

        return ParsedBackup(
            groups = parsedGroups,
            cards = parsedCards,
            tasks = parsedTasks,
            piggyEntries = parsedPiggyEntries,
            assetPlans = parsedAssetPlans,
            settings = settingsByKey.values.toList()
        )
    }

    private fun applyImportedPreferences(
        ctx: Context,
        settings: List<AppSetting>,
        hasImportedFontFile: Boolean,
        importedFontFile: File,
        restoredFontName: String?,
        overwrite: Boolean
    ) {
        val settingsEdit = ctx.getSharedPreferences("cm_settings", Context.MODE_PRIVATE).edit()
        settings.filter { it.key != "custom_font_path" }.forEach {
            settingsEdit.putString(it.key, it.value)
        }
        settingsEdit.apply()

        val fontEdit = ctx.getSharedPreferences("cm_font", Context.MODE_PRIVATE).edit()
        if (hasImportedFontFile && restoredFontName != null) {
            fontEdit
                .putString("font_path", importedFontFile.absolutePath)
                .putString("font_name", restoredFontName)
                .apply()
        } else if (overwrite) {
            fontEdit.clear().apply()
        }
    }

    private fun readBytesLimited(input: java.io.InputStream, maxBytes: Int, label: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw Exception("${label}过大")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun <T> ensureUniqueIds(items: List<T>, id: (T) -> String, label: String) {
        val seen = mutableSetOf<String>()
        items.forEach { item ->
            val value = id(item)
            if (value.isBlank() || !seen.add(value)) throw Exception("备份文件包含无效或重复的${label} ID")
        }
    }

    private fun requireFinite(value: Double, label: String): Double {
        if (!value.isFinite()) throw Exception("备份文件中的${label}无效")
        return value
    }

    private fun validateAssetPlan(plan: AssetPlan) {
        requireFinite(plan.initialCapital, "投资项目初始金额")
        requireFinite(plan.frozenAmount, "投资项目归档金额")
        validateAssetArray("定投计划", plan.ratePlansJson, amountRequired = true)
        validateAssetArray("旧暂停区间", plan.pauseRangesJson, amountRequired = false)
        validateAssetArray("资金调整", plan.adjustmentsJson, amountRequired = true)
        validateAssetArray("流水覆盖", plan.overridesJson, amountRequired = true)
    }

    private fun validateAssetArray(label: String, raw: String, amountRequired: Boolean) {
        try {
            val array = org.json.JSONArray(raw.ifBlank { "[]" })
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                    ?: throw Exception("${label}第 ${i + 1} 项不是对象")
                if (amountRequired) {
                    requireFinite(item.getDouble("amount"), "${label}第 ${i + 1} 项金额")
                }
            }
        } catch (error: Exception) {
            throw Exception("备份文件中的${label}数据无效", error)
        }
    }

    suspend fun exportData(): String {
        val grps = groups.first(); val cds = cards.first()
        val tsks = tasks.first().filterNot { it.isInvest }; val pig = piggyEntries.first()
        val plans = assetPlans.first()
        fun esc(s: String) = jsonEscape(s)
        val sb = StringBuilder("{")
        sb.append("\"groups\":[${grps.joinToString(",") { g -> "{\"id\":\"${g.id}\",\"name\":\"${esc(g.name)}\",\"icon\":\"${g.icon}\",\"isOpen\":${g.isOpen},\"order\":${g.sortOrder}}" }}]")
        sb.append(",\"cards\":[${cds.joinToString(",") { c -> "{\"id\":\"${c.id}\",\"gid\":\"${c.groupId}\",\"bank\":\"${esc(c.bank)}\",\"network\":\"${c.network}\",\"currency\":\"${c.currency}\",\"tail\":\"${c.tail}\",\"role\":\"${esc(c.role)}\",\"note\":\"${esc(c.note)}\",\"status\":\"${c.status}\",\"isVirtual\":${c.isVirtual},\"noCard\":${c.noCard},\"logo\":\"${c.logoEmoji}\",\"order\":${c.sortOrder},\"expiryDate\":\"${esc(c.expiryDate)}\",\"cardCategory\":\"${esc(c.cardCategory)}\",\"imageOrientation\":\"${esc(c.imageOrientation)}\",\"creditLimit\":${c.creditLimit},\"billingDay\":${c.billingDay},\"repaymentDay\":${c.repaymentDay}}" }}]")
        val monthly = tsks.filter { it.freq=="monthly" }; val weekly = tsks.filter { it.freq=="weekly" }
        val quarterly = tsks.filter { it.freq=="quarterly" }; val ndays = tsks.filter { it.freq=="ndays" }
        val once = tsks.filter { it.freq=="once" }
        fun tj(t: Task) = "{\"id\":\"${t.id}\",\"name\":\"${esc(t.name)}\",\"freq\":\"${t.freq}\",\"cardId\":\"${t.cardId}\",\"isInvest\":${t.isInvest},\"investAmount\":${t.investAmount},\"day\":${t.day},\"weekday\":${t.weekday},\"months\":\"${t.months}\",\"ndays\":${t.ndays},\"startDate\":\"${t.startDate}\",\"date\":\"${t.date}\",\"holidays\":\"${esc(t.holidays)}\"}"
        sb.append(",\"monthlyTasks\":[${monthly.joinToString(",") { tj(it) }}]")
        sb.append(",\"weeklyTasks\":[${weekly.joinToString(",") { tj(it) }}]")
        sb.append(",\"quarterlyTasks\":[${quarterly.joinToString(",") { tj(it) }}]")
        sb.append(",\"ndaysTasks\":[${ndays.joinToString(",") { tj(it) }}]")
        sb.append(",\"onceTasks\":[${once.joinToString(",") { tj(it) }}]")
        sb.append(",\"pig\":[${pig.joinToString(",") { e -> "{\"id\":${e.id},\"amount\":${e.amount},\"desc\":\"${esc(e.desc)}\",\"cardId\":\"${e.cardId}\",\"date\":\"${e.date}\",\"ts\":${e.timestamp}}" }}]}")
        sb.insert(sb.length - 1, ",\"assetPlans\":[${plans.joinToString(",") { p -> assetPlanJson(p, ::esc) }}]")
        return sb.toString()
    }

    private fun assetPlanFromTask(task: Task, index: Int): AssetPlan {
        val start = task.startDate.ifBlank { task.date.ifBlank { java.time.LocalDate.now().toString() } }
        val amount = kotlin.math.abs(task.investAmount)
        val cycle = when (task.freq) {
            "once" -> 0
            "weekly" -> 7
            "monthly" -> 30
            "ndays" -> task.ndays.coerceAtLeast(1)
            else -> 90
        }
        val avoidsNonTrading = task.holidays != TaskHolidayPolicy.ALLOW_NON_TRADING_DAYS
        return AssetPlan(
            id = "task_${task.id.ifBlank { java.util.UUID.randomUUID().toString() }}",
            name = task.name,
            cardId = task.cardId,
            currency = "CNY",
            ratePlansJson = AssetPlanCodec.encodeRatePlans(listOf(AssetRatePlan(start, amount))),
            pauseRangesJson = "",
            adjustmentsJson = AssetPlanCodec.encodeAdjustments(emptyList()),
            overridesJson = AssetPlanCodec.encodeOverrides(emptyList()),
            cycleDays = cycle,
            monthlyDay = if (task.freq == "monthly") task.day.coerceIn(1, 31) else 0,
            weeklyDay = if (task.freq == "weekly") task.weekday.coerceIn(1, 7) else 0,
            postponeNonTrading = avoidsNonTrading,
            includeFirstDay = true,
            countInTotal = true,
            skipWeekends = avoidsNonTrading,
            orderIndex = System.currentTimeMillis() - index,
            startDate = start,
            initialDate = start
        )
    }

    private fun parseAssetPlan(o: org.json.JSONObject, index: Int): AssetPlan =
        AssetPlan(
            id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            name = o.optString("name", ""),
            cardId = o.optString("cardId", ""),
            platform = o.optString("platform", ""),
            category = o.optString("category", ""),
            code = o.optString("code", ""),
            currency = o.optString("currency", "CNY"),
            initialCapital = o.optDouble("initialCapital", 0.0),
            initialDate = o.optString("initialDate", ""),
            ratePlansJson = o.optString("ratePlansJson", ""),
            pauseRangesJson = o.optString("pauseRangesJson", ""),
            adjustmentsJson = o.optString("adjustmentsJson", ""),
            overridesJson = o.optString("overridesJson", ""),
            cycleDays = o.optInt("cycleDays", 1),
            monthlyDay = o.optInt("monthlyDay", 0),
            weeklyDay = o.optInt("weeklyDay", 0),
            skipMissingMonthlyDate = o.optBoolean("skipMissingMonthlyDate", false),
            postponeNonTrading = o.optBoolean("postponeNonTrading", false),
            includeFirstDay = o.optBoolean("includeFirstDay", true),
            status = o.optString("status", AssetPlanStatus.RUNNING),
            frozenAmount = o.optDouble("frozenAmount", 0.0),
            countInTotal = o.optBoolean("countInTotal", true),
            skipWeekends = o.optBoolean("skipWeekends", true),
            orderIndex = o.optLong("orderIndex", index.toLong()),
            startDate = o.optString("startDate", "")
        )

    private fun assetPlanJson(p: AssetPlan, esc: (String) -> String): String =
        "{\"id\":\"${esc(p.id)}\",\"name\":\"${esc(p.name)}\",\"cardId\":\"${esc(p.cardId)}\",\"platform\":\"${esc(p.platform)}\"," +
            "\"category\":\"${esc(p.category)}\",\"code\":\"${esc(p.code)}\",\"currency\":\"${esc(p.currency)}\"," +
            "\"initialCapital\":${p.initialCapital},\"initialDate\":\"${esc(p.initialDate)}\"," +
            "\"ratePlansJson\":\"${esc(p.ratePlansJson)}\",\"pauseRangesJson\":\"${esc(p.pauseRangesJson)}\"," +
            "\"adjustmentsJson\":\"${esc(p.adjustmentsJson)}\",\"overridesJson\":\"${esc(p.overridesJson)}\"," +
            "\"cycleDays\":${p.cycleDays},\"monthlyDay\":${p.monthlyDay},\"weeklyDay\":${p.weeklyDay}," +
            "\"skipMissingMonthlyDate\":${p.skipMissingMonthlyDate},\"postponeNonTrading\":${p.postponeNonTrading}," +
            "\"includeFirstDay\":${p.includeFirstDay},\"status\":\"${esc(p.status)}\",\"frozenAmount\":${p.frozenAmount}," +
            "\"countInTotal\":${p.countInTotal},\"skipWeekends\":${p.skipWeekends},\"orderIndex\":${p.orderIndex}," +
            "\"startDate\":\"${esc(p.startDate)}\"}"

    private fun deleteImagesIfOwned(card: Card) {
        val ctx = context ?: return
        ImageStore.deleteOwned(ctx, card.logoImagePath)
        ImageStore.deleteOwned(ctx, card.bankLogoPath)
    }

    private fun safeChildFile(parent: File, name: String): File? {
        if (name.isBlank()) return null
        if (name.contains('/') || name.contains('\\')) return null
        val file = File(parent, name).canonicalFile
        val base = parent.canonicalFile
        return if (file.path.startsWith(base.path + File.separator)) file else null
    }

    private fun importedImagePath(parent: File, name: String, availableNames: Set<String>): String =
        safeChildFile(parent, name)?.takeIf { it.name in availableNames }?.absolutePath ?: ""

    private fun normalizedOrientation(value: String): String =
        if (value == "vertical") "vertical" else "horizontal"

    private fun jsonEscape(value: String): String {
        val quoted = org.json.JSONObject.quote(value)
        return quoted.substring(1, quoted.length - 1)
    }
}
