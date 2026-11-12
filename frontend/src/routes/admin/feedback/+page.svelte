<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type CourseSummary, type Schemas } from '$lib/api';
	import FeedbackNav from '$lib/evaluation/FeedbackNav.svelte';
	import { PART_LABELS, VISIBILITY_LABELS, formatDate } from '$lib/evaluation/evaluation';

	type RoundAdmin = Schemas['RoundAdmin'];

	let rounds = $state<RoundAdmin[]>([]);
	let courses = $state<CourseSummary[]>([]);
	let courseId = $state<number | null>(null);
	let loading = $state(true);
	let error = $state('');

	async function load() {
		error = '';
		try {
			rounds = await api<RoundAdmin[]>(
				`/api/admin/evaluations/rounds${courseId ? `?courseId=${courseId}` : ''}`
			);
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	}

	onMount(async () => {
		try {
			courses = await api<CourseSummary[]>('/api/admin/courses');
		} catch (e) {
			error = (e as Error).message;
		}
		await load();
	});

	async function setStatus(round: RoundAdmin, status: 'OPEN' | 'CLOSED') {
		try {
			const updated = await api<RoundAdmin>(
				`/api/admin/evaluations/rounds/${round.id}/status?status=${status}`,
				{ method: 'PUT' }
			);
			rounds = rounds.map((r) => (r.id === updated.id ? updated : r));
		} catch (e) {
			error = (e as Error).message;
		}
	}

	const STATUS: Record<string, { label: string; cls: string }> = {
		DRAFT: { label: 'Entwurf', cls: 'bg-slate-100 text-slate-600' },
		OPEN: { label: 'Offen', cls: 'bg-emerald-100 text-emerald-800' },
		CLOSED: { label: 'Abgeschlossen', cls: 'bg-slate-200 text-slate-600' },
		EXPIRED: { label: 'Frist abgelaufen', cls: 'bg-amber-100 text-amber-800' }
	};

	function status(r: RoundAdmin) {
		return STATUS[r.status === 'OPEN' && !r.acceptingAnswers ? 'EXPIRED' : r.status];
	}

	function parts(r: RoundAdmin): string[] {
		const list: string[] = [];
		if (r.selfCriteria) list.push(PART_LABELS.SELF);
		if (r.lessonCriteria) list.push(PART_LABELS.LESSON + (r.lessonAnonymous ? ' (anonym)' : ''));
		if (r.teacherCriteria || r.teacherUsesSelfCriteria) list.push(PART_LABELS.TEACHER);
		return list;
	}
</script>

<svelte:head><title>Feedback &amp; Bewertung | CodePath</title></svelte:head>

<div class="mx-auto max-w-7xl pb-24">
	<a
		href="/admin"
		class="mb-6 inline-flex font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
		>← Leitstand</a
	>
	<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
		Feedback &amp; Bewertung
	</h1>
	<p class="mt-2 mb-8 max-w-3xl text-sm text-slate-500">
		Bewertungsrunden je Kurs: Die Klasse schätzt sich selbst ein und bewertet den Unterricht, du
		bewertest jede Schülerin und jeden Schüler. Kriterien, Anonymität und ob deine Bewertung
		sichtbar ist, legst du je Runde fest.
	</p>
	<FeedbackNav />

	<div class="mb-6 flex flex-wrap items-center justify-between gap-4">
		<label
			class="flex items-center gap-3 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
		>
			Kurs
			<select
				bind:value={courseId}
				onchange={load}
				class="border-slate-200 bg-white py-2 font-sans text-sm text-institutional-navy normal-case"
			>
				<option value={null}>Alle Kurse</option>
				{#each courses as course (course.id)}
					<option value={course.id}
						>{course.name}{course.status === 'ENDED' ? ' (beendet)' : ''}</option
					>
				{/each}
			</select>
		</label>
		<a
			href="/admin/feedback/rounds/new"
			class="bg-institutional-navy px-6 py-3 font-mono text-xs font-black tracking-widest text-white uppercase"
			>+ Neue Runde</a
		>
	</div>

	{#if error}
		<p class="mb-6 border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
			{error}
		</p>
	{/if}

	{#if loading}
		<p class="py-12 text-center text-slate-400">Lädt …</p>
	{:else if rounds.length === 0}
		<div class="border border-dashed border-slate-200 bg-slate-50 p-16 text-center">
			<p class="text-slate-500">Noch keine Bewertungsrunde.</p>
			<p class="mt-2 text-sm text-slate-400">
				Lege eine Runde an und starte mit den mitgelieferten Vorlagen – oder baue unter
				<a class="underline" href="/admin/feedback/templates">Vorlagen</a> eigene Bögen.
			</p>
		</div>
	{:else}
		<ul class="flex flex-col gap-3">
			{#each rounds as round (round.id)}
				{@const s = status(round)}
				<li class="flex flex-wrap items-center gap-4 border border-slate-200 bg-white p-5">
					<div class="min-w-0 flex-1">
						<a
							href="/admin/feedback/rounds/{round.id}"
							class="font-bold text-institutional-navy hover:underline">{round.title}</a
						>
						<p class="mt-1 text-xs text-slate-500">
							{round.courseName} · {parts(round).join(' · ')}
						</p>
						<p class="mt-1 font-mono text-[10px] text-slate-400">
							Selbst {round.counts.self}/{round.counts.students} · Unterricht {round.counts
								.lesson}/{round.counts.students} · Bewertet {round.counts.teacher}/{round.counts
								.students}
							{#if round.teacherCriteria || round.teacherUsesSelfCriteria}
								· Lehrerbewertung: {VISIBILITY_LABELS[round.teacherVisibility].title}
							{/if}
							{#if round.closesAt}· Frist {formatDate(round.closesAt)}{/if}
						</p>
					</div>
					<span class="px-2 py-1 font-mono text-[9px] font-bold uppercase {s.cls}">{s.label}</span>
					{#if round.status !== 'OPEN'}
						<button class="ed-button" onclick={() => setStatus(round, 'OPEN')}>Öffnen</button>
					{:else}
						<button class="ed-button" onclick={() => setStatus(round, 'CLOSED')}>Schließen</button>
					{/if}
					<a class="ed-button" href="/admin/feedback/rounds/{round.id}">Verwalten</a>
				</li>
			{/each}
		</ul>
	{/if}
</div>
