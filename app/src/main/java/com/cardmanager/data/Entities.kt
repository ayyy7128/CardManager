package com.cardmanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "card_groups")
data class CardGroup(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String = "💳",
    val isOpen: Boolean = true,
    val sortOrder: Int = 0
)

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey val id: String,
    val groupId: String,
    val bank: String,
    val network: String = "银联",
    val currency: String = "CNY",
    val tail: String = "",
    val role: String = "",
    val note: String = "",
    val status: String = "active",
    val isVirtual: Boolean = false,
    val noCard: Boolean = false,
    val logoEmoji: String = "",
    val logoImagePath: String = "",
    val bankLogoPath: String = "",  // 银行 logo 图片路径
    val cardTypeName: String = "",  // 卡种名称，如"龙卡通""白金卡"
    val expiryDate: String = "",   // 有效期 格式 MM/yy
    val cardCategory: String = "",  // 卡类型：储蓄卡 / 信用卡（纯账户留空）
    val sortOrder: Int = 0,
    val imageOrientation: String = "horizontal", // 卡面方向：horizontal / vertical
    val creditLimit: Double = 0.0,  // 信用卡额度
    val billingDay: Int = 0,        // 信用卡账单日，0 表示未设置
    val repaymentDay: Int = 0       // 信用卡还款日，0 表示未设置
)

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String,
    val name: String,
    val freq: String,
    val cardId: String = "",
    val isInvest: Boolean = false,
    val investAmount: Double = 0.0,
    val day: Int = 1,
    val weekday: Int = 1,
    val months: String = "",
    val ndays: Int = 7,
    val startDate: String = "",
    val date: String = "",
    val holidays: String = ""
)

object TaskHolidayPolicy {
    const val AVOID_NON_TRADING_DAYS = "avoidNonTradingDays"
    const val ALLOW_NON_TRADING_DAYS = "allowNonTradingDays"

    fun avoidsNonTradingDays(task: Task): Boolean =
        task.isInvest && task.holidays != ALLOW_NON_TRADING_DAYS
}

@Entity(tableName = "piggy_entries")
data class PiggyEntry(
    @PrimaryKey val id: Long,
    val amount: Double,
    val desc: String = "",
    val cardId: String = "",
    val date: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "asset_plans")
data class AssetPlan(
    @PrimaryKey val id: String,
    val name: String = "",
    val cardId: String = "",
    val platform: String = "",
    val category: String = "",
    val code: String = "",
    val currency: String = "CNY",
    val initialCapital: Double = 0.0,
    val initialDate: String = "",
    val ratePlansJson: String = "",
    val pauseRangesJson: String = "",
    val adjustmentsJson: String = "",
    val overridesJson: String = "",
    val cycleDays: Int = 1,
    val monthlyDay: Int = 0,
    val weeklyDay: Int = 0,
    val skipMissingMonthlyDate: Boolean = false,
    val postponeNonTrading: Boolean = false,
    val includeFirstDay: Boolean = true,
    val status: String = AssetPlanStatus.RUNNING,
    val frozenAmount: Double = 0.0,
    val countInTotal: Boolean = true,
    val skipWeekends: Boolean = true,
    val orderIndex: Long = 0,
    val startDate: String = ""
)

object AssetPlanStatus {
    const val RUNNING = "running"
    const val STOPPED = "stopped"
}

object AssetLogType {
    const val INITIAL = "initial"
    const val PERIODIC = "periodic"
    const val ADJUSTMENT = "adjustment"
    const val SKIP_WEEKEND = "skipWeekend"
    const val SKIP_PAUSE = "skipPause"
    const val POSTPONED = "postponed"
}

data class AssetRatePlan(val startDate: String, val amount: Double)
data class AssetAdjustment(val date: String, val amount: Double, val note: String = "")
data class AssetOverrideLog(
    val date: String,
    val amount: Double,
    val status: String,
    val note: String = "",
    val isDeleted: Boolean = false,
    val type: String = ""
)
data class AssetTransactionLog(
    val date: String,
    val amount: Double,
    val status: String,
    val currency: String,
    val type: String,
    val note: String = ""
)
data class AssetCalcResult(val amount: Double, val logs: List<AssetTransactionLog>)

@Entity(tableName = "settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)
