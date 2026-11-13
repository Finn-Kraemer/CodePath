<script lang="ts">
	import type { CriterionInput, CriterionView, ObservationOption } from './room';

	let {
		criterion = null,
		busy = false,
		onsave,
		oncancel,
		ondelete
	}: {
		/** null = neues Kriterium */
		criterion?: CriterionView | null;
		busy?: boolean;
		onsave: (input: CriterionInput) => void;
		oncancel: () => void;
		ondelete?: () => void;
	} = $props();

	// Formular startet mit den Werten des Kriteriums; die Komponente wird je Kriterium neu erzeugt
	// svelte-ignore state_referenced_locally
	const start = criterion;
	let name = $state(start?.name ?? '');
	let kind = $state<'NUMBER' | 'CHOICE'>(start?.kind ?? 'CHOICE');
	let min = $state<string>(start?.min != null ? String(start.min) : '');
	let max = $state<string>(start?.max != null ? String(start.max) : '');
	let options = $state<ObservationOption[]>(
		start?.options?.length
			? start.options.map((o) => ({ value: o.value, description: o.description ?? '' }))
			: [
					{ value: '1', description: '' },
					{ value: '2', description: '' }
				]
	);

	function number(value: string): number | null {
		const v = value.trim().replace(',', '.');
		return v === '' || Number.isNaN(Number(v)) ? null : Number(v);
	}

	function addOption() {
		const numeric = options.map((o) => Number(o.value)).filter((n) => Number.isFinite(n));
		const next = numeric.length ? String(Math.max(...numeric) + 1) : '';
		options = [...options, { value: next, description: '' }];
	}

	function moveOption(index: number, dir: -1 | 1) {
		const target = index + dir;
		if (target < 0 || target >= options.length) return;
		const copy = [...options];
		[copy[index], copy[target]] = [copy[target], copy[index]];
		options = copy;
	}

	function submit(e: SubmitEvent) {
		e.preventDefault();
		onsave({
			name: name.trim(),
			kind,
			min: kind === 'NUMBER' ? number(min) : null,
			max: kind === 'NUMBER' ? number(max) : null,
			options:
				kind === 'CHOICE'
					? options.map((o) => ({
							value: o.value.trim(),
							description: o.description?.trim() ?? ''
						}))
					: []
		});
	}
</script>

<form
	class="flex flex-col gap-4 border-2 border-institutional-navy bg-white p-5"
	onsubmit={submit}
	data-testid="criterion-form"
>
	<h3 class="font-bold text-institutional-navy">
		{criterion ? `Kriterium „${criterion.name}“ bearbeiten` : 'Neues Kriterium'}
	</h3>
	<div class="flex flex-wrap items-end gap-4">
		<label class="ed-label flex-1"
			>Name
			<input
				class="ed-input mt-1 w-full"
				bind:value={name}
				maxlength="100"
				required
				placeholder="z. B. Interesse am Beruf"
			/>
		</label>
		<fieldset class="flex border border-slate-200 text-sm">
			<legend class="sr-only">Werttyp</legend>
			<label
				class="cursor-pointer px-4 py-2 {kind === 'CHOICE'
					? 'bg-institutional-navy text-white'
					: ''}"
			>
				<input type="radio" class="sr-only" bind:group={kind} value="CHOICE" /> Auswahlliste
			</label>
			<label
				class="cursor-pointer px-4 py-2 {kind === 'NUMBER'
					? 'bg-institutional-navy text-white'
					: ''}"
			>
				<input type="radio" class="sr-only" bind:group={kind} value="NUMBER" /> Zahl
			</label>
		</fieldset>
	</div>

	{#if kind === 'NUMBER'}
		<div class="flex flex-wrap gap-4">
			<label class="ed-label"
				>Minimum (optional)
				<input
					class="ed-input mt-1 w-32"
					inputmode="decimal"
					bind:value={min}
					placeholder="z. B. 1"
				/>
			</label>
			<label class="ed-label"
				>Maximum (optional)
				<input
					class="ed-input mt-1 w-32"
					inputmode="decimal"
					bind:value={max}
					placeholder="z. B. 5"
				/>
			</label>
		</div>
	{:else}
		<div class="flex flex-col gap-2">
			<p class="text-xs text-slate-500">
				Je Option ein kurzer Wert (der steht in der Tabelle) und eine Beschreibung (als Hilfe beim
				Eintragen und im PDF).
			</p>
			{#each options as option, i (i)}
				<div class="flex items-center gap-2" data-testid="option-row">
					<input
						class="ed-input w-20"
						bind:value={option.value}
						maxlength="40"
						required
						aria-label="Wert der Option {i + 1}"
					/>
					<input
						class="ed-input flex-1"
						bind:value={option.description}
						maxlength="300"
						placeholder="Beschreibung, z. B. Neugier durch Fragen zum Beruf"
						aria-label="Beschreibung der Option {i + 1}"
					/>
					<button
						type="button"
						class="h-10 w-8 text-slate-500 hover:text-institutional-navy disabled:opacity-30"
						disabled={i === 0}
						aria-label="Option {i + 1} nach oben"
						onclick={() => moveOption(i, -1)}>↑</button
					>
					<button
						type="button"
						class="h-10 w-8 text-slate-500 hover:text-institutional-navy disabled:opacity-30"
						disabled={i === options.length - 1}
						aria-label="Option {i + 1} nach unten"
						onclick={() => moveOption(i, 1)}>↓</button
					>
					<button
						type="button"
						class="h-10 w-8 text-rose-600 disabled:opacity-30"
						disabled={options.length === 1}
						aria-label="Option {i + 1} entfernen"
						onclick={() => (options = options.filter((_, j) => j !== i))}>✕</button
					>
				</div>
			{/each}
			<button type="button" class="self-start text-sm underline" onclick={addOption}
				>+ Option hinzufügen</button
			>
		</div>
	{/if}

	<div class="flex flex-wrap items-center gap-3">
		<button
			class="bg-institutional-navy px-6 py-3 text-xs font-black tracking-[3px] text-white uppercase disabled:opacity-50"
			disabled={busy}>Speichern</button
		>
		<button type="button" class="text-sm underline" onclick={oncancel}>Abbrechen</button>
		{#if criterion && ondelete}
			<button type="button" class="ml-auto text-sm text-rose-600 underline" onclick={ondelete}
				>Kriterium löschen</button
			>
		{/if}
	</div>
	{#if criterion}
		<p class="text-xs text-slate-500">
			Werte, die nach einer Änderung nicht mehr passen (Option entfernt, Bereich enger), werden
			gelöscht.
		</p>
	{/if}
</form>
