<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import {
		api,
		type ModuleReleaseView,
		type Schemas,
		type TaskReleaseView,
		type WorksheetSummary
	} from '$lib/api';
	import { courseSelection } from '$lib/courses.svelte';
	import { STEP_TYPES, TYPE_LABEL, emptyStep, type Step, type StepType } from '$lib/lesson/types';

	type LessonDetail = Schemas['LessonDetail'];
	type Candidate = Schemas['Candidate'];
	type RoundAdmin = Schemas['RoundAdmin'];

	const id = page.params.id;

	let lesson = $state<LessonDetail | null>(null);
	let title = $state('');
	let courseId = $state(0);
	let date = $state('');
	let restoreReleases = $state(false);
	let steps = $state<Step[]>([]);
	let modules = $state<ModuleReleaseView[]>([]);
	let tasksByModule = $state<Record<string, TaskReleaseView[]>>({});
	let candidates = $state<Candidate[]>([]);
	let rounds = $state<RoundAdmin[]>([]);
	let worksheets = $state<WorksheetSummary[]>([]);
	let error = $state('');
	let message = $state('');
	let dirty = $state(false);

	async function load() {
		error = '';
		try {
			if (courseSelection.courses.length === 0) await courseSelection.load();
			lesson = await api<LessonDetail>(`/api/admin/lessons/${id}`);
			title = lesson.title;
			courseId = lesson.courseId ?? 0;
			date = lesson.date ?? '';
			restoreReleases = lesson.restoreReleases;
			steps = (lesson.steps as Step[]).map((s) => ({ ...s }));
			const [m, c, w] = await Promise.all([
				api<ModuleReleaseView[]>('/api/admin/modules'),
				api<Candidate[]>('/api/admin/quiz/candidates'),
				api<WorksheetSummary[]>('/api/admin/worksheets')
			]);
			modules = m;
			candidates = c;
			worksheets = w;
			await loadRounds();
			for (const s of steps)
				if (s.type === 'RELEASE' && s.moduleSlug) await loadTasks(s.moduleSlug);
			dirty = false;
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function loadRounds() {
		rounds = courseId
			? await api<RoundAdmin[]>(`/api/admin/evaluations/rounds?courseId=${courseId}`)
			: [];
	}

	async function loadTasks(slug: string) {
		if (!slug || tasksByModule[slug]) return;
		const module = modules.find((m) => m.slug === slug);
		if (!module) return;
		tasksByModule[slug] = await api<TaskReleaseView[]>(`/api/admin/modules/${module.id}/tasks`);
	}

	onMount(load);

	function changed() {
		dirty = true;
		message = '';
	}

	function add(type: StepType) {
		steps.push(emptyStep(type));
		changed();
	}

	function move(i: number, delta: number) {
		const j = i + delta;
		if (j < 0 || j >= steps.length) return;
		[steps[i], steps[j]] = [steps[j], steps[i]];
		changed();
	}

	function remove(i: number) {
		steps.splice(i, 1);
		changed();
	}

	function clean(s: Step): Step {
		const copy: Step = { ...s };
		for (const key of Object.keys(copy) as (keyof Step)[]) {
			if (copy[key] === '' || copy[key] === null) delete copy[key];
		}
		if (
			copy.type === 'RELEASE' &&
			!copy.taskSlug &&
			copy.action &&
			!['UNLOCK', 'LOCK'].includes(copy.action)
		)
			copy.action = 'UNLOCK';
		if (
			copy.type === 'RELEASE' &&
			copy.taskSlug &&
			!['SHOW', 'HIDE', 'MODULE'].includes(copy.action ?? '')
		)
			copy.action = 'SHOW';
		return copy;
	}

	async function save() {
		error = '';
		try {
			lesson = await api<LessonDetail>(`/api/admin/lessons/${id}`, {
				method: 'PUT',
				body: {
					title,
					courseId,
					date: date || null,
					restoreReleases,
					steps: lesson?.status === 'RUNNING' ? null : steps.map(clean)
				}
			});
			steps = (lesson.steps as Step[]).map((s) => ({ ...s }));
			dirty = false;
			message = 'Gespeichert.';
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function startLesson() {
		if (dirty) await save();
		if (error) return;
		try {
			await api(`/api/admin/lessons/${id}/start`, { method: 'POST' });
			goto(`/admin/lessons/${id}/run`);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	function toggleQuestion(step: Step, taskId: number) {
		const ids = step.taskIds ?? [];
		step.taskIds = ids.includes(taskId) ? ids.filter((x) => x !== taskId) : [...ids, taskId];
		changed();
	}
</script>

<svelte:head><title>{title || 'Ablauf'} | Stundenabläufe | CodePath</title></svelte:head>

<div class="flex flex-col gap-6 pb-24">
	<header class="flex flex-wrap items-end justify-between gap-4 border-b border-slate-200 pb-6">
		<div class="flex-1">
			<a
				href="/admin/lessons"
				class="mb-4 inline-block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
				>← Alle Abläufe</a
			>
			<input
				bind:value={title}
				oninput={changed}
				maxlength="200"
				class="w-full border-0 border-b border-slate-200 bg-transparent px-0 font-sans text-3xl font-extrabold text-institutional-navy"
				aria-label="Titel"
				data-testid="lesson-title"
			/>
		</div>
		<div class="flex items-center gap-3">
			<span class="font-mono text-[10px] tracking-widest text-slate-400 uppercase"
				>{dirty ? 'Ungespeichert' : 'Gespeichert'}</span
			>
			<button class="ed-button bg-white" onclick={save} data-testid="lesson-save">Speichern</button>
			{#if lesson?.status === 'RUNNING'}
				<a
					href="/admin/lessons/{id}/run"
					class="bg-emerald-700 px-6 py-3 text-xs font-black tracking-widest text-white uppercase"
					>Zur laufenden Stunde</a
				>
			{:else}
				<button
					onclick={startLesson}
					disabled={!courseId || steps.length === 0}
					class="bg-institutional-navy px-6 py-3 text-xs font-black tracking-widest text-white uppercase disabled:opacity-40"
					data-testid="lesson-start">Stunde starten</button
				>
			{/if}
		</div>
	</header>

	{#if error}<div class="border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
			{error}
		</div>{/if}
	{#if message}<p class="text-xs text-emerald-700" role="status">{message}</p>{/if}

	<section class="flex flex-wrap items-end gap-4 border border-slate-200 bg-white p-6 text-sm">
		<label
			>Kurs<br /><select
				bind:value={courseId}
				onchange={() => {
					changed();
					loadRounds();
				}}
				class="mt-1 border-slate-200 bg-white"
			>
				<option value={0}>Vorlage (ohne Kurs)</option>
				{#each courseSelection.courses.filter((c) => c.status !== 'ENDED') as c (c.id)}
					<option value={c.id}>{c.name}</option>
				{/each}
			</select></label
		>
		<label
			>Datum<br /><input
				type="date"
				bind:value={date}
				oninput={changed}
				class="mt-1 border-slate-200"
			/></label
		>
		<label class="flex items-center gap-2 pb-2"
			><input type="checkbox" bind:checked={restoreReleases} onchange={changed} /> Am Ende alle Freigaben
			zurücksetzen (Stand vom Start)</label
		>
	</section>

	<ol class="space-y-3" data-testid="lesson-steps">
		{#each steps as step, i (step)}
			<li class="border border-slate-200 bg-white p-4 text-sm">
				<div class="mb-3 flex flex-wrap items-center gap-3">
					<span class="font-mono text-xs font-black text-slate-400">{i + 1}</span>
					<span
						class="bg-slate-100 px-2 py-0.5 font-mono text-[10px] font-bold tracking-widest uppercase"
						>{TYPE_LABEL[step.type]}</span
					>
					<input
						bind:value={step.title}
						oninput={changed}
						placeholder="Eigene Beschriftung (optional)"
						maxlength="200"
						class="min-w-48 flex-1 border-slate-200 py-1 text-xs"
					/>
					<label class="text-xs"
						>Minuten <input
							type="number"
							min="0"
							max="240"
							bind:value={step.minutes}
							oninput={changed}
							class="w-16 border-slate-200 py-1 text-xs"
						/></label
					>
					<button onclick={() => move(i, -1)} disabled={i === 0} aria-label="nach oben">▲</button>
					<button
						onclick={() => move(i, 1)}
						disabled={i === steps.length - 1}
						aria-label="nach unten">▼</button
					>
					<button class="text-xs text-rose-600 underline" onclick={() => remove(i)}
						>entfernen</button
					>
				</div>

				{#if step.type === 'RELEASE'}
					<div class="flex flex-wrap items-end gap-3 text-xs">
						<label
							>Modul<br /><select
								bind:value={step.moduleSlug}
								onchange={() => {
									step.taskSlug = '';
									loadTasks(step.moduleSlug ?? '');
									changed();
								}}
								class="border-slate-200 bg-white py-1 text-xs"
							>
								<option value="">– wählen –</option>
								{#each modules as m (m.id)}<option value={m.slug}>{m.title}</option>{/each}
							</select></label
						>
						<label
							>Aufgabe<br /><select
								bind:value={step.taskSlug}
								onchange={() => {
									step.action = step.taskSlug ? 'SHOW' : 'UNLOCK';
									changed();
								}}
								class="max-w-64 border-slate-200 bg-white py-1 text-xs"
							>
								<option value="">Ganzes Modul</option>
								{#each tasksByModule[step.moduleSlug ?? ''] ?? [] as t (t.id)}<option value={t.slug}
										>{t.title}</option
									>{/each}
							</select></label
						>
						<label
							>Aktion<br /><select
								bind:value={step.action}
								onchange={changed}
								class="border-slate-200 bg-white py-1 text-xs"
							>
								{#if step.taskSlug}
									<option value="SHOW">freigeben</option>
									<option value="HIDE">sperren</option>
									<option value="MODULE">wie Modul</option>
								{:else}
									<option value="UNLOCK">freigeben</option>
									<option value="LOCK">sperren</option>
								{/if}
							</select></label
						>
						<label
							>Frist (Uhrzeit, „-“ entfernt)<br /><input
								bind:value={step.deadline}
								oninput={changed}
								placeholder="z. B. 10:30"
								class="w-28 border-slate-200 py-1 text-xs"
							/></label
						>
					</div>
				{:else if step.type === 'QUIZ'}
					<label class="text-xs"
						>Titel <input
							bind:value={step.text}
							oninput={changed}
							class="border-slate-200 py-1 text-xs"
						/></label
					>
					<p class="mt-2 text-xs text-slate-500">{step.taskIds?.length ?? 0} Fragen gewählt:</p>
					<div class="mt-1 max-h-48 overflow-y-auto border border-slate-100 p-2 text-xs">
						{#each candidates as c (c.taskId)}
							<label class="flex items-start gap-2 py-0.5"
								><input
									type="checkbox"
									checked={step.taskIds?.includes(c.taskId)}
									onchange={() => toggleQuestion(step, c.taskId)}
								/>
								<span><span class="text-slate-400">{c.moduleTitle} ›</span> {c.title}</span></label
							>
						{/each}
					</div>
				{:else if step.type === 'WORKSHEET'}
					<select
						bind:value={step.worksheetId}
						onchange={changed}
						class="border-slate-200 bg-white py-1 text-xs"
					>
						<option value={null}>– Aufgabenblatt wählen –</option>
						{#each worksheets as w (w.id)}<option value={w.id}>{w.title}</option>{/each}
					</select>
				{:else if step.type === 'EVALUATION'}
					<div class="flex flex-wrap gap-3 text-xs">
						<select
							bind:value={step.roundId}
							onchange={changed}
							class="border-slate-200 bg-white py-1 text-xs"
						>
							<option value={null}>– Bewertungsrunde wählen –</option>
							{#each rounds as r (r.id)}<option value={r.id}>{r.title}</option>{/each}
						</select>
						<select
							bind:value={step.action}
							onchange={changed}
							class="border-slate-200 bg-white py-1 text-xs"
						>
							<option value="OPEN">öffnen</option>
							<option value="CLOSED">schließen</option>
						</select>
						{#if !courseId}<span class="text-amber-700">Erst einen Kurs wählen.</span>{/if}
					</div>
				{:else if step.type === 'ANNOUNCEMENT'}
					<textarea
						bind:value={step.text}
						oninput={changed}
						rows="2"
						maxlength="2000"
						placeholder="Text der Live-Mitteilung (leer = ausblenden)"
						class="w-full border-slate-200 text-xs"
					></textarea>
					<select
						bind:value={step.displayMode}
						onchange={changed}
						class="mt-1 border-slate-200 bg-white py-1 text-xs"
					>
						<option value="HEADER">Kopfzeile</option>
						<option value="INFO_PAGE">Infoseite</option>
						<option value="BOTH">beides</option>
					</select>
				{:else}
					<textarea
						bind:value={step.text}
						oninput={changed}
						rows="2"
						maxlength="2000"
						placeholder="Hinweis nur für dich"
						class="w-full border-slate-200 text-xs"
					></textarea>
				{/if}
			</li>
		{/each}
	</ol>

	{#if lesson?.status === 'RUNNING'}
		<p class="text-xs text-amber-700">
			Der Ablauf läuft gerade – Schritte lassen sich erst nach dem Beenden ändern.
		</p>
	{:else}
		<div class="flex flex-wrap gap-2" aria-label="Schritt hinzufügen">
			{#each STEP_TYPES as t (t.type)}
				<button
					class="border border-slate-300 bg-white px-3 py-2 text-xs hover:border-institutional-navy"
					title={t.hint}
					onclick={() => add(t.type)}
					data-testid="add-{t.type}">+ {t.label}</button
				>
			{/each}
		</div>
	{/if}
</div>
