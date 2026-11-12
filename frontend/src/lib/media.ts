import { ApiError, type MediaInfo } from '$lib/api';
import { auth } from '$lib/auth.svelte';

export function mediaUrl(key: string): string {
	return `/api/media/${key}`;
}

/** Lädt ein Bild hoch (nur Lehrkraft). Gleiche Dateien bekommen denselben Schlüssel. */
export async function uploadMedia(file: File): Promise<MediaInfo> {
	const form = new FormData();
	form.append('file', file);
	const res = await fetch('/api/admin/media', {
		method: 'POST',
		body: form,
		headers: { 'X-XSRF-TOKEN': auth.getCsrfToken() ?? '' },
		credentials: 'include'
	});
	const data = await res.json().catch(() => ({}));
	if (!res.ok) throw new ApiError(data?.message ?? `Fehler ${res.status}`, res.status);
	return data as MediaInfo;
}

/** Arten von Stellen, an denen ein Bild verwendet werden kann (siehe MediaReference.kind). */
export const mediaKinds = {
	TASK: 'Aufgaben',
	HOMEPAGE: 'Startseite',
	PAGE: 'Seiten',
	KNOWLEDGE: 'Wissensdatenbank',
	WORKSHEET: 'Aufgabenblätter'
} as const;

export function formatBytes(bytes: number): string {
	if (bytes < 1024) return `${bytes} B`;
	if (bytes < 1024 * 1024)
		return `${(bytes / 1024).toLocaleString('de-DE', { maximumFractionDigits: 0 })} KB`;
	return `${(bytes / 1024 / 1024).toLocaleString('de-DE', { maximumFractionDigits: 1 })} MB`;
}
