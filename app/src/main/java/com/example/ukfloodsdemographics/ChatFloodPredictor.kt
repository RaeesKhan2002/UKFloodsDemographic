package com.example.ukfloodsdemographics



import java.util.Locale
import kotlin.math.absoluteValue



private data class PredictionMatch(
    val postcode: String,
    val predictedRisk: String,
    val confidence: Int,
    val sampleSize: Int
)



object ChatFloodPredictor {
    private val fullPostcodeRegex = Regex("\\b([A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2})\\b")
    private val outcodeRegex = Regex("\\b([A-Z]{1,2}\\d[A-Z\\d]?)\\b")




    fun buildReply(
        question: String,
        language: Language,
        floodRows: List<FloodRiskEntry>,
        isDataLoading: Boolean,
        lastBotReply: String? = null,
        lastUserMessage: String? = null
    ): String {
        val q = question.lowercase(Locale.UK)
        if (isGreeting(q)) {
            return when (language) {
                Language.EN -> "Hi, how can I help you?"
                Language.PL -> "Czesc, jak moge Ci pomoc?"
            }
        }



        val rainLevel = parseRainLevel(q)
        val hasPostcodeNow = hasPostcode(question)
        if (hasPostcodeNow && rainLevel == null) {
            return when (language) {
                Language.EN -> "Thanks. How would you describe current rain there: low, medium, or high?"
                Language.PL -> "Dziekuje. Jak oceniasz obecny deszcz: niski, sredni czy wysoki?"
            }
        }

        

        if (rainLevel != null && !hasPostcodeNow) {
            val combined = listOfNotNull(lastUserMessage, question).joinToString(" ")
            if (hasPostcode(combined)) {
                val prediction = predictFromQuestion(combined, floodRows, rainLevel)
                if (prediction != null) {
                    return predictionResponse(prediction, rainLevel, language)
                }


            }
            return when (language) {
                Language.EN -> "Please provide a postcode first, then I can use your rain level."
                Language.PL -> "Najpierw podaj kod pocztowy, wtedy uwzglednie poziom deszczu."
            }




        }

        val candidateReply: String = if (isPredictionIntent(q)) {
            val prediction = predictFromQuestion(question, floodRows, rainLevel)
            if (prediction != null) {
                predictionResponse(prediction, rainLevel, language)
            } else if (isDataLoading) {
                when (language) {
                    Language.EN -> "I am still loading flood data. Try again in a few seconds with a postcode."
                    Language.PL -> "Wciaz laduje dane o powodziach. Sprobuj ponownie za chwile, podajac kod pocztowy."
                }



            } else {
                when (language) {
                    Language.EN -> "To predict flood risk, send a UK postcode, for example: LS1 4AP."
                    Language.PL -> "Aby przewidziec ryzyko powodzi, podaj brytyjski kod pocztowy, np. LS1 4AP."
                }
            }
        } else {
            when {
                q.contains("safe") || q.contains("bezpiec") -> when (language) {
                    Language.EN -> "Stay informed via local alerts, avoid flood water, and keep an emergency kit ready."
                    Language.PL -> "Sledz lokalne alerty, unikaj wody powodziowej i miej przygotowany zestaw awaryjny."
                }



                

                q.contains("map") || q.contains("mapa") -> when (language) {
                    Language.EN -> "Go to Home to see the flood map and your current risk level."
                    Language.PL -> "Przejdz do strony Glownej, aby zobaczyc mape powodzi i aktualny poziom ryzyka."
                }



                else -> when (language) {
                    Language.EN -> "I can predict flood risk from postcode input, plus help with flood safety and alerts."
                    Language.PL -> "Moge przewidywac ryzyko powodzi po kodzie pocztowym oraz pomagac z alertami."
                }
            }

        }

        return avoidRepetition(
            candidate = candidateReply,
            language = language,
            question = question,
            lastBotReply = lastBotReply
        )
    }

    private fun isPredictionIntent(q: String): Boolean {
        return q.contains("predict") ||
            q.contains("prediction") ||
            q.contains("risk") ||
            q.contains("flood") ||
            q.contains("will it flood") ||
            q.contains("postcode") ||
            q.contains("kod")
    }




    private fun isGreeting(q: String): Boolean {
        val normalized = q.trim()
        return normalized == "hi" ||
            normalized == "hello" ||
            normalized == "hey" ||
            normalized == "czesc" ||
            normalized == "hej"
    }




