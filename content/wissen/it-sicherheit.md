---
title: IT-Sicherheit
icon: 🔒
module: it-sicherheit
---

# IT-Sicherheit

IT-Sicherheit schützt Daten und Systeme vor Diebstahl, Manipulation und Ausfall. Die drei **Schutzziele** sind:

| Schutzziel          | Frage                                       | Bedroht durch …                 |
| ------------------- | ------------------------------------------- | ------------------------------- |
| **Vertraulichkeit** | Sehen nur Berechtigte die Daten?            | Datenklau, Mitlesen             |
| **Integrität**      | Sind die Daten unverändert und korrekt?     | Manipulation, Schadsoftware     |
| **Verfügbarkeit**   | Funktioniert das System, wenn man es braucht? | Überlastung, Ransomware, Ausfall |

Die größte Schwachstelle ist oft nicht die Technik, sondern der **Mensch**. Deshalb beginnt Sicherheit mit Aufmerksamkeit.

---

## 1. Phishing erkennen

**Phishing** sind gefälschte Nachrichten, die dich dazu bringen sollen, Passwörter preiszugeben, auf schädliche Links zu klicken oder Anhänge zu öffnen.

### Warnzeichen

| Warnzeichen                 | Beispiel                                                          |
| --------------------------- | ----------------------------------------------------------------- |
| **Gefälschter Absender**    | `service@paypa1-sicherheit.com` statt `paypal.com`                |
| **Druck und Dringlichkeit** | „Ihr Konto wird in 24 Stunden gesperrt!“                          |
| **Allgemeine Anrede**       | „Sehr geehrter Kunde“ statt deines Namens                         |
| **Verdächtige Links**       | Linktext zeigt `bank.de`, der Link führt aber zu `bank-login.xyz` |
| **Fragen nach Daten**       | „Bestätigen Sie Ihr Passwort und Ihre PIN“ – seriöse Firmen tun das nie |
| **Unerwartete Anhänge**     | `Rechnung.pdf.exe`, `.zip`, Office-Dateien mit Makros             |
| **Fehler**                  | Rechtschreibfehler, seltsame Formulierungen, falsches Logo         |
| **Zu gut, um wahr zu sein** | „Sie haben ein iPhone gewonnen!“                                  |

### Richtig reagieren

1. **Nicht klicken**, keine Anhänge öffnen, nicht antworten.
2. Mit der Maus über den Link fahren (nicht klicken!) und die echte Zieladresse ansehen.
3. Im Zweifel die Firma **über die offizielle Webseite oder Telefonnummer** kontaktieren – nie über Daten aus der Mail.
4. Phishing melden und löschen. Hast du schon etwas eingegeben: **sofort Passwort ändern**.

---

## 2. Sichere Passwörter

Angreifer probieren Passwörter automatisch aus – Milliarden pro Sekunde, zuerst mit Listen beliebter Passwörter und Wörterbüchern.

**Was ein Passwort stark macht:**

- **Länge** ist der wichtigste Faktor: mindestens 12 Zeichen, besser mehr.
- Mischung aus Groß- und Kleinbuchstaben, Ziffern und Sonderzeichen.
- Keine Wörter aus dem Wörterbuch, keine Namen, Geburtstage oder Muster wie `123456`, `qwertz`.
- **Für jeden Dienst ein eigenes Passwort** – sonst öffnet ein Datenleck alle Konten.

| Passwort             | Bewertung                                                   |
| -------------------- | ----------------------------------------------------------- |
| `123456`             | ❌ sofort geknackt                                           |
| `Sommer2024!`        | ❌ Wort + Jahr + Zeichen – typisches Muster                  |
| `P@ssw0rt`           | ❌ bekannte Ersetzungen helfen nicht                         |
| `xK9#mQ2$`           | ⚠️ zufällig, aber nur 8 Zeichen                             |
| `Tiger-Regen-Fahrrad-42-Lampe` | ✅ lang und trotzdem merkbar (Passphrase)          |

