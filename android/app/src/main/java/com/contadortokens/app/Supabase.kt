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

/** Lo que gastó una sesión en un día (hora local), sacado de consumo(). */
data class ConsumoDia(
    val dia: java.time.LocalDate,
    val sesion: String,
    val proyecto: String,
    val dispositivo: String,
    val entrada: Long,
    val salida: Long,
    val cacheLeida: Long,
    val cacheEscrita: Long,
    val costoUsd: Double,
) {
    val total get() = entrada + salida + cacheLeida + cacheEscrita
}

fun leerConsumo(texto: String): List<ConsumoDia> {
    val arr = JSONArray(texto)
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.getJSONObject(i)
        val dia = runCatching { java.time.LocalDate.parse(o.getString("dia")) }.getOrNull() ?: return@mapNotNull null
        ConsumoDia(
            dia = dia,
            sesion = o.optString("sesion"),
            proyecto = o.optString("proyecto").ifEmpty { "sesión" },
            dispositivo = o.optString("dispositivo"),
            entrada = o.optLong("entrada"),
            salida = o.optLong("salida"),
            cacheLeida = o.optLong("cache_leida"),
            cacheEscrita = o.optLong("cache_escrita"),
            costoUsd = o.optDouble("costo_usd", 0.0).let { if (it.isNaN()) 0.0 else it },
        )
    }
}

/** Primer día que hace falta para «este mes» y «esta semana» (la semana empieza el lunes). */
fun inicioConsumo(hoy: java.time.LocalDate = java.time.LocalDate.now()): java.time.LocalDate =
    minOf(hoy.withDayOfMonth(1), hoy.with(java.time.DayOfWeek.MONDAY))

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

data class Cuenta(val id: String, val proveedor: String, val etiqueta: String)

val PROVEEDORES = listOf("claude" to "Claude", "codex" to "Codex", "cursor" to "Cursor", "antigravity" to "Antigravity", "otro" to "Otro")

data class Llave(val id: String, val nombre: String, val creado: String, val usado: String?)

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

/** Texto corto del estado según el porcentaje. */
fun estadoDe(p: Int): String = when {
    p >= 85 -> "Casi lleno"
    p >= 60 -> "Atento"
    else -> "Con espacio"
}

/** Verde hasta 60 %, amarillo hasta 85 %, rojo después: en ARGB. */
fun colorDe(p: Int): Long = when {
    p >= 85 -> 0xFFF87171
    p >= 60 -> 0xFFFBBF24
    else -> 0xFF34D399
}

// ---------- Cliente de Supabase (REST, sin librerías) ----------

