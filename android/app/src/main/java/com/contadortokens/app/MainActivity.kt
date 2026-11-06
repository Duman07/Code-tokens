package com.contadortokens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.contadortokens.app.widget.Actualizador
import com.contadortokens.app.widget.TokensWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- Paleta ----------

internal val FONDO = Color(0xFF0A0A14)
internal val TARJETA = Color(0xFF15151F)
internal val BORDE = Color(0x14FFFFFF)
internal val VACIO = Color(0xFF262636)
internal val TEXTO = Color(0xFFF4F4FA)
internal val TENUE = Color(0xFF8E8EA8)
internal val ERROR = Color(0xFFF87171)
internal val ENTRADA = Color(0xFF60A5FA)
internal val SALIDA = Color(0xFFA78BFA)
internal val CACHE = Color(0xFF34D399)
internal val CACHE_ESCRITA = Color(0xFFFBBF24)

internal val VIOLETA = Color(0xFF7C3AED)
internal val INDIGO = Color(0xFF4F46E5)
internal val AZUL = Color(0xFF2563EB)
internal val DEGRADADO = Brush.linearGradient(listOf(VIOLETA, INDIGO, AZUL))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val supabase = Supabase(this)
        Actualizador.programar(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = FONDO, surface = TARJETA, primary = SALIDA)) {
                Box(Modifier.fillMaxSize().background(FONDO)) {
                    Resplandor()
                    Box(Modifier.fillMaxSize().systemBarsPadding()) { App(supabase) }
                }
            }
        }
    }
}

/** Halos de color difuminados detrás del contenido. */
@Composable
private fun Resplandor() {
    Canvas(Modifier.fillMaxSize()) {
        val a = Offset(size.width * 0.15f, 0f)
        drawCircle(Brush.radialGradient(listOf(VIOLETA.copy(alpha = 0.35f), Color.Transparent), a, size.width * 0.9f), size.width * 0.9f, a)
        val b = Offset(size.width, size.height * 0.35f)
        drawCircle(Brush.radialGradient(listOf(AZUL.copy(alpha = 0.22f), Color.Transparent), b, size.width * 0.7f), size.width * 0.7f, b)
    }
}

@Composable
private fun App(supabase: Supabase) {
    var conectado by remember { mutableStateOf(supabase.conectado) }
    if (conectado) Principal(supabase, alSalir = { supabase.salir(); conectado = false })
    else Entrar(supabase, alEntrar = { conectado = true })
}

/** Las dos pantallas con su barra de abajo: «Ahora» (sesión en vivo) y «Consumo» (día, semana, mes). */
@Composable
private fun Principal(supabase: Supabase, alSalir: () -> Unit) {
    var pestana by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        if (pestana == 0) Panel(supabase, alSalir) else Consumo(supabase)
        BarraNavegacion(pestana, Modifier.align(Alignment.BottomCenter)) { pestana = it }
    }
}

