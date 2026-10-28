/**
 * Entwürfe lokal zwischenspeichern, damit ein kurzer WLAN-Ausfall keine Eingabe verliert.
 * Der Browser-Speicher kann fehlen (privater Modus) – dann einfach nichts tun.
 */
const PREFIX = 'codepath:draft:';

export function loadDraft<T>(key: string): T | null {
	try {
		const raw = localStorage.getItem(PREFIX + key);
		return raw ? (JSON.parse(raw) as T) : null;
	} catch {
		return null;
	}
}

export function saveDraft(key: string, value: unknown) {
	try {
		localStorage.setItem(PREFIX + key, JSON.stringify(value));
	} catch {
		// Speicher voll oder gesperrt
	}
}

export function clearDraft(key: string) {
	try {
		localStorage.removeItem(PREFIX + key);
	} catch {
		// ignorieren
	}
}
