<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type Schemas } from '$lib/api';
	import Markdown from '$lib/Markdown.svelte';

	type Entry = Schemas['JournalEntryView'];

	/** Alle eigenen Lerntagebuch-Einträge (Profil). */
	let entries = $state<Entry[] | null>(null);

	onMount(async () => {
		try {
			entries = await api<Entry[]>('/api/journal');
		} catch {
			entries = [];
		}
	});

	async function remove(entry: Entry) {
		if (!confirm('Diese Notiz löschen?')) return;
		await api(`/api/journal/${entry.id}`, { method: 'DELETE' });
		entries = (entries ?? []).filter((e) => e.id !== entry.id);
	}
</script>

<section class="border border-slate-200 bg-white p-8 shadow-sm" data-testid="journal-overview">
	<h3 class="mb-2 font-sans text-sm font-black tracking-widest text-slate-400 uppercase">
		Mein Lerntagebuch
	</h3>
	{#if entries === null}
		<p class="text-xs text-slate-400">Lädt …</p>
	{:else if entries.length === 0}
		<p class="text-sm text-slate-500">
			Noch keine Notizen. Auf jeder Modul- und Aufgabenseite findest du „Mein Lerntagebuch“.
		</p>
	{:else}
		<ul class="space-y-6">
			{#each entries as e (e.id)}
				<li class="border-l-4 border-institutional-gold pl-4">
					<div class="mb-1 flex flex-wrap items-baseline justify-between gap-2 text-xs">
						<a
							href={e.taskSlug
								? `/modules/${e.moduleSlug}/${e.taskSlug}`
								: `/modules/${e.moduleSlug}`}
							class="font-bold text-institutional-navy underline"
							>{e.moduleTitle}{e.taskTitle ? ` › ${e.taskTitle}` : ''}</a
						>
						<span class="text-slate-400"
							>{new Date(e.updatedAt).toLocaleString('de-DE', {
								dateStyle: 'short',
								timeStyle: 'short'
							})}{e.shared ? ' · geteilt' : ''}
							<button class="ml-2 text-rose-600 underline" onclick={() => remove(e)}>löschen</button
							></span
						>
					</div>
					<div class="prose prose-sm max-w-none"><Markdown source={e.text} /></div>
				</li>
			{/each}
		</ul>
	{/if}
</section>
