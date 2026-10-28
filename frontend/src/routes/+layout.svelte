<script lang="ts">
	import './layout.css';
	import '@fontsource/inter/400.css';
	import '@fontsource/inter/600.css';
	import '@fontsource/inter/800.css';
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';
	import { realtime } from '$lib/realtime.svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { siteNavigation } from '$lib/site/navigation.svelte';

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

	onMount(async () => {
		await auth.refresh();
		if (auth.isAuthenticated) {
			realtime.connect();
			try {
				const res = await auth.apiFetch('/api/common/announcement');
				if (res.ok) {
					const data = await res.json();
					if (data.content && (data.displayMode === 'HEADER' || data.displayMode === 'BOTH')) {
						announcement = data;
					}
				}
			} catch (e) {
				console.error('Failed to fetch announcement', e);
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

	const navLinks = [
		{ name: 'Module', path: '/modules' },
		{ name: 'Wissen', path: '/knowledge' },
		{ name: 'Info', path: '/info' },
		{ name: 'Feedback', path: '/feedback' },
		{ name: 'Rangliste', path: '/leaderboard' }
	];

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
</script>

<svelte:head>
	<title>CodePath | IT Berufsorientierung</title>
</svelte:head>

<svelte:window
	onclick={(e) => {
		if (pagesMenuOpen && pagesMenu && !pagesMenu.contains(e.target as Node)) pagesMenuOpen = false;
	}}
	onkeydown={(e) => {
		if (e.key === 'Escape') pagesMenuOpen = false;
	}}
/>

<div class="flex min-h-screen flex-col bg-white text-slate-900">
	<!-- Official Header / Sticky Nav -->
	{#if !isProjector}
		<nav
			class="sticky top-0 z-50 border-b border-slate-200 bg-white/80 shadow-sm backdrop-blur-md transition-all"
		>
			<div class="container-institutional">
				<div class="flex h-20 items-center justify-between">
					<div class="flex items-center gap-8">
						<!-- Logo Section -->
						<a href="/" class="group flex items-center gap-3 rounded-none">
							<div
								class="flex h-10 w-10 items-center justify-center rounded-none bg-institutional-navy text-white shadow-sm transition-transform group-hover:scale-105"
							>
								<svg
									xmlns="http://www.w3.org/2000/svg"
									class="h-6 w-6"
									viewBox="0 0 24 24"
									fill="none"
									stroke="currentColor"
									stroke-width="2.5"
									stroke-linecap="round"
									stroke-linejoin="round"
								>
									<path d="m18 16 4-4-4-4"></path>
									<path d="m6 8-4 4 4 4"></path>
									<path d="m14.5 4-5 16"></path>
								</svg>
							</div>
							<div class="flex flex-col">
								<span
									class="font-sans text-xl leading-none font-black tracking-tighter text-institutional-navy uppercase"
								>
									CodePath
								</span>
								<span class="text-[9px] font-bold tracking-widest text-slate-400 uppercase">
									Berufsorientierung IT
								</span>
							</div>
						</a>

						<!-- Desktop Nav -->
						{#if allLinks.length || siteNavigation.links.length}
							<div class="hidden h-10 w-px bg-slate-200 md:block"></div>
							<div class="hidden space-x-6 lg:flex">
								{#each allLinks as link (link.path)}
									<a
										href={link.path}
										class="relative py-2 text-[10px] font-black tracking-widest uppercase transition-all
                                        {isActive(link.path)
											? 'text-institutional-navy'
											: 'text-slate-500 hover:text-institutional-navy'}"
									>
										{link.name}
										{#if isActive(link.path)}
											<span
												class="animate-in fade-in slide-in-from-left-2 absolute bottom-0 left-0 h-0.5 w-full bg-institutional-gold"
											></span>
										{/if}
									</a>
								{/each}
								{#if siteNavigation.links.length}
									<div class="relative" bind:this={pagesMenu}>
										<button
											type="button"
											class="relative flex items-center gap-1 py-2 text-[10px] font-black tracking-widest uppercase transition-all {isActive(
												'/seiten'
											)
												? 'text-institutional-navy'
												: 'text-slate-500 hover:text-institutional-navy'}"
											aria-expanded={pagesMenuOpen}
											aria-haspopup="true"
											onclick={() => (pagesMenuOpen = !pagesMenuOpen)}
										>
											Seiten
											<svg
												class="h-3 w-3"
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
												class="absolute top-full left-0 z-50 mt-3 w-72 border border-slate-200 bg-white py-2 shadow-xl"
												role="menu"
											>
												{#each siteNavigation.links as link (link.slug)}
													<a
														href={`/seiten/${link.slug}`}
														role="menuitem"
														class="block px-4 py-2 hover:bg-slate-50 {isActive(
															`/seiten/${link.slug}`
														)
															? 'border-l-4 border-institutional-gold'
															: ''}"
													>
														<span class="block text-xs font-bold text-institutional-navy"
															>{link.title}</span
														>
														{#if link.description}
															<span class="block truncate text-[11px] text-slate-500"
																>{link.description}</span
															>
														{/if}
													</a>
												{/each}
												<a
													href="/seiten"
													role="menuitem"
													class="mt-2 block border-t border-slate-100 px-4 pt-3 pb-1 text-[10px] font-black tracking-widest text-institutional-gold uppercase hover:underline"
													>Alle Seiten im Überblick →</a
												>
											</div>
										{/if}
									</div>
								{/if}
							</div>
						{/if}
					</div>

					<div class="flex items-center gap-4">
						{#if auth.isAuthenticated}
							<!-- Desktop Auth Info -->
							<div class="hidden items-center gap-6 md:flex">
								<a
									href="/profile"
									class="flex flex-col items-end transition-opacity hover:opacity-70"
								>
									<p class="text-xs leading-none font-black text-institutional-navy">
										{auth.user?.username}
									</p>
									<p class="mt-1 text-[8px] font-bold tracking-widest text-slate-400 uppercase">
										{auth.user?.role}
									</p>
								</a>

								{#if auth.user?.role === 'ADMIN'}
									<a
										href="/admin"
										class="group flex items-center gap-2 rounded-none border border-institutional-navy/10 bg-slate-50 px-4 py-2.5 text-[9px] font-black tracking-widest text-institutional-navy uppercase transition-all hover:bg-institutional-navy hover:text-white"
									>
										<svg
											xmlns="http://www.w3.org/2000/svg"
											class="h-3 w-3"
											fill="none"
											viewBox="0 0 24 24"
											stroke="currentColor"
										>
											<path
												stroke-linecap="round"
												stroke-linejoin="round"
												stroke-width="2"
												d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
											/>
										</svg>
										Leitstand
									</a>
								{/if}

								<button
									onclick={handleLogout}
									class="rounded-none bg-institutional-navy px-5 py-2.5 text-[9px] font-black tracking-[3px] text-white uppercase shadow-sm transition-all hover:bg-slate-800 hover:shadow-md"
								>
									Abmelden
								</button>
							</div>

							<!-- Mobile Menu Toggle -->
							<button
								class="flex h-10 w-10 flex-col items-center justify-center gap-1.5 lg:hidden"
								onclick={() => (mobileMenuOpen = !mobileMenuOpen)}
							>
								<span
									class="h-0.5 w-6 bg-institutional-navy transition-all {mobileMenuOpen
										? 'translate-y-2 rotate-45'
										: ''}"
								></span>
								<span
									class="h-0.5 w-6 bg-institutional-navy transition-all {mobileMenuOpen
										? 'opacity-0'
										: ''}"
								></span>
								<span
									class="h-0.5 w-6 bg-institutional-navy transition-all {mobileMenuOpen
										? '-translate-y-2 -rotate-45'
										: ''}"
								></span>
							</button>
						{:else}
							<div class="flex items-center gap-4">
								{#if siteNavigation.links.length}
									<!-- Auf kleinen Bildschirmen fehlt die Menüleiste: Link zur Seitenübersicht -->
									<a
										href="/seiten"
										class="text-[10px] font-black tracking-widest text-slate-500 uppercase hover:text-institutional-navy lg:hidden"
										>Seiten</a
									>
								{/if}
								<a
									href="/login"
									class="rounded-none bg-institutional-gold px-6 py-2.5 text-[10px] font-black tracking-[3px] text-white uppercase shadow-sm transition-all hover:opacity-90"
								>
									Anmelden
								</a>
							</div>
						{/if}
					</div>
				</div>
			</div>

			<!-- Mobile Navigation -->
			{#if mobileMenuOpen && auth.isAuthenticated}
				<div
					class="animate-in slide-in-from-top fade-in border-t border-slate-100 bg-white p-6 duration-300 lg:hidden"
				>
					<div class="flex flex-col gap-4">
						{#each allLinks as link (link.path)}
							<a
								href={link.path}
								onclick={() => (mobileMenuOpen = false)}
								class="px-4 py-3 text-[11px] font-black tracking-[2px] uppercase {isActive(
									link.path
								)
									? 'border-l-4 border-institutional-gold bg-slate-50 text-institutional-navy'
									: 'text-slate-500'}"
							>
								{link.name}
							</a>
						{/each}
						{#if siteNavigation.links.length}
							<a
								href="/seiten"
								onclick={() => (mobileMenuOpen = false)}
								class="px-4 py-3 text-[11px] font-black tracking-[2px] uppercase {page.url
									.pathname === '/seiten'
									? 'border-l-4 border-institutional-gold bg-slate-50 text-institutional-navy'
									: 'text-slate-500'}"
							>
								Seiten
							</a>
						{/if}

						<div class="my-4 h-px bg-slate-100"></div>

						<div class="px-4 py-2">
							<p class="text-[10px] font-black text-institutional-navy uppercase">
								{auth.user?.username}
							</p>
							<p class="text-[8px] font-bold tracking-widest text-slate-400 uppercase">
								{auth.user?.role}
							</p>
						</div>

						{#if auth.user?.role === 'ADMIN'}
							<a
								href="/admin"
								onclick={() => (mobileMenuOpen = false)}
								class="px-4 py-3 text-[11px] font-black tracking-[2px] text-institutional-navy uppercase"
							>
								Administrator-Bereich
							</a>
						{/if}

						<button
							onclick={handleLogout}
							class="mt-4 w-full rounded-none bg-institutional-navy py-4 text-[10px] font-black tracking-[4px] text-white uppercase"
						>
							Abmelden
						</button>
					</div>
				</div>
			{/if}
		</nav>
	{/if}

	<!-- Global Announcement Banner -->
	{#if announcement && announcement.content && !isProjector}
		<div class="relative z-40 border-b border-white/10 bg-institutional-navy py-4 shadow-inner">
			<div class="container-institutional flex items-center justify-between gap-8">
				<div class="flex items-center gap-4 text-white">
					<div
						class="flex h-8 w-8 items-center justify-center rounded-none border border-white/20 bg-white/10 text-white shadow-sm"
					>
						<svg
							xmlns="http://www.w3.org/2000/svg"
							class="h-4 w-4"
							fill="none"
							viewBox="0 0 24 24"
							stroke="currentColor"
						>
							<path
								stroke-linecap="round"
								stroke-linejoin="round"
								stroke-width="2"
								d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z"
							/>
						</svg>
					</div>
					<div>
						<p class="mb-0.5 text-[9px] font-black tracking-[3px] text-white/50 uppercase">
							Offizielle Mitteilung der Lehrkraft
						</p>
						<p class="font-sans text-xs leading-relaxed font-bold whitespace-pre-line">
							{announcement.content}
						</p>
					</div>
				</div>
				<button
					onclick={() => (announcement = null)}
					class="p-2 text-white/40 transition-colors hover:text-white"
				>
					<svg
						xmlns="http://www.w3.org/2000/svg"
						class="h-4 w-4"
						fill="none"
						viewBox="0 0 24 24"
						stroke="currentColor"
					>
						<path
							stroke-linecap="round"
							stroke-linejoin="round"
							stroke-width="2"
							d="M6 18L18 6M6 6l12 12"
						/>
					</svg>
				</button>
			</div>
		</div>
	{/if}

	<!-- Main Content Area -->
	<main class="flex-grow {isFrontpage || isProjector ? '' : 'py-12'}">
		<div class={isFrontpage || isProjector ? '' : 'container-institutional'}>
			{@render children()}
		</div>
	</main>

	<!-- Official Footer -->
	{#if !isProjector}
		<footer class="mt-auto border-t border-slate-200 bg-slate-50 py-16">
			<div class="container-institutional">
				<div class="flex flex-col items-center justify-between gap-12 md:flex-row">
					<div class="flex flex-col gap-3">
						<p class="text-xs font-black tracking-[5px] text-institutional-navy uppercase">
							CodePath - IT Berufsorientierung
						</p>
						<p class="max-w-sm text-xs leading-relaxed text-slate-500">
							Lerne den Beruf Informatiker kennen, entwickle deine Fähigkeiten und starte deine
							Karriere in der IT-Branche mit CodePath.
						</p>
					</div>

					<div
						class="flex items-center gap-12 opacity-50 grayscale transition-all hover:grayscale-0"
					>
						<a
							href="https://github.com/Finn-Kraemer/CodePath"
							target="_blank"
							class="group flex flex-col items-center gap-2"
						>
							<span
								class="text-[8px] font-bold tracking-widest text-slate-500 uppercase transition-colors group-hover:text-institutional-navy"
								>Github Open Source</span
							>
						</a>
					</div>
				</div>

				<div class="mt-16 border-t border-slate-200 pt-8 text-center">
					<p class="font-mono text-[9px] tracking-[4px] text-slate-400 uppercase">
						© 2026 CodePath · IT Berufsorientierung · Alle Rechte vorbehalten
					</p>
				</div>
			</div>
		</footer>
	{/if}
</div>
