import { ApiError, type TransferReport } from '$lib/api';
import { auth } from '$lib/auth.svelte';

/** Download-Adresse des zentralen Exports; ohne Bereiche alles, mit only einzelne Einträge (Slugs). */
export function exportUrl(sections: string[] = [], only: string[] = []): string {
	const params = new URLSearchParams();
	if (sections.length) params.set('sections', sections.join(','));
	if (only.length) params.set('only', only.join(','));
	const query = params.toString();
	return '/api/admin/transfer/export' + (query ? `?${query}` : '');
}

/** Zentraler Import: Der Server erkennt selbst, ob es ein CodePath-Export oder ein Content-Paket ist. */
export async function importFile(file: File): Promise<TransferReport> {
	const form = new FormData();
	form.append('file', file);
	const res = await fetch('/api/admin/transfer/import', {
		method: 'POST',
		body: form,
		headers: { 'X-XSRF-TOKEN': auth.getCsrfToken() ?? '' },
		credentials: 'include'
	});
	const data = await res.json().catch(() => ({}));
	if (!res.ok)
		throw new ApiError(data?.message ?? data?.detail ?? `Fehler ${res.status}`, res.status);
	return data as TransferReport;
}

export const ACTION_LABELS: Record<string, string> = {
	CREATED: 'Angelegt',
	UPDATED: 'Überarbeitet',
	UNCHANGED: 'Unverändert',
	ERROR: 'Fehler'
};
