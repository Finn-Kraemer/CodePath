<script lang="ts">
	import { nextId, type Config } from '../types';

	let { config = $bindable() }: { config: Config } = $props();

	// Die Reihenfolge im Editor ist die richtige Reihenfolge; Schüler bekommen sie gemischt
	function sync(items: { id: string; text: string }[]) {
		config.items = items;
		config.correctOrder = items.map((i) => i.id);
	}

	function move(i: number, delta: number) {
		const items = [...config.items];
		const j = i + delta;
		if (j < 0 || j >= items.length) return;
		[items[i], items[j]] = [items[j], items[i]];
		sync(items);
	}
</script>

<p class="ed-label">Elemente in der richtigen Reihenfolge</p>
<div class="mt-2 space-y-2">
	{#each config.items ?? [] as item, i (item.id)}
		<div class="flex items-center gap-2">
			<span class="w-6 font-mono text-xs text-slate-400">{i + 1}.</span>
			<input class="ed-input mt-0" bind:value={item.text} aria-label={`Element ${i + 1}`} />
			<button type="button" class="ed-button" onclick={() => move(i, -1)} aria-label="nach oben"
				>▲</button
			>
			<button type="button" class="ed-button" onclick={() => move(i, 1)} aria-label="nach unten"
				>▼</button
			>
			<button
				type="button"
				class="ed-button"
				onclick={() => sync(config.items.filter((_: unknown, j: number) => j !== i))}
				aria-label="entfernen">✕</button
			>
		</div>
	{/each}
	<button
		type="button"
		class="ed-button"
		onclick={() =>
			sync([...(config.items ?? []), { id: nextId(config.items ?? [], 's'), text: '' }])}
		>+ Element</button
	>
</div>
