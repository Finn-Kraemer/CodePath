<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { ADMIN_GROUPS, activeAdminLink } from '$lib/admin/nav';

	let { children } = $props();

	const active = $derived(activeAdminLink(page.url.pathname));
	const isDashboard = $derived(page.url.pathname === '/admin');
	/** Editoren brauchen die volle Breite – dort ist die Navigation nur eine Auswahl */
	const wide = $derived(/^\/admin\/(learning-paths|content|pages)\/[^/]+/.test(page.url.pathname));
</script>

<div class="flex flex-col gap-6 {wide ? '' : 'lg:flex-row lg:items-start lg:gap-10'}">
	<!-- Seitennavigation des Leitstands: auf großen Bildschirmen links, sonst als Auswahl -->
	<nav
		class={wide ? 'max-w-xs lg:hidden' : 'lg:sticky lg:top-20 lg:w-56 lg:shrink-0'}
		aria-label="Leitstand"
	>
		<div class={wide ? '' : 'lg:hidden'}>
			<label class="sr-only" for="admin-nav">Bereich wählen</label>
			<select
				id="admin-nav"
				class="w-full border border-slate-200 bg-white px-3 py-2.5 text-sm font-semibold text-institutional-navy"
				value={isDashboard ? '/admin' : (active?.href ?? '')}
				onchange={(e) => goto((e.currentTarget as HTMLSelectElement).value)}
			>
				<option value="/admin">Übersicht</option>
				{#each ADMIN_GROUPS as group (group.title)}
					<optgroup label={group.title}>
						{#each group.links as link (link.href)}
							<option value={link.href}>{link.title}</option>
						{/each}
					</optgroup>
				{/each}
			</select>
		</div>

		<div class="hidden flex-col gap-5 {wide ? '' : 'lg:flex'}">
			<a
				href="/admin"
				class="flex items-center gap-2 px-3 py-2 text-sm font-bold {isDashboard
					? 'bg-institutional-navy text-white'
					: 'text-institutional-navy hover:bg-slate-100'}"
			>
				<span aria-hidden="true">⌂</span> Übersicht
			</a>
			{#each ADMIN_GROUPS as group (group.title)}
				<div class="flex flex-col gap-0.5">
					<span
						class="px-3 pb-1 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
						>{group.title}</span
					>
					{#each group.links as link (link.href)}
						<a
							href={link.href}
							aria-current={active?.href === link.href ? 'page' : undefined}
							class="flex items-center gap-2 border-l-2 px-3 py-1.5 text-sm {active?.href ===
							link.href
								? 'border-institutional-gold bg-amber-50/60 font-bold text-institutional-navy'
								: 'border-transparent text-slate-600 hover:bg-slate-50 hover:text-institutional-navy'}"
						>
							<span class="w-5 text-center" aria-hidden="true">{link.icon}</span>
							{link.title}
						</a>
					{/each}
				</div>
			{/each}
		</div>
	</nav>

	<div class="min-w-0 flex-1">
		{@render children()}
	</div>
</div>
