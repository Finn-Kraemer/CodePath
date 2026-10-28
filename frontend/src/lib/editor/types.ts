import type { Schemas } from '$lib/api';

export type TaskFile = Schemas['TaskFile'];
export type TaskEditorView = Schemas['TaskEditorView'];
export type Config = Record<string, any>;

/** Zeilenweise Liste ↔ Array (leere Zeilen fallen weg). */
export function lines(value: string): string[] {
	return value
		.split('\n')
		.map((l) => l.trim())
		.filter(Boolean);
}

export function nextId(existing: { id: string }[], prefix: string): string {
	let n = existing.length + 1;
	while (existing.some((e) => e.id === `${prefix}${n}`)) n++;
	return `${prefix}${n}`;
}
