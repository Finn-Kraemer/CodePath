<script lang="ts">
	import type { TransferReport } from '$lib/api';
	import { ACTION_LABELS } from '$lib/transfer';

	let { report }: { report: TransferReport } = $props();

	/** Unveränderte Einträge sind oft sehr viele – standardmäßig nur Änderungen und Fehler zeigen */
	let showUnchanged = $state(false);

	const STYLES: Record<string, string> = {
		CREATED: 'bg-emerald-100 text-emerald-800',
		UPDATED: 'bg-sky-100 text-sky-800',
		UNCHANGED: 'bg-slate-100 text-slate-500',
		ERROR: 'bg-rose-100 text-rose-800'
	};

	const groups = $derived(
		Object.entries(
			report.items
				.filter((item) => showUnchanged || item.action !== 'UNCHANGED')
				.reduce<Record<string, TransferReport['items']>>((byArea, item) => {
					(byArea[item.area] ??= []).push(item);
					return byArea;
				}, {})
		)
	);

	const counts = $derived([
		{ action: 'CREATED', value: report.created },
		{ action: 'UPDATED', value: report.updated },
		{ action: 'UNCHANGED', value: report.unchanged },
		{ action: 'ERROR', value: report.errors }
	]);
</script>

<section
	class="border bg-white p-5 {report.errors > 0 ? 'border-rose-200' : 'border-emerald-200'}"
	role="status"
	aria-label="Ergebnis des Imports"
>
	<p class="font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
		Erkannt: {report.detected}
	</p>
	<div class="mt-3 flex flex-wrap gap-2">
		{#each counts as c (c.action)}
			<span class="px-3 py-1 text-xs font-bold {STYLES[c.action]}"
				>{c.value} {ACTION_LABELS[c.action]}</span
			>
		{/each}
	</div>
	{#if report.created + report.updated + report.errors === 0}
		<p class="mt-4 text-sm text-slate-600">
			Alles war schon genau so vorhanden – es wurde nichts geändert.
		</p>
	{/if}
	<label class="mt-4 flex items-center gap-2 text-sm text-slate-600">
		<input type="checkbox" bind:checked={showUnchanged} /> Unveränderte Einträge zeigen
	</label>
	{#each groups as [area, items] (area)}
		<h3 class="mt-5 text-xs font-black tracking-widest text-institutional-navy uppercase">
			{area}
		</h3>
		<ul class="mt-2 divide-y divide-slate-100 border border-slate-100">
			{#each items as item, i (i)}
				<li class="flex flex-wrap items-baseline gap-3 px-3 py-2 text-sm">
					<span
						class="w-24 shrink-0 text-center font-mono text-[9px] font-bold tracking-widest uppercase {STYLES[
							item.action
						]}">{ACTION_LABELS[item.action]}</span
					>
					<span class="font-mono text-xs break-all text-institutional-navy">{item.name}</span>
					{#if item.message}<span class="text-xs text-slate-500">– {item.message}</span>{/if}
				</li>
			{/each}
		</ul>
	{/each}
</section>
