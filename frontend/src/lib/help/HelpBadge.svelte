<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type HelpQueue } from '$lib/api';
	import { realtime } from '$lib/realtime.svelte';

	/** Zähler „3 warten“ für Lehrkräfte – erscheint nur, wenn jemand Hilfe braucht. */
	let count = $state(0);

	async function load() {
		try {
			count = (await api<HelpQueue>('/api/admin/help')).open.length;
		} catch {
			count = 0;
		}
	}

	onMount(load);

	$effect(() => {
		if (realtime.helpOpen !== null) count = realtime.helpOpen;
	});
</script>

{#if count > 0}
	<a
		href="/admin/help"
		class="flex items-center gap-2 rounded-[var(--radius-ui-sm)] border border-amber-300 bg-amber-50 px-3 py-2.5 text-sm font-semibold text-amber-800 hover:bg-amber-100"
		data-testid="help-badge"
		aria-label={`Hilfe-Rufe: ${count} warten`}
	>
		✋ {count}
		{count === 1 ? 'wartet' : 'warten'}
	</a>
{/if}
