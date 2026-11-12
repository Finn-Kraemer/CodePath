<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { api, type CourseSummary, type Schemas } from '$lib/api';
	import FeedbackNav from '$lib/evaluation/FeedbackNav.svelte';
	import CriteriaEditor from '$lib/evaluation/CriteriaEditor.svelte';
	import EvaluationForm from '$lib/evaluation/EvaluationForm.svelte';
	import ResultBars from '$lib/evaluation/ResultBars.svelte';
	import {
		PART_LABELS,
		VISIBILITY_LABELS,
		cloneCriteria,
		download,
		formatAnswer,
		formatDate,
		missingRequired,
		toCsv,
		type Answers,
		type Criterion,
		type EvaluationPart,
		type TeacherVisibility
	} from '$lib/evaluation/evaluation';

	type RoundAdmin = Schemas['RoundAdmin'];
	type Results = Schemas['RoundResults'];
	type StudentEvaluation = Schemas['StudentEvaluation'];
	type Template = Schemas['TemplateView'];
	type PartKey = 'self' | 'lesson' | 'teacher';

	const PART_OF: Record<PartKey, EvaluationPart> = {
		self: 'SELF',
		lesson: 'LESSON',
		teacher: 'TEACHER'
	};

	const id = $derived(page.params.id);
	const isNew = $derived(id === 'new');

	let round = $state<RoundAdmin | null>(null);
	let courses = $state<CourseSummary[]>([]);
	let templates = $state<Template[]>([]);
	let tab = $state<'settings' | 'results' | 'rate'>('settings');
	let error = $state('');
	let message = $state('');
	let busy = $state(false);

	// ------------------------------------------------------------ Einstellungen
	let form = $state({
		courseId: null as number | null,
		title: '',
		intro: '',
		closesAt: '',
		enabled: { self: true, lesson: true, teacher: true } as Record<PartKey, boolean>,
		criteria: { self: [], lesson: [], teacher: [] } as Record<PartKey, Criterion[]>,
		teacherUsesSelfCriteria: false,
		lessonAnonymous: true,
		teacherVisibility: 'AFTER_SELF' as TeacherVisibility,
		allowEdit: true
	});
	let chosenTemplate = $state<Record<PartKey, number | null>>({
		self: null,
		lesson: null,
		teacher: null
	});

	function fromRound(r: RoundAdmin) {
		form.courseId = r.courseId;
		form.title = r.title;
		form.intro = r.intro ?? '';
		form.closesAt = r.closesAt ? r.closesAt.slice(0, 16) : '';
		form.enabled = {
			self: !!r.selfCriteria,
			lesson: !!r.lessonCriteria,
			teacher: !!r.teacherCriteria || r.teacherUsesSelfCriteria
		};
		form.criteria = {
			self: cloneCriteria(r.selfCriteria as Criterion[]),
			lesson: cloneCriteria(r.lessonCriteria as Criterion[]),
			teacher: cloneCriteria(r.teacherCriteria as Criterion[])
		};
		form.teacherUsesSelfCriteria = r.teacherUsesSelfCriteria;
		form.lessonAnonymous = r.lessonAnonymous;
		form.teacherVisibility = r.teacherVisibility;
		form.allowEdit = r.allowEdit;
	}

	function templatesFor(part: PartKey) {
		const kind = PART_OF[part];
		return [...templates].sort((a, b) => Number(b.kind === kind) - Number(a.kind === kind));
	}

	function applyTemplate(part: PartKey) {
		const t = templates.find((x) => x.id === chosenTemplate[part]);
		if (!t) return;
		if (
			form.criteria[part].length &&
			!confirm(
				`Die Kriterien von „${PART_LABELS[PART_OF[part]]}“ durch die Vorlage „${t.name}“ ersetzen?`
			)
		)
			return;
		form.criteria[part] = cloneCriteria(t.criteria as Criterion[]);
	}

	onMount(async () => {
		try {
			[courses, templates] = await Promise.all([
				api<CourseSummary[]>('/api/admin/courses'),
				api<Template[]>('/api/admin/evaluations/templates')
			]);
			if (isNew) {
				form.courseId = courses.find((c) => c.status === 'ACTIVE')?.id ?? null;
				form.title = `Feedback ${new Date().toLocaleDateString('de-DE', { month: 'long', year: 'numeric' })}`;
				for (const part of ['self', 'lesson', 'teacher'] as PartKey[]) {
					const t = templates.find((x) => x.kind === PART_OF[part]);
					if (t) {
						form.criteria[part] = cloneCriteria(t.criteria as Criterion[]);
						chosenTemplate[part] = t.id;
					}
				}
			} else {
				round = await api<RoundAdmin>(`/api/admin/evaluations/rounds/${id}`);
				fromRound(round);
				const wanted = page.url.searchParams.get('tab');
				if (wanted === 'results' || wanted === 'rate') tab = wanted;
				if (tab !== 'settings') await loadResults();
			}
		} catch (e) {
			error = (e as Error).message;
		}
	});

	function request() {
		const usesSelf = form.enabled.teacher && form.enabled.self && form.teacherUsesSelfCriteria;
		return {
			courseId: form.courseId,
			title: form.title,
			intro: form.intro,
			closesAt: form.closesAt ? `${form.closesAt}:00` : null,
			selfCriteria: form.enabled.self ? form.criteria.self : null,
			lessonCriteria: form.enabled.lesson ? form.criteria.lesson : null,
			teacherCriteria: form.enabled.teacher && !usesSelf ? form.criteria.teacher : null,
			teacherUsesSelfCriteria: usesSelf,
			lessonAnonymous: form.lessonAnonymous,
			teacherVisibility: form.teacherVisibility,
			allowEdit: form.allowEdit
		};
	}

	async function save() {
		busy = true;
		error = '';
		message = '';
		try {
			if (isNew) {
				const created = await api<RoundAdmin>('/api/admin/evaluations/rounds', {
					method: 'POST',
					body: request()
				});
				await goto(`/admin/feedback/rounds/${created.id}`, { replaceState: true });
				round = created;
				fromRound(created);
				message = 'Runde angelegt. Öffne sie, sobald die Klasse ausfüllen soll.';
			} else {
				round = await api<RoundAdmin>(`/api/admin/evaluations/rounds/${id}`, {
					method: 'PUT',
					body: request()
				});
				fromRound(round);
				message = 'Gespeichert.';
			}
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	async function setStatus(status: 'DRAFT' | 'OPEN' | 'CLOSED') {
		error = '';
		try {
			round = await api<RoundAdmin>(`/api/admin/evaluations/rounds/${id}/status?status=${status}`, {
				method: 'PUT'
			});
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function remove() {
		if (!round) return;
		const answers = round.counts.self + round.counts.lesson + round.counts.teacher;
		if (
			!confirm(
				`Runde „${round.title}“ endgültig löschen?${answers ? ` Dabei werden ${answers} ausgefüllte Bögen gelöscht.` : ''}`
			)
		)
			return;
		try {
			await api(`/api/admin/evaluations/rounds/${id}`, { method: 'DELETE' });
			await goto('/admin/feedback');
		} catch (e) {
			error = (e as Error).message;
		}
	}

	// ------------------------------------------------------------ Auswertung
	let results = $state<Results | null>(null);

	async function loadResults() {
		try {
			results = await api<Results>(`/api/admin/evaluations/rounds/${id}/results`);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function openTab(next: typeof tab) {
		tab = next;
		message = '';
		if (next !== 'settings') await loadResults();
		if (next === 'rate' && !current && results?.students.length) {
			await selectStudent(results.students[0].userId);
		}
	}

	const teacherCriteria = $derived(
		(round?.teacherUsesSelfCriteria ? round.selfCriteria : round?.teacherCriteria) as
			| Criterion[]
			| null
			| undefined
	);

	function exportStudents() {
		if (!results || !round) return;
		const self = (round.selfCriteria ?? []).filter((c) => c.type !== 'HEADING') as Criterion[];
		const teacher = (teacherCriteria ?? []).filter((c) => c.type !== 'HEADING');
		const lesson = round.lessonAnonymous
			? []
			: ((round.lessonCriteria ?? []).filter((c) => c.type !== 'HEADING') as Criterion[]);
		const header = [
			'Name',
			'Benutzername',
			...self.map((c) => `Selbst: ${c.label}`),
			...teacher.map((c) => `Lehrkraft: ${c.label}`),
			...lesson.map((c) => `Unterricht: ${c.label}`),
			'Selbst %',
			'Lehrkraft %'
		];
		const rows = results.students.map((s) => [
			s.displayName,
			s.username,
			...self.map((c) => (s.selfAnswers ? formatAnswer(c, s.selfAnswers[c.id]) : '')),
			...teacher.map((c) => (s.teacherAnswers ? formatAnswer(c, s.teacherAnswers[c.id]) : '')),
			...lesson.map((c) => (s.lessonAnswers ? formatAnswer(c, s.lessonAnswers[c.id]) : '')),
			s.selfScore == null ? '' : String(s.selfScore),
			s.teacherScore == null ? '' : String(s.teacherScore)
		]);
		download(`${slug(round.title)}-personen.csv`, toCsv(header, rows));
	}

	function exportLesson() {
		if (!results || !round?.lessonCriteria) return;
		const lesson = (round.lessonCriteria as Criterion[]).filter((c) => c.type !== 'HEADING');
		const rows = results.lessonAnswers.map((a) => lesson.map((c) => formatAnswer(c, a[c.id])));
		download(
			`${slug(round.title)}-unterricht.csv`,
			toCsv(
				lesson.map((c) => c.label),
				rows
			)
		);
	}

	function slug(text: string) {
		return (
			text
				.toLowerCase()
				.replace(/[^a-z0-9äöüß]+/g, '-')
				.replace(/^-|-$/g, '') || 'feedback'
		);
	}

	// ------------------------------------------------------------ Schüler bewerten
	let current = $state<StudentEvaluation | null>(null);
	let teacherAnswers = $state<Answers>({});
	let missing = $state<string[]>([]);
	let showLesson = $state(false);

	async function selectStudent(userId: number) {
		error = '';
		message = '';
		missing = [];
		try {
			current = await api<StudentEvaluation>(
				`/api/admin/evaluations/rounds/${id}/students/${userId}`
			);
			teacherAnswers = { ...(current.teacherAnswers ?? {}) };
		} catch (e) {
			error = (e as Error).message;
		}
	}

	const studentIndex = $derived(
		results && current ? results.students.findIndex((s) => s.userId === current?.userId) : -1
	);

	async function saveRating(next: boolean) {
		if (!current) return;
		missing = missingRequired((current.teacherCriteria ?? []) as Criterion[], teacherAnswers).map(
			(c) => c.label
		);
		if (missing.length) return;
		busy = true;
		error = '';
		try {
			current = await api<StudentEvaluation>(
				`/api/admin/evaluations/rounds/${id}/students/${current.userId}`,
				{ method: 'PUT', body: { answers: teacherAnswers } }
			);
			teacherAnswers = { ...(current.teacherAnswers ?? {}) };
			await loadResults();
			message = `Bewertung für ${current.displayName} gespeichert.`;
			if (next && results && studentIndex >= 0 && studentIndex < results.students.length - 1) {
				const saved = message;
				await selectStudent(results.students[studentIndex + 1].userId);
				message = saved;
			}
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	const STATUS_LABEL: Record<string, string> = {
		DRAFT: 'Entwurf – für die Klasse unsichtbar',
		OPEN: 'Offen – die Klasse kann ausfüllen',
		CLOSED: 'Abgeschlossen – nur noch lesen'
	};
</script>

<svelte:head><title>{round?.title ?? 'Neue Bewertungsrunde'} | CodePath</title></svelte:head>

{#snippet partCard(part: PartKey, description: string)}
	<section class="border border-slate-200 bg-white">
		<header class="flex flex-wrap items-center gap-3 border-b border-slate-100 p-4">
			<label class="flex min-w-0 flex-1 items-center gap-3">
				<input type="checkbox" bind:checked={form.enabled[part]} />
				<span>
					<span class="block font-bold text-institutional-navy">{PART_LABELS[PART_OF[part]]}</span>
					<span class="block text-xs text-slate-500">{description}</span>
				</span>
			</label>
			{#if form.enabled[part] && !(part === 'teacher' && form.teacherUsesSelfCriteria && form.enabled.self)}
				<div class="flex items-center gap-2">
					<select
						class="border-slate-200 py-1 text-sm"
						aria-label="Vorlage"
						bind:value={chosenTemplate[part]}
					>
						<option value={null}>Vorlage wählen …</option>
						{#each templatesFor(part) as t (t.id)}
							<option value={t.id}>{t.name} ({PART_LABELS[t.kind]})</option>
						{/each}
					</select>
					<button
						type="button"
						class="ed-button"
						disabled={!chosenTemplate[part]}
						onclick={() => applyTemplate(part)}>Übernehmen</button
					>
				</div>
			{/if}
		</header>
		{#if form.enabled[part]}
			<div class="flex flex-col gap-4 p-4">
				{#if part === 'lesson'}
					<label class="flex items-start gap-2 text-sm">
						<input type="checkbox" bind:checked={form.lessonAnonymous} />
						<span
							><b>Anonym</b> – du siehst die Antworten ohne Namen. Kann nach dem Öffnen nicht mehr sinnvoll
							geändert werden, ohne das Vertrauen der Klasse zu verletzen.</span
						>
					</label>
				{/if}
				{#if part === 'teacher'}
					<fieldset class="flex flex-col gap-2 text-sm">
						<legend class="ed-label mb-1">Kriterien</legend>
						<label class="flex items-center gap-2">
							<input
								type="radio"
								name="teacher-criteria"
								checked={form.teacherUsesSelfCriteria}
								disabled={!form.enabled.self}
								onchange={() => (form.teacherUsesSelfCriteria = true)}
							/>
							Wie die Selbsteinschätzung – direkter Vergleich je Kriterium
						</label>
						<label class="flex items-center gap-2">
							<input
								type="radio"
								name="teacher-criteria"
								checked={!form.teacherUsesSelfCriteria || !form.enabled.self}
								onchange={() => (form.teacherUsesSelfCriteria = false)}
							/>
							Eigene Kriterien (gleiche IDs wie in der Selbsteinschätzung werden verglichen)
						</label>
					</fieldset>
					<fieldset class="flex flex-col gap-2 text-sm">
						<legend class="ed-label mb-1"
							>Sehen die Schülerinnen und Schüler deine Bewertung?</legend
						>
						{#each Object.entries(VISIBILITY_LABELS) as [value, label] (value)}
							<label class="flex items-start gap-2">
								<input
									type="radio"
									name="visibility"
									{value}
									bind:group={form.teacherVisibility}
									class="mt-1"
								/>
								<span><b>{label.title}</b> – {label.text}</span>
							</label>
						{/each}
					</fieldset>
				{/if}
				{#if part === 'teacher' && form.teacherUsesSelfCriteria && form.enabled.self}
					<p class="text-sm text-slate-500">
						Du bewertest nach den {form.criteria.self.filter((c) => c.type !== 'HEADING').length}
						Kriterien der Selbsteinschätzung.
					</p>
				{:else}
					<CriteriaEditor bind:criteria={form.criteria[part]} />
				{/if}
			</div>
		{/if}
	</section>
{/snippet}

<div class="mx-auto max-w-7xl pb-24">
	<a
		href="/admin/feedback"
		class="mb-6 inline-flex font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
		>← Bewertungsrunden</a
	>
	<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
		{isNew ? 'Neue Bewertungsrunde' : (round?.title ?? '…')}
	</h1>
	<div class="mt-2 mb-8">
		<FeedbackNav />
	</div>

	{#if round}
		<div class="mb-6 flex flex-wrap items-center gap-3 border border-slate-200 bg-slate-50 p-4">
			<div class="min-w-0 flex-1">
				<p class="font-bold text-institutional-navy">
					{round.status === 'OPEN' && !round.acceptingAnswers
						? 'Frist abgelaufen – die Klasse kann nichts mehr abgeben'
						: STATUS_LABEL[round.status]}
				</p>
				<p class="font-mono text-[10px] text-slate-500">
					{round.courseName} · Selbst {round.counts.self}/{round.counts.students} · Unterricht {round
						.counts.lesson}/{round.counts.students} · Bewertet {round.counts.teacher}/{round.counts
						.students}
				</p>
			</div>
			{#if round.status !== 'OPEN'}
				<button
					class="ed-button border-emerald-400 text-emerald-800"
					onclick={() => setStatus('OPEN')}>Öffnen</button
				>
			{:else}
				<button class="ed-button" onclick={() => setStatus('CLOSED')}>Schließen</button>
			{/if}
			{#if round.status !== 'DRAFT'}
				<button class="ed-button" onclick={() => setStatus('DRAFT')}>Zurück zum Entwurf</button>
			{/if}
			<button class="ed-button border-rose-300 text-rose-700" onclick={remove}>Löschen</button>
		</div>

		<div class="mb-6 flex flex-wrap gap-1 border-b border-slate-200" role="tablist">
			{#each [['settings', 'Einstellungen'], ['results', 'Auswertung'], ['rate', 'Schüler bewerten']] as [key, label] (key)}
				{#if key !== 'rate' || teacherCriteria}
					<button
						role="tab"
						aria-selected={tab === key}
						class="-mb-px border-b-2 px-4 py-2 text-sm font-bold {tab === key
							? 'border-institutional-navy text-institutional-navy'
							: 'border-transparent text-slate-400 hover:text-institutional-navy'}"
						onclick={() => openTab(key as typeof tab)}>{label}</button
					>
				{/if}
			{/each}
		</div>
	{/if}

	{#if error}
		<p class="mb-6 border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
			{error}
		</p>
	{/if}
	{#if message}
		<p
			class="mb-6 border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800"
			role="status"
		>
			{message}
		</p>
	{/if}

	{#if tab === 'settings'}
		<form
			class="flex flex-col gap-6"
			onsubmit={(e) => {
				e.preventDefault();
				save();
			}}
		>
			<section class="grid grid-cols-1 gap-4 border border-slate-200 bg-white p-6 md:grid-cols-3">
				<label class="ed-label md:col-span-2"
					>Titel
					<input class="ed-input" bind:value={form.title} required maxlength="200" /></label
				>
				<label class="ed-label"
					>Kurs
					<select class="ed-input" bind:value={form.courseId} required>
						{#each courses.filter((c) => c.status === 'ACTIVE' || c.id === form.courseId) as course (course.id)}
							<option value={course.id}>{course.name}</option>
						{/each}
					</select></label
				>
				<label class="ed-label md:col-span-2"
					>Einleitung für die Klasse (optional)
					<textarea class="ed-input min-h-20" bind:value={form.intro} maxlength="5000"
					></textarea></label
				>
				<div class="flex flex-col gap-3">
					<label class="ed-label"
						>Frist (optional)
						<input class="ed-input" type="datetime-local" bind:value={form.closesAt} /></label
					>
					<label class="flex items-start gap-2 text-sm">
						<input type="checkbox" bind:checked={form.allowEdit} class="mt-1" />
						Abgeschickte Bögen dürfen geändert werden, solange die Runde offen ist
					</label>
				</div>
			</section>

			{#if round && round.counts.self + round.counts.lesson + round.counts.teacher > 0}
				<p class="border-l-4 border-amber-500 bg-amber-50 p-3 text-sm text-amber-900">
					Es gibt schon ausgefüllte Bögen. Entfernte Kriterien oder geänderte IDs fehlen danach in
					der Auswertung; neue Pflichtfragen gelten erst beim nächsten Speichern eines Bogens.
				</p>
			{/if}

			{@render partCard('self', 'Die Schülerinnen und Schüler schätzen ihre eigene Arbeit ein.')}
			{@render partCard('lesson', 'Die Schülerinnen und Schüler bewerten den Unterricht.')}
			{@render partCard('teacher', 'Du bewertest jede Schülerin und jeden Schüler.')}

			<div class="flex flex-wrap items-center gap-3">
				<button
					type="submit"
					disabled={busy}
					class="bg-institutional-navy px-8 py-4 font-mono text-xs font-black tracking-widest text-white uppercase disabled:opacity-40"
					>{isNew ? 'Runde anlegen' : 'Speichern'}</button
				>
				{#if isNew}
					<span class="text-sm text-slate-500">Die Runde startet als Entwurf.</span>
				{/if}
			</div>
		</form>
	{:else if tab === 'results' && results && round}
		<div class="mb-6 flex flex-wrap gap-2">
			<button class="ed-button" onclick={exportStudents}>CSV: je Person</button>
			{#if round.lessonCriteria}
				<button class="ed-button" onclick={exportLesson}>CSV: Unterrichtsbewertung</button>
			{/if}
		</div>

		<section class="mb-10">
			<h2
				class="mb-3 font-sans text-sm font-black tracking-[3px] text-institutional-navy uppercase"
			>
				Klasse
			</h2>
			<div class="overflow-x-auto border border-slate-200 bg-white">
				<table class="w-full text-left text-sm">
					<thead class="bg-slate-50 font-mono text-[10px] tracking-widest text-slate-500 uppercase">
						<tr>
							<th class="p-3">Name</th>
							{#if round.selfCriteria}<th class="p-3">Selbst</th>{/if}
							{#if round.lessonCriteria}<th class="p-3">Unterricht</th>{/if}
							{#if teacherCriteria}<th class="p-3">Lehrkraft</th>{/if}
							{#if round.selfCriteria && teacherCriteria}<th class="p-3" title="Mittel der Skalen"
									>Selbst / Lehrkraft</th
								>{/if}
							<th class="p-3"></th>
						</tr>
					</thead>
					<tbody class="divide-y divide-slate-100">
						{#each results.students as s (s.userId)}
							<tr>
								<td class="p-3">
									<span class="font-bold text-institutional-navy">{s.displayName}</span>
									<span class="block font-mono text-[10px] text-slate-400">@{s.username}</span>
								</td>
								{#if round.selfCriteria}
									<td class="p-3"
										>{s.selfSubmittedAt ? `✓ ${formatDate(s.selfSubmittedAt)}` : '–'}</td
									>
								{/if}
								{#if round.lessonCriteria}<td class="p-3">{s.lessonSubmitted ? '✓' : '–'}</td>{/if}
								{#if teacherCriteria}
									<td class="p-3">{s.teacherRatedAt ? `✓ ${formatDate(s.teacherRatedAt)}` : '–'}</td
									>
								{/if}
								{#if round.selfCriteria && teacherCriteria}
									<td class="p-3 font-mono">
										{s.selfScore ?? '–'} % / {s.teacherScore ?? '–'} %
										{#if s.selfScore != null && s.teacherScore != null && Math.abs(s.selfScore - s.teacherScore) >= 25}
											<span
												class="ml-1 bg-amber-100 px-1 text-[10px] text-amber-800"
												title="Selbst- und Fremdbild liegen weit auseinander">Abweichung</span
											>
										{/if}
									</td>
								{/if}
								<td class="p-3 text-right">
									{#if teacherCriteria}
										<button
											class="ed-button"
											onclick={async () => {
												await openTab('rate');
												await selectStudent(s.userId);
											}}>{s.teacherRatedAt ? 'Ansehen' : 'Bewerten'}</button
										>
									{/if}
								</td>
							</tr>
						{:else}
							<tr
								><td class="p-6 text-center text-slate-400" colspan="6"
									>Der Kurs hat noch keine Mitglieder.</td
								></tr
							>
						{/each}
					</tbody>
				</table>
			</div>
		</section>

		{#each [['self', results.self], ['lesson', results.lesson], ['teacher', results.teacher]] as const as [key, stats] (key)}
			{#if stats}
				<section class="mb-10">
					<h2
						class="mb-1 font-sans text-sm font-black tracking-[3px] text-institutional-navy uppercase"
					>
						{PART_LABELS[PART_OF[key]]}
					</h2>
					<p class="mb-3 text-xs text-slate-500">
						{stats.responses} ausgefüllt{key === 'lesson' && round.lessonAnonymous
							? ' · anonym'
							: ''}
					</p>
					<div class="grid grid-cols-1 gap-3 lg:grid-cols-2">
						{#each stats.criteria as c (c.id)}
							{#if c.type === 'HEADING'}
								<h3
									class="pt-2 font-sans text-xs font-black tracking-[2px] text-slate-500 uppercase lg:col-span-2"
								>
									{c.label}
								</h3>
							{:else}
								<ResultBars stats={c} />
							{/if}
						{/each}
					</div>
				</section>
			{/if}
		{/each}
	{:else if tab === 'rate' && results && round}
		<div class="grid grid-cols-1 gap-6 lg:grid-cols-[16rem_1fr]">
			<aside>
				<p class="mb-2 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
					{results.students.filter((s) => s.teacherRatedAt).length} von {results.students.length} bewertet
				</p>
				<ul class="flex max-h-[70vh] flex-col gap-1 overflow-y-auto">
					{#each results.students as s (s.userId)}
						<li>
							<button
								class="flex w-full items-center gap-2 border px-3 py-2 text-left text-sm {current?.userId ===
								s.userId
									? 'border-institutional-navy bg-white font-bold'
									: 'border-slate-200 bg-white'}"
								onclick={() => selectStudent(s.userId)}
							>
								<span class={s.teacherRatedAt ? 'text-emerald-600' : 'text-slate-300'}
									>{s.teacherRatedAt ? '✓' : '○'}</span
								>
								<span class="min-w-0 flex-1 truncate">{s.displayName}</span>
								{#if s.selfSubmittedAt}<span
										class="font-mono text-[9px] text-slate-400"
										title="Selbsteinschätzung abgegeben">S</span
									>{/if}
							</button>
						</li>
					{:else}
						<li class="text-sm text-slate-400">Der Kurs hat noch keine Mitglieder.</li>
					{/each}
				</ul>
			</aside>

			{#if current}
				<section>
					<div class="mb-4 flex flex-wrap items-center gap-3 border-b border-slate-200 pb-4">
						<div class="min-w-0 flex-1">
							<h2 class="text-2xl font-black text-institutional-navy">{current.displayName}</h2>
							<p class="font-mono text-[10px] text-slate-400">
								@{current.username}
								{#if current.teacherRatedAt}· zuletzt bewertet {formatDate(
										current.teacherRatedAt
									)}{current.teacherRatedBy ? ` von ${current.teacherRatedBy}` : ''}{/if}
							</p>
						</div>
						<button
							class="ed-button"
							disabled={studentIndex <= 0}
							onclick={() => selectStudent(results!.students[studentIndex - 1].userId)}
							>← Zurück</button
						>
						<button
							class="ed-button"
							disabled={studentIndex < 0 || studentIndex >= results.students.length - 1}
							onclick={() => selectStudent(results!.students[studentIndex + 1].userId)}
							>Weiter →</button
						>
					</div>

					<p class="mb-4 border-l-4 border-slate-300 bg-slate-50 p-3 text-xs text-slate-600">
						Sichtbarkeit für die Person: <b>{VISIBILITY_LABELS[round.teacherVisibility].title}</b> –
						{VISIBILITY_LABELS[round.teacherVisibility].text}
						Ändern unter Einstellungen.
					</p>

					{#if round.selfCriteria}
						{#if current.selfAnswers}
							<p class="mb-3 text-sm text-slate-500">
								Selbsteinschätzung vom {formatDate(current.selfSubmittedAt)} steht jeweils unter deiner
								Bewertung.
							</p>
						{:else}
							<p class="mb-3 text-sm text-slate-500">Noch keine Selbsteinschätzung abgegeben.</p>
						{/if}
					{/if}

					<EvaluationForm
						criteria={(current.teacherCriteria ?? []) as Criterion[]}
						bind:answers={teacherAnswers}
						compare={current.selfAnswers ?? null}
						compareLabel="Selbsteinschätzung"
					/>

					{#if current.selfAnswers && current.selfCriteria}
						{@const others = (current.selfCriteria as Criterion[]).filter(
							(c) =>
								c.type !== 'HEADING' && !(current?.teacherCriteria ?? []).some((t) => t.id === c.id)
						)}
						{#if others.length}
							<details class="mt-4 border border-slate-200 bg-white p-4">
								<summary class="cursor-pointer text-sm font-bold text-institutional-navy"
									>Weitere Antworten der Selbsteinschätzung</summary
								>
								<div class="mt-3">
									<EvaluationForm criteria={others} answers={current.selfAnswers} readonly />
								</div>
							</details>
						{/if}
					{/if}

					{#if current.lessonAnswers && current.lessonCriteria}
						<div class="mt-4 border border-slate-200 bg-white p-4">
							<button
								class="text-sm font-bold text-institutional-navy"
								onclick={() => (showLesson = !showLesson)}
								aria-expanded={showLesson}
								>{showLesson ? '▾' : '▸'} Unterrichtsbewertung dieser Person</button
							>
							{#if showLesson}
								<div class="mt-3">
									<EvaluationForm
										criteria={current.lessonCriteria as Criterion[]}
										answers={current.lessonAnswers}
										readonly
									/>
								</div>
							{/if}
						</div>
					{/if}

					{#if missing.length}
						<p
							class="mt-4 border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900"
							role="alert"
						>
							Bitte noch ausfüllen: {missing.join(' · ')}
						</p>
					{/if}

					<div class="mt-6 flex flex-wrap gap-3">
						<button
							class="bg-institutional-navy px-6 py-3 font-mono text-xs font-black tracking-widest text-white uppercase disabled:opacity-40"
							disabled={busy}
							onclick={() => saveRating(true)}>Speichern &amp; weiter</button
						>
						<button class="ed-button" disabled={busy} onclick={() => saveRating(false)}
							>Speichern</button
						>
					</div>
				</section>
			{:else}
				<p
					class="border border-dashed border-slate-200 bg-slate-50 p-16 text-center text-slate-400"
				>
					Links eine Person auswählen.
				</p>
			{/if}
		</div>
	{/if}
</div>
