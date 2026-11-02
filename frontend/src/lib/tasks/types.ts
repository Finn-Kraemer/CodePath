import type { SubmitResult, TaskDetail } from '$lib/api';
import { DEFAULT_MAX_STEPS } from '$lib/visualizer/trace';

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

/**
 * Code-Visualizer („Schritt für Schritt“) für diese Aufgabe? Abschaltbar mit `config.visualizer: false`,
 * im Abschlusstest standardmäßig aus. `visualizerMaxSteps` setzt das Schrittlimit.
 */
export function visualizerSettings(task: TaskDetail): { enabled: boolean; maxSteps: number } {
	const cfg = config<{ visualizer?: boolean; visualizerMaxSteps?: number }>(task);
	const enabled =
		typeof cfg.visualizer === 'boolean' ? cfg.visualizer : task.moduleSlug !== 'abschlusstest';
	const maxSteps =
		typeof cfg.visualizerMaxSteps === 'number' && cfg.visualizerMaxSteps > 0
			? Math.min(cfg.visualizerMaxSteps, 5000)
			: DEFAULT_MAX_STEPS;
	return { enabled, maxSteps };
}
