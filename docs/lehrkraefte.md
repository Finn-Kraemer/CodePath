# Handbuch für Lehrkräfte

CodePath begleitet einen Berufsorientierungstag oder eine Projektwoche: Die Klasse arbeitet selbstständig an Modulen, die Lehrkraft steuert, was freigeschaltet ist, sieht den Fortschritt und bewertet, was sich nicht automatisch bewerten lässt. Dieses Handbuch folgt dem Ablauf eines Kurses.

![Startseite von CodePath](assets/screenshots/start.png)

> **Begriffe.** Ein **Modul** ist ein Berufsfeld (z. B. IT-Sicherheit) mit 10–18 Aufgaben. Ein **Kurs** ist eine Lerngruppe mit eigenem Beitrittscode, eigenen Freigaben und eigener Rangliste. Der **Leitstand** ist der Lehrkräfte-Bereich (Schaltfläche oben rechts nach der Anmeldung).

## ◼ 1. Vor dem Kurs

### Anmelden

Die Anmeldung als Lehrkraft erfolgt mit dem Benutzer `admin`. Das Passwort legt die IT-Betreuung bei der Installation fest (siehe [Betriebshandbuch](betrieb.md)) und lässt sich danach unter **Profil** ändern.

### Kurs anlegen

**Leitstand → Kurse & Konten → Kurs anlegen.** Name (z. B. „8b Projekttag“) und optional ein Enddatum eintragen. Jeder Kurs bekommt einen sechsstelligen **Beitrittscode**.

Die Klasse kann auf zwei Wegen beitreten:

| Weg | Ablauf | Geeignet für |
|---|---|---|
| **Selbst registrieren** | Schülerinnen und Schüler öffnen den Link (*Link kopieren*) oder geben den Code bei der Registrierung ein und wählen Benutzernamen und Passwort. Mit *Drucken* entsteht ein Aushang mit Code. | Ältere Klassen, eigene Geräte |
| **Konten anlegen** | Präfix und Anzahl eingeben (z. B. `8b-` und `28`). CodePath erzeugt Benutzernamen und Passwörter und zeigt sie **genau einmal** an – sofort drucken oder austeilen. | Jüngere Klassen, Leihgeräte, wenig Zeit |

Ein kompromittierter Code lässt sich mit *neuer Code* jederzeit ersetzen; bereits beigetretene Konten bleiben im Kurs.

### Module freischalten

**Leitstand → Modul-Steuerung**, oben den Kurs wählen. Jedes Modul ist zunächst gesperrt und wird mit *Freigeben* sichtbar. Optional eine **End-Uhrzeit** setzen: Ab dann sind Abgaben in diesem Modul nicht mehr möglich, der Countdown ist für die Klasse sichtbar.

### Einzelne Aufgaben freischalten oder sperren

Unter jedem Modul klappt **Einzelne Aufgaben** die Aufgabenliste auf. Je Aufgabe gibt es drei Stufen:

| Stufe | Wirkung |
|---|---|
| **Wie Modul** | Standard: sichtbar, sobald das Modul freigegeben ist |
| **Freigegeben** | sichtbar, auch wenn das Modul gesperrt ist – so lässt sich eine einzelne Aufgabe vorziehen |
| **Gesperrt** | ausgeblendet, auch wenn das Modul freigegeben ist |

Die Einstellung gilt nur für den gewählten Kurs. Aufgaben- und Punktzahl des Moduls, der Modulfortschritt der Schülerinnen und Schüler und die **Teilnahmebestätigung** zählen nur die sichtbaren Aufgaben. Gesperrte Aufgaben lassen sich nicht abgeben; bereits verdiente Punkte bleiben im Punktestand.

Empfehlung: Mit zwei oder drei Modulen beginnen und weitere im Laufe des Tages freigeben. Das Modul **Abschlusstest** erst am Ende freischalten.

### Geräte prüfen

Code-Aufgaben laufen im Browser mit; das Browser-Labor braucht die **Entwicklerwerkzeuge** (F12) und funktioniert nur am PC oder Laptop, nicht auf Tablets. Auf verwalteten Geräten vorher testen, ob die Entwicklerwerkzeuge freigegeben sind.

## ◼ 2. Während des Kurses

### Was die Klasse sieht

