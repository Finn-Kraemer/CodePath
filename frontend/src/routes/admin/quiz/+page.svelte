<script lang="ts">
	import { onMount } from 'svelte';
	import {
		api,
		type CourseSummary,
		type QuizCandidate,
		type QuizState,
		type QuizView
	} from '$lib/api';
	import { realtime } from '$lib/realtime.svelte';
	import QuizStage from '$lib/quiz/QuizStage.svelte';

	let candidates = $state<QuizCandidate[]>([]);
	let courses = $state<CourseSummary[]>([]);
	let quiz = $state<QuizView | null>(null);
	/** Ergebnis der zuletzt beendeten Runde (danach gibt es keine laufende Runde mehr) */
	let finished = $state<QuizView | null>(null);
	let selected = $state<number[]>([]);
	let title = $state('Quiz');
	let courseId = $state<number | null>(null);
	let error = $state('');
	let busy = $state(false);

	const modules = $derived(
		Object.entries(
			candidates.reduce<Record<string, QuizCandidate[]>>((groups, c) => {
				(groups[c.moduleTitle] ??= []).push(c);
				return groups;
			}, {})
		)
	);

	async function load() {
		try {
			quiz = (await api<QuizState>('/api/admin/quiz/current')).quiz ?? null;
		} catch (e) {
			error = (e as Error).message;
		}
	}

	onMount(async () => {
		await load();
		try {
			[candidates, courses] = await Promise.all([
				api<QuizCandidate[]>('/api/admin/quiz/candidates'),
				api<CourseSummary[]>('/api/admin/courses')
			]);
			courseId = courses.find((c) => c.status === 'ACTIVE')?.id ?? null;
		} catch (e) {
			error = (e as Error).message;
		}
	});

	$effect(() => {
		if (realtime.quizTrigger > 0) load();
	});

	async function action(path: string, body?: unknown) {
		busy = true;
		error = '';
		try {
			const state = await api<QuizState>(path, { method: 'POST', body });
			if (state.quiz?.phase === 'FINISHED') {
				finished = state.quiz;
				quiz = null;
			} else {
				quiz = state.quiz ?? null;
				finished = null;
			}
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	function toggle(id: number) {
		selected = selected.includes(id) ? selected.filter((s) => s !== id) : [...selected, id];
	}
</script>

<svelte:head><title>Quiz-Runde | CodePath</title></svelte:head>

<div class="mx-auto max-w-5xl">
	<a
		href="/admin"
		class="mb-6 inline-flex font-mono text-[11px] font-bold tracking-wider text-slate-400 uppercase hover:text-institutional-navy"
		>← Leitstand</a
	>
	<h1 class="font-sans text-3xl font-extrabold tracking-tight text-institutional-navy">
		Quiz-Runde
	</h1>
	<p class="mt-2 mb-8 text-sm text-slate-500">
		Live-Runde aus Auswahl-Aufgaben: Die Klasse antwortet am eigenen Gerät, der
		<a href="/projector" target="_blank" rel="noopener" class="underline">Beamer</a> zeigt die anonyme
		Verteilung. Es gibt keine Punkte und keine Versuche.
	</p>

	{#if error}
		<p class="mb-6 border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
			{error}
		</p>
	{/if}

	{#if quiz}
		<div class="border-2 border-institutional-navy bg-white p-8">
			<QuizStage
				{quiz}
				onnext={() => action('/api/admin/quiz/current/next')}
				onreveal={() => action('/api/admin/quiz/current/reveal')}
				onfinish={() => action('/api/admin/quiz/current/finish')}
			/>
			{#if quiz.phase === 'QUESTION' && quiz.distribution}
				<p class="mt-6 font-mono text-xs text-slate-500">
					Nur für dich (nicht am Beamer): {Object.entries(quiz.distribution)
						.map(([id, n]) => `${quiz?.options?.find((o) => o.id === id)?.label ?? id}: ${n}`)
						.join(' · ')}
				</p>
			{/if}
		</div>
	{:else}
		{#if finished}
			<div class="mb-8 rounded-[var(--radius-ui)] border border-slate-200 bg-white p-8">
				<QuizStage quiz={finished} />
			</div>
		{/if}
		<form
			class="space-y-6 rounded-[var(--radius-ui)] border border-slate-200 bg-white p-8"
			onsubmit={(e) => {
				e.preventDefault();
				action('/api/admin/quiz', { title, courseId, taskIds: selected });
			}}
		>
			<div class="grid grid-cols-1 gap-4 md:grid-cols-2">
				<label class="block text-sm"
					>Titel
					<input class="mt-1 w-full border border-slate-300 px-3 py-2" bind:value={title} /></label
				>
				<label class="block text-sm"
					>Für
					<select class="mt-1 w-full border border-slate-300 px-3 py-2" bind:value={courseId}>
						<option value={null}>alle Kurse</option>
						{#each courses.filter((c) => c.status === 'ACTIVE') as course (course.id)}
							<option value={course.id}>{course.name}</option>
						{/each}
					</select></label
				>
			</div>
			<fieldset>
				<legend class="mb-2 text-sm font-bold text-institutional-navy">
					Fragen auswählen ({selected.length} gewählt, Reihenfolge wie angeklickt)
				</legend>
				<div class="max-h-[28rem] space-y-4 overflow-y-auto border border-slate-100 p-4">
					{#each modules as [module, tasks] (module)}
						<div>
							<p
								class="mb-1 font-mono text-[11px] font-bold tracking-wider text-slate-400 uppercase"
							>
								{module}
							</p>
							{#each tasks as task (task.taskId)}
								<label class="flex items-start gap-2 py-1 text-sm">
									<input
										type="checkbox"
										checked={selected.includes(task.taskId)}
										onchange={() => toggle(task.taskId)}
									/>
									<span>
										{#if selected.includes(task.taskId)}<span
												class="mr-1 font-mono text-xs font-bold text-institutional-navy"
												>{selected.indexOf(task.taskId) + 1}.</span
											>{/if}{task.title}
									</span>
								</label>
							{/each}
						</div>
					{/each}
				</div>
			</fieldset>
			<button
				type="submit"
				disabled={busy || !selected.length}
				class="rounded-[var(--radius-ui-sm)] bg-institutional-navy px-8 py-4 text-sm font-semibold text-white disabled:opacity-40"
				>Quiz starten</button
			>
		</form>
	{/if}
</div>
