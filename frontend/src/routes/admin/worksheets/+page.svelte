<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { api, type WorksheetSummary } from '$lib/api';
	import { exportUrl } from '$lib/transfer';

	let sheets = $state<WorksheetSummary[]>([]);
	let loading = $state(true);
	let error = $state('');
	let title = $state('');
	let filter = $state('');

	const visible = $derived(
		sheets.filter((s) =>
			`${s.title} ${s.kind} ${s.subject}`.toLowerCase().includes(filter.trim().toLowerCase())
		)
	);

	async function load() {
		try {
			sheets = await api<WorksheetSummary[]>('/api/admin/worksheets');
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	}

	onMount(load);

	async function create(event: SubmitEvent) {
		event.preventDefault();
		error = '';
		try {
			const sheet = await api<WorksheetSummary>('/api/admin/worksheets', {
				method: 'POST',
				body: { title }
			});
			goto(`/admin/worksheets/${sheet.id}`);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function copy(sheet: WorksheetSummary) {
		try {
			const created = await api<WorksheetSummary>(`/api/admin/worksheets/${sheet.id}/copy`, {
				method: 'POST'
			});
			goto(`/admin/worksheets/${created.id}`);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function remove(sheet: WorksheetSummary) {
		if (!confirm(`Aufgabenblatt „${sheet.title}“ endgültig löschen?`)) return;
		try {
			await api(`/api/admin/worksheets/${sheet.id}`, { method: 'DELETE' });
			sheets = sheets.filter((s) => s.id !== sheet.id);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	function pdf(sheet: WorksheetSummary, variant: 'student' | 'solution') {
		return `/api/admin/worksheets/${sheet.id}/pdf?variant=${variant}&download=true`;
	}

	function points(value: number) {
		return value.toLocaleString('de-DE', { maximumFractionDigits: 1 });
	}
</script>

<svelte:head><title>Aufgabenblätter | CodePath</title></svelte:head>

<div class="mx-auto flex max-w-5xl flex-col gap-10 pb-24">
	<header class="border-b border-slate-200 pb-8">
		<div class="mb-2 flex items-center gap-3 text-institutional-navy/40">
			<a href="/admin" class="transition-colors hover:text-institutional-gold">
				<span class="font-mono text-[11px] font-bold tracking-wider uppercase">Leitstand</span>
			</a>
			<span class="text-slate-300">/</span>
			<span class="font-mono text-[11px] font-bold tracking-wider text-institutional-gold uppercase"
				>Aufgabenblätter</span
			>
		</div>
		<h1
			class="font-sans text-3xl leading-none font-extrabold tracking-tight text-institutional-navy"
		>
			Aufgabenblätter
		</h1>
		<p class="mt-3 max-w-2xl text-sm text-slate-500">
			Arbeitsblätter, Hausaufgaben und Lernkontrollen zum Ausdrucken oder Verschicken: aus
			Bausteinen wie freien Aufgaben, Ankreuzen, Lückentext, Zuordnen oder Tabellen zusammenstellen,
			Kopf und Layout anpassen und als PDF exportieren – jeweils als Schülerblatt und als Lösung.
		</p>
		<div class="mt-4 flex flex-wrap gap-3">
			<a class="ed-button" href={exportUrl(['worksheets'])} download>Exportieren</a>
			<a class="ed-button" href="/admin/transfer">Importieren …</a>
		</div>
	</header>

	{#if error}
		<p class="text-[11px] font-bold tracking-wider text-rose-700 uppercase" role="alert">
			{error}
		</p>
	{/if}

	<form
		class="grid grid-cols-1 items-end gap-4 rounded-[var(--radius-ui)] border border-slate-200 bg-white p-6 md:grid-cols-[1fr_auto]"
		onsubmit={create}
	>
		<label class="ed-label"
			>Titel des neuen Blatts
			<input
				class="ed-input"
				bind:value={title}
				required
				maxlength="200"
				placeholder="z. B. Binärzahlen – Übung 1"
			/>
		</label>
		<button
			class="rounded-[var(--radius-ui-sm)] bg-institutional-navy px-6 py-3 text-sm font-semibold text-white disabled:opacity-50"
			disabled={!title.trim()}>Anlegen</button
		>
	</form>

	{#if loading}
		<p class="text-sm text-slate-400">Lädt …</p>
	{:else if sheets.length === 0}
		<p class="border border-dashed border-slate-300 p-8 text-center text-sm text-slate-400">
			Noch keine Aufgabenblätter.
		</p>
	{:else}
		{#if sheets.length > 5}
			<label class="ed-label max-w-sm"
				>Suchen
				<input class="ed-input" bind:value={filter} placeholder="Titel, Art oder Fach" />
			</label>
		{/if}
		<ul
			class="divide-y divide-slate-100 rounded-[var(--radius-ui)] border border-slate-200 bg-white"
		>
			{#each visible as sheet (sheet.id)}
				<li class="flex flex-wrap items-center gap-4 p-4">
					<div class="min-w-0 flex-1">
						<a
							href={`/admin/worksheets/${sheet.id}`}
							class="font-bold text-institutional-navy hover:underline">{sheet.title}</a
						>
						<p class="font-mono text-[11px] text-slate-400">
							{[sheet.kind, sheet.subject].filter(Boolean).join(' · ') || 'Aufgabenblatt'} ·
							{sheet.tasks}
							{sheet.tasks === 1 ? 'Aufgabe' : 'Aufgaben'}{sheet.points > 0
								? ` · ${points(sheet.points)} Punkte`
								: ''} · geändert {new Date(sheet.updatedAt).toLocaleDateString('de-DE')}
						</p>
					</div>
					<div class="flex flex-wrap gap-1">
						<a class="ed-button" href={pdf(sheet, 'student')}>PDF</a>
						<a class="ed-button" href={pdf(sheet, 'solution')}>Lösung</a>
						<a class="ed-button" href={`/admin/worksheets/${sheet.id}`}>Bearbeiten</a>
						<button class="ed-button" onclick={() => copy(sheet)}>Kopieren</button>
						<button class="ed-button text-rose-700" onclick={() => remove(sheet)}>Löschen</button>
					</div>
				</li>
			{/each}
		</ul>
	{/if}
</div>
