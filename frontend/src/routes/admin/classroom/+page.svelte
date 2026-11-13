<script lang="ts">
	import { onDestroy } from 'svelte';
	import { api, type Schemas } from '$lib/api';
	import CourseSelect from '$lib/components/CourseSelect.svelte';
	import { courseSelection } from '$lib/courses.svelte';
	import RoomCanvas from '$lib/observation/RoomCanvas.svelte';
	import ObservationSheet from '$lib/observation/ObservationSheet.svelte';
	import CriterionForm from '$lib/observation/CriterionForm.svelte';
	import { personDrag } from '$lib/observation/personDrag';
	import {
		MAX_SEATS_PER_SIDE,
		SIDES,
		SIDE_LABEL,
		itemLabel,
		newItem,
		seatCounts,
		seatsInOrder,
		sortPeople,
		uid,
		withSeatCounts,
		type BoardView,
		type CriterionInput,
		type CriterionView,
		type ItemKind,
		type PersonView,
		type RoomItem,
		type RoomLayout,
		type Side,
		type SortMode
	} from '$lib/observation/room';

	type TemplateSummary = Schemas['TemplateSummary'];
	type ImportResult = Schemas['ImportResult'];

	const SORT_KEY = 'codepath:observation-sort';
	const LAYOUT_DELAY = 600;

	let board = $state<BoardView | null>(null);
	let layout = $state<RoomLayout>({ items: [] });
	let mode = $state<'assign' | 'edit'>('assign');
	let selectedItemId = $state<string | null>(null);
	let selectedSeatId = $state<string | null>(null);
	let armedPersonId = $state<number | null>(null);
	let highlightId = $state<number | null>(null);
	let sortMode = $state<SortMode>(readSort());
	let criterionForm = $state<{ criterion: CriterionView | null } | null>(null);
	let newPersonName = $state('');
	let seatPersonId = $state<number | ''>('');
	let seatNewName = $state('');
	let templates = $state<TemplateSummary[]>([]);
	let templateSource = $state<number | ''>('');
	let applyRoom = $state(true);
	let applyCriteria = $state(true);
	let templateName = $state('');
	let perPerson = $state(false);
	let importResult = $state<ImportResult | null>(null);
	let layoutState = $state<'saved' | 'pending' | 'saving' | 'error'>('saved');
	let error = $state('');
	let busy = $state(false);
	let sheet = $state<ReturnType<typeof ObservationSheet> | null>(null);

	let layoutTimer: ReturnType<typeof setTimeout> | undefined;
	let layoutVersion = 0;

	const courseId = $derived(board?.courseId ?? null);
	const editable = $derived(board?.editable ?? false);
	const people = $derived(board ? sortPeople(board.people, layout, sortMode) : []);
	const unseated = $derived(
		(board?.people ?? [])
			.filter((p) => !p.seatId)
			.sort((a, b) => a.name.localeCompare(b.name, 'de'))
	);
	const seats = $derived(seatsInOrder(layout));
	const seatLabel = $derived(new Map(seats.map((s) => [s.id, s.label])));
	const selectedItem = $derived(layout.items.find((i) => i.id === selectedItemId) ?? null);
	const occupant = $derived(
		selectedSeatId ? (board?.people.find((p) => p.seatId === selectedSeatId) ?? null) : null
	);
	const sources = $derived(templates.filter((t) => t.courseId !== courseId));

	function readSort(): SortMode {
		try {
			return localStorage.getItem(SORT_KEY) === 'name' ? 'name' : 'seat';
		} catch {
			return 'seat';
		}
	}

	function setSort(value: SortMode) {
		sortMode = value;
		try {
			localStorage.setItem(SORT_KEY, value);
		} catch {
			// kein Speicher
		}
	}

	async function run<T>(work: () => Promise<T>): Promise<T | undefined> {
		error = '';
		busy = true;
		try {
			return await work();
		} catch (e) {
			error = (e as Error).message;
			return undefined;
		} finally {
			busy = false;
		}
	}

	function show(view: BoardView, resetLayout = true) {
		board = view;
		if (resetLayout) {
			layout = view.layout;
			if (selectedItemId && !layout.items.some((i) => i.id === selectedItemId))
				selectedItemId = null;
		}
		if (selectedSeatId && !seatsInOrder(layout).some((s) => s.id === selectedSeatId))
			selectedSeatId = null;
	}

	async function load() {
		sheet?.flush();
		await flushLayout();
		importResult = null;
		criterionForm = null;
		highlightId = null;
		selectedSeatId = null;
		armedPersonId = null;
		const id = courseSelection.selectedId;
		if (id == null) return;
		// Öffnen legt den Bogen beim ersten Mal an und ergänzt neue Kursmitglieder (daher POST)
		const view = await run(() =>
			api<BoardView>(`/api/admin/observations/${id}/open`, { method: 'POST' })
		);
		if (view) {
			show(view);
			mode = view.layout.items.length === 0 && view.editable ? 'edit' : 'assign';
		}
		loadTemplates();
	}

	async function loadTemplates() {
		try {
			templates = await api<TemplateSummary[]>('/api/admin/observations/templates');
		} catch {
			templates = [];
		}
	}

	// ------------------------------------------------------------------ Raum

	function changeLayout(next: RoomLayout) {
		layout = next;
		layoutVersion++;
		layoutState = 'pending';
		clearTimeout(layoutTimer);
		layoutTimer = setTimeout(saveLayout, LAYOUT_DELAY);
	}

	async function saveLayout() {
		clearTimeout(layoutTimer);
		layoutTimer = undefined;
		if (courseId == null) return;
		const version = layoutVersion;
		layoutState = 'saving';
		try {
			const view = await api<BoardView>(`/api/admin/observations/${courseId}/layout`, {
				method: 'PUT',
				body: layout
			});
			// Wurde inzwischen weiter verschoben, bleibt der lokale Stand maßgeblich
			show(view, version === layoutVersion);
			layoutState = layoutTimer ? 'pending' : 'saved';
		} catch (e) {
			layoutState = 'error';
			error = (e as Error).message;
		}
	}

	async function flushLayout() {
		if (layoutTimer) await saveLayout();
	}

	function add(
		kind: ItemKind,
		seatsPerSide: Partial<Record<Side, number>> = {},
		size?: Partial<RoomItem>
	) {
		const offset = (layout.items.length % 6) * 30;
		let item = newItem(kind, seatsPerSide, { x: 600 + offset, y: 380 + offset });
		if (size) item = { ...item, ...size };
		changeLayout({ items: [...layout.items, item] });
		selectedItemId = item.id;
	}

	function patchItem(change: Partial<RoomItem>) {
		if (!selectedItem) return;
		const id = selectedItem.id;
		changeLayout({ items: layout.items.map((i) => (i.id === id ? { ...i, ...change } : i)) });
	}

	function setSeats(side: Side, count: number) {
		if (!selectedItem) return;
		const id = selectedItem.id;
		changeLayout({
			items: layout.items.map((i) => (i.id === id ? withSeatCounts(i, { [side]: count }) : i))
		});
	}

	function rotate(by: number) {
		if (!selectedItem) return;
		patchItem({ rotation: (((selectedItem.rotation + by) % 360) + 360) % 360 });
	}

	function duplicate() {
		if (!selectedItem) return;
		const copy: RoomItem = {
			...selectedItem,
			id: uid('t'),
			x: selectedItem.x + 30,
			y: selectedItem.y + 30,
			seats: selectedItem.seats.map((s) => ({ id: uid('s'), side: s.side }))
		};
		changeLayout({ items: [...layout.items, copy] });
		selectedItemId = copy.id;
	}

	function removeItem() {
		if (!selectedItem) return;
		const taken = board?.people.filter((p) => selectedItem.seats.some((s) => s.id === p.seatId));
		if (
			taken?.length &&
			!confirm(`${taken.length} Person(en) sitzen hier und verlieren ihren Platz. Löschen?`)
		)
			return;
		const id = selectedItem.id;
		changeLayout({ items: layout.items.filter((i) => i.id !== id) });
		selectedItemId = null;
	}

	// ------------------------------------------------------------------ Plätze

	async function assign(personId: number, seatId: string | null) {
		if (courseId == null) return;
		await flushLayout();
		const view = await run(() =>
			api<BoardView>(`/api/admin/observations/${courseId}/people/${personId}/seat`, {
				method: 'PUT',
				body: { seatId }
			})
		);
		if (view) show(view, false);
		armedPersonId = null;
		seatPersonId = '';
	}

	function seatClicked(seatId: string) {
		const person = board?.people.find((p) => p.seatId === seatId);
		if (!person && armedPersonId != null) {
			assign(armedPersonId, seatId);
			return;
		}
		selectedSeatId = selectedSeatId === seatId && !person ? null : seatId;
		if (person) jumpTo(person);
	}

	function jumpTo(person: PersonView) {
		highlightId = person.id;
		const row = document.querySelector<HTMLElement>(`[data-person-id="${person.id}"]`);
		row?.scrollIntoView({ behavior: 'smooth', block: 'center' });
		row?.querySelector<HTMLElement>('[data-cell]')?.focus({ preventScroll: true });
	}

	async function addPerson(name: string, seatId: string | null = null) {
		if (courseId == null || !name.trim()) return;
		const before = new Set(board?.people.map((p) => p.id));
		const view = await run(() =>
			api<BoardView>(`/api/admin/observations/${courseId}/people`, {
				method: 'POST',
				body: { name }
			})
		);
		if (!view) return;
		show(view, false);
		const created = view.people.find((p) => !before.has(p.id));
		if (seatId && created) await assign(created.id, seatId);
	}

	async function renamePerson(person: PersonView) {
		const name = prompt('Neuer Name', person.name);
		if (!name || name.trim() === person.name || courseId == null) return;
		const view = await run(() =>
			api<BoardView>(`/api/admin/observations/${courseId}/people/${person.id}`, {
				method: 'PUT',
				body: { name }
			})
		);
		if (view) show(view, false);
	}

	async function deletePerson(person: PersonView) {
		if (
			courseId == null ||
			!confirm(`„${person.name}“ mit allen Bewertungen und Notizen entfernen?`)
		)
			return;
		const view = await run(() =>
			api<BoardView>(`/api/admin/observations/${courseId}/people/${person.id}`, {
				method: 'DELETE'
			})
		);
		if (view) show(view, false);
	}

	function updatePerson(updated: PersonView) {
		if (!board) return;
		board.people = board.people.map((p) => (p.id === updated.id ? updated : p));
	}

	// ------------------------------------------------------------------ Kriterien

	async function saveCriterion(input: CriterionInput) {
		if (courseId == null || !criterionForm) return;
		const existing = criterionForm.criterion;
		const view = await run(() =>
			api<BoardView>(
				`/api/admin/observations/${courseId}/criteria${existing ? `/${existing.id}` : ''}`,
				{ method: existing ? 'PUT' : 'POST', body: input }
			)
		);
		if (view) {
			show(view, false);
			criterionForm = null;
		}
	}

	async function deleteCriterion() {
		const criterion = criterionForm?.criterion;
		if (!criterion || courseId == null) return;
		if (!confirm(`Kriterium „${criterion.name}“ mit allen eingetragenen Werten löschen?`)) return;
		const view = await run(() =>
			api<BoardView>(`/api/admin/observations/${courseId}/criteria/${criterion.id}`, {
				method: 'DELETE'
			})
		);
		if (view) {
			show(view, false);
			criterionForm = null;
		}
	}

	async function moveCriterion(criterion: CriterionView, dir: -1 | 1) {
		if (!board || courseId == null) return;
		const ids = board.criteria.map((c) => c.id);
		const i = ids.indexOf(criterion.id);
		const j = i + dir;
		if (j < 0 || j >= ids.length) return;
		[ids[i], ids[j]] = [ids[j], ids[i]];
		const view = await run(() =>
			api<BoardView>(`/api/admin/observations/${courseId}/criteria-order`, {
				method: 'PUT',
				body: { ids }
			})
		);
		if (view) show(view, false);
	}

	// ------------------------------------------------------------------ Vorlagen, Import

	async function applyTemplate() {
		if (courseId == null || templateSource === '') return;
		if (
			applyRoom &&
			layout.items.length > 0 &&
			!confirm('Den aktuellen Raum ersetzen? Plätze, die es dann nicht mehr gibt, werden frei.')
		)
			return;
		await flushLayout();
		const view = await run(() =>
			api<BoardView>(`/api/admin/observations/${courseId}/apply`, {
				method: 'POST',
				body: { sourceId: templateSource, room: applyRoom, criteria: applyCriteria }
			})
		);
		if (view) show(view);
	}

	async function saveTemplate() {
		if (courseId == null || !templateName.trim()) return;
		await flushLayout();
		const saved = await run(() =>
			api<TemplateSummary>(`/api/admin/observations/${courseId}/templates`, {
				method: 'POST',
				body: { name: templateName }
			})
		);
		if (saved) {
			templateName = '';
			await loadTemplates();
			templateSource = saved.id;
		}
	}

	async function deleteTemplate() {
		const t = templates.find((x) => x.id === templateSource);
		if (!t || t.courseId != null || !confirm(`Vorlage „${t.name}“ löschen?`)) return;
		await run(() => api(`/api/admin/observations/templates/${t.id}`, { method: 'DELETE' }));
		templateSource = '';
		await loadTemplates();
	}

	async function importFile(e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		const file = input.files?.[0];
		input.value = '';
		if (!file || courseId == null) return;
		await flushLayout();
		sheet?.flush();
		const result = await run(async () => {
			let data: unknown;
			try {
				data = JSON.parse(await file.text());
			} catch {
				throw new Error('Die Datei ist kein gültiges JSON.');
			}
			return api<ImportResult>(`/api/admin/observations/${courseId}/import`, {
				method: 'POST',
				body: data
			});
		});
		if (result) {
			importResult = result;
			show(result.board);
		}
	}

	function beforeUnload(e: BeforeUnloadEvent) {
		if (layoutTimer || sheet?.pending()) {
			sheet?.flush();
			if (layoutTimer) saveLayout();
			e.preventDefault();
		}
	}

	onDestroy(() => {
		if (layoutTimer) saveLayout();
	});

	const presets: { label: string; add: () => void }[] = [
		{ label: 'Einzeltisch', add: () => add('TABLE', { BOTTOM: 1 }) },
		{ label: 'Zweiertisch', add: () => add('TABLE', { BOTTOM: 2 }) },
		{
			label: 'Gruppentisch (4)',
			add: () => add('TABLE', { TOP: 2, BOTTOM: 2 }, { width: 170, height: 90 })
		},
		{ label: 'Computerreihe (4)', add: () => add('TABLE', { BOTTOM: 4 }) },
		{ label: 'Lehrerpult', add: () => add('TEACHER') },
		{ label: 'Tafel', add: () => add('BOARD') }
	];
