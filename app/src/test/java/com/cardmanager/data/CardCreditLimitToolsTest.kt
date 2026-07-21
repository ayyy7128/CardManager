package com.cardmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CardCreditLimitToolsTest {
    @Test
    fun emptyJsonFallsBackToLegacyLimit() {
        assertEquals(
            listOf(CardCreditLimit("HKD", 20_000.0)),
            CardCreditLimitTools.decode("", "HKD", 20_000.0)
        )
    }

    @Test
    fun multipleCurrenciesRoundTrip() {
        val limits = listOf(
            CardCreditLimit("CNY", 10_000.0),
            CardCreditLimit("HKD", 20_000.0)
        )
        val encoded = CardCreditLimitTools.encode(limits)

        assertEquals(limits, CardCreditLimitTools.decode(encoded, "CNY", 0.0))
        assertEquals(10_000.0, CardCreditLimitTools.legacyAmount(limits, "CNY"), 0.0)
    }

    @Test
    fun duplicateCurrencyIsRejected() {
        val raw = """[{"currency":"CNY","amount":1000},{"currency":"CNY","amount":2000}]"""

        assertThrows(IllegalArgumentException::class.java) {
            CardCreditLimitTools.requireValid(raw)
        }
    }
}
