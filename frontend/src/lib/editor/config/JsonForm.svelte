<script lang="ts">
	import type { Config } from '../types';

	let { config = $bindable() }: { config: Config } = $props();
	let text = $state(JSON.stringify(config, null, 2));
	let error = $state('');

	function apply() {
		try {
			config = JSON.parse(text);
			error = '';
		} catch (e) {
			error = (e as Error).message;
		}
	}
</script>

<label class="ed-label"
	>Konfiguration (JSON)
	<textarea class="ed-input mono text-xs" rows="16" bind:value={text} onblur={apply}></textarea>
</label>
{#if error}<p class="mt-1 text-xs text-rose-600">{error}</p>{/if}