Jede Aufgabe hat eine kurze Geschichte, einen Auftrag und ein Lernziel. Pro Aufgabe gibt es bis zu drei **gestufte Hinweise**; jeder kostet 25 % der Punkte (höchstens 75 %). Wer im ersten Versuch ohne Hinweis löst, bekommt **20 % Bonus**. Die Lösung lässt sich aufdecken – danach gibt es keine Punkte mehr. Wie viele Versuche ein Typ erlaubt, steht in der [Übersicht der Aufgabentypen](aufgabentypen.md#-standardregeln).

### CodePath und empfohlene nächste Aufgabe

In der Modulübersicht steht oben die **empfohlene nächste Aufgabe**. Sie kommt aus dem ersten veröffentlichten CodePath; gibt es keinen, empfiehlt die Plattform die nächste offene Aufgabe – bevorzugt in einem Modul, das schon begonnen ist. Nach dem Lösen einer Aufgabe erscheint die Empfehlung auch auf der Aufgabenseite.

**Leitstand → CodePaths** legt CodePaths an. Ein neuer CodePath startet mit einer leeren Fläche. Der Editor nutzt die volle Bildschirmbreite: links die **Bausteine**, in der Mitte die Fläche, rechts die Eigenschaften der gewählten Station (ohne Auswahl: die Einstellungen des CodePaths).

- **Bausteine hineinziehen**: Oben links stehen *Leerer Block* (Art erst danach festlegen), *Ganzes Modul*, *Teil eines Moduls*, *Einzelne Aufgabe* und *Meilenstein*, darunter unter **Steuerung** *Weiche*, *UND*, *ODER*, *Bonus*, *Stütz-Knoten* und *CodePath-Baustein*. Darunter stehen alle Module; ein Modul oder – aufgeklappt – eine einzelne Aufgabe lässt sich direkt hineinziehen. Anklicken statt Ziehen hängt den Block unter die gewählte Station.
- **Wo man loslässt, zählt**: auf einer Station → wird darunter angehängt und verbunden; auf einer Linie → wird zwischen die beiden Stationen eingefügt; auf freier Fläche → steht für sich.
- **Art wechseln**: Rechts lässt sich jede Station jederzeit umstellen – unter *Inhalt* (ganzes Modul, Teil eines Moduls, einzelne Aufgabe, Stütz-Knoten, CodePath-Baustein) und unter *Steuerung* (Meilenstein, Bonus, Weiche, UND, ODER). Noch nicht fertig eingerichtete Blöcke sind gelb gestrichelt; gespeichert wird erst, wenn alle eingerichtet sind.
- **Verbinden**: am Punkt unten an einer Station ziehen und auf der Station loslassen, die danach kommt. Eine Linie anklicken öffnet rechts ihre Einstellungen: *Pflicht* (durchgezogen) oder *Empfehlung* (gestrichelt), Weg-Beschriftung bei Weichen und eine Bedingung. Kreise sind nicht möglich.
- Stationen **ziehen** (oder mit den Pfeiltasten verschieben, Entf entfernt). **Anordnen** legt die Stationen ordentlich in Reihen, **Zoom** und **Alles zeigen** holen große CodePaths auf den Bildschirm. **Vorschau** zeigt die Karte mit dem eigenen Stand oder aus Sicht eines Schülerkontos (*Ansehen als*).
- **Teil eines Moduls**: Eine Modul-Station kann alle oder nur ausgewählte Aufgaben des Moduls nutzen. **Restliche Aufgaben als neue Station** hängt den Rest als zweite Station an – so entsteht z. B. *Programmierung 1 → Web 1 → Programmierung 2*.
- **Einstellungen** (rechts, wenn nichts gewählt ist): Titel, Adresse, *Gilt für*, *Alles außerhalb des CodePaths*, *Verbindlich*, *Fokus-Modus*, *Nur als Baustein* und *Veröffentlicht*; darunter die Übersichten *Weichen im Kurs* und *Punkte-Tore im Kurs*.

Die Bausteine im Einzelnen:

- **UND und ODER**: Logik-Blöcke führen Zweige zusammen. *UND* (`&`) geht erst weiter, wenn alle eingehenden Wege erledigt sind, *ODER* (`≥1` bzw. `≥n`) sobald mindestens *n* von ihnen erledigt sind. Sie brauchen mindestens zwei eingehende und eine ausgehende Verbindung und zählen ebenso wie Weichen und Bonus-Stationen nicht als eigene Station in „x/y Stationen“. Bei ODER bleiben alle Zweige offen; die Empfehlung bevorzugt den Zweig mit dem meisten Fortschritt.
- **Stütz-Knoten**: Eine Station vom Typ *Stütz-Knoten* bietet gezielte Unterstützung, wenn Schülerinnen und Schüler an einer Aufgabe Schwierigkeiten haben. Sie bindet eine Übungsaufgabe sowie optional einen Artikel der Wissensdatenbank ein. Solange keine Bedingung greift, bleibt der Stütz-Knoten auf der Schülerkarte unsichtbar (`EXCLUDED`). Wird er durch eine Bedingung aktiviert, schaltet er sich als Pflicht-Station vor die nächste Station, erscheint mit Rettungsring-Symbol und wird in der Empfehlung verlinkt (inkl. direktem Link zum Wissensartikel).
- **Bedingte Kanten & Fast-Track**: Linien zwischen Stationen können mit Bedingungen konfiguriert werden (Linie anklicken → Bedingung wählen):
 - **Fast-Track (`PERFECT_FIRST_TRY`)**: 100 % im 1. Versuch ohne Inanspruchnahme von Hinweisen schaltet die Abkürzung oder Bonus-Challenge frei. Übersprungene Zwischenstationen erhalten den Status `SKIPPED` („übersprungen – Fast-Track“), blockieren nachfolgende Stationen nicht und werden auf der Schülerkarte mit einem Fast-Track-Banner gewürdigt.
 - **Schwierigkeiten (`STRUGGLED`)**: Greift ab einer einstellbaren Schwelle an Fehlversuchen (z. B. ≥ 3) oder Hinweisen (z. B. ≥ 2), bei `FAILED` oder aufgedeckter Lösung und leitet gezielt auf einen Stütz-Knoten um.
 - **Mindestpunktzahl (`SCORE_AT_LEAST`)**: Verzweigt bei Erreichen eines prozentualen Schwellenwerts.
 - **Normalweg (`DEFAULT`)**: Standardweg, wenn keine andere Bedingung zutrifft. Jede Station mit bedingten Kanten benötigt zwingend eine DEFAULT-Kante.
 Das Verzweigungsergebnis wird beim Erledigen der Bezugsaufgabe im Profil festgeschrieben. Im Editor zeigt der Linien-Inspektor, wie oft eine Kante bereits ausgelöst wurde (`x / y Schüler`).
- **Bonus-Stationen**: Eine Station vom Typ *Bonus-Station* vergibt beim Erreichen eine feste Anzahl an Bonuspunkten (1 bis 1000 Punkte, frei einstellbar) im Punkte-Journal. Sie benötigt keine ausgehende Verbindung (kann als Sackgasse am Ende eines schweren Zweigs stehen) und zählt nicht zu den Pflichtstationen (`x/y erledigt`). Das System bucht die Punkte automatisch und idempotent, sobald alle Pflicht-Vorgänger abgeschlossen sind. Verzweigt eine Weiche in einen Weg mit Bonus-Stationen (auch innerhalb eingebundener Bausteine), zeigt die Weg-Auswahlkarte die erreichbaren Bonuspunkte direkt an (z. B. `+30 P.`). Wird eine Weiche später zurückgesetzt oder umgestellt, storniert CodePath die Bonuspunkte des verlassenen Wegs per Gegenbuchung im Punkte-Journal. Wird eine Bonus-Station manuell durch die Lehrkraft freigeschaltet, gilt sie als absolviert, vergibt jedoch keine Bonuspunkte.
- **Meilenstein-Gates und Boss-Aufgaben**: Jede Aufgabe oder Meilenstein-Station kann als **Boss-Aufgabe** (`boss: true`) und/oder mit einer **Punkte-Schwelle (Gate)** konfiguriert werden (`minPercent` von 1–100 %):
 - **Voraussetzung**: Die Station schaltet sich erst frei, wenn alle Pflicht-Vorgänger erledigt sind **und** die Mindestpunktzahl im Prüfbereich erreicht wurde.
 - **Berechnungsmodi**:
 - **Gesamtpunkte (`TOTAL`)**: Prozentualer Anteil der erzielten Punkte an der Maximalsumme aller Aufgaben im Prüfbereich.
 - **Jede Vorgänger-Aufgabe einzeln (`EACH`)**: Jede einzelne bewertete Station im Prüfbereich muss mindestens die geforderte Schwelle (z. B. 80 %) erreichen.
 - Jede Aufgabe wird maximal bis 100 % ihrer Grundpunkte gewertet (Übererfüllung kompensiert keine anderen Aufgaben).
 - Gesperrte (`LOCKED`), nicht gewählte (`EXCLUDED`), übersprungene (`SKIPPED`) oder Stützaufgaben (`REMEDIAL`) sowie Aufgaben ohne Wertung fließen nicht in den Nenner ein.
 - **Prüfbereich (`scope`)**: Entweder alle Vorgänger-Stationen im Pfad (Standard) oder gezielt ausgewählte Stationen (Mehrfachauswahl).
 - **Rückfall-Regeln bei Nichterreichen (`fallback`)**: Ist die Schwelle rechnerisch nicht mehr erreichbar (z. B. durch aufgedeckte Musterlösungen oder verbrauchte Versuche), greift eine konfigurierbare Rückfall-Regel:
 - **Lehrkraft entscheidet (`TEACHER`)**: Station bleibt gesperrt („Punkte-Schwelle nicht mehr erreichbar – bitte wende dich an deine Lehrkraft“).
 - **Automatisch öffnen (`OPEN`)**: Gibt die Station frei, sobald alle Aufgaben im Prüfbereich bearbeitet wurden.
 - **Stützaufgabe (`REMEDIAL`)**: Aktiviert gezielt eine zugewiesene Stütz-Station. Nach deren Erledigung öffnet sich das Tor.
 - **Einmal freigeschaltet bleibt freigeschaltet**: Die Freischaltung wird für jede Schülerin und jeden Schüler im Profil festgeschrieben (`gate:<nodeId>`).
 - **Schüler-Ansicht**: Tore zeigen auf der Karte ein Tor-Symbol oder bei Boss-Aufgaben eine Krone mit Goldrand. Ein Klick öffnet ein Detail-Modal mit Fortschrittsbalken, Punkten und direkten Verlinkungen zu noch offenen Aufgaben („Hier noch Punkte holen“).
 - **Leitstand-Übersicht**: Im CodePath-Editor zeigt die Übersicht *Punkte-Tore & Boss-Aufgaben im Kurs* den Stand der Klasse je Tor. In der Schülerakte unter *CodePath* können Tore manuell geöffnet oder zurückgesetzt werden.
- **CodePath-Bausteine**: Ein bestehender CodePath lässt sich als wiederverwendbarer Baustein in andere CodePaths einbinden (z. B. erst drei Grundlagen-Aufgaben, dann eine Entweder-oder-Weiche in die Bausteine *Schwerpunkt Cloud* oder *Schwerpunkt Web*). Mit dem Häkchen **Nur als Baustein** erscheint ein CodePath nicht einzeln in der Schüler-Auswahl, sondern nur als Baustein im Editor. Auf der Karte zeigt der Baustein seinen Fortschritt (`x/y erledigt`) und öffnet per Klick die eingebundene Karte mit einer Rücksprung-Leiste („← Zurück zu …“). Ist der Oberpfad verbindlich, gilt Verbindlichkeit auch innerhalb des Bausteins. Zyklen und mehr als zwei Verschachtelungsebenen werden verhindert; genutzte Bausteine sind gegen versehentliches Löschen geschützt.
- **Entweder-oder-Weiche**: Eine Weiche verzweigt in mindestens zwei ausgehende Wege (jeweils mit Pflicht-Beschriftung und optionaler Kurzbeschreibung, z. B. „schwieriger – dafür +50 Punkte“). Der Editor färbt die exklusiven Stationen der einzelnen Wege farblich ein; Stationen hinter dem Zusammenführungspunkt bleiben gemeinsam. Sobald die Vorgänger der Weiche erfüllt sind, wählen Schülerinnen und Schüler explizit über „Diesen Weg nehmen“ (mit Bestätigungsdialog) ihren Weg – die nicht gewählten Zweige werden für sie dauerhaft gesperrt (*nicht gewählt*, auch in der Modulübersicht, per Direktlink und in der Wissensdatenbank).
- **Verbindlich**: Ist *Verbindlich* angehakt, müssen Pflicht-Vorgänger erledigt sein, bevor eine Station freigeschaltet wird. Vorher ist die Station für die Klasse gesperrt („Erst … abschließen“) – auf der Karte, auf der Modulseite und per Direktlink. Empfehlungs-Verbindungen (gestrichelt) bleiben sofort bearbeitbar. Pro Kurs ist höchstens ein verbindlicher CodePath möglich.
- **Gesamtübersicht für Schüler ausblenden (Fokus-Modus)**: Am verbindlichen CodePath aktivierbar. Schülerinnen und Schüler sehen in der Modulübersicht ausschließlich den CodePath – die Modul-Kacheln, der Umschalter Karte/Liste und die Pfadauswahl sind ausgeblendet. Der Menüpunkt in der Hauptnavigation heißt für sie direkt **CodePath** statt **Module**. Die interaktive Karte nutzt die volle Fläche, lässt sich stufenlos zoomen, per Mauszug verschieben, zentriert automatisch die empfohlene bzw. aktuelle Station und bietet eine Schaltfläche „Einpassen“. Auf Smartphones und kleinen Bildschirmen steht eine vertikale Listendarstellung zur Verfügung. Aufgaben und Module außerhalb des Pfads sind serverseitig gesperrt. In Kombination mit **Frei ab Station X** endet der Fokus-Modus automatisch nach Abschluss der Station X (die Modulübersicht erscheint wieder für das freie Arbeiten). Die Lehrkraft behält die vollständige Übersicht mit einem Hinweis-Banner.
- **Alles außerhalb des CodePaths**: *Frei* (folgt der Modul-Steuerung), *Gesperrt* (nur der CodePath ist sichtbar) oder *Frei ab Station …* – damit lassen sich z. B. erst drei Pflicht-Stationen vorgeben und ab dem Meilenstein „Freies Arbeiten“ automatisch alle übrigen Module freischalten.
- **Gilt für**: alle Kurse oder ein Kurs (Gruppe). Ein veröffentlichter CodePath für einen Kurs überträgt bei jedem Speichern seine Module in die **Modul-Steuerung** des Kurses: Die Module werden freigegeben, nicht genutzte Aufgaben darin einzeln gesperrt. Was sich geändert hat, zeigt der Editor nach dem Speichern; Fristen bleiben unverändert.
- **Veröffentlicht** ankreuzen und speichern – dann sieht die Klasse den CodePath als Karte (umschaltbar auf die Liste). Ein Kurs sieht die CodePaths für ihn und die für alle Kurse; die Empfehlung kommt aus dem obersten CodePath des eigenen Kurses, sonst aus dem obersten für alle Kurse.

Auf der Karte ist jede Station *erledigt*, *jetzt dran*, *noch gesperrt* („Erst … abschließen“), *später*, *Zeit abgelaufen* oder *gesperrt*. Von der Lehrkraft gesperrte oder abgelaufene Vorgänger halten niemanden auf.

### Hilfe-Rufe („Ich hänge fest“)

Auf jeder Aufgabe gibt es für Schülerinnen und Schüler den unauffälligen Button **„Ich hänge fest“** – mit Schnellauswahl (*Verstehe die Aufgabe nicht*, *Fehlermeldung*, *Technisches Problem*) und optionaler Notiz. Niemand außer Lehrkräften sieht den Ruf: keine Anzeige für Mitschüler, nichts auf dem Beamer, kein Ton. Der aktuelle Stand der Antwort (Code, SQL, Lücken, Text) und die letzte Ausgabe aus „Ausprobieren“ gehen mit; bewertet oder als Versuch gezählt wird nichts.

**Leitstand → Hilfe-Rufe** zeigt die Warteschlange live, nach Wartezeit sortiert: Name, Kurs, Aufgabe (Link), Wartezeit, Kategorie und Notiz, Tipps und Fehlversuche, letzte Abgabe, Entwurf mit Syntaxhervorhebung und die letzte Fehlermeldung. Wer länger als 5 Minuten wartet, ist rot markiert. Solange jemand wartet, zeigt die Kopfzeile neben *Leitstand* einen Zähler („✋ 3 warten“).

- **Ich komme** – andere Lehrkräfte sehen, wer sich kümmert; der Schüler sieht „Deine Lehrkraft ist unterwegs“.
- **Erledigt** – der Ruf verschwindet und steht 30 Minuten unter *Zuletzt erledigt*.
- **Nachricht schicken** – kurzer Text nur an diese Person, z. B. „Schau dir Tipp 2 an“.

Schüler sehen ihre Position („2 Personen vor dir“, ohne Namen) und können mit **Hat sich erledigt** selbst abbrechen oder mit **Stand senden** ihren Entwurf aktualisieren. Löst jemand die Aufgabe, schließt sich der Ruf von selbst („hat sich selbst gelöst“). Je Person gibt es höchstens einen offenen Ruf; ein neuer Ruf für eine andere Aufgabe ersetzt ihn.

### Quiz-Runde am Beamer

**Leitstand → Quiz-Runde**: Titel eingeben, Kurs wählen (oder alle Kurse) und Auswahl-Aufgaben in der gewünschten Reihenfolge anklicken, dann **Quiz starten**. Die Schülerinnen und Schüler sehen sofort einen Hinweis „Quiz läuft – jetzt mitmachen“ und antworten am eigenen Gerät; bis zur Auflösung lässt sich die Antwort ändern.

Gesteuert wird auf der Quiz-Seite oder direkt im **Beamer-Modus** (die Runde überdeckt dort die Rangliste): **Erste Frage** → **Auflösen** (Verteilung der Antworten als Balken, richtige Antwort markiert) → **Nächste Frage** … → **Ergebnis zeigen** (Anteil richtiger Antworten je Frage). Während eine Frage offen ist, zeigt der Beamer nur, wie viele schon geantwortet haben; die Lehrkraft sieht auf ihrer Seite die Verteilung schon vorher. Alles bleibt anonym, es gibt keine Punkte und keine Versuche. Es läuft immer nur eine Runde gleichzeitig.

### Beamer-Modus

**Leitstand → Beamer-Modus** öffnet eine Vollbild-Ansicht für den Projektor: Live-Rangliste, Uhrzeit und die aktuelle Mitteilung. Die Rangliste aktualisiert sich bei jeder gelösten Aufgabe von selbst.

### Mitteilungen

**Leitstand → Live-Mitteilung.** Der Text erscheint sofort bei allen – je nach Anzeigeort in der Kopfzeile, auf der Info-Seite oder an beiden Stellen; der Beamer-Modus zeigt ihn immer. Gut für Zeitansagen („Noch 15 Minuten in Modul 2“) oder Hinweise an alle.

### Startseite bearbeiten

**Leitstand → Startseite.** Die Startseite besteht aus Abschnitten (Titelbereich, Textabschnitt, Aufzählung, Karten, Text mit Infobox, Ablauf, Text mit Tabelle, Bild, Aufruf mit Buttons). Links wählst du einen Abschnitt und änderst seine Texte, sortierst ihn mit ↑/↓ um, blendest ihn aus, duplizierst oder löschst ihn; *+ Abschnitt hinzufügen* fügt einen neuen hinter dem gewählten ein. Rechts siehst du sofort die Vorschau, ein Klick dort öffnet den Abschnitt zum Bearbeiten. Längere Texte verstehen Markdown (**fett**, Listen, Links); Bilder kommen aus der Medienverwaltung und sind auf der veröffentlichten Startseite auch ohne Anmeldung sichtbar.

*Entwurf speichern* sichert den Stand nur für dich, erst *Veröffentlichen* zeigt ihn allen. *Änderungen verwerfen* kehrt zur Live-Fassung zurück, *Auf Standard zurücksetzen* stellt die mitgelieferte Startseite wieder her. Ein neuer Build oder Neustart ist dafür nicht nötig.

### Eigene Seiten

**Leitstand → Seiten.** Neue Seiten wie „Anfahrt“, „Ablauf“ oder „Kontakt“ anlegen: Titel eingeben, die Adresse (`/seiten/…`) wird vorgeschlagen. Bearbeitet wird mit demselben Abschnitts-Editor wie die Startseite, ebenfalls mit Entwurf und *Veröffentlichen*. Jede veröffentlichte Seite erscheint automatisch im Aufklappmenü **„Seiten“** in der Menüleiste und in der **Seitenübersicht** (`/seiten`, mit Kurzbeschreibung als Kachel). In den Einstellungen jeder Seite legst du fest, ob sie **nur für Angemeldete** oder **für alle** sichtbar ist, gibst ihr eine Kurzbeschreibung und kannst sie **zusätzlich direkt in die Menüleiste** holen; die Reihenfolge stellst du in der Seitenliste mit ↑/↓ ein. *Offline nehmen* versteckt eine Seite wieder, ohne den Entwurf zu verlieren.

### Wissensdatenbank bearbeiten

**Leitstand → Wissensdatenbank** (oder *Artikel bearbeiten* in der Wissensdatenbank). Artikel werden in Markdown geschrieben, rechts steht die Vorschau; *Bild einfügen* setzt ein Bild aus der Medienverwaltung an die Cursorposition. Pro Artikel wählst du, wann Schüler ihn sehen: **immer** (Grundlagen) oder **erst, wenn ein bestimmtes Modul freigegeben ist**. Neue Artikel sind zunächst Entwurf und nur für dich sichtbar, bis du *Veröffentlicht* anhakst. Mitgelieferte Artikel, die du nicht verändert hast, werden bei Updates auf die neue Fassung gebracht; sobald du einen Artikel bearbeitest, bleibt deine Fassung erhalten. Gelöschte mitgelieferte Artikel kommen bei Updates nicht zurück.

**Artikel verlinken:** Im Editor fügt *Artikel verlinken* einen Link auf einen anderen Artikel ein (markierter Text wird zum Linktext). Bei jeder Aufgabe kannst du unter *Passender Wissensartikel* einen Artikel wählen; Schüler sehen dann neben der Aufgabe „Mehr dazu im Wiki“. Ohne Auswahl wird automatisch der Artikel zum Modul der Aufgabe verlinkt, sofern es einen gibt und er für die Schüler sichtbar ist.

**Suche:** Über der Artikelliste gibt es ein Suchfeld. Gesucht wird in Titel und Text aller Artikel, die man sehen darf; alle Suchwörter müssen vorkommen. Treffer erscheinen mit Textausschnitt, im geöffneten Artikel sind die Fundstellen markiert.

### Fortschritt verfolgen

| Ansicht | Zeigt |
|---|---|
| **Teilnehmerliste** | Alle Schülerinnen und Schüler mit Punkten und Fortschritt; Klick öffnet die Akte |
| **Schülerakte** | Reiter **Aufgaben**: Jede Aufgabe mit Status und Versuchen (als gelöst werten, sperren/entsperren, neues Passwort vergeben). Reiter **CodePath**: Karte aus Sicht der Schülerin oder des Schülers, getroffene Entscheidungen an Weichen (auch in eingebundenen Bausteinen) mit Aktionen zum **Wahl zurücksetzen** (wahlweise Fortschritt behalten oder Fortschritt und Punkte im verlassenen Weg über eine Korrekturbuchung zurücknehmen), **Wahl für Schüler setzen**, **weiteren Weg zusätzlich freischalten**, **einzelne Stationen freischalten** (hebt die Blockierung durch Pflicht-Vorgänger auf; die Station gilt als erledigt für Nachfolger), Punkte-Tore mit Status und **Tor zurücksetzen** sowie Übersicht evaluierter bedingter Verzweigungen (Fast-Track, Stütz-Knoten) mit **Neu bewerten** zum Zurücksetzen. Auch kursweit als Sammelaktion im CodePath-Editor unter *Weichen-Übersicht im Kurs* und *Punkte-Tore & Boss-Aufgaben im Kurs* verfügbar |
| **Lernanalyse** | Je Aufgabe: wie viele es versucht haben, Fehlversuche, Lösungsquote und **Hilfe-Rufe** („Ich hänge fest“, mit Anteil selbst gelöster) – zeigt, wo die Klasse hängt. Umschaltbar: nach Hilfe-Rufen sortieren |

Mit **Alle Schüler abmelden** im Control Center endet jede laufende Sitzung, z. B. am Ende des Tages an geteilten Geräten.

## ◼ 3. Bewerten

Praxisaufgaben (Typ *Praxis*) bewertet die Lehrkraft selbst. Neue Abgaben erscheinen im **Prüfungszentrum** (*Abgaben prüfen*).

1. Abgabe öffnen – links die Aufgabenstellung, rechts die Antwort. Hat die Aufgabe eine Python-Werkbank, steht darunter der abgegebene Code und lässt sich direkt ausführen.
2. Für jedes Kriterium der **Rubrik** die Punkte wählen; die Summe ergibt die Punkte der Aufgabe.
3. Optional einen **Kommentar** für die Schülerin oder den Schüler schreiben.
4. **Annehmen** – oder **Zur Überarbeitung zurückgeben**: Die Antwort bleibt im Editor der Schülerin erhalten und kann verbessert werden. Mit *Aufgabe danach endgültig sperren* gibt es keine weitere Abgabe.

**Peer-Review** (im Editor bei Praxisaufgaben: *Peer-Review* ankreuzen, mitgeliefert bei „Deine Stadt im Netz“): Nach der eigenen Abgabe bewertet jede Person bis zu zwei Abgaben anderer aus dem eigenen Kurs – anonym, nach derselben Rubrik, mit optionalem Kommentar. Zugeteilt werden bevorzugt Abgaben, die noch wenig Rückmeldung haben. Die bewertete Person sieht Punkte und Kommentare ohne Namen. In der Abgabe siehst du die Peer-Bewertungen mit Namen und Durchschnitt als Hilfe; Punkte vergibst weiterhin nur du.

Alle anderen Typen bewertet CodePath sofort und ausschließlich auf dem Server.

## ◼ 4. Nach dem Kurs

### Teilnahmebestätigungen

Im Control Center unter *Zeugnisfunktion* die Bestätigungen **freigeben**: Schülerinnen und Schüler laden ihre PDF dann im Profil herunter. Unter **Kurse & Konten** lädt die Lehrkraft alle Bestätigungen eines Kurses auf einmal herunter – als **ZIP**, als **eine PDF** oder direkt zum **Drucken**; ebenso die **Mitnehm-Pakete** der ganzen Klasse. Mit **beidseitig** beginnt beim Duplexdruck jede Person auf einem neuen Blatt.

### Feedback einholen und bewerten

Unter **Leitstand → Feedback & Bewertung** gibt es zwei Wege:

- **Bewertungsrunden** – strukturierte Bögen je Kurs. **Neue Runde** übernimmt die mitgelieferten Vorlagen für drei Teile, die einzeln an- und abschaltbar sind:
  - **Selbsteinschätzung** – die Schülerinnen und Schüler schätzen ihre Arbeit ein.
  - **Unterrichtsbewertung** – die Klasse bewertet den Unterricht, standardmäßig **anonym** (du siehst die Antworten ohne Namen).
  - **Bewertung durch die Lehrkraft** – du bewertest jede Person, wahlweise nach denselben Kriterien wie die Selbsteinschätzung (direkter Vergleich) oder nach eigenen.

  Je Runde legst du fest, ob die Klasse deine Bewertung **nicht**, **sofort** oder **erst nach der eigenen Selbsteinschätzung** sieht (so bleibt die Selbsteinschätzung unbeeinflusst), ob abgeschickte Bögen bis zum Schluss geändert werden dürfen und optional eine Frist. Eine neue Runde ist ein **Entwurf**; erst **Öffnen** macht sie unter *Feedback* sichtbar, **Schließen** lässt nur noch lesen. Unter **Schüler bewerten** gehst du die Klasse mit „Speichern & weiter“ durch; die Selbsteinschätzung steht jeweils unter deiner Bewertung. Die **Auswertung** zeigt Verteilungen, Mittelwerte, Freitexte und je Person Selbst- und Fremdbild (große Abweichungen sind markiert); beides lässt sich als CSV herunterladen. Als **PDF** gibt es den Bogen jeder Person (in der Klassenliste oder beim Bewerten), die Klassenauswertung und oben in der Runde alles auf einmal – als **ZIP**, als **eine PDF** oder direkt zum **Drucken** – praktisch für Elterngespräche oder die Akte, bevor der Kurs beendet wird. Schülerinnen und Schüler laden ihren eigenen Bogen ebenfalls als PDF; deine Bewertung steht darin nur, wenn sie für sie sichtbar ist.
- **Vorlagen** – eigene Bögen bauen: Skalen (Zahlen, Beschriftung je Stufe oder Sterne), Einfach- und Mehrfachauswahl, Ja/Nein, Freitext, Zwischenüberschriften, Pflichtfragen, Vorschau. Kriterien mit gleicher ID in Selbsteinschätzung und Lehrerbewertung werden verglichen. Vorlagen gehen beim **Import & Export** mit, Runden und Antworten nicht.
- **Nachrichten** – die freie Feedback-Nachricht wie bisher: Runde starten, jede Person kann einmal schreiben.

### Mitnehm-Paket

Im Profil laden Schülerinnen und Schüler ihr **Mitnehm-Paket** als PDF herunter: Ergebnisse je Aufgabe, eigener Code (letzte Abgabe bei Python-, SQL-, Terminal- und Praxisaufgaben), **Berufe-Kompass** und Links zum Weiterlernen. Das PDF entsteht beim Herunterladen und wird nicht gespeichert – auch nicht im Protokoll. Die Klasse vor dem Kursende daran erinnern.

Der **Berufe-Kompass** ordnet jedem Modul ein Berufsfeld zu und zeigt die drei Felder, in denen jemand anteilig die meisten Punkte geholt hat. Berufsfeld und Links pflegt die Lehrkraft unter **Inhalte → Modul bearbeiten**.

### Kurs beenden

**Kurse & Konten → Kurs beenden** löscht **alle Schülerkonten des Kurses mit Fortschritt, Versuchen, Abgaben, Hilfe-Rufen und Punkten**. Das ist aus Datenschutzgründen so gewollt und lässt sich nicht rückgängig machen – vorher die Bestätigungen exportieren. Der Beitrittscode ist danach ungültig.

## ◼ 5. Inhalte bearbeiten

**Leitstand → Inhalte** zeigt alle Module und Aufgaben. Änderungen werden ohne Neustart wirksam.

### Aufgabe bearbeiten

1. Aufgabe öffnen. Links stehen die Felder, rechts die **Vorschau** genau so, wie die Klasse sie sieht – mit Probebewertung beliebiger Antworten, ohne dass etwas gespeichert wird.
2. Texte sind Markdown (`**fett**`, `` `Code` ``, Listen, Tabellen). Mit **Bild in die Geschichte einfügen** kommt ein Bild hinzu (PNG, JPEG, GIF, WebP bis 2 MB).
3. **Entwurf speichern** – die Klasse sieht weiter die veröffentlichte Version.
4. **Prüfen** – CodePath kontrolliert die Konfiguration und macht einen Probelauf: Die Musterlösung muss als richtig, eine falsche Antwort als falsch bewertet werden.
5. **Veröffentlichen** – ab jetzt gilt die neue Version. Bisherige Versuche bleiben an der Version, mit der sie entstanden sind; niemandem gehen Punkte verloren.

Unter *Versionen* lässt sich jede frühere Fassung als Entwurf zurückholen.

**Python-Werkbank für Praxisaufgaben:** Im Formular einer Praxisaufgabe *Python-Werkbank anzeigen* ankreuzen. Die Klasse bekommt dann zusätzlich einen Python-Editor mit Eingabefeld und kann auch größere Programme im Browser ausführen (bis 10 Sekunden). Optional einen *Startcode* vorgeben und festlegen, dass der Code mit abgegeben werden muss.

### Reihenfolge ändern

In der Modulliste Aufgaben am Griff **⠿** an die neue Stelle ziehen oder mit **▲ ▼** verschieben – wird Aufgabe 3 an Stelle 10 gezogen, ist sie danach Aufgabe 10. Die Reihenfolge gilt sofort für alle Kurse und bleibt bei *Mitgelieferte Inhalte übernehmen* und Importen erhalten.

### Neue Aufgabe

In der Modulliste unter *Neue Aufgabe* Titel, Slug (Kurzname für die Adresse, z. B. `passwort-check`) und Typ wählen und **Anlegen**. CodePath füllt eine Vorlage des Typs aus, die sofort geprüft und angepasst werden kann. Neue Module entstehen über *Modul anlegen*.

### Archivieren und löschen

| Aktion | Wirkung | Rückgängig? |
|---|---|---|
| **Archivieren** | Die Aufgabe verschwindet für die Klasse. Versuche, Ergebnisse und Punkte bleiben erhalten. | Ja, *Wiederherstellen* |
| **Endgültig löschen** (nur archivierte Aufgaben) | Entfernt die Aufgabe mit allen Versionen, Versuchen, Fortschritten und **Punkten** – die Rangliste ändert sich. CodePath zeigt vorher, was verloren geht; bestätigt wird durch Eintippen des Slugs. | Nein |

Gelöschte mitgelieferte Aufgaben kommen auch bei Updates nicht wieder – außer über einen bewussten ZIP-Import.

### Austausch und Sicherung

**Leitstand → Import & Export** sichert Einstellungen, Startseite, Seiten, Wissensdatenbank, CodePaths sowie Module und Aufgaben (mit Bildern) in **einer** ZIP-Datei – alles oder nur ausgewählte Bereiche. Einzelne Bereiche und einzelne CodePaths lassen sich auch in ihren Editoren exportieren. Schülerdaten sind nie enthalten.

Der **zentrale Import** erkennt die Datei selbst (CodePath-Export als ZIP oder JSON, oder ein Content-Paket) und zeigt danach für jeden Eintrag, ob er **angelegt**, **überarbeitet** (gab es schon) oder **unverändert** gelassen wurde – oder warum ein **Fehler** auftrat. Ein fehlerhafter Eintrag hält die übrigen nicht auf. Gleiche Adresse (Slug) heißt „derselbe Eintrag“; gelöscht wird nichts.

- **Export (ZIP)** unter Inhalte speichert nur Module und Aufgaben.
- **Import** unter Inhalte nimmt dieselben Dateien wie der zentrale Import; geänderte Aufgaben werden zu neuen Versionen.
- **Mitgelieferte Inhalte übernehmen** holt nach einem Update die überarbeiteten Startinhalte in die Datenbank.

Das Format beschreibt [`content/README.md`](../content/README.md).

## ◼ Häufige Fragen

**Eine Schülerin hat ihr Passwort vergessen.** Schülerakte öffnen → *Passwort überschreiben*.

**Eine Aufgabe ist fehlerhaft und alle verlieren Versuche.** Korrigieren und neu veröffentlichen; in der Schülerakte lassen sich Aufgaben einzeln als gelöst werten oder entsperren.

**Code-Aufgaben zeigen „Übung ohne Punkte“.** Auf diesem Server ist kein Code-Runner eingerichtet; Code-Aufgaben lassen sich üben, zählen aber nicht. Die IT-Betreuung kann ihn einschalten ([Betriebshandbuch](betrieb.md#-code-runner)).

**Kann die Klasse von zu Hause weiterarbeiten?** Ja, sofern der Server von außen erreichbar ist (siehe [Betriebshandbuch](betrieb.md#-https-und-erreichbarkeit)). Freigaben und Fristen gelten überall.