**Warum Länge so viel bringt:** Bei 70 möglichen Zeichen gibt es 70⁸ ≈ 576 Billionen Passwörter mit 8 Zeichen – aber 70¹² ≈ 1,4 · 10²² mit 12 Zeichen, also rund **24 Millionen Mal so viele**.

**Tipp:** Ein **Passwort-Manager** erzeugt und speichert für jeden Dienst ein zufälliges Passwort. Du merkst dir nur noch ein starkes Master-Passwort.

### Passwortregeln in Python prüfen

```python
def ist_sicher(pw):
    if len(pw) < 12:
        return False
    hat_gross = any(z.isupper() for z in pw)
    hat_klein = any(z.islower() for z in pw)
    hat_ziffer = any(z.isdigit() for z in pw)
    hat_sonder = any(not z.isalnum() for z in pw)
    return hat_gross and hat_klein and hat_ziffer and hat_sonder

print(ist_sicher("hallo"))              # → False
print(ist_sicher("Mein-Hund-Bello-7"))  # → True
```

Nützliche String-Methoden: `isupper()`, `islower()`, `isdigit()`, `isalpha()`, `isalnum()`.

---

## 3. Zwei-Faktor-Authentifizierung (2FA)

Ein Faktor allein kann gestohlen werden. Bei 2FA brauchst du **zwei verschiedene Arten** von Nachweisen:

| Faktor       | Etwas, das du …    | Beispiele                                   |
| ------------ | ------------------ | ------------------------------------------- |
| **Wissen**   | … weißt            | Passwort, PIN                               |
| **Besitz**   | … hast             | Smartphone mit Authenticator-App, Sicherheitsschlüssel, Bankkarte |
| **Inhärenz** | … bist             | Fingerabdruck, Gesichtserkennung            |

Kennt ein Angreifer dein Passwort, fehlt ihm trotzdem der Code vom Handy. Die Einmal-Codes einer **Authenticator-App** wechseln alle 30 Sekunden.

> ⚠️ Zwei Passwörter sind **kein** 2FA – beides ist „Wissen“.

---

## 4. Hashing und Verschlüsselung

| Verschlüsselung                               | Hashing                                           |
| --------------------------------------------- | ------------------------------------------------- |
| **umkehrbar**: mit dem Schlüssel wieder lesbar | **Einbahnstraße**: aus dem Hash kommt man nicht zurück |
| für Nachrichten, Dateien, Verbindungen        | für **Passwörter**, Prüfsummen                    |
| Beispiel: AES, HTTPS                          | Beispiel: SHA-256, bcrypt, Argon2                 |

**Warum Passwörter gehasht werden:** Wird die Datenbank gestohlen, hat der Angreifer nur Hashes. Wären die Passwörter nur verschlüsselt, könnte er mit dem (oft ebenfalls gestohlenen) Schlüssel alle entschlüsseln.

**So funktioniert der Login:** Das eingegebene Passwort wird ebenfalls gehasht und mit dem gespeicherten Hash verglichen. Gleiche Eingabe → gleicher Hash.

```python
import hashlib

def passwort_hash(pw):
    return hashlib.new("sha256", pw.encode("utf-8")).hexdigest()

gespeichert = passwort_hash("geheim123")
eingabe = passwort_hash("geheim123")
print(gespeichert == eingabe)   # → True
print(len(gespeichert))         # → 64 (Hex-Zeichen, egal wie lang das Passwort ist)
```

- `encode("utf-8")` wandelt den Text in Bytes um – Hashfunktionen arbeiten mit Bytes.
- `hexdigest()` gibt den Hash als lesbare Hex-Zeichenkette zurück.
- Schon eine winzige Änderung (`geheim124`) ergibt einen völlig anderen Hash.

In echten Anwendungen nimmt man langsame Passwort-Hashverfahren (bcrypt, Argon2) mit **Salt** (zufälliger Zusatz pro Passwort), damit vorberechnete Tabellen nichts nützen.

### Symmetrisch und asymmetrisch

