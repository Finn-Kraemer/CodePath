import { auth } from '$lib/auth.svelte';
import type { components } from './schema';

/** Aus der OpenAPI-Beschreibung des Backends erzeugt (pnpm gen:api). */
export type Schemas = components['schemas'];
export type TaskDetail = Schemas['TaskDetail'];
export type TaskSummary = Schemas['TaskSummary'];
export type ModuleSummary = Schemas['ModuleSummary'];
export type ModuleDetail = Schemas['ModuleDetail'];
export type SubmitResult = Schemas['SubmitResult'];
export type HintResult = Schemas['HintResult'];
export type Leaderboard = Schemas['Leaderboard'];
export type Profile = Schemas['Profile'];
export type CourseSummary = Schemas['CourseSummary'];
export type ModuleReleaseView = Schemas['ModuleReleaseView'];
export type TaskReleaseView = Schemas['TaskReleaseView'];
export type StudentOverview = Schemas['StudentOverview'];
export type StudentTask = Schemas['StudentTask'];
export type SubmissionSummary = Schemas['SubmissionSummary'];
export type SubmissionDetail = Schemas['SubmissionDetail'];
export type TaskStatistic = Schemas['TaskStatistic'];
export type AttemptView = Schemas['AttemptView'];
export type MediaInfo = Schemas['MediaInfo'];

export class ApiError extends Error {
	constructor(
		message: string,
		readonly status: number
	) {
		super(message);
	}
}

/** JSON-Anfrage an die API; Fehler kommen als ApiError mit der Meldung des Servers. */
export async function api<T>(
	url: string,
	options: { method?: string; body?: unknown } = {}
): Promise<T> {
	const res = await auth.apiFetch(url, {
		method: options.method ?? 'GET',
		body: options.body === undefined ? undefined : JSON.stringify(options.body)
	});
	const text = await res.text();
	const data = text ? JSON.parse(text) : undefined;
	if (!res.ok) {
		throw new ApiError(data?.message ?? data?.detail ?? `Fehler ${res.status}`, res.status);
	}
	return data as T;
}
