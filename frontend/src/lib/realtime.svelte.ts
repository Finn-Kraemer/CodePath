import { browser } from '$app/environment';

export interface Announcement {
	content: string;
	displayMode: string;
	updatedAt?: string;
}

/** So lange darf die Live-Verbindung weg sein, bevor der Ladebildschirm erscheint. */
const LOST_AFTER_MS = 5000;
/**
 * Ranglisten-Updates kommen bei allen Browsern eines Kurses gleichzeitig an. Jeder lädt darum erst nach
 * einer zufälligen kurzen Pause neu – so verteilen sich 30 Klassen-Browser über gut eine Sekunde, statt
 * den Server im selben Augenblick zu treffen. Was in der Pause noch ankommt, ist damit gleich mit erledigt.
 */
const LEADERBOARD_SPREAD_MS = 1500;

/** Pause vor einem neuen Verbindungsversuch: gestreut, damit nach einem Neustart nicht alle auf einmal kommen. */
function retryDelay() {
	return 2000 + Math.random() * 4000;
}

/**
 * Live-Updates per Server-Sent Events (/api/events). Nutzt die normale Login-Session;
 * EventSource verbindet sich nach einem Abbruch selbst neu. Gibt der Browser auf
 * (Verbindung geschlossen), wird nach kurzer Pause ein neuer Versuch gestartet.
 */
class RealtimeStore {
	private source: EventSource | null = null;
	private lostTimer: ReturnType<typeof setTimeout> | undefined;
	private retryTimer: ReturnType<typeof setTimeout> | undefined;
	private leaderboardTimer: ReturnType<typeof setTimeout> | undefined;
	isConnected = $state(false);
	/** Bestand schon eine Verbindung und ist seit einigen Sekunden weg? */
	isLost = $state(false);
	lastAnnouncement = $state<Announcement | null>(null);
	lastPathNotice = $state<{ username: string; message: string; at: number } | null>(null);
	leaderboardUpdateTrigger = $state(0);
	/** Hilfe-Rufe geändert (nur Lehrkräfte und die Betroffenen bekommen das Ereignis) */
	helpTrigger = $state(0);
	/** Zahl wartender Hilfe-Rufe – kommt nur bei Lehrkräften an */
	helpOpen = $state<number | null>(null);
	/** Quiz-Runde geändert (Phase für alle, Zahl der Antworten nur für Lehrkräfte) */
	quizTrigger = $state(0);

	clearPathNotice() {
		this.lastPathNotice = null;
	}

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
			if (this.leaderboardTimer) return;
			this.leaderboardTimer = setTimeout(() => {
				this.leaderboardTimer = undefined;
				this.leaderboardUpdateTrigger++;
			}, Math.random() * LEADERBOARD_SPREAD_MS);
		});
		this.source.addEventListener('help', (event) => {
			try {
				const data = JSON.parse((event as MessageEvent).data);
				if (typeof data?.open === 'number') this.helpOpen = data.open;
			} catch {
				// ignorieren
			}
			this.helpTrigger++;
		});
		this.source.addEventListener('quiz', () => {
			this.quizTrigger++;
		});
		this.source.addEventListener('codepath', (event) => {
			try {
				const data = JSON.parse((event as MessageEvent).data);
				if (data?.username && data?.message) {
					this.lastPathNotice = {
						username: data.username,
						message: data.message,
						at: Date.now()
					};
				}
			} catch {
				// ignorieren
			}
			this.leaderboardUpdateTrigger++;
		});
		this.source.onerror = () => {
			if (this.isConnected && !this.lostTimer) {
				this.lostTimer = setTimeout(() => (this.isLost = true), LOST_AFTER_MS);
			}
			this.isConnected = false;
			if (this.source?.readyState === EventSource.CLOSED) {
				this.source = null;
				this.retryTimer = setTimeout(() => this.connect(), retryDelay());
			}
		};
	}

	disconnect() {
		clearTimeout(this.lostTimer);
		clearTimeout(this.retryTimer);
		clearTimeout(this.leaderboardTimer);
		this.lostTimer = undefined;
		this.leaderboardTimer = undefined;
		this.source?.close();
		this.source = null;
		this.isConnected = false;
		this.isLost = false;
	}
}

export const realtime = new RealtimeStore();
