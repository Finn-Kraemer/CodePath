<script lang="ts">
	import SubmitButton from './SubmitButton.svelte';
	import { config, solution, type TaskProps } from './types';

	type Item = { id: string; text: string };
	let { task, disabled, busy, result, submit }: TaskProps = $props();

	const cfg = $derived(config<{ left: Item[]; right: Item[] }>(task));
	const sol = $derived(solution<{ pairs: Record<string, string> }>(task));
	const perPair = $derived((result?.details?.pairs as Record<string, boolean> | undefined) ?? null);
	let pairs = $derived({
		...((sol?.pairs ?? task.lastAnswer?.pairs ?? {}) as Record<string, string>)
	});

	const complete = $derived(cfg.left.every((l) => pairs[l.id]));
	const used = $derived(new Set(Object.values(pairs)));

	function rowState(id: string) {
		if (sol) return 'correct';
		if (perPair && id in perPair) return perPair[id] ? 'correct' : 'wrong';
		return 'neutral';
	}
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	<p class="mb-6 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
		Ordne jedem Begriff das Passende zu
	</p>
	<div class="mb-10 space-y-3">
		{#each cfg.left as left (left.id)}
			{@const s = rowState(left.id)}
			<div
				class="grid items-center gap-3 border-2 p-4 sm:grid-cols-2
				{s === 'correct' ? 'border-green-400 bg-green-50' : ''}
				{s === 'wrong' ? 'border-rose-300 bg-rose-50' : ''}
				{s === 'neutral' ? 'border-slate-100 bg-slate-50' : ''}"
			>
				<label for={`match-${left.id}`} class="text-sm font-bold">{left.text}</label>
				<select
					id={`match-${left.id}`}
					class="w-full border-2 border-slate-200 bg-white p-2 text-sm"
					value={pairs[left.id] ?? ''}
					onchange={(e) => (pairs = { ...pairs, [left.id]: e.currentTarget.value })}
					{disabled}
				>
					<option value="" disabled>– bitte wählen –</option>
					{#each cfg.right as right (right.id)}
						<option value={right.id}
							>{right.text}{used.has(right.id) && pairs[left.id] !== right.id
								? ' (vergeben)'
								: ''}</option
						>
					{/each}
				</select>
			</div>
		{/each}
	</div>
	<SubmitButton
		{busy}
		disabled={disabled || !complete}
		label="Zuordnung prüfen"
		onclick={() => submit({ pairs })}
	/>
</div>
