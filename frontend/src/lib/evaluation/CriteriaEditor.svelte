<script lang="ts">
	import EvaluationForm from './EvaluationForm.svelte';
	import {
		TYPE_LABELS,
		newCriterion,
		steps,
		type Answers,
		type Criterion,
		type CriterionType
	} from './evaluation';

	let { criteria = $bindable([]) }: { criteria: Criterion[] } = $props();

	let preview = $state(false);
	let previewAnswers = $state<Answers>({});
	/** Aufgeklapptes Kriterium (Objekt statt ID, damit das Ändern der ID es nicht zuklappt) */
	let open = $state<Criterion | null>(null);

	const types: CriterionType[] = ['SCALE', 'CHOICE', 'YES_NO', 'TEXT', 'HEADING'];

	function add(type: CriterionType) {
		const c = newCriterion(type, criteria);
		criteria = [...criteria, c];
		open = criteria[criteria.length - 1];
	}

	function move(index: number, delta: number) {
		const target = index + delta;
		if (target < 0 || target >= criteria.length) return;
		const next = [...criteria];
		[next[index], next[target]] = [next[target], next[index]];
		criteria = next;
	}

	function remove(index: number) {
		criteria = criteria.filter((_, i) => i !== index);
	}

	function duplicate(index: number) {
		const copy = JSON.parse(JSON.stringify(criteria[index])) as Criterion;
		copy.id = newCriterion(copy.type, criteria).id;
		criteria = [...criteria.slice(0, index + 1), copy, ...criteria.slice(index + 1)];
		open = criteria[index + 1];
	}

	/** Beschriftungen je Stufe an geänderte Skalen-Grenzen anpassen */
	function resizeLabels(c: Criterion) {
		if (!c.labels) return;
		const count = steps(c).length;
		c.labels = Array.from({ length: count }, (_, i) => c.labels?.[i] ?? '');
	}

	function toggleLabels(c: Criterion, on: boolean) {
		c.labels = on ? steps(c).map(() => '') : (undefined as unknown as string[]);
	}

	function setOptions(c: Criterion, text: string) {
		c.options = text.split('\n').map((o) => o.trimEnd());
	}

	function summary(c: Criterion): string {
		switch (c.type) {
			case 'SCALE':
				return `${c.style === 'STARS' ? 'Sterne' : 'Skala'} ${c.min ?? 1}–${c.max ?? 5}`;
			case 'CHOICE':
				return `${c.multiple ? 'Mehrfachauswahl' : 'Einfachauswahl'}, ${c.options?.filter((o) => o.trim()).length ?? 0} Optionen`;
			case 'TEXT':
				return `bis ${c.maxLength ?? 1000} Zeichen`;
			default:
				return TYPE_LABELS[c.type];
		}
	}
</script>

