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
    val imageOrientation: String = "horizontal" // 卡面方向：horizontal / vertical
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

@Entity(tableName = "settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)
