#!/usr/bin/env python3
"""
Pull UP-TO-DATE course material from the official subject sites into
material/current/<SUBJECT>/<type>/. Kept SEPARATE from the archive (material/archive/).

Sources (Alex's BScIA year-1 subjects):
  PA   = Google Sites fii-ad (Drive PDFs)               -> proiectarea algoritmilor AI1205
  PS   = edu.info.uaic.ro/probabilitati-si-statistica    -> AI1204
  ALO  = edu.info.uaic.ro/algebra-liniara                -> AI1203 (Linear Algebra & Optimisation)
  SORC = edu.info.uaic.ro/operating-systems-and-computer-networks (+ RC on profs.info) -> AI1202
  POO  = gdt050579.github.io/poo_course_fii              -> AI1201

NON-DESTRUCTIVE: only downloads into material/current/. Writes out/pull-log.csv.
SO half of SORC is 401-walled and RC labs are 404 (dead) — logged, not fatal.
"""
import csv, os, subprocess, sys
from urllib.parse import quote

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, os.pardir, os.pardir))
DST  = os.path.join(REPO, "material", "current")
OUT  = os.path.join(HERE, "out")
os.makedirs(OUT, exist_ok=True)

def gd(fid):  # google-drive direct-download url
    return f"https://drive.google.com/uc?export=download&confirm=t&id={fid}"

JOBS = []  # (subject, type, filename, url)
def add(subj, typ, fn, url): JOBS.append((subj, typ, fn, url))

# ---------------- PA (Google Drive) ----------------
add("PA","fisa","PA_fisa_IA.pdf",        gd("1QI9BrEdUPqlWNlavTLbVnXm8PV5JagFe"))
add("PA","fisa","PA_fisa_ro.pdf",        gd("1BCAiGAps2AVS4MNX37BxMlQhyLcSzgfE"))
add("PA","fisa","PA_descriere_en.pdf",   gd("1EYZ7YC0YfsPFNZPt_UpUe3giC6pRQoio"))
for fn,fid in [
  ("L05_KMP_ro.pdf","1M9dL9n0Dsa1EhRzVyOup7wK4SKFGqvJa"),
  ("L05_KMP_en.pdf","1Hm9PfDjLzuyGdUPjM19y28y7Vm8ykrcj"),
  ("L06_RabinKarp_ro.pdf","1fMNQhODNQd5eTSXfTNoEp7y8E-W6L4Kd"),
  ("L06_RabinKarp_en.pdf","1Ss-lsuqnw2lC4jvD0qDNWt27EkOD-HPQ"),
  ("L06_BoyerMoore_ro.pdf","1UBQixn41Opql6a-zlRPtWD86UIYVTwbU"),
  ("L06_BoyerMoore_en.pdf","1vkf_eZMAugAkyrkyRFModyNY4psNMqFQ"),
  ("L09-10_NP-complete.pdf","1g5evQcWersz4JT_SKV843ynyGdux3OGP"),
  ("L11_Backtracking_BnB.pdf","1mc2groqsiIoQobRvNYnpzRy-hzyygta9"),
  ("L12_DP.pdf","1kQaY5BYUNmX90A0lxt7fzzjglU1LZ239"),
  ("L13_Advanced_DP.pdf","1V7OR74ZvQJ3t1VXMqkSypBjPZRIxIWbU"),
  ("L14_Greedy.pdf","1V8XaVjqyksU8ow1LMCX9LzNxUWluXCSO"),
]: add("PA","curs",fn,gd(fid))
for wk,en,ro in [
  ("01","1qyQBtyVRBTVr3qrRxRTJjCDOnxViopmj","1LYwxg_e8lHfHw477I9tTLjNd2pI_uCou"),
  ("02","1eKIwuqBecjZdx-FAofMnx4oQSUG5GHF6","1b_hhM-dtLYZ_u1tL8qietmwylULobc_h"),
  ("03","1SdSDFINkJ-j-HxZKukfuWoX0M8JNYg0j","1v2lSCkUJ8w6GffaKrOsNlnlVZDK3k3kj"),
  ("04","1xTZn4ko8BhUUBaQJoPtofMBnsWbsTbAz","1ugMOAP7Vgydtc_PmZDcJNgNIW6G1wD0z"),
  ("05","1MiTSMs4FFQkJn-VaXt3u0WLeYg67U5IB","1OlCXT5nwpOC1MdB9I8Nl9Ofl-cHGJXFU"),
  ("06","1D2ntiU3l-RpTKHQpk7_iGlCNXeq0dhEI","1FDp4xDmy9Y4tsaaA7C1YBUg2CSD7cG4A"),
  ("11","1Y3LGxUZzEkaAMaO-6jQ__oH7vPSWNYrU","1STAtU_rNhEScopgyqbO6-ktvSfJBaR-t"),
  ("12","1gR02fvSIEN--B9dEy0OYA_7Lg5Bo9k4J","10fAKxSSA0QNRTrwZT4BPaJ1TFpe6gNXC"),
  ("13","1PDNNVa5pGKx5PsvWRuSiSG8q7R7svCLz","1Mhzbd4ok5RLMeg0fAK1OGZpjR4vN8U90"),
  ("14","14uQ7HjrgHj6skYEcJZFoFvEmdEHrmlS_","15Up8NhGqiNtkGgsxbJnLiSDEIpM1QVxd"),
]:
    add("PA","seminar",f"S{wk}_en.pdf",gd(en))
    add("PA","seminar",f"S{wk}_ro.pdf",gd(ro))

