<script lang="ts">
	import type { Compass } from '$lib/api';

	let { compass }: { compass: Compass } = $props();
</script>

<section class="ui-card p-6 md:p-8" aria-label="Berufe-Kompass">
	<h2 class="mb-2 text-xl font-bold text-institutional-navy">Mein Berufe-Kompass</h2>
	<p class="mb-6 text-[15px] leading-relaxed text-slate-600">
		Jedes Modul steht für ein Berufsfeld. Je mehr Punkte du in einem Modul holst, desto stärker
		zeigt der Kompass in diese Richtung.
	</p>

	{#if compass.top.length}
		<div class="mb-6 rounded-[var(--radius-ui)] border border-amber-200 bg-amber-50 p-5">
			<p class="font-mono text-[11px] font-black tracking-wider text-institutional-gold uppercase">
				Dein Kompass zeigt Richtung
			</p>
			<ol class="mt-3 flex flex-col gap-2">
				{#each compass.top as entry, i (entry.moduleSlug)}
					<li class="flex flex-wrap items-baseline gap-x-3">
						<span class="font-mono text-xs font-bold text-slate-400">{i + 1}.</span>
						<span
							class="font-bold text-institutional-navy {i === 0 ? 'text-xl' : 'text-base'}"
							data-testid="compass-career">{entry.career}</span
						>
						<span class="text-xs text-slate-500">({entry.icon} {entry.moduleTitle})</span>
					</li>
				{/each}
			</ol>
		</div>
	{:else}
		<p
			class="mb-6 rounded-[var(--radius-ui-sm)] border border-dashed border-slate-300 p-5 text-sm text-slate-600"
		>
			Noch keine Richtung – löse ein paar Aufgaben, dann zeigt dein Kompass, welche Berufe zu dir
			passen könnten.
		</p>
	{/if}

	<ul class="flex flex-col gap-4">
		{#each compass.entries as entry (entry.moduleSlug)}
			<li class="grid grid-cols-1 gap-1 md:grid-cols-[14rem_1fr_3rem] md:items-center md:gap-4">
				<span class="text-sm font-bold text-institutional-navy"
					>{entry.icon} {entry.moduleTitle}</span
				>
				<span class="flex flex-col gap-1">
					<span class="h-2 w-full overflow-hidden rounded-full bg-slate-100">
						<span
							class="block h-full rounded-full bg-institutional-gold transition-all duration-700"
							style="width: {entry.percent}%"
						></span>
					</span>
					<span class="text-xs text-slate-500">{entry.career}</span>
				</span>
				<span class="font-mono text-xs font-bold text-slate-500 md:text-right"
					>{entry.percent} %</span
				>
			</li>
		{/each}
	</ul>
</section>
