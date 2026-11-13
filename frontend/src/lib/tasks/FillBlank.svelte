<script lang="ts">
	import { onDestroy, onMount } from 'svelte';
	import SubmitButton from './SubmitButton.svelte';
	import { config, draftKey, solution, visualizerSettings, type TaskProps } from './types';
	import CodeVisualizer from '$lib/visualizer/CodeVisualizer.svelte';
	import { loadDraft, rememberOutput, saveDraft } from '$lib/drafts';
	import { PythonRunner } from '$lib/python.svelte';
	import CodeOutput from './CodeOutput.svelte';

	let {
		task,
		disabled,
		busy,
		result,
		submit,
		code = false
	}: TaskProps & { code?: boolean } = $props();

	const cfg = $derived(config<{ template: string; blanks: { options?: string[] }[] }>(task));
	const parts = $derived(cfg.template.split('___'));
	const sol = $derived(solution<{ answers: string[] }>(task));
	const perBlank = $derived((result?.details?.blanks as boolean[] | undefined) ?? null);
	let answers = $state<string[]>([]);

	const runner = new PythonRunner();
	let output = $state('');
	let runError = $state('');

	const visualizer = $derived(visualizerSettings(task));
	let visualized = $state<{ code: string; title: string; key: number } | null>(null);

	function assemble(fill: string[]) {
		let program = parts[0];
		for (let i = 1; i < parts.length; i++) program += (fill[i - 1] ?? '') + parts[i];
		return program;
	}

	function visualize(fill: string[], title: string) {
		visualized = { code: assemble(fill), title, key: (visualized?.key ?? 0) + 1 };
	}

	// Bei Code-Lücken lässt sich das Programm im Browser ausprobieren
	async function tryOut() {
		const res = await runner.run(assemble(answers));
		output = res.stdout;
		runError = res.error ?? '';
		rememberOutput(draftKey(task), [output, runError].filter(Boolean).join('\n'));
	}

	onMount(() => {
		if (code) runner.start();
		const last =
			(task.lastAnswer?.answers as string[] | undefined) ?? loadDraft<string[]>(draftKey(task));
		answers = Array.from({ length: parts.length - 1 }, (_, i) => last?.[i] ?? '');
	});

	onDestroy(() => runner.stop());

	$effect(() => {
		if (answers.length) saveDraft(draftKey(task), answers);
	});

	function mark(i: number) {
		if (sol) return 'border-green-500';
		if (perBlank) return perBlank[i] ? 'border-green-500' : 'border-rose-500';
		return code ? 'border-teal-500' : 'border-slate-300';
	}
</script>

<div class="ui-card p-6 sm:p-8">
	<div
		class="mb-10 overflow-x-auto p-8 font-mono text-sm leading-loose whitespace-pre-wrap
		{code
			? 'border-4 border-slate-800 bg-slate-900 text-teal-300'
			: 'border border-slate-100 bg-slate-50'}"
	>
		{#each parts as part, i (i)}
			<span>{part}</span>
			{#if i < parts.length - 1}
				{#if cfg.blanks[i]?.options?.length}
					<select
						bind:value={answers[i]}
						{disabled}
						aria-label={`Lücke ${i + 1}`}
						class="mx-1 border-b-2 bg-white px-2 py-1 text-institutional-navy {mark(i)}"
					>
						<option value="">…</option>
						{#each cfg.blanks[i].options ?? [] as option (option)}
							<option value={option}>{option}</option>
						{/each}
					</select>
				{:else}
					<input
						bind:value={answers[i]}
						{disabled}
						aria-label={`Lücke ${i + 1}`}
						placeholder={sol ? sol.answers[i] : ''}
						class="mx-1 inline-block min-w-[90px] border-b-2 px-2 py-0.5 outline-none disabled:opacity-70
						{code ? 'bg-slate-800 text-white' : 'bg-white text-institutional-navy'} {mark(i)}"
						size={Math.max(6, (answers[i] ?? '').length + 1)}
					/>
				{/if}
			{/if}
		{/each}
	</div>
	{#if sol}
		<p class="mb-6 font-mono text-xs text-green-700">Lösung: {sol.answers.join(' · ')}</p>
	{/if}
	{#if code}
		<div class="mb-6"><CodeOutput {output} error={runError} /></div>
		{#if visualized}
			{#key visualized.key}
				<div class="mb-6">
					<CodeVisualizer
						{runner}
						code={visualized.code}
						title={visualized.title}
						maxSteps={visualizer.maxSteps}
						onclose={() => (visualized = null)}
					/>
				</div>
			{/key}
		{/if}
		<div class="mb-4 grid grid-cols-1 gap-4 {visualizer.enabled ? 'md:grid-cols-2' : ''}">
			<button
				onclick={tryOut}
				disabled={!runner.ready}
				class="w-full rounded-[var(--radius-ui-sm)] border-2 border-institutional-navy py-4 text-sm font-semibold text-institutional-navy disabled:opacity-40"
			>
				{runner.ready ? 'Ausprobieren' : 'Python lädt …'}
			</button>
			{#if visualizer.enabled}
				<button
					onclick={() =>
						sol
							? visualize(sol.answers, 'Lösung Schritt für Schritt')
							: visualize(answers, 'Dein Programm Schritt für Schritt')}
					disabled={!runner.ready}
					class="w-full rounded-[var(--radius-ui-sm)] border-2 border-institutional-navy py-4 text-sm font-semibold text-institutional-navy disabled:opacity-40"
				>
					{sol ? 'Lösung Schritt für Schritt' : 'Schritt für Schritt'}
				</button>
			{/if}
		</div>
	{/if}
	<SubmitButton
		{busy}
		disabled={disabled || answers.some((a) => !a?.trim())}
		onclick={() => submit({ answers })}
	/>
</div>