class Supabase(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("contador-tokens", Context.MODE_PRIVATE)

    /** Lo que eligió el usuario («Servidor propio») o, si no, el servidor compartido de la app. */
    val url get() = prefs.getString("url", "").orEmpty().ifEmpty { BuildConfig.SUPABASE_URL }
    val clave get() = prefs.getString("clave", "").orEmpty().ifEmpty { BuildConfig.SUPABASE_KEY }
    val hayServidor get() = BuildConfig.SUPABASE_URL.isNotEmpty() && BuildConfig.SUPABASE_KEY.isNotEmpty()
    val correo get() = prefs.getString("correo", "") ?: ""
    val conectado get() = Boveda.descifrar(prefs.getString("refresh", null)) != null
    private val acceso get() = Boveda.descifrar(prefs.getString("access", null)).orEmpty()
    private val refresco get() = Boveda.descifrar(prefs.getString("refresh", null)).orEmpty()

    /** Última lista descargada, para el widget y para abrir la app sin red. */
    fun enCache(): List<Uso> = runCatching { leerUso(prefs.getString("cache", "[]")!!) }.getOrDefault(emptyList())

    /** `url` y `clave` vacías: usa el servidor compartido de la app. */
    private fun fijarServidor(url: String, clave: String) {
        if (url.isNotBlank() && clave.isNotBlank()) {
            prefs.edit().putString("url", soloDominio(url)).putString("clave", clave.trim()).apply()
        } else if (!hayServidor) {
            throw Exception("Falta la URL y la clave del servidor.")
        } else {
            prefs.edit().remove("url").remove("clave").apply()
        }
    }

    suspend fun entrar(url: String, clave: String, correo: String, contrasena: String) {
        fijarServidor(url, clave)
        val cuerpo = JSONObject().put("email", correo.trim()).put("password", contrasena)
        guardarSesion(auth("password", cuerpo))
    }

    /** Crea una cuenta nueva. Si el servidor pide confirmar el correo, lo dice y no inicia sesión. */
    suspend fun registrar(url: String, clave: String, correo: String, contrasena: String, terminos: Boolean) {
        if (!terminos) throw Exception("Debes aceptar los términos y condiciones y la política de privacidad.")
        fijarServidor(url, clave)
        if (contrasena.length < 8) throw Exception("La contraseña debe tener al menos 8 caracteres.")
        val (codigo, texto) = http(
            "POST", "${this.url}/auth/v1/signup",
            mapOf("apikey" to this.clave, "Content-Type" to "application/json"),
            JSONObject().put("email", correo.trim()).put("password", contrasena)
                // Queda guardado con la cuenta: qué versión aceptó y cuándo.
                .put("data", JSONObject().put("terminos_version", VERSION_TERMINOS).put("terminos_fecha", java.time.Instant.now().toString()))
                .toString(),
        )
        val json = runCatching { JSONObject(texto) }.getOrDefault(JSONObject())
        if (codigo !in 200..299) {
            val mensaje = json.optString("msg").ifEmpty { json.optString("error_description") }
                .ifEmpty { json.optString("message") }.ifEmpty { "HTTP $codigo" }
            // El trigger del servidor rechaza altas sin términos; GoTrue lo devuelve como error genérico.
            throw Exception(
                if (mensaje.contains("Database error", ignoreCase = true)) "Debes aceptar los términos y condiciones para registrarte." else mensaje,
            )
        }
        if (json.optString("access_token").isEmpty()) {
            throw Exception("Cuenta creada. Revisa tu correo para confirmarla y luego entra.")
        }
        guardarSesion(json)
    }

    /** Código de 8 caracteres (10 min) para `/tokens vincular CÓDIGO` en Claude Code. */
    suspend fun crearCodigo(cuenta: String? = null): String =
        pedir(
            "POST", "/rest/v1/rpc/crear_codigo_vinculo",
            JSONObject().apply { if (cuenta != null) put("p_cuenta", cuenta) }.toString(),
        ).trim().trim('"')

    suspend fun cuentas(): List<Cuenta> {
        val arr = JSONArray(pedir("GET", "/rest/v1/cuentas_ia?select=id,proveedor,etiqueta&order=creado.asc"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Cuenta(o.optString("id"), o.optString("proveedor"), o.optString("etiqueta"))
        }
    }

    /** Una sola cuenta de Claude por usuario; de los demás proveedores, las que quiera (el servidor lo aplica). */
    suspend fun crearCuenta(proveedor: String, etiqueta: String) {
        pedir(
            "POST", "/rest/v1/rpc/crear_cuenta_ia",
            JSONObject().put("p_proveedor", proveedor).put("p_etiqueta", etiqueta).toString(),
        )
    }

    /** Quita la cuenta y sus llaves; el consumo ya guardado se conserva. */
    suspend fun quitarCuenta(id: String) {
        pedir("DELETE", "/rest/v1/cuentas_ia?id=eq.${java.net.URLEncoder.encode(id, "UTF-8")}")
    }

    /** Llave de dispositivo nueva (`ctk_…`): solo se ve esta vez. */
    suspend fun crearLlave(nombre: String): String =
        pedir("POST", "/rest/v1/rpc/crear_token_dispositivo", JSONObject().put("p_nombre", nombre).toString()).trim().trim('"')

    suspend fun llaves(): List<Llave> {
        val arr = JSONArray(pedir("GET", "/rest/v1/tokens_dispositivo?select=id,nombre,creado,usado&order=creado.desc"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Llave(o.optString("id"), o.optString("nombre"), o.optString("creado"), o.texto("usado"))
        }
    }

    /** Revoca una llave: el Claude Code que la usaba deja de sincronizar. */
    suspend fun revocarLlave(id: String) {
        pedir("DELETE", "/rest/v1/tokens_dispositivo?id=eq.${java.net.URLEncoder.encode(id, "UTF-8")}")
    }

    /** Borra la cuenta y todos sus datos en el servidor, y cierra la sesión. */
    suspend fun eliminarCuenta() {
        pedir("POST", "/rest/v1/rpc/eliminar_mi_cuenta", "{}")
        salir()
        prefs.edit().remove("correo").apply()
    }

    /** Equipos con token: nombre y último uso. */
    suspend fun dispositivos(): List<Pair<String, String?>> {
        val arr = JSONArray(pedir("GET", "/rest/v1/tokens_dispositivo?select=nombre,usado&order=creado.desc"))
        return (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.optString("nombre") to o.texto("usado") } }
    }

    fun salir() {
        // Todo lo de la cuenta sale del teléfono: otra persona que entre después no ve nada de la anterior.
        prefs.edit().remove("access").remove("refresh").remove("expira").remove("cache").remove("consumo").remove("cargado").apply()
    }

    suspend fun cargar(): List<Uso> {
        val texto = pedir("GET", "/rest/v1/uso_sesion?select=*&order=actualizado.desc&limit=15")
        val lista = leerUso(texto)
        prefs.edit().putString("cache", texto).putLong("cargado", System.currentTimeMillis()).apply()
        return lista
    }

    fun consumoEnCache(): List<ConsumoDia> =
        runCatching { leerConsumo(prefs.getString("consumo", "[]")!!) }.getOrDefault(emptyList())

    /** Consumo por día y sesión desde el lunes o el día 1, lo que sea antes. */
    suspend fun consumo(): List<ConsumoDia> {
        val zona = java.time.ZoneId.systemDefault()
        val desde = inicioConsumo().atStartOfDay(zona).toOffsetDateTime().toString()
        val cuerpo = JSONObject().put("p_desde", desde).put("p_zona", zona.id).toString()
        val texto = try {
            pedir("POST", "/rest/v1/rpc/consumo", cuerpo)
        } catch (e: Exception) {
            if (e.message.orEmpty().contains("consumo")) {
                throw Exception("Falta actualizar Supabase: vuelve a ejecutar supabase/esquema.sql en el SQL Editor.")
            }
            throw e
        }
        val lista = leerConsumo(texto)
        prefs.edit().putString("consumo", texto).apply()
        return lista
    }

    /** Borra una sesión: su fila y su consumo por hora. */
    suspend fun borrarSesion(sesion: String) {
        val id = java.net.URLEncoder.encode(sesion, "UTF-8")
        pedir("DELETE", "/rest/v1/uso_hora?sesion=eq.$id")
        pedir("DELETE", "/rest/v1/uso_sesion?sesion=eq.$id")
        quitarDeCache("cache", sesion)
        quitarDeCache("consumo", sesion)
    }

    private fun quitarDeCache(clave: String, sesion: String) {
        val arr = JSONArray(prefs.getString(clave, "[]"))
        val quedan = JSONArray()
        for (i in 0 until arr.length()) arr.getJSONObject(i).let { if (it.optString("sesion") != sesion) quedan.put(it) }
        prefs.edit().putString(clave, quedan.toString()).apply()
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
            .putString("access", Boveda.cifrar(json.getString("access_token")))
            .putString("refresh", Boveda.cifrar(json.getString("refresh_token")))
            .putLong("expira", System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000)
            .putString("correo", json.optJSONObject("user")?.optString("email") ?: correo)
            .apply()
    }

    private suspend fun renovar() {
        try {
            guardarSesion(auth("refresh_token", JSONObject().put("refresh_token", refresco)))
        } catch (e: Exception) {
            salir()
            throw Exception("La sesión venció; vuelve a entrar.")
        }
    }

    private suspend fun pedir(metodo: String, ruta: String, cuerpo: String? = null): String {
        if (!conectado) throw Exception("Sin sesión")
        if (System.currentTimeMillis() > prefs.getLong("expira", 0) - 60_000) renovar()
        suspend fun una() = http(
            metodo, "$url$ruta",
            buildMap {
                put("apikey", clave)
                put("Authorization", "Bearer $acceso")
                if (cuerpo != null) put("Content-Type", "application/json")
            },
            cuerpo,
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

/** Deja solo `https://xxxx.supabase.co`, aunque peguen `/rest/v1/` u otra ruta detrás. */
internal fun soloDominio(url: String): String {
    val limpio = url.trim()
    val conEsquema = if (limpio.contains("://")) limpio else "https://$limpio"
    val uri = android.net.Uri.parse(conEsquema)
    // Siempre https: las contraseñas y los tokens nunca viajan sin cifrar.
    if (uri.scheme != "https") throw Exception("El servidor debe usar https://")
    return "https://${uri.host ?: limpio}"
}
