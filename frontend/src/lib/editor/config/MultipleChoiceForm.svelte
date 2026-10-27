<script lang="ts">
	import type { Config } from '../types';

	let { config = $bindable() }: { config: Config } = $props();
	const ids = 'abcdefghijklmnopqrstuvwxyz';

	function toggleCorrect(id: string) {
		const correct: string[] = config.correct ?? [];
		config.correct = correct.includes(id) ? correct.filter((c) => c !== id) : [...correct, id];
		config.multiple = config.correct.length > 1 || config.multiple;
	}

	function add() {
		const used = new Set((config.options ?? []).map((o: any) => o.id));
		const id = [...ids].find((c) => !used.has(c)) ?? `o${used.size + 1}`;
		config.options = [...(config.options ?? []), { id, text: '' }];
	}

	function remove(id: string) {
		config.options = config.options.filter((o: any) => o.id !== id);
		config.correct = (config.correct ?? []).filter((c: string) => c !== id);
	}
</script>

<div class="space-y-3">
	{#each config.options ?? [] as option (option.id)}
		<div class="border border-slate-200 p-3">
			<div class="flex items-center gap-3">
				<label class="flex items-center gap-2 text-xs font-bold whitespace-nowrap text-green-700">
					<input
						type="checkbox"
						checked={(config.correct ?? []).includes(option.id)}
						onchange={() => toggleCorrect(option.id)}
					/> richtig
				</label>
				<input
					class="ed-input mt-0"
					bind:value={option.text}
					placeholder="Antworttext"
					aria-label="Antworttext"
				/>
				<button
					type="button"
					class="ed-button"
					onclick={() => remove(option.id)}
					aria-label="Option entfernen">✕</button
				>
			</div>
			<input
				class="ed-input text-xs"
				bind:value={option.explanation}
				placeholder="Erklärung nach dem Lösen (optional)"
				aria-label="Erklärung"
			/>
		</div>
	{/each}
	<div class="flex items-center gap-4">
		<button type="button" class="ed-button" onclick={add}>+ Option</button>
		<label class="flex items-center gap-2 text-sm"
			><input type="checkbox" bind:checked={config.multiple} /> Mehrfachauswahl</label
		>
		<label class="flex items-center gap-2 text-sm"
			><input type="checkbox" bind:checked={config.keepOrder} /> Reihenfolge nicht mischen</label
		>
	</div>
</div>
