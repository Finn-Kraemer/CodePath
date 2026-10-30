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

<div class="mx-auto flex max-w-7xl flex-col gap-10 pb-24">
	<header class="flex items-end justify-between border-b border-slate-200 pb-10">
		<div>
			<div class="mb-2 flex items-center gap-3 text-institutional-navy/40">
				<a href="/admin" class="transition-colors hover:text-institutional-gold">
					<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase"
						>Admin Dashboard</span
					>
				</a>
				<span class="text-slate-300">/</span>
				<span
					class="font-mono text-[10px] font-bold tracking-[4px] text-institutional-gold uppercase"
					>Live-Mitteilung</span
				>
			</div>
			<h1
				class="font-sans text-2xl md:text-3xl  font-black tracking-tighter text-institutional-navy uppercase"
			>
				Messaging
			</h1>
		</div>
	</header>

	<section class="max-w-3xl">
		<div class="space-y-10 border border-slate-200 bg-white p-12 shadow-sm">
			<div class="space-y-4">
				<label
					for="announcement-content"
					class="font-sans text-[10px] font-black tracking-[4px] text-institutional-navy uppercase"
					>Inhalt der Mitteilung</label
				>
				<textarea
					id="announcement-content"
					bind:value={announcement}
					placeholder="Wichtige System-Meldung verfassen..."
					class="h-64 w-full resize-none rounded-none border border-slate-200 bg-slate-50 p-6 font-sans text-sm text-slate-700 transition-all focus:border-institutional-navy focus:outline-none"
				></textarea>
			</div>

			<div class="space-y-4">
				<span
					id="announcement-mode"
					class="block font-sans text-[10px] font-black tracking-[4px] text-institutional-navy uppercase"
					>Anzeigeort wählen</span
				>
				<div
					class="grid grid-cols-1 gap-3 sm:grid-cols-3"
					role="group"
					aria-labelledby="announcement-mode"
				>
					{#each ['HEADER', 'INFO_PAGE', 'BOTH'] as mode (mode)}
						<button
							onclick={() => (displayMode = mode)}
							class="border py-4 font-mono text-[10px] font-bold tracking-widest uppercase transition-all
							{displayMode === mode
								? 'border-institutional-navy bg-institutional-navy text-white'
								: 'border-slate-200 bg-white text-slate-400 hover:border-slate-300'}"
						>
							{mode.replace('_', ' ')}
						</button>
					{/each}
				</div>
				<p class="font-mono text-[10px] text-slate-400 italic">
					HEADER: Wird oben auf jeder Seite angezeigt. INFO_PAGE: Nur auf der Info-Seite. BOTH:
					Überall.
				</p>
			</div>

			<div class="flex items-center justify-between gap-6 border-t border-slate-100 pt-6">
				{#if success}
					<p
						class="animate-in fade-in text-[10px] font-bold tracking-widest text-teal-600 uppercase duration-300"
					>
						Signal erfolgreich gesendet!
					</p>
				{:else if error}
					<p class="text-[10px] font-bold tracking-widest text-rose-600 uppercase">{error}</p>
				{:else}
					<div></div>
				{/if}

				<button
					onclick={updateAnnouncement}
					disabled={loading}
					class="group flex items-center gap-4 rounded-none bg-institutional-navy px-12 py-5 font-sans text-[11px] font-bold tracking-[3px] text-white uppercase shadow-xl transition-all hover:opacity-95 disabled:opacity-50"
				>
					{loading ? 'Übertrage...' : 'Live Veröffentlichen'}
					<svg
						xmlns="http://www.w3.org/2000/svg"
						class="h-4 w-4 transition-transform group-hover:translate-x-1"
						fill="none"
						viewBox="0 0 24 24"
						stroke="currentColor"
					>
						<path
							stroke-linecap="round"
							stroke-linejoin="round"
							stroke-width="2"
							d="M14 5l7 7m0 0l-7 7m7-7H3"
						/>
					</svg>
				</button>
			</div>
		</div>
	</section>
</div>
