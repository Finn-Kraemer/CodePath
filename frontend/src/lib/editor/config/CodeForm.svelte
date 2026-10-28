<script lang="ts">
	import type { Config } from '../types';
	import TestsForm from './TestsForm.svelte';

	let { config = $bindable() }: { config: Config } = $props();
</script>

<label class="ed-label"
	>Startcode<textarea class="ed-input mono" rows="6" bind:value={config.starterCode}
	></textarea></label
>
<label class="ed-label mt-3"
	>Musterlösung (für Probelauf und Lösungsansicht)<textarea
		class="ed-input mono"
		rows="6"
		bind:value={config.solution}
	></textarea></label
>
<div class="mt-3"><TestsForm bind:config solution={config.solution} /></div>
