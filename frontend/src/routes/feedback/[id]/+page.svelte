<script lang="ts">
	import { page } from '$app/state';
	import { api, type Schemas } from '$lib/api';
	import EvaluationForm from '$lib/evaluation/EvaluationForm.svelte';
	import {
		formatDate,
		missingRequired,
		type Answers,
		type Criterion
	} from '$lib/evaluation/evaluation';

	type Detail = Schemas['MyRoundDetail'];
	type Part = 'self' | 'lesson';

	const id = $derived(page.params.id);
	let round = $state<Detail | null>(null);
	let answers = $state<Record<Part, Answers>>({ self: {}, lesson: {} });
	let editing = $state<Record<Part, boolean>>({ self: false, lesson: false });
	let tab = $state<Part | 'teacher'>('self');
	let error = $state('');
	let saved = $state('');
	let busy = $state(false);
	let missing = $state<string[]>([]);

	const TABS: { key: Part | 'teacher'; label: string }[] = [
		{ key: 'self', label: 'Selbsteinschätzung' },
		{ key: 'lesson', label: 'Unterricht' },
		{ key: 'teacher', label: 'Rückmeldung der Lehrkraft' }
	];

	const tabs = $derived(
		TABS.filter((t) =>
			t.key === 'teacher' ? round?.teacherState && round.teacherState !== 'NONE' : !!round?.[t.key]
		)
	);

	function apply(detail: Detail) {
		round = detail;
		for (const part of ['self', 'lesson'] as Part[]) {
			answers[part] = { ...(detail[part]?.answers ?? {}) };
			editing[part] = !!detail[part]?.canEdit && !detail[part]?.submittedAt;
		}
	}

	async function load() {
		error = '';
		try {
			const detail = await api<Detail>(`/api/evaluations/${id}`);
			apply(detail);
			tab = (tabs[0]?.key as Part | 'teacher') ?? 'self';
		} catch (e) {
			error = (e as Error).message;
		}
	}

	$effect(() => {
		void id;
		load();
	});

	async function submit(part: Part) {
		const criteria = (round?.[part]?.criteria ?? []) as Criterion[];
		missing = missingRequired(criteria, answers[part]).map((c) => c.label);
		if (missing.length) return;
		const first = !round?.[part]?.submittedAt;
		if (
			first &&
			!round?.allowEdit &&
			!confirm('Nach dem Absenden kannst du nichts mehr ändern. Abschicken?')
		)
			return;
		busy = true;
		error = '';
		saved = '';
		try {
			apply(
				await api<Detail>(`/api/evaluations/${id}/${part}`, {
					method: 'PUT',
					body: { answers: answers[part] }
				})
			);
			saved = 'Gespeichert – danke!';
			// Nach der Selbsteinschätzung kann die Rückmeldung der Lehrkraft freigeschaltet sein
			if (part === 'self' && round?.teacherState === 'VISIBLE')
				saved += ' Die Rückmeldung der Lehrkraft ist jetzt sichtbar.';
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	const teacherMessages: Record<string, string> = {
		HIDDEN: 'Die Lehrkraft hat ihre Bewertung (noch) nicht für dich freigegeben.',
		AFTER_SELF:
			'Die Rückmeldung der Lehrkraft siehst du, sobald du deine Selbsteinschätzung abgeschickt hast.',
		PENDING: 'Die Lehrkraft hat dich noch nicht bewertet.'
	};
</script>

<svelte:head><title>{round?.title ?? 'Bewertungsbogen'} | CodePath</title></svelte:head>

<div class="mx-auto max-w-3xl py-12">
	<a
		href="/feedback"
		class="font-mono text-[11px] font-bold tracking-wider text-slate-400 uppercase hover:text-institutional-navy"
		>← Feedback</a
	>

	{#if error && !round}
		<p class="mt-6 border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
			{error}
		</p>
	{:else if round}
		<header class="mt-4 mb-8 border-b border-slate-200 pb-6">
			<h1 class="font-sans text-3xl font-extrabold tracking-tight text-institutional-navy">
				{round.title}
			</h1>
			<p class="mt-2 font-mono text-[11px] tracking-wider text-slate-400 uppercase">
				{#if round.acceptingAnswers}
					Offen{round.closesAt ? ` bis ${formatDate(round.closesAt)}` : ''}
				{:else}
					Abgeschlossen – nur noch lesen
				{/if}
				{#if round.lesson && round.lessonAnonymous}
					· Unterrichtsbewertung anonym
				{/if}
			</p>
			<a
				class="ed-button mt-4 inline-block"
				href="/api/evaluations/{round.id}/pdf"
				download
				data-testid="evaluation-pdf">Als PDF herunterladen</a
			>
			{#if round.intro}
				<p class="mt-4 whitespace-pre-line text-slate-600">{round.intro}</p>
			{/if}
		</header>

		{#if tabs.length > 1}
			<div class="mb-6 flex flex-wrap gap-1 border-b border-slate-200" role="tablist">
				{#each tabs as t (t.key)}
					<button
						role="tab"
						aria-selected={tab === t.key}
						class="-mb-px border-b-2 px-4 py-2 text-sm font-bold {tab === t.key
							? 'border-institutional-navy text-institutional-navy'
							: 'border-transparent text-slate-400 hover:text-institutional-navy'}"
						onclick={() => {
							tab = t.key;
							missing = [];
							saved = '';
						}}
					>
						{t.label}
						{#if t.key !== 'teacher' && round[t.key]?.submittedAt}<span class="text-emerald-600"
								>✓</span
							>{/if}
					</button>
				{/each}
			</div>
		{/if}

		{#if error}
			<p class="mb-4 border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
				{error}
			</p>
		{/if}

		{#if tab === 'self' || tab === 'lesson'}
			{@const part = round[tab]}
			{#if part}
				{#if tab === 'lesson' && round.lessonAnonymous}
					<p class="mb-4 border-l-4 border-emerald-500 bg-emerald-50 p-3 text-sm text-emerald-900">
						Anonym: Die Lehrkraft sieht deine Antworten ohne deinen Namen.
					</p>
				{/if}
				{#if part.submittedAt && !editing[tab]}
					<p class="mb-4 text-sm text-slate-500">
						Abgeschickt am {formatDate(part.submittedAt)}.
					</p>
				{/if}
				<EvaluationForm
					criteria={part.criteria as Criterion[]}
					bind:answers={answers[tab]}
					readonly={!editing[tab]}
				/>

				{#if missing.length}
					<p
						class="mt-4 border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900"
						role="alert"
					>
						Bitte noch beantworten: {missing.join(' · ')}
					</p>
				{/if}
				{#if saved}
					<p class="mt-4 text-sm font-bold text-emerald-700" role="status">{saved}</p>
				{/if}

				<div class="mt-6 flex flex-wrap gap-3">
					{#if editing[tab]}
						<button
							class="rounded-[var(--radius-ui-sm)] bg-institutional-navy px-8 py-4 text-sm font-semibold text-white disabled:opacity-40"
							disabled={busy}
							onclick={() => submit(tab as Part)}
						>
							{busy
								? 'Wird gespeichert …'
								: part.submittedAt
									? 'Änderungen speichern'
									: 'Abschicken'}
						</button>
						{#if part.submittedAt}
							<button
								class="ed-button"
								onclick={() => {
									editing[tab as Part] = false;
									answers[tab as Part] = { ...(part.answers ?? {}) };
									missing = [];
								}}>Abbrechen</button
							>
						{/if}
					{:else if part.canEdit}
						<button class="ed-button" onclick={() => (editing[tab as Part] = true)}>Ändern</button>
					{/if}
				</div>
			{/if}
		{:else if tab === 'teacher'}
			{#if round.teacherState === 'VISIBLE' && round.teacher}
				<p class="mb-4 text-sm text-slate-500">
					Bewertet am {formatDate(round.teacher.submittedAt)}.
					{#if round.self?.answers}Darunter steht jeweils deine eigene Einschätzung.{/if}
				</p>
				<EvaluationForm
					criteria={round.teacher.criteria as Criterion[]}
					answers={round.teacher.answers ?? {}}
					readonly
					compare={round.self?.answers ?? null}
					compareLabel="Deine Einschätzung"
				/>
			{:else}
				<p
					class="border border-dashed border-slate-200 bg-slate-50 p-10 text-center text-slate-500"
				>
					{teacherMessages[round.teacherState] ?? ''}
				</p>
			{/if}
		{/if}
	{:else}
		<div class="flex justify-center py-24">
			<div
				class="h-12 w-12 animate-spin border-4 border-slate-200 border-t-institutional-navy"
			></div>
		</div>
	{/if}
</div>
