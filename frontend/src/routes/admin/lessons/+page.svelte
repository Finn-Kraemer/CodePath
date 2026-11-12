<script lang="ts">
	import { goto } from '$app/navigation';
	import { api, type Schemas } from '$lib/api';
	import CourseSelect from '$lib/components/CourseSelect.svelte';
	import { courseSelection } from '$lib/courses.svelte';

	type LessonSummary = Schemas['LessonSummary'];
	type LessonDetail = Schemas['LessonDetail'];

	let lessons = $state<LessonSummary[]>([]);
	let error = $state('');

	const STATUS: Record<string, string> = {
		DRAFT: 'geplant',
		RUNNING: 'läuft',
		DONE: 'gehalten'
	};

	async function load() {
		error = '';
		try {
			lessons = await api<LessonSummary[]>(`/api/admin/lessons${courseSelection.query()}`);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function create() {
		try {
			const created = await api<LessonDetail>('/api/admin/lessons', {
				method: 'POST',
				body: { title: 'Neue Stunde', courseId: courseSelection.selectedId, steps: [] }
			});
			goto(`/admin/lessons/${created.id}`);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function duplicate(lesson: LessonSummary) {
		await api(`/api/admin/lessons/${lesson.id}/copy`, { method: 'POST' });
		await load();
	}

	async function remove(lesson: LessonSummary) {
		if (!confirm(`Ablauf „${lesson.title}“ löschen?`)) return;
		await api(`/api/admin/lessons/${lesson.id}`, { method: 'DELETE' });
		await load();
	}

	async function move(index: number, delta: number) {
		const ids = lessons.map((l) => l.id);
		const target = index + delta;
		if (target < 0 || target >= ids.length) return;
		[ids[index], ids[target]] = [ids[target], ids[index]];
		await api('/api/admin/lessons/order', { method: 'PUT', body: ids });
		await load();
	}

	async function start(lesson: LessonSummary) {
		error = '';
		try {
			await api(`/api/admin/lessons/${lesson.id}/start`, { method: 'POST' });
			goto(`/admin/lessons/${lesson.id}/run`);
		} catch (e) {
			error = (e as Error).message;
		}
	}
</script>

<svelte:head><title>Stundenabläufe | CodePath</title></svelte:head>

<div class="flex flex-col gap-8 pb-24">
	<header class="border-b border-slate-200 pb-8">
		<a
			href="/admin"
			class="mb-6 inline-block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
			>← Zurück zur Administration</a
		>
		<div class="flex flex-wrap items-end justify-between gap-4">
			<div>
				<h1
					class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase"
				>
					Stundenabläufe
				</h1>
				<p class="mt-4 max-w-2xl text-slate-500">
					Eine Stunde vorab als Folge von Schritten planen – Freigaben, Quiz, Aufgabenblatt,
					Bewertungsrunde, Live-Mitteilung, Notizen – und in der Stunde mit einem Klick starten und
					weiterschalten.
				</p>
			</div>
			<CourseSelect onchange={load} />
		</div>
	</header>

	{#if error}<div class="border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
			{error}
		</div>{/if}

	<div>
		<button
			onclick={create}
			class="bg-institutional-navy px-6 py-3 text-xs font-black tracking-[3px] text-white uppercase"
			data-testid="lesson-create">Neuer Ablauf</button
		>
	</div>

	<ul class="divide-y divide-slate-100 border border-slate-200 bg-white" data-testid="lesson-list">
		{#each lessons as lesson, i (lesson.id)}
			<li class="flex flex-wrap items-center gap-4 px-6 py-4">
				<div class="flex flex-col">
					<button
						class="text-xs text-slate-400 hover:text-institutional-navy disabled:opacity-30"
						disabled={i === 0}
						onclick={() => move(i, -1)}
						aria-label="nach oben">▲</button
					>
					<button
						class="text-xs text-slate-400 hover:text-institutional-navy disabled:opacity-30"
						disabled={i === lessons.length - 1}
						onclick={() => move(i, 1)}
						aria-label="nach unten">▼</button
					>
				</div>
				<a href="/admin/lessons/{lesson.id}" class="min-w-48 flex-1">
					<span class="font-bold text-institutional-navy">{lesson.title}</span>
					<span class="block text-xs text-slate-500">
						{lesson.courseName ?? 'Vorlage (ohne Kurs)'}{lesson.date
							? ` · ${new Date(lesson.date + 'T00:00').toLocaleDateString('de-DE')}`
							: ''} · {lesson.steps}
						{lesson.steps === 1 ? 'Schritt' : 'Schritte'}
					</span>
				</a>
				<span
					class="font-mono text-[10px] tracking-widest uppercase {lesson.status === 'RUNNING'
						? 'text-emerald-700'
						: 'text-slate-400'}">{STATUS[lesson.status]}</span
				>
				{#if lesson.status === 'RUNNING'}
					<a
						href="/admin/lessons/{lesson.id}/run"
						class="bg-emerald-700 px-4 py-2 text-xs font-black tracking-widest text-white uppercase"
						>Zur laufenden Stunde</a
					>
				{:else}
					<button
						onclick={() => start(lesson)}
						disabled={!lesson.courseId || lesson.steps === 0}
						title={!lesson.courseId ? 'Erst einen Kurs wählen' : ''}
						class="bg-institutional-navy px-4 py-2 text-xs font-black tracking-widest text-white uppercase disabled:opacity-40"
						>Starten</button
					>
				{/if}
				<button class="text-xs underline" onclick={() => duplicate(lesson)}>duplizieren</button>
				<button class="text-xs text-rose-600 underline" onclick={() => remove(lesson)}
					>löschen</button
				>
			</li>
		{:else}
			<li class="px-6 py-4 text-sm text-slate-400">Noch keine Abläufe.</li>
		{/each}
	</ul>
</div>
