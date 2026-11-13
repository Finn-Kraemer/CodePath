<script lang="ts">
	import { onMount } from 'svelte';
	import { SvelteSet } from 'svelte/reactivity';
	import {
		api,
		type ModuleReleaseView,
		type Schemas,
		type StudentOverview,
		type TaskReleaseView
	} from '$lib/api';
	import { courseSelection } from '$lib/courses.svelte';

	type UserReleaseView = Schemas['UserReleaseView'];

	/**
	 * Freigaben für einzelne Schülerinnen und Schüler (gehen den Kursfreigaben vor).
	 * Mit `username`: in der Schülerakte für diese Person, Modul und Aufgabe wählbar.
	 * Mit `moduleId`: in der Modul-Steuerung für eine Auswahl aus dem Kurs.
	 */
	let {
		username,
		moduleId,
		onchange
	}: { username?: string; moduleId?: number; onchange?: () => void } = $props();

	let releases = $state<UserReleaseView[] | null>(null);
	let modules = $state<ModuleReleaseView[]>([]);
	let tasks = $state<TaskReleaseView[]>([]);
	let students = $state<StudentOverview[]>([]);
	const selected = new SvelteSet<string>();
	let error = $state('');
	let message = $state('');
	let saving = $state(false);

	let form = $state({
		moduleId: 0,
		taskId: 0,
		mode: 'SHOWN' as 'SHOWN' | 'HIDDEN',
		availableUntil: '',
		bonus: false
	});

	async function load() {
		error = '';
		try {
			if (username) {
				const [list, mods] = await Promise.all([
					api<UserReleaseView[]>(`/api/admin/students/${encodeURIComponent(username)}/releases`),
					modules.length ? Promise.resolve(modules) : api<ModuleReleaseView[]>('/api/admin/modules')
				]);
				releases = list;
				modules = mods;
				if (!form.moduleId && modules.length) form.moduleId = modules[0].id;
			} else if (moduleId) {
				form.moduleId = moduleId;
				const [list, people] = await Promise.all([
					api<UserReleaseView[]>(
						`/api/admin/modules/${moduleId}/user-releases${courseSelection.query()}`
					),
					api<StudentOverview[]>(`/api/admin/students${courseSelection.query()}`)
				]);
				releases = list;
				students = people;
			}
			await loadTasks();
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function loadTasks() {
		if (!form.moduleId) {
			tasks = [];
			return;
		}
		tasks = await api<TaskReleaseView[]>(
			`/api/admin/modules/${form.moduleId}/tasks${courseSelection.query()}`
		);
		if (form.taskId && !tasks.some((t) => t.id === form.taskId)) form.taskId = 0;
	}

	async function save() {
		error = '';
		message = '';
		const usernames = username ? [username] : [...selected];
		if (usernames.length === 0) {
			error = 'Bitte mindestens eine Person auswählen.';
			return;
		}
		saving = true;
		try {
			const body = {
				usernames,
				moduleId: form.taskId ? null : form.moduleId,
				taskId: form.taskId || null,
				mode: form.mode,
				availableUntil: form.mode === 'SHOWN' && form.availableUntil ? form.availableUntil : null,
				bonus: form.mode === 'SHOWN' && form.bonus
			};
			await api('/api/admin/user-releases', { method: 'POST', body });
			message =
				usernames.length === 1 ? 'Gespeichert.' : `Für ${usernames.length} Personen gespeichert.`;
			selected.clear();
			await load();
			onchange?.();
		} catch (e) {
			error = (e as Error).message;
		} finally {
			saving = false;
		}
	}

	async function remove(release: UserReleaseView) {
		error = '';
		try {
			await api(
				`/api/admin/students/${encodeURIComponent(release.username ?? '')}/releases/${release.id}`,
				{
					method: 'DELETE'
				}
			);
			await load();
			onchange?.();
		} catch (e) {
			error = (e as Error).message;
		}
	}

	function deadline(value?: string | null) {
		return value
			? new Date(value).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' })
			: '';
	}

	onMount(() => {
		if (username) load();
	});

	function toggleAll() {
		if (selected.size === students.length) selected.clear();
		else students.forEach((s) => selected.add(s.username));
	}
</script>

{#snippet list()}
	{#if releases && releases.length > 0}
		<ul class="divide-y divide-slate-100 border border-slate-100" data-testid="user-releases">
			{#each releases as r (r.id)}
				<li class="flex flex-wrap items-center gap-3 px-3 py-2 text-xs">
					<span
						class="px-2 py-0.5 font-mono text-[11px] font-bold tracking-wider uppercase {r.mode ===
						'SHOWN'
							? 'bg-emerald-50 text-emerald-700'
							: 'bg-rose-50 text-rose-700'}">{r.mode === 'SHOWN' ? 'frei' : 'gesperrt'}</span
					>
					{#if !username}<strong class="text-institutional-navy">{r.displayName}</strong>{/if}
					<span class="flex-1 text-slate-700">
						{r.moduleTitle}{r.taskTitle ? ` › ${r.taskTitle}` : ' (ganzes Modul)'}
						{#if r.bonus}<span
								class="ml-1 bg-amber-50 px-1.5 py-0.5 font-mono text-[11px] text-amber-800 uppercase"
								>Bonus</span
							>{/if}
						{#if r.availableUntil}<span class="ml-1 text-slate-400"
								>bis {deadline(r.availableUntil)}</span
							>{/if}
					</span>
					<button
						type="button"
						class="text-rose-600 underline"
						onclick={() => remove(r)}
						aria-label="Freigabe aufheben">aufheben</button
					>
				</li>
			{/each}
		</ul>
	{:else if releases}
		<p class="text-xs text-slate-400">Keine Freigaben für einzelne Personen.</p>
	{/if}
{/snippet}

{#snippet fields()}
	<div class="flex flex-wrap items-end gap-3 text-xs">
		{#if username}
			<label
				>Modul<br /><select
					bind:value={form.moduleId}
					onchange={() => loadTasks().catch((e) => (error = (e as Error).message))}
					class="mt-1 border-slate-200 bg-white py-1 text-xs"
				>
					{#each modules as m (m.id)}<option value={m.id}>{m.title}</option>{/each}
				</select></label
			>
		{/if}
		<label
			>Was<br /><select
				bind:value={form.taskId}
				class="mt-1 max-w-64 border-slate-200 bg-white py-1 text-xs"
				data-testid="user-release-target"
			>
				<option value={0}>Ganzes Modul</option>
				{#each tasks as t (t.id)}<option value={t.id}>{t.title}</option>{/each}
			</select></label
		>
		<label
			>Freigabe<br /><select
				bind:value={form.mode}
				class="mt-1 border-slate-200 bg-white py-1 text-xs"
				data-testid="user-release-mode"
			>
				<option value="SHOWN">freigeben</option>
				<option value="HIDDEN">sperren</option>
			</select></label
		>
		{#if form.mode === 'SHOWN'}
			<label
				>Eigene Frist (optional)<br /><input
					type="datetime-local"
					bind:value={form.availableUntil}
					class="mt-1 border-slate-200 py-1 text-xs"
				/></label
			>
			<label class="flex items-center gap-1 pb-1.5" title="Punkte zählen nicht in der Rangliste"
				><input type="checkbox" bind:checked={form.bonus} /> Bonus ohne Ranglistenwirkung</label
			>
		{/if}
		<button
			type="button"
			disabled={saving}
			onclick={save}
			class="rounded-[var(--radius-ui-sm)] bg-institutional-navy px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
			data-testid="user-release-save">Speichern</button
		>
	</div>
{/snippet}

{#if username}
	<section
		class="rounded-[var(--radius-ui)] border border-slate-200 bg-white p-8 shadow-sm"
		data-testid="personal-releases"
	>
		<h3 class="mb-2 font-sans text-sm font-black tracking-wider text-slate-400 uppercase">
			Zusätzliche Aufgaben &amp; Freigaben
		</h3>
		<p class="mb-4 text-xs text-slate-500">
			Nur für diese Person: Zusatzaufgaben für Schnelle, leichtere Aufgaben zur Unterstützung oder
			gezielt gesperrte Aufgaben. Gehen den Freigaben des Kurses vor.
		</p>
		{#if releases === null}
			<p class="text-xs text-slate-400">Lädt …</p>
		{:else}
			<div class="space-y-4">
				{@render list()}
				{@render fields()}
			</div>
		{/if}
		{#if error}<p class="mt-2 text-xs text-rose-700" role="alert">{error}</p>{/if}
		{#if message}<p class="mt-2 text-xs text-emerald-700" role="status">{message}</p>{/if}
	</section>
{:else}
	<details
		class="border-t border-slate-100"
		ontoggle={(e) => {
			if (e.currentTarget.open && !releases) load();
		}}
	>
		<summary
			class="cursor-pointer px-8 py-4 font-mono text-[11px] font-bold tracking-wider text-slate-500 uppercase hover:text-institutional-navy"
		>
			Für einzelne Schüler{releases && releases.length ? ` (${releases.length})` : ''}
		</summary>
		<div class="space-y-4 px-8 pb-6">
			{#if releases === null}
				<p class="text-xs text-slate-400">Lädt …</p>
			{:else}
				{@render list()}
				{#if students.length === 0}
					<p class="text-xs text-slate-400">Der Kurs hat noch keine Mitglieder.</p>
				{:else}
					<fieldset class="text-xs">
						<legend class="mb-2 text-slate-500">
							Auswahl ({selected.size} von {students.length})
							<button type="button" class="ml-2 underline" onclick={toggleAll}
								>{selected.size === students.length ? 'keine' : 'alle'}</button
							>
						</legend>
						<div class="flex flex-wrap gap-x-4 gap-y-1">
							{#each students as s (s.username)}
								<label class="flex items-center gap-1"
									><input
										type="checkbox"
										checked={selected.has(s.username)}
										onchange={(e) =>
											e.currentTarget.checked
												? selected.add(s.username)
												: selected.delete(s.username)}
									/>
									{s.displayName}</label
								>
							{/each}
						</div>
					</fieldset>
					{@render fields()}
				{/if}
			{/if}
			{#if error}<p class="text-xs text-rose-700" role="alert">{error}</p>{/if}
			{#if message}<p class="text-xs text-emerald-700" role="status">{message}</p>{/if}
		</div>
	</details>
{/if}
