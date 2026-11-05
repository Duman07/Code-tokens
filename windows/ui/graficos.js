// Gráficos SVG: los mismos que dibuja el panel de Claude Code en escritorio y móvil.

export const COLOR = {
  ok: '#3fb950',
  medio: '#d29922',
  alto: '#f85149',
  entrada: '#58a6ff',
  salida: '#bc8cff',
  cacheLeida: '#3fb950',
  cacheEscrita: '#d29922',
  vacio: '#30363d',
  tenue: '#8b949e',
}

export const PARTES = [
  { campo: 'entrada', nombre: 'Entrada', color: COLOR.entrada },
  { campo: 'salida', nombre: 'Salida', color: COLOR.salida },
  { campo: 'cacheLeida', nombre: 'Caché leída', color: COLOR.cacheLeida },
  { campo: 'cacheEscrita', nombre: 'Caché escrita', color: COLOR.cacheEscrita },
]

export const NOMBRES_LIMITE = { five_hour: '5 horas', seven_day: '7 días', spend_limit: 'Gasto' }

const FUENTE = 'font-family="system-ui,-apple-system,Segoe UI,sans-serif"'

export function numero(n) {
  n = Number(n) || 0
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(2).replace('.', ',')} M`
  return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, '.')
}

export function corto(n) {
  n = Number(n) || 0
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1).replace('.', ',')} M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1).replace('.', ',')} k`
  return String(Math.round(n))
}

export function colorDe(p) {
  return p >= 85 ? COLOR.alto : p >= 60 ? COLOR.medio : COLOR.ok
}

export function repartir(valores, ancho) {
  const suma = valores.reduce((a, b) => a + b, 0)
  if (suma <= 0) return valores.map(() => 0)
  const exactas = valores.map(v => (v / suma) * ancho)
  const casillas = exactas.map(Math.floor)
  let faltan = ancho - casillas.reduce((a, b) => a + b, 0)
  const orden = exactas.map((v, i) => [v - Math.floor(v), i]).sort((a, b) => b[0] - a[0])
  for (const [, i] of orden) {
    if (faltan-- <= 0) break
    casillas[i]++
  }
  return casillas
}

function esc(s) {
  return String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c])
}

export function hora(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' })
}

