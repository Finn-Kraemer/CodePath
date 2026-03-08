-- V9__update_system_settings_value_type.sql

ALTER TABLE system_settings ALTER COLUMN value TYPE TEXT;

INSERT INTO system_settings (key, value) VALUES ('frontpage_config', '{
  "hero": {
    "badge": "IT-Projekttag 2026",
    "title": "The Path to Code",
    "description": "Willkommen zu deinem Deep-Dive in die Welt der Informationstechnik. Heute lernst du nicht aus Büchern, sondern arbeitest direkt an den Systemen, die unsere digitale Welt antreiben."
  },
  "teacher": {
    "badge": "Deine Lehrperson",
    "title": "Expertise aus der Praxis",
    "quote": "In der IT zählt nicht, was man auswendig gelernt hat, sondern wie kreativ und analytisch man Probleme löst. Mein Ziel für heute ist es, euch die Werkzeuge an die Hand zu geben, mit denen ihr reale Herausforderungen meistern könnt.",
    "description": "Als Dozent mit langjähriger Erfahrung in der Softwareentwicklung und Systemarchitektur begleite ich euch heute durch den IT-Projekttag. Mein Fokus liegt darauf, die Brücke zwischen akademischen Konzepten und der industriellen Anwendung zu schlagen. Ich stehe euch den ganzen Tag für Fragen, Feedback und technisches Mentoring zur Verfügung.",
    "emoji": "👨‍🏫",
    "role": "Dozent für Fachinformatik"
  },
  "agenda": {
    "badge": "Die Agenda",
    "title": "Veranstaltungs-Übersicht",
    "description": "Dieser Projekttag ist als Intensiv-Workshop konzipiert. Wir decken die gesamte Breite der modernen Informationstechnik ab – von der Hardware-Ebene bis zur Cloud-Infrastruktur.",
    "items": [
      { "emoji": "💾", "title": "Grundlagen & Logik", "description": "Verständnis für binäre Systeme, digitale Logikgatter und die Architektur von Prozessoren als Basis jeder Software." },
      { "emoji": "🛠️", "title": "Software Engineering", "description": "Einstieg in Python. Wir lösen reale Probleme durch Algorithmen und lernen saubere Code-Strukturen kennen." },
      { "emoji": "🔒", "title": "Cyber Security", "description": "Sicherheitslücken identifizieren, Kryptografie anwenden und verstehen, wie man Systeme gegen Angriffe härtet." }
    ]
  },
  "timeline": {
    "badge": "Chronologie",
    "title": "Geplanter Ablauf",
    "steps": [
      { "time": "08:30 – 09:00", "title": "Check-in & System-Setup", "description": "Registrierung auf der CodePath-Plattform, Einführung in die Benutzeroberfläche und Vorbereitung der Entwicklungsumgebungen.", "number": "01" },
      { "time": "09:00 – 12:00", "title": "Block I: Core IT", "description": "Intensive Praxisphasen zu Algorithmen, Datenstrukturen und Hardware-Logik. Erste Punktejagd im Live-Leaderboard.", "number": "02" },
      { "time": "12:00 – 13:00", "title": "Netzwerk-Pause", "description": "Gemeinsames Mittagessen und informeller Austausch über Karrierewege in der Informationstechnik.", "emoji": "☕" },
      { "time": "13:00 – 15:30", "title": "Block II: Advanced Topics", "description": "Web-Architekturen, SQL-Injection-Analysen und Cloud-Integration. Finale der praktischen Leistungsnachweise.", "number": "03" },
      { "time": "15:30 – 16:00", "title": "Debriefing & Awarding", "description": "Zusammenfassung der Lernergebnisse, Siegerehrung für die Punktbesten und Ausblick auf weiterführende IT-Qualifikationen.", "emoji": "🏆" }
    ]
  }
}');
