# Contador de tokens: mini ventana para Windows y widget para Android

```
Claude Code (mod contador-tokens)
   │  después de cada turno: reportar_uso(token de dispositivo, medición)
   ▼
Supabase (tablas uso_sesion y tokens_dispositivo, con RLS)
   ▲                                 ▲
   │ cada 15 s mientras está abierta │ cada 15 min, o al tocar ↻
App de Windows (Tauri)          App + widget de Android (Kotlin / Glance)
```

- `supabase/esquema.sql`: tablas, permisos y funciones.
- `windows/`: mini ventana siempre visible (Tauri 2, HTML y JS sin dependencias).
- `android/`: app y widget de pantalla de inicio.
- `claude-code-mod/contador-tokens/`: el mod de Claude Code (panel + sincronización). Para instalarlo, copia esa carpeta a `~/.claude/skills/contador-tokens` y reinicia Claude Code.

## 0. Para cualquier usuario (cuenta propia, sin montar nada)

Quien publica la app monta **un** Supabase (sección 1) y lo deja integrado:
- Android: en `android/local.properties` añade `SUPABASE_URL=https://xxxx.supabase.co` y `SUPABASE_KEY=sb_publishable_…` y compila.
- Mod: pon la misma URL y clave en `SERVIDOR` de `claude-code-mod/contador-tokens/hooks/register.tsx`.
- En Supabase, **Authentication → Providers → Email** activado (con o sin confirmación de correo).

Cada usuario después:
1. Abre la app de Android → **Crear cuenta** (correo y contraseña de 8+ caracteres).
2. Instala el mod en Claude Code: `/plugin install contador-tokens --marketplace Duman07/Code-tokens` (requiere el repositorio público).
3. En la app, tarjeta **Vincular Claude Code → Generar código**; en Claude Code escribe `/tokens vincular CÓDIGO`.
   Desde ese turno sus datos aparecen en la app. `/tokens desvincular` lo quita de ese equipo.

Cada usuario solo ve sus datos (RLS). Los códigos valen 10 minutos y se usan una vez.

## 1. Supabase (una sola vez)

1. Crea un proyecto en https://supabase.com (el plan gratuito sirve).
2. **SQL Editor → New query**: pega `supabase/esquema.sql` y pulsa **Run**.
3. **Authentication → Users → Add user**: crea tu usuario con correo y contraseña (marca *Auto Confirm User*).
4. **Project Settings → API Keys**: copia la **Publishable key** (`sb_publishable_…`), o la **anon** antigua (`eyJ…`) si tu proyecto la tiene. La URL (`https://xxxx.supabase.co`) está en **Project Settings → Data API**.

La clave publishable (o anon) es pública: con ella solo se puede iniciar sesión o llamar a `reportar_uso` con un token válido.
Nunca uses la clave `service_role` en estas apps.

**Al actualizar:** si ya tenías el esquema, vuelve a pegar `supabase/esquema.sql` y pulsa **Run**. Se puede
ejecutar varias veces sin perder datos. Así se crea, por ejemplo, la tabla `uso_hora` que usa la pestaña
**Consumo** de Android (día, semana, mes y por sesión).

## 2. App de Windows

En una PC con Windows:

1. Instala Rust (https://rustup.rs) y, si te lo pide, las *Build Tools* de Visual Studio (C++). WebView2 ya viene con Windows 10 y 11.
2. `cargo install tauri-cli --version "^2" --locked`
3. Copia la carpeta `windows/` y, dentro de ella:
   ```
   cd src-tauri
   cargo tauri build
   ```
4. El instalador queda en `src-tauri\target\release\bundle\nsis\Contador de tokens_0.1.0_x64-setup.exe`.
   Para probar sin instalar: `cargo tauri dev`.

Al abrirla:
- Pon la URL, la clave pública (`sb_publishable_…`), tu correo y tu contraseña de Supabase.
- **⚙ Ajustes → Generar token**: copia el token (empieza por `ctk_`). Se muestra una sola vez.
  Un token por equipo; desde ahí también los quitas.
- La ventana es pequeña (340 × 600) y queda **siempre encima**. Tiene modo compacto en Ajustes.

## 3. Conectar Claude Code

En Claude Code escribe `/config`, busca **contador-tokens** y llena:
- *Supabase: URL del proyecto*
- *Supabase: clave pública (publishable o anon)*
- *Token de dispositivo*: el `ctk_…` de la app de Windows. Queda en `~/.claude/settings.json`; solo sirve para reportar uso y lo puedes revocar desde la app.

El mod se recarga solo. Al terminar el siguiente turno, el panel de Tokens muestra «Sincronizado HH:MM»
o, si algo falla, «Sin sincronizar: motivo».

Si lo prefieres, en `~/.claude/settings.json`:
```json
"pluginConfigs": {
  "contador-tokens": {
    "options": {
      "supabase_url": "https://xxxx.supabase.co",
      "supabase_anon_key": "sb_publishable_..."
    }
  }
}
```
El token también se pone por `/config`.

## 4. App y widget de Android

1. Instala Android Studio.
2. **File → Open** y elige la carpeta `android/`. Android Studio descarga Gradle y las librerías.
3. Conecta el teléfono con la depuración USB activada y pulsa **Run ▶**.
   O bien **Build → Build APK(s)** y pasa el `.apk` al teléfono.
4. Abre la app **Tokens**, entra con tu correo y contraseña de Supabase.
5. En la pantalla de inicio: mantén pulsado → **Widgets → Tokens de Claude**.

El widget muestra el contexto de la sesión más reciente, los límites de 5 horas y 7 días, y lo gastado hoy.
Android solo deja actualizar widgets en segundo plano cada **15 min**. Toca **↻** para actualizar al momento,
o abre la app, que actualiza cada 60 s.

## Qué se sincroniza y qué no

- Lo que miden tus sesiones de **Claude Code**: contexto, tokens por tipo, costo, historial por turno y los
  límites de uso de la cuenta (5 h y 7 d), tal como los reportó la última sesión.
- No ve el uso del chat de claude.ai ni de la app de Claude por sí solo. Los porcentajes de los límites sí
  lo incluyen, pero solo se renuevan cuando una sesión de Claude Code termina un turno.
- Las sesiones de más de 30 días se borran solas.

## Textos legales en la web (para Google Play)

`python3 tools/exportar_legal.py` genera `docs/` (privacidad, términos y eliminar cuenta) a partir de
`Legal.kt`, así la app y la web dicen lo mismo. Vuelve a ejecutarlo cada vez que cambies los textos.
Para publicarlo: en GitHub, **Settings → Pages → Deploy from a branch → `main` / `docs`**. Las URLs quedan en
`https://<usuario>.github.io/<repositorio>/privacidad.html` (y `terminos.html`, `eliminar-cuenta.html`).
Play Console pide la de privacidad y la de eliminar cuenta.

## Anuncios (apagados por defecto)

La app no muestra anuncios mientras no pongas en `android/local.properties` los IDs reales de AdMob
(`ADMOB_APP_ID`, `ADMOB_BANNER`). Para ver el diseño con anuncios de prueba, usa los IDs de prueba de Google:
`ADMOB_APP_ID=ca-app-pub-3940256099942544~3347511713` y `ADMOB_BANNER=ca-app-pub-3940256099942544/9214589741`.
