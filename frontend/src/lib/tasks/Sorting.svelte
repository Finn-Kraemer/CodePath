<script lang="ts">
	import { dndzone, type DndEvent } from 'svelte-dnd-action';
	import { flip } from 'svelte/animate';
	import SubmitButton from './SubmitButton.svelte';
	import { config, solution, type TaskProps } from './types';

	type Item = { id: string; text: string };
	let { task, disabled, busy, submit }: TaskProps = $props();

	const sol = $derived(solution<{ order: string[] }>(task));
	let items = $state<Item[]>([]);

	$effect.pre(() => {
		const initial = config<{ items: Item[] }>(task).items;
		const last = task.lastAnswer?.order as string[] | undefined;
		const order = sol?.order ?? last;
		items = order
			? order.map((id) => initial.find((i) => i.id === id)!).filter(Boolean)
			: [...initial];
	});

	function handle(e: CustomEvent<DndEvent<Item>>) {
		items = e.detail.items;
	}

	// Tastaturbedienung ohne Ziehen
	function move(index: number, delta: number) {
		const target = index + delta;
		if (disabled || target < 0 || target >= items.length) return;
		const next = [...items];
		[next[index], next[target]] = [next[target], next[index]];
		items = next;
	}
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	<p class="mb-6 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
		Ziehen oder mit den Pfeilen sortieren
	</p>
	<div
		class="mb-10 space-y-3"
		use:dndzone={{ items, flipDurationMs: 200, dragDisabled: disabled, dropTargetStyle: {} }}
		onconsider={handle}
		onfinalize={handle}
	>
		{#each items as item, i (item.id)}
			<div
				animate:flip={{ duration: 200 }}
				class="flex items-center gap-4 rounded-none border-2 bg-white p-4 {sol
					? 'border-green-400'
					: 'border-slate-200'}"
			>
				<span class="font-mono text-xs font-black text-slate-400">{i + 1}.</span>
				<span class="flex-1 text-sm">{item.text}</span>
				<button
					type="button"
					class="px-2 text-slate-400 hover:text-institutional-navy"
					aria-label="Nach oben"
					onclick={() => move(i, -1)}
					{disabled}>▲</button
				>
				<button
					type="button"
					class="px-2 text-slate-400 hover:text-institutional-navy"
					aria-label="Nach unten"
					onclick={() => move(i, 1)}
					{disabled}>▼</button
				>
			</div>
		{/each}
	</div>
	<SubmitButton
		{busy}
		{disabled}
		label="Reihenfolge prüfen"
		onclick={() => submit({ order: items.map((i) => i.id) })}
	/>
</div>
