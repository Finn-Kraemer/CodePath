<script lang="ts">
	import { onDestroy, onMount, untrack } from 'svelte';
	import { beforeNavigate } from '$app/navigation';
	import { page } from '$app/state';
	import { api, ApiError, type WorksheetDetail } from '$lib/api';
	import { auth } from '$lib/auth.svelte';
	import MediaPicker from '$lib/components/MediaPicker.svelte';
	import { mediaUrl } from '$lib/media';
	import WorksheetBlockForm from '$lib/worksheet/WorksheetBlockForm.svelte';
	import {
		BLOCK_TYPES,
		PRESETS,
		blockId,
		blockLabel,
		isTask,
		normalize,
		totalPoints,
		type BlockType,
		type WorksheetContent
	} from '$lib/worksheet/types';

	type Variant = 'student' | 'solution';

	const id = $derived(page.params.id);

	let title = $state('');
	let content = $state<WorksheetContent>({ settings: {}, blocks: [] });
	let saved = $state('');
	let loading = $state(true);
	let busy = $state(false);
	let message = $state('');
	let error = $state('');
	let selected = $state<string | null>(null);
	let adding = $state(false);
	let panel = $state<'blocks' | 'settings'>('blocks');

	let variant = $state<Variant>('student');
	let autoPreview = $state(true);
	let previewUrl = $state('');
	let previewError = $state('');
	let previewBusy = $state(false);
	let timer: ReturnType<typeof setTimeout> | undefined;

	const current = $derived(JSON.stringify({ title, content }));
	const dirty = $derived(!loading && current !== saved);
	const index = $derived(content.blocks.findIndex((b) => b.id === selected));
	const points = $derived(totalPoints(content.blocks));
	const groups = ['Aufgaben', 'Inhalt', 'Layout'] as const;
	const taskCount = $derived(content.blocks.filter(isTask).length);

	/** Laufende Nummer je Aufgabe für die Blockliste (wie im PDF). */
	const numbers = $derived.by(() => {
		const map: Record<string, number> = {};
		let n = 0;
		for (const block of content.blocks) if (isTask(block)) map[block.id] = ++n;
		return map;
	});

	onMount(async () => {
		try {
			const detail = await api<WorksheetDetail>(`/api/admin/worksheets/${id}`);
			title = detail.title;
			content = normalize(detail.content);
		} catch (e) {
			error = (e as Error).message;
		} finally {
			saved = JSON.stringify({ title, content });
			loading = false;
		}
	});

	onDestroy(() => {
		clearTimeout(timer);
		if (previewUrl) URL.revokeObjectURL(previewUrl);
	});

	beforeNavigate((navigation) => {
		if (dirty && !confirm('Ungespeicherte Änderungen verwerfen?')) navigation.cancel();
	});

	// Vorschau kurz nach der letzten Änderung neu erzeugen
	$effect(() => {
		const body = current;
		const v = variant;
		if (loading || !autoPreview) return;
		clearTimeout(timer);
		const delay = untrack(() => (previewUrl ? 900 : 0));
		timer = setTimeout(() => refreshPreview(body, v), delay);
	});

	async function refreshPreview(body = current, v = variant) {
		previewBusy = true;
		try {
			const res = await auth.apiFetch(`/api/admin/worksheets/preview?variant=${v}`, {
				method: 'POST',
				body
			});
			if (!res.ok) {
				const data = await res.json().catch(() => ({}));
				throw new ApiError(data?.message ?? data?.detail ?? `Fehler ${res.status}`, res.status);
			}
			const url = URL.createObjectURL(await res.blob());
			if (previewUrl) URL.revokeObjectURL(previewUrl);
			previewUrl = url;
			previewError = '';
		} catch (e) {
			previewError = (e as Error).message;
		} finally {
			previewBusy = false;
		}
	}

	function flash(text: string) {
		message = text;
		error = '';
		setTimeout(() => (message = ''), 3000);
	}

	async function save() {
		busy = true;
		error = '';
		try {
			const snapshot = current;
			await api(`/api/admin/worksheets/${id}`, { method: 'PUT', body: JSON.parse(snapshot) });
			saved = snapshot;
			flash('Gespeichert.');
			return true;
		} catch (e) {
			error = (e as Error).message;
			return false;
		} finally {
			busy = false;
		}
	}

	/** Speichert bei Bedarf und lädt dann das PDF herunter. */
	async function download(v: Variant) {
		if (dirty && !(await save())) return;
		const link = document.createElement('a');
		link.href = `/api/admin/worksheets/${id}/pdf?variant=${v}&download=true`;
		link.click();
	}

	function add(type: BlockType) {
		const block = BLOCK_TYPES[type].create();
		block.show = 'both';
		const at = index >= 0 ? index + 1 : content.blocks.length;
		content.blocks.splice(at, 0, block);
		selected = block.id;
		adding = false;
	}

	function move(i: number, delta: number) {
		const j = i + delta;
		if (j < 0 || j >= content.blocks.length) return;
		const blocks = content.blocks;
		[blocks[i], blocks[j]] = [blocks[j], blocks[i]];
	}

	function duplicate(i: number) {
		const copy = { ...structuredClone($state.snapshot(content.blocks[i])), id: blockId() };
		content.blocks.splice(i + 1, 0, copy);
		selected = copy.id;
	}

	function remove(i: number) {
		const block = content.blocks[i];
		if (!confirm(`Block „${BLOCK_TYPES[block.type].label}“ löschen?`)) return;
		content.blocks.splice(i, 1);
		if (selected === block.id) selected = null;
	}

	function select(blockId: string) {
		selected = selected === blockId ? null : blockId;
		adding = false;
	}

	function applyPreset(settings: Partial<WorksheetContent['settings']>) {
		Object.assign(content.settings, settings);
	}

	function today() {
		content.settings.date = new Date().toLocaleDateString('de-DE');
	}
