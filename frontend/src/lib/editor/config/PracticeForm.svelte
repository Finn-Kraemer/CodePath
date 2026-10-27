<script lang="ts">
	import { nextId, type Config } from '../types';

	let { config = $bindable(), points }: { config: Config; points: number } = $props();

	const sum = $derived(
		(config.rubric ?? []).reduce((a: number, c: any) => a + (Number(c.points) || 0), 0)
	);
</script>

<label class="ed-label"
	>Arbeitsauftrag (Markdown)<textarea class="ed-input" rows="6" bind:value={config.instructions}
	></textarea></label
>
<label class="ed-label mt-3"
	>Hinweis zur Abgabe<input class="ed-input" bind:value={config.submissionNote} /></label
>
<p class="ed-label mt-4">Rubrik</p>
<div class="mt-2 space-y-2">
	{#each config.rubric ?? [] as criterion, i (criterion.id)}
		<div class="flex items-center gap-2">
			<input
				class="ed-input mt-0"
				bind:value={criterion.label}
				placeholder="Kriterium"
				aria-label="Kriterium"
			/>
			<input
				class="ed-input mt-0 w-24"
				type="number"
				min="1"
				bind:value={criterion.points}
				aria-label="Punkte"
			/>
			<button
				type="button"
				class="ed-button"
				onclick={() => (config.rubric = config.rubric.filter((_: unknown, j: number) => j !== i))}
				aria-label="entfernen">✕</button
			>
		</div>
	{/each}
	<button
		type="button"
		class="ed-button"
		onclick={() =>
			(config.rubric = [
				...(config.rubric ?? []),
				{ id: nextId(config.rubric ?? [], 'k'), label: '', points: 1 }
			])}>+ Kriterium</button
	>
	<p class="text-xs {sum === points ? 'text-slate-500' : 'font-bold text-rose-600'}">
		Summe {sum} von {points} Punkten
	</p>
</div>
