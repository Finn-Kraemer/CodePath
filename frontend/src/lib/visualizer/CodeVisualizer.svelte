<script lang="ts">
	import { onDestroy, onMount } from 'svelte';
	import type { PythonRunner } from '$lib/python.svelte';
	import {
		DEFAULT_MAX_STEPS,
		aliases,
		changedItems,
		changedVariables,
		describeStep,
		formatValue,
		objectNumbers,
		stopNotice,
		type HeapObject,
		type Trace,
		type Value
	} from './trace';

	/**
	 * Code-Visualizer: geht ein Programm Schritt für Schritt durch und zeigt Variablen, Listen,
	 * Aufrufstapel und Ausgabe. Reines Lernwerkzeug – nichts wird bewertet oder gespeichert.
	 */
	let {
		runner,
		code,
		stdin = '',
		maxSteps = DEFAULT_MAX_STEPS,
		title = 'Schritt für Schritt',
		onclose
	}: {
		runner: PythonRunner;
		code: string;
		stdin?: string;
		maxSteps?: number;
		title?: string;
		onclose?: () => void;
	} = $props();

	const INTRO_KEY = 'codepath.visualizer.intro';
	const SPEEDS = [
		{ label: 'Langsam', ms: 1200 },
		{ label: 'Mittel', ms: 600 },
		{ label: 'Schnell', ms: 200 }
	];

	let trace = $state<Trace | null>(null);
	let index = $state(0);
	let playing = $state(false);
	let speed = $state(SPEEDS[1].ms);
	let showIntro = $state(false);
	let container = $state<HTMLElement | null>(null);
	let timer: ReturnType<typeof setInterval> | undefined;

	const lines = $derived(code.split('\n'));
	const steps = $derived(trace?.steps ?? []);
	const step = $derived(steps[index]);
	const prev = $derived(index > 0 ? steps[index - 1] : undefined);
	const numbers = $derived(objectNumbers(steps));
	const changed = $derived(step ? changedVariables(prev, step) : []);
	const description = $derived(step ? describeStep(prev, step) : []);
	const aliasMap = $derived(step ? aliases(step) : new Map<string, string[]>());
	const output = $derived(
		!trace ? '' : step && index < steps.length - 1 ? trace.stdout.slice(0, step.out) : trace.stdout
	);
	const atEnd = $derived(index >= steps.length - 1);
	const notice = $derived(trace ? stopNotice(trace, maxSteps) : null);
	/** Objekte, auf die eine Variable direkt zeigt, in der Reihenfolge ihrer Nummer */
	const objects = $derived.by(() => {
		if (!step) return [] as [string, HeapObject][];
		const ids = step.stack.flatMap((frame) =>
			Object.values(frame.locals).flatMap((value) => (value.k === 'ref' ? [value.id] : []))
		);
		return ids
			.filter((id, i) => ids.indexOf(id) === i && step.heap[id])
			.sort((a, b) => (numbers.get(a) ?? 0) - (numbers.get(b) ?? 0))
			.map((id) => [id, step.heap[id]] as [string, HeapObject]);
	});

	const KIND_LABELS: Record<string, string> = {
		list: 'Liste',
		tuple: 'Tupel',
		set: 'Menge',
		frozenset: 'Menge',
		dict: 'Dict'
	};

	function label(value: Value) {
		if (value.k !== 'ref') return value.r;
		const object = step?.heap[value.id];
		return `→ ${KIND_LABELS[object?.k ?? ''] ?? 'Objekt'} #${numbers.get(value.id) ?? '?'}`;
	}

	onMount(async () => {
		try {
			showIntro = localStorage.getItem(INTRO_KEY) !== 'gesehen';
		} catch {
			showIntro = true;
		}
		trace = await runner.trace(code, stdin, { maxSteps });
		index = 0;
		container?.focus();
	});

	onDestroy(() => clearInterval(timer));

	function closeIntro() {
		showIntro = false;
		try {
			localStorage.setItem(INTRO_KEY, 'gesehen');
		} catch {
			// ohne Speicher erscheint die Einführung eben wieder
		}
	}

	function go(target: number) {
		index = Math.max(0, Math.min(steps.length - 1, target));
		if (index >= steps.length - 1) pause();
	}

	function play() {
		if (atEnd) index = 0;
		playing = true;
		clearInterval(timer);
		timer = setInterval(() => go(index + 1), speed);
	}

	function pause() {
		playing = false;
		clearInterval(timer);
	}

	function setSpeed(ms: number) {
		speed = ms;
		if (playing) play();
	}

	function onKeydown(event: KeyboardEvent) {
		const target = event.target as HTMLElement;
		if (target.tagName === 'SELECT' || target.tagName === 'INPUT') return;
		if (event.key === 'ArrowRight') go(index + 1);
		else if (event.key === 'ArrowLeft') go(index - 1);
		else if (event.key === 'Home') go(0);
		else if (event.key === 'End') go(steps.length - 1);
		else return;
		event.preventDefault();
	}

	const button =
		'border border-slate-300 bg-white px-3 py-2 font-mono text-xs font-bold text-institutional-navy hover:border-institutional-navy disabled:opacity-40';
