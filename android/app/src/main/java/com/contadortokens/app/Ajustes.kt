package com.contadortokens.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.contadortokens.app.widget.TokensWidget
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

// ---------- Ajustes ----------

@Composable
internal fun Ajustes(supabase: Supabase, alSalir: () -> Unit) {
    var vista by remember { mutableStateOf("inicio") }
    BackHandler(enabled = vista != "inicio") { vista = "inicio" }
    when (vista) {
        "cuentas" -> Subpantalla("Cuentas conectadas", { vista = "inicio" }) { Cuentas(supabase) }
        "llaves" -> Subpantalla("Llaves de dispositivo", { vista = "inicio" }) { Llaves(supabase) }
        "privacidad" -> Subpantalla("Política de privacidad", { vista = "inicio" }) { Documento(POLITICA_PRIVACIDAD) }
        "terminos" -> Subpantalla("Términos y condiciones", { vista = "inicio" }) { Documento(TERMINOS) }
        "licencias" -> Subpantalla("Licencias de código abierto", { vista = "inicio" }) { ListaLicencias() }
        else -> Inicio(supabase, alSalir) { vista = it }
    }
}

@Composable
internal fun Subpantalla(titulo: String, atras: () -> Unit, contenido: @Composable () -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Atrás", color = SALIDA, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(onClick = atras).padding(end = 14.dp, top = 6.dp, bottom = 6.dp))
        }
        Text(titulo, color = TEXTO, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        contenido()
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun Inicio(supabase: Supabase, alSalir: () -> Unit, ir: (String) -> Unit) {
    val contexto = LocalContext.current
    var borrar by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val alcance = rememberCoroutineScope()

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Ajustes", color = TEXTO, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Tarjeta("Apariencia", R.drawable.ic_widgets, SALIDA) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("sistema" to "Sistema", "claro" to "Claro", "oscuro" to "Oscuro").forEach { (clave, nombre) ->
                    val activo = Tema.modo == clave
                    val forma = RoundedCornerShape(50)
                    Text(
                        nombre, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = if (activo) androidx.compose.ui.graphics.Color.White else TENUE,
                        modifier = Modifier.weight(1f).clip(forma)
                            .background(if (activo) DEGRADADO else androidx.compose.ui.graphics.SolidColor(VACIO))
                            .clickable {
                                Tema.elegir(contexto, clave)
                            }.padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        Tarjeta("Cuenta", R.drawable.ic_correo, ENTRADA) {
            Text(supabase.correo.ifEmpty { "Sesión iniciada" }, color = TEXTO, fontSize = 15.sp)
            Fila("Cuentas conectadas", "Claude, Codex, Cursor, Antigravity…") { ir("cuentas") }
            Fila("Llaves de dispositivo", "Revocar equipos o crear una a mano") { ir("llaves") }
            Fila("Cerrar sesión", null, ERROR, alSalir)
            Fila("Eliminar cuenta y datos", "Borra todo, no se puede deshacer", ERROR) { borrar = true }
            if (error.isNotEmpty()) Aviso(error)
        }

        Tarjeta("Legal", R.drawable.ic_candado, CACHE) {
            Fila("Política de privacidad", null) { ir("privacidad") }
            Fila("Términos y condiciones", null) { ir("terminos") }
            Fila("Licencias de código abierto", null) { ir("licencias") }
            if (Anuncios.requierePrivacidad) {
                val actividad = LocalContext.current as android.app.Activity
                Fila("Privacidad de anuncios", "Cambia tu consentimiento") { Anuncios.opcionesPrivacidad(actividad) }
            }
        }

        Tarjeta("Acerca de", R.drawable.ic_chispa, CACHE_ESCRITA) {
            Text("Contador de tokens ${BuildConfig.VERSION_NAME}", color = TEXTO, fontSize = 15.sp)
            Text(
                "Proyecto independiente, no afiliado a Anthropic. Contacto: $CONTACTO",
                color = TENUE, fontSize = 12.sp, lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.height(84.dp))
    }

    if (borrar) AlertDialog(
        onDismissRequest = { borrar = false },
        containerColor = TARJETA,
        title = { Text("¿Eliminar tu cuenta?", color = TEXTO) },
        text = {
            Text(
                "Se borrarán tu cuenta, tus llaves y todo tu consumo. Claude Code dejará de sincronizar. No se puede deshacer.",
                color = TENUE,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                borrar = false
                alcance.launch {
                    try {
                        supabase.eliminarCuenta()
                        TokensWidget().updateAll(contexto)
                        alSalir()
                    } catch (e: Exception) {
                        error = e.message ?: "No se pudo eliminar la cuenta"
                    }
                }
            }) { Text("Eliminar", color = ERROR) }
        },
        dismissButton = { TextButton(onClick = { borrar = false }) { Text("Cancelar", color = TEXTO) } },
    )
}

@Composable
private fun Fila(titulo: String, detalle: String?, color: androidx.compose.ui.graphics.Color = TEXTO, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            detalle?.let { Text(it, color = TENUE, fontSize = 12.sp) }
        }
        Text("›", color = TENUE, fontSize = 18.sp)
    }
}

