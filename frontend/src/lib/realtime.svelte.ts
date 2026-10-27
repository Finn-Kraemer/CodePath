import { browser } from '$app/environment';

export interface Announcement {
	content: string;
	displayMode: string;
	updatedAt?: string;
}

/**
 * Live-Updates per Server-Sent Events (/api/events). Nutzt die normale Login-Session;
 * EventSource verbindet sich nach einem Abbruch selbst neu.
 */
class RealtimeStore {
	private source: EventSource | null = null;
	isConnected = $state(false);
	lastAnnouncement = $state<Announcement | null>(null);
	leaderboardUpdateTrigger = $state(0);

	connect() {
		if (!browser || this.source) return;

		this.source = new EventSource('/api/events', { withCredentials: true });
		this.source.addEventListener('connected', () => {
			this.isConnected = true;
		});
		this.source.addEventListener('announcement', (event) => {
			this.lastAnnouncement = JSON.parse((event as MessageEvent).data);
		});
		this.source.addEventListener('leaderboard', () => {
			this.leaderboardUpdateTrigger++;
		});
		this.source.onerror = () => {
			this.isConnected = false;
		};
	}

	disconnect() {
		this.source?.close();
		this.source = null;
		this.isConnected = false;
	}
}

export const realtime = new RealtimeStore();