</script>

<!-- svelte-ignore a11y_no_noninteractive_tabindex, a11y_no_noninteractive_element_interactions -->
<section
	bind:this={container}
	tabindex="0"
	onkeydown={onKeydown}
	class="border-2 border-institutional-navy bg-white p-6 outline-none focus-visible:ring-2 focus-visible:ring-institutional-gold"
	aria-label="Code-Visualizer"
	data-testid="code-visualizer"
>
	<div class="mb-4 flex items-center justify-between gap-4">
		<h3 class="font-sans text-xs font-black tracking-[3px] text-institutional-navy uppercase">
			{title}
		</h3>
		{#if onclose}
			<button type="button" class={button} onclick={onclose}>Schließen</button>
		{/if}
	</div>

	{#if showIntro}
		<div class="mb-4 border-l-4 border-institutional-gold bg-amber-50 p-4 text-sm text-slate-700">
			<p>
				Der <strong>gelbe Balken</strong> (▶) zeigt, welche Zeile Python
				<strong>als Nächstes</strong>
				ausführt. Die hellere Markierung (·) ist die Zeile davor. Rechts siehst du, welche Werte deine
				Variablen gerade haben – Änderungen sind fett und mit „neu“ markiert.
			</p>
			<p class="mt-2 text-xs text-slate-500">Tipp: Mit ← und → gehst du schrittweise.</p>
			<button type="button" class="{button} mt-3" onclick={closeIntro}>Verstanden</button>
		</div>
	{/if}

	{#if !trace}
		<p class="py-8 text-center font-mono text-xs text-slate-500" role="status">
			{runner.ready ? 'Programm wird aufgezeichnet …' : 'Python lädt …'}
		</p>
	{:else}
		{#if notice}
			<p class="mb-4 border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900" role="alert">
				{notice}
			</p>
		{/if}
		{#if trace.error && (atEnd || !steps.length)}
			<p
				class="mb-4 border border-rose-200 bg-rose-50 p-3 font-mono text-xs text-rose-700"
				role="alert"
			>
				{trace.error}
			</p>
		{/if}

		{#if steps.length}
			<div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
				<ol
					class="overflow-x-auto border-4 border-slate-800 bg-slate-900 py-3 font-mono text-xs leading-6 text-slate-200"
					aria-label="Programmcode"
				>
					{#each lines as text, i (i)}
						{@const current = step?.line === i + 1}
						{@const last = !current && prev?.line === i + 1}
						<li
							class="flex px-2 whitespace-pre {current
								? 'bg-yellow-300 text-slate-900'
								: last
									? 'bg-yellow-100/20'
									: ''}"
							aria-current={current ? 'step' : undefined}
							data-line={i + 1}
						>
							<span class="w-5 shrink-0 text-center" aria-hidden="true"
								>{current ? '▶' : last ? '·' : ''}</span
							>
							<span class="w-8 shrink-0 pr-3 text-right opacity-50">{i + 1}</span>
							<span>{text || ' '}</span>
						</li>
					{/each}
				</ol>

				<div class="space-y-4">
					<div>
						<p
							class="mb-2 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
						>
							Aufrufstapel und Variablen
						</p>
						<div class="space-y-2" data-testid="visualizer-frames">
							{#each step?.stack ?? [] as frame, f (f)}
								{@const active = f === (step?.stack.length ?? 0) - 1}
								<div
									class="border p-3 {active
										? 'border-institutional-navy bg-slate-50'
										: 'border-slate-200 bg-white opacity-80'}"
									data-frame={frame.func}
								>
									<p class="mb-1 font-mono text-[11px] font-bold text-institutional-navy">
										{frame.func === '<module>' ? 'Hauptprogramm' : `${frame.func}()`}
										{#if active}<span class="ml-2 text-[9px] text-slate-400 uppercase">aktiv</span
											>{/if}
									</p>
									{#if Object.keys(frame.locals).length === 0}
										<p class="font-mono text-[11px] text-slate-400">keine Variablen</p>
									{/if}
									<table class="w-full font-mono text-xs">
										<tbody>
											{#each Object.entries(frame.locals) as [name, value] (name)}
												{@const isNew = changed[f]?.has(name)}
												<tr class={isNew ? 'bg-amber-100 font-bold' : ''} data-var={name}>
													<td class="w-1/3 py-0.5 pr-2 text-slate-500">{name}</td>
													<td class="py-0.5 text-slate-900"
														>{label(value)}
														{#if isNew}<span
																class="ml-1 border border-amber-400 px-1 text-[9px] font-black text-amber-700 uppercase"
																>neu</span
															>{/if}</td
													>
												</tr>
											{/each}
										</tbody>
									</table>
									{#if active && step?.event === 'return' && step.returnValue}
										<p class="mt-2 font-mono text-xs text-green-700">
											↩ gibt {formatValue(step.returnValue, step.heap)} zurück
										</p>
									{/if}
								</div>
							{/each}
						</div>
					</div>

					{#if objects.length}
						<div>
							<p
								class="mb-2 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
							>
								Listen und andere Objekte
							</p>
							<div class="space-y-3" data-testid="visualizer-objects">
								{#each objects as [id, object] (id)}
									{@const cells = step ? changedItems(prev, step, id) : new Set<number>()}
									{@const names = aliasMap.get(id)}
									<div data-object={numbers.get(id)}>
										<p class="mb-1 font-mono text-[11px] text-slate-600">
											{KIND_LABELS[object.k] ?? 'Objekt'} #{numbers.get(id)}
											{#if names}
												<span class="ml-2 text-institutional-navy" data-testid="visualizer-alias"
													>← dieselbe {KIND_LABELS[object.k] ?? 'Objekt'} für {names.join(
														', '
													)}</span
												>
											{/if}
										</p>
										<div class="flex flex-wrap">
											{#each object.items as item, i (i)}
												<div
													class="-ml-px min-w-10 border px-2 py-1 text-center font-mono text-xs transition-colors duration-300 {cells.has(
														i
													)
														? 'border-amber-500 bg-amber-100 font-bold'
														: 'border-slate-300 bg-white'}"
												>
													{#if object.k === 'list' || object.k === 'tuple'}
														<span class="block text-[9px] text-slate-400">{i}</span>
													{/if}
													{#if Array.isArray(item)}
														{formatValue(item[0], step?.heap ?? {})}: {formatValue(
															item[1],
															step?.heap ?? {}
														)}
													{:else}
														{formatValue(item, step?.heap ?? {})}
													{/if}
													{#if cells.has(i)}<span class="sr-only"> (geändert)</span>{/if}
												</div>
											{/each}
											{#if object.items.length === 0}
												<span class="font-mono text-xs text-slate-400">leer</span>
											{/if}
											{#if object.more > 0}
												<span class="px-2 py-1 font-mono text-xs text-slate-400"
													>… +{object.more}</span
												>
											{/if}
										</div>
									</div>
								{/each}
							</div>
						</div>
					{/if}

					<div>
						<p
							class="mb-2 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
						>
							Ausgabe
						</p>
						<pre
							class="max-h-32 min-h-10 overflow-y-auto border border-slate-100 bg-slate-50 p-3 font-mono text-xs whitespace-pre-wrap text-slate-700"
							data-testid="visualizer-output">{output || ' '}</pre>
					</div>
				</div>
			</div>

			<div
				class="mt-4 min-h-10 border-l-4 border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-700"
				aria-live="polite"
				data-testid="visualizer-changes"
			>
				{#if step?.exception}
					<p class="text-rose-700">Fehler in Zeile {step.line}: {step.exception}</p>
				{/if}
				{#each description as line, i (i)}
					<p>{line}</p>
				{:else}
					<p class="text-slate-400">Zeile {step?.line} kommt als Nächstes.</p>
				{/each}
			</div>

			<div class="mt-4 flex flex-wrap items-center gap-2">
				<button
					type="button"
					class={button}
					onclick={() => go(0)}
					disabled={index === 0}
					aria-label="Zum Anfang">⏮</button
				>
				<button
					type="button"
					class={button}
					onclick={() => go(index - 1)}
					disabled={index === 0}
					aria-label="Schritt zurück">◀ Zurück</button
				>
				<button
					type="button"
					class={button}
					onclick={() => go(index + 1)}
					disabled={atEnd}
					aria-label="Schritt vor">Vor ▶</button
				>
				<button
					type="button"
					class={button}
					onclick={() => go(steps.length - 1)}
					disabled={atEnd}
					aria-label="Zum Ende">⏭</button
				>
				<button type="button" class={button} onclick={() => (playing ? pause() : play())}>
					{playing ? 'Pause' : 'Abspielen'}
				</button>
				<label class="flex items-center gap-2 font-mono text-[11px] text-slate-500">
					Tempo
					<select
						class="min-w-28 border border-slate-300 bg-white py-1 pr-8 pl-2 text-xs"
						value={speed}
						onchange={(e) => setSpeed(Number((e.target as HTMLSelectElement).value))}
					>
						{#each SPEEDS as option (option.ms)}
							<option value={option.ms}>{option.label}</option>
						{/each}
					</select>
				</label>
				<span class="ml-auto font-mono text-[11px] text-slate-500" data-testid="visualizer-position"
					>Schritt {index + 1} von {steps.length}</span
				>
			</div>
			<input
				type="range"
				min="0"
				max={steps.length - 1}
				value={index}
				oninput={(e) => go(Number((e.target as HTMLInputElement).value))}
				class="mt-3 w-full"
				aria-label="Schritt wählen"
			/>
		{/if}
	{/if}
</section>
