# ADR-1: Modularer Monolith ohne Message Broker

**Status:** Akzeptiert, umgesetzt in Phase 1

**Kontext:** 90 Schüler, höchstens 5 Abgaben pro Sekunde, eine Person im Betrieb. Die asynchrone Punktevergabe über RabbitMQ öffnet ein Zeitfenster, in dem die Antwort „richtig“ lautet, der Fortschritt aber noch nicht gespeichert ist.

**Entscheidung:** Eine Spring-Boot-Anwendung mit Package-Grenzen; Abgabe, Bewertung und Punktevergabe laufen synchron in einer Datenbanktransaktion.

**Alternativen:** RabbitMQ behalten (Komplexität ohne Lastproblem); Microservices (für eine Person nicht wartbar).

**Konsequenzen:** Zwei Container statt sechs, einfachere Tests. Echte Lastspitzen würden mit Connection-Pool und Indizes gelöst, nicht mit einem Broker.
