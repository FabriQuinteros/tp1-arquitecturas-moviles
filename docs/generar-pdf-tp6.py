"""docs/tp6.md -> docs/TP6-Gimenez-Quinteros-Tarjetazo.pdf, imprimiendo HTML con Edge sin ventana."""
import io, pathlib, re, subprocess, tempfile, time
import markdown

DOCS = pathlib.Path(__file__).resolve().parent
EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

md = io.open(DOCS / "tp6.md", encoding="utf-8").read()
# Las líneas de la portada ("**Cátedra:** ...") van una debajo de la otra; solo antes del primer "---".
portada, resto = md.split("\n---\n", 1)
md = re.sub(r"^(\*\*[^\n]+)$", r"\1  ", portada, flags=re.M) + "\n---\n" + resto
cuerpo = markdown.markdown(md, extensions=["tables", "fenced_code", "md_in_html"])

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
pre { background: #f2f2f2; padding: 6px 8px; font-size: 8.5pt; white-space: pre-wrap; page-break-inside: avoid; }
code { font-family: Consolas, monospace; font-size: 9pt; background: #f2f2f2; padding: 0 3px; }
img { width: 30%; margin: 0 1% 1em 0; border: 1px solid #ccc; vertical-align: top; }
img[src$="logo-utn-frsf.svg"] { width: 5cm; border: 0; }
img[src*="5-consola"], img[src*="6-aviso"] { width: 62%; display: block; margin: .3em 0 1em; }
img[src*="8-consola"] { width: 100%; display: block; margin: .3em 0 1em; }
img[src*="7-campania"] { width: 33%; display: block; margin: .3em 0 1em; }
.pagina-diagrama { page-break-before: always; page-break-after: always; }
.pagina-diagrama h2 { margin-top: 0; }
img[src$="arquitectura-tp6.svg"] { width: auto; max-width: 100%; max-height: 13.5cm; display: block; margin: 0 auto; border: 0; page-break-inside: avoid; }
"""
html = f'<!doctype html><html lang="es"><head><meta charset="utf-8"><style>{CSS}</style></head><body>{cuerpo}</body></html>'
def imprimir(html, salida):
    tmp = DOCS / "_tp6.html"
    tmp.write_text(html, encoding="utf-8")
    try:
        subprocess.run([EDGE, "--headless=new", "--disable-gpu", "--no-pdf-header-footer", f"--user-data-dir={tempfile.mkdtemp(prefix='edge-pdf-')}",
                        f"--print-to-pdf={salida}", tmp.as_uri()], check=True, timeout=120, capture_output=True)
        tam = -1  # Edge devuelve el control antes de terminar de escribir: se espera a que el PDF deje de crecer
        for _ in range(60):
            time.sleep(1)
            if pathlib.Path(salida).exists() and pathlib.Path(salida).stat().st_size == tam > 0:
                break
            tam = pathlib.Path(salida).stat().st_size if pathlib.Path(salida).exists() else -1
    finally:
        tmp.unlink(missing_ok=True)


salida = DOCS / "TP6-Gimenez-Quinteros-Tarjetazo.pdf"
imprimir(html, salida)

# El diagrama va en una página apaisada: se imprime aparte y reemplaza su página en el documento.
import pymupdf
m = re.search(r'<div class="pagina-diagrama">.*?</div>', cuerpo, flags=re.S)
diag = DOCS / "_diagrama.pdf"
imprimir(f'<!doctype html><html lang="es"><head><meta charset="utf-8"><style>{CSS}@page {{ size: A4 landscape; margin: 1.2cm; }}</style></head><body>{m.group(0)}</body></html>', diag)
doc, d = pymupdf.open(salida), pymupdf.open(diag)
i = next(n for n, pg in enumerate(doc) if "6. Arquitectura actualizada" in pg.get_text())
doc.delete_page(i)
doc.insert_pdf(d, start_at=i)
doc.save(str(salida) + ".tmp")
doc.close(); d.close()
diag.unlink()
pathlib.Path(str(salida) + ".tmp").replace(salida)
print(salida, salida.stat().st_size, "bytes")
