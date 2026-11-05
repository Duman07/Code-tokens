package com.contadortokens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.contadortokens.app.widget.Actualizador
import com.contadortokens.app.widget.TokensWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FONDO = Color(0xFF0D1117)
private val TARJETA = Color(0xFF161B22)
private val VACIO = Color(0xFF30363D)
private val TENUE = Color(0xFF8B949E)
private val ENTRADA = Color(0xFF58A6FF)
private val SALIDA = Color(0xFFBC8CFF)
private val CACHE = Color(0xFF3FB950)
private val CACHE_ESCRITA = Color(0xFFD29922)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val supabase = Supabase(this)
        Actualizador.programar(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = FONDO, surface = TARJETA, primary = ENTRADA)) {
                Box(Modifier.fillMaxSize().background(FONDO).systemBarsPadding()) { App(supabase) }
            }
        }
    }
}

@Composable
private fun App(supabase: Supabase) {
    var conectado by remember { mutableStateOf(supabase.conectado) }
    if (conectado) Panel(supabase, alSalir = { supabase.salir(); conectado = false })
    else Entrar(supabase, alEntrar = { conectado = true })
}

@Composable
private fun Entrar(supabase: Supabase, alEntrar: () -> Unit) {
    var url by remember { mutableStateOf(supabase.url) }
    var clave by remember { mutableStateOf(supabase.clave) }
    var correo by remember { mutableStateOf(supabase.correo) }
    var contrasena by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()

    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Contador de tokens", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Conecta con tu proyecto de Supabase.", color = TENUE)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(url, { url = it }, label = { Text("URL del proyecto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(clave, { clave = it }, label = { Text("Clave pública (publishable o anon)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            correo, { correo = it }, label = { Text("Correo") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            contrasena, { contrasena = it }, label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = !ocupado,
            onClick = {
                ocupado = true; error = ""
                alcance.launch {
                    try {
                        supabase.entrar(url, clave, correo, contrasena)
                        alEntrar()
                    } catch (e: Exception) {
                        error = e.message ?: "Error"
                    } finally {
                        ocupado = false
                    }
                }
            },
        ) { Text(if (ocupado) "Entrando…" else "Entrar") }
        if (error.isNotEmpty()) Text(error, color = Color(0xFFF85149))
    }
}

@Composable
private fun Panel(supabase: Supabase, alSalir: () -> Unit) {
    var filas by remember { mutableStateOf(supabase.enCache()) }
    var error by remember { mutableStateOf("") }
    var elegida by remember { mutableStateOf(0) }
    val alcance = rememberCoroutineScope()
    val contexto = androidx.compose.ui.platform.LocalContext.current

    suspend fun cargar() {
        try {
            filas = supabase.cargar(); error = ""
            TokensWidget().updateAll(contexto)
        } catch (e: Exception) {
            error = e.message ?: "Error"
            if (!supabase.conectado) alSalir()
        }
    }
    // Mientras la app está abierta, refresca cada 20 s.
    LaunchedEffect(Unit) {
        while (true) { cargar(); delay(20_000) }
    }

    Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tokens", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { alcance.launch { cargar() } }) { Text("Actualizar") }
        }
        if (error.isNotEmpty()) Text(error, color = Color(0xFFF85149))
        if (filas.isEmpty()) {
            Text("Todavía no llegan datos. Conecta Claude Code con un token de dispositivo y termina un turno.", color = TENUE)
        } else {
            val u = filas.getOrElse(elegida) { filas.first() }
            val limites = filas.first().limites.ifEmpty { u.limites }

            // Selector de sesión
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                filas.take(3).forEachIndexed { i, f ->
                    val activa = f.sesion == u.sesion
                    OutlinedButton(onClick = { elegida = i }, modifier = Modifier.weight(1f)) {
                        Text(f.proyecto, maxLines = 1, color = if (activa) Color.White else TENUE, fontSize = 12.sp)
                    }
                }
            }
            Text("${u.dispositivo} · ${u.modelo ?: ""} · medido ${haceCuanto(u.actualizado)}", color = TENUE, fontSize = 12.sp)

            Tarjeta("Contexto") { Contexto(u) }
            if (limites.isNotEmpty()) Tarjeta("Límites de la cuenta") {
                limites.forEach { l -> BarraLimite(l.nombre, l.usado, l.reinicia) }
            }
            if (u.historial.isNotEmpty()) Tarjeta("Por turno") { Historial(u.historial) }
            Tarjeta("Sesión · ${u.totales.turnos} turnos" + if (u.totales.turnosSubagentes > 0) " + ${u.totales.turnosSubagentes} de subagentes" else "") {
                Cifra("Entrada", u.totales.entrada, ENTRADA)
                Cifra("Salida", u.totales.salida, SALIDA)
                Cifra("Caché leída", u.totales.cacheLeida, CACHE)
                Cifra("Caché escrita", u.totales.cacheEscrita, CACHE_ESCRITA)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Añade el widget «Tokens de Claude» desde la pantalla de inicio. Se actualiza cada 15 min o con ↻.", color = TENUE, fontSize = 12.sp)
        OutlinedButton(onClick = alSalir) { Text("Cerrar sesión") }
    }
}

