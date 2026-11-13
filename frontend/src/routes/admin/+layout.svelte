<script lang="ts">
	import { onDestroy, onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { auth } from '$lib/auth.svelte';
	import { realtime } from '$lib/realtime.svelte';
	import Logo from '$lib/components/Logo.svelte';
	import { ADMIN_ICONS, ADMIN_NAV, isAdminLinkActive, type AdminLink } from '$lib/admin/nav';

	let { children } = $props();

	let drawerOpen = $state(false);
	let helpCount = $state(0);
	let submissionCount = $state(0);
	let poll: ReturnType<typeof setInterval> | undefined;

	const isAdmin = $derived(auth.user?.role === 'ADMIN');
	// Editoren mit Zeichenfläche bekommen die volle Breite und nur eine schmale Icon-Leiste
	const compact = $derived(/^\/admin\/(codepaths|content)\/[^/]+/.test(page.url.pathname));

	async function loadCounts() {
		if (!isAdmin) return;
		try {
			const [help, subs] = await Promise.all([
				auth.apiFetch('/api/admin/help'),
				auth.apiFetch('/api/admin/submissions')
			]);
			if (help.ok) helpCount = (await help.json()).open?.length ?? 0;
			if (subs.ok) submissionCount = (await subs.json()).length ?? 0;
		} catch {
			// Zähler sind nur ein Hinweis
		}
	}

	onMount(() => {
		loadCounts();
		poll = setInterval(loadCounts, 30000);
	});
	onDestroy(() => clearInterval(poll));

	$effect(() => {
		if (realtime.helpOpen !== null) helpCount = realtime.helpOpen;
	});

	$effect(() => {
		void page.url.pathname;
		drawerOpen = false;
	});

	function count(link: AdminLink): number {
		if (link.badge === 'help') return helpCount;
		if (link.badge === 'submissions') return submissionCount;
		return 0;
	}

	function logout() {
		auth.logout();
		goto('/login');
	}

	const initial = $derived((auth.user?.username ?? '?').charAt(0).toUpperCase());
</script>

{#snippet icon(name: keyof typeof ADMIN_ICONS, cls = 'h-[18px] w-[18px]')}
	<svg
		class="shrink-0 {cls}"
		fill="none"
		viewBox="0 0 24 24"
		stroke="currentColor"
		stroke-width="1.9"
		stroke-linecap="round"
		stroke-linejoin="round"
		aria-hidden="true"><path d={ADMIN_ICONS[name]} /></svg
	>
{/snippet}

{#snippet navList(rail: boolean)}
	{#each ADMIN_NAV as group (group.title)}
		{#if rail}
			<div class="mx-auto my-2 h-px w-8 bg-slate-700/70 first:hidden"></div>
		{:else}
			<p
				class="mt-5 mb-1.5 px-3 font-mono text-[11px] font-bold tracking-wider text-slate-400 uppercase first:mt-2"
			>
				{group.title}
			</p>
		{/if}
		{#each group.links as link (link.href)}
			{@const active = isAdminLinkActive(link.href, page.url.pathname)}
			{@const n = count(link)}
			<a
				href={link.href}
				aria-current={active ? 'page' : undefined}
				title={rail ? link.title : undefined}
				aria-label={rail ? link.title + (n ? ` (${n})` : '') : undefined}
				class="relative flex items-center gap-3 rounded-[var(--radius-ui-sm)] text-sm transition-colors {rail
					? 'h-11 w-11 justify-center'
					: 'px-3 py-2'} {active
					? 'bg-slate-800 font-semibold text-white shadow-[inset_3px_0_0_#f59e0b]'
					: 'text-slate-300 hover:bg-slate-800 hover:text-white'}"
			>
				{@render icon(link.icon)}
				{#if !rail}<span class="flex-1">{link.title}</span>{/if}
				{#if n > 0}
					<span
						class="font-mono text-[11px] font-bold {rail
							? 'absolute -top-0.5 -right-0.5 flex h-5 min-w-5 items-center justify-center rounded-full px-1'
							: 'rounded-full px-2 py-0.5'} {link.badge === 'help'
							? 'bg-rose-500 text-white'
							: 'bg-amber-400 text-slate-900'}"
						data-testid={link.badge === 'help' ? 'help-badge' : undefined}>{n}</span
					>
				{/if}
			</a>
		{/each}
	{/each}
{/snippet}

{#snippet footer(rail: boolean)}
	<div
		class="mt-auto flex flex-col gap-1 border-t border-slate-800 pt-3 {rail ? 'items-center' : ''}"
	>
		<a
			href="/projector"
			target="_blank"
			title={rail ? 'Beamer-Modus' : undefined}
			aria-label={rail ? 'Beamer-Modus (neuer Tab)' : undefined}
			class="flex items-center gap-3 rounded-[var(--radius-ui-sm)] text-sm text-slate-300 hover:bg-slate-800 hover:text-white {rail
				? 'h-11 w-11 justify-center'
				: 'px-3 py-2'}"
		>
			{@render icon('projector')}{#if !rail}<span>Beamer-Modus</span>{/if}
		</a>
		<a
			href="/modules"
			title={rail ? 'Schüleransicht' : undefined}
			aria-label={rail ? 'Schüleransicht' : undefined}
			class="flex items-center gap-3 rounded-[var(--radius-ui-sm)] text-sm text-slate-300 hover:bg-slate-800 hover:text-white {rail
				? 'h-11 w-11 justify-center'
				: 'px-3 py-2'}"
		>
			{@render icon('eye')}{#if !rail}<span>Schüleransicht</span>{/if}
		</a>
		<div class="mt-2 flex items-center gap-3 {rail ? 'flex-col' : 'px-2'}">
			<span
				class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-700 text-sm font-bold text-white"
				aria-hidden="true">{initial}</span
			>
			{#if !rail}
				<span class="min-w-0 flex-1 leading-tight">
					<span class="block truncate text-sm font-semibold text-white">{auth.user?.username}</span>
					<span class="block text-xs text-slate-400">Lehrkraft</span>
				</span>
			{/if}
			<button
				type="button"
				onclick={logout}
				class="flex h-9 w-9 items-center justify-center text-slate-400 hover:bg-slate-800 hover:text-white"
				aria-label="Abmelden"
				title="Abmelden"
			>
				{@render icon('logout')}
			</button>
		</div>
	</div>
{/snippet}

{#if isAdmin}
	<div class="flex min-h-screen bg-canvas">
		<!-- Seitennavigation (Desktop) -->
		<!-- Der dunkle Streifen läuft über die ganze Seitenhöhe, das Menü darin bleibt beim Scrollen stehen -->
		<aside
			class="hidden shrink-0 self-stretch bg-institutional-navy lg:block {compact
				? 'w-[68px]'
				: 'w-64'}"
			aria-label="Verwaltung"
		>
			<div
				class="sticky top-0 flex h-screen flex-col overflow-y-auto px-3 py-4 {compact
					? 'items-center'
					: ''}"
			>
				<a
					href="/admin"
					class="mb-3 flex items-center gap-2.5 {compact ? 'justify-center' : 'px-2'}"
					aria-label={compact ? 'Leitstand' : undefined}
				>
					<span
						class="flex h-9 w-9 shrink-0 items-center justify-center rounded-[var(--radius-ui-sm)] bg-white text-institutional-navy"
					>
						<Logo class="h-full w-full" />
					</span>
					{#if !compact}
						<span class="leading-tight">
							<span class="block text-base font-extrabold text-white">CodePath</span>
							<span class="block text-xs text-slate-400">Verwaltung</span>
						</span>
					{/if}
				</a>
				<nav class="flex flex-col gap-0.5 {compact ? 'items-center' : ''}" aria-label="Admin-Menü">
					{@render navList(compact)}
				</nav>
				{@render footer(compact)}
			</div>
		</aside>

		<div class="flex min-w-0 flex-1 flex-col">
			<!-- Kopfzeile (Handy/Tablet) -->
			<header
				class="sticky top-0 z-40 flex h-14 items-center gap-3 bg-institutional-navy px-4 text-white lg:hidden"
			>
				<button
					type="button"
					class="flex h-10 w-10 items-center justify-center hover:bg-slate-800"
					aria-label="Menü öffnen"
					aria-expanded={drawerOpen}
					onclick={() => (drawerOpen = true)}
				>
					{@render icon('menu', 'h-5 w-5')}
				</button>
				<a href="/admin" class="flex items-center gap-2 font-extrabold">
					<span
						class="flex h-8 w-8 items-center justify-center rounded bg-white text-institutional-navy"
						><Logo class="h-full w-full" /></span
					>
					CodePath
				</a>
				<span class="flex-1"></span>
				{#if helpCount > 0}
					<a
						href="/admin/help"
						class="rounded-full bg-rose-500 px-2.5 py-1 font-mono text-xs font-bold"
						aria-label="Hilfe-Rufe: {helpCount} warten">✋ {helpCount}</a
					>
				{/if}
			</header>

			<main class="flex-1 {compact ? 'px-3 py-3 lg:px-5' : 'px-4 py-6 sm:px-6 lg:px-10 lg:py-8'}">
				<div class={compact ? '' : 'mx-auto w-full max-w-7xl'}>
					{@render children()}
				</div>
			</main>
		</div>

		<!-- Menü (Handy/Tablet) -->
		{#if drawerOpen}
			<button
				type="button"
				class="fixed inset-0 z-50 cursor-default bg-slate-900/50 lg:hidden"
				aria-label="Menü schließen"
				onclick={() => (drawerOpen = false)}
			></button>
			<aside
				class="fixed inset-y-0 left-0 z-50 flex w-72 flex-col overflow-y-auto bg-institutional-navy px-3 py-4 lg:hidden"
				aria-label="Verwaltung"
			>
				<button
					type="button"
					class="mb-2 ml-auto flex h-10 w-10 items-center justify-center text-slate-300 hover:bg-slate-800"
					aria-label="Menü schließen"
					onclick={() => (drawerOpen = false)}
				>
					{@render icon('close', 'h-5 w-5')}
				</button>
				<nav class="flex flex-col gap-0.5" aria-label="Admin-Menü">
					{@render navList(false)}
				</nav>
				{@render footer(false)}
			</aside>
		{/if}
	</div>
{:else}
	{@render children()}
{/if}
