<script lang="ts">
	import { auth } from '$lib/auth.svelte';
	import { dndzone } from 'svelte-dnd-action';
	import { flip } from 'svelte/animate';

	let { task, moduleSlug, supportUsed = false } = $props();

	// Initialize items with IDs for dnd-action
	let items = $state(
		(task.config.items || []).map((text: string, index: number) => ({
			id: index,
			text: text
		}))
	);

	let result = $state<{ correct: boolean; feedback: string } | null>(null);
	let loading = $state(false);

	const flipDurationMs = 300;

	function handleDndConsider(e: CustomEvent<GenericDndEvent<any>>) {
		items = e.detail.items;
	}

	function handleDndFinalize(e: CustomEvent<GenericDndEvent<any>>) {
		items = e.detail.items;
	}

	async function handleSubmit() {
		loading = true;
		try {
			const currentOrder = items.map((item: any) => item.id);
			const res = await auth.apiFetch(`/api/modules/${moduleSlug}/${task.slug}/submit`, {
				method: 'POST',
				body: JSON.stringify({
					payload: { order: currentOrder },
					supportUsed
				})
			});
			const data = await res.json();
			result = { correct: data.isCorrect, feedback: data.feedback };
			if (data.isCorrect) task.isCompleted = true;
		} catch (e) {
			result = { correct: false, feedback: 'Fehler bei der Übermittlung' };
		} finally {
			loading = false;
		}
	}
</script>

<div class="border border-slate-200 bg-white p-10 shadow-sm rounded-none">
	<h3 class="mb-8 font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase">
		Sortier-Aufgabe:
	</h3>

	<p class="mb-6 text-sm text-slate-500 italic">Bringen Sie die Elemente in die richtige Reihenfolge (Drag & Drop):</p>

	<div
		use:dndzone={{ items, flipDurationMs, dropTargetStyle: {} }}
		onconsider={handleDndConsider}
		onfinalize={handleDndFinalize}
		class="mb-10 space-y-2 min-h-[50px]"
	>
		{#each items as item (item.id)}
			<div
				animate:flip={{ duration: flipDurationMs }}
				class="flex items-center gap-4 border-2 border-slate-100 bg-slate-50 p-5 cursor-move hover:border-institutional-navy transition-all rounded-none shadow-sm"
			>
				<div class="flex h-8 w-8 items-center justify-center bg-institutional-navy text-white font-mono text-xs font-bold">
					::
				</div>
				<span class="font-sans text-sm font-bold text-slate-700">{item.text}</span>
			</div>
		{/each}
	</div>

	{#if result}
		<div
			class="mb-8 border p-6 font-mono text-xs rounded-none
            {result.correct ? 'border-green-200 bg-green-50 text-green-700' : 'border-rose-200 bg-rose-50 text-rose-700'}"
		>
			<div class="flex items-center gap-4">
				<span class="text-xl font-bold">{result.correct ? '✓' : '✗'}</span>
				<div>
					<span class="font-bold uppercase tracking-widest">{result.correct ? 'Korrekt:' : 'Hinweis:'}</span> {result.feedback}
				</div>
			</div>
		</div>
	{/if}

	<button
		onclick={handleSubmit}
		disabled={loading || task.isCompleted || task.isLocked}
		class="w-full bg-institutional-navy py-5 font-sans text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:opacity-90 disabled:opacity-20 rounded-none"
	>
		{loading ? 'Prüfung läuft...' : task.isCompleted ? 'Bereits erfolgreich sortiert' : 'Reihenfolge validieren'}
	</button>
</div>
