<script lang="ts">
	import type { Config } from '../types';

	let { config = $bindable() }: { config: Config } = $props();
</script>

<p class="ed-label">Testfälle (versteckte prüfen, ob die Lösung allgemein ist)</p>
<div class="mt-2 space-y-2">
	{#each config.tests ?? [] as test, i (i)}
		<div class="grid grid-cols-12 gap-2">
			<textarea
				class="ed-input mono col-span-4 mt-0 text-xs"
				rows="2"
				bind:value={test.stdin}
				placeholder="Eingabe (stdin)"
				aria-label="Eingabe"
			></textarea>
			<textarea
				class="ed-input mono col-span-5 mt-0 text-xs"
				rows="2"
				bind:value={test.expectedOutput}
				placeholder="Erwartete Ausgabe"
				aria-label="Erwartete Ausgabe"
			></textarea>
			<label class="col-span-2 flex items-center gap-1 text-xs">
				<input
					type="checkbox"
					checked={test.hidden !== false}
					onchange={(e) => (test.hidden = e.currentTarget.checked)}
				/> versteckt
			</label>
			<button
				type="button"
				class="ed-button col-span-1"
				onclick={() => (config.tests = config.tests.filter((_: unknown, j: number) => j !== i))}
				aria-label="Test entfernen">✕</button
			>
		</div>
	{/each}
	<button
		type="button"
		class="ed-button"
		onclick={() => (config.tests = [...(config.tests ?? []), { stdin: '', expectedOutput: '' }])}
		>+ Testfall</button
	>
</div>
