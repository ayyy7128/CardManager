package com.cardmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AssetCalculatorTest {
    private val today = LocalDate.of(2026, 7, 14)

    @Test
    fun futureInitialCapitalIsNotIncluded() {
        val plan = AssetPlan(
            id = "future-initial",
            initialCapital = 1000.0,
            initialDate = today.plusDays(1).toString(),
            startDate = today.plusDays(1).toString()
        )

        val result = AssetCalculator.calc(plan, today)

        assertEquals(0.0, result.amount, 0.0)
        assertTrue(result.logs.isEmpty())
    }

    @Test
    fun futureAdjustmentIsNotIncluded() {
        val plan = AssetPlan(
            id = "future-adjustment",
            initialCapital = 1000.0,
            initialDate = today.minusDays(1).toString(),
            startDate = today.minusDays(1).toString(),
            adjustmentsJson = AssetPlanCodec.encodeAdjustments(
                listOf(
                    AssetAdjustment(today.toString(), -100.0, "effective"),
                    AssetAdjustment(today.plusDays(1).toString(), 500.0, "future")
                )
            )
        )

        val result = AssetCalculator.calc(plan, today)

        assertEquals(900.0, result.amount, 0.0)
        assertEquals(listOf("初始资金", "资金调整"), result.logs.map { it.status })
    }
}
