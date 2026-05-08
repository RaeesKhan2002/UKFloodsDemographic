package com.example.ukfloodsdemographics

fun isLoginFormMissingCredentials(email: String, password: String): Boolean =
    email.isBlank() || password.isBlank()

fun loginMissingCredentialsMessage(language: Language): String = when (language) {
    Language.EN -> "Enter email and password."
    Language.PL -> "Podaj e-mail i hasło."
}
