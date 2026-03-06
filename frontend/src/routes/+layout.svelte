<script lang="ts">
	import './layout.css';
	import { auth } from '$lib/auth.svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';

	let { children } = $props();

	// Global Auth Guard
	$effect(() => {
		const path = page.url.pathname;
		const isPublicPage = path === '/login' || path === '/register' || path === '/';

		if (!auth.isAuthenticated && !isPublicPage) {
			goto('/login');
		}

		if (auth.isAuthenticated && (path === '/login' || path === '/register')) {
			goto('/modules');
		}
	});

	function handleLogout() {
		auth.logout();
		goto('/login');
	}
</script>

<svelte:head>
	<link
		href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;800&display=swap"
		rel="stylesheet"
	/>
	<title>CodePath | Kompetenzzentrum für Informationstechnik</title>
</svelte:head>

<div class="flex min-h-screen flex-col bg-white text-slate-900">
	<!-- Official Header / Sticky Nav -->
	<nav class="sticky top-0 z-50 border-b border-slate-200 bg-white shadow-sm">
		<div class="container-institutional">
			<div class="flex h-20 items-center justify-between">
				<div class="flex items-center gap-8">
					<!-- Logo Section -->
					<a href="/" class="flex items-center gap-3 rounded-none">
						<div
							class="flex h-10 w-10 items-center justify-center bg-institutional-navy text-white shadow-sm rounded-none"
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
							<span class="font-sans text-xl font-extrabold tracking-tighter uppercase leading-none text-institutional-navy">
								CodePath
							</span>
							<span class="text-[9px] font-bold uppercase tracking-widest text-slate-400">
								Kompetenzzentrum IT
							</span>
						</div>
					</a>

					<!-- Desktop Nav -->
					{#if auth.isAuthenticated}
						<div class="hidden h-10 w-px bg-slate-200 md:block"></div>
						<div class="hidden space-x-8 md:flex">
							<a
								href="/modules"
								class="text-[11px] font-bold tracking-widest text-slate-600 uppercase transition-colors hover:text-institutional-navy"
								>Module</a
							>
							<a
								href="/knowledge"
								class="text-[11px] font-bold tracking-widest text-slate-600 uppercase transition-colors hover:text-institutional-navy"
								>Wissen</a
							>
							<a
								href="/leaderboard"
								class="text-[11px] font-bold tracking-widest text-slate-600 uppercase transition-colors hover:text-institutional-navy"
								>Rangliste</a
							>
						</div>
					{/if}
				</div>

				<div class="flex items-center gap-4">
					{#if auth.isAuthenticated}
						<div class="hidden text-right lg:block">
							<p class="text-xs font-bold text-institutional-navy">{auth.user?.username}</p>
							<p class="text-[9px] font-bold tracking-widest text-slate-400 uppercase">
								{auth.user?.role}
							</p>
						</div>
						{#if auth.user?.role === 'ADMIN'}
							<a
								href="/admin"
								class="border border-institutional-navy/20 bg-slate-50 px-4 py-2 text-[10px] font-bold tracking-widest text-institutional-navy uppercase transition-all hover:bg-slate-100 rounded-none"
							>
								Admin-Bereich
							</a>
						{/if}
						<button
							onclick={handleLogout}
							class="bg-institutional-navy px-4 py-2 text-[10px] font-bold tracking-widest text-white uppercase shadow-sm transition-all hover:opacity-90 rounded-none"
						>
							Logout
						</button>
					{:else}
						<a
							href="/login"
							class="bg-institutional-gold px-6 py-2.5 text-xs font-bold tracking-widest text-white uppercase shadow-sm transition-all hover:opacity-90 rounded-none"
						>
							Anmelden
						</a>
					{/if}
				</div>
			</div>
		</div>
	</nav>

	<!-- Main Content Area -->
	<main class="flex-grow py-12">
		<div class="container-institutional">
			{@render children()}
		</div>
	</main>

	<!-- Official Footer -->
	<footer class="border-t border-slate-200 bg-slate-50 py-16">
		<div class="container-institutional">
			<div class="flex flex-col items-center justify-between gap-12 md:flex-row">
				<div class="flex flex-col gap-3">
					<p class="text-xs font-black tracking-[5px] text-institutional-navy uppercase">
						CodePath - Kompetenzzentrum IT
					</p>
					<p class="max-w-sm text-xs leading-relaxed text-slate-500">
						Lerne den Beruf Informatiker kennen, entwickle deine Fähigkeiten und starte deine Karriere in der IT-Branche mit CodePath.
					</p>
				</div>

				<!-- Partner Logos / Seals -->
				<div class="flex items-center gap-12 opacity-50 grayscale transition-all hover:grayscale-0">
					<div class="flex flex-col items-center gap-2">
						<span class="text-[8px] font-bold uppercase tracking-widest text-slate-500">Github</span>
					</div>
				</div>
			</div>

			<div class="mt-16 border-t border-slate-200 pt-8 text-center">
				<p class="font-mono text-[9px] tracking-[4px] text-slate-400 uppercase">
					© 2026 CodePath · Kompetenzzentrum IT · Alle Rechte vorbehalten
				</p>
			</div>
		</div>
	</footer>
</div>
