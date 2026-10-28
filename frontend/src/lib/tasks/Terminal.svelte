<script lang="ts">
	import { tick, untrack } from 'svelte';
	import SubmitButton from './SubmitButton.svelte';
	import GitGraph from './GitGraph.svelte';
	import { config, solution, type TaskProps } from './types';
	import { TerminalSimulator } from './terminal';
	import { HELP } from './terminal-help';
	import { GIT_HELP, type GitSetup } from './git';

	let { task, disabled, busy, submit }: TaskProps = $props();

	const cfg = $derived(
		config<{
			initialDirectories: string[];
			initialFiles: Record<string, string>;
			git?: GitSetup;
			answerLabel?: string;
		}>(task)
	);
	const sol = $derived(solution<{ commands: string[]; answer?: string }>(task));

	let sim = $state<TerminalSimulator>(new TerminalSimulator());
	let version = $state(0);
	let commands = $state<string[]>([]);
	let lines = $state<{ type: 'cmd' | 'out'; text: string }[]>([]);
	let input = $state('');
	let answer = $state('');
	let prompt = $state('~');
	let screen = $state<HTMLElement | null>(null);
	let field = $state<HTMLInputElement | null>(null);
	// Position beim Blättern im Verlauf mit ↑/↓ (commands.length = neue Eingabe)
	let browsing = $state(0);

	const welcome =
		'CodePath Terminal – "help" zeigt die Befehle, Tab ergänzt Namen, ↑ holt alte Befehle.';
	const showGit = $derived(version >= 0 && (Boolean(cfg.git) || sim.git.root !== null));

	$effect.pre(() => {
		const { initialDirectories, initialFiles, git } = cfg;
		const previous = (task.lastAnswer?.commands as string[] | undefined) ?? [];
		const previousAnswer = (task.lastAnswer?.answer as string | undefined) ?? '';
		// run() liest und schreibt commands/lines – ohne untrack würde der Effekt sich selbst neu auslösen
		untrack(() => {
			sim = new TerminalSimulator(initialDirectories, initialFiles, git);
			commands = [];
			lines = [{ type: 'out', text: welcome }];
			prompt = sim.prompt();
			answer = previousAnswer;
			for (const c of previous) run(c);
			browsing = commands.length;
		});
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
		version++;
	}

	function send() {
		const payload: Record<string, unknown> = { commands };
		if (cfg.answerLabel) payload.answer = answer;
		return submit(payload);
	}

	async function onKey(e: KeyboardEvent) {
		if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
			e.preventDefault();
			browsing = Math.min(commands.length, Math.max(0, browsing + (e.key === 'ArrowUp' ? -1 : 1)));
			input = commands[browsing] ?? '';
			return;
		}
		if (e.key === 'Tab') {
			e.preventDefault();
			const completed = sim.complete(input);
			if (completed !== null) input = completed;
			return;
		}
		if (e.key !== 'Enter' || !input.trim()) return;
		if (input.trim() === 'submit') {
			input = '';
			await send();
			return;
		}
		run(input);
		input = '';
		browsing = commands.length;
		await tick();
		screen?.scrollTo({ top: screen.scrollHeight });
	}

	function reset() {
		sim = new TerminalSimulator(cfg.initialDirectories, cfg.initialFiles, cfg.git);
		commands = [];
		browsing = 0;
		lines = [{ type: 'out', text: 'Zurückgesetzt – alles wieder wie am Anfang.' }];
		prompt = sim.prompt();
		version++;
	}
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	<!-- Klick irgendwo ins Terminal setzt den Cursor in die Eingabezeile -->
	<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
	<div
		bind:this={screen}
		onclick={() => field?.focus()}
		class="mb-6 h-80 overflow-y-auto bg-slate-950 p-6 font-mono text-sm text-slate-200"
		aria-live="polite"
	>
		{#each lines as line, i (i)}
			<div class={line.type === 'cmd' ? 'text-teal-300' : 'whitespace-pre-wrap'}>{line.text}</div>
		{/each}
		{#if !disabled}
			<div class="flex gap-2">
				<span class="whitespace-nowrap text-teal-300">{prompt} $</span>
				<input
					bind:this={field}
					bind:value={input}
					onkeydown={onKey}
					aria-label="Befehl eingeben"
					autocomplete="off"
					autocapitalize="off"
					spellcheck="false"
					class="flex-1 bg-transparent text-white outline-none"
				/>
			</div>
		{/if}
	</div>

	{#if showGit}
		<div class="mb-6">
			<GitGraph git={sim.git} {version} />
		</div>
	{/if}

	<details class="mb-6 border border-slate-200 bg-slate-50 text-sm">
		<summary
			class="cursor-pointer px-4 py-3 text-xs font-black tracking-[3px] text-slate-500 uppercase"
			>Spickzettel</summary
		>
		<pre class="overflow-x-auto px-4 pb-4 font-mono text-xs text-slate-700">{HELP}{showGit
				? '\n\n' + GIT_HELP
				: ''}</pre>
	</details>

	{#if cfg.answerLabel}
		<label class="mb-6 block">
			<span class="mb-2 block text-xs font-black tracking-[3px] text-slate-500 uppercase"
				>{cfg.answerLabel}</span
			>
			<input
				bind:value={answer}
				{disabled}
				maxlength="200"
				autocomplete="off"
				class="w-full border-2 border-slate-300 px-4 py-3 font-mono text-sm focus:border-teal-600 focus:outline-none disabled:bg-slate-100"
			/>
		</label>
	{/if}

	{#if sol}
		<div class="mb-6 bg-green-50 p-4 font-mono text-xs text-green-800">
			<pre>{sol.commands.join('\n')}</pre>
			{#if sol.answer}<p class="mt-3 font-sans font-bold">Antwort: {sol.answer}</p>{/if}
		</div>
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
				disabled={disabled ||
					(commands.length === 0 && !cfg.answerLabel) ||
					(Boolean(cfg.answerLabel) && !answer.trim())}
				label="Ergebnis prüfen"
				onclick={send}
			/>
		</div>
	</div>
</div>
