package com.example.ukfloodsdemographics

import android.content.Context

data class FloodRiskEntry(
    val postcode: String,
    val riskLevel: String,
    val detail: String?,
    val dateRecorded: String?,

    // The longitude and latitude is converted into WGS84 which can be used by Google Maps. (Using geodcoder)


    val latitude: Double?,
    val longitude: Double?
)


//Due to the high volume of data the APK
// wasn't able to handle it. I then Reduced it to 10,000 to allow
// the system to function without crashing.


object FloodRiskCsvLoader {

    private const val ASSET_NAME = "open_flood_risk_by_postcode.csv"

    private const val USER_ADDED_CSV_NAME = "user_added_floods.csv"

    private const val USER_CSV_HEADER = "index,postcode,dummy,risk,detail,date,d6,d7,d8,lat,lon"


    fun loadFromAssets(context: Context): List<FloodRiskEntry> {
        val result = ArrayList<FloodRiskEntry>(10_000)
        context.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, rawLine ->
                parseCsvLine(index, rawLine)?.let { result.add(it) }
            }
        }



        val userFile = context.getFileStreamPath(USER_ADDED_CSV_NAME)
        if (userFile.exists()) {
            userFile.bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, rawLine ->
                    parseCsvLine(index, rawLine)?.let { result.add(it) }
                }
            }
        }


        return result
    }

    fun appendUserFlood(context: Context, entry: FloodRiskEntry) {
        val userFile = context.getFileStreamPath(USER_ADDED_CSV_NAME)
        if (!userFile.exists()) {
            context.openFileOutput(USER_ADDED_CSV_NAME, Context.MODE_PRIVATE).bufferedWriter().use { writer ->
                writer.appendLine(USER_CSV_HEADER)
            }
        }

        context.openFileOutput(USER_ADDED_CSV_NAME, Context.MODE_APPEND).bufferedWriter().use { writer ->
            writer.appendLine(toCsvLine(entry))
        }
    }

    fun loadUserAddedFloods(context: Context): List<FloodRiskEntry> {
        val userFile = context.getFileStreamPath(USER_ADDED_CSV_NAME)
        if (!userFile.exists()) return emptyList()
        val result = mutableListOf<FloodRiskEntry>()
        userFile.bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, rawLine ->
                parseCsvLine(index, rawLine)?.let { result.add(it) }
            }
        }

        return result
    }



    private fun parseCsvLine(index: Int, rawLine: String): FloodRiskEntry? {
        val line = rawLine.trimStart('\uFEFF')
        if (line.isBlank()) return null
        if (index == 0 && line.startsWith("index,")) return null
        val parts = line.split(',')
        if (parts.size < 4) return null
        val postcode = parts[1].trim()
        val risk = parts[3].trim()
        val detail = parts.getOrNull(4)?.trim()?.takeIf { it != "\\N" && it.isNotEmpty() }
        val date = parts.getOrNull(5)?.trim()?.takeIf { it != "\\N" && it.isNotEmpty() }
        val lat = parts.getOrNull(9)?.trim()?.toDoubleOrNull()
        val lon = parts.getOrNull(10)?.trim()?.toDoubleOrNull()
        return FloodRiskEntry(postcode, risk, detail, date, lat, lon)
    }



    private fun toCsvLine(entry: FloodRiskEntry): String {
        val postcode = csvSafe(entry.postcode)
        val risk = csvSafe(entry.riskLevel)
        val detail = csvSafe(entry.detail.orEmpty())
        val date = csvSafe(entry.dateRecorded.orEmpty())
        val lat = entry.latitude?.toString().orEmpty()
        val lon = entry.longitude?.toString().orEmpty()
        return "0,$postcode,,$risk,$detail,$date,,,,$lat,$lon"
    }



    private fun csvSafe(value: String): String = value
        .replace(",", " ")
        .replace("\n", " ")
        .replace("\r", " ")
}
