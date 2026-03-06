import { browser } from '$app/environment';
import { goto } from '$app/navigation';

interface User {
	username: string;
	role: string;
}

class AuthStore {
	token = $state<string | null>(browser ? localStorage.getItem('token') : null);
	user = $state<User | null>(browser ? JSON.parse(localStorage.getItem('user') || 'null') : null);

	login(token: string, user: User) {
		this.token = token;
		this.user = user;
		if (browser) {
			localStorage.setItem('token', token);
			localStorage.setItem('user', JSON.stringify(user));
		}
	}

	logout() {
		this.token = null;
		this.user = null;
		if (browser) {
			localStorage.removeItem('token');
			localStorage.removeItem('user');
		}
	}

	get isAuthenticated() {
		return !!this.token;
	}

	async apiFetch(url: string, options: RequestInit = {}) {
		const currentToken = this.token;
		const headers: Record<string, string> = {
			'Content-Type': 'application/json'
		};

		if (currentToken) {
			headers['Authorization'] = `Bearer ${currentToken}`;
		}

		if (options.headers) {
			Object.assign(headers, options.headers);
		}

		const res = await fetch(url, {
			...options,
			headers,
			credentials: 'omit'
		});

		if (res.status === 401) {
			this.logout();
			if (browser) {
				goto('/login');
			}
		}

		return res;
	}
}

export const auth = new AuthStore();