</script>

<svelte:window
	onbeforeunload={(e) => {
		if (dirty) e.preventDefault();
	}}
	onkeydown={(e) => {
		if ((e.ctrlKey || e.metaKey) && e.key === 's') {
			e.preventDefault();
			if (dirty && !busy) save();
		}
	}}
/>

<svelte:head><title>{title || 'Aufgabenblatt'} | CodePath</title></svelte:head>

<div class="flex flex-col gap-6 pb-24">
	<header
		class="flex flex-col gap-6 border-b border-slate-200 pb-6 lg:flex-row lg:items-end lg:justify-between"
	>
		<div class="min-w-0 flex-1">
			<div class="mb-2 flex items-center gap-3 text-institutional-navy/40">
				<a href="/admin" class="transition-colors hover:text-institutional-gold">
					<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase"
						>Admin Dashboard</span
					>
				</a>
				<span class="text-slate-300">/</span>
				<a href="/admin/worksheets" class="transition-colors hover:text-institutional-gold">
					<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase"
						>Aufgabenblätter</span
					>
				</a>
			</div>
			<label class="sr-only" for="worksheet-title">Titel</label>
			<input
				id="worksheet-title"
				class="w-full max-w-3xl border-0 border-b-2 border-transparent bg-transparent p-0 font-sans text-4xl font-black tracking-tighter text-institutional-navy focus:border-institutional-gold focus:ring-0"
				bind:value={title}
				maxlength="200"
				placeholder="Titel des Blatts"
			/>
			<p class="mt-1 font-mono text-[11px] text-slate-400">
				{taskCount}
				{taskCount === 1 ? 'Aufgabe' : 'Aufgaben'} · {points.toLocaleString('de-DE')} Punkte · {content
					.blocks.length} Blöcke
			</p>
		</div>
		<div class="flex flex-wrap items-center gap-3">
			<span class="font-mono text-[10px] tracking-widest text-slate-400 uppercase">
				{#if loading}Lädt …{:else if dirty}Ungespeicherte Änderungen{:else}Gespeichert{/if}
			</span>
			<button
				class="ed-button bg-white"
				onclick={save}
				disabled={busy || loading || !dirty || !title.trim()}
				title="Strg+S">Speichern</button
			>
			<button
				class="ed-button bg-white"
				onclick={() => download('solution')}
				disabled={busy || loading || !title.trim()}>PDF Lösung</button
			>
			<button
				class="bg-institutional-navy px-6 py-3 font-sans text-[11px] font-bold tracking-[3px] text-white uppercase shadow-lg transition-all hover:opacity-95 disabled:opacity-50"
				onclick={() => download('student')}
				disabled={busy || loading || !title.trim()}>PDF Schülerblatt</button
			>
		</div>
	</header>

	{#if message}
		<p class="text-[11px] font-bold tracking-widest text-teal-700 uppercase" role="status">
			{message}
		</p>
	{/if}
	{#if error}
		<p class="text-[11px] font-bold tracking-widest text-rose-700 uppercase" role="alert">
			{error}
		</p>
	{/if}

	<div class="grid grid-cols-1 gap-8 xl:grid-cols-[30rem_1fr]">
		<aside
			class="flex flex-col gap-4 xl:sticky xl:top-4 xl:max-h-[calc(100vh-2rem)] xl:self-start xl:overflow-y-auto"
		>
			<div class="flex border border-slate-200 bg-white" role="tablist">
				{#each [['blocks', 'Inhalt'], ['settings', 'Kopf & Layout']] as [key, label] (key)}
					<button
						type="button"
						role="tab"
						aria-selected={panel === key}
						class="flex-1 px-4 py-2 text-[11px] font-bold tracking-widest uppercase {panel === key
							? 'bg-institutional-navy text-white'
							: 'text-slate-500 hover:text-institutional-navy'}"
						onclick={() => (panel = key as 'blocks' | 'settings')}>{label}</button
					>
				{/each}
			</div>

			{#if panel === 'settings'}
				{@const s = content.settings}
				<section class="flex flex-col gap-4 border border-slate-200 bg-white p-4">
					<div>
						<p class="ed-label">Vorlage für den Kopf</p>
						<div class="mt-2 flex flex-wrap gap-2">
							{#each PRESETS as preset (preset.label)}
								<button type="button" class="ed-button" onclick={() => applyPreset(preset.settings)}
									>{preset.label}</button
								>
							{/each}
						</div>
					</div>
					<div class="grid grid-cols-2 gap-3">
						<label class="ed-label"
							>Art des Blatts
							<input class="ed-input" bind:value={s.kind} placeholder="Arbeitsblatt" />
						</label>
						<label class="ed-label"
							>Fach
							<input class="ed-input" bind:value={s.subject} placeholder="Informatik" />
						</label>
						<label class="ed-label col-span-2"
							>Schule / Kurs (oben links)
							<input class="ed-input" bind:value={s.school} />
						</label>
						<label class="ed-label col-span-2"
							>Untertitel
							<input class="ed-input" bind:value={s.subtitle} />
						</label>
						<label class="ed-label"
							>Klasse (vorausgefüllt)
							<input class="ed-input" bind:value={s.className} placeholder="leer = zum Ausfüllen" />
						</label>
						<label class="ed-label"
							>Datum (vorausgefüllt)
							<div class="flex gap-1">
								<input class="ed-input" bind:value={s.date} placeholder="leer = zum Ausfüllen" />
								<button type="button" class="ed-button mt-1 px-2" onclick={today} title="Heute"
									>Heute</button
								>
							</div>
						</label>
					</div>
					<label class="ed-label"
						>Arbeitsanweisung (grau hinterlegt unter dem Kopf)
						<textarea class="ed-input" rows="3" bind:value={s.instructions}></textarea>
					</label>
					<div>
						<p class="ed-label">Logo (oben links)</p>
						{#if s.logo}
							<div class="mt-2 flex items-center gap-3">
								<img src={mediaUrl(s.logo)} alt="" class="h-10 border border-slate-200" />
								<button
									type="button"
									class="ed-button text-rose-700"
									onclick={() => (content.settings.logo = '')}>Entfernen</button
								>
							</div>
						{/if}
						<MediaPicker
							label={s.logo ? 'Anderes Logo wählen' : 'Logo wählen'}
							onselect={(m) => (content.settings.logo = m.key)}
						/>
					</div>

					<fieldset class="flex flex-col gap-2 border-t border-slate-100 pt-4">
						<legend class="ed-label">Felder im Kopf</legend>
						<label class="flex items-center gap-2 text-sm text-slate-700">
							<input type="checkbox" bind:checked={s.nameField} /> Name
						</label>
						<label class="flex items-center gap-2 text-sm text-slate-700">
							<input type="checkbox" bind:checked={s.classField} /> Klasse
						</label>
						<label class="flex items-center gap-2 text-sm text-slate-700">
							<input type="checkbox" bind:checked={s.dateField} /> Datum
						</label>
						<label class="flex items-center gap-2 text-sm text-slate-700">
							<input type="checkbox" bind:checked={s.pointsField} /> Punkte (erreicht / gesamt)
						</label>
						<label class="flex items-center gap-2 text-sm text-slate-700">
							<input type="checkbox" bind:checked={s.gradeField} /> Note
						</label>
					</fieldset>

					<fieldset class="flex flex-col gap-2 border-t border-slate-100 pt-4">
						<legend class="ed-label">Aufgaben</legend>
						<label class="flex items-center gap-2 text-sm text-slate-700">
							<input type="checkbox" bind:checked={s.numbering} /> Aufgaben nummerieren
						</label>
						{#if s.numbering}
							<label class="ed-label"
								>Bezeichnung
								<input class="ed-input" bind:value={s.numberLabel} placeholder="Aufgabe" />
							</label>
						{/if}
						<label class="flex items-center gap-2 text-sm text-slate-700">
							<input type="checkbox" bind:checked={s.showPoints} /> Punkte je Aufgabe anzeigen
						</label>
					</fieldset>

					<fieldset class="grid grid-cols-2 gap-3 border-t border-slate-100 pt-4">
						<legend class="ed-label">Layout</legend>
						<label class="ed-label"
							>Papier
							<select class="ed-input" bind:value={s.pageSize}>
								<option value="A4">A4</option>
								<option value="A5">A5</option>
								<option value="LETTER">US Letter</option>
							</select>
						</label>
						<label class="ed-label"
							>Ausrichtung
							<select class="ed-input" bind:value={s.orientation}>
								<option value="portrait">Hochformat</option>
								<option value="landscape">Querformat</option>
							</select>
						</label>
						<label class="ed-label"
							>Ränder
							<select class="ed-input" bind:value={s.margins}>
								<option value="narrow">Schmal</option>
								<option value="normal">Normal</option>
								<option value="wide">Breit (Lochung, Korrekturrand)</option>
							</select>
						</label>
						<label class="ed-label"
							>Schrift
							<select class="ed-input" bind:value={s.font}>
								<option value="sans">Serifenlos (Helvetica)</option>
								<option value="serif">Serif (Times)</option>
							</select>
						</label>
						<label class="ed-label"
							>Schriftgröße: {s.fontSize} pt
							<input class="mt-2 w-full" type="range" min="9" max="14" bind:value={s.fontSize} />
						</label>
						<label class="ed-label"
							>Linienabstand: {s.lineSpacing} mm
							<input class="mt-2 w-full" type="range" min="6" max="14" bind:value={s.lineSpacing} />
						</label>
						<label class="ed-label"
							>Akzentfarbe
							<input class="ed-input h-10 p-1" type="color" bind:value={s.accent} />
						</label>
						<label class="flex items-center gap-2 self-end pb-2 text-sm text-slate-700">
							<input type="checkbox" bind:checked={s.pageNumbers} /> Seitenzahlen
						</label>
						<label class="ed-label col-span-2"
							>Fußzeile
							<input
								class="ed-input"
								bind:value={s.footer}
								placeholder="z. B. Abgabe bis Freitag"
							/>
						</label>
					</fieldset>
				</section>
			{:else}
				<ol class="flex flex-col gap-2">
					{#each content.blocks as block, i (block.id)}
						<li
							class="border bg-white {selected === block.id
								? 'border-institutional-navy'
								: 'border-slate-200'}"
						>
							<div class="flex items-center gap-2 p-2">
								<button
									type="button"
									class="min-w-0 flex-1 text-left"
									onclick={() => select(block.id)}
									aria-expanded={selected === block.id}
								>
									<span class="block font-mono text-[9px] tracking-widest text-slate-400 uppercase"
										>{numbers[block.id] ? `${numbers[block.id]} · ` : ''}{BLOCK_TYPES[block.type]
											.label}{block.show === 'solution'
											? ' · nur Lösung'
											: block.show === 'student'
												? ' · nur Schülerblatt'
												: ''}</span
									>
									<span class="block truncate text-sm font-bold text-institutional-navy"
										>{blockLabel(block) || '—'}</span
									>
								</button>
								<button
									type="button"
									class="ed-button px-2"
									onclick={() => move(i, -1)}
									disabled={i === 0}
									aria-label="Nach oben"
									title="Nach oben">↑</button
								>
								<button
									type="button"
									class="ed-button px-2"
									onclick={() => move(i, 1)}
									disabled={i === content.blocks.length - 1}
									aria-label="Nach unten"
									title="Nach unten">↓</button
								>
							</div>
							{#if selected === block.id}
								<div class="border-t border-slate-100 p-4">
									<WorksheetBlockForm bind:block={content.blocks[i]} />
									<div class="mt-6 flex flex-wrap gap-2 border-t border-slate-100 pt-4">
										<button type="button" class="ed-button" onclick={() => duplicate(i)}
											>Duplizieren</button
										>
										<button type="button" class="ed-button text-rose-700" onclick={() => remove(i)}
											>Löschen</button
										>
									</div>
								</div>
							{/if}
						</li>
					{/each}
				</ol>

				{#if adding}
					<div class="border border-dashed border-slate-300 bg-white p-3">
						<p class="ed-label mb-2">
							Neuer Block {index >= 0 ? 'nach dem gewählten' : 'am Ende'}
						</p>
						{#each groups as group (group)}
							<p class="mt-3 mb-1 font-mono text-[9px] tracking-widest text-slate-400 uppercase">
								{group}
							</p>
							<div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
								{#each Object.entries(BLOCK_TYPES).filter(([, d]) => d.group === group) as [type, def] (type)}
									<button
										type="button"
										class="border border-slate-200 p-2 text-left hover:border-institutional-navy"
										onclick={() => add(type as BlockType)}
									>
										<span class="block text-sm font-bold text-institutional-navy">{def.label}</span>
										<span class="block text-xs text-slate-500">{def.description}</span>
									</button>
								{/each}
							</div>
						{/each}
						<button type="button" class="ed-button mt-3" onclick={() => (adding = false)}
							>Abbrechen</button
						>
					</div>
				{:else}
					<button
						type="button"
						class="border-2 border-dashed border-slate-300 p-3 text-[11px] font-bold tracking-widest text-slate-500 uppercase hover:border-institutional-navy hover:text-institutional-navy"
						onclick={() => (adding = true)}>+ Block hinzufügen</button
					>
				{/if}
			{/if}
		</aside>

		<section
			aria-label="Vorschau"
			class="flex min-w-0 flex-col gap-2 xl:sticky xl:top-4 xl:self-start"
		>
			<div class="flex flex-wrap items-center gap-3">
				<div class="flex border border-slate-200 bg-white">
					{#each [['student', 'Schülerblatt'], ['solution', 'Lösung']] as [key, label] (key)}
						<button
							type="button"
							class="px-4 py-1.5 text-[11px] font-bold tracking-widest uppercase {variant === key
								? 'bg-institutional-navy text-white'
								: 'text-slate-500 hover:text-institutional-navy'}"
							aria-pressed={variant === key}
							onclick={() => (variant = key as Variant)}>{label}</button
						>
					{/each}
				</div>
				<label class="flex items-center gap-2 text-xs text-slate-600">
					<input type="checkbox" bind:checked={autoPreview} /> automatisch aktualisieren
				</label>
				<button
					type="button"
					class="ed-button"
					onclick={() => refreshPreview()}
					disabled={previewBusy}>Aktualisieren</button
				>
				{#if previewBusy}<span class="text-xs text-slate-400">Erzeuge PDF …</span>{/if}
			</div>
			{#if previewError}
				<p class="text-xs text-rose-700" role="alert">Vorschau: {previewError}</p>
			{/if}
			{#if previewUrl}
				<iframe
					title="PDF-Vorschau"
					src={`${previewUrl}#view=FitH&toolbar=1`}
					class="h-[calc(100vh-7rem)] min-h-[32rem] w-full border border-slate-300 bg-slate-100"
				></iframe>
			{:else}
				<div
					class="flex h-[32rem] items-center justify-center border border-dashed border-slate-300 text-sm text-slate-400"
				>
					{loading ? 'Lädt …' : 'Vorschau wird erzeugt …'}
				</div>
			{/if}
		</section>
	</div>
</div>
