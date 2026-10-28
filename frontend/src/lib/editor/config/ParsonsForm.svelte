<script lang="ts">
	import { nextId, type Config } from '../types';

	type Line = { id: string; code: string; indent?: number };
	let { config = $bindable() }: { config: Config } = $props();

	function move(i: number, delta: number) {
		const lines = [...config.lines];
		const j = i + delta;
		if (j < 0 || j >= lines.length) return;
		[lines[i], lines[j]] = [lines[j], lines[i]];
		config.lines = lines;
	}

	const allIds = $derived([...(config.lines ?? []), ...(config.distractors ?? [])] as Line[]);
</script>

<p class="ed-label">Zeilen in der richtigen Reihenfolge (Schüler bekommen sie gemischt)</p>
<div class="mt-2 space-y-2">
	{#each config.lines ?? [] as line, i (line.id)}
		<div class="flex items-center gap-2">
			<span class="w-6 font-mono text-xs text-slate-400">{i + 1}.</span>
			<input
				type="number"
				class="ed-input mt-0 w-16"
				min="0"
				max="6"
				bind:value={line.indent}
				aria-label={`Einrückung Zeile ${i + 1}`}
				title="Einrückung (Stufen)"
			/>
			<input class="ed-input mono mt-0" bind:value={line.code} aria-label={`Code Zeile ${i + 1}`} />
			<button type="button" class="ed-button" onclick={() => move(i, -1)} aria-label="nach oben"
				>▲</button
			>
			<button type="button" class="ed-button" onclick={() => move(i, 1)} aria-label="nach unten"
				>▼</button
			>
			<button
				type="button"
				class="ed-button"
				onclick={() => (config.lines = config.lines.filter((_: Line, j: number) => j !== i))}
				aria-label="entfernen">✕</button
			>
		</div>
	{/each}
	<button
		type="button"
		class="ed-button"
		onclick={() =>
			(config.lines = [...(config.lines ?? []), { id: nextId(allIds, 'z'), code: '', indent: 0 }])}
		>+ Zeile</button
	>
</div>

<p class="ed-label mt-4">Ablenker (Zeilen, die nicht ins Programm gehören)</p>
<div class="mt-2 space-y-2">
	{#each config.distractors ?? [] as line, i (line.id)}
		<div class="flex items-center gap-2">
			<input class="ed-input mono mt-0" bind:value={line.code} aria-label={`Ablenker ${i + 1}`} />
			<button
				type="button"
				class="ed-button"
				onclick={() =>
					(config.distractors = config.distractors.filter((_: Line, j: number) => j !== i))}
				aria-label="Ablenker entfernen">✕</button
			>
		</div>
	{/each}
	<button
		type="button"
		class="ed-button"
		onclick={() =>
			(config.distractors = [...(config.distractors ?? []), { id: nextId(allIds, 'd'), code: '' }])}
		>+ Ablenker</button
	>
</div>

<label class="mt-4 flex items-center gap-2 text-sm">
	<input
		type="checkbox"
		checked={config.checkIndent !== false}
		onchange={(e) => (config.checkIndent = e.currentTarget.checked)}
	/> Einrückung wird bewertet
</label>
