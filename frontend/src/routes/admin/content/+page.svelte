<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { api, type Schemas } from '$lib/api';
	import { TYPE_LABELS } from '$lib/tasks/labels';
	import { dndzone, type DndEvent } from 'svelte-dnd-action';
	import ModuleForm from '$lib/editor/ModuleForm.svelte';
	import TransferReportView from '$lib/components/TransferReport.svelte';
	import { exportUrl, importFile } from '$lib/transfer';
	import type { TransferReport } from '$lib/api';

	type ModuleRow = Schemas['ModuleRow'];
	type TaskRow = ModuleRow['tasks'][number];
	type ImportReport = Schemas['ImportReport'];

	let modules = $state<ModuleRow[]>([]);
	let error = $state('');
	let report = $state<ImportReport | null>(null);
	let transferReport = $state<TransferReport | null>(null);
	let editingModule = $state<number | null>(null);
	let newModule = $state({ slug: '', title: '', description: '', icon: '📘', sortOrder: 99 });
	let newTask = $state<Record<number, { slug: string; type: string; title: string }>>({});
	let showArchived = $state(false);

	// Alle Typen mit Beschriftung – neue Typen erscheinen so automatisch
	const types = Object.keys(TYPE_LABELS);

	// Angezeigte Reihenfolge je Modul; während des Ziehens lokal, gespeichert beim Loslassen
	let lists = $derived<Record<number, TaskRow[]>>(
		Object.fromEntries(
			modules.map((m) => [m.id, m.tasks.filter((t) => showArchived || !t.archived)])
		)
	);
	let saving = $state(false);

	function consider(moduleId: number, e: CustomEvent<DndEvent<TaskRow>>) {
		lists = { ...lists, [moduleId]: e.detail.items };
	}

	function finalize(row: ModuleRow, e: CustomEvent<DndEvent<TaskRow>>) {
		lists = { ...lists, [row.id]: e.detail.items };
		saveOrder(row, e.detail.items);
	}

	function move(row: ModuleRow, index: number, delta: number) {
		const items = [...lists[row.id]];
		const target = index + delta;
		if (target < 0 || target >= items.length) return;
		[items[index], items[target]] = [items[target], items[index]];
		lists = { ...lists, [row.id]: items };
		saveOrder(row, items);
	}

	// Ausgeblendete (archivierte) Aufgaben behalten ihren Platz, die sichtbaren füllen die übrigen
	async function saveOrder(row: ModuleRow, visible: TaskRow[]) {
		const shown = new Map(visible.map((t) => [t.id, t]));
		const queue = visible.map((t) => t.id);
		const taskIds = row.tasks.map((t) => (shown.has(t.id) ? queue.shift()! : t.id));
		if (taskIds.every((id, i) => id === row.tasks[i].id)) return;
		error = '';
		saving = true;
		try {
			modules = await api<ModuleRow[]>(`/api/admin/content/modules/${row.id}/order`, {
				method: 'PUT',
				body: { taskIds }
			});
		} catch (e) {
			error = (e as Error).message;
			await load();
		} finally {
			saving = false;
		}
	}

	async function load() {
		try {
			modules = await api<ModuleRow[]>('/api/admin/content');
			for (const m of modules) newTask[m.id] ??= { slug: '', type: 'MULTIPLE_CHOICE', title: '' };
		} catch (e) {
			error = (e as Error).message;
		}
	}

	onMount(load);

	async function createModule() {
		error = '';
		try {
			await api('/api/admin/content/modules', { method: 'POST', body: newModule });
			newModule = { slug: '', title: '', description: '', icon: '📘', sortOrder: 99 };
			await load();
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function createTask(moduleId: number) {
		error = '';
		try {
			const view = await api<Schemas['TaskEditorView']>(
				`/api/admin/content/modules/${moduleId}/tasks`,
				{ method: 'POST', body: newTask[moduleId] }
			);
			goto(`/admin/content/${view.taskId}`);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	/** Über den zentralen Import: erkennt Content-Pakete und CodePath-Exporte und berichtet je Eintrag */
	async function upload(event: Event) {
		const input = event.currentTarget as HTMLInputElement;
		const file = input.files?.[0];
		input.value = '';
		if (!file) return;
		error = '';
		report = null;
		try {
			transferReport = await importFile(file);
		} catch (e) {
			error = (e as Error).message;
		}
		await load();
	}

	async function importBundled() {
		if (
			!confirm(
				'Mitgelieferte Inhalte übernehmen? Geänderte Aufgaben werden als neue Versionen veröffentlicht.'
			)
		)
			return;
		transferReport = null;
		report = await api<ImportReport>('/api/admin/content/import-bundled', { method: 'POST' });
		await load();
	}
</script>

<svelte:head><title>Inhalte | CodePath</title></svelte:head>

<div class="flex flex-col gap-8 pb-24">
	<header class="border-b border-slate-200 pb-8">
		<a
			href="/admin"
			class="mb-6 inline-block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
			>← Zurück zur Administration</a
		>
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Inhalte
		</h1>
		<p class="mt-4 text-slate-500">
			Module und Aufgaben anlegen und bearbeiten – ohne Deployment. Jede Veröffentlichung ist eine
			neue Version. Aufgaben per Ziehen oder mit ▲▼ umsortieren; die Reihenfolge gilt sofort.
		</p>
		<div class="mt-6 flex flex-wrap gap-3">
			<a class="ed-button" href={exportUrl(['modules'])} download>Export (ZIP)</a>
			<label class="ed-button cursor-pointer"
				>Import<input type="file" accept=".zip,.json" class="hidden" onchange={upload} /></label
			>
			<a class="ed-button" href="/admin/transfer">Alles importieren &amp; exportieren …</a>
			<button class="ed-button" onclick={importBundled}>Mitgelieferte Inhalte übernehmen</button>
			<label class="flex items-center gap-2 text-sm"
				><input type="checkbox" bind:checked={showArchived} /> Archivierte zeigen</label
			>
		</div>
	</header>

	{#if error}<div class="border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
			{error}
		</div>{/if}
	{#if report}
		<div class="border border-green-200 bg-green-50 p-4 text-sm text-green-900" role="status">
			Import: {report.modulesCreated} Module neu, {report.tasksCreated} Aufgaben neu, {report.versionsPublished}
			neue Versionen, {report.unchanged} unverändert.
			{#each report.errors as e (e)}<div class="text-rose-700">{e}</div>{/each}
		</div>
	{/if}

	{#if transferReport}
		<TransferReportView report={transferReport} />
	{/if}

	{#each modules as row (row.id)}
		<section class="border border-slate-200 bg-white">
			<div class="flex items-center justify-between border-b border-slate-100 p-5">
				<h2 class="text-lg font-bold text-institutional-navy">
					{row.module.icon}
					{row.module.title} <span class="font-mono text-xs text-slate-400">{row.module.slug}</span>
				</h2>
				<span class="flex items-center gap-4">
					<span class="text-xs text-slate-400">{row.tasks.length} Aufgaben</span>
					<button
						class="ed-button"
						onclick={() => (editingModule = editingModule === row.id ? null : row.id)}
						aria-expanded={editingModule === row.id}>Modul bearbeiten</button
					>
				</span>
			</div>
			{#if row.module.career && editingModule !== row.id}
				<p class="border-b border-slate-100 px-5 py-2 text-xs text-slate-500">
					Berufsfeld: {row.module.career} · {row.module.learnMore?.length ?? 0} Links zum Weiterlernen
				</p>
			{/if}
			{#if editingModule === row.id}
				<ModuleForm
					{row}
					onsaved={async () => {
						editingModule = null;
						await load();
					}}
					oncancel={() => (editingModule = null)}
				/>
			{/if}
			<ul
				class="divide-y divide-slate-100"
				aria-label="Aufgaben von {row.module.title}"
				use:dndzone={{
					items: lists[row.id] ?? [],
					flipDurationMs: 150,
					dragDisabled: saving,
					dropTargetStyle: {}
				}}
				onconsider={(e) => consider(row.id, e)}
				onfinalize={(e) => finalize(row, e)}
			>
				{#each lists[row.id] ?? [] as task, i (task.id)}
					<li class="flex items-center bg-white" data-task={task.id}>
						<span
							class="cursor-grab px-3 text-slate-300 select-none"
							title="Ziehen zum Verschieben"
							aria-hidden="true">⠿</span
						>
						<a
							href={`/admin/content/${task.id}`}
							class="flex flex-1 items-center justify-between py-3 pr-3 text-sm hover:bg-slate-50 {task.archived
								? 'opacity-50'
								: ''}"
						>
							<span
								><span class="mr-3 font-mono text-xs text-slate-400">{i + 1}</span
								>{task.title}</span
							>
							<span class="flex items-center gap-3 font-mono text-[10px] uppercase">
								<span class="text-slate-400">{TYPE_LABELS[task.type] ?? task.type}</span>
								<span>{task.points} P.</span>
								{#if task.currentVersion}<span class="text-slate-500">v{task.currentVersion}</span
									>{:else}<span class="text-amber-600">unveröffentlicht</span>{/if}
								{#if task.hasDraft}<span class="bg-amber-100 px-2 text-amber-700">Entwurf</span
									>{/if}
								{#if task.archived}<span class="bg-slate-200 px-2">archiviert</span>{/if}
							</span>
						</a>
						<span class="flex pr-3">
							<button
								type="button"
								class="px-2 text-slate-400 hover:text-institutional-navy disabled:opacity-20"
								aria-label="{task.title} nach oben"
								disabled={saving || i === 0}
								onclick={() => move(row, i, -1)}>▲</button
							>
							<button
								type="button"
								class="px-2 text-slate-400 hover:text-institutional-navy disabled:opacity-20"
								aria-label="{task.title} nach unten"
								disabled={saving || i === (lists[row.id]?.length ?? 0) - 1}
								onclick={() => move(row, i, 1)}>▼</button
							>
						</span>
					</li>
				{/each}
			</ul>
			{#if newTask[row.id]}
				<form
					class="flex flex-wrap items-end gap-3 border-t border-slate-100 bg-slate-50 p-4"
					onsubmit={(e) => {
						e.preventDefault();
						createTask(row.id);
					}}
				>
					<label class="ed-label"
						>Neue Aufgabe: Titel<input
							class="ed-input"
							bind:value={newTask[row.id].title}
							required
						/></label
					>
					<label class="ed-label"
						>Slug<input
							class="ed-input mono"
							bind:value={newTask[row.id].slug}
							pattern="[a-z0-9][a-z0-9-]*"
							required
							placeholder="z. b. pw-check"
						/></label
					>
					<label class="ed-label"
						>Typ
						<select class="ed-input" bind:value={newTask[row.id].type}>
							{#each types as type (type)}<option value={type}>{TYPE_LABELS[type] ?? type}</option
								>{/each}
						</select>
					</label>
					<button class="ed-button bg-institutional-navy text-white">Anlegen</button>
				</form>
			{/if}
		</section>
	{/each}

	<form
		class="grid grid-cols-1 gap-3 border border-dashed border-slate-300 p-5 md:grid-cols-5"
		onsubmit={(e) => {
			e.preventDefault();
			createModule();
		}}
	>
		<p class="ed-label md:col-span-5">Neues Modul</p>
		<label class="ed-label"
			>Titel<input class="ed-input" bind:value={newModule.title} required /></label
		>
		<label class="ed-label"
			>Slug<input
				class="ed-input mono"
				bind:value={newModule.slug}
				required
				pattern="[a-z0-9][a-z0-9-]*"
			/></label
		>
		<label class="ed-label">Symbol<input class="ed-input" bind:value={newModule.icon} /></label>
		<label class="ed-label"
			>Reihenfolge<input class="ed-input" type="number" bind:value={newModule.sortOrder} /></label
		>
		<button class="ed-button self-end">Modul anlegen</button>
		<label class="ed-label md:col-span-5"
			>Beschreibung<input class="ed-input" bind:value={newModule.description} /></label
		>
	</form>
</div>
