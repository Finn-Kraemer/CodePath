<script lang="ts">
	import './layout.css';
	import '@fontsource/inter/400.css';
	import '@fontsource/inter/600.css';
	import '@fontsource/inter/800.css';
	import { onMount } from 'svelte';
	import Logo from '$lib/components/Logo.svelte';
	import ConnectionOverlay from '$lib/components/ConnectionOverlay.svelte';
	import favicon from '$lib/assets/favicon.svg';
	import { auth } from '$lib/auth.svelte';
	import { realtime } from '$lib/realtime.svelte';
	import HelpBadge from '$lib/help/HelpBadge.svelte';
	import QuizBanner from '$lib/quiz/QuizBanner.svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { siteNavigation } from '$lib/site/navigation.svelte';
	import { pathFocus } from '$lib/pathway/focus.svelte';

	let { children } = $props();
	let announcement = $state<{ content: string; updatedAt?: string; displayMode?: string } | null>(
		null
	);
	let mobileMenuOpen = $state(false);

	// Global Auth Guard
	$effect(() => {
		const path = page.url.pathname;
		// Public pages that don't need auth
		// Eigene Seiten prüft der Server selbst (öffentlich oder nur angemeldet)
		const isPublicPage =
			path === '/login' ||
			path === '/register' ||
			path === '/' ||
			path === '/seiten' ||
			path.startsWith('/seiten/');

		if (!auth.isAuthenticated && !isPublicPage) {
			goto('/login');
		}

		if (auth.isAuthenticated && (path === '/login' || path === '/register')) {
			goto('/modules');
		}
	});

	// Menüpunkte eigener Seiten hängen davon ab, ob jemand angemeldet ist
	$effect(() => {
		void auth.isAuthenticated;
		siteNavigation.refresh();
	});

	// Live-Verbindung auch nach Anmeldung ohne Neuladen (Login, Registrierung)
	$effect(() => {
		if (auth.isAuthenticated) realtime.connect();
	});

	onMount(async () => {
		await auth.refresh();
		// Start-Intro aus app.html ausblenden, sobald die Anmeldung geklärt ist
		window.cpSplashDone?.();
		if (auth.isAuthenticated) {
			realtime.connect();
			try {
				const [res, pathsRes] = await Promise.all([
					auth.apiFetch('/api/common/announcement'),
					auth.apiFetch('/api/codepaths')
				]);
				if (res.ok) {
					const data = await res.json();
					if (data.content && (data.displayMode === 'HEADER' || data.displayMode === 'BOTH')) {
						announcement = data;
					}
				}
				if (pathsRes.ok) {
					const links: { hideOverview?: boolean }[] = await pathsRes.json();
					pathFocus.set(links.some((l) => !!l.hideOverview));
				}
			} catch (e) {
				console.error('Failed to fetch announcement or paths', e);
			}
		}
	});

	// Live-Updates (SSE)
	$effect(() => {
		if (realtime.lastAnnouncement) {
			const data = realtime.lastAnnouncement;
			if (data.content && (data.displayMode === 'HEADER' || data.displayMode === 'BOTH')) {
				announcement = data;
			} else {
				announcement = null;
			}
		}
	});

	function handleLogout() {
		auth.logout();
		mobileMenuOpen = false;
		goto('/login');
	}

	function isActive(path: string) {
		return page.url.pathname.startsWith(path);
	}

	const navLinks = $derived([
		{
			name: pathFocus.active && auth.user?.role !== 'ADMIN' ? 'CodePath' : 'Module',
			path: '/modules'
		},
		{ name: 'Wissen', path: '/knowledge' },
		{ name: 'Info', path: '/info' },
		{ name: 'Feedback', path: '/feedback' },
		{ name: 'Rangliste', path: '/leaderboard' }
	]);

	// Feste Menüpunkte für Angemeldete, dazu angeheftete eigene Seiten (öffentliche auch ohne Login).
	// Alle übrigen Seiten stehen im Aufklappmenü „Seiten“ und unter /seiten.
	const allLinks = $derived([
		...(auth.isAuthenticated ? navLinks : []),
		...siteNavigation.links
			.filter((l) => l.showInNav)
			.map((l) => ({ name: l.title, path: `/seiten/${l.slug}` }))
	]);
	let pagesMenuOpen = $state(false);
	let pagesMenu = $state<HTMLElement | null>(null);

	// Aufklappmenü beim Seitenwechsel schließen
	$effect(() => {
		void page.url.pathname;
		pagesMenuOpen = false;
	});

	// Helpers for special layouts
	let isFrontpage = $derived(page.url.pathname === '/' || page.url.pathname.startsWith('/seiten/'));
	let isProjector = $derived(page.url.pathname === '/projector');
	// Admin-Bereich: eigene Seitennavigation statt Kopfzeile (siehe admin/+layout.svelte)
	let isAdminArea = $derived(
		auth.user?.role === 'ADMIN' && /^\/admin(\/|$)/.test(page.url.pathname)
	);

	// Handy: feste Leiste unten mit den wichtigsten Zielen, der Rest unter „Mehr“
	const tabLinks = $derived(
		auth.isAuthenticated
			? [
					{
						name: navLinks[0].name,
						path: '/modules',
						icon: 'M9 4L3 6v14l6-2 6 2 6-2V4l-6 2zM9 4v14M15 6v14'
					},
					{
						name: 'Wissen',
						path: '/knowledge',
						icon: 'M4 5a2 2 0 012-2h14v16H6a2 2 0 00-2 2zM4 5v16'
					},
					{
						name: 'Rangliste',
						path: '/leaderboard',
						icon: 'M8 21h8M12 17v4M7 4h10v5a5 5 0 01-10 0zM7 6H4a3 3 0 003 4M17 6h3a3 3 0 01-3 4'
					},
					{
						name: 'Ich',
						path: '/profile',
						icon: 'M12 12a4 4 0 100-8 4 4 0 000 8zM4 21a8 8 0 0116 0'
					}
				]
			: []
	);
	const initial = $derived((auth.user?.username ?? '?').charAt(0).toUpperCase());
