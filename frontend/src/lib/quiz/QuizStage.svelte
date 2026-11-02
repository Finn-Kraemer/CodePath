<script lang="ts">
	import type { QuizView } from '$lib/api';
	import Markdown from '$lib/Markdown.svelte';

	/**
	 * Große Anzeige einer Quiz-Runde für Beamer und Lehrkraft: Frage, Optionen A–D, während der Frage
	 * nur die Zahl der Antworten, nach der Auflösung die anonyme Verteilung und die richtige Antwort.
	 */
	let {
		quiz,
		dark = false,
		onnext,
		onreveal,
		onfinish
	}: {
		quiz: QuizView;
		dark?: boolean;
		onnext?: () => void;
		onreveal?: () => void;
		onfinish?: () => void;
	} = $props();

	const revealed = $derived(quiz.phase === 'REVEAL');
	const max = $derived(Math.max(1, ...Object.values(quiz.distribution ?? {})));
	const COLORS = ['bg-rose-500', 'bg-sky-500', 'bg-amber-400', 'bg-emerald-500', 'bg-violet-500'];

	const button = $derived(
		dark
			? 'border-2 border-white/40 px-6 py-3 font-mono text-xs font-black tracking-widest uppercase hover:bg-white/10'
			: 'border-2 border-institutional-navy px-6 py-3 font-mono text-xs font-black tracking-widest text-institutional-navy uppercase hover:bg-slate-50'
	);
</script>

<section data-testid="quiz-stage" class={dark ? 'text-white' : 'text-slate-900'}>
	<div class="mb-6 flex flex-wrap items-baseline justify-between gap-4">
		<p
			class="font-mono text-sm font-bold tracking-[4px] uppercase {dark
				? 'text-institutional-gold'
				: 'text-slate-500'}"
		>
			{quiz.title}{quiz.courseName ? ` · ${quiz.courseName}` : ''}
		</p>
		{#if quiz.phase !== 'LOBBY' && quiz.phase !== 'FINISHED'}
			<p class="font-mono text-sm font-bold" data-testid="quiz-progress">
				Frage {quiz.index + 1} von {quiz.total}
			</p>
		{/if}
	</div>

	{#if quiz.phase === 'LOBBY'}
		<p class="text-4xl font-black tracking-tight uppercase">Gleich geht's los!</p>
		<p class="mt-4 text-xl {dark ? 'text-slate-300' : 'text-slate-600'}">
			Öffnet CodePath auf eurem Gerät und tippt auf „Quiz – mitmachen“. {quiz.total} Fragen.
		</p>
	{:else if quiz.phase === 'FINISHED'}
		<p class="text-4xl font-black tracking-tight uppercase">Geschafft!</p>
		{#if quiz.results}
			<ol class="mt-6 space-y-3" data-testid="quiz-results">
				{#each quiz.results as result (result.index)}
					{@const percent = result.answered
						? Math.round((result.correct / result.answered) * 100)
						: 0}
					<li class="text-lg">
						<span class="font-mono font-bold">{result.index + 1}.</span>
						{percent} % richtig
						<span class="text-sm opacity-60">({result.correct} von {result.answered})</span>
					</li>
				{/each}
			</ol>
		{/if}
	{:else}
		<div class="text-3xl leading-snug font-extrabold md:text-4xl" data-testid="quiz-question">
			<Markdown source={quiz.question ?? ''} class={dark ? 'prose-invert' : ''} />
		</div>
		<ul class="mt-8 grid grid-cols-1 gap-4 md:grid-cols-2">
			{#each quiz.options ?? [] as option, i (option.id)}
				{@const count = quiz.distribution?.[option.id] ?? 0}
				{@const right = revealed && quiz.correct?.includes(option.id)}
				<li
					class="relative overflow-hidden border-4 p-5 text-xl font-bold {right
						? 'border-emerald-400'
						: revealed
							? 'border-transparent opacity-60'
							: 'border-transparent'} {dark ? 'bg-slate-800' : 'bg-slate-100'}"
					data-option={option.id}
					data-correct={right ? 'true' : undefined}
				>
					{#if revealed}
						<div
							class="absolute inset-y-0 left-0 {COLORS[i % COLORS.length]} opacity-30"
							style="width: {(count / max) * 100}%"
						></div>
					{/if}
					<div class="relative flex items-center gap-4">
						<span
							class="flex h-10 w-10 shrink-0 items-center justify-center text-white {COLORS[
								i % COLORS.length
							]}">{option.label}</span
						>
						<span class="flex-1">{option.text}</span>
						{#if revealed}
							<span class="font-mono text-2xl">{count}</span>
							{#if right}<span aria-label="richtig">✓</span>{/if}
						{/if}
					</div>
				</li>
			{/each}
		</ul>
		<p class="mt-6 font-mono text-lg" data-testid="quiz-answered">
			{quiz.answered}
			{quiz.answered === 1 ? 'Antwort' : 'Antworten'}
		</p>
	{/if}

	{#if onnext || onreveal || onfinish}
		<div class="mt-10 flex flex-wrap gap-3">
			{#if quiz.phase === 'QUESTION' && onreveal}
				<button type="button" class={button} onclick={onreveal}>Auflösen</button>
			{:else if (quiz.phase === 'LOBBY' || quiz.phase === 'REVEAL') && onnext}
				<button type="button" class={button} onclick={onnext}>
					{quiz.phase === 'LOBBY'
						? 'Erste Frage'
						: quiz.index + 1 >= quiz.total
							? 'Ergebnis zeigen'
							: 'Nächste Frage'}
				</button>
			{/if}
			{#if quiz.phase !== 'FINISHED' && onfinish}
				<button type="button" class="{button} opacity-60" onclick={onfinish}>Quiz beenden</button>
			{/if}
		</div>
	{/if}
</section>