# ---------------- PS (edu.info direct) ----------------
PSB="https://edu.info.uaic.ro/probabilitati-si-statistica/files/"
add("PS","fisa","PS_fisa_IA.pdf",   PSB+quote("IA_sem_2_Fisa disciplinei_Probabilitati si statistica.pdf"))
add("PS","fisa","PS_fisa_InfoRo.pdf",PSB+quote("InfoRo_sem_2_Fisa disciplinei_Probabilitati si Statistica.pdf"))
add("PS","fisa","PS_fisa_InfoEn.pdf",PSB+quote("InfoEng_sem_2_Fisa disciplinei_Probabilitati si Statistica.pdf"))
for i in range(1,14):
    add("PS","curs",f"ps{i}.pdf",   PSB+f"ps{i}.pdf")
    add("PS","curs",f"ps{i}_en.pdf",PSB+f"ps{i}_en.pdf")
for i in range(1,7):
    add("PS","laborator",f"lab_stat{i}.pdf",   PSB+f"lab_stat{i}.pdf")
    add("PS","laborator",f"lab_stat{i}_en.pdf",PSB+f"lab_stat{i}_en.pdf")
for t in ["A","B","C","D"]:
    add("PS","tema",f"Tema_{t}.pdf",   PSB+f"Tema_{t}.pdf")
    add("PS","tema",f"Tema_{t}_en.pdf",PSB+f"Tema_{t}_en.pdf")
for sub,fn in [("lab4","unemployment.csv"),("lab4","sample1.txt"),("lab4","sample2.txt"),
               ("lab4","life_expect.csv"),("lab4","unemploy2012.csv"),
               ("lab5","history.txt"),("lab6","history.txt"),("lab6","program.txt")]:
    add("PS","data",f"{sub}_{fn}",PSB+f"data/{sub}/{fn}")

# ---------------- ALO (edu.info direct, spaces) ----------------
ALOB="https://edu.info.uaic.ro/algebra-liniara/"
for i in range(1,14):
    add("ALO","curs",f"ALO_curs_{i:02d}.pdf",ALOB+quote(f"curs/ALO curs {i:02d}.pdf"))
for i in range(1,8):
    add("ALO","seminar",f"exercitii_{i}.pdf",ALOB+quote(f"seminar/exercitii {i}.pdf"))
for i in range(1,6):
    add("ALO","laborator",f"Tema_{i}.pdf",ALOB+quote(f"lab/Tema {i}.pdf"))

