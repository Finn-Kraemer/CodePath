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
export type PageSummary = Schemas['PageSummary'];
export type NavLink = Schemas['NavLink'];
export type ArticleAdmin = Schemas['ArticleAdmin'];
export type ArticleSummary = Schemas['ArticleSummary'];
export type ArticleView = Schemas['ArticleView'];
export type SearchHit = Schemas['SearchHit'];
export type Compass = Schemas['Compass'];
export type CompassEntry = Schemas['CompassEntry'];
export type Recommendation = Schemas['Recommendation'];
export type PathView = Schemas['PathView'];
export type PathLink = Schemas['PathLink'];
export type PathSummary = Schemas['PathSummary'];
export type PathAdmin = Schemas['PathAdmin'];
export type PathGraph = Schemas['Graph'];
export type PathNode = Schemas['Node'];
export type NodeView = Schemas['NodeView'];
export type PathEdge = Schemas['Edge'];
export type EdgeCondition = Schemas['EdgeCondition'];
export type GateRequirement = Schemas['GateRequirement'];
export type GateProgress = Schemas['GateProgress'];
export type GateStudentStat = Schemas['GateStudentStat'];
export type OpenTaskView = Schemas['OpenTaskView'];
export type StudentPathAdminView = Schemas['StudentPathAdminView'];
export type StudentBranchEntry = Schemas['StudentBranchEntry'];
export type BranchConditionStats = Schemas['BranchConditionStats'];
export type TransferReport = Schemas['TransferReport'];
export type SectionInfo = Schemas['SectionInfo'];

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
