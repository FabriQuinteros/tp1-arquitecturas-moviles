"""docs/tp2.md -> docs/TP2-Gimenez-Quinteros-Tarjetazo.pdf, imprimiendo HTML con Edge sin ventana."""
import io, pathlib, re, subprocess
import markdown

DOCS = pathlib.Path(r"E:\tp1-arquitecturas-moviles\docs")
EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

md = io.open(DOCS / "tp2.md", encoding="utf-8").read()
# Las líneas de la portada ("**Cátedra:** ...") van una debajo de la otra; solo antes del primer "---".
portada, resto = md.split("\n---\n", 1)
md = re.sub(r"^(\*\*[^\n]+)$", r"\1  ", portada, flags=re.M) + "\n---\n" + resto
cuerpo = markdown.markdown(md, extensions=["tables"])

CSS = """
@page { size: A4; margin: 2cm; }
body { font-family: "Segoe UI", Arial, sans-serif; font-size: 10.5pt; line-height: 1.45; color: #111; }
h1 { font-size: 18pt; margin: 0 0 .6em; }
h2 { font-size: 13.5pt; margin: 1.5em 0 .5em; padding-bottom: .15em; border-bottom: 1px solid #999; page-break-after: avoid; }
h3 { font-size: 11.5pt; margin: 1.2em 0 .4em; page-break-after: avoid; }
hr { border: 0; border-top: 1px solid #999; }
a { color: #1a4f8b; }
table { width: 100%; border-collapse: collapse; font-size: 9.5pt; margin: .5em 0 1em; }
th, td { border: 1px solid #bbb; padding: 4px 6px; vertical-align: top; text-align: left; }
td { height: 1.6em; }
tr { page-break-inside: avoid; }
th { background: #eee; }
code { font-family: Consolas, monospace; font-size: 9pt; background: #f2f2f2; padding: 0 3px; }
img { width: 30%; margin: 0 1% 1em 0; border: 1px solid #ccc; vertical-align: top; }
img[src$="logo-utn-frsf.svg"] { width: 5cm; border: 0; }
img[src$="arquitectura-tp2.svg"] { width: 100%; border: 0; page-break-inside: avoid; }
"""
html = f'<!doctype html><html lang="es"><head><meta charset="utf-8"><style>{CSS}</style></head><body>{cuerpo}</body></html>'
tmp = DOCS / "_tp2.html"
tmp.write_text(html, encoding="utf-8")
salida = DOCS / "TP2-Gimenez-Quinteros-Tarjetazo.pdf"
try:
    subprocess.run([EDGE, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                    f"--print-to-pdf={salida}", tmp.as_uri()], check=True, timeout=120, capture_output=True)
finally:
    tmp.unlink(missing_ok=True)
print(salida, salida.stat().st_size, "bytes")
