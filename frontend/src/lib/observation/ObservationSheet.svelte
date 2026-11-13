<script lang="ts">
	import { onDestroy } from 'svelte';
	import { SvelteMap } from 'svelte/reactivity';
	import { api } from '$lib/api';
	import {
		criterionHint,
		optionLabel,
		seatsInOrder,
		type CriterionView,
		type PersonView,
		type RoomLayout
	} from './room';

	let {
		courseId,
		criteria,
		people,
		layout,
		editable,
		highlightId = null,
		onperson,
		oneditcriterion,
		onmovecriterion,
		onrename,
		ondelete
	}: {
		courseId: number;
		criteria: CriterionView[];
		/** schon sortiert */
		people: PersonView[];
		layout: RoomLayout;
		editable: boolean;
		highlightId?: number | null;
		onperson: (person: PersonView) => void;
		oneditcriterion: (criterion: CriterionView) => void;
		onmovecriterion: (criterion: CriterionView, dir: -1 | 1) => void;
		onrename: (person: PersonView) => void;
		ondelete: (person: PersonView) => void;
	} = $props();

	const NOTE_DELAY = 700;

	const seatLabels = $derived(new Map(seatsInOrder(layout).map((s) => [s.id, s.label])));
	/** Zustand je Zelle („personId:criterionId“ bzw. „personId:note“): speichert, gespeichert oder Fehlermeldung */
	let status = $state<Record<string, { state: 'saving' | 'saved' | 'error'; message?: string }>>(
		{}
	);
	/** Notizen, die gerade getippt und noch nicht bestätigt sind */
	let drafts = $state<Record<number, string>>({});
	const timers = new SvelteMap<number, ReturnType<typeof setTimeout>>();

	async function rate(person: PersonView, criterion: CriterionView, value: string) {
		const key = `${person.id}:${criterion.id}`;
		status[key] = { state: 'saving' };
		try {
			const updated = await api<PersonView>(
				`/api/admin/observations/${courseId}/people/${person.id}/ratings/${criterion.id}`,
				{ method: 'PUT', body: { value } }
			);
			onperson(updated);
			status[key] = { state: 'saved' };
		} catch (e) {
			status[key] = { state: 'error', message: (e as Error).message };
		}
	}

	function noteInput(person: PersonView, value: string) {
		drafts[person.id] = value;
		clearTimeout(timers.get(person.id));
		timers.set(
			person.id,
			setTimeout(() => saveNote(person), NOTE_DELAY)
		);
	}

	async function saveNote(person: PersonView) {
		clearTimeout(timers.get(person.id));
		timers.delete(person.id);
		const value = drafts[person.id];
		if (value === undefined) return;
		const key = `${person.id}:note`;
		status[key] = { state: 'saving' };
		try {
			const updated = await api<PersonView>(
				`/api/admin/observations/${courseId}/people/${person.id}/note`,
				{ method: 'PUT', body: { note: value } }
			);
			onperson(updated);
			// nur verwerfen, wenn inzwischen nicht weitergetippt wurde
			if (drafts[person.id] === value) delete drafts[person.id];
			status[key] = { state: 'saved' };
		} catch (e) {
			status[key] = { state: 'error', message: (e as Error).message };
		}
	}

	/** Ungespeicherte Notizen sofort sichern (Seitenwechsel, Schließen). */
	export function flush() {
		for (const p of people) if (timers.has(p.id)) saveNote(p);
	}

	export function pending(): boolean {
		return timers.size > 0;
	}

	onDestroy(flush);

	/** Enter springt wie in einer Tabellenkalkulation eine Zeile nach unten (Umschalt+Enter: nach oben bzw. Zeilenumbruch in Notizen). */
	function keydown(e: KeyboardEvent) {
		if (e.key !== 'Enter') return;
		const target = e.target as HTMLElement;
		const cell = target.dataset.cell;
		if (!cell) return;
		const isNote = target.tagName === 'TEXTAREA';
		if (isNote && e.shiftKey) return;
		e.preventDefault();
		const [row, col] = cell.split(':').map(Number);
		const next = document.querySelector<HTMLElement>(
			`[data-cell="${row + (e.shiftKey ? -1 : 1)}:${col}"]`
		);
		if (next) next.focus();
		else target.blur();
	}

	function cellClass(key: string) {
		const s = status[key];
		if (s?.state === 'error') return 'border-rose-500 bg-rose-50';
		if (s?.state === 'saving') return 'border-amber-400';
		return 'border-slate-200';
	}

	function selectedOption(criterion: CriterionView, person: PersonView) {
		const value = person.ratings[criterion.id];
		return criterion.options.find((o) => o.value === value);
	}
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div class="overflow-x-auto" onkeydown={keydown}>
	<table class="w-full border-collapse text-sm" data-testid="observation-sheet">
		<thead>
			<tr class="border-b-2 border-institutional-navy text-left">
				<th class="sticky left-0 z-10 min-w-44 bg-white py-2 pr-3 align-bottom">Name</th>
				{#each criteria as criterion, i (criterion.id)}
					<th class="min-w-36 px-2 py-2 align-bottom">
						<div class="flex items-center gap-1">
							<button
								type="button"
								class="text-left font-bold text-institutional-navy underline decoration-dotted underline-offset-4 hover:decoration-solid disabled:no-underline"
								title="{criterionHint(criterion)}\n\nKlicken zum Bearbeiten"
								disabled={!editable}
								onclick={() => oneditcriterion(criterion)}>{criterion.name}</button
							>
							{#if editable}
								<span class="ml-auto flex">
									<button
										type="button"
										class="h-7 w-6 text-slate-400 hover:text-institutional-navy disabled:opacity-20"
										disabled={i === 0}
										aria-label="{criterion.name} nach links"
										onclick={() => onmovecriterion(criterion, -1)}>←</button
									>
									<button
										type="button"
										class="h-7 w-6 text-slate-400 hover:text-institutional-navy disabled:opacity-20"
										disabled={i === criteria.length - 1}
										aria-label="{criterion.name} nach rechts"
										onclick={() => onmovecriterion(criterion, 1)}>→</button
									>
								</span>
							{/if}
						</div>
						<div class="text-[10px] font-normal text-slate-400">
							{criterion.kind === 'CHOICE'
								? `Auswahl · ${criterion.options.length} Optionen`
								: criterionHint(criterion)}
						</div>
					</th>
				{/each}
				<th class="min-w-64 px-2 py-2 align-bottom">Notizen</th>
			</tr>
		</thead>
		<tbody>
			{#each people as person, row (person.id)}
				{@const noteKey = `${person.id}:note`}
				<tr
					class="border-b border-slate-100 align-top transition-colors {highlightId === person.id
						? 'bg-amber-50'
						: ''}"
					data-person-id={person.id}
					data-testid="row-{person.username ?? person.name}"
				>
					<th
						scope="row"
						class="sticky left-0 z-10 py-2 pr-3 text-left font-normal {highlightId === person.id
							? 'bg-amber-50'
							: 'bg-white'}"
					>
						<div class="font-bold text-institutional-navy">{person.name}</div>
						<div class="flex flex-wrap items-center gap-x-2 text-[11px] text-slate-400">
							{#if person.username}
								<span>@{person.username}</span>
							{:else}
								<span class="text-amber-700">ohne Konto</span>
								{#if editable}
									<button type="button" class="underline" onclick={() => onrename(person)}
										>umbenennen</button
									>
									<button
										type="button"
										class="text-rose-600 underline"
										onclick={() => ondelete(person)}>entfernen</button
									>
								{/if}
							{/if}
							<span
								>· {person.seatId ? (seatLabels.get(person.seatId) ?? 'Platz') : 'kein Platz'}</span
							>
						</div>
					</th>
					{#each criteria as criterion, col (criterion.id)}
						{@const key = `${person.id}:${criterion.id}`}
						{@const s = status[key]}
						<td class="px-2 py-2">
							{#if criterion.kind === 'CHOICE'}
								{@const option = selectedOption(criterion, person)}
								<select
									class="h-11 w-full border bg-white px-2 text-sm {cellClass(key)}"
									value={person.ratings[criterion.id] ?? ''}
									disabled={!editable}
									data-cell="{row}:{col}"
									aria-label="{criterion.name} für {person.name}"
									title={option ? optionLabel(option) : criterionHint(criterion)}
									onchange={(e) => rate(person, criterion, e.currentTarget.value)}
								>
									<option value="">–</option>
									{#each criterion.options as o (o.value)}
										<option value={o.value}>{optionLabel(o)}</option>
									{/each}
								</select>
								{#if option?.description}
									<div class="mt-1 text-[11px] leading-tight text-slate-500">
										{option.description}
									</div>
								{/if}
							{:else}
								<input
									class="h-11 w-full border px-2 text-sm {cellClass(key)}"
									inputmode="decimal"
									value={person.ratings[criterion.id] ?? ''}
									disabled={!editable}
									data-cell="{row}:{col}"
									aria-label="{criterion.name} für {person.name}"
									placeholder={criterion.min != null || criterion.max != null
										? `${criterion.min ?? ''}–${criterion.max ?? ''}`
										: ''}
									title={criterionHint(criterion)}
									onchange={(e) => rate(person, criterion, e.currentTarget.value)}
								/>
							{/if}
							{#if s?.state === 'error'}
								<div class="mt-1 text-[11px] text-rose-700" role="alert">{s.message}</div>
							{/if}
						</td>
					{/each}
					<td class="px-2 py-2">
						<textarea
							class="min-h-11 w-full resize-y border px-2 py-2 text-sm {cellClass(noteKey)}"
							rows="1"
							value={drafts[person.id] ?? person.note ?? ''}
							disabled={!editable}
							maxlength="5000"
							data-cell="{row}:{criteria.length}"
							aria-label="Notizen zu {person.name}"
							placeholder="Beobachtung …"
							oninput={(e) => noteInput(person, e.currentTarget.value)}
							onblur={() => {
								if (timers.has(person.id)) saveNote(person);
							}}
						></textarea>
						<div class="h-4 text-[10px] text-slate-400" aria-live="polite">
							{#if status[noteKey]?.state === 'saving' || timers.has(person.id)}
								speichert …
							{:else if status[noteKey]?.state === 'saved'}
								gespeichert ✓
							{:else if status[noteKey]?.state === 'error'}
								<span class="text-rose-700">{status[noteKey]?.message}</span>
							{/if}
						</div>
					</td>
				</tr>
			{:else}
				<tr>
					<td colspan={criteria.length + 2} class="py-6 text-center text-slate-400">
						Noch niemand im Kurs. Schülerkonten erscheinen automatisch; Personen ohne Konto unten
						eintragen.
					</td>
				</tr>
			{/each}
		</tbody>
	</table>
</div>
