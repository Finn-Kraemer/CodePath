<script lang="ts">
	import type { Config } from '../types';

	let { config = $bindable() }: { config: Config } = $props();

	const textLines = $derived(((config.text ?? '') as string).replace(/\r\n/g, '\n').split('\n'));
	const targets = $derived((config.targets ?? []) as number[]);

	// Ziele, die nach dem Bearbeiten des Texts nicht mehr existieren, fallen weg
	$effect(() => {
		const valid = targets.filter((t) => t <= textLines.length && textLines[t - 1]?.trim());
		if (valid.length !== targets.length) config.targets = valid;
	});

	function toggle(line: number) {
		config.targets = targets.includes(line)
			? targets.filter((t) => t !== line)
			: [...targets, line].sort((a, b) => a - b);
	}
</script>

<label class="ed-label"
	>Text (jede Zeile ist markierbar)<textarea class="ed-input mono" rows="8" bind:value={config.text}
	></textarea></label
>

<p class="ed-label mt-4">Richtige Zeilen anklicken</p>
<div class="mt-2 border border-slate-200">
	{#each textLines as text, i (i)}
		{#if text.trim()}
			<label
				class="flex items-center gap-2 px-2 py-1 text-sm {targets.includes(i + 1)
					? 'bg-green-50'
					: ''}"
			>
				<input type="checkbox" checked={targets.includes(i + 1)} onchange={() => toggle(i + 1)} />
				<span class="w-6 text-right font-mono text-xs text-slate-400">{i + 1}</span>
				<span class="truncate {config.mono ? 'font-mono' : ''}">{text}</span>
			</label>
		{/if}
	{/each}
</div>

<label class="mt-3 flex items-center gap-2 text-sm"
	><input type="checkbox" bind:checked={config.mono} /> Als Code anzeigen (Festbreitenschrift)</label
>
<label class="mt-1 flex items-center gap-2 text-sm">
	<input
		type="checkbox"
		checked={config.showCount !== false}
		onchange={(e) => (config.showCount = e.currentTarget.checked)}
	/> Anzahl der gesuchten Zeilen verraten
</label>