@Composable
private fun Tarjeta(titulo: String, contenido: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(TARJETA, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(titulo.uppercase(), color = TENUE, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        contenido()
    }
}

@Composable
private fun Contexto(u: Uso) {
    val pct = u.porcentaje ?: 0
    val color = Color(colorDe(pct))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
            CircularProgressIndicator(
                progress = { pct.coerceIn(0, 100) / 100f },
                modifier = Modifier.size(96.dp), color = color, trackColor = VACIO, strokeWidth = 10.dp,
            )
            Text("$pct%", color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text("Ventana", color = TENUE, fontSize = 12.sp)
            Text("${corto(u.contexto ?: 0)} / ${corto(u.ventana ?: 0)}", color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Sesión: ${corto(u.totales.total)} tokens", color = TENUE, fontSize = 12.sp)
            u.costoUsd?.let { Text("Costo: US$ ${"%.2f".format(it)}", color = TENUE, fontSize = 12.sp) }
        }
    }
    // Barra de reparto por tipo
    val t = u.totales
    val total = t.total.coerceAtLeast(1).toFloat()
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        drawRect(VACIO, size = size)
        var x = 0f
        listOf(t.entrada to ENTRADA, t.salida to SALIDA, t.cacheLeida to CACHE, t.cacheEscrita to CACHE_ESCRITA).forEach { (v, c) ->
            val w = size.width * v / total
            drawRect(c, topLeft = Offset(x, 0f), size = Size(w, size.height))
            x += w
        }
    }
}

@Composable
private fun BarraLimite(nombre: String, usado: Int, reinicia: String?) {
    val color = Color(colorDe(usado))
    val hora = reinicia?.let {
        runCatching {
            java.time.OffsetDateTime.parse(it).atZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalTime().toString().take(5)
        }.getOrNull()
    }
    Row {
        Text(nombre + (hora?.let { " · reinicia $it" } ?: ""), color = TENUE, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("$usado%", color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
    LinearProgressIndicator(
        progress = { usado.coerceIn(0, 100) / 100f },
        modifier = Modifier.fillMaxWidth().height(8.dp), color = color, trackColor = VACIO,
        drawStopIndicator = {},
    )
}

@Composable
private fun Historial(lista: List<Turno>) {
    val max = (lista.maxOfOrNull { it.total } ?: 1L).coerceAtLeast(1L).toFloat()
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        val paso = size.width / lista.size
        val ancho = (paso - 2f).coerceAtLeast(2f)
        lista.forEachIndexed { i, t ->
            val x = i * paso
            var y = size.height
            listOf(t.cache to CACHE.copy(alpha = 0.55f), t.entrada to ENTRADA, t.salida to SALIDA).forEach { (v, c) ->
                val h = size.height * v / max
                y -= h
                drawRect(c, topLeft = Offset(x, y), size = Size(ancho, h))
            }
        }
    }
    Text("${lista.size} turnos · máx ${corto(max.toLong())} · último ${corto(lista.last().total)}", color = TENUE, fontSize = 11.sp)
}

@Composable
private fun Cifra(nombre: String, valor: Long, color: Color) {
    Row {
        Text("■ ", color = color)
        Text(nombre, color = Color.White, modifier = Modifier.weight(1f))
        Text(numero(valor), color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}
