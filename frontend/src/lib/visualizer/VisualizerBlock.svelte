<script lang="ts">
	import { onDestroy } from 'svelte';
	import { PythonRunner } from '$lib/python.svelte';
	import CodeVisualizer from './CodeVisualizer.svelte';

	/**
	 * Markdown-Baustein ```schritte: zeigt ein festes Beispielprogramm, das man Schritt für Schritt
	 * ansehen kann (z. B. Bubble Sort im Wissensartikel oder am Beamer). Python lädt erst beim Klick.
	 */
	let { code }: { code: string } = $props();

	let runner: PythonRunner | null = $state(null);
	let open = $state(false);
	let key = $state(0);

	function start() {
		if (!runner) {
			runner = new PythonRunner();
			runner.start();
		}
		key++;
		open = true;
	}

	onDestroy(() => runner?.stop());
</script>

<div class="not-prose my-6" data-testid="visualizer-block">
	{#if open && runner}
		{#key key}
			<CodeVisualizer
				{runner}
				{code}
				title="Beispiel Schritt für Schritt"
				onclose={() => (open = false)}
			/>
		{/key}
	{:else}
		<pre
			class="overflow-x-auto border-4 border-slate-800 bg-slate-900 p-4 font-mono text-xs leading-6 text-slate-200">{code}</pre>
		<button
			type="button"
			onclick={start}
			class="mt-2 rounded-[var(--radius-ui-sm)] border-2 border-institutional-navy px-4 py-2 text-sm font-semibold text-institutional-navy hover:bg-slate-50"
		>
			▶ Schritt für Schritt ansehen
		</button>
	{/if}
</div>