/** Anillo de contexto + barra de reparto por tipo de token. */
export function svgContexto(u) {
  const t = u.totales || {}
  const pct = u.porcentaje ?? 0
  const r = 46
  const c = 2 * Math.PI * r
  const lleno = (Math.min(100, pct) / 100) * c
  const total = PARTES.reduce((a, p) => a + (t[p.campo] || 0), 0)
  const anchos = repartir(PARTES.map(p => t[p.campo] || 0), 300)
  let x = 10
  const segmentos = PARTES.map((p, i) => {
    const s = `<rect x="${x}" y="134" width="${anchos[i]}" height="14" fill="${p.color}"/>`
    x += anchos[i]
    return s
  }).join('')
  const leyenda = PARTES.map((p, i) => {
    const lx = 10 + (i % 2) * 155
    const ly = 172 + Math.floor(i / 2) * 20
    return `<rect x="${lx}" y="${ly - 9}" width="10" height="10" rx="2" fill="${p.color}"/><text x="${lx + 16}" y="${ly}" font-size="12" fill="${COLOR.tenue}">${p.nombre} ${corto(t[p.campo])}</text>`
  }).join('')
  const costo = u.costo_usd != null ? `<text x="132" y="104" font-size="12" fill="${COLOR.tenue}">Costo: US$ ${Number(u.costo_usd).toFixed(2)}</text>` : ''

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 210" ${FUENTE}>
<circle cx="66" cy="62" r="${r}" fill="none" stroke="${COLOR.vacio}" stroke-width="12"/>
<circle cx="66" cy="62" r="${r}" fill="none" stroke="${colorDe(pct)}" stroke-width="12" stroke-linecap="round"
  stroke-dasharray="${lleno.toFixed(1)} ${c.toFixed(1)}" transform="rotate(-90 66 62)"/>
<text x="66" y="66" text-anchor="middle" font-size="22" font-weight="700" fill="${colorDe(pct)}">${pct}%</text>
<text x="66" y="84" text-anchor="middle" font-size="11" fill="${COLOR.tenue}">contexto</text>
<text x="132" y="44" font-size="12" fill="${COLOR.tenue}">Ventana</text>
<text x="132" y="62" font-size="15" font-weight="600" fill="${colorDe(pct)}">${corto(u.contexto)} / ${corto(u.ventana)}</text>
<text x="132" y="86" font-size="12" fill="${COLOR.tenue}">Sesión: ${corto(total)} tokens</text>
${costo}
<rect x="10" y="134" width="300" height="14" rx="7" fill="${COLOR.vacio}"/>
<clipPath id="r"><rect x="10" y="134" width="300" height="14" rx="7"/></clipPath>
<g clip-path="url(#r)">${segmentos}</g>
${leyenda}
</svg>`
}

/** Columnas apiladas por turno: caché, entrada, salida. */
export function svgHistorial(lista) {
  const alto = 80
  const n = Math.max(lista.length, 1)
  const paso = 300 / n
  const ancho = Math.max(2, paso - 2)
  const tot = t => (t.entrada || 0) + (t.salida || 0) + (t.cache || 0)
  const max = Math.max(1, ...lista.map(tot))
  const k = alto / max
  const base = 10 + alto
  const barras = lista
    .map((t, i) => {
      const x = (10 + i * paso).toFixed(1)
      const hc = (t.cache || 0) * k
      const he = (t.entrada || 0) * k
      const hs = (t.salida || 0) * k
      return (
        `<rect x="${x}" y="${(base - hc).toFixed(1)}" width="${ancho.toFixed(1)}" height="${hc.toFixed(1)}" fill="${COLOR.cacheLeida}" opacity="0.55"/>` +
        `<rect x="${x}" y="${(base - hc - he).toFixed(1)}" width="${ancho.toFixed(1)}" height="${he.toFixed(1)}" fill="${COLOR.entrada}"/>` +
        `<rect x="${x}" y="${(base - hc - he - hs).toFixed(1)}" width="${ancho.toFixed(1)}" height="${hs.toFixed(1)}" fill="${COLOR.salida}"/>`
      )
    })
    .join('')
  const ultimo = lista.length ? tot(lista[lista.length - 1]) : 0
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 110" ${FUENTE}>
<line x1="10" y1="90.5" x2="310" y2="90.5" stroke="${COLOR.vacio}"/>
${barras}
<text x="10" y="106" font-size="11" fill="${COLOR.tenue}">${lista.length} turnos · máx ${corto(max)}</text>
<text x="310" y="106" text-anchor="end" font-size="11" fill="${COLOR.tenue}">último ${corto(ultimo)}</text>
</svg>`
}

/** Barras de los límites de uso de la cuenta. */
export function svgLimites(limites) {
  const filas = limites
    .map((l, i) => {
      const y = 10 + i * 34
      const w = (Math.min(100, l.usado) / 100) * 300
      const reinicia = hora(l.reinicia)
      const nombre = esc(NOMBRES_LIMITE[l.tipo] ?? l.tipo)
      return `<text x="10" y="${y + 10}" font-size="12" fill="${COLOR.tenue}">${nombre}${reinicia ? ` · reinicia ${reinicia}` : ''}</text>
<text x="310" y="${y + 10}" text-anchor="end" font-size="12" font-weight="600" fill="${colorDe(l.usado)}">${l.usado}%</text>
<rect x="10" y="${y + 16}" width="300" height="8" rx="4" fill="${COLOR.vacio}"/>
<rect x="10" y="${y + 16}" width="${w.toFixed(1)}" height="8" rx="4" fill="${colorDe(l.usado)}"/>`
    })
    .join('')
  const alto = 10 + limites.length * 34
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 ${alto}" ${FUENTE}>${filas}</svg>`
}

export { esc }
