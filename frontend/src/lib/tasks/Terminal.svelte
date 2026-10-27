<script lang="ts">
	import { tick } from 'svelte';
	import SubmitButton from './SubmitButton.svelte';
	import { config, solution, type TaskProps } from './types';
	import { TerminalSimulator } from './terminal';

	let { task, disabled, busy, submit }: TaskProps = $props();

	const cfg = $derived(
		config<{ initialDirectories: string[]; initialFiles: Record<string, string> }>(task)
	);
	const sol = $derived(solution<{ commands: string[] }>(task));

	let sim = $state<TerminalSimulator>(new TerminalSimulator());
	let commands = $state<string[]>([]);
	let lines = $state<{ type: 'cmd' | 'out'; text: string }[]>([
		{ type: 'out', text: 'CodePath Terminal – "help" zeigt die Befehle.' }
	]);
	let input = $state('');
	let prompt = $state('~');
	let screen = $state<HTMLElement | null>(null);

	$effect.pre(() => {
		sim = new TerminalSimulator(cfg.initialDirectories, cfg.initialFiles);
		const previous = (task.lastAnswer?.commands as string[] | undefined) ?? [];
		for (const c of previous) run(c);
	});

	function run(command: string) {
		const out = sim.execute(command);
		commands = [...commands, command];
		if (command.trim() === 'clear') lines = [];
		else
			lines = [
				...lines,
				{ type: 'cmd', text: `${prompt} $ ${command}` },
				...(out ? [{ type: 'out' as const, text: out }] : [])
			];
		prompt = sim.prompt();
	}

	async function onKey(e: KeyboardEvent) {
		if (e.key !== 'Enter' || !input.trim()) return;
		if (input.trim() === 'submit') {
			input = '';
			await submit({ commands });
			return;
		}
		run(input);
		input = '';
		await tick();
		screen?.scrollTo({ top: screen.scrollHeight });
	}

	function reset() {
		sim = new TerminalSimulator(cfg.initialDirectories, cfg.initialFiles);
		commands = [];
		lines = [{ type: 'out', text: 'Zurückgesetzt.' }];
		prompt = '~';
	}
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	<div
		bind:this={screen}
		class="mb-6 h-80 overflow-y-auto bg-slate-950 p-6 font-mono text-sm text-slate-200"
		aria-live="polite"
	>
		{#each lines as line, i (i)}
			<div class={line.type === 'cmd' ? 'text-teal-300' : 'whitespace-pre-wrap'}>{line.text}</div>
		{/each}
		{#if !disabled}
			<div class="flex gap-2">
				<span class="text-teal-300">{prompt} $</span>
				<input
					bind:value={input}
					onkeydown={onKey}
					aria-label="Befehl eingeben"
					class="flex-1 bg-transparent text-white outline-none"
				/>
			</div>
		{/if}
	</div>
	{#if sol}
		<pre class="mb-6 bg-green-50 p-4 font-mono text-xs text-green-800">{sol.commands.join(
				'\n'
			)}</pre>
	{/if}
	<div class="grid grid-cols-1 gap-4 md:grid-cols-3">
		<button
			onclick={reset}
			{disabled}
			class="border-2 border-slate-300 py-5 text-xs font-black tracking-[3px] text-slate-500 uppercase disabled:opacity-40"
			>Zurücksetzen</button
		>
		<div class="md:col-span-2">
			<SubmitButton
				{busy}
				disabled={disabled || commands.length === 0}
				label="Ergebnis prüfen"
				onclick={() => submit({ commands })}
			/>
		</div>
	</div>
</div>
