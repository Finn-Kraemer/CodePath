<script lang="ts">
	import { mediaUrl } from '$lib/media';
	import SubmitButton from './SubmitButton.svelte';
	import { config, solution, type TaskProps } from './types';

	type Mark = { x: number; y: number };
	type Target = { id: string; x: number; y: number; w: number; h: number; label: string };

	let { task, disabled, busy, submit }: TaskProps = $props();

	const cfg = $derived(
		config<{ image: string; alt: string; maxMarks: number; count?: number }>(task)
	);
	const sol = $derived(solution<{ targets: Target[] }>(task));
	let marks = $derived<Mark[]>([...((task.lastAnswer?.marks as Mark[] | undefined) ?? [])]);

	function place(event: MouseEvent) {
		if (disabled) return;
		const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
		const x = Math.round(((event.clientX - rect.left) / rect.width) * 1000) / 10;
		const y = Math.round(((event.clientY - rect.top) / rect.height) * 1000) / 10;
		if (marks.length >= cfg.maxMarks) return;
		marks = [...marks, { x: Math.min(100, Math.max(0, x)), y: Math.min(100, Math.max(0, y)) }];
	}

	function remove(index: number) {
		if (disabled) return;
		marks = marks.filter((_, i) => i !== index);
	}
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	<p class="mb-6 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
		Klicke ins Bild, um zu markieren{cfg.count ? ` – gesucht: ${cfg.count}` : ''} · {marks.length} von
		höchstens {cfg.maxMarks}
	</p>
	<div class="relative mb-4 select-none">
		<!-- Klick setzt eine Markierung; per Tastatur sind die Markierungen unten entfernbar -->
		<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
		<div class="relative cursor-crosshair" onclick={place}>
			<img src={mediaUrl(cfg.image)} alt={cfg.alt} class="block w-full" draggable="false" />
			{#if sol}
				{#each sol.targets as t (t.id)}
					<div
						class="pointer-events-none absolute border-4 border-green-500 bg-green-400/20"
						style={`left:${t.x}%;top:${t.y}%;width:${t.w}%;height:${t.h}%`}
					>
						{#if t.label}<span
								class="absolute top-full left-0 mt-1 bg-green-700 px-2 py-0.5 text-xs whitespace-nowrap text-white"
								>{t.label}</span
							>{/if}
					</div>
				{/each}
			{/if}
		</div>
		{#each marks as mark, i (i)}
			<button
				type="button"
				class="absolute flex h-9 w-9 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full border-4 border-white bg-amber-500 text-sm font-black text-white shadow-lg ring-2 ring-amber-700"
				style={`left:${mark.x}%;top:${mark.y}%`}
				onclick={() => remove(i)}
				aria-label={`Markierung ${i + 1} entfernen`}
				{disabled}>{i + 1}</button
			>
		{/each}
	</div>
	{#if marks.length}
		<p class="mb-8 text-xs text-slate-500">Klick auf eine Nummer entfernt die Markierung wieder.</p>
	{/if}
	<SubmitButton
		{busy}
		disabled={disabled || marks.length === 0}
		label="Markierungen prüfen"
		onclick={() => submit({ marks })}
	/>
</div>
