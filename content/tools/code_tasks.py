"""Musterlösungen und versteckte Tests für die CODE-Aufgaben (Phase 4).

Die erwarteten Ausgaben werden nicht von Hand geschrieben, sondern durch Ausführen der
Musterlösung erzeugt:  python content/tools/code_tasks.py
"""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

TASKS = {
    "algorithmen/tasks/05-a5.json": {
        "solution": """def binaere_suche(liste, ziel):
    links = 0
    rechts = len(liste) - 1

    while links <= rechts:
        mitte = (links + rechts) // 2
        if liste[mitte] == ziel:
            return mitte
        if liste[mitte] < ziel:
            links = mitte + 1
        else:
            rechts = mitte - 1

    return -1

zahlen = [2, 5, 8, 12, 16, 23, 38, 42, 55, 72]
print(binaere_suche(zahlen, 23))  # → 5
print(binaere_suche(zahlen, 10))  # → -1
""",
        "hidden": [
            "print(binaere_suche([1, 3, 5, 7, 9, 11], 1))\nprint(binaere_suche([1, 3, 5, 7, 9, 11], 11))\nprint(binaere_suche([1, 3, 5, 7, 9, 11], 4))",
            "print(binaere_suche([], 3))\nprint(binaere_suche([42], 42))\nprint(binaere_suche(list(range(0, 1000, 3)), 999))",
        ],
    },
    "algorithmen/tasks/10-a10.json": {
        "solution": """def bubble_sort(liste):
    n = len(liste)
    for i in range(n - 1):          # n-1 Runden reichen
        for j in range(0, n - i - 1):  # Innere Schleife: Paare vergleichen
            if liste[j] > liste[j + 1]:
                liste[j], liste[j + 1] = liste[j + 1], liste[j]
    return liste

zahlen = [64, 34, 25, 12, 22, 11, 90]
print(bubble_sort(zahlen))
""",
        "hidden": [
            "print(bubble_sort([5, 1, 4, 2, 8]))\nprint(bubble_sort([3, 3, 1]))",
            "print(bubble_sort([]))\nprint(bubble_sort([1]))\nprint(bubble_sort([9, 8, 7, 6, 5, 4, 3, 2, 1]))",
        ],
    },
    "algorithmen/tasks/12-a12.json": {
        "solution": """class Knoten:
    def __init__(self, wert):
        self.wert = wert
        self.links = None
        self.rechts = None

def inorder(knoten):
    if knoten is None:
        return
    inorder(knoten.links)
    print(knoten.wert, end=' ')
    inorder(knoten.rechts)

# Baum aufbauen
wurzel = Knoten(4)
wurzel.links = Knoten(2)
wurzel.rechts = Knoten(6)
wurzel.links.links = Knoten(1)
wurzel.links.rechts = Knoten(3)

inorder(wurzel)
print()  # Zeilenumbruch
""",
        "hidden": [
            "b = Knoten(10)\nb.links = Knoten(5)\nb.links.links = Knoten(2)\nb.rechts = Knoten(20)\nb.rechts.links = Knoten(15)\ninorder(b)\nprint()",
            "inorder(None)\nprint('leer')\ninorder(Knoten(7))\nprint()",
        ],
    },
    "cloud-netze/tasks/10-c10.json": {
        "solution": """def http_get(host, pfad):
    return f'GET {pfad} HTTP/1.1\\r\\nHost: {host}\\r\\nConnection: close\\r\\n\\r\\n'

result = http_get('www.schule.de', '/stundenplan')
print(result)
""",
        "hidden": [
            "print(http_get('example.org', '/'))",
            "print(repr(http_get('api.test', '/v1/noten?klasse=8a')))",
        ],
    },
    "it-sicherheit/tasks/07-s7.json": {
        "solution": """def passwort_check(pw):
    hat_zahl = any(zeichen.isdigit() for zeichen in pw)
    if len(pw) >= 8 and hat_zahl:
        return 'Sicher'
    return 'Unsicher'

print(passwort_check('abc'))        # → Unsicher
print(passwort_check('abcdef12'))   # → Sicher
""",
        "hidden": [
            "print(passwort_check('abcdefgh'))\nprint(passwort_check('1234567'))\nprint(passwort_check('12345678'))",
            "print(passwort_check('langes passwort ohne zahl'))\nprint(passwort_check('a1'))\nprint(passwort_check('Sommer2026!'))",
        ],
    },
    "it-sicherheit/tasks/10-s10.json": {
        "starterCode": """def caesar_brute_force(text):
    for versatz in range(1, 26):
        ergebnis = ""
        for zeichen in text:
            if zeichen.isalpha():
                basis = ord('A') if zeichen.isupper() else ord('a')
                # Zeichen um "versatz" Stellen zurückschieben (Tipp: % 26)
                ergebnis += ...
            else:
                ergebnis += zeichen
        print(f'Versatz {versatz:2}: {ergebnis}')

caesar_brute_force('KHOOR')
""",
        "assignment": "Der Code soll alle 25 möglichen Versätze durchprobieren. Ergänze die Zeile, die ein Zeichen zurückschiebt. In welcher Zeile steht der Klartext?",
        "solution": """def caesar_brute_force(text):
    for versatz in range(1, 26):
        ergebnis = ""
        for zeichen in text:
            if zeichen.isalpha():
                basis = ord('A') if zeichen.isupper() else ord('a')
                # Zeichen um "versatz" Stellen zurückschieben (Tipp: % 26)
                ergebnis += chr((ord(zeichen) - basis - versatz) % 26 + basis)
            else:
                ergebnis += zeichen
        print(f'Versatz {versatz:2}: {ergebnis}')

caesar_brute_force('KHOOR')
""",
        "hidden": [
            "caesar_brute_force('Bcd, efg!')",
            "caesar_brute_force('ZAB')",
        ],
    },
    "ki-informatik/tasks/11-ai11.json": {
        "solution": """def tier_klassifizieren(hat_fluegel, ist_saeugetier, lebt_im_wasser):
    if hat_fluegel:
        return 'Fledermaus' if ist_saeugetier else 'Vogel'
    if lebt_im_wasser:
        return 'Delphin' if ist_saeugetier else 'Fisch'
    return 'Hund'

print(tier_klassifizieren(True, False, False))   # → Vogel
print(tier_klassifizieren(True, True, False))    # → Fledermaus
print(tier_klassifizieren(False, True, True))    # → Delphin
print(tier_klassifizieren(False, False, True))   # → Fisch
print(tier_klassifizieren(False, True, False))   # → Hund
""",
        "hidden": [
            "print(tier_klassifizieren(True, False, True))\nprint(tier_klassifizieren(True, True, True))",
            "print(tier_klassifizieren(False, False, False))\nprint(tier_klassifizieren(False, True, True))",
        ],
    },
    "programmierung/tasks/13-p13.json": {
        "solution": """for i in range(1, 16):
    if i % 3 == 0 and i % 5 == 0:
        print('FizzBuzz')
    elif i % 3 == 0:
        print('Fizz')
    elif i % 5 == 0:
        print('Buzz')
    else:
        print(i)
""",
        # FizzBuzz hat keine Funktion – stattdessen den Bereich über die Eingabe variieren geht nicht ohne
        # Änderung der Aufgabe; die sichtbare Ausgabe bleibt der einzige Test.
        "hidden": [],
    },
}


def run(code):
    result = subprocess.run([sys.executable, "-I", "-c", code], capture_output=True, text=True, timeout=10)
    if result.returncode != 0:
        raise RuntimeError(result.stderr)
    return result.stdout


def main():
    for relative, spec in TASKS.items():
        path = ROOT / relative
        task = json.loads(path.read_text(encoding="utf-8"))
        cfg = task["config"]
        if "starterCode" in spec:
            cfg["starterCode"] = spec["starterCode"]
        if "assignment" in spec:
            task["assignment"] = spec["assignment"]
        cfg["solution"] = spec["solution"]
        tests = [{"expectedOutput": run(spec["solution"]), "hidden": False}]
        for append in spec["hidden"]:
            tests.append({"append": append, "expectedOutput": run(spec["solution"] + "\n\n" + append + "\n")})
        cfg["tests"] = tests
        path.write_text(json.dumps(task, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{relative}: {len(tests)} Tests")


if __name__ == "__main__":
    main()
