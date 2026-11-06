#!/usr/bin/env python3
"""Genera docs/*.html (política de privacidad, términos y eliminar cuenta) a partir de Legal.kt,
para publicarlos con GitHub Pages. Así el texto de la app y el de la web son siempre el mismo.

    python3 tools/exportar_legal.py
"""
import html, re, sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
FUENTE = RAIZ / "android/app/src/main/java/com/contadortokens/app/Legal.kt"
SALIDA = RAIZ / "docs"
src = FUENTE.read_text(encoding="utf-8")


def constante(nombre):
    m = re.search(r'const val %s = "([^"]*)"' % nombre, src)
    if not m:
        sys.exit(f"No encuentro {nombre} en Legal.kt")
    return m.group(1)


CONTACTO, ACTUALIZADO = constante("CONTACTO"), constante("ACTUALIZADO")


def literal(t):
    """Contenido de un literal de Kotlin con comillas, sin las comillas."""
    return t.encode().decode("unicode_escape").encode("latin-1").decode("utf-8")


def secciones(nombre):
    m = re.search(r"val %s = listOf\((.*?)\n\)\n" % nombre, src, re.S)
    if not m:
        sys.exit(f"No encuentro la lista {nombre}")
    cuerpo = m.group(1)
    salida = []
    for s in re.finditer(r"Seccion\(\s*((?:\"(?:[^\"\\]|\\.)*\"\s*\+?\s*)+),\s*((?:\"(?:[^\"\\]|\\.)*\"\s*\+?\s*)+),?\s*\)", cuerpo, re.S):
        partes = [
            re.findall(r'"((?:[^"\\]|\\.)*)"', bloque) for bloque in (s.group(1), s.group(2))
        ]
        titulo, texto = ("".join(literal(x) for x in p) for p in partes)
        salida.append((titulo.replace("$CONTACTO", CONTACTO).replace("$ACTUALIZADO", ACTUALIZADO),
                       texto.replace("$CONTACTO", CONTACTO).replace("$ACTUALIZADO", ACTUALIZADO)))
    if not salida:
        sys.exit(f"{nombre}: no se pudo leer ninguna sección")
    return salida


def parrafos(texto):
    """Cada línea que empieza por • es un elemento de lista; el resto, párrafos."""
    out, lista = [], []
    for linea in texto.split("\n"):
        if linea.startswith("•"):
            lista.append(f"<li>{enlazar(linea[1:].strip())}</li>")
            continue
        if lista:
            out.append("<ul>" + "".join(lista) + "</ul>"); lista = []
        if linea.strip():
            out.append(f"<p>{enlazar(linea.strip())}</p>")
    if lista:
        out.append("<ul>" + "".join(lista) + "</ul>")
    return "\n".join(out)


def enlazar(t):
    t = html.escape(t)
    return re.sub(r"(https?://[^\s)<]+[^\s).,<])", r'<a href="\1">\1</a>', t)


PLANTILLA = """<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{titulo} · Contador de tokens</title>
<style>
  :root {{ color-scheme: light dark; }}
  body {{ font: 16px/1.6 system-ui, sans-serif; max-width: 42rem; margin: 0 auto; padding: 2rem 1.25rem 4rem; }}
  h1 {{ font-size: 1.8rem; }} h2 {{ font-size: 1.15rem; margin-top: 2rem; }}
  nav a {{ margin-right: 1rem; }} .fecha {{ opacity: .65; font-size: .9rem; }}
  footer {{ margin-top: 3rem; opacity: .65; font-size: .85rem; }}
</style>
</head>
<body>
<nav><a href="index.html">Inicio</a><a href="privacidad.html">Privacidad</a><a href="terminos.html">Términos</a><a href="eliminar-cuenta.html">Eliminar cuenta</a></nav>
<h1>{titulo}</h1>
{cuerpo}
<footer>Contador de tokens es un proyecto independiente, no afiliado a Anthropic. Contacto: <a href="mailto:{contacto}">{contacto}</a></footer>
</body>
</html>
"""


def pagina(archivo, titulo, cuerpo):
    (SALIDA / archivo).write_text(
        PLANTILLA.format(titulo=html.escape(titulo), cuerpo=cuerpo, contacto=html.escape(CONTACTO)), encoding="utf-8")


def documento(lista):
    return f'<p class="fecha">Última actualización: {html.escape(ACTUALIZADO)}</p>\n' + "\n".join(
        f"<h2>{html.escape(t)}</h2>\n{parrafos(x)}" for t, x in lista)


SALIDA.mkdir(exist_ok=True)
pagina("privacidad.html", "Política de privacidad", documento(secciones("POLITICA_PRIVACIDAD")))
pagina("terminos.html", "Términos y condiciones", documento(secciones("TERMINOS")))
# Google Play pide una URL pública donde se explique cómo borrar la cuenta y los datos.
pagina("eliminar-cuenta.html", "Eliminar tu cuenta y tus datos", f"""
<h2>Desde la app</h2>
<ol>
<li>Abre <strong>Contador de tokens</strong> e inicia sesión.</li>
<li>Ve a <strong>Ajustes → Cuenta → Eliminar cuenta y datos</strong>.</li>
<li>Confirma. Se borran de inmediato tu cuenta, tus llaves de dispositivo y todo tu consumo.</li>
</ol>
<h2>Sin acceso a la app</h2>
<p>Escribe a <a href="mailto:{html.escape(CONTACTO)}">{html.escape(CONTACTO)}</a> desde el correo con el que te registraste y eliminaremos tu cuenta y tus datos.</p>
<h2>Qué se borra</h2>
<ul><li>Tu cuenta y tu correo.</li><li>Tus cuentas conectadas y llaves de dispositivo.</li><li>Todo el consumo y las sesiones guardadas.</li></ul>
<p>No conservamos copias una vez eliminada la cuenta.</p>
""")
pagina("index.html", "Contador de tokens", """
<p>Mira desde tu teléfono el contexto, los tokens y los límites de uso de tu Claude Code.</p>
<ul>
<li><a href="privacidad.html">Política de privacidad</a></li>
<li><a href="terminos.html">Términos y condiciones</a></li>
<li><a href="eliminar-cuenta.html">Eliminar tu cuenta y tus datos</a></li>
</ul>
""")

if "tudominio.com" in CONTACTO:
    print(f"AVISO: el contacto sigue siendo el de ejemplo ({CONTACTO}). Cámbialo en Legal.kt y vuelve a ejecutar esto antes de publicar.")
print("Generado en", SALIDA)
