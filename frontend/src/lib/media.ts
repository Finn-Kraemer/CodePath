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
