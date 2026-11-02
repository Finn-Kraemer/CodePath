<script lang="ts">
	import type { Config } from '../types';

	/** Code-Visualizer („Schritt für Schritt“) je Aufgabe ein- oder ausschalten und das Schrittlimit setzen. */
	let { config = $bindable() }: { config: Config } = $props();

	const mode = $derived(
		config.visualizer === true ? 'on' : config.visualizer === false ? 'off' : 'default'
	);

	function setMode(value: string) {
		if (value === 'on') config.visualizer = true;
		else if (value === 'off') config.visualizer = false;
		else delete config.visualizer;
	}

	function setMaxSteps(value: string) {
		const steps = Number(value);
		if (value && Number.isFinite(steps) && steps > 0) config.visualizerMaxSteps = Math.round(steps);
		else delete config.visualizerMaxSteps;
	}
</script>

<div class="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
	<label class="ed-label"
		>Schritt für Schritt (Code-Visualizer)<select
			class="ed-input"
			value={mode}
			onchange={(e) => setMode((e.target as HTMLSelectElement).value)}
		>
			<option value="default">Standard (an, im Abschlusstest aus)</option>
			<option value="on">An</option>
			<option value="off">Aus</option>
		</select></label
	>
	<label class="ed-label"
		>Schrittlimit<input
			class="ed-input"
			type="number"
			min="1"
			max="5000"
			placeholder="1000"
			value={config.visualizerMaxSteps ?? ''}
			oninput={(e) => setMaxSteps((e.target as HTMLInputElement).value)}
		/></label
	>
</div>
