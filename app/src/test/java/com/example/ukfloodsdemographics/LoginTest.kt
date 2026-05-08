package com.example.ukfloodsdemographics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


 // Unit tests for login form validation

class LoginTest {

    @Test
    fun emptyEmailAndPassword_isMissingCredentials() {
        assertTrue(isLoginFormMissingCredentials("", ""))
    }

    @Test
    fun blankOnlyEmailAndPassword_isMissingCredentials() {
        assertTrue(isLoginFormMissingCredentials("   ", "\t"))
    }


    @Test
    fun filledEmailAndPassword_isNotMissingCredentials() {
        assertFalse(isLoginFormMissingCredentials("a@b.com", "secret"))
    }


    @Test
    fun emptyFields_englishMessage() {
        assertEquals(
            "Enter email and password.",
            loginMissingCredentialsMessage(Language.EN),
        )
    }


    @Test
    fun emptyFields_polishMessage() {
        assertEquals(
            "Podaj e-mail i hasło.",
            loginMissingCredentialsMessage(Language.PL),
        )
    }
}
