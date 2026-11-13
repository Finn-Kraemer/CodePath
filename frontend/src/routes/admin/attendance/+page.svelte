<script lang="ts">
	import { api, type Schemas } from '$lib/api';
	import CourseSelect from '$lib/components/CourseSelect.svelte';
	import PdfBundle from '$lib/components/PdfBundle.svelte';
	import { courseSelection } from '$lib/courses.svelte';
	import { STATUS, STATUS_ORDER, type AttendanceStatus } from '$lib/attendance';

	type Overview = Schemas['AttendanceOverview'];
	type SessionDetail = Schemas['AttendanceSessionDetail'];
	type EntryView = Schemas['AttendanceEntryView'];
	type Settings = Schemas['AttendanceSettings'];

	let overview = $state<Overview | null>(null);
	let detail = $state<SessionDetail | null>(null);
	let settings = $state<Settings | null>(null);
	let date = $state(new Date().toISOString().slice(0, 10));
	let title = $state('');
	let error = $state('');
	let busy = $state(false);
	let view = $state<'list' | 'matrix'>('list');

	async function load() {
		error = '';
		try {
			const [o, s] = await Promise.all([
				api<Overview>(`/api/admin/attendance${courseSelection.query()}`),
				settings ? Promise.resolve(settings) : api<Settings>('/api/admin/settings/attendance')
			]);
			overview = o;
			settings = s;
			if (detail && !o.sessions.some((x) => x.id === detail?.session.id)) detail = null;
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function open(id: number) {
		error = '';
		try {
			detail = await api<SessionDetail>(`/api/admin/attendance/sessions/${id}`);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function create() {
		busy = true;
		error = '';
		try {
			detail = await api<SessionDetail>('/api/admin/attendance/sessions', {
				method: 'POST',
				body: { courseId: courseSelection.selectedId, date, title: title || null }
			});
			title = '';
			await load();
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	async function setStatus(
		entry: EntryView,
		status: AttendanceStatus,
		note: string | null | undefined = entry.note
	) {
		if (!detail) return;
		error = '';
		try {
			const updated = await api<EntryView>(
				`/api/admin/attendance/sessions/${detail.session.id}/entries/${entry.userId}`,
				{ method: 'PUT', body: { status, note: note ?? null } }
			);
			detail.entries = detail.entries.map((e) => (e.userId === updated.userId ? updated : e));
			await load();
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function remove() {
		if (!detail || !confirm('Diesen Termin mit allen Einträgen löschen?')) return;
		await api(`/api/admin/attendance/sessions/${detail.session.id}`, { method: 'DELETE' });
		detail = null;
		await load();
	}

	async function saveSettings(change: Partial<Settings>) {
		settings = await api<Settings>('/api/admin/settings/attendance', {
			method: 'PUT',
			body: change
		});
	}

	function formatDate(value: string) {
		return new Date(value + 'T00:00').toLocaleDateString('de-DE', {
			weekday: 'short',
			day: '2-digit',
			month: '2-digit',
			year: 'numeric'
		});
	}

	function markAllPresent() {
		if (!detail) return;
		for (const e of detail.entries) if (e.status !== 'PRESENT') setStatus(e, 'PRESENT', null);
	}
</script>

<svelte:head><title>Anwesenheit | CodePath</title></svelte:head>

<div class="flex flex-col gap-8 pb-24">
	<header class="border-b border-slate-200 pb-8">
		<a
			href="/admin"
			class="mb-6 inline-block font-mono text-[11px] font-bold tracking-wider text-slate-400 uppercase hover:text-institutional-navy"
			>← Leitstand</a
		>
		<div class="flex flex-wrap items-end justify-between gap-4">
			<div>
				<h1 class="font-sans text-3xl font-extrabold tracking-tight text-institutional-navy">
					Anwesenheit
				</h1>
				<p class="mt-4 max-w-2xl text-slate-500">
					Neuer Termin: Alle sind zunächst anwesend – nur Abweichungen anklicken. Ein Punkt neben
					dem Namen heißt: hat an dem Tag in CodePath gearbeitet (nur ein Vorschlag).
				</p>
			</div>
			<CourseSelect
				onchange={() => {
					detail = null;
					load();
				}}
			/>
		</div>
	</header>

	{#if error}<div class="border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
			{error}
		</div>{/if}

	<form
		class="flex flex-wrap items-end gap-4 rounded-[var(--radius-ui)] border border-slate-200 bg-white p-6"
		onsubmit={(e) => {
			e.preventDefault();
			create();
		}}
	>
		<label class="text-sm"
			>Datum<br /><input
				type="date"
				bind:value={date}
				required
				class="mt-1 border-slate-200"
				data-testid="attendance-date"
			/></label
		>
		<label class="text-sm"
			>Titel (optional)<br /><input
				bind:value={title}
				maxlength="200"
				placeholder="z. B. Projekttag 3"
				class="mt-1 border-slate-200"
			/></label
		>
		<button
			disabled={busy || !courseSelection.selectedId}
			class="rounded-[var(--radius-ui-sm)] bg-institutional-navy px-6 py-3 text-sm font-semibold text-white disabled:opacity-50"
			data-testid="attendance-create">Termin anlegen</button
		>
	</form>

	{#if detail}
		<section
			class="border-2 border-institutional-navy bg-white p-6"
			data-testid="attendance-session"
		>
			<div class="mb-4 flex flex-wrap items-center justify-between gap-3">
				<h2 class="text-xl font-bold text-institutional-navy">
					{formatDate(detail.session.date)}{detail.session.title
						? ` · ${detail.session.title}`
						: ''}
				</h2>
				<div class="flex gap-3 text-xs">
					<span class="text-slate-500"
						>{detail.session.present} anwesend · {detail.session.late} verspätet · {detail.session
							.excused} entschuldigt · {detail.session.absent} fehlen</span
					>
					<button class="underline" onclick={markAllPresent}>alle anwesend</button>
					<button class="text-rose-600 underline" onclick={remove}>Termin löschen</button>
					<button class="underline" onclick={() => (detail = null)}>schließen</button>
				</div>
			</div>
			<ul class="divide-y divide-slate-100">
				{#each detail.entries as entry (entry.userId)}
					<li
						class="flex flex-wrap items-center gap-3 py-2"
						data-testid="attendance-{entry.username}"
					>
						<span class="flex w-48 items-center gap-2 text-sm font-bold text-institutional-navy">
							{#if entry.activeThatDay}<span
									class="h-2 w-2 rounded-full bg-emerald-500"
									title="Hat an diesem Tag Aufgaben abgegeben"
								></span>{/if}
							{entry.displayName}
						</span>
						<div class="flex" role="group" aria-label="Status {entry.displayName}">
							{#each STATUS_ORDER as status (status)}
								<button
									type="button"
									aria-pressed={entry.status === status}
									class="border px-3 py-1 text-xs {entry.status === status
										? STATUS[status].active
										: 'border-slate-200 text-slate-500 hover:bg-slate-50'}"
									onclick={() => setStatus(entry, status)}>{STATUS[status].label}</button
								>
							{/each}
						</div>
						<input
							value={entry.note ?? ''}
							placeholder="Notiz"
							maxlength="500"
							class="min-w-40 flex-1 border-slate-200 py-1 text-xs"
							onchange={(e) => setStatus(entry, entry.status ?? 'PRESENT', e.currentTarget.value)}
						/>
					</li>
				{:else}
					<li class="py-2 text-sm text-slate-400">Der Kurs hat keine Mitglieder.</li>
				{/each}
			</ul>
		</section>
	{/if}

	{#if overview}
		<section class="rounded-[var(--radius-ui)] border border-slate-200 bg-white p-6">
			<div class="mb-4 flex flex-wrap items-center justify-between gap-3">
				<h2 class="font-bold text-institutional-navy">Termine von {overview.courseName}</h2>
				<div class="flex border border-slate-200 text-xs" role="tablist">
					<button
						role="tab"
						aria-selected={view === 'list'}
						class="px-3 py-1 {view === 'list' ? 'bg-institutional-navy text-white' : ''}"
						onclick={() => (view = 'list')}>Termine</button
					>
					<button
						role="tab"
						aria-selected={view === 'matrix'}
						class="px-3 py-1 {view === 'matrix' ? 'bg-institutional-navy text-white' : ''}"
						onclick={() => (view = 'matrix')}>Übersicht</button
					>
				</div>
			</div>
			{#if overview.sessions.length === 0}
				<p class="text-sm text-slate-400">Noch keine Termine.</p>
			{:else if view === 'list'}
				<ul class="divide-y divide-slate-100">
					{#each [...overview.sessions].reverse() as s (s.id)}
						<li>
							<button
								class="flex w-full items-center justify-between py-2 text-left text-sm hover:bg-slate-50"
								onclick={() => open(s.id)}
							>
								<span>{formatDate(s.date)}{s.title ? ` · ${s.title}` : ''}</span>
								<span class="text-xs text-slate-500"
									>{s.present + s.late} da · {s.excused} entsch. · {s.absent} fehlen</span
								>
							</button>
						</li>
					{/each}
				</ul>
			{:else}
				<div class="overflow-x-auto">
					<table class="text-xs" data-testid="attendance-matrix">
						<thead>
							<tr>
								<th class="sticky left-0 bg-white pr-4 text-left">Name</th>
								{#each overview.sessions as s (s.id)}
									<th class="px-1 font-mono font-normal text-slate-500">
										<button onclick={() => open(s.id)} title={s.title ?? ''}
											>{new Date(s.date + 'T00:00').toLocaleDateString('de-DE', {
												day: '2-digit',
												month: '2-digit'
											})}</button
										>
									</th>
								{/each}
								<th class="pl-4 text-left">Quote</th>
							</tr>
						</thead>
						<tbody>
							{#each overview.students as row (row.userId)}
								<tr class="border-t border-slate-100">
									<td class="sticky left-0 bg-white py-1 pr-4 font-bold">{row.displayName}</td>
									{#each overview.sessions as s (s.id)}
										{@const status = overview.cells[row.userId]?.[s.id] as
											| AttendanceStatus
											| undefined}
										<td class="px-1 text-center">
											{#if status}<span
													class="inline-block w-6 {STATUS[status].cell}"
													title={STATUS[status].label}>{STATUS[status].short}</span
												>{:else}<span class="text-slate-300">·</span>{/if}
										</td>
									{/each}
									<td class="pl-4 whitespace-nowrap text-slate-600"
										>{row.rate.attended}/{row.rate.recorded}</td
									>
								</tr>
							{/each}
						</tbody>
					</table>
				</div>
			{/if}
		</section>

		<section
			class="flex flex-col gap-3 rounded-[var(--radius-ui)] border border-slate-200 bg-white p-6"
		>
			<h2 class="font-bold text-institutional-navy">Export &amp; Einstellungen</h2>
			<div class="flex flex-wrap items-center gap-3">
				<a
					class="ed-button"
					href="/api/admin/attendance/export.csv?courseId={overview.courseId}"
					download>CSV (Tabelle)</a
				>
			</div>
			<PdfBundle
				url="/api/admin/attendance/export?courseId={overview.courseId}"
				label="Anwesenheit je Person"
				testid="bundle-attendance"
			/>
			{#if settings}
				<label class="flex items-center gap-2 text-sm"
					><input
						type="checkbox"
						checked={settings.onCertificate}
						onchange={(e) => saveSettings({ onCertificate: e.currentTarget.checked })}
					/> Anwesenheitsquote auf der Teilnahmebestätigung ausweisen (gilt für die ganze Installation)</label
				>
				<label class="flex items-center gap-2 text-sm"
					><input
						type="checkbox"
						checked={settings.visibleToStudents}
						onchange={(e) => saveSettings({ visibleToStudents: e.currentTarget.checked })}
					/> Schülerinnen und Schüler sehen ihre eigene Anwesenheit im Profil (ohne Notizen)</label
				>
			{/if}
		</section>
	{/if}
</div>
