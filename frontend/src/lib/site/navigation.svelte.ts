import type { NavLink } from '$lib/api';

/** Eigene Seiten, die die Lehrkraft ins Menü gestellt hat. */
class SiteNavigation {
	links = $state<NavLink[]>([]);

	async refresh() {
		try {
			const res = await fetch('/api/public/pages', { credentials: 'include' });
			this.links = res.ok ? await res.json() : [];
		} catch {
			// Offline: bisherige Links behalten
		}
	}
}

export const siteNavigation = new SiteNavigation();
