#!/usr/bin/env python3
# Build the RC complete study guide HTML from the rc-fill-gaps workflow output JSON.
import json, html, sys, pathlib

SRC = r"C:\Users\User\AppData\Local\Temp\claude\C--Users-User-jarvis-kotlin\d5dcc214-11c0-4c93-b6e7-a1378335bc5b\tasks\wepwy46v9.output"
OUT = r"C:\Users\User\Desktop\cursuri RC\rc-ghid-complet.html"

data = json.loads(pathlib.Path(SRC).read_text(encoding="utf-8"))
sheets = data["result"] if isinstance(data, dict) and "result" in data else data

# color accents cycled per course
ACCENTS = ["#7dd87d", "#7dd3fc", "#ffd400", "#ff9e64", "#ff9ec4", "#b48ead"]

def esc(s):
    return html.escape(str(s))

nav, body = [], []
for i, sheet in enumerate(sheets):
    course = sheet.get("course", f"Curs {i+1}")
    sid = f"c{i}"
    acc = ACCENTS[i % len(ACCENTS)]
    nav.append(f'<a href="#{sid}" style="border-color:{acc};color:{acc}">{esc(course)}</a>')
    secs = []
    for sec in sheet.get("sections", []):
        pts = "".join(f"<li>{esc(p)}</li>" for p in sec.get("points", []))
        secs.append(
            f'<div class="topic">'
            f'<h3>{esc(sec.get("topic",""))}</h3>'
            f'<p class="plain">{esc(sec.get("plain",""))}</p>'
            f'<ul>{pts}</ul>'
            f'</div>'
        )
    body.append(
        f'<section id="{sid}" class="course" style="--acc:{acc}">'
        f'<div class="chead"><h2>{esc(course)}</h2>'
        f'<span class="count">{len(sheet.get("sections",[]))} teme</span></div>'
        f'<div class="cbody">{"".join(secs)}</div>'
        f'</section>'
    )

HTMLDOC = f"""<!DOCTYPE html>
<html lang="ro">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>RC — Ghid complet (tot ce mai era neatins)</title>
<style>
  :root{{--bg:#0e0e10;--panel:#16161a;--panel2:#1d1d22;--ink:#f4f4f2;--dim:#9a9aa2;--line:#26262c}}
  *{{box-sizing:border-box}}
  body{{margin:0;background:var(--bg);color:var(--ink);font-family:"Segoe UI",system-ui,sans-serif;line-height:1.55;padding:0 0 80px}}
  header{{padding:26px 22px 14px;max-width:1000px;margin:0 auto}}
  h1{{font-size:24px;margin:0 0 4px;border-left:6px solid #ffd400;padding-left:13px}}
  .sub{{color:var(--dim);font-size:13.5px;padding-left:19px;margin:2px 0 0}}
  nav{{position:sticky;top:0;z-index:5;background:rgba(14,14,16,.95);backdrop-filter:blur(6px);
    border-bottom:1px solid var(--line);padding:10px 22px;display:flex;gap:8px;flex-wrap:wrap}}
  nav a{{font-size:12px;text-decoration:none;border:1px solid;border-radius:7px;padding:5px 10px;white-space:nowrap}}
  .wrap{{max-width:1000px;margin:0 auto;padding:0 22px}}
  .course{{margin-top:26px;border:1px solid var(--line);border-radius:12px;overflow:hidden}}
  .chead{{display:flex;justify-content:space-between;align-items:center;padding:13px 18px;
    background:var(--panel2);border-bottom:2px solid var(--acc)}}
  .chead h2{{margin:0;font-size:18px;color:var(--acc)}}
  .count{{font-size:12px;color:var(--dim)}}
  .cbody{{padding:6px 18px 16px}}
  .topic{{padding:14px 0;border-bottom:1px solid var(--line)}}
  .topic:last-child{{border-bottom:none}}
  .topic h3{{margin:0 0 6px;font-size:15.5px;color:var(--ink)}}
  .topic h3::before{{content:"▸ ";color:var(--acc)}}
  .plain{{margin:0 0 9px;font-size:14.5px;color:#e6e6e2}}
  .topic ul{{margin:0;padding-left:18px}}
  .topic li{{font-size:13.5px;color:#cfcfca;margin-bottom:4px}}
  .topic li::marker{{color:var(--acc)}}
  footer{{max-width:1000px;margin:30px auto 0;padding:14px 22px;color:var(--dim);font-size:12.5px}}
</style>
</head>
<body>
<header>
  <h1>RC — Ghid complet</h1>
  <p class="sub">Tot ce era neatins din cele 6 cursuri, succint. Generat din slide-urile tale. Sari la curs din bara de sus.</p>
</header>
<nav>{''.join(nav)}</nav>
<div class="wrap">
{''.join(body)}
</div>
<footer>Generat automat din PDF-urile cursului (Curs1–6). Pentru cod: vezi <b>rc-skeleton-intreg</b> + <b>rc-cheatsheet-restul</b>.</footer>
</body>
</html>
"""

pathlib.Path(OUT).write_text(HTMLDOC, encoding="utf-8")
total = sum(len(s.get("sections", [])) for s in sheets)
print(f"OK: {len(sheets)} courses, {total} topics -> {OUT}")
