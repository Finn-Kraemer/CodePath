<script lang="ts">
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';
	import { realtime } from '$lib/mqtt.svelte';

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
			const [annRes] = await Promise.all([
				auth.apiFetch('/api/common/announcement')
			]);

			if (annRes.ok) {
				const annData = await annRes.json();
				announcement = annData.content || null;
			}

            // Fetch full leaderboard
            await fetchFullLeaderboard();
		} catch (e) {
			console.error("Polling error", e);
		} finally {
			loading = false;
		}
	}

    async function fetchFullLeaderboard() {
        try {
            const res = await auth.apiFetch('/api/admin/students'); 
            if (res.ok) {
                const data = await res.json();
                leaderboard = data
                    .sort((a: any, b: any) => b.totalPoints - a.totalPoints)
                    .slice(0, 8) 
                    .map((s: any, i: number) => ({
                        username: s.username,
                        displayName: s.displayName,
                        points: s.totalPoints,
                        rank: i + 1
                    }));
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
        const clockInterval = setInterval(() => now = new Date(), 1000);
        
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
<div class="fixed inset-0 bg-slate-950 text-white overflow-hidden flex flex-col font-sans z-[100]">
	
    <!-- Top Bar -->
	<div class="bg-institutional-navy p-8 border-b-4 border-institutional-gold flex justify-between items-center shadow-2xl relative z-20">
		<div class="flex items-center gap-6">
			<div class="bg-white p-3">
				<svg xmlns="http://www.w3.org/2000/svg" class="h-12 w-12 text-institutional-navy" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
					<path d="m18 16 4-4-4-4"></path><path d="m6 8-4 4 4 4"></path><path d="m14.5 4-5 16"></path>
				</svg>
			</div>
			<div>
				<h1 class="text-4xl font-black tracking-tighter uppercase leading-none">CodePath Live</h1>
				<p class="text-institutional-gold font-mono text-sm font-bold tracking-[5px] uppercase mt-1">Berufsorientierungstag IT 2026</p>
			</div>
		</div>
		<div class="text-right">
			<p class="text-slate-200 font-mono text-4xl font-black">{now.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}</p>
			<div class="flex items-center gap-2 justify-end mt-2">
				<div class="h-3 w-3 rounded-full bg-teal-500 animate-pulse"></div>
				<span class="text-xs font-bold text-teal-500 uppercase tracking-[3px]">System Live</span>
			</div>
		</div>
	</div>

	<!-- Main Grid -->
	<div class="flex-1 p-12 grid grid-cols-1 lg:grid-cols-12 gap-12 overflow-hidden relative z-10">
		
		<!-- Announcement Area -->
		<div class="lg:col-span-5 flex flex-col h-full">
			<section class="flex-1 bg-slate-900 border-2 border-slate-800 p-12 flex flex-col justify-center relative overflow-hidden shadow-2xl">
                <div class="absolute top-0 left-0 w-2 h-full bg-institutional-gold"></div>
                <span class="font-mono text-sm font-bold text-institutional-gold uppercase tracking-[5px] mb-8 block">Aktuelle Mitteilung der Lehrkraft</span>
                
                {#if announcement}
                    <div class="animate-in fade-in slide-in-from-left-4 duration-700">
                        <p class="text-4xl md:text-5xl lg:text-6xl font-black leading-tight text-white uppercase tracking-tight whitespace-pre-line">
                            {announcement}
                        </p>
                    </div>
                {:else}
                    <p class="text-4xl font-black text-slate-800 uppercase italic tracking-tighter">Bereit für neue Durchsagen...</p>
                {/if}
			</section>
		</div>

		<!-- Leaderboard Area -->
		<div class="lg:col-span-7 flex flex-col h-full overflow-hidden">
            <div class="flex items-end justify-between mb-8">
			    <h2 class="text-5xl font-black uppercase tracking-tighter">Live Ranking</h2>
                <div class="flex items-center gap-4">
                    <span class="font-mono text-slate-500 text-lg uppercase tracking-widest">Top Performance</span>
                    <div class="h-px w-24 bg-slate-800"></div>
                </div>
            </div>
			
			<div class="flex-1 space-y-3 overflow-hidden">
				{#each leaderboard as entry (entry.username)}
					<div
						class="flex items-center justify-between bg-slate-900 border border-slate-800 p-6 transition-all duration-500 animate-in fade-in slide-in-from-right-4
                        {entry.rank === 1 ? 'border-institutional-gold bg-institutional-gold/10' : ''}"
					>
						<div class="flex items-center gap-10">
							<span class="font-mono text-5xl font-black w-20 text-center
                                {entry.rank === 1 ? 'text-institutional-gold' : 
                                 entry.rank === 2 ? 'text-slate-400' : 
                                 entry.rank === 3 ? 'text-amber-700' : 'text-slate-700'}">
								{entry.rank.toString().padStart(2, '0')}
							</span>
							<div class="flex flex-col">
								<span class="text-3xl font-bold uppercase tracking-tight">{entry.displayName || entry.username}</span>
								<span class="text-xs font-mono text-slate-500 uppercase tracking-widest mt-1">ID: {entry.username}</span>
							</div>
						</div>
						<div class="flex items-center gap-6">
                            <div class="text-right">
                                <span class="text-4xl font-mono font-black text-institutional-gold">{entry.points}</span>
                                <span class="text-[10px] font-bold text-slate-500 uppercase block tracking-[2px] mt-1">Score</span>
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
    <div class="bg-slate-950 p-6 border-t border-white/5 flex items-center overflow-hidden relative z-20">
        <div class="whitespace-nowrap animate-ticker flex gap-24 items-center">
            <span class="text-xs font-bold text-slate-600 uppercase tracking-[6px]">+++ CodePath IT-Workshop 2026 +++ Echtzeit-Leistungsdaten aktiv +++ Viel Erfolg bei den Modulen +++ Updates via Message Queue +++</span>
            <span class="text-xs font-bold text-slate-600 uppercase tracking-[6px]">+++ CodePath IT-Workshop 2026 +++ Echtzeit-Leistungsdaten aktiv +++ Viel Erfolg bei den Aufgaben +++ Leaderboard Sync: 1000ms +++</span>
        </div>
    </div>
</div>

<style>
    @keyframes ticker {
        0% { transform: translateX(0); }
        100% { transform: translateX(-50%); }
    }
    .animate-ticker {
        animation: ticker 40s linear infinite;
    }
    
    /* Ensure no scrollbars ever show on projector */
    :global(body) {
        overflow: hidden !important;
    }
</style>
