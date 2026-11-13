<script lang="ts">
	import type { Recommendation } from '$lib/api';
	import PathIcon from './PathIcon.svelte';

	/** reload: von einer Aufgabenseite aus, die ihre Adresse nur beim Laden liest, neu laden */
	let {
		next,
		compact = false,
		reload = false
	}: { next: Recommendation; compact?: boolean; reload?: boolean } = $props();
</script>

<a
	href="/modules/{next.moduleSlug}/{next.taskSlug}"
	class="group flex flex-wrap items-center gap-x-5 gap-y-3 rounded-[var(--radius-ui)] transition-shadow hover:shadow-lg {compact
		? 'ui-card p-4 hover:border-slate-300'
		: 'bg-institutional-navy p-5 text-white sm:px-6'}"
	data-testid="next-task"
	data-sveltekit-reload={reload ? '' : undefined}
>
	<span
		class="flex shrink-0 items-center justify-center rounded-[var(--radius-ui-sm)] text-white {compact
			? 'h-10 w-10 bg-institutional-navy'
			: 'h-12 w-12 bg-institutional-gold'}"
	>
		<svg class="h-5 w-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"
			><path d="M8 5v14l11-7z" /></svg
		>
	</span>
	<span class="flex min-w-0 flex-1 basis-64 flex-col gap-0.5">
		<span
			class="font-mono text-[11px] font-bold tracking-wider uppercase {compact
				? 'text-institutional-gold'
				: 'text-amber-300'}">{compact ? 'Als Nächstes' : 'Weitermachen'}</span
		>
		<span
			class="flex items-center gap-2 text-lg leading-tight font-bold {compact
				? 'text-institutional-navy'
				: 'text-white'}"
		>
			{#if next.moduleIcon}<PathIcon
					emoji={next.moduleIcon}
					title={next.taskTitle}
					size={18}
				/>{/if}
			{next.taskTitle}
		</span>
		<span class="text-[13px] {compact ? 'text-slate-500' : 'text-slate-300'}">{next.reason}</span>
		{#if next.articleSlug}
			<span class="mt-1 text-[13px]">
				<button
					type="button"
					class="cursor-pointer text-left font-semibold underline {compact
						? 'text-institutional-navy hover:text-institutional-gold'
						: 'text-white hover:text-amber-300'}"
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
		class="inline-flex min-h-11 shrink-0 items-center rounded-[var(--radius-ui-sm)] px-5 text-sm font-semibold {compact
			? 'bg-institutional-navy text-white group-hover:bg-slate-800'
			: 'bg-white text-institutional-navy group-hover:bg-amber-50'}">Los →</span
	>
</a>
