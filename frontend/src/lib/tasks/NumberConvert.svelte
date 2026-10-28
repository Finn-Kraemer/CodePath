<script lang="ts">
	import SubmitButton from './SubmitButton.svelte';
	import { config, solution, type TaskProps } from './types';

	let { task, disabled, busy, result, submit }: TaskProps = $props();

	const cfg = $derived(config<{ from: number; to: number; values: string[] }>(task));
	const sol = $derived(solution<{ answers: string[] }>(task));
	const perValue = $derived((result?.details?.values as boolean[] | undefined) ?? null);
	const names: Record<number, string> = {
		2: 'binär',
		8: 'oktal',
		10: 'dezimal',
		16: 'hexadezimal'
	};
	let answers = $state<string[]>([]);

	$effect.pre(() => {
		const last = task.lastAnswer?.answers as string[] | undefined;
		answers = cfg.values.map((_, i) => sol?.answers[i] ?? last?.[i] ?? '');
	});

	function rowState(i: number) {
		if (sol) return 'correct';
		if (perValue) return perValue[i] ? 'correct' : 'wrong';
		return 'neutral';
	}
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	<p class="mb-6 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
		Von {names[cfg.from]} (Basis {cfg.from}) nach {names[cfg.to]} (Basis {cfg.to})
	</p>
	<div class="mb-10 space-y-3">
		{#each cfg.values as value, i (i)}
			{@const s = rowState(i)}
			<div
				class="flex flex-wrap items-center gap-3 border-2 p-4
				{s === 'correct' ? 'border-green-400 bg-green-50' : ''}
				{s === 'wrong' ? 'border-rose-300 bg-rose-50' : ''}
				{s === 'neutral' ? 'border-slate-100 bg-slate-50' : ''}"
			>
				<label for={`convert-${i}`} class="min-w-32 font-mono text-lg font-bold"
					><span data-value>{value}</span><sub class="text-xs text-slate-500">{cfg.from}</sub> =</label
				>
				<input
					id={`convert-${i}`}
					class="min-w-0 flex-1 border-2 border-slate-200 bg-white p-2 font-mono text-lg tracking-wider"
					bind:value={answers[i]}
					autocomplete="off"
					spellcheck="false"
					inputmode={cfg.to === 16 ? 'text' : 'numeric'}
					{disabled}
				/>
				<span class="font-mono text-xs text-slate-500">Basis {cfg.to}</span>
			</div>
		{/each}
	</div>
	<SubmitButton
		{busy}
		disabled={disabled || answers.some((a) => !a.trim())}
		onclick={() => submit({ answers })}
	/>
</div>
