import { COLOR, PARTES, corto, esc, numero, svgContexto, svgHistorial, svgLimites } from './graficos.js'

const CADA_MS = 15_000
const $ = id => document.getElementById(id)

// ---------- Guardado local ----------
const guardado = {
  get: () => JSON.parse(localStorage.getItem('contador-tokens') || '{}'),
  set: v => localStorage.setItem('contador-tokens', JSON.stringify({ ...guardado.get(), ...v })),
  borrarSesion: () => {
    const g = guardado.get()
    delete g.sesion
    localStorage.setItem('contador-tokens', JSON.stringify(g))
  },
}

// ---------- Supabase (REST, sin librerías) ----------
async function auth(tipo, cuerpo) {
  const { url, clave } = guardado.get()
  const r = await fetch(`${url}/auth/v1/token?grant_type=${tipo}`, {
    method: 'POST',
    headers: { apikey: clave, 'Content-Type': 'application/json' },
    body: JSON.stringify(cuerpo),
  })
  const datos = await r.json().catch(() => ({}))
  if (!r.ok) throw new Error(datos.error_description || datos.msg || datos.message || `HTTP ${r.status}`)
  guardado.set({
    sesion: {
      access_token: datos.access_token,
      refresh_token: datos.refresh_token,
      expira: Date.now() + (datos.expires_in ?? 3600) * 1000,
      correo: datos.user?.email,
    },
  })
}

async function renovar() {
  try {
    await auth('refresh_token', { refresh_token: guardado.get().sesion?.refresh_token })
  } catch (e) {
    guardado.borrarSesion()
    throw new Error('La sesión venció; vuelve a entrar.')
  }
}

async function tokenVigente() {
  const s = guardado.get().sesion
  if (!s) throw new Error('Sin sesión')
  if (Date.now() > s.expira - 60_000) await renovar()
  return guardado.get().sesion.access_token
}

async function api(ruta, opciones = {}) {
  const { url, clave } = guardado.get()
  const hacer = async () =>
    fetch(`${url}${ruta}`, {
      ...opciones,
      headers: {
        apikey: clave,
        Authorization: `Bearer ${await tokenVigente()}`,
        'Content-Type': 'application/json',
        ...(opciones.headers || {}),
      },
    })
  let r = await hacer()
  if (r.status === 401) {
    await renovar()
    r = await hacer()
  }
  const texto = await r.text()
  const datos = texto ? JSON.parse(texto) : null
  if (!r.ok) throw new Error(datos?.message || `HTTP ${r.status}`)
  return datos
}

// ---------- Vistas ----------
function mostrar(vista) {
  for (const v of ['entrar', 'panel', 'ajustes']) $(`vista-${v}`).hidden = v !== vista
}

let filas = []
let elegida = ''
let error = ''

function haceCuanto(iso) {
  const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000))
  if (s < 60) return 'hace un momento'
  if (s < 3600) return `hace ${Math.round(s / 60)} min`
  if (s < 86400) return `hace ${Math.round(s / 3600)} h`
  return `hace ${Math.round(s / 86400)} d`
}

function pintarSelector() {
  const sel = $('sesiones')
  sel.innerHTML = filas
    .map(
      f =>
        `<option value="${esc(f.sesion)}">${esc(f.proyecto || 'sesión')} · ${esc(f.dispositivo)} · ${haceCuanto(f.actualizado)}</option>`,
    )
    .join('')
  if (filas.some(f => f.sesion === elegida)) sel.value = elegida
}

function pintar() {
  const caja = $('contenido')
  const compacto = !!guardado.get().compacto
  if (!filas.length) {
    caja.innerHTML = `<div class="vacio tenue">Todavía no llegan datos.<br/><br/>Conecta Claude Code en Ajustes (⚙) y termina un turno.</div>`
    pintarEstado()
    return
  }
  const u = filas.find(f => f.sesion === elegida) || filas[0]
  // Los límites son de la cuenta: se toman de la medición más reciente de cualquier sesión.
  const limites = (filas[0].limites || []).length ? filas[0].limites : u.limites || []
  const t = u.totales || {}
  const hoy = new Date().toDateString()
  const deHoy = filas.filter(f => new Date(f.actualizado).toDateString() === hoy)
  const tokensHoy = deHoy.reduce((a, f) => a + PARTES.reduce((b, p) => b + (f.totales?.[p.campo] || 0), 0), 0)
  const costoHoy = deHoy.reduce((a, f) => a + Number(f.costo_usd || 0), 0)

  let html = ''
  html += `<div class="tarjeta"><h4>Contexto · ${esc(u.modelo || '')}</h4>${svgContexto(u)}</div>`
  if (limites.length) html += `<div class="tarjeta"><h4>Límites de la cuenta</h4>${svgLimites(limites)}</div>`
  if (!compacto) {
    if ((u.historial || []).length) html += `<div class="tarjeta"><h4>Por turno</h4>${svgHistorial(u.historial)}</div>`
    html += `<div class="tarjeta"><h4>Sesión · ${t.turnos ?? 0} turnos${t.turnosSubagentes ? ` + ${t.turnosSubagentes} de subagentes` : ''}</h4><div class="cifras">
      ${PARTES.map(p => `<span><span style="color:${p.color}">■</span> ${p.nombre}</span><b>${numero(t[p.campo])}</b>`).join('')}
    </div></div>`
    html += `<div class="tarjeta"><h4>Hoy, todas las sesiones</h4><div class="cifras">
      <span>Sesiones</span><b>${deHoy.length}</b>
      <span>Tokens</span><b>${corto(tokensHoy)}</b>
      <span>Costo</span><b>US$ ${costoHoy.toFixed(2)}</b>
    </div></div>`
  }
  caja.innerHTML = html
  pintarEstado()
}