@Composable
internal fun Documento(secciones: List<Seccion>) {
    Text("Última actualización: $ACTUALIZADO", color = TENUE, fontSize = 12.sp)
    secciones.forEach {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(it.titulo, color = TEXTO, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(it.texto, color = TENUE, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun ListaLicencias() {
    Text("Esta app usa estas librerías de código abierto:", color = TENUE, fontSize = 14.sp)
    LICENCIAS.forEach {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TARJETA)
                .border(1.dp, BORDE, RoundedCornerShape(16.dp)).padding(14.dp),
        ) {
            Text(it.nombre, color = TEXTO, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("${it.autor} · ${it.licencia}", color = TENUE, fontSize = 12.sp)
        }
    }
    Text(
        "Apache License 2.0: http://www.apache.org/licenses/LICENSE-2.0",
        color = TENUE, fontSize = 12.sp,
    )
}

// ---------- Llaves de dispositivo ----------

@Composable
private fun Llaves(supabase: Supabase) {
    var lista by remember { mutableStateOf<List<Llave>>(emptyList()) }
    var nombre by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var quitar by remember { mutableStateOf<Llave?>(null) }
    var version by remember { mutableStateOf(0) }
    val alcance = rememberCoroutineScope()
    val portapapeles = LocalClipboardManager.current

    LaunchedEffect(version) {
        try { lista = supabase.llaves(); error = "" } catch (e: Exception) { error = e.message ?: "Error" }
    }

    Text(
        "Cada llave deja a un equipo enviar su consumo a tu cuenta. Lo normal es vincular con un código desde la pestaña " +
            "Ahora; crea una llave a mano solo si lo necesitas (por ejemplo, para /config en Claude Code).",
        color = TENUE, fontSize = 13.sp, lineHeight = 18.sp,
    )
    if (error.isNotEmpty()) Aviso(error)

    if (nueva.isNotEmpty()) Tarjeta("Llave nueva", R.drawable.ic_llave, CACHE) {
        Text("Cópiala ahora: no se vuelve a mostrar.", color = TENUE, fontSize = 13.sp)
        Text(
            nueva, color = TEXTO, fontSize = 13.sp,
            modifier = Modifier.clickable { portapapeles.setText(AnnotatedString(nueva)) },
        )
        Text("Toca para copiar", color = SALIDA, fontSize = 12.sp)
    }

    Tarjeta("Crear llave", R.drawable.ic_llave, SALIDA) {
        Campo(nombre, { nombre = it }, "Nombre del equipo", R.drawable.ic_equipo)
        BotonDegradado("Crear llave", habilitado = true, modifier = Modifier.fillMaxWidth()) {
            alcance.launch {
                try {
                    nueva = supabase.crearLlave(nombre.ifBlank { "Equipo" })
                    nombre = ""; version++
                } catch (e: Exception) { error = e.message ?: "Error" }
            }
        }
    }

    Tarjeta("Llaves activas", R.drawable.ic_equipo, ENTRADA, extra = "${lista.size}") {
        if (lista.isEmpty()) Text("No hay llaves.", color = TENUE, fontSize = 14.sp)
        lista.forEach { l ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l.nombre, color = TEXTO, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (l.usado == null) "Sin usar todavía" else "Último uso " + haceCuanto(fecha(l.usado)),
                        color = TENUE, fontSize = 12.sp,
                    )
                }
                Text("Revocar", color = ERROR, fontSize = 13.sp, modifier = Modifier.clickable { quitar = l }.padding(8.dp))
            }
        }
    }

    quitar?.let { l ->
        AlertDialog(
            onDismissRequest = { quitar = null },
            containerColor = TARJETA,
            title = { Text("¿Revocar «${l.nombre}»?", color = TEXTO) },
            text = { Text("Ese Claude Code dejará de sincronizar hasta que lo vincules otra vez.", color = TENUE) },
            confirmButton = {
                TextButton(onClick = {
                    quitar = null
                    alcance.launch {
                        try { supabase.revocarLlave(l.id); version++ } catch (e: Exception) { error = e.message ?: "Error" }
                    }
                }) { Text("Revocar", color = ERROR) }
            },
            dismissButton = { TextButton(onClick = { quitar = null }) { Text("Cancelar", color = TEXTO) } },
        )
    }
}

private fun fecha(s: String): Long =
    runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrDefault(0L)

