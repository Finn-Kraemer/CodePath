import { browser } from '$app/environment';
import { goto } from '$app/navigation';

interface User {
	username: string;
	role: string;
}

class AuthStore {
	// We no longer read token from localStorage, it's in a HttpOnly cookie
	token = $state<string | null>(null);
	user = $state<User | null>(browser ? JSON.parse(localStorage.getItem('user') || 'null') : null);

	login(token: string, user: User) {
		this.token = token;
		this.user = user;
		if (browser) {
			localStorage.setItem('user', JSON.stringify(user));
		}
	}

	async logout() {
		this.token = null;
		this.user = null;
		if (browser) {
			localStorage.removeItem('user');
			// Call backend to clear cookie
			try {
				await fetch('/api/auth/logout', { method: 'POST' });
			} catch (e) {
				console.error('Logout failed', e);
			}
			goto('/login');
		}
	}

	get isAuthenticated() {
		// Since token is in HttpOnly cookie, we use user presence as a proxy for UI state
		return !!this.user;
	}

	private getCsrfToken(): string | null {
		if (!browser) return null;
		const name = 'XSRF-TOKEN=';
		const decodedCookie = decodeURIComponent(document.cookie);
		const ca = decodedCookie.split(';');
		for (let i = 0; i < ca.length; i++) {
			let c = ca[i];
			while (c.charAt(0) === ' ') {
				c = c.substring(1);
			}
			if (c.indexOf(name) === 0) {
				return c.substring(name.length, c.length);
			}
		}
		return null;
	}

	async apiFetch(url: string, options: RequestInit = {}) {
		const headers: Record<string, string> = {
			'Content-Type': 'application/json'
		};

		// CSRF Protection
		const csrfToken = this.getCsrfToken();
		if (csrfToken && options.method && !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(options.method.toUpperCase())) {
			headers['X-XSRF-TOKEN'] = csrfToken;
		}

		if (options.headers) {
			Object.assign(headers, options.headers);
		}

		const res = await fetch(url, {
			...options,
			headers,
			// Important: credentials 'include' sends cookies
			credentials: 'include'
		});

		if (res.status === 401) {
			await this.logout();
		}

		return res;
	}
}

export const auth = new AuthStore();
