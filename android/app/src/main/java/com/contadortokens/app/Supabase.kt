package com.contadortokens.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.util.Locale

// ---------- Datos ----------

data class Totales(
    val entrada: Long, val salida: Long, val cacheLeida: Long, val cacheEscrita: Long,
    val turnos: Int, val turnosSubagentes: Int,
) {
    val total get() = entrada + salida + cacheLeida + cacheEscrita
}

data class Limite(val tipo: String, val usado: Int, val reinicia: String?) {
    val nombre get() = when (tipo) {
        "five_hour" -> "5 horas"
        "seven_day" -> "7 días"
        "spend_limit" -> "Gasto"
        else -> tipo
    }
}

data class Turno(val entrada: Long, val salida: Long, val cache: Long) {
    val total get() = entrada + salida + cache
}

data class Uso(
    val sesion: String,
    val proyecto: String,
    val dispositivo: String,
    val actualizado: Long,
    val contexto: Long?,
    val ventana: Long?,
    val porcentaje: Int?,
    val costoUsd: Double?,
    val modelo: String?,
    val totales: Totales,
    val limites: List<Limite>,
    val historial: List<Turno>,
)

private fun JSONObject.long(k: String) = if (isNull(k)) null else optLong(k)
private fun JSONObject.texto(k: String) = if (isNull(k)) null else optString(k)

fun leerUso(texto: String): List<Uso> {
    val arr = JSONArray(texto)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val t = o.optJSONObject("totales") ?: JSONObject()
        val lim = o.optJSONArray("limites") ?: JSONArray()
        val his = o.optJSONArray("historial") ?: JSONArray()
        Uso(
            sesion = o.optString("sesion"),
            proyecto = o.optString("proyecto", "sesión"),
            dispositivo = o.optString("dispositivo"),
            actualizado = runCatching { OffsetDateTime.parse(o.getString("actualizado")).toInstant().toEpochMilli() }
                .getOrDefault(0L),
            contexto = o.long("contexto"),
            ventana = o.long("ventana"),
            porcentaje = o.long("porcentaje")?.toInt(),
            costoUsd = if (o.isNull("costo_usd")) null else o.optDouble("costo_usd"),
            modelo = o.texto("modelo"),
            totales = Totales(
                t.optLong("entrada"), t.optLong("salida"), t.optLong("cacheLeida"), t.optLong("cacheEscrita"),
                t.optInt("turnos"), t.optInt("turnosSubagentes"),
            ),
            limites = (0 until lim.length()).map { j ->
                val l = lim.getJSONObject(j)
                Limite(l.optString("tipo"), l.optInt("usado"), l.texto("reinicia"))
            },
            historial = (0 until his.length()).map { j ->
                val h = his.getJSONObject(j)
                Turno(h.optLong("entrada"), h.optLong("salida"), h.optLong("cache"))
            },
        )
    }
}

// ---------- Formatos ----------

fun numero(n: Long): String =
    if (n >= 1_000_000) String.format(Locale.US, "%.2f M", n / 1_000_000.0).replace('.', ',')
    else String.format(Locale.US, "%,d", n).replace(',', '.')

fun corto(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1f M", n / 1_000_000.0).replace('.', ',')
    n >= 1_000 -> String.format(Locale.US, "%.1f k", n / 1_000.0).replace('.', ',')
    else -> n.toString()
}

fun haceCuanto(ms: Long): String {
    val s = ((System.currentTimeMillis() - ms) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "hace un momento"
        s < 3600 -> "hace ${s / 60} min"
        s < 86400 -> "hace ${s / 3600} h"
        else -> "hace ${s / 86400} d"
    }
}

/** Verde hasta 60 %, amarillo hasta 85 %, rojo después: en ARGB. */
fun colorDe(p: Int): Long = when {
    p >= 85 -> 0xFFF85149
    p >= 60 -> 0xFFD29922
    else -> 0xFF3FB950
}

// ---------- Cliente de Supabase (REST, sin librerías) ----------

