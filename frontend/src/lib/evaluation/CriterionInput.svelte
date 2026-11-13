<script lang="ts">
	import { formatAnswer, stepLabel, steps, type Criterion } from './evaluation';

	let {
		criterion,
		value = $bindable(),
		readonly = false,
		compare,
		compareLabel = ''
	}: {
		criterion: Criterion;
		value?: unknown;
		readonly?: boolean;
		/** Zweiter Wert zum Vergleich, z. B. die Selbsteinschätzung neben der Lehrerbewertung */
		compare?: unknown;
		compareLabel?: string;
	} = $props();

	const c = $derived(criterion);
	const hasLabels = $derived(!!c.labels?.some((l) => l));
	const selected = $derived(Array.isArray(value) ? (value as string[]) : []);

	function setScale(step: number) {
		if (readonly) return;
		value = value === step && !c.required ? undefined : step;
	}

	function toggleOption(option: string) {
		if (readonly) return;
		if (c.multiple) {
			const next = selected.includes(option)
				? selected.filter((o) => o !== option)
				: [...selected, option];
			value = next.length ? next : undefined;
		} else {
			value = value === option && !c.required ? undefined : option;
		}
	}
</script>

{#if c.type === 'HEADING'}
	<div class="border-b-2 border-institutional-navy pt-4 pb-1">
		<h3 class="font-sans text-sm font-black tracking-wider text-institutional-navy uppercase">
			{c.label}
		</h3>
		{#if c.help}<p class="mt-1 text-sm text-slate-500">{c.help}</p>{/if}
	</div>
{:else}
	<fieldset
		class="rounded-[var(--radius-ui)] border border-slate-200 bg-white p-5"
		data-criterion={c.id}
	>
		<legend class="sr-only">{c.label}</legend>
		<p class="font-bold text-institutional-navy" aria-hidden="true">
			{c.label}{#if c.required && !readonly}<span class="text-rose-600" title="Pflichtfrage">
					*</span
				>{/if}
		</p>
		{#if c.help}<p class="mt-1 text-sm text-slate-500">{c.help}</p>{/if}

		<div class="mt-3">
			{#if c.type === 'SCALE'}
				{#if c.style === 'STARS'}
					<div class="flex flex-wrap gap-1" role="group" aria-label={c.label}>
						{#each steps(c) as step (step)}
							<button
								type="button"
								disabled={readonly}
								aria-pressed={value === step}
								aria-label="{stepLabel(c, step)} von {c.max}"
								title={stepLabel(c, step)}
								class="text-3xl leading-none transition-colors disabled:cursor-default {typeof value ===
									'number' && step <= value
									? 'text-amber-500'
									: 'text-slate-200'} {readonly ? '' : 'hover:text-amber-400'}"
								onclick={() => setScale(step)}>★</button
							>
						{/each}
						{#if typeof value === 'number'}
							<span class="ml-2 self-center font-mono text-xs text-slate-500"
								>{stepLabel(c, value)}</span
							>
						{/if}
					</div>
				{:else}
					<div class="flex flex-wrap gap-2" role="group" aria-label={c.label}>
						{#each steps(c) as step (step)}
							<button
								type="button"
								disabled={readonly && value !== step}
								aria-pressed={value === step}
								class="min-w-11 border px-3 py-2 text-sm font-bold transition-colors {value === step
									? 'border-institutional-navy bg-institutional-navy text-white'
									: 'border-slate-200 bg-white text-slate-600'} {readonly
									? 'cursor-default disabled:opacity-40'
									: 'hover:border-institutional-navy'}"
								onclick={() => setScale(step)}
							>
								{hasLabels ? stepLabel(c, step) : step}
							</button>
						{/each}
					</div>
				{/if}
				{#if (c.minLabel || c.maxLabel) && !hasLabels}
					<div class="mt-1 flex justify-between gap-4 font-mono text-[11px] text-slate-400">
						<span>{c.min}: {c.minLabel ?? ''}</span>
						<span>{c.max}: {c.maxLabel ?? ''}</span>
					</div>
				{/if}
			{:else if c.type === 'YES_NO'}
				<div class="flex gap-2" role="group" aria-label={c.label}>
					{#each [true, false] as option (option)}
						<button
							type="button"
							disabled={readonly && value !== option}
							aria-pressed={value === option}
							class="min-w-20 border px-4 py-2 text-sm font-bold {value === option
								? 'border-institutional-navy bg-institutional-navy text-white'
								: 'border-slate-200 bg-white text-slate-600'} {readonly
								? 'cursor-default disabled:opacity-40'
								: 'hover:border-institutional-navy'}"
							onclick={() => {
								if (!readonly) value = value === option && !c.required ? undefined : option;
							}}>{option ? 'Ja' : 'Nein'}</button
						>
					{/each}
				</div>
			{:else if c.type === 'CHOICE'}
				<div class="flex flex-col gap-1">
					{#each c.options ?? [] as option (option)}
						{@const checked = c.multiple ? selected.includes(option) : value === option}
						<label
							class="flex items-center gap-2 text-sm {readonly && !checked ? 'text-slate-400' : ''}"
						>
							<input
								type={c.multiple ? 'checkbox' : 'radio'}
								name="c-{c.id}"
								{checked}
								disabled={readonly}
								onchange={() => toggleOption(option)}
							/>
							{option}
						</label>
					{/each}
				</div>
			{:else if c.type === 'TEXT'}
				{#if readonly}
					<p class="text-sm whitespace-pre-line text-slate-700">
						{typeof value === 'string' && value ? value : '–'}
					</p>
				{:else}
					<textarea
						class="ed-input min-h-24"
						aria-label={c.label}
						maxlength={c.maxLength ?? 1000}
						value={typeof value === 'string' ? value : ''}
						oninput={(e) => {
							const text = (e.currentTarget as HTMLTextAreaElement).value;
							value = text === '' ? undefined : text;
						}}
					></textarea>
					<p class="mt-1 text-right font-mono text-[11px] text-slate-400">
						{typeof value === 'string' ? value.length : 0} / {c.maxLength ?? 1000}
					</p>
				{/if}
			{/if}
		</div>

		{#if compareLabel}
			<p
				class="mt-3 border-l-4 border-institutional-gold bg-amber-50 px-3 py-2 text-sm text-slate-700"
			>
				<span class="font-mono text-[11px] font-bold tracking-wider text-slate-500 uppercase"
					>{compareLabel}:</span
				>
				<span class="whitespace-pre-line">{formatAnswer(c, compare)}</span>
			</p>
		{/if}
	</fieldset>
{/if}
