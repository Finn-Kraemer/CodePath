import { browser } from '$app/environment';

export interface Announcement {
	content: string;
	displayMode: string;
	updatedAt?: string;
}

/** So lange darf die Live-Verbindung weg sein, bevor der Ladebildschirm erscheint. */
const LOST_AFTER_MS = 5000;

/**
 * Live-Updates per Server-Sent Events (/api/events). Nutzt die normale Login-Session;
 * EventSource verbindet sich nach einem Abbruch selbst neu. Gibt der Browser auf
 * (Verbindung geschlossen), wird nach kurzer Pause ein neuer Versuch gestartet.
 */
class RealtimeStore {
	private source: EventSource | null = null;
	private lostTimer: ReturnType<typeof setTimeout> | undefined;
	private retryTimer: ReturnType<typeof setTimeout> | undefined;
	isConnected = $state(false);
	/** Bestand schon eine Verbindung und ist seit einigen Sekunden weg? */
	isLost = $state(false);
	lastAnnouncement = $state<Announcement | null>(null);
	leaderboardUpdateTrigger = $state(0);

	connect() {
		if (!browser || this.source) return;

		this.source = new EventSource('/api/events', { withCredentials: true });
		this.source.addEventListener('connected', () => {
			this.isConnected = true;
			this.isLost = false;
			clearTimeout(this.lostTimer);
			this.lostTimer = undefined;
		});
		this.source.addEventListener('announcement', (event) => {
			this.lastAnnouncement = JSON.parse((event as MessageEvent).data);
		});
		this.source.addEventListener('leaderboard', () => {
			this.leaderboardUpdateTrigger++;
		});
		this.source.onerror = () => {
			if (this.isConnected && !this.lostTimer) {
				this.lostTimer = setTimeout(() => (this.isLost = true), LOST_AFTER_MS);
			}
			this.isConnected = false;
			if (this.source?.readyState === EventSource.CLOSED) {
				this.source = null;
				this.retryTimer = setTimeout(() => this.connect(), 3000);
			}
		};
	}

	disconnect() {
		clearTimeout(this.lostTimer);
		clearTimeout(this.retryTimer);
		this.lostTimer = undefined;
		this.source?.close();
		this.source = null;
		this.isConnected = false;
		this.isLost = false;
	}
}

export const realtime = new RealtimeStore();
