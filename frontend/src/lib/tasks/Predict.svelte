<script lang="ts">
	import { onDestroy, onMount } from 'svelte';
	import SubmitButton from './SubmitButton.svelte';
	import { config, draftKey, solution, visualizerSettings, type TaskProps } from './types';
	import { loadDraft, saveDraft } from '$lib/drafts';
	import { PythonRunner } from '$lib/python.svelte';
	import CodeVisualizer from '$lib/visualizer/CodeVisualizer.svelte';

	/**
	 * Vorhersage: Das Programm hält an einem Haltepunkt. Welche Werte haben die Variablen dort?
	 * Bewertet wird auf dem Server; nach dem Lösen zeigt der Visualizer die Auflösung.
	 */
	let { task, disabled, busy, result, submit }: TaskProps = $props();

	type Cfg = {
		code: string;
		stdin?: string;
		breakpoint: { line: number; hit: number };
		questions: { variable: string }[];
	};
	const cfg = $derived(config<Cfg>(task));
	const sol = $derived(solution<{ answers: string[] }>(task));
	const lines = $derived((cfg.code ?? '').split('\n'));
	const perQuestion = $derived((result?.details?.blanks as boolean[] | undefined) ?? null);
	const visualizer = $derived(visualizerSettings(task));

	let answers = $state<string[]>([]);
	let runner: PythonRunner | null = $state(null);
	let showSolution = $state(false);

	onMount(() => {
		const last =
			(task.lastAnswer?.answers as string[] | undefined) ?? loadDraft<string[]>(draftKey(task));
		answers = cfg.questions.map((_, i) => last?.[i] ?? '');
	});

	onDestroy(() => runner?.stop());

	$effect(() => {
		if (answers.length) saveDraft(draftKey(task), answers);
	});

	function openSolution() {
		if (!runner) {
			runner = new PythonRunner();
			runner.start();
		}
		showSolution = true;
	}

	function mark(i: number) {
		if (sol) return 'border-green-500';
		if (perQuestion) return perQuestion[i] ? 'border-green-500' : 'border-rose-500';
		return 'border-slate-300';
	}

	const hitText = $derived(
		cfg.breakpoint.hit > 1 ? `zum ${cfg.breakpoint.hit}. Mal` : 'zum ersten Mal'
	);
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	<p class="mb-4 text-sm text-slate-600" data-testid="predict-breakpoint">
		Das Programm hält an, wenn Python <strong>Zeile {cfg.breakpoint.line}</strong>
		{hitText} als Nächstes ausführen würde (●). Welche Werte haben die Variablen in diesem Moment?
	</p>
	<ol
		class="mb-6 overflow-x-auto border-4 border-slate-800 bg-slate-900 py-3 font-mono text-xs leading-6 text-slate-200"
		aria-label="Programmcode"
	>
		{#each lines as text, i (i)}
			{@const halt = cfg.breakpoint.line === i + 1}
			<li class="flex px-2 whitespace-pre {halt ? 'bg-rose-500/20' : ''}">
				<span class="w-5 shrink-0 text-center text-rose-400" aria-hidden="true"
					>{halt ? '●' : ''}</span
				>
				<span class="w-8 shrink-0 pr-3 text-right opacity-50">{i + 1}</span>
				<span>{text || ' '}</span>
				{#if halt}<span class="sr-only"> (Haltepunkt)</span>{/if}
			</li>
		{/each}
	</ol>
	{#if cfg.stdin}
		<p class="mb-6 font-mono text-xs text-slate-500">
			Eingaben für input(): <span class="text-slate-800">{cfg.stdin.split('\n').join(' · ')}</span>
		</p>
	{/if}

	<div class="mb-8 space-y-3">
		{#each cfg.questions as question, i (i)}
			<label class="flex items-center gap-3 font-mono text-sm">
				<span class="min-w-24 text-right text-institutional-navy">{question.variable} =</span>
				<input
					bind:value={answers[i]}
					{disabled}
					placeholder={sol ? sol.answers[i] : 'Wert'}
					aria-label={`Wert von ${question.variable}`}
					class="w-48 border-b-2 bg-white px-2 py-1 outline-none disabled:opacity-70 {mark(i)}"
				/>
				{#if sol}<span class="text-xs text-green-700">Lösung: {sol.answers[i]}</span>{/if}
			</label>
		{/each}
	</div>

	{#if sol && visualizer.enabled}
		{#if showSolution && runner}
			<div class="mb-6">
				<CodeVisualizer
					{runner}
					code={cfg.code}
					stdin={cfg.stdin ?? ''}
					breakpoint={cfg.breakpoint}
					maxSteps={visualizer.maxSteps}
					title="Auflösung Schritt für Schritt"
					onclose={() => (showSolution = false)}
				/>
			</div>
		{:else}
			<button
				type="button"
				onclick={openSolution}
				class="mb-4 w-full border-2 border-green-700 py-4 text-xs font-black tracking-[4px] text-green-700 uppercase"
			>
				Auflösung Schritt für Schritt
			</button>
		{/if}
	{/if}

	<SubmitButton
		{busy}
		disabled={disabled || answers.some((a) => !a?.trim())}
		label="Vorhersage prüfen"
		onclick={() => submit({ answers })}
	/>
</div>
