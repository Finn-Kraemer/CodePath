<script lang="ts">
	import SubmitButton from './SubmitButton.svelte';
	import { config, solution, type TaskProps } from './types';

	let { task, disabled, busy, submit }: TaskProps = $props();

	const cfg = $derived(
		config<{ options: { id: string; text: string }[]; multiple: boolean }>(task)
	);
	const sol = $derived(solution<{ correct: string[]; explanations: Record<string, string> }>(task));
	let selected = $state<string[]>([]);

	function toggle(id: string) {
		if (disabled) return;
		if (!cfg.multiple) {
			selected = [id];
		} else {
			selected = selected.includes(id) ? selected.filter((s) => s !== id) : [...selected, id];
		}
	}

	function optionState(id: string) {
		if (sol)
			return sol.correct.includes(id) ? 'correct' : selected.includes(id) ? 'wrong' : 'neutral';
		return selected.includes(id) ? 'selected' : 'neutral';
	}
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	<p class="mb-6 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
		{cfg.multiple ? 'Mehrere Antworten können richtig sein' : 'Genau eine Antwort ist richtig'}
	</p>
	<div class="mb-10 space-y-3" role={cfg.multiple ? 'group' : 'radiogroup'}>
		{#each cfg.options as option (option.id)}
			{@const s = optionState(option.id)}
			<button
				type="button"
				role={cfg.multiple ? 'checkbox' : 'radio'}
				aria-checked={selected.includes(option.id)}
				onclick={() => toggle(option.id)}
				{disabled}
				class="flex w-full items-start gap-4 rounded-none border-2 p-5 text-left font-sans text-sm transition-all
				{s === 'selected' ? 'border-institutional-navy bg-slate-50 font-bold' : ''}
				{s === 'correct' ? 'border-green-500 bg-green-50' : ''}
				{s === 'wrong' ? 'border-rose-400 bg-rose-50' : ''}
				{s === 'neutral' ? 'border-slate-100 bg-slate-50 hover:border-slate-300' : ''}"
			>
				<span
					class="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center border-2 {cfg.multiple
						? ''
						: 'rounded-full'}
					{selected.includes(option.id)
						? 'border-institutional-navy bg-institutional-navy'
						: 'border-slate-300 bg-white'}"
				></span>
				<span>
					{option.text}
					{#if sol?.explanations?.[option.id]}
						<span class="mt-2 block text-xs font-normal text-slate-500"
							>{sol.explanations[option.id]}</span
						>
					{/if}
				</span>
			</button>
		{/each}
	</div>
	<SubmitButton
		{busy}
		disabled={disabled || selected.length === 0}
		onclick={() => submit({ selected })}
	/>
</div>
