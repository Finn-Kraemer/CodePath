export const TYPE_LABELS: Record<string, string> = {
	MULTIPLE_CHOICE: 'Auswahl',
	FILL_BLANK: 'Lückentext',
	FILL_CODE: 'Code-Lücken',
	CODE: 'Programmieren',
	SORTING: 'Sortieren',
	SQL: 'SQL',
	TERMINAL: 'Terminal',
	PRACTICE: 'Praxis',
	MATCHING: 'Zuordnen',
	PARSONS: 'Code-Puzzle',
	NUMBER_CONVERT: 'Zahlensysteme',
	HOTSPOT: 'Markieren'
};

export const DIFFICULTY_LABELS: Record<string, string> = {
	EASY: 'Leicht',
	MEDIUM: 'Mittel',
	HARD: 'Schwer'
};

export const STATUS_LABELS: Record<string, string> = {
	OPEN: 'Offen',
	SOLVED: 'Gelöst',
	PENDING_REVIEW: 'In Prüfung',
	FAILED: 'Keine Versuche mehr',
	PRACTICED: 'Geübt'
};