</script>

<svelte:head><title>Klassenraum | CodePath</title></svelte:head>
<svelte:window onbeforeunload={beforeUnload} />

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
					Klassenraum
				</h1>
				<p class="mt-4 max-w-2xl text-slate-500">
					Sitzplan anlegen und im Unterricht beobachten: Bewertungen und Notizen werden sofort
					gespeichert. Nur Lehrkräfte sehen diese Seite; beim Beenden des Kurses wird alles
					gelöscht.
				</p>
			</div>
			<CourseSelect onchange={load} />
		</div>
	</header>

	{#if error}<div class="border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
			{error}
		</div>{/if}

	{#if board}
		{#if !editable}
			<div class="border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
				Der Kurs ist beendet – der Bogen lässt sich nur noch ansehen.
			</div>
		{/if}

		<!-- ------------------------------------------------------------ Raum -->
		<section
			class="flex flex-col gap-4 rounded-[var(--radius-ui)] border border-slate-200 bg-white p-4 sm:p-6"
		>
			<div class="flex flex-wrap items-center justify-between gap-3">
				<div class="flex border border-slate-200 text-sm" role="tablist">
					<button
						role="tab"
						aria-selected={mode === 'assign'}
						class="px-4 py-2 {mode === 'assign' ? 'bg-institutional-navy text-white' : ''}"
						onclick={() => {
							mode = 'assign';
							selectedItemId = null;
						}}>Sitzplan</button
					>
					<button
						role="tab"
						aria-selected={mode === 'edit'}
						disabled={!editable}
						class="px-4 py-2 disabled:opacity-40 {mode === 'edit'
							? 'bg-institutional-navy text-white'
							: ''}"
						onclick={() => {
							mode = 'edit';
							selectedSeatId = null;
							armedPersonId = null;
						}}>Raum bearbeiten</button
					>
				</div>
				<span class="text-xs text-slate-500" aria-live="polite" data-testid="layout-state">
					{#if layoutState === 'saving' || layoutState === 'pending'}Raum wird gespeichert …{:else if layoutState === 'error'}<span
							class="text-rose-700">Raum nicht gespeichert</span
						>{:else}{layout.items.filter((i) => i.kind === 'TABLE').length} Tische · {seats.length} Plätze
						· {board.people.length - unseated.length} belegt{/if}
				</span>
			</div>

			{#if mode === 'edit'}
				<div class="flex flex-wrap gap-2" aria-label="Hinzufügen">
					{#each presets as preset (preset.label)}
						<button
							type="button"
							class="h-10 rounded-[var(--radius-ui-sm)] border border-slate-300 px-3 text-sm hover:border-institutional-navy"
							onclick={preset.add}>+ {preset.label}</button
						>
					{/each}
				</div>
			{/if}

			<div class="grid gap-4 lg:grid-cols-[1fr_19rem]">
				<RoomCanvas
					{layout}
					people={board.people}
					{mode}
					bind:selectedItemId
					{selectedSeatId}
					{armedPersonId}
					onchange={changeLayout}
					onseat={seatClicked}
					onassign={assign}
				/>

				<aside class="flex min-w-0 flex-col gap-4 text-sm">
					{#if mode === 'edit'}
						{#if selectedItem}
							<div class="flex flex-col gap-3 border border-slate-200 p-4" data-testid="item-panel">
								<h2 class="font-bold text-institutional-navy">{itemLabel(layout, selectedItem)}</h2>
								<label class="ed-label"
									>Bezeichnung
									<input
										class="ed-input mt-1 w-full"
										maxlength="40"
										value={selectedItem.label ?? ''}
										placeholder={itemLabel(layout, { ...selectedItem, label: '' })}
										oninput={(e) => patchItem({ label: e.currentTarget.value })}
									/>
								</label>
								<div class="flex gap-2">
									<label class="ed-label min-w-0 flex-1 basis-48"
										>Breite
										<input
											type="number"
											min="20"
											max="800"
											step="10"
											class="ed-input mt-1 w-full"
											value={selectedItem.width}
											onchange={(e) => patchItem({ width: Number(e.currentTarget.value) || 20 })}
										/>
									</label>
									<label class="ed-label min-w-0 flex-1 basis-48"
										>Tiefe
										<input
											type="number"
											min="10"
											max="600"
											step="10"
											class="ed-input mt-1 w-full"
											value={selectedItem.height}
											onchange={(e) => patchItem({ height: Number(e.currentTarget.value) || 10 })}
										/>
									</label>
								</div>
								<div>
									<span class="ed-label">Drehung: {selectedItem.rotation}°</span>
									<div class="mt-1 flex gap-1">
										<button
											type="button"
											class="h-10 flex-1 border border-slate-300"
											onclick={() => rotate(-15)}
											aria-label="15 Grad gegen den Uhrzeigersinn">⟲ 15°</button
										>
										<button
											type="button"
											class="h-10 flex-1 border border-slate-300"
											onclick={() => rotate(15)}
											aria-label="15 Grad im Uhrzeigersinn">⟳ 15°</button
										>
										<button
											type="button"
											class="h-10 flex-1 border border-slate-300"
											onclick={() => rotate(90)}
											aria-label="90 Grad drehen">⟳ 90°</button
										>
									</div>
								</div>
								{#if selectedItem.kind === 'TABLE'}
									{@const counts = seatCounts(selectedItem)}
									<fieldset>
										<legend class="ed-label">Sitzplätze je Seite</legend>
										<div class="mt-1 grid grid-cols-2 gap-2">
											{#each SIDES as side (side)}
												<label class="flex items-center gap-2"
													><span class="w-12 text-xs text-slate-500">{SIDE_LABEL[side]}</span>
													<input
														type="number"
														min="0"
														max={MAX_SEATS_PER_SIDE}
														class="ed-input w-full"
														value={counts[side]}
														aria-label="Plätze {SIDE_LABEL[side]}"
														onchange={(e) =>
															setSeats(side, Math.trunc(Number(e.currentTarget.value) || 0))}
													/></label
												>
											{/each}
										</div>
									</fieldset>
								{/if}
								<div class="flex gap-3">
									<button type="button" class="underline" onclick={duplicate}>Duplizieren</button>
									<button type="button" class="ml-auto text-rose-600 underline" onclick={removeItem}
										>Löschen</button
									>
								</div>
							</div>
						{:else}
							<p class="border border-dashed border-slate-300 p-4 text-slate-500">
								Element anklicken, um es zu bearbeiten. Ziehen verschiebt (Raster 10). Mit der
								Tastatur: Tab zum Element, Pfeiltasten verschieben, <kbd>R</kbd> dreht,
								<kbd>Entf</kbd>
								löscht. Alles wird automatisch gespeichert.
							</p>
						{/if}
					{:else}
						{#if selectedSeatId}
							<div
								class="flex flex-col gap-3 border-2 border-amber-400 p-4"
								data-testid="seat-panel"
							>
								<h2 class="font-bold text-institutional-navy">
									{seatLabel.get(selectedSeatId) ?? 'Platz'}
								</h2>
								{#if occupant}
									<p>Hier sitzt <strong>{occupant.name}</strong>.</p>
									<div class="flex flex-wrap gap-3">
										<button type="button" class="underline" onclick={() => jumpTo(occupant)}
											>Zur Zeile</button
										>
										{#if editable}
											<button
												type="button"
												class="text-rose-600 underline"
												onclick={() => assign(occupant.id, null)}>Platz freigeben</button
											>
										{/if}
									</div>
								{:else if editable}
									<form
										class="flex flex-col gap-2"
										onsubmit={(e) => {
											e.preventDefault();
											if (seatPersonId !== '' && selectedSeatId)
												assign(seatPersonId, selectedSeatId);
										}}
									>
										<label class="ed-label"
											>Person zuweisen
											<select class="ed-input mt-1 w-full" bind:value={seatPersonId}>
												<option value="">– auswählen –</option>
												{#each unseated as p (p.id)}
													<option value={p.id}>{p.name}{p.username ? '' : ' (ohne Konto)'}</option>
												{/each}
												{#each board.people.filter((p) => p.seatId) as p (p.id)}
													<option value={p.id}
														>{p.name} (sitzt: {seatLabel.get(p.seatId!) ?? 'Platz'})</option
													>
												{/each}
											</select>
										</label>
										<button
											class="h-10 rounded-[var(--radius-ui-sm)] bg-institutional-navy px-4 text-sm font-semibold text-white disabled:opacity-50"
											disabled={seatPersonId === '' || busy}>Zuweisen</button
										>
									</form>
									<form
										class="flex flex-col gap-2 border-t border-slate-100 pt-3"
										onsubmit={async (e) => {
											e.preventDefault();
											await addPerson(seatNewName, selectedSeatId);
											seatNewName = '';
										}}
									>
										<label class="ed-label"
											>oder Name ohne Konto
											<input
												class="ed-input mt-1 w-full"
												bind:value={seatNewName}
												maxlength="100"
												placeholder="z. B. Gast aus der 9b"
											/>
										</label>
										<button
											class="h-10 rounded-[var(--radius-ui-sm)] border border-institutional-navy px-4 text-sm disabled:opacity-50"
											disabled={!seatNewName.trim() || busy}>Eintragen &amp; zuweisen</button
										>
									</form>
								{/if}
								<button
									type="button"
									class="self-start text-xs text-slate-500 underline"
									onclick={() => (selectedSeatId = null)}>schließen</button
								>
							</div>
						{/if}
						<div
							class="flex flex-col gap-2 border border-slate-200 p-4 transition-colors data-[drop-over]:border-emerald-500 data-[drop-over]:bg-emerald-50"
							data-unseat
							data-testid="unseated"
						>
							<h2 class="font-bold text-institutional-navy">Ohne Platz ({unseated.length})</h2>
							{#if editable}
								<p class="text-xs text-slate-500">
									Person auf einen Platz ziehen – oder erst die Person, dann den Platz antippen.
									Hierher ziehen gibt den Platz frei.
								</p>
							{/if}
							<div class="flex flex-wrap gap-2">
								{#each unseated as p (p.id)}
									<button
										type="button"
										class="min-h-10 touch-none border px-3 py-1 text-sm {armedPersonId === p.id
											? 'border-emerald-600 bg-emerald-600 text-white'
											: p.username
												? 'border-slate-300 bg-white'
												: 'border-amber-300 bg-amber-50'}"
										aria-pressed={armedPersonId === p.id}
										disabled={!editable}
										onpointerdown={(e) =>
											personDrag(e, p.name, (seatId) => {
												if (seatId) assign(p.id, seatId);
											})}
										onclick={() => (armedPersonId = armedPersonId === p.id ? null : p.id)}
										>{p.name}</button
									>
								{:else}
									<span class="text-xs text-slate-400">Alle haben einen Platz.</span>
								{/each}
							</div>
						</div>
					{/if}
				</aside>
			</div>
		</section>

		<!-- ------------------------------------------------------------ Beobachtungsbogen -->
		<section
			class="flex flex-col gap-4 rounded-[var(--radius-ui)] border border-slate-200 bg-white p-4 sm:p-6"
		>
			<div class="flex flex-wrap items-center justify-between gap-3">
				<h2 class="text-xl font-bold text-institutional-navy">Beobachtungsbogen</h2>
				<div class="flex flex-wrap items-center gap-3">
					<div class="flex border border-slate-200 text-xs" role="group" aria-label="Sortierung">
						<button
							class="px-3 py-2 {sortMode === 'seat' ? 'bg-institutional-navy text-white' : ''}"
							aria-pressed={sortMode === 'seat'}
							onclick={() => setSort('seat')}>nach Sitzplan</button
						>
						<button
							class="px-3 py-2 {sortMode === 'name' ? 'bg-institutional-navy text-white' : ''}"
							aria-pressed={sortMode === 'name'}
							onclick={() => setSort('name')}>alphabetisch</button
						>
					</div>
					{#if editable}
						<button
							class="h-10 rounded-[var(--radius-ui-sm)] bg-institutional-navy px-4 text-sm font-semibold text-white"
							onclick={() => (criterionForm = { criterion: null })}>+ Kriterium</button
						>
					{/if}
				</div>
			</div>

			{#if criterionForm}
				{#key criterionForm.criterion?.id ?? 'neu'}
					<CriterionForm
						criterion={criterionForm.criterion}
						{busy}
						onsave={saveCriterion}
						oncancel={() => (criterionForm = null)}
						ondelete={deleteCriterion}
					/>
				{/key}
			{/if}

			{#if board.criteria.length === 0 && editable && !criterionForm}
				<p class="border border-dashed border-slate-300 p-4 text-sm text-slate-500">
					Noch keine Kriterien. Lege z. B. „Interesse am Beruf“ als Auswahlliste an (1 = Neugier
					durch Fragen zum Beruf, 2 = wirkt motiviert …) oder „Mitarbeit“ als Zahl von 1 bis 5.
				</p>
			{/if}

			<ObservationSheet
				bind:this={sheet}
				courseId={board.courseId}
				criteria={board.criteria}
				{people}
				{layout}
				{editable}
				{highlightId}
				onperson={updatePerson}
				oneditcriterion={(c) => (criterionForm = { criterion: c })}
				onmovecriterion={moveCriterion}
				onrename={renamePerson}
				ondelete={deletePerson}
			/>

			{#if editable}
				<form
					class="flex flex-wrap items-end gap-3 border-t border-slate-100 pt-4"
					onsubmit={async (e) => {
						e.preventDefault();
						await addPerson(newPersonName);
						newPersonName = '';
					}}
				>
					<label class="ed-label"
						>Person ohne Konto
						<input
							class="ed-input mt-1 w-64 max-w-full"
							bind:value={newPersonName}
							maxlength="100"
							placeholder="Name"
							data-testid="new-person"
						/>
					</label>
					<button
						class="h-10 rounded-[var(--radius-ui-sm)] border border-institutional-navy px-4 text-sm disabled:opacity-50"
						disabled={!newPersonName.trim() || busy}>Hinzufügen</button
					>
					<p class="text-xs text-slate-400">
						Schülerkonten des Kurses stehen automatisch im Bogen.
					</p>
				</form>
			{/if}
		</section>

		<!-- ------------------------------------------------------------ Vorlagen, Export, Import -->
		<section
			class="grid gap-6 rounded-[var(--radius-ui)] border border-slate-200 bg-white p-4 sm:p-6 lg:grid-cols-2"
		>
			<div class="flex min-w-0 flex-col gap-3">
				<h2 class="font-bold text-institutional-navy">Vorlagen</h2>
				<p class="text-xs text-slate-500">
					Raum und Kriterien aus einer Vorlage oder einem anderen Kurs übernehmen – z. B. denselben
					Computerraum. Personen, Bewertungen und Notizen kommen nie mit.
				</p>
				{#if editable}
					<div class="flex flex-wrap items-end gap-2">
						<label class="ed-label min-w-0 flex-1 basis-48"
							>Quelle
							<select
								class="ed-input mt-1 w-full"
								bind:value={templateSource}
								data-testid="template-source"
							>
								<option value="">– auswählen –</option>
								{#each sources as t (t.id)}
									<option value={t.id}
										>{t.courseId == null ? 'Vorlage' : 'Kurs'}: {t.name} ({t.tables} Tische, {t.seats}
										Plätze, {t.criteria} Kriterien)</option
									>
								{/each}
							</select>
						</label>
						<button
							class="h-10 rounded-[var(--radius-ui-sm)] border border-institutional-navy px-4 text-sm disabled:opacity-50"
							disabled={templateSource === '' || (!applyRoom && !applyCriteria) || busy}
							onclick={applyTemplate}>Übernehmen</button
						>
					</div>
					<div class="flex flex-wrap gap-4 text-sm">
						<label class="flex items-center gap-2"
							><input type="checkbox" bind:checked={applyRoom} /> Raum (ersetzt den jetzigen)</label
						>
						<label class="flex items-center gap-2"
							><input type="checkbox" bind:checked={applyCriteria} /> Kriterien (ergänzt fehlende)</label
						>
						{#if templates.find((t) => t.id === templateSource)?.courseId === null}
							<button class="text-rose-600 underline" onclick={deleteTemplate}
								>Vorlage löschen</button
							>
						{/if}
					</div>
				{/if}
				<form
					class="flex flex-wrap items-end gap-2 border-t border-slate-100 pt-3"
					onsubmit={(e) => {
						e.preventDefault();
						saveTemplate();
					}}
				>
					<label class="ed-label min-w-0 flex-1 basis-48"
						>Diesen Raum mit Kriterien als Vorlage speichern
						<input
							class="ed-input mt-1 w-full"
							bind:value={templateName}
							maxlength="200"
							placeholder="z. B. Computerraum 1"
						/>
					</label>
					<button
						class="h-10 rounded-[var(--radius-ui-sm)] border border-institutional-navy px-4 text-sm disabled:opacity-50"
						disabled={!templateName.trim() || busy}>Speichern</button
					>
				</form>
				<p class="text-xs text-slate-400">
					Vorlagen stehen auch im <a class="underline" href="/admin/transfer">zentralen Export</a>
					(Bereich „Klassenraum-Vorlagen“).
				</p>
			</div>

			<div class="flex min-w-0 flex-col gap-3">
				<h2 class="font-bold text-institutional-navy">Export &amp; Import</h2>
				<div class="flex flex-wrap items-center gap-3">
					<a
						class="ed-button"
						href="/api/admin/observations/{board.courseId}/pdf?perPerson={perPerson}"
						data-testid="observation-pdf"
						download>PDF</a
					>
					<a
						class="ed-button"
						href="/api/admin/observations/{board.courseId}/pdf?perPerson={perPerson}&inline=true"
						target="_blank"
						rel="noopener">PDF ansehen</a
					>
					<label class="flex items-center gap-2 text-sm"
						><input type="checkbox" bind:checked={perPerson} /> mit einer Seite je Person</label
					>
				</div>
				<div class="flex flex-wrap items-center gap-3">
					<a class="ed-button" href="/api/admin/observations/{board.courseId}/export.csv" download
						>CSV (Tabelle)</a
					>
					<a
						class="ed-button"
						href="/api/admin/observations/{board.courseId}/export.json"
						data-testid="observation-json"
						download>JSON (alles)</a
					>
				</div>
				{#if editable}
					<label class="ed-label"
						>JSON importieren
						<input
							type="file"
							accept="application/json,.json"
							class="mt-1 block w-full max-w-full text-sm"
							onchange={importFile}
							data-testid="observation-import"
						/>
					</label>
					<p class="text-xs text-slate-400">
						Raum wird ersetzt, Kriterien über den Namen abgeglichen, Personen über den Benutzernamen
						(sonst als Name ohne Konto).
					</p>
				{/if}
				{#if importResult}
					<div class="border border-emerald-200 bg-emerald-50 p-3 text-sm" role="status">
						Importiert: {importResult.criteria} Kriterien, {importResult.people} Personen.
						{#if importResult.warnings.length}
							<ul class="mt-2 list-disc pl-5 text-amber-800">
								{#each importResult.warnings as w, i (i)}<li>{w}</li>{/each}
							</ul>
						{/if}
					</div>
				{/if}
				<p class="text-xs text-slate-500">
					Die Dateien enthalten personenbezogene Daten – nur sicher aufbewahren und nicht
					weitergeben.
				</p>
			</div>
		</section>
	{/if}
</div>
