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

## 1. Supabase (una sola vez)

1. Crea un proyecto en https://supabase.com (el plan gratuito sirve).
2. **SQL Editor → New query**: pega `supabase/esquema.sql` y pulsa **Run**.
3. **Authentication → Users → Add user**: crea tu usuario con correo y contraseña (marca *Auto Confirm User*).
4. **Project Settings → API**: copia la **Project URL** y la clave **anon public**.

La clave anon es pública: con ella solo se puede iniciar sesión o llamar a `reportar_uso` con un token válido.
Nunca uses la clave `service_role` en estas apps.

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
- Pon la URL, la clave anon, tu correo y tu contraseña de Supabase.
- **⚙ Ajustes → Generar token**: copia el token (empieza por `ctk_`). Se muestra una sola vez.
  Un token por equipo; desde ahí también los quitas.
- La ventana es pequeña (340 × 600) y queda **siempre encima**. Tiene modo compacto en Ajustes.

## 3. Conectar Claude Code

En Claude Code escribe `/config`, busca **contador-tokens** y llena:
- *Supabase: URL del proyecto*
- *Supabase: clave anon (pública)*
- *Token de dispositivo*: el `ctk_…` de la app de Windows. Se guarda en el almacén seguro.

El mod se recarga solo. Al terminar el siguiente turno, el panel de Tokens muestra «Sincronizado HH:MM»
o, si algo falla, «Sin sincronizar: motivo».

Si lo prefieres, en `~/.claude/settings.json`:
```json
"pluginConfigs": {
  "contador-tokens": {
    "options": {
      "supabase_url": "https://xxxx.supabase.co",
      "supabase_anon_key": "eyJ..."
    }
  }
}
```
Y el token por `/config`, para que no quede en texto plano.

## 4. App y widget de Android

1. Instala Android Studio.
2. **File → Open** y elige la carpeta `android/`. Android Studio descarga Gradle y las librerías.
3. Conecta el teléfono con la depuración USB activada y pulsa **Run ▶**.
   O bien **Build → Build APK(s)** y pasa el `.apk` al teléfono.
4. Abre la app **Tokens**, entra con tu correo y contraseña de Supabase.
5. En la pantalla de inicio: mantén pulsado → **Widgets → Tokens de Claude**.

El widget muestra el contexto de la sesión más reciente, los límites de 5 horas y 7 días, y lo gastado hoy.
Android solo deja actualizar widgets en segundo plano cada **15 min**. Toca **↻** para actualizar al momento,
o abre la app, que actualiza cada 20 s.

## Qué se sincroniza y qué no

- Lo que miden tus sesiones de **Claude Code**: contexto, tokens por tipo, costo, historial por turno y los
  límites de uso de la cuenta (5 h y 7 d), tal como los reportó la última sesión.
- No ve el uso del chat de claude.ai ni de la app de Claude por sí solo. Los porcentajes de los límites sí
  lo incluyen, pero solo se renuevan cuando una sesión de Claude Code termina un turno.
- Las sesiones de más de 30 días se borran solas.
