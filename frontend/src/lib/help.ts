import { draftKey } from '$lib/tasks/types';
import { lastOutput, loadDraft } from '$lib/drafts';
import type { TaskDetail } from '$lib/api';

/** Schnellauswahl beim Hilfe-Ruf (Werte wie im Backend `HelpRequest.Category`) */
export const HELP_CATEGORIES = [
	{ value: 'TASK_UNCLEAR', label: 'Verstehe die Aufgabe nicht' },
	{ value: 'ERROR_MESSAGE', label: 'Fehlermeldung' },
	{ value: 'TECHNICAL', label: 'Technisches Problem' },
	{ value: 'OTHER', label: 'Etwas anderes' }
] as const;

export const HELP_CATEGORY_LABELS: Record<string, string> = Object.fromEntries(
	HELP_CATEGORIES.map((c) => [c.value, c.label])
);

/** Höchstens so viele Zeichen gehen mit (wie im Backend) */
export const MAX_DRAFT = 20_000;

/** HTML der Praxisabgabe als reiner Text – über den DOM-Parser des Browsers, nicht per Regex. */
function stripHtml(html: string): string {
	const doc = new DOMParser().parseFromString(html, 'text/html');
	doc.querySelectorAll('br').forEach((br) => br.replaceWith('\n'));
	doc.querySelectorAll('p, div, li, h1, h2, h3, h4, h5, h6').forEach((block) => block.append('\n'));
	return (doc.body.textContent ?? '').replace(/\n{3,}/g, '\n\n').trim();
}

/** Stand einer Antwort als lesbarer Text für die Lehrkraft (Code, SQL, Lücken, Text). */
export function draftText(value: unknown): string {
	if (value === null || value === undefined) return '';
	if (typeof value === 'string') return /<\/?[a-z][^>]*>/i.test(value) ? stripHtml(value) : value;
	if (Array.isArray(value)) {
		return value
			.map((item, i) => `Lücke ${i + 1}: ${typeof item === 'string' ? item : JSON.stringify(item)}`)
			.join('\n');
	}
	if (typeof value === 'object') {
		const record = value as Record<string, unknown>;
		for (const key of ['code', 'sql', 'query']) {
			if (typeof record[key] === 'string') return record[key] as string;
		}
		if (Array.isArray(record.answers)) return draftText(record.answers);
		if (typeof record.content === 'string') return draftText(record.content);
		return JSON.stringify(value, null, 2);
	}
	return String(value);
}

/**
 * Aktueller Stand der Antwort für den Hilfe-Ruf: lokaler Entwurf, sonst die letzte Abgabe.
 * Bei Praxisaufgaben kommen Text und Programm zusammen.
 */
export function currentDraft(task: TaskDetail): { draft: string; lastOutput: string } {
	const key = draftKey(task);
	const parts = [draftText(loadDraft<unknown>(key) ?? task.lastAnswer ?? null)];
	const code = loadDraft<string>(`${key}:code`);
	if (code) parts.push(`# Programm\n${code}`);
	const draft = parts.filter((p) => p.trim()).join('\n\n');
	return { draft: draft.slice(0, MAX_DRAFT), lastOutput: lastOutput(key).slice(0, MAX_DRAFT) };
}

/** Wartezeit kurz und lesbar, z. B. „3 Min.“ */
export function waitingTime(seconds: number): string {
	if (seconds < 60) return 'gerade eben';
	const minutes = Math.floor(seconds / 60);
	if (minutes < 60) return `${minutes} Min.`;
	return `${Math.floor(minutes / 60)} Std. ${minutes % 60} Min.`;
}
