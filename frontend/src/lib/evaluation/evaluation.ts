import type { Schemas } from '$lib/api';

export type Criterion = Schemas['EvaluationCriterion'];
export type CriterionType = Criterion['type'];
export type Answers = Record<string, unknown>;
export type EvaluationPart = 'SELF' | 'LESSON' | 'TEACHER';
export type TeacherVisibility = 'HIDDEN' | 'VISIBLE' | 'AFTER_SELF';

export const PART_LABELS: Record<EvaluationPart, string> = {
	SELF: 'Selbsteinschätzung',
	LESSON: 'Unterrichtsbewertung',
	TEACHER: 'Bewertung durch die Lehrkraft'
};

export const TYPE_LABELS: Record<CriterionType, string> = {
	SCALE: 'Skala',
	CHOICE: 'Auswahl',
	YES_NO: 'Ja/Nein',
	TEXT: 'Freitext',
	HEADING: 'Zwischenüberschrift'
};

export const VISIBILITY_LABELS: Record<TeacherVisibility, { title: string; text: string }> = {
	HIDDEN: {
		title: 'Nicht sichtbar',
		text: 'Nur du siehst deine Bewertung.'
	},
	AFTER_SELF: {
		title: 'Nach der Selbsteinschätzung',
		text: 'Sichtbar, sobald die Person sich selbst eingeschätzt hat – so bleibt die Selbsteinschätzung unbeeinflusst.'
	},
	VISIBLE: {
		title: 'Sofort sichtbar',
		text: 'Sichtbar, sobald du bewertet hast.'
	}
};

/** Neues Kriterium mit sinnvollen Vorgaben je Typ. */
export function newCriterion(type: CriterionType, existing: Criterion[]): Criterion {
	const ids = new Set(existing.map((c) => c.id));
	let n = existing.length + 1;
	while (ids.has(`k${n}`)) n++;
	const base = { id: `k${n}`, type, label: '' } as Criterion;
	switch (type) {
		case 'SCALE':
			return { ...base, required: true, min: 1, max: 5, style: 'NUMBERS' };
		case 'CHOICE':
			return { ...base, required: false, options: ['Option 1', 'Option 2'], multiple: false };
		case 'TEXT':
			return { ...base, required: false, maxLength: 1000 };
		case 'YES_NO':
			return { ...base, required: true };
		default:
			return base;
	}
}

/** Kopie ohne geteilte Referenzen (Vorlage → Runde). */
export function cloneCriteria(criteria: Criterion[] | null | undefined): Criterion[] {
	return criteria ? (JSON.parse(JSON.stringify(criteria)) as Criterion[]) : [];
}

export function steps(c: Criterion): number[] {
	const min = c.min ?? 1;
	const max = c.max ?? 5;
	const result: number[] = [];
	for (let i = min; i <= max; i++) result.push(i);
	return result;
}

/** Beschriftung einer Stufe: eigene Beschriftung, sonst die Zahl. */
export function stepLabel(c: Criterion, value: number): string {
	const label = c.labels?.[value - (c.min ?? 1)];
	return label ? label : String(value);
}

function isEmpty(value: unknown): boolean {
	return (
		value === undefined ||
		value === null ||
		(typeof value === 'string' && value.trim() === '') ||
		(Array.isArray(value) && value.length === 0)
	);
}

/** Pflichtfragen ohne Antwort – damit das Formular vor dem Absenden darauf hinweisen kann. */
export function missingRequired(criteria: Criterion[], answers: Answers): Criterion[] {
	return criteria.filter((c) => c.type !== 'HEADING' && c.required && isEmpty(answers[c.id]));
}

/** Antwort als lesbarer Text (Auswertung, CSV, Vergleich). */
export function formatAnswer(c: Criterion, value: unknown): string {
	if (isEmpty(value)) return '–';
	switch (c.type) {
		case 'SCALE': {
			const n = Number(value);
			const label = c.labels?.[n - (c.min ?? 1)];
			return label ? `${n} (${label})` : `${n} / ${c.max ?? 5}`;
		}
		case 'YES_NO':
			return value ? 'Ja' : 'Nein';
		case 'CHOICE':
			return Array.isArray(value) ? value.join(', ') : String(value);
		default:
			return String(value);
	}
}

/** Kriterien, die in Selbsteinschätzung und Lehrerbewertung vorkommen (gleiche ID, beide Skalen). */
export function comparableIds(self: Criterion[] | null, teacher: Criterion[] | null): Set<string> {
	if (!self || !teacher) return new Set();
	const scales = new Set(self.filter((c) => c.type === 'SCALE').map((c) => c.id));
	return new Set(teacher.filter((c) => c.type === 'SCALE' && scales.has(c.id)).map((c) => c.id));
}

function csvCell(value: string): string {
	return /[";\n\r]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

/** CSV mit Semikolon (öffnet sich in deutschem Excel direkt in Spalten). */
export function toCsv(header: string[], rows: string[][]): string {
	return '﻿' + [header, ...rows].map((row) => row.map(csvCell).join(';')).join('\r\n');
}

export function download(filename: string, content: string, type = 'text/csv;charset=utf-8') {
	const url = URL.createObjectURL(new Blob([content], { type }));
	const a = document.createElement('a');
	a.href = url;
	a.download = filename;
	a.click();
	URL.revokeObjectURL(url);
}

export function formatDate(value: string | null | undefined): string {
	return value
		? new Date(value).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' })
		: '–';
}
