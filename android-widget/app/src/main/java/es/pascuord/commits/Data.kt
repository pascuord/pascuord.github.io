package es.pascuord.commits

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Day(val date: String, val count: Int, val level: Int)

data class Contributions(val days: List<Day>, val total: Int)

/** Ajustes y última descarga, guardados en el propio móvil. */
object Store {
    private const val PREFS = "commits"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun user(ctx: Context): String = prefs(ctx).getString("user", "") ?: ""
    fun token(ctx: Context): String = prefs(ctx).getString("token", "") ?: ""
    fun lastUpdate(ctx: Context): Long = prefs(ctx).getLong("updated", 0L)
    fun lastError(ctx: Context): String = prefs(ctx).getString("error", "") ?: ""

    fun saveSettings(ctx: Context, user: String, token: String?) {
        val e = prefs(ctx).edit().putString("user", user)
        if (token != null) e.putString("token", token)
        e.apply()
    }

    fun saveData(ctx: Context, data: Contributions) {
        val arr = JSONArray()
        data.days.forEach { arr.put(JSONArray().put(it.date).put(it.count).put(it.level)) }
        prefs(ctx).edit()
            .putString("days", arr.toString())
            .putInt("total", data.total)
            .putLong("updated", System.currentTimeMillis())
            .putString("error", "")
            .apply()
    }

    fun saveError(ctx: Context, msg: String) {
        prefs(ctx).edit().putString("error", msg).apply()
    }

    fun data(ctx: Context): Contributions? {
        val raw = prefs(ctx).getString("days", null) ?: return null
        return try {
            val arr = JSONArray(raw)
            val days = (0 until arr.length()).map {
                val d = arr.getJSONArray(it)
                Day(d.getString(0), d.getInt(1), d.getInt(2))
            }
            Contributions(days, prefs(ctx).getInt("total", 0))
        } catch (e: Exception) {
            null
        }
    }
}

/** Descarga el calendario de contribuciones del último año. */
object GitHub {
    private val LEVELS = mapOf(
        "NONE" to 0, "FIRST_QUARTILE" to 1, "SECOND_QUARTILE" to 2,
        "THIRD_QUARTILE" to 3, "FOURTH_QUARTILE" to 4
    )

    fun fetch(user: String, token: String): Contributions =
        if (token.isNotBlank()) fetchOfficial(user, token) else fetchPublic(user)

    // API oficial: incluye repositorios privados y de organizaciones
    private fun fetchOfficial(user: String, token: String): Contributions {
        val query = "query(\$login:String!){user(login:\$login){contributionsCollection{contributionCalendar{" +
            "totalContributions weeks{contributionDays{date contributionCount contributionLevel}}}}}}"
        val body = JSONObject()
            .put("query", query)
            .put("variables", JSONObject().put("login", user))
            .toString()

        val conn = URL("https://api.github.com/graphql").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "MisCommits-Android")
        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        if (code == 401) throw IOException("GitHub no acepta el token. Revisa que no haya caducado.")
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw IOException("GitHub respondió con el error $code.")

        val json = JSONObject(text)
        json.optJSONArray("errors")?.let {
            throw IOException(it.getJSONObject(0).optString("message", "Error de GitHub"))
        }
        val userObj = json.optJSONObject("data")?.optJSONObject("user")
            ?: throw IOException("No se encontró el usuario «$user».")
        val cal = userObj.getJSONObject("contributionsCollection").getJSONObject("contributionCalendar")
        val days = mutableListOf<Day>()
        val weeks = cal.getJSONArray("weeks")
        for (w in 0 until weeks.length()) {
            val wd = weeks.getJSONObject(w).getJSONArray("contributionDays")
            for (d in 0 until wd.length()) {
                val o = wd.getJSONObject(d)
                days.add(
                    Day(
                        o.getString("date"),
                        o.getInt("contributionCount"),
                        LEVELS[o.getString("contributionLevel")] ?: 0
                    )
                )
            }
        }
        return Contributions(days, cal.getInt("totalContributions"))
    }

    // Servicio público: solo lo que ve cualquier visitante del perfil
    private fun fetchPublic(user: String): Contributions {
        val url = "https://github-contributions-api.jogruber.de/v4/" +
            URLEncoder.encode(user, "UTF-8") + "?y=last"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", "MisCommits-Android")
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw IOException("No se encontró el usuario «$user».")

        val json = JSONObject(text)
        val arr = json.getJSONArray("contributions")
        val days = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Day(o.getString("date"), o.getInt("count"), o.getInt("level"))
        }
        val totals = json.optJSONObject("total")
        var total = 0
        totals?.keys()?.forEach { total += totals.optInt(it) }
        return Contributions(days, total)
    }
}
