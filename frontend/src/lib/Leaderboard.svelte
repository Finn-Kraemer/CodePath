<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { auth } from '$lib/auth.svelte';

	interface Entry {
		username: string;
		displayName: string;
		points: number;
		rank: number;
	}

	interface LeaderboardData {
		topThree: Entry[];
		ownEntry: Entry;
	}

	let data = $state<LeaderboardData | null>(null);
	let pollInterval: any;

	async function fetchLeaderboard() {
		try {
			const res = await auth.apiFetch('/api/leaderboard');
			if (res.ok) {
				data = await res.json();
			}
		} catch (e) {
			console.error('Leaderboard fetch failed', e);
		}
	}

	onMount(() => {
		fetchLeaderboard();
		pollInterval = setInterval(fetchLeaderboard, 10000);
	});

	onDestroy(() => {
		if (pollInterval) clearInterval(pollInterval);
	});
</script>

<div class="border border-slate-200 bg-white shadow-sm rounded-none">
	<div class="p-8">
		{#if data}
			<div class="space-y-2">
				{#each data.topThree as entry (entry.username)}
					<div
						class="flex items-center justify-between border p-6 transition-colors rounded-none {entry.username ===
						auth.user?.username
							? 'border-institutional-gold bg-amber-50'
							: 'border-slate-100 bg-slate-50'}"
					>
						<div class="flex items-center gap-6">
							<span
								class="font-mono text-xl font-black {entry.rank === 1
									? 'text-institutional-gold'
									: entry.rank === 2
										? 'text-slate-400'
										: 'text-amber-700'}"
							>
								#0{entry.rank}
							</span>
							<div>
								<p class="font-sans text-sm font-bold text-institutional-navy leading-none">
									{entry.displayName || entry.username}
								</p>
								<p class="mt-1 font-mono text-[9px] font-bold tracking-tighter text-slate-400 uppercase">
									@{entry.username}
								</p>
							</div>
						</div>
						<div class="text-right">
							<span class="block font-mono text-sm font-black text-institutional-navy"
								>{entry.points}</span
							>
							<span class="text-[8px] font-bold tracking-widest text-slate-400 uppercase">Punkte</span>
						</div>
					</div>
				{/each}

				{#if data.ownEntry.rank > 3}
					<div class="flex justify-center py-4">
						<div class="h-8 w-px bg-slate-200"></div>
					</div>
					<div
						class="flex items-center justify-between border border-institutional-gold bg-amber-50 p-6 rounded-none"
					>
						<div class="flex items-center gap-6">
							<span class="font-mono text-xl font-black text-institutional-navy">
								#{data.ownEntry.rank}
							</span>
							<div>
								<p class="font-sans text-sm font-bold text-institutional-navy leading-none">
									{data.ownEntry.displayName || data.ownEntry.username}
								</p>
								<p class="mt-1 font-mono text-[9px] font-bold tracking-tighter text-slate-400 uppercase">
									Ihre Position
								</p>
							</div>
						</div>
						<div class="text-right">
							<span class="block font-mono text-sm font-black text-institutional-navy"
								>{data.ownEntry.points}</span
							>
							<span class="text-[8px] font-bold tracking-widest text-slate-400 uppercase">Punkte</span>
						</div>
					</div>
				{/if}
			</div>
		{:else}
			<div class="flex justify-center py-16">
				<div
					class="h-8 w-8 animate-spin border-2 border-slate-200 border-t-institutional-navy"
				></div>
			</div>
		{/if}
	</div>
</div>
