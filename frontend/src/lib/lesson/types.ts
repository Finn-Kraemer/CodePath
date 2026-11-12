/** Schritte eines Stundenablaufs (Backend: lesson.LessonStep). Felder je nach Art. */
export type StepType = 'RELEASE' | 'QUIZ' | 'WORKSHEET' | 'EVALUATION' | 'ANNOUNCEMENT' | 'NOTE';

export type Step = {
	id?: string;
	type: StepType;
	title?: string | null;
	minutes?: number | null;
	moduleSlug?: string | null;
	taskSlug?: string | null;
	action?: string | null;
	deadline?: string | null;
	taskIds?: number[] | null;
	roundId?: number | null;
	worksheetId?: number | null;
	text?: string | null;
	displayMode?: string | null;
};

export const STEP_TYPES: { type: StepType; label: string; hint: string }[] = [
	{
		type: 'RELEASE',
		label: 'Freigabe',
		hint: 'Modul oder Aufgabe freigeben bzw. sperren, Frist setzen'
	},
	{ type: 'QUIZ', label: 'Quiz-Runde', hint: 'Live-Quiz mit vorausgewählten Fragen starten' },
	{ type: 'WORKSHEET', label: 'Aufgabenblatt', hint: 'Blatt zum Drucken bereitlegen' },
	{ type: 'EVALUATION', label: 'Bewertungsrunde', hint: 'Runde öffnen oder schließen' },
	{ type: 'ANNOUNCEMENT', label: 'Live-Mitteilung', hint: 'Mitteilung an die Klasse senden' },
	{ type: 'NOTE', label: 'Notiz', hint: 'Hinweis nur für dich' }
];

export const TYPE_LABEL: Record<StepType, string> = Object.fromEntries(
	STEP_TYPES.map((t) => [t.type, t.label])
) as Record<StepType, string>;

export function emptyStep(type: StepType): Step {
	switch (type) {
		case 'RELEASE':
			return { type, moduleSlug: '', taskSlug: '', action: 'UNLOCK', deadline: '' };
		case 'QUIZ':
			return { type, text: 'Quiz', taskIds: [] };
		case 'EVALUATION':
			return { type, action: 'OPEN', roundId: null };
		case 'ANNOUNCEMENT':
			return { type, text: '', displayMode: 'HEADER' };
		default:
			return { type, text: '' };
	}
}

/** Verbleibende Zeit eines Schritts in Sekunden (negativ = überzogen), null ohne geplante Dauer. */
export function remaining(
	startedAt: string | null | undefined,
	minutes: number | null | undefined,
	now: number
) {
	if (!startedAt || !minutes) return null;
	return Math.round((new Date(startedAt).getTime() + minutes * 60_000 - now) / 1000);
}

export function clock(seconds: number) {
	const sign = seconds < 0 ? '−' : '';
	const s = Math.abs(seconds);
	return `${sign}${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}
