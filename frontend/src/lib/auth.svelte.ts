import { browser } from '$app/environment';
import { goto } from '$app/navigation';
import { realtime } from '$lib/realtime.svelte';
import { connection } from '$lib/connection.svelte';

export interface User {
	username: string;
	role: string;
	displayName?: string;
}

/**
 * Die Anmeldung steckt in einer serverseitigen Session (HttpOnly-Cookie). Im Browser liegt nur,
 * wer angemeldet ist – als Hinweis für die Oberfläche; /api/auth/me bestätigt es beim Start.
 */
class AuthStore {
	user = $state<User | null>(browser ? JSON.parse(localStorage.getItem('user') || 'null') : null);

	login(user: User) {
		this.user = user;
		if (browser) {
			localStorage.setItem('user', JSON.stringify(user));
		}
	}

	private clear() {
		this.user = null;
		if (browser) {
			localStorage.removeItem('user');
		}
		realtime.disconnect();
	}

	async logout() {
		try {
			await this.apiFetch('/api/auth/logout', { method: 'POST' }, false);
		} catch (e) {
			console.error('Logout failed', e);
		}
		this.clear();
		if (browser) goto('/login');
	}

	/** Gleicht den lokalen Zustand mit der Session auf dem Server ab. */
	async refresh() {
		if (!browser) return;
		try {
			// Zeitlimit, damit ein hängender Server den Start nicht endlos aufhält
			const res = await fetch('/api/auth/me', {
				credentials: 'include',
				signal: AbortSignal.timeout(10_000)
			});
			if (res.ok) {
				this.login(await res.json());
			} else if (res.status === 401) {
				this.clear();
			}
		} catch {
			// Offline: lokalen Zustand behalten
		}
	}

	get isAuthenticated() {
		return !!this.user;
	}

	getCsrfToken(): string | null {
		if (!browser) return null;
		const match = document.cookie
			.split(';')
			.map((c) => c.trim())
			.find((c) => c.startsWith('XSRF-TOKEN='));
		return match ? decodeURIComponent(match.substring('XSRF-TOKEN='.length)) : null;
	}

	async apiFetch(url: string, options: RequestInit = {}, logoutOnUnauthorized = true) {
		const headers: Record<string, string> = {
			'Content-Type': 'application/json'
		};

		const csrfToken = this.getCsrfToken();
		if (
			csrfToken &&
			options.method &&
			!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(options.method.toUpperCase())
		) {
			headers['X-XSRF-TOKEN'] = csrfToken;
		}

		if (options.headers) {
			Object.assign(headers, options.headers);
		}

		const done = connection.begin(url);
		let res: Response;
		try {
			res = await fetch(url, {
				...options,
				headers,
				credentials: 'include'
			});
		} finally {
			done();
		}

		if (res.status === 401 && logoutOnUnauthorized) {
			this.clear();
			if (browser) goto('/login');
		}

		return res;
	}
}

export const auth = new AuthStore();
