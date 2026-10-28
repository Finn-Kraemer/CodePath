---
title: Cloud & Netzwerke
icon: ☁️
module: cloud-netze
---

# Cloud & Netzwerke

Ein **Netzwerk** verbindet Geräte, damit sie Daten austauschen können. Das **Internet** ist ein Netz aus unzähligen Netzen. Hier lernst du, wie Daten ihren Weg finden – und was zu tun ist, wenn sie es nicht tun.

---

## 1. Datenübertragung in Paketen

Eine große Datei wird nicht am Stück verschickt, sondern in viele kleine **Pakete** zerlegt. Jedes Paket enthält Absender, Empfänger und eine Nummer. Die Pakete reisen einzeln – manchmal auf verschiedenen Wegen – und werden beim Empfänger wieder in der richtigen Reihenfolge zusammengesetzt.

Wichtige Geräte auf dem Weg:

| Gerät      | Aufgabe                                                                 |
| ---------- | ----------------------------------------------------------------------- |
| **Switch** | verbindet Geräte **im selben Netz** (z. B. alle PCs in einem Raum)      |
| **Router** | verbindet **verschiedene Netze** und sucht den Weg für jedes Paket      |
| **Firewall** | lässt nur erlaubten Verkehr durch (siehe IT-Sicherheit)               |
| **Access Point** | bringt Geräte per WLAN ins Netz                                   |

---

## 2. IP-Adressen

Jedes Gerät im Netz braucht eine **IP-Adresse** – wie eine Postanschrift.

Eine **IPv4-Adresse** besteht aus **4 Zahlen**, getrennt durch Punkte. Jede Zahl ist **1 Byte** groß, also zwischen **0 und 255**.

| Adresse              | Gültig? | Warum                                 |
| -------------------- | ------- | ------------------------------------- |
| `192.168.1.100`      | ✅      | vier Zahlen, alle zwischen 0 und 255  |
| `10.0.0.1`           | ✅      |                                       |
| `999.1.1.1`          | ❌      | 999 ist größer als 255                |
| `192.168.1`          | ❌      | nur drei Zahlen                       |
| `hello.world.net.de` | ❌      | das ist ein Name, keine Adresse       |

- **Private Adressen** (nur im eigenen Netz, z. B. zu Hause oder in der Schule): `192.168.x.x`, `10.x.x.x`, `172.16.x.x – 172.31.x.x`
- **127.0.0.1** (`localhost`) ist immer der eigene Rechner.
- **IPv6** wurde eingeführt, weil die rund 4,3 Milliarden IPv4-Adressen nicht mehr reichen. Beispiel: `2001:db8::1`.

---

## 3. Subnetze

Eine Adresse wie `192.168.1.0/24` beschreibt ein ganzes **Netz**. Die Zahl hinter dem Schrägstrich sagt, wie viele der 32 Bits den **Netzteil** bilden. Der Rest ist der **Geräteteil** (Host-Teil).

```
192.168.1.0/24
└─ 24 Bit Netz ─┘└ 8 Bit für Geräte ┘

Subnetzmaske: 255.255.255.0
```

**Wie viele Geräte passen hinein?**

1. Host-Bits = 32 − Präfix
2. Adressen = 2^(Host-Bits)
3. Nutzbar = Adressen − 2

Zwei Adressen sind reserviert: die **Netzadresse** (erste, z. B. `.0`) und die **Broadcast-Adresse** (letzte, z. B. `.255`, erreicht alle Geräte im Netz).

| Präfix | Maske             | Adressen | Nutzbare Geräte |
| ------ | ----------------- | -------- | --------------- |
| /24    | 255.255.255.0     | 256      | 254             |
| /25    | 255.255.255.128   | 128      | 126             |
| /26    | 255.255.255.192   | 64       | 62              |
| /28    | 255.255.255.240   | 16       | 14              |
| /16    | 255.255.0.0       | 65 536   | 65 534          |

> **Warum Subnetze?** Man trennt z. B. Schüler-WLAN, Lehrer-PCs und Server voneinander. Fehler und Angriffe bleiben dann im eigenen Bereich, und man kann zwischen den Netzen gezielt Regeln setzen.

---

## 4. DNS – das Telefonbuch des Internets

Menschen merken sich Namen (`schule.de`), Computer brauchen IP-Adressen. Das **DNS (Domain Name System)** übersetzt Namen in Adressen.

So läuft eine Anfrage ab:

