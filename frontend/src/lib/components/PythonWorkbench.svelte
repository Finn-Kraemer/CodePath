<script lang="ts">
	import { onDestroy, onMount } from 'svelte';
	import CodeOutput from '$lib/tasks/CodeOutput.svelte';
	import { createEditor } from '$lib/monaco';
	import { PythonRunner } from '$lib/python.svelte';
	import CodeVisualizer from '$lib/visualizer/CodeVisualizer.svelte';
	import { DEFAULT_MAX_STEPS } from '$lib/visualizer/trace';

	/**
	 * Python-Werkbank: Editor, Eingabe und Ausführen im Browser. Für größere Programme
	 * gibt es mehr Zeit als bei den kleinen Code-Aufgaben.
	 */
	let {
		initialCode = '',
		readOnly = false,
		visualizer = true,
		maxSteps = DEFAULT_MAX_STEPS,
		onchange
	}: {
		initialCode?: string;
		readOnly?: boolean;
		/** Button „Schritt für Schritt“ (Code-Visualizer) anbieten */
		visualizer?: boolean;
		maxSteps?: number;
		onchange?: (code: string) => void;
	} = $props();

	let element = $state<HTMLElement | null>(null);
	let editor: any = null;
	let code = $state('');
	let stdin = $state('');
	let output = $state('');
	let error = $state('');
	let running = $state(false);
	const runner = new PythonRunner();
	let visualized = $state<{ code: string; stdin: string; key: number } | null>(null);

	onMount(async () => {
		code = initialCode;
		runner.start();
		if (element) {
			editor = await createEditor(element, {
				value: code,
				language: 'python',
				readOnly,
				onChange: (value) => {
					code = value;
					onchange?.(value);
				}
			});
		}
	});

	onDestroy(() => {
		editor?.dispose();
		runner.stop();
	});

	async function run() {
		running = true;
		const res = await runner.run(code, stdin, 10_000);
		output = res.stdout;
		error = res.error ?? '';
		running = false;
	}
</script>

<div class="space-y-4" data-workbench>
	<div
		bind:this={element}
		class="h-96 w-full border-4 border-slate-800 bg-slate-900"
		aria-label="Python-Editor"
	></div>
	<div class="grid grid-cols-1 gap-4 md:grid-cols-2">
		<label class="block">
			<span class="font-mono text-[11px] font-bold tracking-wider text-slate-400 uppercase"
				>Eingaben für input() – eine pro Zeile</span
			>
			<textarea
				class="mt-1 h-32 w-full rounded-[var(--radius-ui)] border border-slate-200 bg-white p-3 font-mono text-xs"
				bind:value={stdin}
			></textarea>
		</label>
		<div>
			<span class="font-mono text-[11px] font-bold tracking-wider text-slate-400 uppercase"
				>Ausgabe</span
			>
			<div class="mt-1"><CodeOutput {output} {error} /></div>
		</div>
	</div>
	{#if visualized}
		{#key visualized.key}
			<CodeVisualizer
				{runner}
				code={visualized.code}
				stdin={visualized.stdin}
				{maxSteps}
				title="Dein Programm Schritt für Schritt"
				onclose={() => (visualized = null)}
			/>
		{/key}
	{/if}
	<div class="grid grid-cols-1 gap-4 {visualizer ? 'md:grid-cols-2' : ''}">
		<button
			type="button"
			onclick={run}
			disabled={!runner.ready || running}
			class="w-full rounded-[var(--radius-ui-sm)] border-2 border-institutional-navy py-4 text-sm font-semibold text-institutional-navy disabled:opacity-40"
		>
			{runner.ready ? (running ? 'Läuft …' : 'Programm ausführen') : 'Python lädt …'}
		</button>
		{#if visualizer}
			<button
				type="button"
				onclick={() => (visualized = { code, stdin, key: (visualized?.key ?? 0) + 1 })}
				disabled={!runner.ready || running || !code.trim()}
				class="w-full border-2 border-institutional-navy py-4 font-sans text-xs font-black tracking-wider text-institutional-navy uppercase disabled:opacity-40"
			>
				Schritt für Schritt
			</button>
		{/if}
	</div>
</div>
