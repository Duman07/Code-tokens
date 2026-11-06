package com.contadortokens.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.contadortokens.app.widget.TokensWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Periodo(val nombre: String) { HOY("Hoy"), SEMANA("Semana"), MES("Mes") }

private val ES = Locale("es")

/** Lo que gastó una sesión en todo el periodo elegido. */
private data class GastoSesion(
    val sesion: String,
    val proyecto: String,
    val dispositivo: String,
    val ultimoDia: LocalDate,
    val dias: Int,
    val total: Long,
    val costo: Double,
)

private fun sumar(lista: List<ConsumoDia>) = Totales(
    entrada = lista.sumOf { it.entrada }, salida = lista.sumOf { it.salida },
    cacheLeida = lista.sumOf { it.cacheLeida }, cacheEscrita = lista.sumOf { it.cacheEscrita },
    turnos = 0, turnosSubagentes = 0,
)

private fun usd(v: Double) = "US$ ${"%.2f".format(v)}"

@Composable
internal fun Consumo(supabase: Supabase) {
    var dias by remember { mutableStateOf(supabase.consumoEnCache()) }
    var error by remember { mutableStateOf("") }
    var periodo by remember { mutableStateOf(Periodo.SEMANA) }
    var aBorrar by remember { mutableStateOf<GastoSesion?>(null) }
    val alcance = rememberCoroutineScope()
    val contexto = androidx.compose.ui.platform.LocalContext.current

    suspend fun cargar() {
        try {
            dias = supabase.consumo(); error = ""
        } catch (e: Exception) {
            error = e.message ?: "Error"
        }
    }
    LaunchedEffect(Unit) {
        while (true) { cargar(); delay(60_000) }
    }

    val hoy = LocalDate.now()
    val desde = when (periodo) {
        Periodo.HOY -> hoy
        Periodo.SEMANA -> hoy.with(DayOfWeek.MONDAY)
        Periodo.MES -> hoy.withDayOfMonth(1)
    }
    val enPeriodo = dias.filter { !it.dia.isBefore(desde) && !it.dia.isAfter(hoy) }
    val sesiones = enPeriodo.groupBy { it.sesion }.map { (id, filas) ->
        GastoSesion(
            sesion = id,
            proyecto = filas.maxBy { it.dia }.proyecto,
            dispositivo = filas.maxBy { it.dia }.dispositivo,
            ultimoDia = filas.maxOf { it.dia },
            dias = filas.size,
            total = filas.sumOf { it.total },
            costo = filas.sumOf { it.costoUsd },
        )
    }.sortedByDescending { it.total }
    val total = sesiones.sumOf { it.total }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Encabezado
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("En qué se gastan", color = TENUE, fontSize = 13.sp)
                Text("Consumo", color = TEXTO, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            BotonRedondo(R.drawable.ic_refresh) { alcance.launch { cargar() } }
        }

        // Selector de periodo
        val forma = RoundedCornerShape(50)
        Row(
            Modifier.fillMaxWidth().clip(forma).background(TARJETA).border(1.dp, BORDE, forma).padding(5.dp),
        ) {
            Periodo.entries.forEach { p ->
                val activo = p == periodo
                Box(
                    Modifier.weight(1f).clip(forma)
                        .background(if (activo) DEGRADADO else SolidColor(Color.Transparent))
                        .clickable { periodo = p }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(p.nombre, color = if (activo) Color.White else TENUE, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (error.isNotEmpty()) Aviso(error)

        Resumen(periodo, desde, hoy, total, enPeriodo.sumOf { it.costoUsd }, sesiones.size)

        if (periodo != Periodo.HOY) {
            val ultimo = if (periodo == Periodo.SEMANA) desde.plusDays(6) else hoy.withDayOfMonth(hoy.lengthOfMonth())
            Tarjeta("Por día", R.drawable.ic_calendario, ENTRADA) {
                BarrasDias(desde, ultimo, hoy, enPeriodo.groupBy { it.dia }.mapValues { (_, l) -> l.sumOf { it.total } }, periodo)
            }
        }

        if (total > 0) {
            Tarjeta("Por tipo de token", R.drawable.ic_capas, CACHE) { Reparto(sumar(enPeriodo)) }

            val proyectos = sesiones.groupBy { it.proyecto }
                .map { (nombre, l) -> Triple(nombre, l.sumOf { it.total }, l.sumOf { it.costo }) }
                .sortedByDescending { it.second }
            Tarjeta("Por proyecto", R.drawable.ic_carpeta, CACHE_ESCRITA, extra = "${proyectos.size}") {
                proyectos.forEachIndexed { i, (nombre, tokens, costo) ->
                    FilaProyecto(nombre, tokens, costo, tokens.toFloat() / total, COLORES[i % COLORES.size])
                }
            }

            Tarjeta("Sesiones", R.drawable.ic_equipo, SALIDA, extra = "${sesiones.size}") {
                sesiones.forEach { s -> FilaSesion(s, total) { aBorrar = s } }
            }
        } else {
            Tarjeta("Sin consumo", R.drawable.ic_reloj, TENUE) {
                Text(
                    when (periodo) {
                        Periodo.HOY -> "Hoy todavía no hay tokens registrados."
                        Periodo.SEMANA -> "Esta semana todavía no hay tokens registrados."
                        Periodo.MES -> "Este mes todavía no hay tokens registrados."
                    },
                    color = TENUE, fontSize = 14.sp,
                )
            }
        }
        Spacer(Modifier.height(84.dp)) // deja sitio a la barra de abajo
    }

    aBorrar?.let { s ->
        AlertDialog(
            onDismissRequest = { aBorrar = null },
            containerColor = TARJETA,
            title = { Text("¿Borrar esta sesión?", color = TEXTO) },
            text = {
                Text(
                    "«${s.proyecto}» · ${s.dispositivo}\n${corto(s.total)} tokens. Se borra su historial y deja de contar en el consumo.",
                    color = TENUE,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    aBorrar = null
                    alcance.launch {
                        try {
                            supabase.borrarSesion(s.sesion)
                            dias = supabase.consumoEnCache()
                            TokensWidget().updateAll(contexto)
                            cargar()
                        } catch (e: Exception) {
                            error = e.message ?: "Error"
                        }
                    }
                }) { Text("Borrar", color = ERROR, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { aBorrar = null }) { Text("Cancelar", color = TEXTO) } },
        )
    }
}

private val COLORES = listOf(SALIDA, ENTRADA, CACHE, CACHE_ESCRITA, Color(0xFFF472B6), Color(0xFF22D3EE))

@Composable
private fun Resumen(periodo: Periodo, desde: LocalDate, hoy: LocalDate, total: Long, costo: Double, sesiones: Int) {
    val titulo = when (periodo) {
        Periodo.HOY -> hoy.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)).replaceFirstChar { it.uppercase() }
        Periodo.SEMANA -> "Desde el lunes ${desde.format(DateTimeFormatter.ofPattern("d 'de' MMMM", ES))}"
        Periodo.MES -> hoy.format(DateTimeFormatter.ofPattern("MMMM yyyy", ES)).replaceFirstChar { it.uppercase() }
    }
    val diasTranscurridos = (hoy.toEpochDay() - desde.toEpochDay() + 1).coerceAtLeast(1)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(DEGRADADO).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icono(R.drawable.ic_calendario, Color.White.copy(alpha = 0.85f), 16.dp)
            Spacer(Modifier.width(8.dp))
            Text(titulo, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(corto(total), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("tokens", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(numero(total), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            val vidrio = Color.White.copy(alpha = 0.16f)
            Pastilla(usd(costo), vidrio, Color.White, R.drawable.ic_dinero)
            Pastilla("$sesiones ${if (sesiones == 1) "sesión" else "sesiones"}", vidrio, Color.White, R.drawable.ic_equipo)
            if (periodo != Periodo.HOY) Pastilla("${corto(total / diasTranscurridos)} por día", vidrio, Color.White, R.drawable.ic_grafica)
        }
    }
}

@Composable
private fun BarrasDias(desde: LocalDate, hasta: LocalDate, hoy: LocalDate, porDia: Map<LocalDate, Long>, periodo: Periodo) {
    val dias = generateSequence(desde) { it.plusDays(1) }.takeWhile { !it.isAfter(hasta) }.toList()
    val max = (porDia.values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    var meta by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { meta = 1f }
    val crecer by animateFloatAsState(meta, tween(800, easing = FastOutSlowInEasing), label = "dias")

    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val paso = size.width / dias.size
        val ancho = (paso * if (periodo == Periodo.SEMANA) 0.5f else 0.62f).coerceAtLeast(3f)
        val r = CornerRadius((ancho / 2).coerceAtMost(8.dp.toPx()))
        for (k in 1..3) {
            val y = size.height * k / 4
            drawLine(BORDE, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        dias.forEachIndexed { i, d ->
            val x = i * paso + (paso - ancho) / 2
            drawRoundRect(VACIO.copy(alpha = 0.5f), Offset(x, 0f), Size(ancho, size.height), r)
            val v = porDia[d] ?: 0L
            if (v > 0) {
                val alto = (size.height * v / max * crecer).coerceAtLeast(ancho)
                val pincel = if (d == hoy) Brush.verticalGradient(listOf(Color(0xFFC4B5FD), VIOLETA))
                else Brush.verticalGradient(listOf(ENTRADA, INDIGO))
                drawRoundRect(pincel, Offset(x, size.height - alto), Size(ancho, alto), r)
            }
        }
    }
    // Etiquetas: en la semana, cada día; en el mes, algunos para no amontonar.
    Row(Modifier.fillMaxWidth()) {
        dias.forEach { d ->
            val texto = when {
                periodo == Periodo.SEMANA -> d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, ES).uppercase()
                d.dayOfMonth == 1 || d.dayOfMonth % 5 == 0 -> "${d.dayOfMonth}"
                else -> ""
            }
            Text(
                texto, color = if (d == hoy) TEXTO else TENUE, fontSize = 11.sp,
                fontWeight = if (d == hoy) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f),
            )
        }
    }
    val pico = porDia.maxByOrNull { it.value }
    if (pico != null && pico.value > 0) {
        Text(
            "Día con más uso: ${pico.key.format(DateTimeFormatter.ofPattern("EEEE d", ES))} · ${corto(pico.value)}",
            color = TENUE, fontSize = 12.sp,
        )
    }
}

@Composable
private fun FilaProyecto(nombre: String, tokens: Long, costo: Double, parte: Float, color: Color) {
    val progreso by animateFloatAsState(parte, tween(800, easing = FastOutSlowInEasing), label = "proyecto")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(nombre, color = TEXTO, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${(parte * 100).toInt()}%", color = TENUE, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(corto(tokens), color = TEXTO, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (costo > 0) Text(usd(costo), color = TENUE, fontSize = 11.sp)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(VACIO, cornerRadius = r)
            if (progreso > 0f) drawRoundRect(color, size = Size((size.width * progreso).coerceAtLeast(size.height), size.height), cornerRadius = r)
        }
    }
}

@Composable
private fun FilaSesion(s: GastoSesion, total: Long, alBorrar: () -> Unit) {
    val forma = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(forma).background(FONDO.copy(alpha = 0.5f)).border(1.dp, BORDE, forma).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChipIcono(R.drawable.ic_carpeta, SALIDA, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.proyecto, color = TEXTO, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val fecha = s.ultimoDia.format(DateTimeFormatter.ofPattern("d MMM", ES))
            Text(
                listOf(s.dispositivo, fecha, if (s.dias > 1) "${s.dias} días" else null).filter { !it.isNullOrBlank() }.joinToString(" · "),
                color = TENUE, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(corto(s.total), color = TEXTO, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                if (s.costo > 0) usd(s.costo) else "${(s.total * 100 / total.coerceAtLeast(1))}%",
                color = TENUE, fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(34.dp).clip(CircleShape).clickable(onClick = alBorrar),
            contentAlignment = Alignment.Center,
        ) { Icono(R.drawable.ic_borrar, TENUE, 18.dp) }
    }
}