# ---------------- SORC (edu.info + profs.info RC) ----------------
SOB="https://edu.info.uaic.ro/operating-systems-and-computer-networks/"
add("SORC","fisa","Fisa-SO+RC_ro.pdf",        SOB+"Fisa-SO+RC_ro.pdf")
add("SORC","curs","SO_Linux-intro_handout_ro.pdf",SOB+"Linux-intro_handout-ro.pdf")
CAL="https://profs.info.uaic.ro/georgiana.calancea/"
for fn,u in [
  ("RC_Curs1_procese_la_retele_RO.pdf","Curs1_De_la_procese_la_retele_RO.pptx.pdf"),
  ("RC_Curs2_Programare_in_retea_1.pdf","Curs2_Programare_in_retea_1.pptx.pdf"),
  ("RC_Curs3_Programare_in_retea_2.pdf","Curs3_Programare_in_retea_2.pptx.pdf"),
  ("RC_Curs4_Programare_in_retea_3.pdf","Curs4_Programare_in_retea_3.pptx.pdf"),
  ("RC_Curs5_DNS_NivelulAplicatie.pdf","Curs5_DNS_NivelulAplicatie.pptx.pdf"),
  ("RC_Curs6_HTTP_WebSockets_MCP.pdf","Curs6_HTTP_WebSockets_MCP.pptx.pdf"),
]: add("SORC","curs",fn,CAL+u)

# ---------------- POO (github pages) ----------------
POB="https://gdt050579.github.io/poo_course_fii/"
POO_C={1:"Introduction",2:"Cpp_Language_Specifiers",3:"Creating_an_Object",4:"Operators",
       5:"Inheritance",6:"Templates",7:"STL_1",8:"STL_2",9:"Advances_beyond_Cpp98",
       10:"Lambda_Expressions",11:"Exceptions",12:"Design_Patterns"}
for i,name in POO_C.items():
    add("POO","curs",f"Course-{i:02d}_{name}.pdf",POB+f"courses/Course-{i}.pdf")
add("POO","curs","cpp_to_rust.pdf",POB+"courses/cpp_to_rust.pdf")
LAB_EXTRA={1,2,3,5,6,7}
for i in range(1,13):
    add("POO","laborator",f"lab{i:02d}.html",POB+f"labs/lab{i:02d}.html")
    if i in LAB_EXTRA:
        add("POO","laborator",f"lab{i:02d}_extra.html",POB+f"labs/lab{i:02d}_extra.html")

# ---------------- download ----------------
def looks_ok(path, fn):
    try:
        with open(path,"rb") as f: head=f.read(16)
    except OSError: return False
    if fn.endswith(".pdf"): return head[:4]==b"%PDF"
    if fn.endswith(".html"): return b"<" in head
    return os.path.getsize(path)>0

rows=[]; ok=0; bad=0
for subj,typ,fn,url in JOBS:
    d=os.path.join(DST,subj,typ); os.makedirs(d,exist_ok=True)
    out=os.path.join(d,fn)
    r=subprocess.run(["curl","-sL","--max-time","120","-o",out,url],capture_output=True)
    good = r.returncode==0 and os.path.exists(out) and looks_ok(out,fn)
    status="OK" if good else "FAIL"
    size=os.path.getsize(out) if os.path.exists(out) else 0
    if good: ok+=1
    else:
        bad+=1
        if os.path.exists(out) and not looks_ok(out,fn): status="NOT_"+fn.split('.')[-1].upper()
    rows.append([subj,typ,fn,status,size,url])
    print(f"  [{status:>8}] {subj}/{typ}/{fn}  ({size}B)",file=sys.stderr)

with open(os.path.join(OUT,"pull-log.csv"),"w",encoding="utf-8",newline="") as f:
    w=csv.writer(f); w.writerow(["subject","type","filename","status","bytes","url"]); w.writerows(rows)

print(f"\nDONE  jobs={len(JOBS)} ok={ok} bad={bad}",file=sys.stderr)
from collections import Counter
c=Counter((s,st) for s,_,_,st,_,_ in [(r[0],r[1],r[2],r[3],r[4],r[5]) for r in rows])
