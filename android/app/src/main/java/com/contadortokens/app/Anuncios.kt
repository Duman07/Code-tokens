package com.contadortokens.app

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Publicidad con Google AdMob. Antes de pedir ningún anuncio se pasa por el formulario de consentimiento
 * de Google (UMP), que solo aparece donde la ley lo exige (UE, Reino Unido…).
 * Los IDs vienen de local.properties (ADMOB_APP_ID, ADMOB_BANNER); sin ellos se usan los de PRUEBA de Google.
 */
object Anuncios {
    /** Verdadero cuando ya se puede pedir un anuncio (hay consentimiento o no hace falta). */
    var listo by mutableStateOf(false)
        private set
    var requierePrivacidad by mutableStateOf(false)
        private set
    private val iniciado = AtomicBoolean(false)

    fun iniciar(actividad: Activity) {
        if (!BuildConfig.ADS_REALES) return // sin IDs reales de AdMob no se muestra ni se inicia nada
        val info = UserMessagingPlatform.getConsentInformation(actividad)
        info.requestConsentInfoUpdate(
            actividad, ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(actividad) { _ -> actualizar(actividad, info) }
                actualizar(actividad, info) // si ya había consentimiento de otra vez
            },
            { _ -> actualizar(actividad, info) },
        )
        actualizar(actividad, info)
    }

    private fun actualizar(actividad: Activity, info: ConsentInformation) {
        requierePrivacidad = info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (info.canRequestAds() && iniciado.compareAndSet(false, true)) {
            MobileAds.initialize(actividad.applicationContext) { listo = true }
        }
    }

    fun opcionesPrivacidad(actividad: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(actividad) { _ -> actualizar(actividad, UserMessagingPlatform.getConsentInformation(actividad)) }
    }
}

/** Banner adaptable. AdMob lo renueva solo (el intervalo se fija en la consola de AdMob), así rotan los anuncios. */
@Composable
fun BannerAnuncios() {
    if (!Anuncios.listo) return
    val contexto = LocalContext.current
    val ancho = LocalConfiguration.current.screenWidthDp
    Column(Modifier.fillMaxWidth().background(TARJETA), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Publicidad", color = TENUE, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        AndroidView(
            factory = {
                AdView(contexto).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(contexto, ancho))
                    adUnitId = BuildConfig.ADMOB_BANNER
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
