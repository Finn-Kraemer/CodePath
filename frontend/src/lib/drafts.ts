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

/**
 * Letzte Ausgabe bzw. Fehlermeldung aus „Ausprobieren“ – nur im Speicher dieser Seite, damit ein
 * Hilfe-Ruf sie mitschicken kann. Wird nie gespeichert oder automatisch übertragen.
 */
const outputs = new Map<string, string>();

export function rememberOutput(key: string, text: string) {
	outputs.set(key, text);
}

export function lastOutput(key: string): string {
	return outputs.get(key) ?? '';
}