1. Du tippst `www.beispiel.de` in den Browser.
2. Der Rechner schaut in seinen **Cache** – kennt er die Adresse schon?
3. Wenn nicht, fragt er einen **DNS-Server** (Resolver, meist vom Router oder Provider).
4. Der Resolver fragt sich durch: **Root-Server** → zuständiger Server für `.de` → zuständiger Server für `beispiel.de`.
5. Die IP-Adresse kommt zurück, wird gespeichert und der Browser verbindet sich.

Typischer Fehler: Die Webseite lädt nicht, aber die IP-Adresse funktioniert → **DNS-Problem**.

---

## 5. Das OSI-Modell

Das OSI-Modell teilt Netzwerkkommunikation in **7 Schichten**. Jede Schicht hat eine Aufgabe und nutzt die darunterliegende.

| Nr. | Schicht           | Aufgabe                                      | Beispiele                    |
| --- | ----------------- | -------------------------------------------- | ---------------------------- |
| 7   | Anwendung         | Dienste für Programme                         | HTTP, DNS, SMTP              |
| 6   | Darstellung       | Format, Verschlüsselung                       | TLS, JPEG                    |
| 5   | Sitzung           | Verbindungen verwalten                        | Sitzungen                    |
| 4   | Transport         | Ende-zu-Ende-Übertragung, Ports              | **TCP, UDP**                 |
| 3   | Netzwerk          | Adressierung und Weg (Routing)               | **IP**, Router, IP-Firewall  |
| 2   | Sicherung         | Übertragung im lokalen Netz, MAC-Adressen    | Ethernet, Switch             |
| 1   | Bitübertragung    | Signale auf dem Medium                        | Kabel, Funk, Glasfaser       |

> **Eselsbrücke (von 1 nach 7):** „**B**itte **S**ende **N**eue **T**ermine **S**chnell **D**urch **A**ufruf“ – Bitübertragung, Sicherung, Netzwerk, Transport, Sitzung, Darstellung, Anwendung.

### Fehlersuche von unten nach oben

Wenn „das Internet nicht geht“, prüfe systematisch von Schicht 1 aufwärts:

1. **Kabel/WLAN:** Ist das Gerät überhaupt verbunden?
2. **IP-Adresse:** Hat das Gerät eine gültige Adresse? (`ipconfig` / `ip a`)
3. **Erreichbarkeit:** Antwortet der Router oder ein Server? (`ping 8.8.8.8`)
4. **DNS:** Klappt die Namensauflösung? (`ping google.de`, `nslookup google.de`)
5. **Anwendung:** Liegt es am Browser oder an der Webseite selbst?

---

## 6. TCP und UDP

Beide arbeiten auf der Transportschicht und nutzen **Ports**, damit die Daten beim richtigen Programm ankommen.

| TCP                                           | UDP                                        |
| --------------------------------------------- | ------------------------------------------ |
| verbindungsorientiert (Verbindungsaufbau mit „Handshake“) | verbindungslos, einfach losschicken |
| **zuverlässig**: verlorene Pakete werden neu gesendet | keine Garantie, Pakete können fehlen |
| richtige Reihenfolge garantiert               | Reihenfolge nicht garantiert               |
| langsamer, mehr Aufwand                       | **schnell**, wenig Verzögerung             |
| Webseiten, E-Mail, Dateien, Online-Banking    | Videotelefonie, Live-Streams, Online-Spiele, DNS |

> **Faustregel:** Muss **alles vollständig** ankommen → TCP. Ist **Geschwindigkeit wichtiger** als ein einzelnes verlorenes Paket → UDP. Bei einem Videoanruf ist ein kurzer Bildfehler besser als eine Verzögerung.

### Wichtige Ports

| Dienst | Port | Wofür                         |
| ------ | ---- | ----------------------------- |
| FTP    | 21   | Dateiübertragung (alt, unverschlüsselt) |
| SSH    | 22   | sichere Fernwartung           |
| SMTP   | 25   | E-Mail-Versand                |
| DNS    | 53   | Namensauflösung               |
| HTTP   | 80   | Webseiten                     |
| HTTPS  | 443  | verschlüsselte Webseiten      |

---

## 7. HTTP – wie der Browser fragt

Eine **HTTP-Anfrage** ist reiner Text. Sie besteht aus der **Request-Zeile**, **Headern** und einer Leerzeile (bei POST danach noch Daten):

```
GET /index.html HTTP/1.1
Host: www.beispiel.de
User-Agent: Mozilla/5.0
Accept: text/html

```

- **Request-Zeile:** Methode (`GET`, `POST` …), Pfad, Protokollversion
- **Header:** Zusatzinfos als `Name: Wert`; `Host` ist Pflicht

