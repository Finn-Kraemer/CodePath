import { browser } from '$app/environment';

/** Ab wann eine Anfrage als „hängt“ gilt. */
export const SLOW_REQUEST_MS = 4000;

/**
 * Anfragen, die absichtlich lange dauern (Code ausführen und prüfen, Inhalte importieren).
 * Sie haben eigene Ladeanzeigen und lösen den Ladebildschirm nicht aus.
 */
const LONG_RUNNING = [/\/check$/, /\/submit$/, /\/run-solution$/, /\/import/];

/**
 * Verbindungszustand für den Ladebildschirm: Netzwerk weg (Browser meldet offline)
 * oder Anfragen an die API, die ungewöhnlich lange brauchen.
 */
class ConnectionStore {
	online = $state(true);
	private slowCount = $state(0);

	get slow() {
		return this.slowCount > 0;
	}

	constructor() {
		if (!browser) return;
		this.online = navigator.onLine;
		window.addEventListener('online', () => (this.online = true));
		window.addEventListener('offline', () => (this.online = false));
	}

	/** Meldet eine laufende Anfrage an; die zurückgegebene Funktion meldet sie wieder ab. */
	begin(url: string): () => void {
		if (!browser || LONG_RUNNING.some((pattern) => pattern.test(url.split('?')[0]))) {
			return () => {};
		}
		let counted = false;
		const timer = setTimeout(() => {
			counted = true;
			this.slowCount++;
		}, SLOW_REQUEST_MS);
		return () => {
			clearTimeout(timer);
			if (counted) this.slowCount--;
		};
	}
}

export const connection = new ConnectionStore();