    private fun predictFromQuestion(
        question: String,
        floodRows: List<FloodRiskEntry>,
        rainLevel: String?
    ): PredictionMatch? {
        if (floodRows.isEmpty()) return null




        val upperQuestion = question.uppercase(Locale.UK)
        val fullPostcode = fullPostcodeRegex.find(upperQuestion)?.groupValues?.get(1)
        val outcode = outcodeRegex.find(upperQuestion)?.groupValues?.get(1)



        val normalizedFull = normalizePostcode(fullPostcode)
        val normalizedOutcode = normalizePostcode(outcode)



        val exactMatches = if (normalizedFull.isNotBlank()) {
            floodRows.filter { normalizePostcode(it.postcode) == normalizedFull }
        } else {
            emptyList()
        }



        val areaMatches = if (exactMatches.isNotEmpty()) {
            exactMatches
        } else if (normalizedOutcode.isNotBlank()) {
            floodRows.filter { normalizePostcode(it.postcode).startsWith(normalizedOutcode) }
        } else {
            emptyList()
        }



        if (areaMatches.isEmpty()) return null



        val grouped = areaMatches.groupingBy { normalizeRiskLabel(it.riskLevel) }.eachCount()
        val baseline = grouped.maxByOrNull { riskRank(it.key) * 1000 + it.value }?.key ?: "Medium"
        val predicted = applyRainAdjustment(baseline, rainLevel)
        val confidence = ((grouped[baseline] ?: 0) * 100 / areaMatches.size).coerceIn(1, 99)



        val displayCode = when {
            normalizedFull.isNotBlank() -> formatPostcode(normalizedFull)
            normalizedOutcode.isNotBlank() -> normalizedOutcode
            else -> areaMatches.first().postcode
        }



        return PredictionMatch(
            postcode = displayCode,
            predictedRisk = predicted,
            confidence = confidence,
            sampleSize = areaMatches.size
        )
    }


    private fun normalizeRiskLabel(risk: String): String {
        val r = risk.lowercase(Locale.UK)
        return when {
            "very high" in r || "severe" in r -> "Very High"
            "high" in r -> "High"
            "medium" in r || "moderate" in r -> "Medium"
            "low" in r -> "Low"
            else -> "Medium"
        }
    }



    private fun riskRank(risk: String): Int = when (risk) {
        "Very High" -> 4
        "High" -> 3
        "Medium" -> 2
        "Low" -> 1
        else -> 0
    }



    private fun riskLabel(rank: Int): String = when (rank.coerceIn(1, 4)) {
        4 -> "Very High"
        3 -> "High"
        2 -> "Medium"
        else -> "Low"
    }



    private fun applyRainAdjustment(baseline: String, rainLevel: String?): String {
        val bump = when (rainLevel) {
            "medium" -> 1
            "high" -> 2
            else -> 0
        }
        return riskLabel(riskRank(baseline) + bump)
    }



    private fun normalizePostcode(postcode: String?): String =
        postcode.orEmpty().uppercase(Locale.UK).replace("\\s+".toRegex(), "")



    private fun formatPostcode(postcode: String): String =
        if (postcode.length > 3) postcode.dropLast(3) + " " + postcode.takeLast(3) else postcode



    private fun hasPostcode(text: String): Boolean {
        val upper = text.uppercase(Locale.UK)
        return fullPostcodeRegex.containsMatchIn(upper) || outcodeRegex.containsMatchIn(upper)
    }



    private fun parseRainLevel(q: String): String? = when {
        Regex("\\blow\\b|\\bniski\\b").containsMatchIn(q) -> "low"
        Regex("\\bmedium\\b|\\bmid\\b|\\bsredni\\b").containsMatchIn(q) -> "medium"
        Regex("\\bhigh\\b|\\bheavy\\b|\\bwysoki\\b").containsMatchIn(q) -> "high"
        else -> null
    }



    private fun predictionResponse(
        prediction: PredictionMatch,
        rainLevel: String?,
        language: Language
    ): String {
        return when (language) {
            Language.EN -> {
                val rainText = rainLevel?.let {
                    " With reported rain level: ${it.replaceFirstChar { c -> c.uppercase() }}."
                } ?: ""
                "Prediction for ${prediction.postcode}: ${prediction.predictedRisk} risk. " +
                    "Confidence ${prediction.confidence}% based on ${prediction.sampleSize} local records.$rainText Is that all?"
            }


            Language.PL -> {
                val rainText = when (rainLevel) {
                    "low" -> " Uwzgledniono poziom deszczu: niski."
                    "medium" -> " Uwzgledniono poziom deszczu: sredni."
                    "high" -> " Uwzgledniono poziom deszczu: wysoki."
                    else -> ""
                }


                "Prognoza dla ${prediction.postcode}: ryzyko ${prediction.predictedRisk}. " +
                    "Pewnosc ${prediction.confidence}% na podstawie ${prediction.sampleSize} lokalnych rekordow.$rainText Czy to wszystko?"
            }
        }
    }

    private fun avoidRepetition(
        candidate: String,
        language: Language,
        question: String,
        lastBotReply: String?
    ): String {
        if (lastBotReply.isNullOrBlank()) return candidate
        if (!candidate.equals(lastBotReply, ignoreCase = true)) return candidate


        val fallback = when (language) {
            Language.EN -> listOf(
                "I am seeing the same request. Try a full UK postcode like LS1 4AP for a sharper prediction.",
                "I can give a better result if you include a full postcode and ask: predict flood risk for <postcode>.",
                "Please add more detail (postcode or nearby area) and I will return a more specific risk estimate."
            )

            
            Language.PL -> listOf(
                "Widze to samo pytanie. Podaj pelny kod pocztowy, np. LS1 4AP, aby uzyskac dokladniejsza prognoze.",
                "Aby dostac lepszy wynik, wpisz pelny kod i zapytaj: przewidz ryzyko powodzi dla <kod>.",
                "Dodaj wiecej szczegolow (kod pocztowy lub okolice), a podam bardziej precyzyjna ocene ryzyka."
            )
        }

        val index = (question.hashCode().absoluteValue) % fallback.size
        return fallback[index]
    }
}
