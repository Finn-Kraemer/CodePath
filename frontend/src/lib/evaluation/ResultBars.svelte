<script lang="ts">
	import type { Schemas } from '$lib/api';

	let { stats }: { stats: Schemas['CriterionStats'] } = $props();

	const max = $derived(Math.max(1, ...(stats.distribution ?? []).map((b) => b.count)));
	const total = $derived((stats.distribution ?? []).reduce((sum, b) => sum + b.count, 0));
</script>

<div class="border border-slate-200 bg-white p-5">
	<div class="flex flex-wrap items-baseline justify-between gap-2">
		<p class="font-bold text-institutional-navy">{stats.label}</p>
		<p class="font-mono text-[10px] text-slate-400">
			{stats.answered} Antwort{stats.answered === 1 ? '' : 'en'}{#if stats.average != null}
				· Ø <span class="text-sm font-bold text-institutional-navy"
					>{stats.average.toLocaleString('de-DE')}</span
				>{/if}
		</p>
	</div>

	{#if stats.distribution}
		<ul class="mt-3 flex flex-col gap-1">
			{#each stats.distribution as bucket (bucket.value)}
				<li class="grid grid-cols-[minmax(6rem,12rem)_1fr_5.5rem] items-center gap-3 text-xs">
					<span class="truncate text-slate-600" title={bucket.label ?? bucket.value}
						>{bucket.label && bucket.label !== bucket.value
							? stats.type === 'SCALE'
								? `${bucket.value} · ${bucket.label}`
								: bucket.label
							: bucket.value}</span
					>
					<span class="h-4 bg-slate-100" aria-hidden="true">
						<span
							class="block h-4 bg-institutional-navy"
							style="width: {(bucket.count / max) * 100}%"
						></span>
					</span>
					<span class="text-right font-mono whitespace-nowrap text-slate-500"
						>{bucket.count}{#if total > 0 && stats.type !== 'CHOICE'}
							<span class="text-slate-300">({Math.round((bucket.count / total) * 100)} %)</span
							>{/if}</span
					>
				</li>
			{/each}
		</ul>
	{/if}

	{#if stats.texts}
		{#if stats.texts.length === 0}
			<p class="mt-2 text-sm text-slate-400 italic">Keine Antworten.</p>
		{:else}
			<ul class="mt-3 flex max-h-80 flex-col gap-2 overflow-y-auto">
				{#each stats.texts as text, i (i)}
					<li class="border-l-4 border-slate-200 bg-slate-50 px-3 py-2 text-sm">
						<p class="whitespace-pre-line text-slate-700">{text.text}</p>
						{#if text.author}
							<p class="mt-1 font-mono text-[10px] text-slate-400">– {text.author}</p>
						{/if}
					</li>
				{/each}
			</ul>
		{/if}
	{/if}
</div>
