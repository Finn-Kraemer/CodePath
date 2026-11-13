<script lang="ts">
	import { onDestroy, onMount } from 'svelte';
	import { page } from '$app/state';
	import { api, type Schemas } from '$lib/api';
	import { TYPE_LABEL, clock, remaining, type Step } from '$lib/lesson/types';

	type LessonDetail = Schemas['LessonDetail'];

	const id = page.params.id;

	let lesson = $state<LessonDetail | null>(null);
	let error = $state('');
	let busy = $state(false);
	let now = $state(Date.now());
	const tick = setInterval(() => (now = Date.now()), 1000);
	onDestroy(() => clearInterval(tick));

	const steps = $derived((lesson?.steps ?? []) as Step[]);
	const current = $derived(lesson ? lesson.currentStep : -1);
	const step = $derived(current >= 0 ? steps[current] : null);
	const left = $derived(remaining(lesson?.stepStartedAt, step?.minutes, now));
	const result = $derived(lesson?.log.find((r) => r.index === current));

	async function load() {
		try {
			lesson = await api<LessonDetail>(`/api/admin/lessons/${id}`);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	onMount(load);

	async function act(path: string, body?: unknown) {
		busy = true;
		error = '';
		try {
			lesson = await api<LessonDetail>(`/api/admin/lessons/${id}/${path}`, {
				method: 'POST',
				body
			});
		} catch (e) {
			error = (e as Error).message;
			await load();
		} finally {
			busy = false;
		}
	}

	function next() {
		act('next', { expectedStep: current + 1 });
	}

	function finish() {
		const restore = lesson?.restoreReleases
			? ' Die Freigaben werden auf den Stand vom Start zurückgesetzt.'
			: '';
		if (confirm('Stunde beenden?' + restore)) act('finish');
	}

	function time(value: string) {
		return new Date(value).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
	}
</script>

<svelte:head><title>Stunde läuft | CodePath</title></svelte:head>

<div class="flex flex-col gap-6 pb-24">
	<header class="flex flex-wrap items-end justify-between gap-4 border-b border-slate-200 pb-6">
		<div>
			<a
				href="/admin/lessons/{id}"
				class="mb-4 inline-block font-mono text-[11px] font-bold tracking-wider text-slate-400 uppercase hover:text-institutional-navy"
				>← Ablauf bearbeiten</a
			>
			<h1 class="font-sans text-3xl font-extrabold text-institutional-navy">
				{lesson?.title ?? 'Stunde'}
			</h1>
			<p class="text-sm text-slate-500">
				{lesson?.courseName ?? ''}{lesson?.startedAt
					? ` · gestartet ${time(lesson.startedAt)}`
					: ''}
			</p>
		</div>
		{#if lesson?.status === 'RUNNING'}
			<button
				onclick={finish}
				disabled={busy}
				class="rounded-[var(--radius-ui-sm)] border border-rose-400 px-4 py-2 text-sm text-rose-700"
				data-testid="lesson-finish">Stunde beenden</button
			>
		{/if}
	</header>

	{#if error}<div class="border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
			{error}
		</div>{/if}

	{#if lesson}
		{#if lesson.status === 'RUNNING' && step}
			<section
				class="border-2 border-institutional-navy bg-white p-8"
				data-testid="lesson-current"
				aria-live="polite"
			>
				<p class="font-mono text-xs tracking-wider text-slate-400 uppercase">
					Schritt {current + 1} von {steps.length} · {TYPE_LABEL[step.type]}
				</p>
				<h2 class="mt-2 text-3xl font-extrabold text-institutional-navy">
					{lesson.labels[current]}
				</h2>
				{#if step.text && (step.type === 'NOTE' || step.type === 'ANNOUNCEMENT')}
					<p class="mt-4 text-lg whitespace-pre-line text-slate-700">{step.text}</p>
				{/if}
				{#if step.type === 'WORKSHEET' && step.worksheetId}
					<p class="mt-4 flex gap-3">
						<a
							class="ed-button"
							target="_blank"
							rel="noopener"
							href="/api/admin/worksheets/{step.worksheetId}/pdf?variant=student">Schülerblatt</a
						>
						<a
							class="ed-button"
							target="_blank"
							rel="noopener"
							href="/api/admin/worksheets/{step.worksheetId}/pdf?variant=solution">Lösung</a
						>
					</p>
				{/if}
				{#if step.type === 'QUIZ'}
					<p class="mt-4"><a class="ed-button" href="/admin/quiz">Zur Quiz-Steuerung</a></p>
				{/if}
				{#if result}
					<p
						class="mt-4 text-sm {result.ok ? 'text-emerald-700' : 'font-bold text-rose-700'}"
						role="status"
					>
						{result.ok ? '✓' : '⚠'}
						{result.message ?? (result.ok ? 'Ausgeführt' : 'Fehlgeschlagen')}
					</p>
				{/if}
				{#if left !== null}
					<p
						class="mt-6 font-mono text-6xl font-black {left < 0
							? 'text-rose-600'
							: 'text-institutional-navy'}"
						data-testid="lesson-timer"
					>
						{clock(left)}
					</p>
				{/if}
				<div class="mt-8">
					{#if current + 1 < steps.length}
						<button
							onclick={next}
							disabled={busy}
							class="rounded-[var(--radius-ui-sm)] bg-institutional-navy px-10 py-4 text-sm font-semibold text-white disabled:opacity-50"
							data-testid="lesson-next">Weiter: {lesson.labels[current + 1]}</button
						>
					{:else}
						<button
							onclick={finish}
							disabled={busy}
							class="rounded-[var(--radius-ui-sm)] bg-emerald-700 px-10 py-4 text-sm font-semibold text-white"
							>Letzter Schritt – Stunde beenden</button
						>
					{/if}
				</div>
			</section>
		{:else if lesson.status === 'DONE'}
			<section class="border border-emerald-300 bg-emerald-50 p-6 text-sm text-emerald-900">
				Die Stunde ist beendet{lesson.finishedAt ? ` (${time(lesson.finishedAt)})` : ''}.
				<button class="ml-2 underline" onclick={() => act('reset')}
					>Für die nächste Stunde zurücksetzen</button
				>
			</section>
		{:else}
			<section class="rounded-[var(--radius-ui)] border border-slate-200 bg-white p-6 text-sm">
				Der Ablauf ist noch nicht gestartet.
				<button
					class="ml-2 rounded-[var(--radius-ui-sm)] bg-institutional-navy px-4 py-2 text-sm font-semibold text-white"
					onclick={() => act('start')}
					disabled={busy}>Jetzt starten</button
				>
			</section>
		{/if}

		<section class="rounded-[var(--radius-ui)] border border-slate-200 bg-white p-6">
			<h2 class="mb-3 font-bold text-institutional-navy">Ablauf</h2>
			<ol class="space-y-1 text-sm">
				{#each steps as s, i (i)}
					{@const r = lesson.log.find((x) => x.index === i)}
					<li
						class="flex flex-wrap items-baseline gap-3 {i === current
							? 'font-bold text-institutional-navy'
							: i < current
								? 'text-slate-400'
								: 'text-slate-700'}"
					>
						<span class="w-6 font-mono">{i + 1}</span>
						<span class="flex-1">{lesson.labels[i]}{s.minutes ? ` · ${s.minutes} min` : ''}</span>
						{#if r}<span class="text-xs {r.ok ? 'text-emerald-700' : 'text-rose-700'}"
								>{r.ok ? '✓' : '⚠'} {r.message ?? ''} · {time(r.at)}</span
							>{/if}
					</li>
				{/each}
				{#each lesson.log.filter((r) => r.index < 0) as r (r.at)}
					<li class="text-xs text-emerald-700">✓ {r.label}: {r.message}</li>
				{/each}
			</ol>
		</section>
	{/if}
</div>
