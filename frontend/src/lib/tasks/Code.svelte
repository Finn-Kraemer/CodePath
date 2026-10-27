<script lang="ts">
	import { onDestroy, onMount } from 'svelte';
	import SubmitButton from './SubmitButton.svelte';
	import CodeOutput from './CodeOutput.svelte';
	import { config, draftKey, solution, type TaskProps } from './types';
	import { createEditor } from '$lib/monaco';
	import { PythonRunner } from '$lib/python.svelte';
	import { loadDraft, saveDraft } from '$lib/drafts';

	let { task, disabled, busy, result, submit }: TaskProps = $props();

	type Example = { stdin: string; expectedOutput: string };
	const cfg = $derived(config<{ starterCode: string; graded: boolean; examples: Example[] }>(task));
	const sol = $derived(solution<{ code?: string }>(task));
	const tests = $derived(
		(result?.details?.tests as
			| { name: string; passed: boolean; stdin?: string; output?: string }[]
			| undefined) ?? []
	);

	let element = $state<HTMLElement | null>(null);
	let editor: any = null;
	let code = $state('');
	let output = $state('');
	let error = $state('');
	let running = $state(false);
	const runner = new PythonRunner();

	onMount(async () => {
		code =
			(task.lastAnswer?.code as string | undefined) ??
			loadDraft<string>(draftKey(task)) ??
			cfg.starterCode ??
			'';
		runner.start();
		if (element) {
			editor = await createEditor(element, {
				value: code,
				language: 'python',
				readOnly: disabled,
				onChange: (value) => {
					code = value;
					saveDraft(draftKey(task), value);
				}
			});
		}
	});

	onDestroy(() => {
		editor?.dispose();
		runner.stop();
	});

	async function tryOut() {
		running = true;
		const res = await runner.run(code, cfg.examples?.[0]?.stdin ?? '');
		output = res.stdout;
		error = res.error ?? '';
		running = false;
	}
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	{#if !cfg.graded}
		<div class="mb-6 border border-amber-200 bg-amber-50 p-4 text-xs text-amber-800">
			Übung ohne Punkte: Auf diesem Server ist kein Code-Prüfer eingerichtet. Probiere dein Programm
			aus und speichere es.
		</div>
	{/if}
	{#if cfg.examples?.length}
		<div class="mb-6 space-y-2">
			{#each cfg.examples as example, i (i)}
				<div class="grid grid-cols-2 gap-2 font-mono text-xs">
					<div class="bg-slate-50 p-3">
						<span class="text-slate-400">Eingabe</span>
						<pre>{example.stdin || '—'}</pre>
					</div>
					<div class="bg-slate-50 p-3">
						<span class="text-slate-400">Erwartete Ausgabe</span>
						<pre>{example.expectedOutput}</pre>
					</div>
				</div>
			{/each}
		</div>
	{/if}
	<div
		bind:this={element}
		class="mb-6 h-96 w-full border-4 border-slate-800 bg-slate-900"
		aria-label="Python-Editor"
	></div>
	<div class="mb-6 grid grid-cols-1 gap-4 md:grid-cols-2">
		<CodeOutput {output} {error} />
		<div class="space-y-2">
			{#each tests as test (test.name)}
				<div
					class="flex items-center justify-between border p-3 font-mono text-xs {test.passed
						? 'border-green-200 bg-green-50 text-green-700'
						: 'border-rose-200 bg-rose-50 text-rose-700'}"
				>
					<span>{test.name}</span><span>{test.passed ? 'bestanden' : 'nicht bestanden'}</span>
				</div>
			{/each}
		</div>
	</div>
	{#if sol?.code}
		<details class="mb-6">
			<summary class="cursor-pointer text-xs font-bold text-green-700">Musterlösung</summary>
			<pre class="mt-2 bg-slate-50 p-4 text-xs">{sol.code}</pre>
		</details>
	{/if}
	<div class="grid grid-cols-1 gap-4 md:grid-cols-2">
		<button
			onclick={tryOut}
			disabled={!runner.ready || running}
			class="border-2 border-institutional-navy py-5 font-sans text-xs font-black tracking-[4px] text-institutional-navy uppercase disabled:opacity-40"
		>
			{runner.ready ? (running ? 'Läuft …' : 'Ausprobieren') : 'Python lädt …'}
		</button>
		<SubmitButton
			{busy}
			disabled={disabled || !code.trim()}
			label={cfg.graded ? 'Abgeben' : 'Speichern'}
			onclick={() => submit({ code })}
		/>
	</div>
</div>
