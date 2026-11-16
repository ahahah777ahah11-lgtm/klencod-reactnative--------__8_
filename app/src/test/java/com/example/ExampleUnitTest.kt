package com.example

import com.example.data.assistant.LocalSmartAssistant
import com.example.data.local.CustomerWithBalance
import com.example.data.local.DashboardSummary
import com.example.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `arabic numerals and transfer references are parsed safely`() {
        assertEquals(1250.5, FormatUtils.parsePositiveAmount("١٬٢٥٠٫٥".replace("٬", ",")) ?: 0.0, 0.01)
        assertEquals(5000.0, FormatUtils.parsePositiveAmount("5,000") ?: 0.0, 0.01)
        // Reference numbers like ت/8000011 must never be interpreted as monetary amounts
        assertNull(FormatUtils.parsePositiveAmount("ت/8000011"))
        assertEquals("777123456", FormatUtils.normalizePhone("٧٧٧-١٢٣-٤٥٦"))
    }

    @Test
    fun `local smart assistant summarizes debts and top debtors offline`() {
        val assistant = LocalSmartAssistant()
        val customers = listOf(
            CustomerWithBalance(1L, "محمد علي", "771000001", "A1", "", 0.0, 0L, 0L, false, 10000.0, 2000.0, 8000.0, 2, 0L, 0),
            CustomerWithBalance(2L, "سعيد حسن", "771000002", "A2", "", 0.0, 0L, 0L, false, 3000.0, 3000.0, 0.0, 2, 0L, 0),
        )
        val summary = DashboardSummary(
            totalCustomers = 2,
            activeDebtorsCount = 1,
            settledCustomersCount = 1,
            totalDebts = 13000.0,
            totalPayments = 5000.0,
            netOutstandingBalance = 8000.0,
        )
        val reply = assistant.answerQuery("أعلى عملاء مديونية", summary, customers, emptyList(), emptyList(), emptyList(), "ر.ي")
        assertTrue(reply.text.contains("محمد علي"))
        assertEquals(1, reply.highlightedCustomers.size)
    }
}
