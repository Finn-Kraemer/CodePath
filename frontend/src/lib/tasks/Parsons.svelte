<script lang="ts">
	import { dndzone, type DndEvent } from 'svelte-dnd-action';
	import { flip } from 'svelte/animate';
	import SubmitButton from './SubmitButton.svelte';
	import { config, solution, type TaskProps } from './types';

	type Block = { id: string; code: string };
	type Line = Block & { indent: number };
	type Placed = { id: string; indent: number };

	let { task, disabled, busy, submit }: TaskProps = $props();

	const cfg = $derived(
		config<{ blocks: Block[]; checkIndent: boolean; hasDistractors: boolean }>(task)
	);
	const sol = $derived(solution<{ lines: Placed[] }>(task));
	const MAX_INDENT = 6;

	let program = $state<Line[]>([]);

	$effect.pre(() => {
		const placed = sol?.lines ?? (task.lastAnswer?.lines as Placed[] | undefined) ?? [];
		program = placed
			.map((p) => {
				const block = cfg.blocks.find((b) => b.id === p.id);
				return block ? { ...block, indent: p.indent ?? 0 } : null;
			})
			.filter((l): l is Line => l !== null);
	});

	const available = $derived(cfg.blocks.filter((b) => !program.some((p) => p.id === b.id)));

	function handle(e: CustomEvent<DndEvent<Line>>) {
		program = e.detail.items;
	}

	function add(block: Block) {
		if (disabled) return;
		program = [...program, { ...block, indent: program.at(-1)?.indent ?? 0 }];
	}

	function update(index: number, change: (line: Line) => Line | null) {
		if (disabled) return;
		const next = [...program];
		const changed = change(next[index]);
		if (changed) next[index] = changed;
		else next.splice(index, 1);
		program = next;
	}

	function move(index: number, delta: number) {
		const target = index + delta;
		if (disabled || target < 0 || target >= program.length) return;
		const next = [...program];
		[next[index], next[target]] = [next[target], next[index]];
		program = next;
	}
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	<p class="mb-6 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
		Baue das Programm aus den Bausteinen{cfg.checkIndent
			? ' – die Einrückung zählt'
			: ''}{cfg.hasDistractors ? '. Nicht jeder Baustein wird gebraucht' : ''}
	</p>

	<div class="mb-8 grid gap-6 lg:grid-cols-2">
		<section aria-label="Bausteine">
			<h3 class="mb-3 text-xs font-black tracking-widest text-slate-500 uppercase">Bausteine</h3>
			<div class="space-y-2">
				{#each available as block (block.id)}
					<button
						type="button"
						class="flex w-full items-center justify-between gap-3 border-2 border-slate-200 bg-slate-50 p-3 text-left font-mono text-sm hover:border-slate-400"
						onclick={() => add(block)}
						{disabled}
					>
						<span class="whitespace-pre">{block.code}</span>
						<span class="text-xs text-slate-400" aria-hidden="true">＋</span>
					</button>
				{:else}
					<p class="text-xs text-slate-400">Alle Bausteine sind im Programm.</p>
				{/each}
			</div>
		</section>

		<section aria-label="Dein Programm">
			<h3 class="mb-3 text-xs font-black tracking-widest text-slate-500 uppercase">
				Dein Programm
			</h3>
			<div
				class="min-h-24 space-y-2 border-2 border-dashed border-slate-200 bg-slate-900 p-3"
				use:dndzone={{
					items: program,
					flipDurationMs: 150,
					dragDisabled: disabled,
					dropTargetStyle: {}
				}}
				onconsider={handle}
				onfinalize={handle}
			>
				{#each program as line, i (line.id)}
					<div
						animate:flip={{ duration: 150 }}
						class="flex items-center gap-2 bg-slate-800 py-2 pr-2 text-slate-100 {sol
							? 'ring-1 ring-green-400'
							: ''}"
						style={`padding-left: ${0.75 + line.indent * 1.5}rem`}
					>
						<code class="flex-1 font-mono text-sm whitespace-pre">{line.code}</code>
						{#if cfg.checkIndent}
							<button
								type="button"
								class="px-1 text-slate-400 hover:text-white"
								aria-label="Weniger einrücken"
								onclick={() => update(i, (l) => ({ ...l, indent: Math.max(0, l.indent - 1) }))}
								{disabled}>⇤</button
							>
							<button
								type="button"
								class="px-1 text-slate-400 hover:text-white"
								aria-label="Mehr einrücken"
								onclick={() =>
									update(i, (l) => ({ ...l, indent: Math.min(MAX_INDENT, l.indent + 1) }))}
								{disabled}>⇥</button
							>
						{/if}
						<button
							type="button"
							class="px-1 text-slate-400 hover:text-white"
							aria-label="Nach oben"
							onclick={() => move(i, -1)}
							{disabled}>▲</button
						>
						<button
							type="button"
							class="px-1 text-slate-400 hover:text-white"
							aria-label="Nach unten"
							onclick={() => move(i, 1)}
							{disabled}>▼</button
						>
						<button
							type="button"
							class="px-1 text-slate-400 hover:text-white"
							aria-label="Entfernen"
							onclick={() => update(i, () => null)}
							{disabled}>✕</button
						>
					</div>
				{/each}
			</div>
		</section>
	</div>
	<SubmitButton
		{busy}
		disabled={disabled || program.length === 0}
		label="Programm prüfen"
		onclick={() => submit({ lines: program.map((l) => ({ id: l.id, indent: l.indent })) })}
	/>
</div>
