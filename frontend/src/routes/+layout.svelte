<script lang="ts">
	import './layout.css';
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';
	import { realtime } from '$lib/mqtt.svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';

	let { children } = $props();
	let announcement = $state<{ content: string; updatedAt?: string; displayMode?: string } | null>(null);
	let mobileMenuOpen = $state(false);

	// Global Auth Guard
	$effect(() => {
		const path = page.url.pathname;
		// Public pages that don't need auth
		const isPublicPage = path === '/login' || path === '/register' || path === '/';

		if (!auth.isAuthenticated && !isPublicPage) {
			goto('/login');
		}

		if (auth.isAuthenticated && (path === '/login' || path === '/register')) {
			goto('/modules');
		}
	});

	onMount(async () => {
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

	// Reactive MQTT updates
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

	// Helpers for special layouts
	let isFrontpage = $derived(page.url.pathname === '/');
    let isProjector = $derived(page.url.pathname === '/projector');
</script>

<svelte:head>
	<link
		href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;800&display=swap"
		rel="stylesheet"
	/>
	<title>CodePath | IT Berufsorientierung</title>
</svelte:head>

<div class="flex min-h-screen flex-col bg-white text-slate-900">
	<!-- Official Header / Sticky Nav -->
    {#if !isProjector}
        <nav class="sticky top-0 z-50 border-b border-slate-200 bg-white/80 backdrop-blur-md transition-all shadow-sm">
            <div class="container-institutional">
                <div class="flex h-20 items-center justify-between">
                    <div class="flex items-center gap-8">
                        <!-- Logo Section -->
                        <a href="/" class="flex items-center gap-3 rounded-none group">
                            <div
                                class="flex h-10 w-10 items-center justify-center bg-institutional-navy text-white shadow-sm rounded-none transition-transform group-hover:scale-105"
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
                                <span class="font-sans text-xl font-black tracking-tighter uppercase leading-none text-institutional-navy">
                                    CodePath
                                </span>
                                <span class="text-[9px] font-bold uppercase tracking-widest text-slate-400">
                                    Berufsorientierung IT
                                </span>
                            </div>
                        </a>

                        <!-- Desktop Nav -->
                        {#if auth.isAuthenticated}
                            <div class="hidden h-10 w-px bg-slate-200 md:block"></div>
                            <div class="hidden space-x-6 lg:flex">
                                {#each navLinks as link}
                                    <a
                                        href={link.path}
                                        class="relative text-[10px] font-black tracking-widest uppercase transition-all py-2
                                        {isActive(link.path) ? 'text-institutional-navy' : 'text-slate-500 hover:text-institutional-navy'}"
                                    >
                                        {link.name}
                                        {#if isActive(link.path)}
                                            <span class="absolute bottom-0 left-0 h-0.5 w-full bg-institutional-gold animate-in fade-in slide-in-from-left-2"></span>
                                        {/if}
                                    </a>
                                {/each}
                            </div>
                        {/if}
                    </div>

                    <div class="flex items-center gap-4">
                        {#if auth.isAuthenticated}
                            <!-- Desktop Auth Info -->
                            <div class="hidden items-center gap-6 md:flex">
                                <a href="/profile" class="flex flex-col items-end hover:opacity-70 transition-opacity">
                                    <p class="text-xs font-black text-institutional-navy leading-none">{auth.user?.username}</p>
                                    <p class="mt-1 text-[8px] font-bold tracking-widest text-slate-400 uppercase">
                                        {auth.user?.role}
                                    </p>
                                </a>
                                
                                {#if auth.user?.role === 'ADMIN'}
                                    <a
                                        href="/admin"
                                        class="group flex items-center gap-2 border border-institutional-navy/10 bg-slate-50 px-4 py-2.5 text-[9px] font-black tracking-widest text-institutional-navy uppercase transition-all hover:bg-institutional-navy hover:text-white rounded-none"
                                    >
                                        <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                                        </svg>
                                        Leitstand
                                    </a>
                                {/if}
                                
                                <button
                                    onclick={handleLogout}
                                    class="bg-institutional-navy px-5 py-2.5 text-[9px] font-black tracking-[3px] text-white uppercase shadow-sm transition-all hover:bg-slate-800 hover:shadow-md rounded-none"
                                >
                                    Abmelden
                                </button>
                            </div>

                            <!-- Mobile Menu Toggle -->
                            <button 
                                class="flex h-10 w-10 flex-col items-center justify-center gap-1.5 lg:hidden"
                                onclick={() => mobileMenuOpen = !mobileMenuOpen}
                            >
                                <span class="h-0.5 w-6 bg-institutional-navy transition-all {mobileMenuOpen ? 'translate-y-2 rotate-45' : ''}"></span>
                                <span class="h-0.5 w-6 bg-institutional-navy transition-all {mobileMenuOpen ? 'opacity-0' : ''}"></span>
                                <span class="h-0.5 w-6 bg-institutional-navy transition-all {mobileMenuOpen ? '-translate-y-2 -rotate-45' : ''}"></span>
                            </button>
                        {:else}
                            <div class="flex gap-4 items-center">
                                <a
                                    href="/login"
                                    class="bg-institutional-gold px-6 py-2.5 text-[10px] font-black tracking-[3px] text-white uppercase shadow-sm transition-all hover:opacity-90 rounded-none"
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
                <div class="border-t border-slate-100 bg-white p-6 lg:hidden animate-in slide-in-from-top fade-in duration-300">
                    <div class="flex flex-col gap-4">
                        {#each navLinks as link}
                            <a 
                                href={link.path} 
                                onclick={() => mobileMenuOpen = false}
                                class="px-4 py-3 font-black text-[11px] tracking-[2px] uppercase {isActive(link.path) ? 'bg-slate-50 text-institutional-navy border-l-4 border-institutional-gold' : 'text-slate-500'}"
                            >
                                {link.name}
                            </a>
                        {/each}
                        
                        <div class="my-4 h-px bg-slate-100"></div>
                        
                        <div class="px-4 py-2">
                            <p class="text-[10px] font-black text-institutional-navy uppercase">{auth.user?.username}</p>
                            <p class="text-[8px] font-bold text-slate-400 uppercase tracking-widest">{auth.user?.role}</p>
                        </div>

                        {#if auth.user?.role === 'ADMIN'}
                            <a 
                                href="/admin" 
                                onclick={() => mobileMenuOpen = false}
                                class="px-4 py-3 font-black text-[11px] tracking-[2px] uppercase text-institutional-navy"
                            >
                                Administrator-Bereich
                            </a>
                        {/if}

                        <button 
                            onclick={handleLogout}
                            class="mt-4 w-full bg-institutional-navy py-4 text-[10px] font-black tracking-[4px] text-white uppercase rounded-none"
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
		<div class="bg-institutional-navy py-4 shadow-inner border-b border-white/10 relative z-40">
			<div class="container-institutional flex items-center justify-between gap-8">
				<div class="flex items-center gap-4 text-white">
					<div class="flex h-8 w-8 items-center justify-center bg-white/10 text-white shadow-sm rounded-none border border-white/20">
						<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
							<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
						</svg>
					</div>
					<div>
						<p class="text-[9px] font-black uppercase tracking-[3px] text-white/50 mb-0.5">Offizielle Mitteilung der Lehrkraft</p>
						<p class="font-sans text-xs font-bold leading-relaxed whitespace-pre-line">{announcement.content}</p>
					</div>
				</div>
				<button 
					onclick={() => announcement = null}
					class="text-white/40 hover:text-white transition-colors p-2"
				>
					<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
						<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
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
        <footer class="border-t border-slate-200 bg-slate-50 py-16 mt-auto">
            <div class="container-institutional">
                <div class="flex flex-col items-center justify-between gap-12 md:flex-row">
                    <div class="flex flex-col gap-3">
                        <p class="text-xs font-black tracking-[5px] text-institutional-navy uppercase">
                            CodePath - IT Berufsorientierung
                        </p>
                        <p class="max-w-sm text-xs leading-relaxed text-slate-500">
                            Lerne den Beruf Informatiker kennen, entwickle deine Fähigkeiten und starte deine Karriere in der IT-Branche mit CodePath.
                        </p>
                    </div>

                    <div class="flex items-center gap-12 opacity-50 grayscale transition-all hover:grayscale-0">
                        <a 
                            href="https://github.com/Finn-Kraemer/CodePath" 
                            target="_blank"
                            class="flex flex-col items-center gap-2 group"
                        >
                            <span class="text-[8px] font-bold uppercase tracking-widest text-slate-500 group-hover:text-institutional-navy transition-colors">Github Open Source</span>
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
