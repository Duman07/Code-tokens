package com.contadortokens.app

/**
 * Textos legales de la app. SON BORRADORES: revísalos (idealmente con un abogado) antes de publicar,
 * y pon tu correo real en CONTACTO. Los mismos textos están en docs/ para publicarlos en una página web,
 * porque Google Play pide una URL pública de la política de privacidad.
 */
const val CONTACTO = "soporte@tudominio.com"
const val ACTUALIZADO = "6 de noviembre de 2026"

data class Seccion(val titulo: String, val texto: String)

val POLITICA_PRIVACIDAD = listOf(
    Seccion(
        "Qué datos recogemos",
        "• Tu correo electrónico y una contraseña (guardada cifrada por el proveedor de autenticación; nunca la vemos).\n" +
            "• Las mediciones que envía tu Claude Code: cantidad de tokens (entrada, salida, caché), costo estimado, " +
            "porcentaje de contexto, límites de uso, modelo usado, identificador de sesión, el nombre de la carpeta del proyecto " +
            "y el nombre que le diste a cada llave de dispositivo.\n" +
            "• No recogemos el contenido de tus conversaciones, tu código, tus archivos ni tus prompts.\n" +
            "• No usamos publicidad, analítica de terceros ni rastreadores.",
    ),
    Seccion(
        "Para qué los usamos",
        "Solo para mostrarte tu consumo en la app y en el widget, y para que puedas vincular y revocar tus equipos. " +
            "No vendemos ni compartimos tus datos con terceros con fines comerciales.",
    ),
    Seccion(
        "Dónde se guardan",
        "En Supabase (base de datos y autenticación), con acceso restringido por usuario: cada cuenta solo puede leer sus propias filas. " +
            "Las conexiones van cifradas (HTTPS). En tu teléfono se guarda una copia local de tu último consumo y tu sesión.",
    ),
    Seccion(
        "Cuánto tiempo",
        "El detalle de cada sesión se borra solo a los 30 días y el consumo por hora a los 400 días. " +
            "Puedes borrar sesiones sueltas, revocar llaves o eliminar tu cuenta cuando quieras.",
    ),
    Seccion(
        "Tus derechos",
        "Puedes ver tus datos en la app, borrarlos y eliminar tu cuenta desde Ajustes → Eliminar cuenta. " +
            "Al eliminarla se borran de inmediato tu cuenta, tus llaves y todo tu consumo. " +
            "Para cualquier otra petición escribe a $CONTACTO.",
    ),
    Seccion(
        "Menores",
        "La app no está dirigida a menores de 13 años (o la edad mínima de tu país) y no recogemos sus datos a sabiendas.",
    ),
    Seccion(
        "Cambios y contacto",
        "Si cambiamos esta política, lo avisaremos en la app. Última actualización: $ACTUALIZADO. Contacto: $CONTACTO.",
    ),
)

val TERMINOS = listOf(
    Seccion(
        "1. Qué es esta app",
        "Contador de tokens muestra el consumo de tokens de tu Claude Code. Es un proyecto independiente: " +
            "no está afiliado a Anthropic ni respaldado por ella. «Claude» es marca de Anthropic.",
    ),
    Seccion(
        "2. Tu cuenta",
        "Eres responsable de tu contraseña y de tus llaves de dispositivo. Si crees que una llave se filtró, revócala en " +
            "Ajustes → Llaves de dispositivo. Una llave solo permite enviar mediciones a tu cuenta, no leerlas.",
    ),
    Seccion(
        "3. Uso aceptable",
        "Cada usuario puede conectar una sola cuenta de Claude (con todos los equipos que quiera); de otros proveedores puede conectar varias. " +
            "No intentes acceder a datos de otros usuarios, saturar el servicio, ni usarlo para fines ilegales. " +
            "Podemos suspender cuentas que lo hagan.",
    ),
    Seccion(
        "4. Exactitud de los datos",
        "Las cifras y costos son estimaciones tomadas de lo que reporta Claude Code; pueden no coincidir con tu factura. " +
            "No uses la app como única fuente para decisiones de facturación.",
    ),
    Seccion(
        "5. Sin garantías",
        "El servicio se ofrece «tal cual», sin garantías de disponibilidad. En la medida que permita la ley, no respondemos " +
            "por pérdidas derivadas de su uso o de una interrupción.",
    ),
    Seccion(
        "6. Terminar",
        "Puedes dejar de usar la app y eliminar tu cuenta cuando quieras. Podemos modificar o cerrar el servicio avisando con antelación razonable.",
    ),
    Seccion("7. Contacto", "$CONTACTO. Última actualización: $ACTUALIZADO."),
)

data class Licencia(val nombre: String, val autor: String, val licencia: String)

/** Librerías que lleva la app (todas Apache 2.0). Ponla al día si añades dependencias. */
val LICENCIAS = listOf(
    Licencia("Jetpack Compose (UI, Material 3, Foundation)", "Google / AndroidX", "Apache License 2.0"),
    Licencia("Jetpack Glance (widget)", "Google / AndroidX", "Apache License 2.0"),
    Licencia("AndroidX Activity, Lifecycle, WorkManager", "Google / AndroidX", "Apache License 2.0"),
    Licencia("Kotlin y kotlinx.coroutines", "JetBrains", "Apache License 2.0"),
)
