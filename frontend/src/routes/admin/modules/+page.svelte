<script lang="ts">
	import { onMount } from 'svelte';
	import { SvelteSet } from 'svelte/reactivity';
	import { auth } from '$lib/auth.svelte';

	interface Module {
		id: number;
		slug: string;
		title: string;
		isUnlocked: boolean;
		iconEmoji: string;
		availableUntil?: string;
	}

	let modules = $state<Module[]>([]);
	let loading = $state(true);
	let processingIds = new SvelteSet<number>();

	async function fetchModules() {
		try {
			const res = await auth.apiFetch('/api/admin/modules');
			if (!res.ok) throw new Error('Unauthorized');
			const data = await res.json();
			modules = data;
		} catch (e) {
			console.error(e);
		} finally {
			loading = false;
		}
	}
	async function toggleModule(mod: Module) {
		if (processingIds.has(mod.id)) return;

		processingIds.add(mod.id);

		try {
			const res = await auth.apiFetch(`/api/admin/modules/${mod.id}/toggle`, {
				method: 'PUT'
			});

			if (res.ok) {
				const data = await res.json();
				mod.isUnlocked = data.isUnlocked;
			} else {
				alert(`Fehler beim Speichern der Einstellung (Status: ${res.status}).`);
			}
		} catch (e) {
			console.error(e);
			alert('Netzwerkfehler.');
		} finally {
			processingIds.delete(mod.id);
		}
	}

	async function setDeadline(mod: Module, time: string) {
		try {
			const res = await auth.apiFetch(`/api/admin/modules/${mod.id}/deadline`, {
				method: 'PUT',
				body: JSON.stringify({ deadline: time })
			});
			if (res.ok) {
                // If it's a new time, we don't know the exact full ISO string yet from the server, 
                // but for the UI display, we can just fetch again or assume success
                fetchModules();
			}
		} catch (e) {
			console.error(e);
		}
	}

    function formatTime(isoString?: string) {
        if (!isoString) return '';
        const date = new Date(isoString);
        return date.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
    }

	onMount(fetchModules);
</script>

<div class="flex flex-col gap-12">
	<header class="border-b border-slate-200 pb-8">
		<a
			href="/admin"
			class="mb-6 inline-block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase transition-colors hover:text-institutional-navy"
			>← Zurück zur Administration</a
		>
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Modul-Steuerung
		</h1>
		<p class="mt-4 text-slate-500">Aktivieren oder Deaktivieren der Lerninhalte und Zeitlimits.</p>
	</header>

	{#if loading}
		<div class="flex justify-center py-24">
			<div class="h-12 w-12 border-4 border-slate-200 border-t-institutional-navy animate-spin"></div>
		</div>
	{:else}
		<div class="grid grid-cols-1 gap-8 md:grid-cols-2">
			{#each modules as mod (mod.id)}
				<div
					class="flex flex-col border border-slate-200 bg-white shadow-sm rounded-none transition-all hover:border-slate-300"
				>
					<div class="flex items-center justify-between p-8 border-b border-slate-50">
						<div class="flex items-center gap-6">
							<span class="text-4xl grayscale transition-all">{mod.iconEmoji}</span>
							<div>
								<h3 class="font-sans text-sm font-bold text-institutional-navy uppercase tracking-tight">{mod.title}</h3>
								<p class="mt-1 font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase">
									REF: {mod.slug}
								</p>
							</div>
						</div>

						{#if mod.isUnlocked}
							<button
								onclick={() => toggleModule(mod)}
								disabled={processingIds.has(mod.id)}
								class="border border-rose-200 bg-rose-50 px-5 py-2.5 text-[10px] font-black tracking-widest text-rose-700 uppercase transition-all hover:bg-rose-100 disabled:opacity-50 rounded-none"
							>
								{processingIds.has(mod.id) ? '...' : 'Sperren'}
							</button>
						{:else}
							<button
								onclick={() => toggleModule(mod)}
								disabled={processingIds.has(mod.id)}
								class="border border-teal-200 bg-teal-50 px-5 py-2.5 text-[10px] font-black tracking-widest text-teal-700 uppercase transition-all hover:bg-teal-100 disabled:opacity-50 rounded-none"
							>
								{processingIds.has(mod.id) ? '...' : 'Freigeben'}
							</button>
						{/if}
					</div>

					<div class="p-8 bg-slate-50/50 space-y-4">
						<label class="block font-mono text-[9px] font-bold text-slate-400 uppercase tracking-widest">End-Uhrzeit (Heute)</label>
						<div class="flex gap-2">
							<input 
								type="time" 
								value={mod.availableUntil ? formatTime(mod.availableUntil) : ''}
								onchange={(e) => setDeadline(mod, e.currentTarget.value)}
								class="flex-1 border border-slate-200 bg-white px-4 py-2 font-mono text-xs outline-none focus:border-institutional-navy rounded-none transition-all"
							/>
							{#if mod.availableUntil}
								<button 
									onclick={() => setDeadline(mod, '')}
									class="px-4 text-[10px] font-bold text-rose-600 uppercase hover:bg-rose-50 transition-all"
								>Entfernen</button>
							{/if}
						</div>
						<p class="text-[10px] text-slate-400 italic">Geben Sie nur die Uhrzeit an. Das Datum wird automatisch auf den heutigen Tag gesetzt.</p>
					</div>
				</div>
			{/each}
		</div>
	{/if}
</div>
