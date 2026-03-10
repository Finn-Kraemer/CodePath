<script lang="ts">
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';

	let announcement = $state('');
	let displayMode = $state('HEADER');
	let loading = $state(false);
	let success = $state(false);
	let error = $state('');
    let hasFetched = false;

	async function fetchData() {
        if (hasFetched) return;
		try {
			const res = await auth.apiFetch('/api/common/announcement');
			if (res.ok) {
				const data = await res.json();
                if (!announcement) {
				    announcement = data.content || '';
                }
				displayMode = data.displayMode || 'HEADER';
                hasFetched = true;
			}
		} catch {
			error = 'Fehler beim Laden der aktuellen Ankündigung';
		}
	}

	async function updateAnnouncement() {
		loading = true;
		success = false;
		error = '';
		try {
			const res = await auth.apiFetch('/api/admin/announcement', {
				method: 'PUT',
				body: JSON.stringify({ content: announcement, displayMode: displayMode })
			});
			if (res.ok) {
				success = true;
				setTimeout(() => (success = false), 3000);
			} else {
				error = 'Fehler beim Aktualisieren';
			}
		} catch {
			error = 'Netzwerkfehler beim Aktualisieren';
		} finally {
			loading = false;
		}
	}

	onMount(fetchData);
</script>

<div class="flex flex-col gap-10 pb-24 max-w-7xl mx-auto">
	<header class="flex items-end justify-between border-b border-slate-200 pb-10">
		<div>
			<div class="flex items-center gap-3 text-institutional-navy/40 mb-2">
				<a href="/admin" class="hover:text-institutional-gold transition-colors">
					<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase">Admin Dashboard</span>
				</a>
				<span class="text-slate-300">/</span>
				<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase text-institutional-gold">Live-Mitteilung</span>
			</div>
			<h1 class="font-sans text-6xl font-black tracking-tighter text-institutional-navy uppercase leading-none">
				Messaging
			</h1>
		</div>
	</header>

	<section class="max-w-3xl">
		<div class="bg-white border border-slate-200 p-12 shadow-sm space-y-10">
			<div class="space-y-4">
				<label class="font-sans text-[10px] font-black tracking-[4px] text-institutional-navy uppercase">Inhalt der Mitteilung</label>
				<textarea
					bind:value={announcement}
					placeholder="Wichtige System-Meldung verfassen..."
					class="w-full border border-slate-200 bg-slate-50 p-6 font-sans text-sm text-slate-700 focus:border-institutional-navy focus:outline-none rounded-none transition-all h-64 resize-none"
				></textarea>
			</div>
			
			<div class="space-y-4">
				<label class="font-sans text-[10px] font-black tracking-[4px] text-institutional-navy uppercase">Anzeigeort wählen</label>
				<div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
					{#each ['HEADER', 'INFO_PAGE', 'BOTH'] as mode}
						<button
							onclick={() => displayMode = mode}
							class="py-4 font-mono text-[10px] font-bold tracking-widest uppercase transition-all border
							{displayMode === mode ? 'bg-institutional-navy text-white border-institutional-navy' : 'bg-white text-slate-400 border-slate-200 hover:border-slate-300'}"
						>
							{mode.replace('_', ' ')}
						</button>
					{/each}
				</div>
                <p class="text-[10px] text-slate-400 font-mono italic">
                    HEADER: Wird oben auf jeder Seite angezeigt. INFO_PAGE: Nur auf der Info-Seite. BOTH: Überall.
                </p>
			</div>
			
			<div class="pt-6 border-t border-slate-100 flex items-center justify-between gap-6">
                {#if success}
                    <p class="text-[10px] font-bold text-teal-600 uppercase tracking-widest animate-in fade-in duration-300">Signal erfolgreich gesendet!</p>
                {:else if error}
                    <p class="text-[10px] font-bold text-rose-600 uppercase tracking-widest">{error}</p>
                {:else}
                    <div></div>
                {/if}

				<button
					onclick={updateAnnouncement}
					disabled={loading}
					class="bg-institutional-navy px-12 py-5 font-sans text-[11px] font-bold tracking-[3px] text-white uppercase transition-all hover:opacity-95 disabled:opacity-50 rounded-none shadow-xl flex items-center gap-4 group"
				>
					{loading ? 'Übertrage...' : 'Live Veröffentlichen'}
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 group-hover:translate-x-1 transition-transform" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3" />
                    </svg>
				</button>
			</div>
		</div>
	</section>
</div>