<div class="flex flex-col gap-3">
	<div class="flex flex-wrap items-center justify-between gap-2">
		<p class="text-sm text-slate-500">
			{criteria.filter((c) => c.type !== 'HEADING').length} Kriterien
		</p>
		<button type="button" class="ed-button" onclick={() => (preview = !preview)}>
			{preview ? 'Bearbeiten' : 'Vorschau'}
		</button>
	</div>

	{#if preview}
		<div class="border border-dashed border-slate-300 bg-slate-50 p-4">
			<p class="mb-3 font-mono text-[11px] font-bold tracking-wider text-slate-400 uppercase">
				So sieht der Bogen aus
			</p>
			<EvaluationForm {criteria} bind:answers={previewAnswers} />
		</div>
	{:else}
		<ol class="flex flex-col gap-2">
			{#each criteria as c, i (c)}
				<li class="border bg-white {open === c ? 'border-institutional-navy' : 'border-slate-200'}">
					<div class="flex flex-wrap items-center gap-2 p-3">
						<span
							class="bg-slate-100 px-2 py-1 font-mono text-[11px] font-bold tracking-wider text-slate-500 uppercase"
							>{TYPE_LABELS[c.type]}</span
						>
						<button
							type="button"
							class="min-w-0 flex-1 truncate text-left text-sm {c.type === 'HEADING'
								? 'font-black uppercase'
								: 'font-bold'} text-institutional-navy"
							onclick={() => (open = open === c ? null : c)}
							aria-expanded={open === c}
						>
							{c.label || '(ohne Beschriftung)'}
							{#if c.required}<span class="text-rose-600">*</span>{/if}
							<span class="ml-2 font-mono text-[11px] font-normal text-slate-400">{summary(c)}</span
							>
						</button>
						<div class="flex gap-1">
							<button
								type="button"
								class="ed-button px-2"
								aria-label="Nach oben"
								disabled={i === 0}
								onclick={() => move(i, -1)}>↑</button
							>
							<button
								type="button"
								class="ed-button px-2"
								aria-label="Nach unten"
								disabled={i === criteria.length - 1}
								onclick={() => move(i, 1)}>↓</button
							>
							<button
								type="button"
								class="ed-button px-2"
								aria-label="Kopieren"
								title="Kopieren"
								onclick={() => duplicate(i)}>⧉</button
							>
							<button
								type="button"
								class="ed-button border-rose-300 px-2 text-rose-700"
								aria-label="Entfernen"
								onclick={() => remove(i)}>✕</button
							>
						</div>
					</div>

					{#if open === c}
						<div class="grid grid-cols-1 gap-3 border-t border-slate-100 p-4 md:grid-cols-6">
							<label class="ed-label md:col-span-4"
								>{c.type === 'HEADING' ? 'Überschrift' : 'Frage / Aussage'}
								<input class="ed-input" bind:value={c.label} maxlength="300" /></label
							>
							<label class="ed-label md:col-span-2"
								>Typ
								<select
									class="ed-input"
									value={c.type}
									onchange={(e) => {
										const replacement = newCriterion(
											(e.currentTarget as HTMLSelectElement).value as CriterionType,
											criteria
										);
										criteria[i] = { ...replacement, id: c.id, label: c.label, help: c.help };
										open = criteria[i];
									}}
								>
									{#each types as t (t)}<option value={t}>{TYPE_LABELS[t]}</option>{/each}
								</select></label
							>
							<label class="ed-label md:col-span-4"
								>Erläuterung (optional)
								<input class="ed-input" bind:value={c.help} maxlength="1000" /></label
							>
							<label class="ed-label md:col-span-2" title="Gleiche IDs werden verglichen">
								ID
								<input class="ed-input mono" bind:value={c.id} maxlength="40" /></label
							>

							{#if c.type !== 'HEADING'}
								<label class="flex items-center gap-2 text-sm md:col-span-6">
									<input type="checkbox" bind:checked={c.required} /> Pflichtfrage
								</label>
							{/if}

							{#if c.type === 'SCALE'}
								<label class="ed-label md:col-span-1"
									>Von
									<input
										class="ed-input"
										type="number"
										min="0"
										max="9"
										bind:value={c.min}
										onchange={() => resizeLabels(c)}
									/></label
								>
								<label class="ed-label md:col-span-1"
									>Bis
									<input
										class="ed-input"
										type="number"
										min="1"
										max="10"
										bind:value={c.max}
										onchange={() => resizeLabels(c)}
									/></label
								>
								<label class="ed-label md:col-span-2"
									>Darstellung
									<select class="ed-input" bind:value={c.style}>
										<option value="NUMBERS">Zahlen / Beschriftungen</option>
										<option value="STARS">Sterne</option>
									</select></label
								>
								<label class="flex items-end gap-2 pb-2 text-sm md:col-span-2">
									<input
										type="checkbox"
										checked={!!c.labels}
										onchange={(e) => toggleLabels(c, (e.currentTarget as HTMLInputElement).checked)}
									/>
									Beschriftung je Stufe
								</label>
								{#if c.labels}
									<div class="grid grid-cols-2 gap-2 md:col-span-6 md:grid-cols-5">
										{#each steps(c) as step, s (step)}
											<label class="ed-label"
												>Stufe {step}
												<input class="ed-input" bind:value={c.labels[s]} maxlength="60" /></label
											>
										{/each}
									</div>
								{:else}
									<label class="ed-label md:col-span-3"
										>Beschriftung links ({c.min})
										<input
											class="ed-input"
											bind:value={c.minLabel}
											placeholder="z. B. trifft nicht zu"
											maxlength="60"
										/></label
									>
									<label class="ed-label md:col-span-3"
										>Beschriftung rechts ({c.max})
										<input
											class="ed-input"
											bind:value={c.maxLabel}
											placeholder="z. B. trifft voll zu"
											maxlength="60"
										/></label
									>
								{/if}
							{:else if c.type === 'CHOICE'}
								<label class="ed-label md:col-span-4"
									>Optionen (eine je Zeile)
									<textarea
										class="ed-input min-h-28"
										value={(c.options ?? []).join('\n')}
										oninput={(e) => setOptions(c, (e.currentTarget as HTMLTextAreaElement).value)}
									></textarea></label
								>
								<label class="flex items-start gap-2 pt-6 text-sm md:col-span-2">
									<input type="checkbox" bind:checked={c.multiple} /> Mehrere Antworten erlaubt
								</label>
							{:else if c.type === 'TEXT'}
								<label class="ed-label md:col-span-2"
									>Höchstens Zeichen
									<input
										class="ed-input"
										type="number"
										min="1"
										max="5000"
										bind:value={c.maxLength}
									/></label
								>
							{/if}
						</div>
					{/if}
				</li>
			{:else}
				<li class="border border-dashed border-slate-200 p-6 text-center text-sm text-slate-400">
					Noch keine Kriterien – unten hinzufügen.
				</li>
			{/each}
		</ol>

		<div class="flex flex-wrap items-center gap-2">
			<span class="font-mono text-[11px] font-bold tracking-wider text-slate-400 uppercase"
				>Hinzufügen:</span
			>
			{#each types as t (t)}
				<button type="button" class="ed-button" onclick={() => add(t)}>+ {TYPE_LABELS[t]}</button>
			{/each}
		</div>
	{/if}
</div>
