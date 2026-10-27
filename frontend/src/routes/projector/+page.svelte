<script lang="ts">
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';
	import { realtime } from '$lib/realtime.svelte';

	interface Entry {
		username: string;
		displayName: string;
		points: number;
		rank: number;
	}

	let leaderboard = $state<Entry[]>([]);
	let announcement = $state<string | null>(null);
	let loading = $state(true);
	let now = $state(new Date());

	async function fetchData() {
		try {
			// Parallel fetch
			const [annRes] = await Promise.all([auth.apiFetch('/api/common/announcement')]);

			if (annRes.ok) {
				const annData = await annRes.json();
				announcement = annData.content || null;
			}

			// Fetch full leaderboard
			await fetchFullLeaderboard();
		} catch (e) {
			console.error('Polling error', e);
		} finally {
			loading = false;
		}
	}

	async function fetchFullLeaderboard() {
		try {
			const res = await auth.apiFetch('/api/leaderboard');
			if (res.ok) {
				const data = await res.json();
				leaderboard = data.entries.slice(0, 8);
			}
		} catch (e) {
			console.error('Leaderboard fetch failed', e);
		}
	}

	onMount(() => {
		fetchData();
		realtime.connect();

		// 30s Polling Fallback
		const pollInterval = setInterval(fetchData, 30000);

		// Clock Interval
		const clockInterval = setInterval(() => (now = new Date()), 1000);

		return () => {
			clearInterval(pollInterval);
			clearInterval(clockInterval);
		};
	});

	// Reagiere auf MQTT Updates
	$effect(() => {
		if (realtime.leaderboardUpdateTrigger > 0) {
			fetchFullLeaderboard();
		}
	});

	$effect(() => {
		if (realtime.lastAnnouncement) {
			announcement = realtime.lastAnnouncement.content;
		}
	});
</script>

<svelte:head>
	<title>CodePath | Live Dashboard</title>
</svelte:head>

