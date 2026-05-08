package com.example.ukfloodsdemographics



import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

 // Unit tests for "Add flood alert" validation

class AddFloodTest {

    @Test
    fun emptyPostcodeAndSeverity_isMissingRequiredFields() {
        assertTrue(isAddFloodFormMissingRequiredFields("", ""))
    }

    @Test
    fun blankWhitespaceOnly_isMissingRequiredFields() {
        assertTrue(isAddFloodFormMissingRequiredFields("   ", "\t"))


    }

    @Test
    fun postcodeOnly_isMissingRequiredFields() {
        assertTrue(isAddFloodFormMissingRequiredFields("SW1A 1AA", ""))


    }

    @Test
    fun severityOnly_isMissingRequiredFields() {
        assertTrue(isAddFloodFormMissingRequiredFields("", "High"))

    }

    @Test
    fun postcodeAndSeverityPresent_isNotMissing() {
        assertFalse(isAddFloodFormMissingRequiredFields("SW1A 1AA", "High"))
    }

    @Test
    fun missingFields_englishMessage() {
        assertEquals(
            "Postcode and severity are required.",
            addFloodMissingFieldsMessage(Language.EN),
        )
    }



    @Test
    fun missingFields_polishMessage() {
        assertEquals(
            "Kod pocztowy i poziom zagrozenia sa wymagane.",
            addFloodMissingFieldsMessage(Language.PL),
        )
    }
}
