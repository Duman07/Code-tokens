package com.contadortokens.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.contadortokens.app.MainActivity
import com.contadortokens.app.R
import com.contadortokens.app.Supabase
import com.contadortokens.app.Uso
import com.contadortokens.app.colorDe
import com.contadortokens.app.corto
import com.contadortokens.app.estadoDe
import com.contadortokens.app.haceCuanto
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private val VACIO = ColorProvider(Color(0x33FFFFFF))
private val TEXTO = ColorProvider(Color(0xFFF4F4FA))
private val TENUE = ColorProvider(Color(0xFFA1A1BD))
private val BLANCO = ColorProvider(Color.White)

private val PEQUENO = DpSize(160.dp, 100.dp)
private val GRANDE = DpSize(250.dp, 150.dp)

class TokensWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(PEQUENO, GRANDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val supabase = Supabase(context)
        val filas = supabase.enCache()
        val conectado = supabase.conectado
        val anillo = anillo(filas.firstOrNull()?.porcentaje ?: 0)
        val hoy = LocalDate.now()
        val consumoHoy = supabase.consumoEnCache().filter { it.dia == hoy }
        // Con el consumo por hora se cuenta solo lo de hoy; si aún no hay, se aproxima con las sesiones.
        val (tokensHoy, costoHoy) = if (consumoHoy.isNotEmpty()) {
            consumoHoy.sumOf { it.total } to consumoHoy.sumOf { it.costoUsd }
        } else {
            val deHoy = filas.filter { java.time.Instant.ofEpochMilli(it.actualizado).atZone(ZoneId.systemDefault()).toLocalDate() == hoy }
            deHoy.sumOf { it.totales.total } to deHoy.sumOf { it.costoUsd ?: 0.0 }
        }
        provideContent { Contenido(filas, conectado, anillo, tokensHoy, costoHoy) }
    }

    @Composable
    private fun Contenido(filas: List<Uso>, conectado: Boolean, anillo: Bitmap, tokens: Long, costo: Double) {
        val amplio = LocalSize.current.width >= GRANDE.width
        val u = filas.firstOrNull()
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_fondo))
                .cornerRadius(24.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            // Encabezado: logo, título y botón de refrescar
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier.size(26.dp).background(ImageProvider(R.drawable.widget_logo)),
                    contentAlignment = Alignment.Center,
                ) { Icono(R.drawable.ic_chispa, BLANCO, 15) }
                Spacer(GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text("Claude Code", style = TextStyle(color = TEXTO, fontWeight = FontWeight.Bold, fontSize = 13.sp), maxLines = 1)
                    if (u != null) Text(u.proyecto, style = TextStyle(color = TENUE, fontSize = 11.sp), maxLines = 1)
                }
                Box(
                    modifier = GlanceModifier.size(30.dp).background(ImageProvider(R.drawable.widget_chip))
                        .clickable(actionRunCallback<Refrescar>()),
                    contentAlignment = Alignment.Center,
                ) { Icono(R.drawable.ic_refresh, TEXTO, 16) }
            }
            Spacer(GlanceModifier.height(10.dp))

            if (!conectado || u == null) {
                Text(
                    if (!conectado) "Abre la app para conectar tu cuenta." else "Sin datos todavía.",
                    style = TextStyle(color = TENUE, fontSize = 12.sp),
                )
                return@Column
            }

            val pct = u.porcentaje ?: 0
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                // Anillo de contexto
                Box(modifier = GlanceModifier.size(if (amplio) 78.dp else 64.dp), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(anillo), contentDescription = "Contexto $pct%", modifier = GlanceModifier.fillMaxSize())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$pct%",
                            style = TextStyle(color = TEXTO, fontWeight = FontWeight.Bold, fontSize = if (amplio) 18.sp else 15.sp),
                        )
                        if (amplio) Text("contexto", style = TextStyle(color = TENUE, fontSize = 9.sp))
                    }
                }
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        "${corto(u.contexto ?: 0)} / ${corto(u.ventana ?: 0)}",
                        style = TextStyle(color = TEXTO, fontWeight = FontWeight.Bold, fontSize = 14.sp), maxLines = 1,
                    )
                    Text(
                        estadoDe(pct),
                        style = TextStyle(color = ColorProvider(Color(colorDe(pct))), fontWeight = FontWeight.Medium, fontSize = 11.sp),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    u.limites.take(if (amplio) 3 else 1).forEach { l -> Barra(l.nombre, l.usado) }
                }
            }

            if (amplio) {
                Spacer(GlanceModifier.height(8.dp))
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Chip(R.drawable.ic_rayo, Color(0xFFA78BFA), "Hoy ${corto(tokens)}")
                    Spacer(GlanceModifier.width(6.dp))
                    Chip(R.drawable.ic_dinero, Color(0xFF34D399), "US$ ${"%.2f".format(costo)}")
                    Spacer(GlanceModifier.defaultWeight())
                    Icono(R.drawable.ic_reloj, TENUE, 11)
                    Spacer(GlanceModifier.width(3.dp))
                    Text(haceCuanto(u.actualizado), style = TextStyle(color = TENUE, fontSize = 10.sp), maxLines = 1)
                }
            } else {
                Text(haceCuanto(u.actualizado), style = TextStyle(color = TENUE, fontSize = 10.sp), maxLines = 1)
            }
        }
    }

    @Composable
    private fun Icono(id: Int, color: ColorProvider, tam: Int) {
        Image(
            ImageProvider(id), contentDescription = null,
            modifier = GlanceModifier.size(tam.dp), colorFilter = ColorFilter.tint(color),
        )
    }

    @Composable
    private fun Chip(icono: Int, color: Color, texto: String) {
        Row(
            modifier = GlanceModifier.background(ImageProvider(R.drawable.widget_chip)).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icono(icono, ColorProvider(color), 12)
            Spacer(GlanceModifier.width(4.dp))
            Text(texto, style = TextStyle(color = TEXTO, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }

    @Composable
    private fun Barra(nombre: String, porcentaje: Int) {
        val color = ColorProvider(Color(colorDe(porcentaje)))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(nombre, style = TextStyle(color = TENUE, fontSize = 10.sp), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
            Text("$porcentaje%", style = TextStyle(color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp))
        }
        Spacer(GlanceModifier.height(2.dp))
        LinearProgressIndicator(
            progress = porcentaje.coerceIn(0, 100) / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(5.dp).cornerRadius(3.dp),
            color = color,
            backgroundColor = VACIO,
        )
        Spacer(GlanceModifier.height(5.dp))
    }
}

/** Dibuja el anillo de progreso en un bitmap (Glance no sabe dibujar arcos). */
private fun anillo(pct: Int, lado: Int = 220): Bitmap {
    val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    val lienzo = Canvas(bmp)
    val grosor = lado * 0.11f
    val caja = RectF(grosor / 2, grosor / 2, lado - grosor / 2, lado - grosor / 2)
    val pista = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = grosor; color = 0x26FFFFFF
    }
    lienzo.drawArc(caja, 0f, 360f, false, pista)
    val barrido = 360f * pct.coerceIn(0, 100) / 100f
    if (barrido > 0f) {
        val c = lado / 2f
        val estado = colorDe(pct).toInt()
        val arco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = grosor; strokeCap = Paint.Cap.ROUND
            shader = SweepGradient(c, c, intArrayOf(0xFFA78BFA.toInt(), 0xFF60A5FA.toInt(), estado, 0xFFA78BFA.toInt()), floatArrayOf(0f, 0.45f, 0.9f, 1f))
                .also { g -> android.graphics.Matrix().apply { setRotate(-90f, c, c) }.let(g::setLocalMatrix) }
        }
        lienzo.drawArc(caja, -90f, barrido, false, arco)
    }
    return bmp
}

class TokensWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TokensWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Actualizador.programar(context)
    }
}

/** El botón ↻ del widget. */
class Refrescar : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Actualizador.ahora(context)
    }
}

/** Descarga lo último de Supabase y repinta los widgets. Android lo corre cada 15 min como mínimo. */
class Actualizador(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val supabase = Supabase(applicationContext)
        if (!supabase.conectado) {
            TokensWidget().updateAll(applicationContext)
            return Result.success()
        }
        return try {
            supabase.cargar()
            // Si el esquema aún no tiene consumo(), el widget sigue con lo de antes.
            runCatching { supabase.consumo() }
            TokensWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            TokensWidget().updateAll(applicationContext)
            Result.retry()
        }
    }

    companion object {
        private val red = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun programar(context: Context) {
            val pedido = PeriodicWorkRequestBuilder<Actualizador>(15, TimeUnit.MINUTES).setConstraints(red).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("tokens-periodico", ExistingPeriodicWorkPolicy.KEEP, pedido)
        }

        fun ahora(context: Context) {
            val pedido = OneTimeWorkRequestBuilder<Actualizador>().setConstraints(red).build()
            WorkManager.getInstance(context).enqueueUniqueWork("tokens-ahora", ExistingWorkPolicy.REPLACE, pedido)
        }
    }
}
