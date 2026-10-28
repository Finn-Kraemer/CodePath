<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
	import { auth } from '$lib/auth.svelte';
	import { api, type Schemas, type SubmitResult, type TaskDetail } from '$lib/api';
	import ConfigForm from '$lib/editor/ConfigForm.svelte';
	import MediaPicker from '$lib/components/MediaPicker.svelte';
	import { mediaUrl } from '$lib/media';
	import type { TaskEditorView, TaskFile } from '$lib/editor/types';
	import Markdown from '$lib/Markdown.svelte';
	import TaskRenderer from '$lib/tasks/TaskRenderer.svelte';
	import type { Answer } from '$lib/tasks/types';
	import { TYPE_LABELS } from '$lib/tasks/labels';

	type CheckResult = Schemas['CheckResult'];
	type DeleteImpact = Schemas['DeleteImpact'];

	const taskId = page.params.taskId;
	const base = `/api/admin/content/tasks/${taskId}`;

	let view = $state<TaskEditorView | null>(null);
	let file = $state<TaskFile | null>(null);
	let hints = $state<string[]>(['', '', '']);
	let preview = $state<TaskDetail | null>(null);
	let previewResult = $state<SubmitResult | null>(null);
	let previewKey = $state(0);
	let check = $state<CheckResult | null>(null);
	let message = $state('');
	let error = $state('');
	let busy = $state(false);
	let dirty = $state(false);

	function edit(v: TaskEditorView) {
		view = v;
		const source = v.draft ?? v.published;
		file = source ? JSON.parse(JSON.stringify(source)) : null;
		if (file) {
			file.rules ??= {};
			hints = [...(file.hints ?? []), '', '', ''].slice(0, 3);
		}
		dirty = false;
	}

	async function refreshPreview() {
		preview = await api<TaskDetail>(`${base}/preview`);
		previewResult = null;
		previewKey++;
	}

	onMount(async () => {
		try {
			edit(await api<TaskEditorView>(base));
			await refreshPreview();
		} catch (e) {
			error = (e as Error).message;
		}
	});

	async function run<T>(action: () => Promise<T>, success?: string): Promise<T | undefined> {
		busy = true;
		error = '';
		message = '';
		try {
			const result = await action();
			if (success) message = success;
			return result;
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	async function save() {
		if (!file) return;
		file.hints = hints.filter((h) => h.trim());
		// Geleerte Felder bedeuten „Standard des Typs“, nicht „unbegrenzt“
		file.rules = Object.fromEntries(
			Object.entries(file.rules ?? {}).filter(([, v]) => v !== null && v !== undefined && v !== '')
		);
		const saved = await run(
			() => api<TaskEditorView>(`${base}/draft`, { method: 'PUT', body: file }),
			'Entwurf gespeichert.'
		);
		if (saved) {
			edit(saved);
			check = null;
			await refreshPreview();
		}
	}

	async function runCheck() {
		if (dirty) await save();
		check = (await run(() => api<CheckResult>(`${base}/check`, { method: 'POST' }))) ?? null;
	}

	async function publish() {
		if (dirty) await save();
		const result = await run(
			() => api<TaskEditorView>(`${base}/publish`, { method: 'POST' }),
			'Veröffentlicht – Schüler sehen jetzt diese Version.'
		);
		if (result) edit(result);
	}

	async function discard() {
		if (!confirm('Entwurf verwerfen?')) return;
		const result = await run(
			() => api<TaskEditorView>(`${base}/draft`, { method: 'DELETE' }),
			'Entwurf verworfen.'
		);
		if (result) {
			edit(result);
			await refreshPreview();
		}
	}

	async function archive(value: boolean) {
		const result = await run(
			() => api<TaskEditorView>(`${base}/archived?value=${value}`, { method: 'PUT' }),
			value ? 'Archiviert.' : 'Wiederhergestellt.'
		);
		if (result) edit(result);
	}

	let impact = $state<DeleteImpact | null>(null);
	let confirmSlug = $state('');

	async function prepareDelete() {
		confirmSlug = '';
		impact = (await run(() => api<DeleteImpact>(`${base}/delete-impact`))) ?? null;
	}

	async function deleteTask() {
		if (!impact) return;
		const done = await run(async () => {
			const res = await auth.apiFetch(`${base}?confirm=${encodeURIComponent(confirmSlug)}`, {
				method: 'DELETE'
			});
			if (!res.ok)
				throw new Error((await res.json().catch(() => ({})))?.message ?? `Fehler ${res.status}`);
			return true;
		});
		if (done) await goto('/admin/content');
	}

	async function restore(versionId: number) {
		const result = await run(
			() => api<TaskEditorView>(`${base}/versions/${versionId}/restore`, { method: 'POST' }),
			'Als Entwurf übernommen.'
		);
		if (result) {
			edit(result);
			await refreshPreview();
		}
	}

	async function previewSubmit(answer: Answer) {
		previewResult = await api<SubmitResult>(`${base}/preview/evaluate`, {
			method: 'POST',
			body: answer
		});
	}
</script>

<svelte:head><title>Aufgabe bearbeiten | CodePath</title></svelte:head>

<div class="flex flex-col gap-6 pb-24">
	<a
		href="/admin/content"
		class="font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
		>← Alle Inhalte</a
	>
	{#if view && file}
		<header
			class="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 pb-4"
		>
			<div>
				<p class="font-mono text-xs text-slate-400">
					{view.moduleSlug}/{file.slug} · {TYPE_LABELS[view.type] ?? view.type} · {view.currentVersion
						? `veröffentlicht: v${view.currentVersion}`
						: 'noch nicht veröffentlicht'}{view.draft ? ' · Entwurf vorhanden' : ''}
				</p>
				<h1 class="text-3xl font-extrabold text-institutional-navy">{file.title}</h1>
			</div>
			<div class="flex flex-wrap gap-2">
				<button class="ed-button" onclick={save} disabled={busy}>Entwurf speichern</button>
				<button class="ed-button" onclick={runCheck} disabled={busy}>Prüfen</button>
				<button
					class="ed-button bg-green-700 text-white"
					onclick={publish}
					disabled={busy || (!view.draft && !dirty)}>Veröffentlichen</button
				>
				{#if view.draft && view.currentVersion}<button
						class="ed-button"
						onclick={discard}
						disabled={busy}>Entwurf verwerfen</button
					>{/if}
				<button class="ed-button" onclick={() => archive(!view!.archived)} disabled={busy}
					>{view.archived ? 'Wiederherstellen' : 'Archivieren'}</button
				>
				{#if view.archived}<button
						class="ed-button border-rose-300 text-rose-700"
						onclick={prepareDelete}
						disabled={busy}>Endgültig löschen …</button
					>{/if}
			</div>
		</header>

		{#if impact}
			<section
				class="border-2 border-rose-300 bg-rose-50 p-4 text-sm"
				aria-label="Endgültig löschen"
			>
				<p class="font-bold text-rose-800">„{impact.title}“ endgültig löschen?</p>
				<p class="mt-2 text-rose-900">
					Das lässt sich nicht rückgängig machen. Gelöscht werden alle Versionen und mit ihnen:
				</p>
				<ul class="mt-1 list-disc pl-5 text-rose-900">
					<li>{impact.attempts} Versuche von {impact.students} Schülerinnen und Schülern</li>
					<li>
						{impact.points} vergebene Punkte – die Rangliste ändert sich{#if impact.pendingReviews}
							, {impact.pendingReviews} unbewertete Praxisabgaben{/if}
					</li>
				</ul>
				<p class="mt-2 text-xs text-rose-900">
					Nur ausblenden statt löschen? Archiviert lassen – dann bleiben Ergebnisse und Punkte
					erhalten.
				</p>
				<label class="ed-label mt-3"
					>Zur Bestätigung den Slug <code>{impact.slug}</code> eingeben<input
						class="ed-input mono"
						bind:value={confirmSlug}
						autocomplete="off"
					/></label
				>
				<div class="mt-3 flex gap-2">
					<button
						class="ed-button bg-rose-700 text-white"
						onclick={deleteTask}
						disabled={busy || confirmSlug !== impact.slug}>Endgültig löschen</button
					>
					<button class="ed-button" onclick={() => (impact = null)}>Abbrechen</button>
				</div>
			</section>
		{/if}

		{#if error}<div class="border border-red-200 bg-red-50 p-3 text-sm text-red-700" role="alert">
				{error}
			</div>{/if}
		{#if message}<div
				class="border border-green-200 bg-green-50 p-3 text-sm text-green-800"
				role="status"
			>
				{message}
			</div>{/if}
		{#if check}
			<div
				class="border p-4 text-sm {check.valid
					? 'border-green-200 bg-green-50'
					: 'border-rose-200 bg-rose-50'}"
			>
				<p class="font-bold">{check.valid ? 'Prüfung bestanden' : 'Prüfung nicht bestanden'}</p>
				{#if check.dryRun}<p class="mt-1 text-xs">
						Probelauf: Musterlösung → {check.dryRun.sampleOutcome}, falsche Antwort → {check.dryRun
							.wrongOutcome}
					</p>{/if}
				<ul class="mt-2 list-disc pl-5">
					{#each check.problems as p (p)}<li>{p}</li>{/each}
				</ul>
			</div>
		{/if}

		<div class="grid grid-cols-1 gap-8 xl:grid-cols-2">
			<form
				class="space-y-4"
				oninput={() => (dirty = true)}
				onsubmit={(e) => {
					e.preventDefault();
					save();
				}}
			>
				<div class="grid grid-cols-6 gap-3">
					<label class="ed-label col-span-3"
						>Titel<input class="ed-input" bind:value={file.title} /></label
					>
					<label class="ed-label"
						>Punkte<input class="ed-input" type="number" min="0" bind:value={file.points} /></label
					>
					<label class="ed-label"
						>Stufe
						<select class="ed-input" bind:value={file.difficulty}
							><option value="EASY">leicht</option><option value="MEDIUM">mittel</option><option
								value="HARD">schwer</option
							></select
						>
					</label>
					<label class="ed-label"
						>Position<input class="ed-input" type="number" bind:value={file.sortOrder} /></label
					>
				</div>
				<label class="ed-label"
					>Lernziel (ein Satz)<input
						class="ed-input"
						bind:value={file.learningGoal}
						placeholder="Du kannst …"
					/></label
				>
				<label class="ed-label"
					>Geschichte / Kontext (Markdown)<textarea
						class="ed-input"
						rows="4"
						bind:value={file.story}
					></textarea></label
				>
				<MediaPicker
					label="Bild in die Geschichte einfügen"
					onselect={(m) => {
						if (!file) return;
						const alt = (m.originalName ?? 'Bild').replace(/\.[a-z]+$/i, '').replace(/[[\]]/g, '');
						file.story = `${(file.story ?? '').trimEnd()}\n\n![${alt}](${mediaUrl(m.key)})\n`;
						dirty = true;
					}}
				/>
				<label class="ed-label"
					>Auftrag (Markdown)<textarea class="ed-input" rows="3" bind:value={file.assignment}
					></textarea></label
				>
				<fieldset class="border border-slate-200 p-4">
					<legend class="ed-label px-1">Aufgabenspezifisch</legend>
					<ConfigForm type={view.type} bind:config={file.config} points={file.points} />
				</fieldset>
				<fieldset class="space-y-2 border border-slate-200 p-4">
					<legend class="ed-label px-1">Gestufte Hinweise (höchstens drei)</legend>
					{#each hints as _, i (i)}<textarea
							class="ed-input"
							rows="2"
							bind:value={hints[i]}
							placeholder={`Hinweis ${i + 1}`}
						></textarea>{/each}
				</fieldset>
				<label class="ed-label"
					>Erklärung nach dem Lösen (Markdown)<textarea
						class="ed-input"
						rows="3"
						bind:value={file.explanation}
					></textarea></label
				>
				<fieldset class="grid grid-cols-2 gap-3 border border-slate-200 p-4 md:grid-cols-4">
					<legend class="ed-label px-1">Regeln (leer = Standard des Typs)</legend>
					<label class="ed-label"
						>Max. Versuche<input
							class="ed-input"
							type="number"
							min="0"
							bind:value={file.rules.maxAttempts}
							placeholder="Standard"
						/></label
					>
					<label class="ed-label"
						>Abzug je Hinweis %<input
							class="ed-input"
							type="number"
							min="0"
							max="100"
							bind:value={file.rules.hintPenaltyPercent}
							placeholder="25"
						/></label
					>
					<label class="ed-label"
						>Bonus 1. Versuch %<input
							class="ed-input"
							type="number"
							min="0"
							max="100"
							bind:value={file.rules.firstTryBonusPercent}
							placeholder="20"
						/></label
					>
					<label class="ed-label flex items-center gap-2"
						><input
							type="checkbox"
							checked={file.rules.revealAllowed !== false}
							onchange={(e) => (file!.rules.revealAllowed = e.currentTarget.checked)}
						/> Aufdecken erlaubt</label
					>
				</fieldset>
				<details class="border border-slate-200 p-4">
					<summary class="ed-label cursor-pointer">Versionen ({view.versions.length})</summary>
					<ul class="mt-3 space-y-1 text-sm">
						{#each view.versions as v (v.id)}
							<li class="flex items-center justify-between">
								<span
									>v{v.version} · {v.title} · {v.points} P. · {new Date(
										v.publishedAt
									).toLocaleString('de-DE')}</span
								>
								<button type="button" class="ed-button" onclick={() => restore(v.id)}
									>als Entwurf</button
								>
							</li>
						{/each}
					</ul>
				</details>
			</form>

			<aside class="space-y-4">
				<div class="flex items-center justify-between">
					<p class="ed-label">
						Vorschau wie bei Schülern {dirty ? '(Änderungen erst nach dem Speichern)' : ''}
					</p>
					<button class="ed-button" onclick={refreshPreview}>Neu laden</button>
				</div>
				{#if preview}
					<div class="space-y-4 border-4 border-dashed border-slate-200 p-4">
						{#if preview.learningGoal}<p class="text-sm">
								<b>Lernziel:</b>
								{preview.learningGoal}
							</p>{/if}
						{#if preview.story}<Markdown
								source={preview.story}
								class="text-sm text-slate-600"
							/>{/if}
						{#if preview.assignment}<Markdown
								source={preview.assignment}
								class="text-sm font-medium"
							/>{/if}
						{#if previewResult}
							<div
								class="border p-3 text-sm {previewResult.isCorrect
									? 'border-green-200 bg-green-50'
									: 'border-rose-200 bg-rose-50'}"
							>
								{previewResult.feedback}
								{previewResult.isCorrect ? `(${previewResult.pointsAwarded} Punkte)` : ''}
							</div>
						{/if}
						{#key previewKey}
							<TaskRenderer
								task={preview}
								disabled={false}
								busy={false}
								result={previewResult}
								submit={previewSubmit}
							/>
						{/key}
					</div>
				{/if}
			</aside>
		</div>
	{:else if error}
		<div class="border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">{error}</div>
	{/if}
</div>
