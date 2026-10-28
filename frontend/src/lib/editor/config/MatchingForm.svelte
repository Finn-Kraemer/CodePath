<script lang="ts">
	import { nextId, type Config } from '../types';

	type Item = { id: string; text: string };
	let { config = $bindable() }: { config: Config } = $props();

	// Jede Zeile ist ein Paar; rechte Elemente ohne Paar sind Ablenker
	const pairs = $derived((config.pairs ?? []) as { left: string; right: string }[]);
	const distractors = $derived(
		((config.right ?? []) as Item[]).filter((r) => !pairs.some((p) => p.right === r.id))
	);

	function text(list: 'left' | 'right', id: string): string {
		return (config[list] as Item[]).find((i) => i.id === id)?.text ?? '';
	}

	function setText(list: 'left' | 'right', id: string, value: string) {
		config[list] = (config[list] as Item[]).map((i) => (i.id === id ? { ...i, text: value } : i));
	}

	function addPair() {
		const left = { id: nextId(config.left ?? [], 'l'), text: '' };
		const right = { id: nextId(config.right ?? [], 'r'), text: '' };
		config.left = [...(config.left ?? []), left];
		config.right = [...(config.right ?? []), right];
		config.pairs = [...pairs, { left: left.id, right: right.id }];
	}

	function removePair(i: number) {
		const pair = pairs[i];
		config.left = config.left.filter((l: Item) => l.id !== pair.left);
		config.right = config.right.filter((r: Item) => r.id !== pair.right);
		config.pairs = pairs.filter((_, j) => j !== i);
	}

	function addDistractor() {
		config.right = [...(config.right ?? []), { id: nextId(config.right ?? [], 'r'), text: '' }];
	}

	function removeDistractor(id: string) {
		config.right = config.right.filter((r: Item) => r.id !== id);
	}
</script>

<p class="ed-label">Paare (links Begriff, rechts das Passende – Schüler sehen rechts gemischt)</p>
<div class="mt-2 space-y-2">
	{#each pairs as pair, i (pair.left)}
		<div class="flex items-center gap-2">
			<input
				class="ed-input mt-0"
				value={text('left', pair.left)}
				oninput={(e) => setText('left', pair.left, e.currentTarget.value)}
				aria-label={`Begriff ${i + 1}`}
			/>
			<span aria-hidden="true">→</span>
			<input
				class="ed-input mt-0"
				value={text('right', pair.right)}
				oninput={(e) => setText('right', pair.right, e.currentTarget.value)}
				aria-label={`Gegenstück ${i + 1}`}
			/>
			<button
				type="button"
				class="ed-button"
				onclick={() => removePair(i)}
				aria-label="Paar entfernen">✕</button
			>
		</div>
	{/each}
	<button type="button" class="ed-button" onclick={addPair}>+ Paar</button>
</div>

<p class="ed-label mt-4">Ablenker rechts (optional)</p>
<div class="mt-2 space-y-2">
	{#each distractors as distractor (distractor.id)}
		<div class="flex items-center gap-2">
			<input
				class="ed-input mt-0"
				value={text('right', distractor.id)}
				oninput={(e) => setText('right', distractor.id, e.currentTarget.value)}
				aria-label="Ablenker"
			/>
			<button
				type="button"
				class="ed-button"
				onclick={() => removeDistractor(distractor.id)}
				aria-label="Ablenker entfernen">✕</button
			>
		</div>
	{/each}
	<button type="button" class="ed-button" onclick={addDistractor}>+ Ablenker</button>
</div>