In Python zusammengebaut:

```python
methode = "GET"
pfad = "/index.html"
host = "www.beispiel.de"

print(f"{methode} {pfad} HTTP/1.1")
print(f"Host: {host}")
```

Mehr zu HTTP-Methoden und Statuscodes steht im Artikel **Web-Entwicklung**.

---

## 8. Cloud vs. lokaler Server

Ein **lokaler Server** steht bei dir (z. B. im Serverraum der Schule). Die **Cloud** bedeutet: Du mietest Rechenleistung und Speicher bei einem Anbieter und nutzt sie übers Internet.

| Cloud                                          | Lokaler Server                                 |
| ---------------------------------------------- | ---------------------------------------------- |
| ✅ Anbieter kümmert sich um Hardware, Updates, Backups | ❌ alles selbst warten                  |
| ✅ schnell mehr Leistung buchbar (skalierbar)  | ❌ neue Hardware kaufen und einbauen           |
| ✅ zahlen nach Nutzung, keine großen Anschaffungen | ✅ einmalige Kosten, danach planbar       |
| ❌ abhängig von Internetverbindung und Anbieter | ✅ funktioniert auch ohne Internet           |
| ❌ Daten liegen bei Dritten (Datenschutz, DSGVO) | ✅ volle Kontrolle über die Daten            |

**Servicemodelle:**

- **IaaS** (Infrastructure as a Service): virtuelle Server mieten, alles darauf selbst einrichten.
- **PaaS** (Platform as a Service): Plattform zum Ausführen eigener Programme, Server werden verwaltet.
- **SaaS** (Software as a Service): fertige Anwendung im Browser, z. B. Online-Office oder E-Mail.

---

## 9. Load Balancing

Ein großer Dienst läuft auf vielen Servern. Ein **Load Balancer** verteilt die eingehenden Anfragen, damit kein Server überlastet wird – und fällt einer aus, übernehmen die anderen.

Das einfachste Verfahren ist **Round Robin**: Die Anfragen gehen der Reihe nach an Server 1, 2, 3, dann wieder 1, 2, 3 …

```python
server = ["Server-A", "Server-B", "Server-C"]

for anfrage in range(7):
    ziel = server[anfrage % len(server)]   # % = Rest der Division
    print(f"Anfrage {anfrage} → {ziel}")
# Anfrage 0 → Server-A, 1 → Server-B, 2 → Server-C, 3 → Server-A, …
```

Der **Modulo-Operator `%`** sorgt dafür, dass der Index nach dem letzten Server wieder bei 0 beginnt.

---

## 10. Netzwerk messen: Ping

`ping` schickt ein kleines Paket an ein Ziel und misst die **Antwortzeit** in Millisekunden (ms). Große oder stark schwankende Werte deuten auf Probleme hin.

Um einen Durchschnitt zu berechnen, brauchst du die **Summe** aller Messwerte und ihre **Anzahl**. Die Summe sammelst du in einer Schleife in einer Variablen, die bei 0 beginnt (ein **Akkumulator**). Dasselbe Muster funktioniert für viele Auswertungen – hier zum Beispiel: Wie viele Pakete waren langsamer als 50 ms?

```python
messwerte = [32, 75, 18, 51, 9]

zu_langsam = 0                 # Zähler startet bei 0
for wert in messwerte:
    if wert > 50:
        zu_langsam += 1        # += 1 heißt: um eins erhöhen

print(f"{zu_langsam} von {len(messwerte)} Paketen zu langsam")   # → 2 von 5 Paketen zu langsam
```

Python kennt dafür auch fertige Funktionen: `sum(liste)`, `len(liste)`, `max(liste)`, `min(liste)`.

---

## 11. Ein Netzwerk planen

Bei der Planung (z. B. für eine Schule) helfen diese Fragen:

1. **Bereiche:** Welche Gruppen gibt es? (Verwaltung, Lehrkräfte, Schüler, Gäste-WLAN, Server)
2. **Adressen:** Jeder Bereich bekommt ein eigenes Subnetz, z. B. `10.0.10.0/24` für Lehrkräfte, `10.0.20.0/24` für Schüler.
3. **Regeln:** Wer darf wohin? Schüler dürfen ins Internet, aber nicht ins Verwaltungsnetz.
4. **Geräte:** Router, Switches, Access Points, Firewall – wo stehen sie?
5. **Ausfallsicherheit und Backups:** Was passiert, wenn ein Gerät ausfällt?
