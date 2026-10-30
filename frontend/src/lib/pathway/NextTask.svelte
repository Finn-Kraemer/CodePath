<script lang="ts">
	import type { Recommendation } from '$lib/api';

	/** reload: von einer Aufgabenseite aus, die ihre Adresse nur beim Laden liest, neu laden */
	let {
		next,
		compact = false,
		reload = false
	}: { next: Recommendation; compact?: boolean; reload?: boolean } = $props();
</script>

<a
	href="/modules/{next.moduleSlug}/{next.taskSlug}"
	class="group flex items-center gap-5 border-2 border-institutional-gold bg-amber-50/60 transition-all hover:bg-amber-50 hover:shadow-md {compact
		? 'p-4'
		: 'p-6'}"
	data-testid="next-task"
	data-sveltekit-reload={reload ? '' : undefined}
>
	<span class="text-3xl">{next.moduleIcon}</span>
	<span class="flex min-w-0 flex-1 flex-col gap-1">
		<span class="font-mono text-[9px] font-black tracking-[3px] text-institutional-gold uppercase"
			>Empfohlen als Nächstes</span
		>
		<span class="text-lg leading-tight font-bold text-institutional-navy">{next.taskTitle}</span>
		<span class="text-xs text-slate-500">{next.reason}</span>
		{#if next.articleSlug}
			<span class="mt-1 text-xs">
				<button
					type="button"
					class="cursor-pointer text-left font-bold text-institutional-navy underline hover:text-institutional-gold"
					onclick={(e) => {
						e.preventDefault();
						e.stopPropagation();
						window.location.href = `/knowledge/${next.articleSlug}`;
					}}
				>
					Hier hilft dir der Artikel „{next.articleTitle ?? 'Wissensartikel'}“ →
				</button>
			</span>
		{/if}
	</span>
	<span
		class="shrink-0 bg-institutional-navy px-5 py-3 text-[10px] font-black tracking-[3px] text-white uppercase group-hover:bg-slate-800"
		>Los</span
	>
</a>