@Composable
private fun BarraNavegacion(elegida: Int, modifier: Modifier, alElegir: (Int) -> Unit) {
    val forma = RoundedCornerShape(50)
    Row(
        modifier.padding(bottom = 12.dp).clip(forma).background(Color(0xF21B1B28)).border(1.dp, BORDE, forma).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(Triple("Ahora", R.drawable.ic_rayo, 0), Triple("Consumo", R.drawable.ic_grafica, 1)).forEach { (nombre, icono, i) ->
            val activa = i == elegida
            Row(
                Modifier.clip(forma).background(if (activa) DEGRADADO else SolidColor(Color.Transparent))
                    .clickable { alElegir(i) }.padding(horizontal = 20.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icono(icono, if (activa) Color.White else TENUE, 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(nombre, color = if (activa) Color.White else TENUE, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------- Piezas comunes ----------

@Composable
internal fun Icono(@DrawableRes id: Int, color: Color, tam: Dp = 20.dp) =
    Icon(painterResource(id), contentDescription = null, tint = color, modifier = Modifier.size(tam))

/** Icono dentro de un cuadrito redondeado con fondo tenue de su color. */
@Composable
internal fun ChipIcono(@DrawableRes id: Int, color: Color, tam: Dp = 36.dp) {
    Box(
        Modifier.size(tam).background(color.copy(alpha = 0.16f), RoundedCornerShape(tam / 3)),
        contentAlignment = Alignment.Center,
    ) { Icono(id, color, tam * 0.55f) }
}

@Composable
internal fun BotonRedondo(@DrawableRes id: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(TARJETA).border(1.dp, BORDE, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icono(id, TEXTO) }
}

@Composable
private fun BotonDegradado(texto: String, habilitado: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(54.dp).clip(RoundedCornerShape(18.dp))
            .background(if (habilitado) DEGRADADO else SolidColor(VACIO))
            .clickable(enabled = habilitado, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

@Composable
internal fun Tarjeta(
    titulo: String,
    @DrawableRes icono: Int,
    color: Color,
    extra: String? = null,
    contenido: @Composable () -> Unit,
) {
    val forma = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).background(TARJETA).border(1.dp, BORDE, forma).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChipIcono(icono, color, 32.dp)
            Spacer(Modifier.width(10.dp))
            Text(titulo, color = TEXTO, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            extra?.let { Text(it, color = TENUE, fontSize = 12.sp) }
        }
        contenido()
    }
}

@Composable
internal fun Pastilla(texto: String, fondo: Color, color: Color, @DrawableRes icono: Int? = null) {
    Row(
        Modifier.background(fondo, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icono?.let { Icono(it, color, 13.dp); Spacer(Modifier.width(5.dp)) }
        Text(texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
internal fun Aviso(texto: String) {
    Row(
        Modifier.fillMaxWidth().background(ERROR.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icono(R.drawable.ic_alerta, ERROR, 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(texto, color = ERROR, fontSize = 13.sp)
    }
}

@Composable
private fun Logo(tam: Dp) {
    Box(
        Modifier.size(tam).clip(RoundedCornerShape(tam * 0.3f)).background(DEGRADADO),
        contentAlignment = Alignment.Center,
    ) { Icono(R.drawable.ic_chispa, Color.White, tam * 0.5f) }
}

// ---------- Entrar ----------

@Composable
private fun Campo(
    valor: String, cambio: (String) -> Unit, etiqueta: String, @DrawableRes icono: Int,
    tipo: KeyboardType = KeyboardType.Text, secreto: Boolean = false,
) {
    OutlinedTextField(
        valor, cambio,
        label = { Text(etiqueta) },
        leadingIcon = { Icono(icono, TENUE) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (secreto) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = tipo),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = TARJETA, unfocusedContainerColor = TARJETA,
            focusedBorderColor = SALIDA, unfocusedBorderColor = BORDE,
            focusedLabelColor = SALIDA, unfocusedLabelColor = TENUE,
            focusedTextColor = TEXTO, unfocusedTextColor = TEXTO, cursorColor = SALIDA,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Logo(64.dp)
        Spacer(Modifier.height(4.dp))
        Text("Contador de\ntokens", fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, color = TEXTO)
        Text("Mira el contexto y los límites de Claude Code desde tu teléfono.", color = TENUE, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Campo(url, { url = it }, "URL del proyecto", R.drawable.ic_enlace, KeyboardType.Uri)
        Campo(clave, { clave = it }, "Clave pública (publishable o anon)", R.drawable.ic_llave)
        Campo(correo, { correo = it }, "Correo", R.drawable.ic_correo, KeyboardType.Email)
        Campo(contrasena, { contrasena = it }, "Contraseña", R.drawable.ic_candado, KeyboardType.Password, secreto = true)
        if (error.isNotEmpty()) Aviso(error)
        Spacer(Modifier.height(4.dp))
        BotonDegradado(if (ocupado) "Entrando…" else "Entrar", habilitado = !ocupado, modifier = Modifier.fillMaxWidth()) {
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
        }
    }
}

// ---------- Panel ----------

@Composable
private fun Panel(supabase: Supabase, alSalir: () -> Unit) {
    var filas by remember { mutableStateOf(supabase.enCache()) }
    var consumo by remember { mutableStateOf(supabase.consumoEnCache()) }
    var error by remember { mutableStateOf("") }
    // null = seguir siempre la sesión más reciente.
    var elegida by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    val contexto = LocalContext.current

    suspend fun cargar() {
        cargando = true
        try {
            filas = supabase.cargar(); error = ""
            runCatching { supabase.consumo() }.onSuccess { consumo = it }
            TokensWidget().updateAll(contexto)
        } catch (e: Exception) {
            error = e.message ?: "Error"
            if (!supabase.conectado) alSalir()
        } finally {
            cargando = false
        }
    }
    // Mientras la app está abierta, refresca cada 20 s.
    LaunchedEffect(Unit) {
        while (true) { cargar(); delay(20_000) }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Encabezado(supabase.correo, cargando) { alcance.launch { cargar() } }
        if (error.isNotEmpty()) Aviso(error)

        if (filas.isEmpty()) {
            Tarjeta("Esperando datos", R.drawable.ic_reloj, SALIDA) {
                Text(
                    "Todavía no llegan datos. Conecta Claude Code con un token de dispositivo y termina un turno.",
                    color = TENUE, fontSize = 14.sp,
                )
            }
        } else {
            val u = filas.firstOrNull { it.sesion == elegida } ?: filas.first()
            val limites = filas.first().limites.ifEmpty { u.limites }

            Cuenta(filas, consumo)

            // Selector de sesión
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filas.take(6).forEachIndexed { i, f ->
                    val activa = f.sesion == u.sesion
                    val viva = System.currentTimeMillis() - f.actualizado < 5 * 60_000
                    val forma = RoundedCornerShape(50)
                    Box(
                        Modifier.clip(forma)
                            .background(if (activa) DEGRADADO else SolidColor(TARJETA))
                            .border(1.dp, if (activa) Color.Transparent else BORDE, forma)
                            // La primera es «la más reciente»: tocarla vuelve a seguirla sola.
                            .clickable { elegida = if (i == 0) null else f.sesion }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        if (viva) { PuntoVivo(CACHE); Spacer(Modifier.width(6.dp)) }
                        Text(
                            f.proyecto, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (activa) Color.White else TENUE, fontSize = 13.sp,
                            fontWeight = if (activa) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        }
                    }
                }
            }

            Heroe(u)

            // Rejilla de cifras
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Cifra("Entrada", u.totales.entrada, ENTRADA, R.drawable.ic_entrada)
                Cifra("Salida", u.totales.salida, SALIDA, R.drawable.ic_salida)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Cifra("Caché leída", u.totales.cacheLeida, CACHE, R.drawable.ic_cache)
                Cifra("Caché escrita", u.totales.cacheEscrita, CACHE_ESCRITA, R.drawable.ic_guardar)
            }

            if (limites.isNotEmpty()) Tarjeta("Límites de la cuenta", R.drawable.ic_reloj, CACHE_ESCRITA) {
                limites.forEach { l -> BarraLimite(l.nombre, l.usado, l.reinicia) }
            }
            if (u.historial.isNotEmpty()) Tarjeta("Por turno", R.drawable.ic_grafica, ENTRADA, extra = "${u.historial.size} turnos") {
                Historial(u.historial)
            }
            Tarjeta(
                "Sesión", R.drawable.ic_capas, CACHE,
                extra = "${u.totales.turnos} turnos" + if (u.totales.turnosSubagentes > 0) " + ${u.totales.turnosSubagentes} sub" else "",
            ) { Reparto(u.totales) }
        }

        // Pie
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SALIDA.copy(alpha = 0.10f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChipIcono(R.drawable.ic_widgets, SALIDA, 34.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                "Añade el widget «Tokens de Claude» desde la pantalla de inicio. Se actualiza cada 15 min o al tocar ↻.",
                color = TENUE, fontSize = 12.sp, lineHeight = 16.sp,
            )
        }
        val formaSalir = RoundedCornerShape(18.dp)
        Row(
            Modifier.fillMaxWidth().clip(formaSalir).border(1.dp, BORDE, formaSalir).clickable(onClick = alSalir).padding(14.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icono(R.drawable.ic_salir, ERROR, 18.dp)
            Spacer(Modifier.width(8.dp))
            Text("Cerrar sesión", color = ERROR, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(84.dp)) // deja sitio a la barra de abajo
    }
}

/** Resumen de toda la cuenta: todas las sesiones y equipos de hoy, no solo la elegida. */
@Composable
private fun Cuenta(filas: List<Uso>, consumo: List<ConsumoDia>) {
    val hoy = java.time.LocalDate.now()
    val deHoy = filas.filter {
        java.time.Instant.ofEpochMilli(it.actualizado).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == hoy
    }
    // Lo gastado hoy de verdad (por hora); si el esquema aún no lo tiene, las sesiones de hoy enteras.
    val consumoHoy = consumo.filter { it.dia == hoy }
    val tokensHoy = if (consumoHoy.isNotEmpty()) consumoHoy.sumOf { it.total } else deHoy.sumOf { it.totales.total }
    val costoHoy = if (consumoHoy.isNotEmpty()) consumoHoy.sumOf { it.costoUsd } else deHoy.sumOf { it.costoUsd ?: 0.0 }
    val sesionesHoy = if (consumoHoy.isNotEmpty()) consumoHoy.map { it.sesion }.distinct().size else deHoy.size
    val ahora = System.currentTimeMillis()
    val activas = filas.count { ahora - it.actualizado < 5 * 60_000 }
    val ultimo = filas.maxOf { it.actualizado }
    val forma = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).background(TARJETA).border(1.dp, BORDE, forma).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Toda la cuenta · hoy", color = TEXTO, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("último uso ${haceCuanto(ultimo)}", color = TENUE, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Dato(R.drawable.ic_rayo, SALIDA, corto(tokensHoy), "tokens")
            Dato(R.drawable.ic_dinero, CACHE, "US$ ${"%.2f".format(costoHoy)}", "costo")
            Dato(R.drawable.ic_capas, ENTRADA, "$sesionesHoy", if (activas > 0) "$activas activa" + (if (activas > 1) "s" else "") else "sesiones")
        }
    }
}

@Composable
private fun RowScope.Dato(@DrawableRes icono: Int, color: Color, valor: String, nombre: String) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChipIcono(icono, color, 30.dp)
        Text(valor, color = TEXTO, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(nombre, color = TENUE, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun Encabezado(correo: String, cargando: Boolean, alActualizar: () -> Unit) {
    val giro = rememberInfiniteTransition(label = "giro")
    val angulo by giro.animateFloat(0f, 360f, infiniteRepeatable(tween(900)), label = "angulo")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Logo(44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                correo.substringBefore('@').ifEmpty { "Hola" }, color = TENUE, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("Tus tokens", color = TEXTO, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.rotate(if (cargando) angulo else 0f)) { BotonRedondo(R.drawable.ic_refresh, alActualizar) }
    }
}

/** Punto que late: indica que los datos son recientes. */
@Composable
internal fun PuntoVivo(color: Color) {
    val latido = rememberInfiniteTransition(label = "latido")
    val a by latido.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alfa")
    Box(Modifier.size(8.dp).background(color.copy(alpha = a), CircleShape))
}

@Composable
private fun Heroe(u: Uso) {
    val pct = u.porcentaje ?: 0
    val estado = Color(colorDe(pct))
    val progreso by animateFloatAsState(pct.coerceIn(0, 100) / 100f, tween(900, easing = FastOutSlowInEasing), label = "anillo")
    val reciente = System.currentTimeMillis() - u.actualizado < 5 * 60_000

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(DEGRADADO).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ventana de contexto", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.weight(1f))
            Row(
                Modifier.background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (reciente) { PuntoVivo(CACHE); Spacer(Modifier.width(6.dp)) }
                Text(
                    if (reciente) "En vivo" else haceCuanto(u.actualizado),
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
                Canvas(Modifier.size(132.dp)) {
                    val grosor = 14.dp.toPx()
                    val arco = Size(size.width - grosor, size.height - grosor)
                    val origen = Offset(grosor / 2, grosor / 2)
                    drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, origen, arco, style = Stroke(grosor))
                    drawArc(Color.White, -90f, 360f * progreso, false, origen, arco, style = Stroke(grosor, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$pct%", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("usado", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(corto(u.contexto ?: 0), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("de ${corto(u.ventana ?: 0)} tokens", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Pastilla(estadoDe(pct), FONDO.copy(alpha = 0.4f), estado)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            val vidrio = Color.White.copy(alpha = 0.16f)
            u.modelo?.takeIf { it.isNotBlank() }?.let { Pastilla(it, vidrio, Color.White, R.drawable.ic_chispa) }
            if (u.dispositivo.isNotBlank()) Pastilla(u.dispositivo, vidrio, Color.White, R.drawable.ic_equipo)
            u.costoUsd?.let { Pastilla("US$ ${"%.2f".format(it)}", vidrio, Color.White, R.drawable.ic_dinero) }
            Pastilla("${corto(u.totales.total)} en sesión", vidrio, Color.White, R.drawable.ic_rayo)
        }
    }
}

@Composable
private fun RowScope.Cifra(nombre: String, valor: Long, color: Color, @DrawableRes icono: Int) {
    val forma = RoundedCornerShape(22.dp)
    Column(
        Modifier.weight(1f).clip(forma).background(TARJETA).border(1.dp, BORDE, forma).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChipIcono(icono, color)
        Column {
            Text(corto(valor), color = TEXTO, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(nombre, color = TENUE, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BarraLimite(nombre: String, usado: Int, reinicia: String?) {
    val color = Color(colorDe(usado))
    val progreso by animateFloatAsState(usado.coerceIn(0, 100) / 100f, tween(900, easing = FastOutSlowInEasing), label = "limite")
    val hora = reinicia?.let {
        runCatching {
            java.time.OffsetDateTime.parse(it).atZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalTime().toString().take(5)
        }.getOrNull()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(nombre, color = TEXTO, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                hora?.let { Text("Reinicia a las $it", color = TENUE, fontSize = 12.sp) }
            }
            Text("$usado%", color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(VACIO, cornerRadius = r)
            if (progreso > 0f) drawRoundRect(
                Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color)),
                size = Size((size.width * progreso).coerceAtLeast(size.height), size.height), cornerRadius = r,
            )
        }
    }
}

@Composable
private fun Historial(lista: List<Turno>) {
    val max = (lista.maxOfOrNull { it.total } ?: 1L).coerceAtLeast(1L).toFloat()
    var meta by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { meta = 1f }
    val crecer by animateFloatAsState(meta, tween(800, easing = FastOutSlowInEasing), label = "crecer")

    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val paso = size.width / lista.size
        val ancho = (paso * 0.62f).coerceAtLeast(3f)
        val r = CornerRadius((ancho / 2).coerceAtMost(6.dp.toPx()))
        for (k in 1..3) {
            val y = size.height * k / 4
            drawLine(BORDE, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        lista.forEachIndexed { i, t ->
            val x = i * paso + (paso - ancho) / 2
            val alto = size.height * (t.total / max) * crecer
            drawRoundRect(VACIO.copy(alpha = 0.5f), Offset(x, 0f), Size(ancho, size.height), r)
            // Columna apilada: caché abajo, entrada en medio, salida arriba
            var y = size.height
            var arriba = CACHE
            listOf(t.cache to CACHE.copy(alpha = 0.7f), t.entrada to ENTRADA, t.salida to SALIDA).forEach { (v, c) ->
                val h = alto * v / t.total.coerceAtLeast(1)
                y -= h
                if (h > 0f) { drawRect(c, Offset(x, y), Size(ancho, h)); arriba = c }
            }
            // Tapa redondeada sobre la columna
            if (alto > r.y * 2) drawRoundRect(arriba, Offset(x, y), Size(ancho, r.y * 2), r)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Leyenda("Entrada", ENTRADA); Leyenda("Salida", SALIDA); Leyenda("Caché", CACHE)
        Spacer(Modifier.weight(1f))
        Text("último ${corto(lista.last().total)}", color = TENUE, fontSize = 12.sp)
    }
}

@Composable
internal fun Leyenda(nombre: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(nombre, color = TENUE, fontSize = 12.sp)
    }
}

@Composable
internal fun Reparto(t: Totales) {
    val total = t.total.coerceAtLeast(1).toFloat()
    val partes = listOf(
        Triple("Entrada", t.entrada, ENTRADA), Triple("Salida", t.salida, SALIDA),
        Triple("Caché leída", t.cacheLeida, CACHE), Triple("Caché escrita", t.cacheEscrita, CACHE_ESCRITA),
    )
    Row(verticalAlignment = Alignment.Bottom) {
        Text(numero(t.total), color = TEXTO, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text("tokens", color = TENUE, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
    }
    // Barra segmentada con separaciones
    Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        partes.filter { it.second > 0 }.forEach { (_, v, c) ->
            Box(Modifier.weight(v / total).fillMaxHeight().background(c, RoundedCornerShape(6.dp)))
        }
    }
    partes.forEach { (nombre, v, c) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(c, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(10.dp))
            Text(nombre, color = TENUE, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${(v * 100 / total).toInt()}%", color = TENUE, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
            Text(numero(v), color = TEXTO, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
