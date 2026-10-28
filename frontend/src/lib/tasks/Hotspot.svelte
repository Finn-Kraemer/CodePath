<script lang="ts">
	import SubmitButton from './SubmitButton.svelte';
	import { config, solution, type TaskProps } from './types';

	let { task, disabled, busy, submit }: TaskProps = $props();

	const cfg = $derived(config<{ lines: string[]; mono: boolean; count?: number }>(task));
	const sol = $derived(solution<{ selected: number[] }>(task));
	let selected = $derived([...((task.lastAnswer?.selected as number[] | undefined) ?? [])]);

	function toggle(line: number) {
		if (disabled) return;
		selected = selected.includes(line)
			? selected.filter((l) => l !== line)
			: [...selected, line].sort((a, b) => a - b);
	}

	function lineState(line: number) {
		if (sol) {
			if (sol.selected.includes(line)) return 'correct';
			return selected.includes(line) ? 'wrong' : 'neutral';
		}
		return selected.includes(line) ? 'selected' : 'neutral';
	}
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	<p class="mb-6 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
		Klicke die passenden Zeilen an{cfg.count ? ` – gesucht: ${cfg.count}` : ''} · markiert: {selected.length}
	</p>
	<div class="mb-10 border-2 border-slate-100 bg-slate-50 p-2" role="group" aria-label="Zeilen">
		{#each cfg.lines as text, i (i)}
			{@const line = i + 1}
			{@const s = lineState(line)}
			{#if text.trim()}
				<button
					type="button"
					aria-pressed={selected.includes(line)}
					onclick={() => toggle(line)}
					{disabled}
					class="flex w-full gap-3 border-l-4 px-3 py-1.5 text-left text-sm transition-colors
					{cfg.mono ? 'font-mono whitespace-pre' : ''}
					{s === 'selected' ? 'border-amber-500 bg-amber-100' : ''}
					{s === 'correct' ? 'border-green-500 bg-green-100' : ''}
					{s === 'wrong' ? 'border-rose-400 bg-rose-100' : ''}
					{s === 'neutral' ? 'border-transparent hover:bg-white' : ''}"
				>
					<span class="w-6 shrink-0 text-right font-mono text-xs text-slate-400">{line}</span>
					<span class="break-all">{text}</span>
				</button>
			{:else}
				<div class="h-4" aria-hidden="true"></div>
			{/if}
		{/each}
	</div>
	<SubmitButton
		{busy}
		disabled={disabled || selected.length === 0}
		label="Markierungen prüfen"
		onclick={() => submit({ selected })}
	/>
</div>