// ---------- Cuentas conectadas ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Cuentas(supabase: Supabase) {
    var lista by remember { mutableStateOf<List<Cuenta>>(emptyList()) }
    var proveedor by remember { mutableStateOf("codex") }
    var etiqueta by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(0) }
    var codigos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var quitar by remember { mutableStateOf<Cuenta?>(null) }
    val alcance = rememberCoroutineScope()
    val portapapeles = LocalClipboardManager.current
    val tieneClaude = lista.any { it.proveedor == "claude" }

    LaunchedEffect(version) {
        try { lista = supabase.cuentas(); error = "" } catch (e: Exception) { error = e.message ?: "Error" }
    }
    // Si Claude ya está conectado, el selector no puede quedarse en Claude.
    LaunchedEffect(tieneClaude) { if (tieneClaude && proveedor == "claude") proveedor = "codex" }

    Text(
        "Puedes ver el consumo de una sola cuenta de Claude. De los demás proveedores puedes conectar las que quieras. " +
            "Una misma cuenta puede tener varios equipos.",
        color = TENUE, fontSize = 13.sp, lineHeight = 18.sp,
    )
    if (error.isNotEmpty()) Aviso(error)

    lista.forEach { c ->
        val nombreProv = PROVEEDORES.firstOrNull { it.first == c.proveedor }?.second ?: c.proveedor
        Tarjeta(c.etiqueta, R.drawable.ic_equipo, if (c.proveedor == "claude") SALIDA else ENTRADA, extra = nombreProv) {
            codigos[c.id]?.let { k ->
                Text(
                    k.chunked(4).joinToString(" "), color = TEXTO, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp, modifier = Modifier.clickable { portapapeles.setText(AnnotatedString(k)) },
                )
                Text(
                    if (c.proveedor == "claude") "En Claude Code escribe:  /tokens vincular $k"
                    else "Código para el conector de $nombreProv (todavía no existe uno listo; la cuenta queda preparada).",
                    color = TENUE, fontSize = 13.sp, lineHeight = 18.sp,
                )
                Text("Vale 10 minutos y se usa una vez. Toca el código para copiarlo.", color = TENUE, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BotonDegradado(if (codigos.containsKey(c.id)) "Otro código" else "Vincular equipo", habilitado = true, modifier = Modifier.weight(1f)) {
                    alcance.launch {
                        try { codigos = codigos + (c.id to supabase.crearCodigo(c.id)); error = "" }
                        catch (e: Exception) { error = e.message ?: "Error" }
                    }
                }
                Text("Quitar", color = ERROR, fontSize = 14.sp, modifier = Modifier.clickable { quitar = c }.padding(8.dp))
            }
        }
    }

    Tarjeta("Añadir cuenta", R.drawable.ic_enlace, CACHE) {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PROVEEDORES.forEach { (clave, nombre) ->
                val bloqueado = clave == "claude" && tieneClaude
                val activo = proveedor == clave
                val forma = RoundedCornerShape(50)
                Text(
                    nombre, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (activo) androidx.compose.ui.graphics.Color.White else if (bloqueado) TENUE.copy(alpha = 0.4f) else TENUE,
                    modifier = Modifier.clip(forma)
                        .background(if (activo) DEGRADADO else androidx.compose.ui.graphics.SolidColor(VACIO))
                        .clickable(enabled = !bloqueado) { proveedor = clave }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
        if (tieneClaude) Text("Ya tienes una cuenta de Claude; añade más equipos a esa.", color = TENUE, fontSize = 12.sp)
        Campo(etiqueta, { etiqueta = it }, "Nombre (opcional)", R.drawable.ic_correo)
        BotonDegradado("Añadir", habilitado = true, modifier = Modifier.fillMaxWidth()) {
            alcance.launch {
                try { supabase.crearCuenta(proveedor, etiqueta); etiqueta = ""; version++; error = "" }
                catch (e: Exception) { error = e.message ?: "Error" }
            }
        }
    }

    quitar?.let { c ->
        AlertDialog(
            onDismissRequest = { quitar = null },
            containerColor = TARJETA,
            title = { Text("¿Quitar «${c.etiqueta}»?", color = TEXTO) },
            text = { Text("Sus equipos dejarán de sincronizar. El consumo ya guardado se conserva.", color = TENUE) },
            confirmButton = {
                TextButton(onClick = {
                    quitar = null
                    alcance.launch {
                        try { supabase.quitarCuenta(c.id); version++ } catch (e: Exception) { error = e.message ?: "Error" }
                    }
                }) { Text("Quitar", color = ERROR) }
            },
            dismissButton = { TextButton(onClick = { quitar = null }) { Text("Cancelar", color = TEXTO) } },
        )
    }
}
