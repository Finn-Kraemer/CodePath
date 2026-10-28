<script lang="ts">
	import type { Compass } from '$lib/api';

	let { compass }: { compass: Compass } = $props();
</script>

<section class="border border-slate-200 bg-white p-8 shadow-sm md:p-12" aria-label="Berufe-Kompass">
	<h2 class="mb-2 font-sans text-sm font-black tracking-widest text-slate-400 uppercase">
		Mein Berufe-Kompass
	</h2>
	<p class="mb-8 text-sm leading-relaxed text-slate-500">
		Jedes Modul steht für ein Berufsfeld. Je mehr Punkte du in einem Modul holst, desto stärker
		zeigt der Kompass in diese Richtung.
	</p>

	{#if compass.top.length}
		<div class="mb-8 border-2 border-institutional-gold bg-amber-50/60 p-6">
			<p class="font-mono text-[9px] font-black tracking-[3px] text-institutional-gold uppercase">
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
		<p class="mb-8 border border-dashed border-slate-200 p-6 text-sm text-slate-500">
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
					<span class="h-2 w-full bg-slate-100">
						<span
							class="block h-full bg-institutional-gold transition-all duration-700"
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
