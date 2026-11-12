-- Bewertungsbögen: Selbsteinschätzung, Unterrichtsbewertung und Bewertung durch die Lehrkraft.
-- Vorlagen sind wiederverwendbar; eine Runde kopiert die Kriterien, damit spätere Änderungen an
-- einer Vorlage laufende Runden nicht verändern.
CREATE TABLE evaluation_templates (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(80)  NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    kind        VARCHAR(20)  NOT NULL,
    criteria    JSONB        NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE evaluation_rounds (
    id                         BIGSERIAL PRIMARY KEY,
    course_id                  BIGINT       NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    title                      VARCHAR(200) NOT NULL,
    intro                      TEXT,
    status                     VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    self_criteria              JSONB,
    lesson_criteria            JSONB,
    teacher_criteria           JSONB,
    teacher_uses_self_criteria BOOLEAN      NOT NULL DEFAULT FALSE,
    lesson_anonymous           BOOLEAN      NOT NULL DEFAULT TRUE,
    teacher_visibility         VARCHAR(20)  NOT NULL DEFAULT 'HIDDEN',
    allow_edit                 BOOLEAN      NOT NULL DEFAULT TRUE,
    closes_at                  TIMESTAMP,
    created_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_evaluation_rounds_course ON evaluation_rounds (course_id);

-- subject_id: um wen es geht (bei Selbsteinschätzung und Unterrichtsbewertung die ausfüllende Person,
-- bei der Lehrerbewertung die bewertete Person). Je Runde, Teil und Person genau ein Bogen.
CREATE TABLE evaluation_responses (
    id           BIGSERIAL PRIMARY KEY,
    round_id     BIGINT      NOT NULL REFERENCES evaluation_rounds (id) ON DELETE CASCADE,
    part         VARCHAR(20) NOT NULL,
    subject_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    author_id    BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    answers      JSONB       NOT NULL,
    submitted_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_evaluation_responses UNIQUE (round_id, part, subject_id)
);

-- Mitgelieferte Vorlagen. Selbsteinschätzung und Lehrerbewertung teilen die Kriterien-IDs,
-- damit beide Sichten nebeneinander verglichen werden können.
INSERT INTO evaluation_templates (slug, name, description, kind, criteria) VALUES
('selbsteinschaetzung', 'Selbsteinschätzung', 'Wie schätzt du deine Arbeit im Kurs ein?', 'SELF', '[
  {"id": "h-arbeit", "type": "HEADING", "label": "Arbeitsweise"},
  {"id": "konzentration", "type": "SCALE", "label": "Ich habe konzentriert gearbeitet.", "required": true, "min": 1, "max": 5, "minLabel": "trifft nicht zu", "maxLabel": "trifft voll zu"},
  {"id": "selbststaendig", "type": "SCALE", "label": "Ich habe Aufgaben selbstständig gelöst.", "required": true, "min": 1, "max": 5, "minLabel": "trifft nicht zu", "maxLabel": "trifft voll zu"},
  {"id": "zusammenarbeit", "type": "SCALE", "label": "Ich habe anderen geholfen und mir bei Problemen Hilfe geholt.", "required": true, "min": 1, "max": 5, "minLabel": "trifft nicht zu", "maxLabel": "trifft voll zu"},
  {"id": "h-lernen", "type": "HEADING", "label": "Lernerfolg"},
  {"id": "verstaendnis", "type": "SCALE", "label": "Ich habe die Inhalte verstanden.", "required": true, "min": 1, "max": 5, "minLabel": "kaum", "maxLabel": "vollständig"},
  {"id": "zufriedenheit", "type": "SCALE", "style": "STARS", "label": "Wie zufrieden bist du mit deinem Lernfortschritt?", "required": true, "min": 1, "max": 5},
  {"id": "gelungen", "type": "TEXT", "label": "Was ist dir gut gelungen?", "maxLength": 1000},
  {"id": "ziele", "type": "TEXT", "label": "Woran möchtest du weiterarbeiten?", "maxLength": 1000}
]'::jsonb),
('unterrichtsbewertung', 'Unterrichtsbewertung', 'Deine Meinung zum Unterricht – ehrlich und konstruktiv.', 'LESSON', '[
  {"id": "erklaerungen", "type": "SCALE", "label": "Die Erklärungen waren verständlich.", "required": true, "min": 1, "max": 5, "minLabel": "trifft nicht zu", "maxLabel": "trifft voll zu"},
  {"id": "tempo", "type": "SCALE", "label": "Wie war das Tempo?", "required": true, "min": 1, "max": 5, "labels": ["viel zu langsam", "etwas zu langsam", "genau richtig", "etwas zu schnell", "viel zu schnell"]},
  {"id": "aufgaben", "type": "SCALE", "label": "Die Aufgaben waren abwechslungsreich und passend schwer.", "required": true, "min": 1, "max": 5, "minLabel": "trifft nicht zu", "maxLabel": "trifft voll zu"},
  {"id": "hilfreich", "type": "CHOICE", "label": "Was hat dir beim Lernen am meisten geholfen?", "multiple": true, "options": ["Erklärungen der Lehrkraft", "Aufgaben auf der Plattform", "Hinweise und Wissensartikel", "Arbeit mit anderen", "Quiz-Runden"]},
  {"id": "empfehlung", "type": "YES_NO", "label": "Würdest du den Kurs weiterempfehlen?", "required": true},
  {"id": "gesamt", "type": "SCALE", "style": "STARS", "label": "Gesamteindruck", "required": true, "min": 1, "max": 5},
  {"id": "aendern", "type": "TEXT", "label": "Was sollten wir ändern?", "maxLength": 2000}
]'::jsonb),
('lehrerbewertung', 'Bewertung durch die Lehrkraft', 'Rückmeldung der Lehrkraft zu Arbeitsweise und Lernerfolg.', 'TEACHER', '[
  {"id": "h-arbeit", "type": "HEADING", "label": "Arbeitsweise"},
  {"id": "konzentration", "type": "SCALE", "label": "Arbeitet konzentriert.", "required": true, "min": 1, "max": 5, "minLabel": "trifft nicht zu", "maxLabel": "trifft voll zu"},
  {"id": "selbststaendig", "type": "SCALE", "label": "Löst Aufgaben selbstständig.", "required": true, "min": 1, "max": 5, "minLabel": "trifft nicht zu", "maxLabel": "trifft voll zu"},
  {"id": "zusammenarbeit", "type": "SCALE", "label": "Hilft anderen und holt sich bei Problemen Hilfe.", "required": true, "min": 1, "max": 5, "minLabel": "trifft nicht zu", "maxLabel": "trifft voll zu"},
  {"id": "h-lernen", "type": "HEADING", "label": "Lernerfolg"},
  {"id": "verstaendnis", "type": "SCALE", "label": "Hat die Inhalte verstanden.", "required": true, "min": 1, "max": 5, "minLabel": "kaum", "maxLabel": "vollständig"},
  {"id": "staerken", "type": "TEXT", "label": "Stärken", "maxLength": 2000},
  {"id": "tipps", "type": "TEXT", "label": "Tipps für die nächsten Schritte", "maxLength": 2000}
]'::jsonb);
