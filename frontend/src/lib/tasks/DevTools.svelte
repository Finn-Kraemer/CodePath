<script lang="ts">
	import SubmitButton from './SubmitButton.svelte';
	import { config, solution, type TaskProps } from './types';

	let { task, disabled, busy, result, submit }: TaskProps = $props();

	const cfg = $derived(
		config<{ scenario: string; title: string; tools: string[]; parts: number; format: string }>(
			task
		)
	);
	const sol = $derived(solution<{ code: string }>(task));
	const perPart = $derived((result?.details?.parts as boolean[] | undefined) ?? null);
	let code = $derived((task.lastAnswer?.code as string | undefined) ?? '');
	// Die Lehrkraft sieht in der Vorschau dasselbe Labor mit den Vorschau-Codes
	const labUrl = $derived(`/api/lab/${task.id}/`);
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	<p class="mb-6 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
		Browser-Labor · Werkzeuge: {cfg.tools.join(', ')}
	</p>

	<div class="mb-8 border-l-4 border-amber-400 bg-amber-50 p-4 text-sm text-amber-900">
		Nur am <b>PC oder Laptop</b> mit Chrome, Edge oder Firefox. Entwicklerwerkzeuge öffnen:
		<b>F12</b> (Mac: <b>⌥ ⌘ I</b>). Auf Tablets und Handys gibt es sie nicht.
	</div>

	<a
		href={labUrl}
		target="_blank"
		rel="noopener"
		class="mb-10 inline-block border-2 border-institutional-navy px-6 py-3 text-xs font-black tracking-widest text-institutional-navy uppercase hover:bg-institutional-navy hover:text-white"
		>{cfg.title} öffnen ↗</a
	>

	<label
		class="mb-2 block text-xs font-black tracking-widest text-slate-500 uppercase"
		for="lab-code">Dein Code ({cfg.parts} Teile)</label
	>
	<input
		id="lab-code"
		class="mb-3 w-full border-2 border-slate-200 p-3 font-mono text-xl tracking-[4px] uppercase"
		bind:value={code}
		placeholder={cfg.format}
		autocomplete="off"
		spellcheck="false"
		{disabled}
	/>
	{#if perPart}
		<div class="mb-6 flex gap-2" aria-label="Ergebnis je Teil">
			{#each perPart as ok, i (i)}
				<span
					class="px-3 py-1 font-mono text-xs font-bold {ok
						? 'bg-green-100 text-green-800'
						: 'bg-rose-100 text-rose-800'}">Teil {i + 1}: {ok ? 'richtig' : 'falsch'}</span
				>
			{/each}
		</div>
	{/if}
	{#if sol}
		<p class="mb-6 font-mono text-lg text-green-700">Dein Code: {sol.code}</p>
	{/if}
	<SubmitButton
		{busy}
		disabled={disabled || !code.trim()}
		label="Code prüfen"
		onclick={() => submit({ code })}
	/>
</div>
