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
    version = 11,
    exportSchema = false
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
                .addMigrations(M_1_2, M_2_3, M_3_4, M_4_5, M_5_6, M_6_7, M_7_8, M_8_9, M_9_10, M_10_11)
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
        "visibleDataCharts",
        "dataChartOrder",
        "visibleDataOverview",
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
        val hasPassword = !password.isNullOrBlank()
        val flags = if (hasPassword) passwordBackupFlag else 0
        val salt = if (hasPassword) ByteArray(16).also { java.security.SecureRandom().nextBytes(it) } else ByteArray(0)
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val key = if (hasPassword) passwordBackupKey(password.orEmpty(), salt) else appBackupKey()
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
            appBackupKey()
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
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, appBackupKey(), javax.crypto.spec.GCMParameterSpec(128, iv))
        cipher.updateAAD(legacyEncryptedBackupMagic)
        return runCatching { cipher.doFinal(cipherBytes) }
            .getOrElse { throw Exception("备份文件已损坏", it) }
    }

    private fun isEncryptedBackup(bytes: ByteArray): Boolean =
        hasMagic(bytes, encryptedBackupMagic) || hasMagic(bytes, legacyEncryptedBackupMagic)

    private fun hasMagic(bytes: ByteArray, magic: ByteArray): Boolean =
        bytes.size >= magic.size && magic.indices.all { bytes[it] == magic[it] }

    private fun appBackupKey(): javax.crypto.SecretKey {
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
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
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
            "\"creditLimit\":${c.creditLimit},\"billingDay\":${c.billingDay}}"
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
        var hasImportedFontFile = false

        val rawBytes = ctx.contentResolver.openInputStream(uri)?.buffered()?.use { it.readBytes() }
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
                            entry.name == "data.json" ->
                                jsonStr = zip.readBytes().toString(Charsets.UTF_8)
                            entry.name.startsWith("images/") -> {
                                val name = entry.name.removePrefix("images/")
                                if (name.isNotEmpty()) imageMap[name] = zip.readBytes()
                            }
                            entry.name == "fonts/custom_font.ttf" -> {
                                val bytes = zip.readBytes()
                                importedFontFile.writeBytes(bytes)
                                hasImportedFontFile = true
                            }
                            entry.name == "fonts/font_meta.txt" -> {
                                val fontName = zip.readBytes().toString(Charsets.UTF_8)
                                importedFontName = fontName
                            }
                        }
                    }
                    zip.closeEntry(); entry = zip.nextEntry
                }
            }
        }

        if (mode == ImportMode.OVERWRITE) {
            val oldCards = cards.first()
            db.withTransaction {
                db.taskDao().deleteAll()
                db.piggyDao().deleteAll()
                db.assetPlanDao().deleteAll()
                db.cardDao().deleteAll()
                db.groupDao().deleteAll()
            }
            oldCards.forEach { deleteImagesIfOwned(it) }
            if (!hasImportedFontFile) {
                importedFontFile.delete()
                ctx.getSharedPreferences("cm_font", android.content.Context.MODE_PRIVATE)
                    .edit().clear().apply()
                db.settingDao().set(AppSetting("custom_font_path", ""))
                db.settingDao().set(AppSetting("custom_font_name", ""))
            }
        }

        val restoredFontName = importedFontName
        if (hasImportedFontFile && restoredFontName != null) {
            ctx.getSharedPreferences("cm_font", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("font_path", importedFontFile.absolutePath)
                .putString("font_name", restoredFontName)
                .apply()
            db.settingDao().set(AppSetting("custom_font_path", importedFontFile.absolutePath))
            db.settingDao().set(AppSetting("custom_font_name", restoredFontName))
        }

        // 解压图片
        imageMap.forEach { (name, bytes) ->
            safeChildFile(imgDir, name)?.writeBytes(bytes)
        }

        val root = org.json.JSONObject(
            jsonStr ?: throw Exception("备份文件格式错误：未找到 data.json"))

        importSettings(ctx, root.optJSONArray("settings"), hasImportedFontFile, importedFontFile, restoredFontName)

        // Groups
        val grpsArr = root.optJSONArray("groups") ?: org.json.JSONArray()
        for (i in 0 until grpsArr.length()) {
            val g = grpsArr.getJSONObject(i)
            db.groupDao().insert(CardGroup(
                id = g.getString("id"), name = g.getString("name"),
                icon = g.optString("icon", "💳"),
                isOpen = g.optBoolean("isOpen", true),
                sortOrder = g.optInt("order", i)))
        }

        // Cards
        val cdsArr = root.optJSONArray("cards") ?: org.json.JSONArray()
        for (i in 0 until cdsArr.length()) {
            val c = cdsArr.getJSONObject(i)
            val lf = c.optString("logoFile", "")
            val bf = c.optString("bankLogoFile", "")
            db.cardDao().insert(Card(
                id = c.getString("id"), groupId = c.getString("gid"),
                bank = c.getString("bank"), network = c.optString("network", "银联"),
                currency = c.optString("currency", "CNY"), tail = c.optString("tail", ""),
                role = c.optString("role", ""), note = c.optString("note", ""),
                status = c.optString("status", "active"),
                isVirtual = c.optBoolean("isVirtual", false),
                noCard = c.optBoolean("noCard", false),
                logoEmoji = c.optString("logo", ""),
                logoImagePath = safeImportedImagePath(imgDir, lf),
                bankLogoPath  = safeImportedImagePath(imgDir, bf),
                cardTypeName  = c.optString("cardTypeName", ""),
                expiryDate    = c.optString("expiryDate", ""),
                cardCategory  = c.optString("cardCategory", ""),
                sortOrder = c.optInt("order", i),
                imageOrientation = normalizedOrientation(c.optString("imageOrientation", "horizontal")),
                creditLimit = c.optDouble("creditLimit", 0.0).coerceAtLeast(0.0),
                billingDay = c.optInt("billingDay", 0).takeIf { it in 1..31 } ?: 0))
        }

        // Tasks
        var taskCount = 0
        fun parseTask(t: org.json.JSONObject) = Task(
            id = t.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
            name = t.optString("name", "未命名"), freq = t.getString("freq"),
            cardId = t.optString("cardId", ""),
            isInvest = t.optBoolean("isInvest", false),
            investAmount = t.optDouble("investAmount", 0.0),
            day = t.optInt("day", 1), weekday = t.optInt("weekday", 1),
            months = t.optString("months", ""), ndays = t.optInt("ndays", 7).coerceAtLeast(1),
            startDate = t.optString("startDate", ""), date = t.optString("date", ""),
            holidays = t.optString("holidays", ""))
        listOf("monthlyTasks","weeklyTasks","quarterlyTasks","ndaysTasks","onceTasks").forEach { key ->
            val arr = root.optJSONArray(key) ?: return@forEach
            for (i in 0 until arr.length()) {
                val task = parseTask(arr.getJSONObject(i))
                if (task.isInvest && kotlin.math.abs(task.investAmount) > 0.0) {
                    db.assetPlanDao().insert(assetPlanFromTask(task, i))
                } else if (!task.isInvest) {
                    db.taskDao().insert(task)
                    taskCount++
                }
            }
        }

        // Piggy
        val pigArr = root.optJSONArray("pig") ?: org.json.JSONArray()
        for (i in 0 until pigArr.length()) {
            val e = pigArr.getJSONObject(i)
            db.piggyDao().insert(PiggyEntry(
                id = e.getLong("id"), amount = e.getDouble("amount"),
                desc = e.optString("desc", ""), cardId = e.optString("cardId", ""),
                date = e.getString("date"), timestamp = e.optLong("ts", e.getLong("id"))))
        }

        val assetArr = root.optJSONArray("assetPlans") ?: org.json.JSONArray()
        for (i in 0 until assetArr.length()) {
            db.assetPlanDao().insert(parseAssetPlan(assetArr.getJSONObject(i), i))
        }

        return ImportSummary(grpsArr.length(), cdsArr.length(), taskCount, pigArr.length(), assetArr.length())
    }

    private suspend fun importSettings(
        ctx: android.content.Context,
        settingsArr: org.json.JSONArray?,
        hasImportedFontFile: Boolean,
        importedFontFile: File,
        restoredFontName: String?
    ) {
        if (settingsArr == null) return
        val prefsEdit = ctx.getSharedPreferences("cm_settings", android.content.Context.MODE_PRIVATE).edit()
        for (i in 0 until settingsArr.length()) {
            val item = settingsArr.optJSONObject(i) ?: continue
            val key = item.optString("key", "")
            if (key !in exportedSettingKeys || key == "custom_font_path") continue
            var value = item.optString("value", "")
            if (key == "custom_font_name") {
                if (!hasImportedFontFile || restoredFontName.isNullOrBlank()) continue
                value = restoredFontName
            }
            db.settingDao().set(AppSetting(key, value))
            prefsEdit.putString(key, value)
        }
        if (hasImportedFontFile && !restoredFontName.isNullOrBlank()) {
            db.settingDao().set(AppSetting("custom_font_path", importedFontFile.absolutePath))
            db.settingDao().set(AppSetting("custom_font_name", restoredFontName))
        }
        prefsEdit.apply()
    }

    suspend fun exportData(): String {
        val grps = groups.first(); val cds = cards.first()
        val tsks = tasks.first().filterNot { it.isInvest }; val pig = piggyEntries.first()
        val plans = assetPlans.first()
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val sb = StringBuilder("{")
        sb.append("\"groups\":[${grps.joinToString(",") { g -> "{\"id\":\"${g.id}\",\"name\":\"${esc(g.name)}\",\"icon\":\"${g.icon}\",\"isOpen\":${g.isOpen},\"order\":${g.sortOrder}}" }}]")
        sb.append(",\"cards\":[${cds.joinToString(",") { c -> "{\"id\":\"${c.id}\",\"gid\":\"${c.groupId}\",\"bank\":\"${esc(c.bank)}\",\"network\":\"${c.network}\",\"currency\":\"${c.currency}\",\"tail\":\"${c.tail}\",\"role\":\"${esc(c.role)}\",\"note\":\"${esc(c.note)}\",\"status\":\"${c.status}\",\"isVirtual\":${c.isVirtual},\"noCard\":${c.noCard},\"logo\":\"${c.logoEmoji}\",\"order\":${c.sortOrder},\"expiryDate\":\"${esc(c.expiryDate)}\",\"cardCategory\":\"${esc(c.cardCategory)}\",\"imageOrientation\":\"${esc(c.imageOrientation)}\",\"creditLimit\":${c.creditLimit},\"billingDay\":${c.billingDay}}" }}]")
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

    private fun safeImportedImagePath(parent: File, name: String): String =
        safeChildFile(parent, name)?.takeIf { it.exists() }?.absolutePath ?: ""

    private fun normalizedOrientation(value: String): String =
        if (value == "vertical") "vertical" else "horizontal"
}