<!-- Full screen container, escaping layout padding if any remained -->
<div class="fixed inset-0 z-[100] flex flex-col overflow-hidden bg-slate-950 font-sans text-white">
	<!-- Top Bar -->
	<div
		class="relative z-20 flex items-center justify-between border-b-4 border-institutional-gold bg-institutional-navy p-8 shadow-2xl"
	>
		<div class="flex items-center gap-6">
			<div class="bg-white p-3">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-12 w-12 text-institutional-navy"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="3"
				>
					<path d="m18 16 4-4-4-4"></path><path d="m6 8-4 4 4 4"></path><path d="m14.5 4-5 16"
					></path>
				</svg>
			</div>
			<div>
				<h1 class="text-4xl leading-none font-black tracking-tighter uppercase">CodePath Live</h1>
				<p
					class="mt-1 font-mono text-sm font-bold tracking-[5px] text-institutional-gold uppercase"
				>
					Berufsorientierungstag IT 2026
				</p>
			</div>
		</div>
		<div class="text-right">
			<p class="font-mono text-4xl font-black text-slate-200">
				{now.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
			</p>
			<div class="mt-2 flex items-center justify-end gap-2">
				<div class="h-3 w-3 animate-pulse rounded-full bg-teal-500"></div>
				<span class="text-xs font-bold tracking-[3px] text-teal-500 uppercase">System Live</span>
			</div>
		</div>
	</div>

	<!-- Main Grid -->
	<div class="relative z-10 grid flex-1 grid-cols-1 gap-12 overflow-hidden p-12 lg:grid-cols-12">
		<!-- Announcement Area -->
		<div class="flex h-full flex-col lg:col-span-5">
			<section
				class="relative flex flex-1 flex-col justify-center overflow-hidden border-2 border-slate-800 bg-slate-900 p-12 shadow-2xl"
			>
				<div class="absolute top-0 left-0 h-full w-2 bg-institutional-gold"></div>
				<span
					class="mb-8 block font-mono text-sm font-bold tracking-[5px] text-institutional-gold uppercase"
					>Aktuelle Mitteilung der Lehrkraft</span
				>

				{#if announcement}
					<div class="animate-in fade-in slide-in-from-left-4 duration-700">
						<p
							class="text-4xl leading-tight font-black tracking-tight whitespace-pre-line text-white uppercase md:text-5xl lg:text-6xl"
						>
							{announcement}
						</p>
					</div>
				{:else}
					<p class="text-4xl font-black tracking-tighter text-slate-800 uppercase italic">
						Bereit für neue Durchsagen...
					</p>
				{/if}
			</section>
		</div>

		<!-- Leaderboard Area -->
		<div class="flex h-full flex-col overflow-hidden lg:col-span-7">
			<div class="mb-8 flex items-end justify-between">
				<h2 class="text-5xl font-black tracking-tighter uppercase">Live Ranking</h2>
				<div class="flex items-center gap-4">
					<span class="font-mono text-lg tracking-widest text-slate-500 uppercase"
						>Top Performance</span
					>
					<div class="h-px w-24 bg-slate-800"></div>
				</div>
			</div>

			<div class="flex-1 space-y-3 overflow-hidden">
				{#each leaderboard as entry (entry.username)}
					<div
						class="animate-in fade-in slide-in-from-right-4 flex items-center justify-between border border-slate-800 bg-slate-900 p-6 transition-all duration-500
                        {entry.rank === 1
							? 'border-institutional-gold bg-institutional-gold/10'
							: ''}"
					>
						<div class="flex items-center gap-10">
							<span
								class="w-20 text-center font-mono text-5xl font-black
                                {entry.rank === 1
									? 'text-institutional-gold'
									: entry.rank === 2
										? 'text-slate-400'
										: entry.rank === 3
											? 'text-amber-700'
											: 'text-slate-700'}"
							>
								{entry.rank.toString().padStart(2, '0')}
							</span>
							<div class="flex flex-col">
								<span class="text-3xl font-bold tracking-tight uppercase"
									>{entry.displayName || entry.username}</span
								>
								<span class="mt-1 font-mono text-xs tracking-widest text-slate-500 uppercase"
									>ID: {entry.username}</span
								>
							</div>
						</div>
						<div class="flex items-center gap-6">
							<div class="text-right">
								<span class="font-mono text-4xl font-black text-institutional-gold"
									>{entry.points}</span
								>
								<span
									class="mt-1 block text-[10px] font-bold tracking-[2px] text-slate-500 uppercase"
									>Score</span
								>
							</div>
							{#if entry.rank === 1}
								<div class="text-5xl drop-shadow-lg">👑</div>
							{/if}
						</div>
					</div>
				{/each}
			</div>
		</div>
	</div>

	<!-- Bottom Ticker -->
	<div
		class="relative z-20 flex items-center overflow-hidden border-t border-white/5 bg-slate-950 p-6"
	>
		<div class="animate-ticker flex items-center gap-24 whitespace-nowrap">
			<span class="text-xs font-bold tracking-[6px] text-slate-600 uppercase"
				>+++ CodePath IT-Workshop 2026 +++ Echtzeit-Leistungsdaten aktiv +++ Viel Erfolg bei den
				Modulen +++ Updates via Message Queue +++</span
			>
			<span class="text-xs font-bold tracking-[6px] text-slate-600 uppercase"
				>+++ CodePath IT-Workshop 2026 +++ Echtzeit-Leistungsdaten aktiv +++ Viel Erfolg bei den
				Aufgaben +++ Leaderboard Sync: 1000ms +++</span
			>
		</div>
	</div>
</div>

<style>
	@keyframes ticker {
		0% {
			transform: translateX(0);
		}
		100% {
			transform: translateX(-50%);
		}
	}
	.animate-ticker {
		animation: ticker 40s linear infinite;
	}

	/* Ensure no scrollbars ever show on projector */
	:global(body) {
		overflow: hidden !important;
	}
</style>
