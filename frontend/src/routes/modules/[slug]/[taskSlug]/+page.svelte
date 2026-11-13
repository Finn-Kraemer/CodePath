<script lang="ts">
	import PathIcon from '$lib/pathway/PathIcon.svelte';
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import {
		api,
		ApiError,
		type ArticleSummary,
		type HintResult,
		type Recommendation,
		type SubmitResult,
		type TaskDetail
	} from '$lib/api';
	import NextTask from '$lib/pathway/NextTask.svelte';
	import HelpButton from '$lib/help/HelpButton.svelte';
	import { auth } from '$lib/auth.svelte';
	import Markdown from '$lib/Markdown.svelte';
	import TaskRenderer from '$lib/tasks/TaskRenderer.svelte';
	import type { Answer } from '$lib/tasks/types';
	import { DIFFICULTY_LABELS, STATUS_LABELS, TYPE_LABELS } from '$lib/tasks/labels';

	const moduleSlug = page.params.slug;
	const taskSlug = page.params.taskSlug;
	const base = `/api/modules/${moduleSlug}/${taskSlug}`;

	let task = $state<TaskDetail | null>(null);
	let result = $state<SubmitResult | null>(null);
	let error = $state('');
	let blockedReason = $state('');
	let busy = $state(false);
	// Erzwingt ein Neuzeichnen der Aufgabe, wenn sich Lösung oder Status ändern
	let renderKey = $state(0);
	/** Empfohlene nächste Aufgabe, sobald diese hier erledigt ist */
	let next = $state<Recommendation | null>(null);

	const disabled = $derived(
		!task ||
			!task.isOpen ||
			task.isLocked ||
			task.status === 'SOLVED' ||
			task.status === 'PENDING_REVIEW'
	);
	const canHint = $derived(
		!!task && (task.hints?.length ?? 0) < (task.hintsTotal ?? 0) && task.status === 'OPEN'
	);

	// Wissensartikel, die diese Person sehen darf – für „Mehr dazu im Wiki“
	let articles = $state<ArticleSummary[]>([]);
	/** Ausdrücklich verknüpfter Artikel, sonst der Artikel zum Modul; nur wenn er sichtbar ist */
	const wikiArticle = $derived(
		task
			? (articles.find((a) => a.slug === task?.knowledgeArticle) ??
					articles.find((a) => a.moduleSlug === task?.moduleSlug))
			: undefined
	);

	async function load() {
		try {
			task = await api<TaskDetail>(base);
			renderKey++;
			await loadNext();
		} catch (e) {
			if (e instanceof ApiError && e.status === 403) blockedReason = e.message;
			else error = (e as Error).message;
		}
	}

	onMount(() => {
		load();
		api<ArticleSummary[]>('/api/knowledge')
			.then((list) => (articles = list))
			.catch(() => (articles = []));
	});

	async function submit(answer: Answer) {
		busy = true;
		error = '';
		try {
			result = await api<SubmitResult>(`${base}/submit`, { method: 'POST', body: answer });
			if (result.outcome !== 'INVALID') {
				const before = task?.status;
				await reload();
				if (before !== task?.status) renderKey++;
			}
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	async function reload() {
		task = await api<TaskDetail>(base);
		await loadNext();
	}

	async function loadNext() {
		if (!task || task.status === 'OPEN') return;
		const recommendation = await api<Recommendation | undefined>('/api/learning/next').catch(
			() => undefined
		);
		next =
			recommendation &&
			!(recommendation.moduleSlug === moduleSlug && recommendation.taskSlug === taskSlug)
				? recommendation
				: null;
	}

	async function requestHint() {
		if (!task) return;
		const penalty = task.hintPenaltyPercent ?? 0;
		if (
			penalty > 0 &&
			!confirm(`Ein Hinweis kostet ${penalty} % der Punkte dieser Aufgabe. Anzeigen?`)
		)
			return;
		try {
			const hint = await api<HintResult>(`${base}/hint`, { method: 'POST' });
			task.hints = hint.hints;
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function reveal() {
		if (
			!confirm(
				'Wenn du die Lösung aufdeckst, bekommst du für diese Aufgabe keine Punkte mehr. Fortfahren?'
			)
		)
			return;
		try {
			await api(`${base}/reveal`, { method: 'POST' });
			await reload();
			renderKey++;
		} catch (e) {
			error = (e as Error).message;
		}
	}

	const outcomeStyle: Record<string, string> = {
		CORRECT: 'border-emerald-300 bg-emerald-50 text-emerald-900',
		PARTIAL: 'border-amber-200 bg-amber-50 text-amber-800',
		WRONG: 'border-rose-200 bg-rose-50 text-rose-800',
		INVALID: 'border-slate-200 bg-slate-50 text-slate-700',
		MANUAL: 'border-blue-200 bg-blue-50 text-blue-800',
		UNGRADED: 'border-slate-200 bg-slate-50 text-slate-700'
	};
</script>

<svelte:head><title>{task?.title ?? 'Aufgabe'} | CodePath</title></svelte:head>

<div class="mx-auto max-w-7xl">
	<div class="mb-6">
		<a
			href={`/modules/${moduleSlug}`}
			class="inline-flex items-center gap-1.5 text-sm font-semibold text-slate-600 hover:text-institutional-navy"
		>
			<svg
				class="h-4 w-4"
				viewBox="0 0 24 24"
				fill="none"
				stroke="currentColor"
				stroke-width="2.2"
				stroke-linecap="round"
				stroke-linejoin="round"
				aria-hidden="true"><path d="M15 6l-6 6 6 6" /></svg
			>
			{task?.moduleTitle ?? 'Modul'}
		</a>
		{#if task}
			<h1 class="ui-title mt-2 sm:text-[2rem]">{task.title}</h1>
			<div class="mt-3 flex flex-wrap items-center gap-2">
				<span class="ui-chip ui-chip-info">{TYPE_LABELS[task.type ?? ''] ?? task.type}</span>
				<span class="ui-chip">{DIFFICULTY_LABELS[task.difficulty ?? ''] ?? task.difficulty}</span>
				<span class="ui-chip ui-chip-now">{task.points} Punkte</span>
				<span
					class="ui-chip {task.status === 'SOLVED'
						? 'ui-chip-done'
						: task.isLocked
							? 'ui-chip-danger'
							: 'border border-slate-300 bg-white'}"
				>
					{task.isLocked && task.status !== 'FAILED'
						? 'Gesperrt'
						: STATUS_LABELS[task.status ?? 'OPEN']}
				</span>
			</div>
		{/if}
	</div>

	{#if error}
		<div class="ui-card mb-6 border-rose-200 bg-rose-50 p-5 text-sm text-rose-700" role="alert">
			{error}
		</div>
	{/if}

	{#if blockedReason}
		<div
			class="ui-card mx-auto max-w-xl border-dashed border-slate-300 p-10 text-center"
			role="alert"
			data-testid="task-blocked"
		>
			<span
				class="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-slate-100 text-slate-500"
			>
				<PathIcon name="lock" size={28} />
			</span>
			<h2 class="text-xl font-extrabold text-institutional-navy">Noch nicht freigeschaltet</h2>
			<p class="mt-2 text-[15px] text-slate-600">{blockedReason}</p>
			<a href="/modules" class="ui-btn ui-btn-primary mt-6">Zum CodePath →</a>
		</div>
	{:else if !task}
		<div class="flex justify-center py-24">
			<div
				class="h-12 w-12 animate-spin rounded-full border-4 border-slate-200 border-t-institutional-navy"
			></div>
		</div>
	{:else}
		<div class="grid grid-cols-1 gap-6 lg:grid-cols-3 lg:gap-8">
			<div class="min-w-0 space-y-5 lg:col-span-2">
				<section class="ui-card space-y-4 p-6 sm:p-8" aria-label="Aufgabe">
					{#if task.learningGoal}
						<p
							class="flex gap-2.5 rounded-[var(--radius-ui-sm)] bg-amber-50 px-4 py-3 text-[15px] leading-relaxed text-amber-950"
						>
							<PathIcon name="target" size={18} class="mt-0.5 text-institutional-gold" />
							<span><strong>Lernziel:</strong> {task.learningGoal}</span>
						</p>
					{/if}
					<div class="prose max-w-none prose-slate">
						{#if task.story}<Markdown source={task.story} class="text-slate-700" />{/if}
						{#if task.assignment}
							<div class={task.story ? 'mt-5 border-t border-slate-100 pt-5' : ''}>
								<p class="ui-eyebrow !text-institutional-navy">Deine Aufgabe</p>
								<Markdown source={task.assignment} />
							</div>
						{/if}
					</div>
				</section>

				{#if task.reviewComment}
					<div class="ui-card border-sky-200 bg-sky-50 p-5 text-sm text-sky-950">
						<span class="mb-1 block font-semibold">Rückmeldung der Lehrkraft</span>
						{task.reviewComment}
					</div>
				{/if}
				{#if !task.isOpen && task.status !== 'SOLVED'}
					<div class="ui-card border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
						Die Bearbeitungszeit für dieses Modul ist abgelaufen.
					</div>
				{/if}

				{#if result}
					<div
						class="flex gap-3 rounded-[var(--radius-ui)] border p-5 text-[15px] {outcomeStyle[
							result.outcome ?? 'INVALID'
						]}"
						role="status"
						aria-live="polite"
					>
						{#if result.outcome === 'CORRECT'}
							<span
								class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-emerald-600 text-white"
								aria-hidden="true"><PathIcon name="check" size={18} /></span
							>
						{/if}
						<div>
							<p class="font-semibold">{result.feedback}</p>
							{#if result.attemptsLeft !== undefined && result.attemptsLeft !== null && !result.isCorrect && result.outcome !== 'INVALID'}
								<p class="mt-1 text-[13px]">Verbleibende Versuche: {result.attemptsLeft}</p>
							{/if}
						</div>
					</div>
				{/if}

				{#key renderKey}
					<TaskRenderer {task} {disabled} {busy} {result} {submit} />
				{/key}

				{#if task.explanation}
					<div class="ui-card border-emerald-200 bg-emerald-50 p-6">
						<p class="mb-2 font-semibold text-emerald-900">Erklärung</p>
						<Markdown source={task.explanation} class="text-[15px] text-emerald-950" />
					</div>
				{/if}

				{#if next}
					<NextTask {next} compact reload />
				{/if}
			</div>

			<aside class="space-y-5">
				<section class="ui-card p-5" aria-labelledby="hints-title">
					<div class="mb-3 flex items-center gap-2.5">
						<span
							class="flex h-8 w-8 items-center justify-center rounded-[var(--radius-ui-sm)] bg-amber-100 text-institutional-gold"
						>
							<svg
								class="h-[18px] w-[18px]"
								viewBox="0 0 24 24"
								fill="none"
								stroke="currentColor"
								stroke-width="2"
								stroke-linecap="round"
								stroke-linejoin="round"
								aria-hidden="true"
								><path
									d="M9 18h6M10 21h4M12 3a6 6 0 00-3.5 10.9c.6.4 1 1.1 1 1.8V16h5v-.3c0-.7.4-1.4 1-1.8A6 6 0 0012 3z"
								/></svg
							>
						</span>
						<h2 id="hints-title" class="flex-1 text-base font-bold">Hinweise</h2>
						{#if task.hintsTotal}
							<span class="font-mono text-xs text-slate-500"
								>{task.hints?.length ?? 0} / {task.hintsTotal}</span
							>
						{/if}
					</div>
					{#if task.hintsTotal === 0}
						<p class="text-sm text-slate-500">Für diese Aufgabe gibt es keine Hinweise.</p>
					{/if}
					<ol class="space-y-2">
						{#each task.hints ?? [] as hint, i (i)}
							<li
								class="rounded-[var(--radius-ui-sm)] border border-amber-200 bg-amber-50 px-3.5 py-3 text-sm leading-relaxed"
							>
								<span class="mr-1.5 font-mono text-xs font-bold text-amber-800">{i + 1}.</span
								><Markdown source={hint} class="inline" />
							</li>
						{/each}
					</ol>
					{#if canHint}
						<button onclick={requestHint} class="ui-btn mt-3 w-full">
							Hinweis {(task.hints?.length ?? 0) + 1} von {task.hintsTotal} anzeigen (−{task.hintPenaltyPercent}
							%)
						</button>
					{/if}
				</section>

				{#if auth.user && auth.user.role !== 'ADMIN'}
					<HelpButton {task} moduleSlug={moduleSlug ?? ''} taskSlug={taskSlug ?? ''} />
				{/if}

				<section class="ui-card space-y-3 p-5 text-sm" aria-label="Punkte und Versuche">
					<div class="flex justify-between">
						<span class="text-slate-600">Erreichte Punkte</span><span class="font-bold"
							>{task.pointsEarned}</span
						>
					</div>
					{#if task.maxAttempts}
						<div class="flex justify-between">
							<span class="text-slate-600">Versuche übrig</span><span class="font-bold"
								>{task.attemptsLeft} von {task.maxAttempts}</span
							>
						</div>
					{/if}
					{#if (task.firstTryBonusPercent ?? 0) > 0 && task.status === 'OPEN'}
						<p class="text-[13px] text-slate-500">
							Im ersten Versuch ohne Hinweis gibt es {task.firstTryBonusPercent} % Bonus.
						</p>
					{/if}
					{#if task.revealAllowed && !task.solutionRevealed && task.status !== 'SOLVED' && task.status !== 'FAILED'}
						<button onclick={reveal} class="ui-btn ui-btn-danger w-full">
							Lösung aufdecken (0 Punkte)
						</button>
					{/if}
				</section>

				{#if wikiArticle}
					<a
						href={`/knowledge?artikel=${wikiArticle.slug}`}
						class="ui-card flex items-center gap-3 p-4 transition-colors hover:border-slate-400"
					>
						<span
							class="flex h-9 w-9 shrink-0 items-center justify-center rounded-[var(--radius-ui-sm)] bg-sky-100 text-lg"
							>{wikiArticle.icon}</span
						>
						<span class="min-w-0 flex-1">
							<span class="block text-[13px] text-slate-500">Mehr dazu im Wiki</span>
							<span class="block text-[15px] font-semibold text-institutional-navy"
								>{wikiArticle.title}</span
							>
						</span>
						<span class="text-slate-400" aria-hidden="true">→</span>
					</a>
				{:else}
					<a
						href="/knowledge"
						class="block text-sm font-semibold text-institutional-navy underline hover:text-institutional-gold"
						>Zum Wissens-Wiki →</a
					>
				{/if}
			</aside>
		</div>
	{/if}
</div>
