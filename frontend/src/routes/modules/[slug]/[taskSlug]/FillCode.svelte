<script lang="ts">
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';

	let { task, moduleSlug } = $props();

	let answers = $state<string[]>([]);
	let output = $state('');
	let result = $state<{ correct: boolean; feedback: string } | null>(null);
	let loading = $state(false);
	let pyodideReady = $state(false);
	let pyodide: any = null;

	let parts = $derived(task.config.template.split('___'));

	$effect(() => {
		if (answers.length === 0) {
			answers = new Array(parts.length - 1).fill('');
		}
	});

	onMount(async () => {
		// Load Pyodide
		if (!(window as any).loadPyodide) {
			const pyodideScript = document.createElement('script');
			pyodideScript.src = 'https://cdn.jsdelivr.net/pyodide/v0.25.0/full/pyodide.js';
			pyodideScript.onload = async () => {
				pyodide = await (window as any).loadPyodide();
				pyodideReady = true;
			};
			document.head.appendChild(pyodideScript);
		} else {
			pyodide = await (window as any).loadPyodide();
			pyodideReady = true;
		}
	});

	async function handleSubmit() {
		if (!pyodideReady || answers.some((a) => !a)) return;
		loading = true;
		output = '';

		// Assemble code
		let finalCode = '';
		parts.forEach((part: string, i: number) => {
			finalCode += part;
			if (i < answers.length) {
				finalCode += answers[i];
			}
		});

		try {
			pyodide.runPython(`
                import sys
                import io
                sys.stdout = io.StringIO()
            `);

			await pyodide.runPythonAsync(finalCode);
			const stdout = pyodide.runPython('sys.stdout.getvalue()');
			output = stdout;

			const res = await auth.apiFetch(`/api/modules/${moduleSlug}/${task.slug}/submit`, {
				method: 'POST',
				body: JSON.stringify({ payload: { output: stdout } })
			});
			const data = await res.json();
			result = { correct: data.isCorrect, feedback: data.feedback };
			if (data.isCorrect) task.isCompleted = true;
		} catch (err: any) {
			output = err.message;
			result = { correct: false, feedback: 'Syntax-Fehler im Skript' };
		} finally {
			loading = false;
		}
	}
</script>

<div class="border border-slate-200 bg-white p-10 shadow-sm rounded-none">
	<h3 class="mb-8 font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase">
		Syntax-Vervollständigung:
	</h3>

	<div
		class="relative mb-10 border-4 border-slate-800 bg-slate-900 p-10 font-mono text-sm leading-relaxed text-teal-400 rounded-none shadow-inner"
	>
		<pre class="whitespace-pre-wrap">{#each parts as part, i (i)}{part}{#if i < parts.length - 1}<input
						bind:value={answers[i]}
						disabled={task.isCompleted}
						type="text"
						class="mx-2 min-w-[80px] border-b-2 border-teal-500 bg-slate-800 px-3 py-1 text-white outline-none transition-colors focus:border-white disabled:opacity-50 rounded-none"
						placeholder="..."
					/>{/if}{/each}</pre>

		{#if !pyodideReady}
			<div
				class="absolute inset-0 z-10 flex flex-col items-center justify-center bg-slate-900/90 backdrop-blur-sm"
			>
				<div
					class="mb-4 h-10 w-10 border-4 border-teal-500 border-t-transparent animate-spin"
				></div>
				<p class="font-mono text-[10px] tracking-widest text-teal-500 uppercase">
					Initialisierung Code-Umgebung...
				</p>
			</div>
		{/if}
	</div>

	{#if output || result}
		<div class="mb-10 grid grid-cols-1 gap-10 lg:grid-cols-2">
			<div>
				<h4 class="mb-4 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">System-Ausgabe</h4>
				<div
					class="h-32 overflow-y-auto whitespace-pre border border-slate-100 bg-slate-50 p-6 font-mono text-xs text-slate-600 rounded-none"
				>
					{output || 'Keine Ausgabe registriert'}
				</div>
			</div>
			<div>
				<h4 class="mb-4 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">Validierung</h4>
				{#if result}
					<div
						class="flex h-32 flex-col items-center justify-center border p-6 font-mono text-xs text-center rounded-none
                        {result.correct
							? 'border-green-200 bg-green-50 text-green-700'
							: 'border-rose-200 bg-rose-50 text-rose-700'}"
					>
						<span class="mb-2 text-xl font-bold">{result.correct ? '✓' : '✗'}</span>
						{result.feedback}
					</div>
				{:else}
					<div
						class="flex h-32 items-center justify-center border border-dashed border-slate-200 bg-slate-50 px-6 text-center font-mono text-xs text-slate-400 italic rounded-none"
					>
						Warten auf Code-Ausführung...
					</div>
				{/if}
			</div>
		</div>
	{/if}

	<button
		onclick={handleSubmit}
		disabled={loading || !pyodideReady || answers.some((a) => !a) || task.isCompleted}
		class="w-full bg-institutional-navy py-5 font-sans text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:opacity-90 disabled:opacity-20 rounded-none"
	>
		{loading ? 'Prüfprozess...' : task.isCompleted ? 'Skript bereits verifiziert' : 'Skript ausführen & validieren'}
	</button>
</div>
