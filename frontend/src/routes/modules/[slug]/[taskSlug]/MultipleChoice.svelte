<script lang="ts">
	import { auth } from '$lib/auth.svelte';
	import TaskTimer from '$lib/components/tasks/TaskTimer.svelte';

	let { task, moduleSlug, supportUsed = false } = $props();

	let selected = $state<number[]>([]);
	let result = $state<{ correct: boolean; feedback: string; solution?: string; isLocked?: boolean } | null>(null);
	let loading = $state(false);
	let timerRef = $state<TaskTimer | null>(null);

	async function handleSubmit() {
		if (selected.length === 0) return;
		loading = true;

		try {
			const currentSeconds = timerRef ? timerRef.getSeconds() : (task.timeSpentSeconds || 0);
			const timeSpentSinceLastSubmit = currentSeconds - (task.timeSpentSeconds || 0);

			const res = await auth.apiFetch(`/api/modules/${moduleSlug}/${task.slug}/submit`, {
				method: 'POST',
				body: JSON.stringify({ 
					payload: { selected },
					timeSpentSeconds: timeSpentSinceLastSubmit,
					supportUsed
				})
			});
			const data = await res.json();
			result = { 
				correct: data.isCorrect, 
				feedback: data.feedback,
				solution: data.correctSolution,
				isLocked: data.isLocked
			};
			if (data.isCorrect) task.isCompleted = true;
			if (data.isLocked) task.isLocked = true;
			task.failedAttempts = data.failedAttempts;
			task.timeSpentSeconds = currentSeconds;
		} catch (e) {
			result = { correct: false, feedback: 'Fehler bei der Verbindung zum Server' };
		} finally {
			loading = false;
		}
	}

	function toggleOption(index: number) {
		if (task.isCompleted || task.isLocked) return;
		if (selected.includes(index)) {
			selected = selected.filter((i) => i !== index);
		} else {
			selected = [...selected, index];
		}
	}
</script>

<div class="border border-slate-200 bg-white p-10 shadow-sm rounded-none">
	<div class="mb-10 flex flex-col md:flex-row md:items-center md:justify-between gap-6">
		<h3 class="font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase">
			Prüfungsfrage:
		</h3>
		<TaskTimer bind:this={timerRef} startTime={task.timeSpentSeconds || 0} />
	</div>

	<div class="mb-10 space-y-3">
		{#each task.config.options as option, i (i)}
			<button
				onclick={() => toggleOption(i)}
				disabled={task.isCompleted || task.isLocked}
				class="w-full border-2 p-5 text-left font-sans text-sm transition-all rounded-none
                {selected.includes(i)
					? 'border-institutional-navy bg-slate-50 font-bold'
					: 'border-slate-100 bg-slate-50 hover:border-slate-300'}
                {task.isCompleted || task.isLocked ? 'opacity-60 cursor-default' : ''}"
			>
				<div class="flex items-center gap-5">
					<div
						class="flex h-6 w-6 items-center justify-center border-2 transition-colors rounded-none
                        {selected.includes(i) ? 'border-institutional-navy bg-institutional-navy' : 'border-slate-300 bg-white'}"
					>
						{#if selected.includes(i)}
							<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-white" viewBox="0 0 20 20" fill="currentColor">
								<path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" />
							</svg>
						{/if}
					</div>
					<span class={selected.includes(i) ? 'text-institutional-navy' : 'text-slate-600'}>{option}</span>
				</div>
			</button>
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
					{#if result.solution}
						<div class="mt-4 border-t border-rose-200 pt-4 text-rose-800">
							<p class="mb-1 font-black uppercase tracking-[2px] text-[10px]">Lösungshilfe (nach 3 Versuchen):</p>
							<p class="font-sans italic">{result.solution}</p>
						</div>
					{/if}
				</div>
			</div>
		</div>
	{/if}

	<button
		onclick={handleSubmit}
		disabled={loading || selected.length === 0 || task.isCompleted || task.isLocked}
		class="w-full bg-institutional-navy py-5 font-sans text-xs font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:opacity-90 disabled:opacity-20 rounded-none"
	>
		{loading ? 'Verarbeitung...' : task.isLocked ? 'Aufgabe gesperrt' : task.isCompleted ? 'Aufgabe bereits erfolgreich abgeschlossen' : 'Antwort einreichen'}
	</button>
</div>
