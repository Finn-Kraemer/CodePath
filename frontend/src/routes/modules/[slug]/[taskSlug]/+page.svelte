<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
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
		CORRECT: 'border-green-200 bg-green-50 text-green-800',
		PARTIAL: 'border-amber-200 bg-amber-50 text-amber-800',
		WRONG: 'border-rose-200 bg-rose-50 text-rose-800',
		INVALID: 'border-slate-200 bg-slate-50 text-slate-700',
		MANUAL: 'border-blue-200 bg-blue-50 text-blue-800',
		UNGRADED: 'border-slate-200 bg-slate-50 text-slate-700'
	};
</script>

<svelte:head><title>{task?.title ?? 'Aufgabe'} | CodePath</title></svelte:head>

<div class="mx-auto max-w-7xl">
	<div class="mb-12 border-b border-slate-200 pb-8">
		<a
			href={`/modules/${moduleSlug}`}
			class="mb-6 inline-flex font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
		>
			← Zurück zu {task?.moduleTitle ?? 'Modul'}
		</a>
		{#if task}
			<div class="flex flex-col items-start justify-between gap-6 md:flex-row md:items-center">
				<h1
					class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase"
				>
					{task.title}
				</h1>
				<div
					class="flex flex-wrap items-center gap-3 font-mono text-[10px] font-black tracking-widest uppercase"
				>
					<span class="border border-slate-200 bg-slate-50 px-3 py-2"
						>{TYPE_LABELS[task.type ?? ''] ?? task.type}</span
					>
					<span class="border border-slate-200 bg-slate-50 px-3 py-2"
						>{DIFFICULTY_LABELS[task.difficulty ?? ''] ?? task.difficulty}</span
					>
					<span class="border border-institutional-navy/10 bg-slate-50 px-3 py-2"
						>{task.points} Punkte</span
					>
					<span
						class="border px-3 py-2 {task.status === 'SOLVED'
							? 'border-green-200 bg-green-50 text-green-700'
							: task.isLocked
								? 'border-red-200 bg-red-50 text-red-700'
								: 'border-slate-200 bg-white text-slate-500'}"
					>
						{task.isLocked && task.status !== 'FAILED'
							? 'Gesperrt'
							: STATUS_LABELS[task.status ?? 'OPEN']}
					</span>
				</div>
			</div>
		{/if}
	</div>

	{#if error}
		<div
			class="mb-8 border border-red-200 bg-red-50 p-6 font-mono text-sm text-red-700"
			role="alert"
		>
			{error}
		</div>
	{/if}

	{#if blockedReason}
		<div
			class="mx-auto max-w-xl border-2 border-amber-300 bg-amber-50/60 p-10 text-center"
			role="alert"
			data-testid="task-blocked"
		>
			<div class="mb-4 text-4xl">🔒</div>
			<h2 class="font-sans text-xl font-extrabold text-institutional-navy uppercase">
				Noch nicht freigeschaltet
			</h2>
			<p class="mt-3 text-sm text-slate-700">{blockedReason}</p>
			<a
				href="/modules"
				class="mt-6 inline-block bg-institutional-navy px-6 py-3 font-mono text-[10px] font-black tracking-[3px] text-white uppercase hover:bg-slate-800"
				>Zum Lernpfad →</a
			>
		</div>
	{:else if !task}
		<div class="flex justify-center py-24">
			<div
				class="h-12 w-12 animate-spin border-4 border-slate-200 border-t-institutional-navy"
			></div>
		</div>
	{:else}
		<div class="grid grid-cols-1 gap-12 lg:grid-cols-3">
			<div class="space-y-8 lg:col-span-2">
				{#if task.learningGoal}
					<div class="border-l-4 border-institutional-gold bg-amber-50/50 p-5 text-sm">
						<span
							class="mr-2 font-mono text-[10px] font-black tracking-widest text-amber-700 uppercase"
							>Lernziel</span
						>{task.learningGoal}
					</div>
				{/if}
				<div class="prose max-w-none border border-slate-200 bg-white p-10 shadow-sm prose-slate">
					{#if task.story}<Markdown source={task.story} class="mb-6 text-slate-600" />{/if}
					{#if task.assignment}
						<p
							class="mb-2 font-sans text-xs font-black tracking-widest text-institutional-navy uppercase"
						>
							Deine Aufgabe
						</p>
						<Markdown source={task.assignment} />
					{/if}
				</div>

				{#if task.reviewComment}
					<div class="border border-blue-200 bg-blue-50 p-6 text-sm text-blue-900">
						<span class="mb-1 block font-mono text-[10px] font-black tracking-widest uppercase"
							>Rückmeldung der Lehrkraft</span
						>
						{task.reviewComment}
					</div>
				{/if}
				{#if !task.isOpen && task.status !== 'SOLVED'}
					<div class="border border-amber-200 bg-amber-50 p-6 text-sm text-amber-900">
						Die Bearbeitungszeit für dieses Modul ist abgelaufen.
					</div>
				{/if}

				{#if result}
					<div
						class="border p-6 text-sm {outcomeStyle[result.outcome ?? 'INVALID']}"
						role="status"
						aria-live="polite"
					>
						<p class="font-bold">{result.feedback}</p>
						{#if result.attemptsLeft !== undefined && result.attemptsLeft !== null && !result.isCorrect && result.outcome !== 'INVALID'}
							<p class="mt-2 font-mono text-xs">Verbleibende Versuche: {result.attemptsLeft}</p>
						{/if}
					</div>
				{/if}

				{#key renderKey}
					<TaskRenderer {task} {disabled} {busy} {result} {submit} />
				{/key}

				{#if task.explanation}
					<div class="border border-green-200 bg-green-50 p-8">
						<p
							class="mb-3 font-mono text-[10px] font-black tracking-widest text-green-700 uppercase"
						>
							Erklärung
						</p>
						<Markdown source={task.explanation} class="text-sm text-green-900" />
					</div>
				{/if}

				{#if next}
					<NextTask {next} compact reload />
				{/if}
			</div>

			<aside class="space-y-8">
				<div class="bg-institutional-navy p-10 text-white shadow-sm">
					<h2
						class="mb-6 font-sans text-[10px] font-black tracking-[4px] text-institutional-gold uppercase"
					>
						Hinweise
					</h2>
					{#if task.hintsTotal === 0}
						<p class="text-sm text-slate-300">Für diese Aufgabe gibt es keine Hinweise.</p>
					{/if}
					<ol class="space-y-4">
						{#each task.hints ?? [] as hint, i (i)}
							<li class="text-sm leading-relaxed text-slate-200">
								<span class="mr-2 font-mono text-institutional-gold">{i + 1}.</span><Markdown
									source={hint}
									class="inline"
								/>
							</li>
						{/each}
					</ol>
					{#if canHint}
						<button
							onclick={requestHint}
							class="mt-6 w-full border border-institutional-gold/30 bg-white/5 py-4 font-mono text-[9px] font-bold tracking-[2px] text-institutional-gold uppercase hover:bg-white/10"
						>
							Hinweis {(task.hints?.length ?? 0) + 1} von {task.hintsTotal} anzeigen (−{task.hintPenaltyPercent}
							%)
						</button>
					{/if}
				</div>

				<div class="space-y-4 border border-slate-200 bg-white p-8 text-sm shadow-sm">
					<div class="flex justify-between">
						<span class="text-slate-500">Erreichte Punkte</span><span class="font-bold"
							>{task.pointsEarned}</span
						>
					</div>
					{#if task.maxAttempts}
						<div class="flex justify-between">
							<span class="text-slate-500">Versuche übrig</span><span class="font-bold"
								>{task.attemptsLeft} von {task.maxAttempts}</span
							>
						</div>
					{/if}
					{#if (task.firstTryBonusPercent ?? 0) > 0 && task.status === 'OPEN'}
						<p class="text-xs text-slate-500">
							Im ersten Versuch ohne Hinweis gibt es {task.firstTryBonusPercent} % Bonus.
						</p>
					{/if}
					{#if task.revealAllowed && !task.solutionRevealed && task.status !== 'SOLVED' && task.status !== 'FAILED'}
						<button
							onclick={reveal}
							class="mt-2 w-full border border-slate-300 py-3 font-mono text-[10px] font-bold tracking-widest text-slate-500 uppercase hover:border-rose-400 hover:text-rose-600"
						>
							Lösung aufdecken (0 Punkte)
						</button>
					{/if}
					{#if wikiArticle}
						<a
							href={`/knowledge?artikel=${wikiArticle.slug}`}
							class="mt-2 flex items-center gap-3 border border-institutional-navy/20 bg-slate-50 p-3 transition-colors hover:border-institutional-navy"
						>
							<span class="text-xl">{wikiArticle.icon}</span>
							<span>
								<span class="block text-[9px] font-bold tracking-widest text-slate-400 uppercase"
									>Mehr dazu im Wiki</span
								>
								<span class="block text-xs font-bold text-institutional-navy"
									>{wikiArticle.title} →</span
								>
							</span>
						</a>
					{:else}
						<a
							href="/knowledge"
							class="block pt-2 text-[10px] font-bold tracking-widest text-institutional-navy uppercase underline"
							>Zum Wissens-Wiki →</a
						>
					{/if}
				</div>
			</aside>
		</div>
	{/if}
</div>
