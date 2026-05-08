package com.example.ukfloodsdemographics

fun isAddFloodFormMissingRequiredFields(postcodeTrimmed: String, severityTrimmed: String): Boolean =
    postcodeTrimmed.isBlank() || severityTrimmed.isBlank()

fun addFloodMissingFieldsMessage(language: Language): String = when (language) {
    Language.EN -> "Postcode and severity are required."
    Language.PL -> "Kod pocztowy i poziom zagrozenia sa wymagane."
}