function pintarEstado() {
  const u = filas.find(f => f.sesion === elegida) || filas[0]
  const partes = []
  if (u) partes.push(`Medido ${haceCuanto(u.actualizado)}`)
  if (error) partes.push(`<span style="color:${COLOR.alto}">${esc(error)}</span>`)
  $('estado').innerHTML = partes.join(' · ')
}

async function cargar() {
  try {
    filas = await api('/rest/v1/uso_sesion?select=*&order=actualizado.desc&limit=15')
    if (!elegida || !filas.some(f => f.sesion === elegida)) elegida = filas[0]?.sesion || ''
    error = ''
  } catch (e) {
    error = e.message
    if (!guardado.get().sesion) return entrar()
  }
  pintarSelector()
  pintar()
}

$('sesiones').addEventListener('change', e => {
  elegida = e.target.value
  pintar()
})

// ---------- Entrar ----------
function entrar() {
  const g = guardado.get()
  const f = $('form-entrar')
  f.url.value = g.url || ''
  f.clave.value = g.clave || ''
  mostrar('entrar')
}

$('form-entrar').addEventListener('submit', async ev => {
  ev.preventDefault()
  const f = ev.target
  $('error-entrar').textContent = ''
  guardado.set({ url: f.url.value.trim().replace(/\/+$/, ''), clave: f.clave.value.trim() })
  try {
    await auth('password', { email: f.correo.value.trim(), password: f.clave_usuario.value })
    f.clave_usuario.value = ''
    abrirPanel()
  } catch (e) {
    $('error-entrar').textContent = e.message
  }
})

// ---------- Ajustes ----------
async function abrirAjustes() {
  mostrar('ajustes')
  const g = guardado.get()
  $('cuenta').textContent = `${g.sesion?.correo ?? ''} · ${g.url ?? ''}`
  $('compacto').checked = !!g.compacto
  $('token-generado').hidden = true
  await pintarEquipos()
}

async function pintarEquipos() {
  try {
    const equipos = await api('/rest/v1/tokens_dispositivo?select=id,nombre,creado,usado&order=creado.desc')
    $('equipos').innerHTML = equipos.length
      ? equipos
          .map(
            q =>
              `<li>${esc(q.nombre)} · ${q.usado ? `último envío ${haceCuanto(q.usado)}` : 'sin uso'}<button data-id="${q.id}">Quitar</button></li>`,
          )
          .join('')
      : '<li>Ninguno todavía.</li>'
  } catch (e) {
    $('equipos').innerHTML = `<li class="error">${esc(e.message)}</li>`
  }
}

$('equipos').addEventListener('click', async ev => {
  const id = ev.target?.dataset?.id
  if (!id) return
  await api(`/rest/v1/tokens_dispositivo?id=eq.${encodeURIComponent(id)}`, { method: 'DELETE' })
  await pintarEquipos()
})

$('btn-token').addEventListener('click', async () => {
  try {
    const token = await api('/rest/v1/rpc/crear_token_dispositivo', {
      method: 'POST',
      body: JSON.stringify({ p_nombre: $('nombre-equipo').value }),
    })
    $('token-texto').textContent = token
    $('token-generado').hidden = false
    await pintarEquipos()
  } catch (e) {
    alert(e.message)
  }
})

$('btn-copiar').addEventListener('click', async () => {
  await navigator.clipboard.writeText($('token-texto').textContent)
  $('btn-copiar').textContent = 'Copiado'
  setTimeout(() => ($('btn-copiar').textContent = 'Copiar'), 1500)
})

$('compacto').addEventListener('change', e => {
  guardado.set({ compacto: e.target.checked })
})

$('btn-ajustes').addEventListener('click', abrirAjustes)
$('btn-volver').addEventListener('click', abrirPanel)
$('btn-salir').addEventListener('click', () => {
  guardado.borrarSesion()
  filas = []
  entrar()
})

// ---------- Arranque ----------
let reloj
function abrirPanel() {
  mostrar('panel')
  pintar()
  cargar()
  clearInterval(reloj)
  reloj = setInterval(() => {
    if (!$('vista-panel').hidden && document.visibilityState === 'visible') cargar()
    else pintarEstado()
  }, CADA_MS)
}

if (guardado.get().sesion) abrirPanel()
else entrar()
