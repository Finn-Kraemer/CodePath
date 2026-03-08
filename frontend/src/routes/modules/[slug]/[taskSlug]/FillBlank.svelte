<script lang="ts">
	import { auth } from '$lib/auth.svelte';

	let { task, moduleSlug, supportUsed = false } = $props();

	let answers = $state<string[]>([]);
	let result = $state<{ correct: boolean; feedback: string; isLocked?: boolean } | null>(null);
	let loading = $state(false);

	// Split template by ___ to render inputs
	let parts = $derived(task.config.template.split('___'));

	$effect(() => {
		if (answers.length === 0) {
			answers = new Array(parts.length - 1).fill('');
		}
	});

	async function handleSubmit() {
		if (answers.some((a) => !a)) return;
		loading = true;

		try {
			const res = await auth.apiFetch(`/api/modules/${moduleSlug}/${task.slug}/submit`, {
				method: 'POST',
				body: JSON.stringify({ 
					payload: { answers },
					supportUsed
				})
			});
			const data = await res.json();
			result = { correct: data.isCorrect, feedback: data.feedback, isLocked: data.isLocked };
			if (data.isCorrect) task.isCompleted = true;
			if (data.isLocked) task.isLocked = true;
		} catch (e) {
			result = { correct: false, feedback: 'Fehler bei der Verbindung zum Server' };
		} finally {
			loading = false;
		}
	}
</script>

<div class="border border-slate-200 bg-white p-10 shadow-sm rounded-none">
	<h3 class="mb-8 font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase">
		Vervollständigung:
	</h3>

	<div
		class="mb-10 border border-slate-100 bg-slate-50 p-8 font-mono text-sm leading-loose rounded-none"
	>
		{#each parts as part, i (i)}
			{part}
			{#if i < parts.length - 1}
				<input
					bind:value={answers[i]}
					disabled={task.isCompleted || task.isLocked}
					type="text"
					class="mx-2 min-w-[100px] border-b-2 border-slate-300 bg-white px-3 py-1 text-institutional-navy outline-none transition-colors focus:border-institutional-navy disabled:opacity-50 rounded-none"
					placeholder="..."
				/>
			{/if}
		{/each}
	</div>

	{#if result}
		<div
			class="mb-8 border p-6 font-mono text-xs rounded-none
            {result.correct ? 'border-green-200 bg-green-50 text-green-700' : 'border-rose-200 bg-rose-50 text-rose-700'}"
		>
			<div class="flex items-center gap-4">
				<span class="text-xl font-bold">{result.correct ? '✓' : (result.isLocked ? '🔒' : '✗')}</span>
				<div>
					<span class="font-bold uppercase tracking-widest">{result.correct ? 'Ergebnis:' : 'Hinweis:'}</span> {result.feedback}
				</div>
			</div>
		</div>
	{/if}

	<button
		onclick={handleSubmit}
		disabled={loading || answers.some((a) => !a) || task.isCompleted || task.isLocked}
		class="w-full bg-institutional-navy py-5 font-sans text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:opacity-90 disabled:opacity-20 rounded-none"
	>
		{loading ? 'Prüfung läuft...' : task.isLocked ? 'Aufgabe gesperrt' : task.isCompleted ? 'Eintrag bereits validiert' : 'Prüfungsantwort absenden'}
	</button>
</div>
