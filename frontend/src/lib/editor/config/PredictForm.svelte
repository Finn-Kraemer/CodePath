<script lang="ts">
	import { onDestroy } from 'svelte';
	import type { Config } from '../types';
	import { PythonRunner } from '$lib/python.svelte';
	import { findBreakpoint, variableAt } from '$lib/visualizer/trace';
	import VisualizerOptions from './VisualizerOptions.svelte';

	/** Vorhersage: Programm, Haltepunkt und Variablen; erwartete Werte per Knopfdruck aus dem Programm. */
	let { config = $bindable() }: { config: Config } = $props();

	type Question = { variable: string; answers: string[] };
	const questions = $derived((config.questions ?? []) as Question[]);

	let runner: PythonRunner | null = null;
	let status = $state('');
	let running = $state(false);

	onDestroy(() => runner?.stop());

	function setBreakpoint(key: 'line' | 'hit', value: string) {
		const number = Math.max(1, Math.round(Number(value) || 1));
		config.breakpoint = { ...(config.breakpoint ?? { line: 1, hit: 1 }), [key]: number };
	}

	function setQuestion(i: number, patch: Partial<Question>) {
		config.questions = questions.map((q, j) => (j === i ? { ...q, ...patch } : q));
	}

	function addQuestion() {
		config.questions = [...questions, { variable: '', answers: [] }];
	}

	function removeQuestion(i: number) {
		config.questions = questions.filter((_, j) => j !== i);
	}

	/** Führt das Programm im Browser aus und trägt die Werte am Haltepunkt als Antworten ein. */
	async function detect() {
		running = true;
		status = 'Programm läuft …';
		try {
			if (!runner) {
				runner = new PythonRunner();
				runner.start();
			}
			const trace = await runner.trace(config.code ?? '', config.stdin ?? '', { maxSteps: 5000 });
			const index = findBreakpoint(trace.steps, config.breakpoint ?? { line: 1, hit: 1 });
			if (index < 0) {
				status = trace.error
					? `Fehler im Programm: ${trace.error}`
					: 'Der Haltepunkt wird nie erreicht – Zeile oder Anzahl prüfen.';
				return;
			}
			const step = trace.steps[index];
			const missing: string[] = [];
			config.questions = questions.map((q) => {
				const value = variableAt(step, q.variable);
				if (value === undefined) {
					missing.push(q.variable);
					return q;
				}
				// Texte auch ohne Anführungszeichen akzeptieren ('Hallo' oder Hallo)
				const variants = /^'.*'$/.test(value) ? [value, value.slice(1, -1)] : [value];
				return { ...q, answers: [...variants, ...q.answers.filter((a) => !variants.includes(a))] };
			});
			status = missing.length
				? `Am Haltepunkt noch nicht vorhanden: ${missing.join(', ')}`
				: 'Werte eingetragen.';
		} finally {
			running = false;
		}
	}
</script>

<label class="ed-label"
	>Programm<textarea class="ed-input mono" rows="8" bind:value={config.code}></textarea></label
>
<label class="ed-label mt-3"
	>Eingaben für input() (optional, eine pro Zeile)<textarea
		class="ed-input mono"
		rows="2"
		bind:value={config.stdin}
	></textarea></label
>
<div class="mt-3 grid grid-cols-2 gap-3">
	<label class="ed-label"
		>Haltepunkt: Zeile<input
			class="ed-input"
			type="number"
			min="1"
			value={config.breakpoint?.line ?? 1}
			oninput={(e) => setBreakpoint('line', (e.target as HTMLInputElement).value)}
		/></label
	>
	<label class="ed-label"
		>… beim wievielten Erreichen<input
			class="ed-input"
			type="number"
			min="1"
			value={config.breakpoint?.hit ?? 1}
			oninput={(e) => setBreakpoint('hit', (e.target as HTMLInputElement).value)}
		/></label
	>
</div>
<p class="mt-1 text-xs text-slate-500">
	Angehalten wird, bevor die Zeile ausgeführt wird – wie der gelbe Balken im Visualizer.
</p>

<p class="ed-label mt-4">Variablen und erwartete Werte</p>
<div class="mt-2 space-y-2">
	{#each questions as question, i (i)}
		<div class="flex items-center gap-2">
			<input
				class="ed-input mono mt-0 w-40"
				value={question.variable}
				oninput={(e) => setQuestion(i, { variable: (e.target as HTMLInputElement).value.trim() })}
				placeholder="Variable"
				aria-label={`Variable ${i + 1}`}
			/>
			<input
				class="ed-input mono mt-0 flex-1"
				value={question.answers.join(' | ')}
				onchange={(e) =>
					setQuestion(i, {
						answers: (e.target as HTMLInputElement).value
							.split('|')
							.map((a) => a.trim())
							.filter(Boolean)
					})}
				placeholder="akzeptierte Antworten, getrennt mit |"
				aria-label={`Antworten ${i + 1}`}
			/>
			<button type="button" class="text-xs text-rose-600" onclick={() => removeQuestion(i)}
				>Entfernen</button
			>
		</div>
	{/each}
</div>
<div class="mt-2 flex flex-wrap items-center gap-3">
	<button type="button" class="ed-button" onclick={addQuestion}>+ Variable</button>
	<button
		type="button"
		class="ed-button"
		disabled={running || !config.code || !questions.length}
		onclick={detect}>Werte aus dem Programm ermitteln</button
	>
	{#if status}<span class="text-xs text-slate-600" role="status">{status}</span>{/if}
</div>
<VisualizerOptions bind:config />