class Supabase(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("contador-tokens", Context.MODE_PRIVATE)

    val url get() = prefs.getString("url", "") ?: ""
    val clave get() = prefs.getString("clave", "") ?: ""
    val correo get() = prefs.getString("correo", "") ?: ""
    val conectado get() = !prefs.getString("refresh", null).isNullOrEmpty()

    /** Última lista descargada, para el widget y para abrir la app sin red. */
    fun enCache(): List<Uso> = runCatching { leerUso(prefs.getString("cache", "[]")!!) }.getOrDefault(emptyList())

    suspend fun entrar(url: String, clave: String, correo: String, contrasena: String) {
        prefs.edit().putString("url", url.trim().trimEnd('/')).putString("clave", clave.trim()).apply()
        val cuerpo = JSONObject().put("email", correo.trim()).put("password", contrasena)
        guardarSesion(auth("password", cuerpo))
    }

    fun salir() {
        prefs.edit().remove("access").remove("refresh").remove("expira").remove("cache").apply()
    }

    suspend fun cargar(): List<Uso> {
        val texto = pedir("GET", "/rest/v1/uso_sesion?select=*&order=actualizado.desc&limit=15")
        val lista = leerUso(texto)
        prefs.edit().putString("cache", texto).putLong("cargado", System.currentTimeMillis()).apply()
        return lista
    }

    private suspend fun auth(tipo: String, cuerpo: JSONObject): JSONObject {
        val (codigo, texto) = http(
            "POST", "$url/auth/v1/token?grant_type=$tipo",
            mapOf("apikey" to clave, "Content-Type" to "application/json"), cuerpo.toString(),
        )
        val json = runCatching { JSONObject(texto) }.getOrDefault(JSONObject())
        if (codigo !in 200..299) {
            throw Exception(
                json.optString("error_description").ifEmpty { json.optString("msg") }
                    .ifEmpty { json.optString("message") }.ifEmpty { "HTTP $codigo" },
            )
        }
        return json
    }

    private fun guardarSesion(json: JSONObject) {
        prefs.edit()
            .putString("access", json.getString("access_token"))
            .putString("refresh", json.getString("refresh_token"))
            .putLong("expira", System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000)
            .putString("correo", json.optJSONObject("user")?.optString("email") ?: correo)
            .apply()
    }

    private suspend fun renovar() {
        try {
            guardarSesion(auth("refresh_token", JSONObject().put("refresh_token", prefs.getString("refresh", ""))))
        } catch (e: Exception) {
            salir()
            throw Exception("La sesión venció; vuelve a entrar.")
        }
    }

    private suspend fun pedir(metodo: String, ruta: String): String {
        if (!conectado) throw Exception("Sin sesión")
        if (System.currentTimeMillis() > prefs.getLong("expira", 0) - 60_000) renovar()
        suspend fun una() = http(
            metodo, "$url$ruta",
            mapOf("apikey" to clave, "Authorization" to "Bearer ${prefs.getString("access", "")}"),
        )
        var (codigo, texto) = una()
        if (codigo == 401) {
            renovar()
            val otra = una(); codigo = otra.first; texto = otra.second
        }
        if (codigo !in 200..299) {
            throw Exception(runCatching { JSONObject(texto).optString("message") }.getOrNull()?.ifEmpty { null } ?: "HTTP $codigo")
        }
        return texto
    }

    private suspend fun http(metodo: String, direccion: String, cabeceras: Map<String, String>, cuerpo: String? = null) =
        withContext(Dispatchers.IO) {
            val c = URL(direccion).openConnection() as HttpURLConnection
            try {
                c.requestMethod = metodo
                c.connectTimeout = 15_000
                c.readTimeout = 15_000
                cabeceras.forEach { (k, v) -> c.setRequestProperty(k, v) }
                if (cuerpo != null) {
                    c.doOutput = true
                    c.outputStream.use { it.write(cuerpo.toByteArray()) }
                }
                val codigo = c.responseCode
                val flujo = if (codigo in 200..299) c.inputStream else c.errorStream
                codigo to (flujo?.bufferedReader()?.use { it.readText() } ?: "")
            } finally {
                c.disconnect()
            }
        }
}
