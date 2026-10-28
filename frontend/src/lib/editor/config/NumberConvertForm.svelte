<script lang="ts">
	import { lines, type Config } from '../types';

	let { config = $bindable() }: { config: Config } = $props();
	const bases = [
		{ value: 2, label: 'binär (2)' },
		{ value: 8, label: 'oktal (8)' },
		{ value: 10, label: 'dezimal (10)' },
		{ value: 16, label: 'hexadezimal (16)' }
	];
	const fixed = $derived(Array.isArray(config.values) && config.values.length > 0);

	function setMode(useFixed: boolean) {
		if (useFixed) {
			config.values = ['42'];
			delete config.min;
			delete config.max;
			delete config.count;
		} else {
			delete config.values;
			config.min = 1;
			config.max = 255;
			config.count = 3;
		}
	}
</script>

<div class="grid grid-cols-2 gap-3">
	<label class="ed-label"
		>Von
		<select class="ed-input" bind:value={config.from}>
			{#each bases as base (base.value)}<option value={base.value}>{base.label}</option>{/each}
		</select></label
	>
	<label class="ed-label"
		>Nach
		<select class="ed-input" bind:value={config.to}>
			{#each bases as base (base.value)}<option value={base.value}>{base.label}</option>{/each}
		</select></label
	>
</div>

<fieldset class="mt-4 text-sm">
	<legend class="ed-label">Zahlen</legend>
	<label class="mr-4"
		><input type="radio" checked={!fixed} onchange={() => setMode(false)} /> zufällig je Person</label
	>
	<label><input type="radio" checked={fixed} onchange={() => setMode(true)} /> feste Werte</label>
</fieldset>

{#if fixed}
	<label class="ed-label mt-3"
		>Werte (einer pro Zeile, in der Ausgangsbasis)
		<textarea
			class="ed-input mono"
			rows="4"
			value={(config.values ?? []).join('\n')}
			oninput={(e) => (config.values = lines(e.currentTarget.value))}
		></textarea></label
	>
{:else}
	<div class="mt-3 grid grid-cols-3 gap-3">
		<label class="ed-label"
			>Kleinster Wert<input type="number" class="ed-input" min="0" bind:value={config.min} /></label
		>
		<label class="ed-label"
			>Größter Wert<input
				type="number"
				class="ed-input"
				max="65535"
				bind:value={config.max}
			/></label
		>
		<label class="ed-label"
			>Anzahl<input
				type="number"
				class="ed-input"
				min="1"
				max="10"
				bind:value={config.count}
			/></label
		>
	</div>
{/if}
