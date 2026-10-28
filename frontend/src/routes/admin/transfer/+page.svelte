<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type SectionInfo, type TransferReport } from '$lib/api';
	import TransferReportView from '$lib/components/TransferReport.svelte';
	import { exportUrl, importFile } from '$lib/transfer';

	let sections = $state<SectionInfo[]>([]);
	let selected = $state<string[]>([]);
	let report = $state<TransferReport | null>(null);
	let error = $state('');
	let busy = $state(false);
	let dragging = $state(false);

	onMount(async () => {
		try {
			sections = await api<SectionInfo[]>('/api/admin/transfer/sections');
			selected = sections.map((s) => s.key);
		} catch (e) {
			error = (e as Error).message;
		}
	});

	const allSelected = $derived(selected.length === sections.length);

	async function upload(file: File | undefined) {
		if (!file) return;
		error = '';
		report = null;
		busy = true;
		try {
			report = await importFile(file);
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}
</script>

<svelte:head><title>Import &amp; Export | CodePath</title></svelte:head>

<div class="flex flex-col gap-10 pb-24">
	<header class="border-b border-slate-200 pb-8">
		<a
			href="/admin"
			class="mb-6 inline-block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
			>← Zurück zur Administration</a
		>
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Import &amp; Export
		</h1>
		<p class="mt-4 max-w-3xl text-slate-500">
			Einstellungen, Startseite, Seiten, Wissensdatenbank, Lernpfade sowie Module und Aufgaben in
			eine Datei sichern oder auf eine andere CodePath-Installation übertragen. Der Import erkennt
			die Datei selbst und zeigt danach, was angelegt, überarbeitet oder unverändert gelassen wurde
			– und wo ein Fehler aufgetreten ist. Schülerdaten werden nie exportiert.
		</p>
	</header>

	{#if error}
		<div class="border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
			{error}
		</div>
	{/if}

	<div class="grid grid-cols-1 gap-8 lg:grid-cols-2">
		<section class="flex flex-col gap-5 border border-slate-200 bg-white p-8">
			<h2 class="text-xs font-black tracking-widest text-institutional-navy uppercase">Export</h2>
			<fieldset class="flex flex-col gap-3">
				<legend class="sr-only">Bereiche</legend>
				<label class="flex items-center gap-3 text-sm font-bold">
					<input
						type="checkbox"
						checked={allSelected}
						onchange={() => (selected = allSelected ? [] : sections.map((s) => s.key))}
					/> Alles
				</label>
				{#each sections as section (section.key)}
					<label class="flex items-center gap-3 pl-6 text-sm">
						<input type="checkbox" value={section.key} bind:group={selected} />
						{section.label}
					</label>
				{/each}
			</fieldset>
			<a
				class="ed-button self-start bg-institutional-navy text-white {selected.length
					? ''
					: 'pointer-events-none opacity-30'}"
				href={exportUrl(allSelected ? [] : selected)}
				download
				aria-disabled={!selected.length}>Exportieren (ZIP)</a
			>
			<p class="text-xs text-slate-400">
				Verwendete Bilder werden mitgepackt. Einzelne Seiten, Artikel oder Lernpfade lassen sich
				auch direkt in ihren Editoren exportieren.
			</p>
		</section>

		<section class="flex flex-col gap-5 border border-slate-200 bg-white p-8">
			<h2 class="text-xs font-black tracking-widest text-institutional-navy uppercase">Import</h2>
			<label
				class="flex min-h-48 cursor-pointer flex-col items-center justify-center gap-3 border-2 border-dashed p-8 text-center transition-colors {dragging
					? 'border-institutional-navy bg-slate-50'
					: 'border-slate-300'}"
				ondragover={(e) => {
					e.preventDefault();
					dragging = true;
				}}
				ondragleave={() => (dragging = false)}
				ondrop={(e) => {
					e.preventDefault();
					dragging = false;
					upload(e.dataTransfer?.files?.[0]);
				}}
			>
				<span class="text-3xl">📦</span>
				<span class="text-sm font-bold text-institutional-navy"
					>{busy ? 'Wird importiert …' : 'Datei hierher ziehen oder auswählen'}</span
				>
				<span class="text-xs text-slate-400"
					>CodePath-Export (.zip oder .json) oder Content-Paket (.zip)</span
				>
				<input
					type="file"
					accept=".zip,.json"
					class="sr-only"
					data-testid="transfer-file"
					disabled={busy}
					onchange={(e) => {
						const input = e.currentTarget as HTMLInputElement;
						upload(input.files?.[0]);
						input.value = '';
					}}
				/>
			</label>
			<p class="text-xs text-slate-400">
				Vorhandene Einträge mit gleicher Adresse (Slug) werden überarbeitet, geänderte Aufgaben als
				neue Version veröffentlicht. Nichts wird gelöscht.
			</p>
		</section>
	</div>

	{#if report}
		<TransferReportView {report} />
	{/if}
</div>
