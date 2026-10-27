import type { SubmitResult, TaskDetail } from '$lib/api';

export type Answer = Record<string, unknown>;

/** Gemeinsame Props aller Aufgaben-Komponenten. Bewertet wird nur auf dem Server. */
export interface TaskProps {
	task: TaskDetail;
	/** Keine Abgabe möglich (gelöst, gesperrt, Frist vorbei, keine Versuche mehr) */
	disabled: boolean;
	busy: boolean;
	result: SubmitResult | null;
	submit: (answer: Answer) => Promise<void>;
}

export function config<T>(task: TaskDetail): T {
	return (task.config ?? {}) as T;
}

export function solution<T>(task: TaskDetail): T | null {
	return (task.solution ?? null) as T | null;
}

export function draftKey(task: TaskDetail) {
	return `${task.moduleSlug}/${task.slug}`;
}
