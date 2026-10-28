<script lang="ts">
	import type { Config } from '../types';
	import TestsForm from './TestsForm.svelte';

	let { config = $bindable(), code = false }: { config: Config; code?: boolean } = $props();

	const gaps = $derived((config.template ?? '').split('___').length - 1);
	// Vorlage mit der jeweils ersten richtigen Antwort, für „Ausgaben erzeugen“
	const filled = $derived(
		(config.template ?? '')
			.split('___')
			.map(
				(part: string, i: number) =>
					part + (i < gaps ? (config.blanks?.[i]?.answers?.[0] ?? '') : '')
			)
			.join('')
	);

	// Anzahl der Lösungen folgt der Anzahl der Lücken in der Vorlage
	$effect(() => {
		const blanks = config.blanks ?? [];
		if (blanks.length !== gaps) {
			config.blanks = Array.from({ length: gaps }, (_, i) => blanks[i] ?? { answers: [''] });
		}
	});

	function setAnswers(i: number, value: string) {
		config.blanks[i].answers = value
			.split('|')
			.map((a: string) => a.trim())
			.filter(Boolean);
	}

	function setOptions(i: number, value: string) {
		const options = value
			.split('|')
			.map((a: string) => a.trim())
			.filter(Boolean);
		if (options.length) config.blanks[i].options = options;
		else delete config.blanks[i].options;
	}
</script>

<label class="ed-label"
	>Vorlage – Lücken als ___ (drei Unterstriche)
	<textarea class="ed-input mono" rows="8" bind:value={config.template}></textarea>
</label>
<p class="mt-1 text-xs text-slate-500">{gaps} Lücke(n)</p>
<div class="mt-3 space-y-2">
	{#each config.blanks ?? [] as blank, i (i)}
		<div class="grid grid-cols-12 items-center gap-2 text-sm">
			<span class="col-span-1 font-mono text-slate-400">{i + 1}</span>
			<input
				class="ed-input mono col-span-5 mt-0"
				value={(blank.answers ?? []).join(' | ')}
				onchange={(e) => setAnswers(i, e.currentTarget.value)}
				placeholder="Antwort | Alternative"
				aria-label={`Antworten Lücke ${i + 1}`}
			/>
			<input
				class="ed-input col-span-4 mt-0 text-xs"
				value={(blank.options ?? []).join(' | ')}
				onchange={(e) => setOptions(i, e.currentTarget.value)}
				placeholder="Auswahlliste (optional)"
				aria-label={`Auswahl Lücke ${i + 1}`}
			/>
			<label class="col-span-2 flex items-center gap-1 text-xs"
				><input type="checkbox" bind:checked={blank.caseSensitive} /> Groß/klein</label
			>
		</div>
	{/each}
</div>
{#if code}
	<div class="mt-4"><TestsForm bind:config solution={filled} /></div>
{/if}
