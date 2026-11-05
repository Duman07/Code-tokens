package com.contadortokens.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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
import com.contadortokens.app.Supabase
import com.contadortokens.app.Uso
import com.contadortokens.app.colorDe
import com.contadortokens.app.corto
import com.contadortokens.app.haceCuanto
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private val FONDO = ColorProvider(Color(0xFF0D1117))
private val VACIO = ColorProvider(Color(0xFF30363D))
private val TEXTO = ColorProvider(Color(0xFFE6EDF3))
private val TENUE = ColorProvider(Color(0xFF8B949E))

class TokensWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val supabase = Supabase(context)
        val filas = supabase.enCache()
        val conectado = supabase.conectado
        provideContent { Contenido(filas, conectado) }
    }

    @Composable
    private fun Contenido(filas: List<Uso>, conectado: Boolean) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(FONDO)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tokens",
                    style = TextStyle(color = TEXTO, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    "↻",
                    style = TextStyle(color = TENUE, fontSize = 16.sp),
                    modifier = GlanceModifier.padding(horizontal = 4.dp).clickable(actionRunCallback<Refrescar>()),
                )
            }
            Spacer(GlanceModifier.height(6.dp))

            val u = filas.firstOrNull()
            if (!conectado || u == null) {
                Text(
                    if (!conectado) "Abre la app para conectar tu cuenta." else "Sin datos todavía.",
                    style = TextStyle(color = TENUE, fontSize = 12.sp),
                )
                return@Column
            }

            Barra("Contexto", u.porcentaje ?: 0, "${corto(u.contexto ?: 0)} / ${corto(u.ventana ?: 0)}")
            u.limites.forEach { l -> Barra(l.nombre, l.usado, null) }

            val hoy = LocalDate.now()
            val deHoy = filas.filter {
                java.time.Instant.ofEpochMilli(it.actualizado).atZone(ZoneId.systemDefault()).toLocalDate() == hoy
            }
            val costo = deHoy.sumOf { it.costoUsd ?: 0.0 }
            val tokens = deHoy.sumOf { it.totales.total }
            Spacer(GlanceModifier.height(2.dp))
            Text(
                "Hoy ${corto(tokens)} · US$ ${"%.2f".format(costo)} · ${haceCuanto(u.actualizado)}",
                style = TextStyle(color = TENUE, fontSize = 11.sp),
            )
        }
    }

    @Composable
    private fun Barra(nombre: String, porcentaje: Int, detalle: String?) {
        val color = ColorProvider(Color(colorDe(porcentaje)))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                if (detalle != null) "$nombre · $detalle" else nombre,
                style = TextStyle(color = TENUE, fontSize = 11.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text("$porcentaje%", style = TextStyle(color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp))
        }
        LinearProgressIndicator(
            progress = (porcentaje.coerceIn(0, 100)) / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = color,
            backgroundColor = VACIO,
        )
        Spacer(GlanceModifier.height(6.dp))
    }
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
