<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type Schemas } from '$lib/api';
	import FeedbackNav from '$lib/evaluation/FeedbackNav.svelte';
	import CriteriaEditor from '$lib/evaluation/CriteriaEditor.svelte';
	import {
		PART_LABELS,
		cloneCriteria,
		newCriterion,
		type Criterion,
		type EvaluationPart
	} from '$lib/evaluation/evaluation';

	type Template = Schemas['TemplateView'];
	type Draft = {
		id: number | null;
		name: string;
		description: string;
		kind: EvaluationPart;
		criteria: Criterion[];
	};

	let templates = $state<Template[]>([]);
	let draft = $state<Draft | null>(null);
	let error = $state('');
	let message = $state('');
	let busy = $state(false);

	const kinds: EvaluationPart[] = ['SELF', 'LESSON', 'TEACHER'];

	async function load() {
		try {
			templates = await api<Template[]>('/api/admin/evaluations/templates');
		} catch (e) {
			error = (e as Error).message;
		}
	}

	onMount(load);

	function edit(t: Template) {
		draft = {
			id: t.id,
			name: t.name,
			description: t.description ?? '',
			kind: t.kind,
			criteria: cloneCriteria(t.criteria as Criterion[])
		};
		message = '';
		error = '';
	}

	function create(kind: EvaluationPart) {
		draft = {
			id: null,
			name: '',
			description: '',
			kind,
			criteria: [newCriterion('SCALE', [])]
		};
		message = '';
		error = '';
	}

	function duplicate(t: Template) {
		edit(t);
		if (draft) {
			draft.id = null;
			draft.name = `${t.name} (Kopie)`;
		}
	}

	async function save() {
		if (!draft) return;
		busy = true;
		error = '';
		message = '';
		try {
			const body = {
				name: draft.name,
				description: draft.description,
				kind: draft.kind,
				criteria: draft.criteria
			};
			const saved = draft.id
				? await api<Template>(`/api/admin/evaluations/templates/${draft.id}`, {
						method: 'PUT',
						body
					})
				: await api<Template>('/api/admin/evaluations/templates', { method: 'POST', body });
			await load();
			edit(saved);
			message = 'Vorlage gespeichert.';
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	async function remove(t: Template) {
		if (!confirm(`Vorlage „${t.name}“ löschen? Laufende Runden bleiben unverändert.`)) return;
		try {
			await api(`/api/admin/evaluations/templates/${t.id}`, { method: 'DELETE' });
			if (draft?.id === t.id) draft = null;
			await load();
		} catch (e) {
			error = (e as Error).message;
		}
	}
</script>

<svelte:head><title>Bewertungsvorlagen | CodePath</title></svelte:head>

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
		Vorlagen sind wiederverwendbare Bögen. Eine Runde kopiert die Kriterien – Änderungen an einer
		Vorlage wirken erst bei neuen Runden. Gleiche Kriterien-IDs in Selbsteinschätzung und
		Lehrerbewertung werden nebeneinander verglichen. Vorlagen lassen sich unter
		<a class="underline" href="/admin/transfer">Import &amp; Export</a> sichern.
	</p>
	<FeedbackNav />

	{#if error}
		<p class="mb-6 border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
			{error}
		</p>
	{/if}

	<div class="grid grid-cols-1 gap-8 lg:grid-cols-[22rem_1fr]">
		<aside class="flex flex-col gap-6">
			{#each kinds as kind (kind)}
				<section>
					<div class="mb-2 flex items-center justify-between border-b border-slate-100 pb-1">
						<h2
							class="font-sans text-xs font-black tracking-[2px] text-institutional-navy uppercase"
						>
							{PART_LABELS[kind]}
						</h2>
						<button
							class="text-xs font-bold text-institutional-navy hover:underline"
							onclick={() => create(kind)}>+ Neu</button
						>
					</div>
					<ul class="flex flex-col gap-1">
						{#each templates.filter((t) => t.kind === kind) as t (t.id)}
							<li
								class="flex items-center gap-2 border bg-white px-3 py-2 {draft?.id === t.id
									? 'border-institutional-navy'
									: 'border-slate-200'}"
							>
								<button
									class="min-w-0 flex-1 truncate text-left text-sm font-bold"
									onclick={() => edit(t)}
									>{t.name}
									<span class="block truncate text-xs font-normal text-slate-400"
										>{t.criteria.filter((c) => c.type !== 'HEADING').length} Kriterien</span
									></button
								>
								<button
									class="text-xs text-slate-400 hover:text-institutional-navy"
									title="Kopieren"
									onclick={() => duplicate(t)}>⧉</button
								>
								<button
									class="text-xs text-slate-400 hover:text-rose-700"
									title="Löschen"
									onclick={() => remove(t)}>✕</button
								>
							</li>
						{:else}
							<li class="text-xs text-slate-400 italic">Keine Vorlage.</li>
						{/each}
					</ul>
				</section>
			{/each}
		</aside>

		<section>
			{#if draft}
				<form
					class="flex flex-col gap-4 border border-slate-200 bg-slate-50 p-6"
					onsubmit={(e) => {
						e.preventDefault();
						save();
					}}
				>
					<div class="grid grid-cols-1 gap-4 md:grid-cols-3">
						<label class="ed-label md:col-span-2"
							>Name
							<input class="ed-input" bind:value={draft.name} required maxlength="200" /></label
						>
						<label class="ed-label"
							>Gedacht für
							<select class="ed-input" bind:value={draft.kind}>
								{#each kinds as kind (kind)}<option value={kind}>{PART_LABELS[kind]}</option>{/each}
							</select></label
						>
						<label class="ed-label md:col-span-3"
							>Beschreibung
							<input class="ed-input" bind:value={draft.description} maxlength="2000" /></label
						>
					</div>
					<CriteriaEditor bind:criteria={draft.criteria} />
					<div class="flex flex-wrap items-center gap-3 border-t border-slate-200 pt-4">
						<button
							type="submit"
							disabled={busy}
							class="bg-institutional-navy px-6 py-3 font-mono text-xs font-black tracking-widest text-white uppercase disabled:opacity-40"
							>{draft.id ? 'Speichern' : 'Vorlage anlegen'}</button
						>
						<button type="button" class="ed-button" onclick={() => (draft = null)}>Schließen</button
						>
						{#if message}<span class="text-sm text-emerald-700" role="status">{message}</span>{/if}
					</div>
				</form>
			{:else}
				<p
					class="border border-dashed border-slate-200 bg-slate-50 p-16 text-center text-slate-400"
				>
					Vorlage links auswählen oder neu anlegen.
				</p>
			{/if}
		</section>
	</div>
</div>