- **Symmetrisch:** Beide Seiten nutzen **denselben Schlüssel** (schnell, aber wie tauscht man ihn sicher aus?).
- **Asymmetrisch:** Ein **öffentlicher Schlüssel** verschlüsselt, nur der passende **private Schlüssel** entschlüsselt. HTTPS nutzt beides: asymmetrisch für den Schlüsselaustausch, symmetrisch für die Daten.

---

## 5. Caesar-Verschlüsselung

Jeder Buchstabe wird um eine feste Zahl (den **Schlüssel**) im Alphabet verschoben. Mit Schlüssel 3: `A → D`, `B → E`, …, `X → A`, `Y → B`, `Z → C`.

```
Klartext:  H A L L O
+3:        K D O O R
```

**Entschlüsseln** = um denselben Wert zurückschieben.

So rechnet man einen Großbuchstaben um:

1. Buchstabe in eine Zahl von 0 bis 25 umwandeln: `ord(zeichen) - ord("A")` (für `"C"`: 67 − 65 = 2)
2. Verschiebung addieren (verschlüsseln) oder abziehen (entschlüsseln)
3. `% 26` rechnen, damit es nach `Z` wieder bei `A` weitergeht – das klappt in Python auch bei negativen Zahlen: `-1 % 26` ergibt `25`
4. Zurück in einen Buchstaben: `chr(zahl + ord("A"))`

```python
print(ord("A"), ord("Z"))   # → 65 90
print(chr(65 + 3))          # → D
print((25 + 3) % 26)        # → 2   (Z + 3 landet bei C)
```

Zeichen, die keine Buchstaben sind (Leerzeichen, Satzzeichen), lässt man unverändert. Bei Kleinbuchstaben rechnet man genauso, nur mit `ord("a")` als Basis.

### Brute Force

Es gibt nur **25 sinnvolle Schlüssel**. Ein Angreifer probiert einfach alle aus und sucht den lesbaren Text:

```
FÜR jeden Schlüssel von 1 bis 25:
    entschlüssle den Geheimtext mit diesem Schlüssel
    gib Schlüssel und Ergebnis aus
→ die eine Zeile, die sinnvolles Deutsch ergibt, ist der Klartext
```

Genau deshalb ist Caesar unsicher. Moderne Verfahren wie AES haben so viele mögliche Schlüssel (2¹²⁸ und mehr), dass Ausprobieren länger dauert als das Universum alt ist.

---

## 6. SQL-Injection

Wenn ein Programm Benutzereingaben **direkt in SQL-Befehle einbaut**, kann ein Angreifer eigenen SQL-Code einschleusen.

```python
# ❌ GEFÄHRLICH
sql = "SELECT * FROM nutzer WHERE name = '" + name + "' AND passwort = '" + pw + "'"
```

Gibt der Angreifer als Namen `admin'--` ein, entsteht:

```sql
SELECT * FROM nutzer WHERE name = 'admin'--' AND passwort = '...'
```

`--` leitet in SQL einen **Kommentar** ein. Die Passwortprüfung wird ignoriert – der Angreifer ist als `admin` angemeldet. Mit `' OR '1'='1` wird die Bedingung immer wahr.

**Schutz:** Eingaben nie per Text zusammensetzen, sondern **Platzhalter (Prepared Statements)** verwenden. Die Datenbank behandelt die Eingabe dann immer als Wert, nie als Befehl:

```python
# ✅ SICHER
cursor.execute("SELECT * FROM nutzer WHERE name = ? AND passwort_hash = ?", (name, pw_hash))
```

Weitere Regeln: Eingaben prüfen, Fehlermeldungen nicht zu ausführlich, Datenbanknutzer nur mit nötigen Rechten.

---

## 7. Firewall

Eine **Firewall** prüft jedes Paket anhand einer **Regelliste**. Regeln werden **von oben nach unten** gelesen – **die erste passende Regel entscheidet**, der Rest wird nicht mehr angesehen.

```
Regel 1: ALLOW  Port 443  (HTTPS)
Regel 2: DENY   Port 25   (SMTP)
Regel 3: ALLOW  Port 53   (DNS)
Regel 4: DENY   ALL
```