</script>

<svelte:head>
	<title>CodePath | IT Berufsorientierung</title>
	<link rel="icon" href={favicon} type="image/svg+xml" />
</svelte:head>

<svelte:window
	onclick={(e) => {
		if (pagesMenuOpen && pagesMenu && !pagesMenu.contains(e.target as Node)) pagesMenuOpen = false;
	}}
	onkeydown={(e) => {
		if (e.key === 'Escape') pagesMenuOpen = false;
	}}
/>

<ConnectionOverlay />

<div
	class="flex min-h-screen flex-col text-slate-900 {isFrontpage || isProjector
		? 'bg-white'
		: 'bg-canvas'}"
>
	{#if !isProjector && !isAdminArea}
		<nav
			class="sticky top-0 z-50 border-b border-slate-200 bg-white/90 backdrop-blur-md"
			aria-label="Hauptmenü"
		>
			<div class="container-institutional">
				<div class="flex h-16 items-center justify-between gap-6">
					<div class="flex min-w-0 items-center gap-6">
						<a href="/" class="group flex shrink-0 items-center gap-2.5">
							<span
								class="flex h-9 w-9 items-center justify-center rounded-[var(--radius-ui-sm)] bg-institutional-navy text-white"
							>
								<Logo class="h-full w-full" />
							</span>
							<span class="flex flex-col leading-none">
								<span class="text-[17px] font-extrabold tracking-tight text-institutional-navy"
									>CodePath</span
								>
								<span class="mt-0.5 hidden text-[11px] font-medium text-slate-500 sm:block"
									>Berufsorientierung IT</span
								>
							</span>
						</a>

						{#if allLinks.length || siteNavigation.links.length}
							<div class="hidden items-center gap-1 lg:flex">
								{#each allLinks as link (link.path)}
									<a
										href={link.path}
										aria-current={isActive(link.path) ? 'page' : undefined}
										class="rounded-[var(--radius-ui-sm)] px-3 py-2 text-sm font-semibold transition-colors {isActive(
											link.path
										)
											? 'bg-slate-100 text-institutional-navy'
											: 'text-slate-600 hover:bg-slate-50 hover:text-institutional-navy'}"
									>
										{link.name}
									</a>
								{/each}
								{#if siteNavigation.links.length}
									<div class="relative" bind:this={pagesMenu}>
										<button
											type="button"
											class="flex items-center gap-1 px-3 py-2 text-sm font-semibold {isActive(
												'/seiten'
											)
												? 'bg-slate-100 text-institutional-navy'
												: 'text-slate-600 hover:bg-slate-50 hover:text-institutional-navy'}"
											aria-expanded={pagesMenuOpen}
											aria-haspopup="true"
											onclick={() => (pagesMenuOpen = !pagesMenuOpen)}
										>
											Seiten
											<svg
												class="h-3.5 w-3.5"
												viewBox="0 0 20 20"
												fill="currentColor"
												aria-hidden="true"
												><path
													fill-rule="evenodd"
													d="M5.23 7.21a.75.75 0 011.06.02L10 11.17l3.71-3.94a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z"
													clip-rule="evenodd"
												/></svg
											>
										</button>
										{#if pagesMenuOpen}
											<div
												class="ui-card absolute top-full left-0 z-50 mt-2 w-72 overflow-hidden py-1.5 shadow-xl"
												role="menu"
											>
												{#each siteNavigation.links as link (link.slug)}
													<a
														href={`/seiten/${link.slug}`}
														role="menuitem"
														class="block px-4 py-2 hover:bg-slate-50 {isActive(
															`/seiten/${link.slug}`
														)
															? 'bg-slate-50'
															: ''}"
													>
														<span class="block text-sm font-semibold text-institutional-navy"
															>{link.title}</span
														>
														{#if link.description}
															<span class="block truncate text-xs text-slate-500"
																>{link.description}</span
															>
														{/if}
													</a>
												{/each}
												<a
													href="/seiten"
													role="menuitem"
													class="mt-1 block border-t border-slate-100 px-4 pt-2.5 pb-1.5 text-sm font-semibold text-institutional-gold hover:underline"
													>Alle Seiten im Überblick →</a
												>
											</div>
										{/if}
									</div>
								{/if}
							</div>
						{/if}
					</div>

					<div class="flex shrink-0 items-center gap-3">
						{#if auth.isAuthenticated}
							{#if auth.user?.role === 'ADMIN'}
								<div class="hidden md:block"><HelpBadge /></div>
								<a href="/admin" class="ui-btn ui-btn-primary hidden md:inline-flex">
									<svg
										class="h-4 w-4"
										fill="none"
										viewBox="0 0 24 24"
										stroke="currentColor"
										stroke-width="2"
										aria-hidden="true"
										><path
											stroke-linecap="round"
											stroke-linejoin="round"
											d="M3 11l9-7 9 7v9H3zM9 20v-6h6v6"
										/></svg
									>
									Leitstand
								</a>
							{/if}
							<a
								href="/profile"
								class="flex items-center gap-2.5 rounded-full py-1 pr-1 pl-1 hover:bg-slate-50 md:pr-3"
								title="Mein Profil"
							>
								<span
									class="flex h-9 w-9 items-center justify-center rounded-full bg-indigo-100 text-sm font-bold text-indigo-800"
									aria-hidden="true">{initial}</span
								>
								<span class="hidden flex-col leading-tight md:flex">
									<span class="text-sm font-semibold text-institutional-navy"
										>{auth.user?.username}</span
									>
									<span class="text-xs text-slate-500"
										>{auth.user?.role === 'ADMIN' ? 'Lehrkraft' : 'Schüler:in'}</span
									>
								</span>
							</a>
							<button
								onclick={handleLogout}
								class="hidden h-9 w-9 items-center justify-center text-slate-500 hover:bg-slate-100 hover:text-institutional-navy md:flex"
								aria-label="Abmelden"
								title="Abmelden"
							>
								<svg
									class="h-[18px] w-[18px]"
									fill="none"
									viewBox="0 0 24 24"
									stroke="currentColor"
									stroke-width="2"
									aria-hidden="true"
									><path
										stroke-linecap="round"
										stroke-linejoin="round"
										d="M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10"
									/></svg
								>
							</button>
						{:else}
							{#if siteNavigation.links.length}
								<!-- Auf kleinen Bildschirmen fehlt die Menüleiste: Link zur Seitenübersicht -->
								<a
									href="/seiten"
									class="text-sm font-semibold text-slate-600 hover:text-institutional-navy lg:hidden"
									>Seiten</a
								>
							{/if}
							<a href="/login" class="ui-btn ui-btn-accent">Anmelden</a>
						{/if}
					</div>
				</div>
			</div>
		</nav>
	{/if}

	{#if auth.isAuthenticated && auth.user?.role !== 'ADMIN' && !isProjector}
		<QuizBanner />
	{/if}

	<!-- Mitteilung der Lehrkraft -->
	{#if announcement && announcement.content && !isProjector}
		<div class="relative z-40 border-b border-amber-200 bg-amber-50" role="status">
			<div class="container-institutional flex items-start gap-3 py-3">
				<span
					class="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-institutional-gold text-white"
					aria-hidden="true"
				>
					<svg
						class="h-4 w-4"
						fill="none"
						viewBox="0 0 24 24"
						stroke="currentColor"
						stroke-width="2.2"
						><path
							stroke-linecap="round"
							stroke-linejoin="round"
							d="M3 11v2a1 1 0 001 1h3l6 5V5L7 10H4a1 1 0 00-1 1zM17 9a4 4 0 010 6"
						/></svg
					>
				</span>
				<div class="min-w-0 flex-1 text-sm text-amber-950">
					<p class="font-semibold">Mitteilung der Lehrkraft</p>
					<p class="leading-relaxed whitespace-pre-line">{announcement.content}</p>
				</div>
				<button
					onclick={() => (announcement = null)}
					class="flex h-8 w-8 items-center justify-center text-amber-800 hover:bg-amber-100"
					aria-label="Mitteilung ausblenden"
				>
					<svg
						class="h-4 w-4"
						fill="none"
						viewBox="0 0 24 24"
						stroke="currentColor"
						stroke-width="2"
						aria-hidden="true"
						><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" /></svg
					>
				</button>
			</div>
		</div>
	{/if}

	<main
		class="flex-grow {isFrontpage || isProjector || isAdminArea
			? ''
			: 'pt-8 pb-12'} {tabLinks.length && !isProjector && !isAdminArea ? 'pb-24 lg:pb-12' : ''}"
	>
		<div class={isFrontpage || isProjector || isAdminArea ? '' : 'container-institutional'}>
			{@render children()}
		</div>
	</main>

	{#if !isProjector && !isAdminArea}
		<footer
			class="mt-auto border-t border-slate-200 bg-white py-8 {tabLinks.length
				? 'mb-16 lg:mb-0'
				: ''}"
		>
			<div
				class="container-institutional flex flex-col items-start justify-between gap-4 text-sm text-slate-500 md:flex-row md:items-center"
			>
				<p>
					<span class="font-semibold text-institutional-navy">CodePath</span> · Lerne IT-Berufe kennen
					und probiere sie aus.
				</p>
				<p class="flex gap-4">
					<a
						href="https://github.com/Finn-Kraemer/CodePath"
						target="_blank"
						rel="noopener"
						class="hover:text-institutional-navy">Open Source auf GitHub</a
					>
					<span>© 2026 CodePath</span>
				</p>
			</div>
		</footer>
	{/if}

	<!-- Handy: Leiste unten statt Hamburger-Menü -->
	{#if tabLinks.length && !isProjector && !isAdminArea}
		<nav
			class="fixed inset-x-0 bottom-0 z-50 border-t border-slate-200 bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur lg:hidden"
			aria-label="Schnellnavigation"
		>
			<div class="flex">
				{#each tabLinks as link (link.path)}
					<a
						href={link.path}
						aria-current={isActive(link.path) ? 'page' : undefined}
						class="flex min-h-14 flex-1 flex-col items-center justify-center gap-1 text-[11px] font-semibold {isActive(
							link.path
						)
							? 'text-institutional-navy'
							: 'text-slate-500'}"
					>
						<svg
							class="h-[22px] w-[22px]"
							fill="none"
							viewBox="0 0 24 24"
							stroke="currentColor"
							stroke-width="2"
							stroke-linecap="round"
							stroke-linejoin="round"
							aria-hidden="true"><path d={link.icon} /></svg
						>
						{link.name}
					</a>
				{/each}
				<button
					type="button"
					class="flex min-h-14 flex-1 flex-col items-center justify-center gap-1 text-[11px] font-semibold {mobileMenuOpen
						? 'text-institutional-navy'
						: 'text-slate-500'}"
					aria-expanded={mobileMenuOpen}
					onclick={() => (mobileMenuOpen = !mobileMenuOpen)}
				>
					<svg
						class="h-[22px] w-[22px]"
						fill="none"
						viewBox="0 0 24 24"
						stroke="currentColor"
						stroke-width="2"
						stroke-linecap="round"
						aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" /></svg
					>
					Mehr
				</button>
			</div>
		</nav>

		{#if mobileMenuOpen}
			<button
				type="button"
				class="fixed inset-0 z-40 cursor-default bg-slate-900/30 lg:hidden"
				aria-label="Menü schließen"
				onclick={() => (mobileMenuOpen = false)}
			></button>
			<div
				class="ui-card fixed inset-x-3 bottom-[4.5rem] z-50 flex flex-col gap-1 p-3 shadow-2xl lg:hidden"
				role="menu"
			>
				{#each allLinks.filter((l) => !tabLinks.some((t) => t.path === l.path)) as link (link.path)}
					<a
						href={link.path}
						role="menuitem"
						onclick={() => (mobileMenuOpen = false)}
						class="px-3 py-3 text-sm font-semibold {isActive(link.path)
							? 'bg-slate-100 text-institutional-navy'
							: 'text-slate-700'}">{link.name}</a
					>
				{/each}
				{#if siteNavigation.links.length}
					<a
						href="/seiten"
						role="menuitem"
						onclick={() => (mobileMenuOpen = false)}
						class="px-3 py-3 text-sm font-semibold text-slate-700">Seiten</a
					>
				{/if}
				{#if auth.user?.role === 'ADMIN'}
					<a
						href="/admin"
						role="menuitem"
						onclick={() => (mobileMenuOpen = false)}
						class="px-3 py-3 text-sm font-semibold text-slate-700">Leitstand</a
					>
				{/if}
				<button
					onclick={handleLogout}
					role="menuitem"
					class="mt-1 border-t border-slate-100 px-3 pt-3 pb-2 text-left text-sm font-semibold text-rose-700"
				>
					Abmelden
				</button>
			</div>
		{/if}
	{/if}
</div>
