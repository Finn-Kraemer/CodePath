<script lang="ts">
	import { page } from '$app/state';

	const links = [
		{ href: '/admin/feedback', label: 'Bewertungsrunden' },
		{ href: '/admin/feedback/templates', label: 'Vorlagen' },
		{ href: '/admin/feedback/messages', label: 'Nachrichten' }
	];

	function active(href: string) {
		const path = page.url.pathname.replace(/\/$/, '');
		return href === '/admin/feedback'
			? path === href || path.startsWith('/admin/feedback/rounds')
			: path.startsWith(href);
	}
</script>

<nav class="mb-8 flex flex-wrap gap-1 border-b border-slate-200" aria-label="Feedback">
	{#each links as link (link.href)}
		<a
			href={link.href}
			aria-current={active(link.href) ? 'page' : undefined}
			class="-mb-px border-b-2 px-4 py-2 text-sm font-bold {active(link.href)
				? 'border-institutional-navy text-institutional-navy'
				: 'border-transparent text-slate-400 hover:text-institutional-navy'}">{link.label}</a
		>
	{/each}
</nav>