| Anfrage           | Ergebnis                                                    |
| ----------------- | ----------------------------------------------------------- |
| HTTPS (Port 443)  | erlaubt durch Regel 1                                       |
| SMTP (Port 25)    | blockiert durch **Regel 2** – Regel 3 und 4 werden gar nicht mehr gelesen |
| DNS (Port 53)     | erlaubt durch Regel 3                                       |
| HTTP (Port 80)    | blockiert durch Regel 4 – keine Regel davor passt           |

> **Grundsatz:** Erst gezielt erlauben, was nötig ist, am Ende **alles andere verbieten** („Default Deny“). Die Reihenfolge ist entscheidend: Steht `DENY ALL` oben, kommt nichts mehr durch.

---

## 8. Man-in-the-Middle (MITM)

Ein Angreifer schaltet sich **unbemerkt zwischen** zwei Kommunikationspartner und kann mitlesen oder Daten verändern – typisch in offenen WLANs („Free_WiFi“), die er selbst aufgestellt hat.

```
Du  ⇄  Angreifer  ⇄  Webseite
```

**Schutz:**

- Nur **HTTPS**-Seiten nutzen (Schloss-Symbol); Zertifikatswarnungen des Browsers nie wegklicken.
- In öffentlichen WLANs keine sensiblen Dinge erledigen oder ein **VPN** nutzen.
- Ende-zu-Ende-Verschlüsselung bei Messengern.

---

## 9. Arbeitsplatz-Sicherheit (Clean Desk)

Viele Lecks entstehen ganz analog:

- Passwörter auf **Haftnotizen** am Bildschirm oder unter der Tastatur
- Bildschirm **nicht gesperrt** beim Verlassen des Platzes (Windows: `Win + L`)
- Vertrauliche **Ausdrucke** offen liegen lassen
- Fremde **USB-Sticks** einstecken (können Schadsoftware enthalten)
- Zugangskarten oder Schlüssel liegen lassen
- Bildschirm von außen einsehbar (z. B. am Fenster oder im Zug)

---

## 10. Schadsoftware (Malware)

| Art            | Was sie tut                                                  |
| -------------- | ------------------------------------------------------------ |
| **Virus**      | hängt sich an Dateien und verbreitet sich beim Ausführen     |
| **Wurm**       | verbreitet sich selbstständig über das Netzwerk              |
| **Trojaner**   | tarnt sich als nützliches Programm, öffnet eine Hintertür    |
| **Ransomware** | verschlüsselt Daten und verlangt Lösegeld                    |
| **Spyware**    | spioniert Eingaben und Daten aus                             |

**Schutz:** Updates zeitnah einspielen, nur Software aus vertrauenswürdigen Quellen, **regelmäßige Backups** (auch offline – gegen Ransomware), Virenschutz, geringe Rechte im Alltag.

---

## 11. Red Team, Blue Team und Sicherheits-Audits

- **Red Team:** greift mit Erlaubnis an, um Lücken zu finden (Penetrationstest).
- **Blue Team:** verteidigt, überwacht Systeme, reagiert auf Vorfälle.
- **Purple Team:** beide arbeiten zusammen und lernen voneinander.

> ⚠️ Angriffe auf fremde Systeme ohne ausdrückliche Erlaubnis sind **strafbar** (§ 202a ff. StGB) – auch „nur zum Testen“.

### Ein Audit dokumentieren

Für jede gefundene Schwachstelle:

1. **Was** ist das Problem? (z. B. „Passwörter werden im Klartext gespeichert“)
2. **Wo** tritt es auf? (Seite, Funktion, Datei)
3. **Risiko:** Wie schlimm ist es und wie wahrscheinlich? (niedrig / mittel / hoch / kritisch)
4. **Empfehlung:** Wie behebt man es? (z. B. „mit bcrypt hashen“)

Auf einen Angriff reagiert das Blue Team nach dem Muster **Erkennen → Eindämmen → Beseitigen → Wiederherstellen → Lernen**.
