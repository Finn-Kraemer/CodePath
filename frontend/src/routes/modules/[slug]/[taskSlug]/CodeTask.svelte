<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { auth } from '$lib/auth.svelte';

	let { task, moduleSlug, supportUsed = false } = $props();

	let editorElement = $state<HTMLElement | null>(null);
	let editor: any = null;
	let output = $state('');
	let result = $state<{ correct: boolean; feedback: string; isLocked?: boolean } | null>(null);
	let loading = $state(false);
	let pyodideReady = $state(false);

	let worker: Worker | null = null;
	let executionTimeout: any = null;

	function initWorker() {
		if (worker) worker.terminate();
		pyodideReady = false;

		worker = new Worker('/pyodide-worker.js');
		worker.onmessage = (event) => {
			const { type, stdout, error: workerError } = event.data;

			if (type === 'ready') {
				pyodideReady = true;
			} else if (type === 'result' || type === 'error') {
				clearTimeout(executionTimeout);
				handleWorkerResponse(stdout, workerError);
			}
		};
	}

	onMount(async () => {
		// Load Monaco
		if (!(window as any).monaco) {
			const loaderScript = document.createElement('script');
			loaderScript.src = '/vendor/monaco/vs/loader.js';
			loaderScript.onload = () => {
				(window as any).require.config({
					paths: { vs: '/vendor/monaco/vs' }
				});
				(window as any).require(['vs/editor/editor.main'], () => {
					initEditor();
				});
			};
			document.head.appendChild(loaderScript);
		} else {
			initEditor();
		}

		initWorker();
	});

	function initEditor() {
		if (!editorElement || !(window as any).monaco) return;
		editor = (window as any).monaco.editor.create(editorElement, {
			value: task.config.starterCode || '',
			language: 'python',
			theme: 'vs-dark',
			minimap: { enabled: false },
			fontSize: 14,
			automaticLayout: true,
			readOnly: task.isCompleted || task.isLocked,
			roundedSelection: false,
			scrollBeyondLastLine: false
		});
	}

	onDestroy(() => {
		if (editor) editor.dispose();
		if (worker) worker.terminate();
		if (executionTimeout) clearTimeout(executionTimeout);
	});

	async function handleWorkerResponse(stdout: string, workerError: string) {
		if (workerError) {
			output = workerError;
			result = { correct: false, feedback: 'Code-Fehler im Skript' };
			loading = false;
			return;
		}

		output = stdout;

		try {
			const res = await auth.apiFetch(`/api/modules/${moduleSlug}/${task.slug}/submit`, {
				method: 'POST',
				body: JSON.stringify({
					payload: { output: stdout },
					supportUsed
				})
			});
			const data = await res.json();
			result = { correct: data.isCorrect, feedback: data.feedback, isLocked: data.isLocked };
			if (data.isCorrect) {
				task.isCompleted = true;
				editor.updateOptions({ readOnly: true });
			}
			if (data.isLocked) {
				task.isLocked = true;
				editor.updateOptions({ readOnly: true });
			}
		} catch (e) {
			result = { correct: false, feedback: 'Fehler bei der Verbindung zum Server' };
		} finally {
			loading = false;
		}
	}

	async function runCode() {
		if (!pyodideReady || !editor || task.isLocked || !worker) return;
		loading = true;
		output = '';
		result = null;
		const code = editor.getValue();

		// Start execution with timeout
		worker.postMessage({ type: 'run', code: code });

		executionTimeout = setTimeout(() => {
			if (loading) {
				worker?.terminate();
				loading = false;
				output = 'TIMEOUT: Die Code-Ausführung hat zu lange gedauert (evtl. Endlosschleife).';
				result = { correct: false, feedback: 'Ausführung abgebrochen.' };
				initWorker(); // Restart worker
			}
		}, 5000);
	}
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	<h3 class="mb-8 font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase">
		Python-Entwicklungsumgebung:
	</h3>

	<div class="relative mb-10">
		<div
			bind:this={editorElement}
			class="h-96 w-full rounded-none border-4 border-slate-800 bg-slate-900 shadow-inner"
		></div>

		{#if !pyodideReady}
			<div
				class="absolute inset-0 z-10 flex flex-col items-center justify-center bg-slate-900/90 backdrop-blur-sm"
			>
				<div
					class="mb-4 h-10 w-10 animate-spin border-4 border-teal-500 border-t-transparent"
				></div>
				<p class="px-6 text-center font-mono text-[10px] tracking-widest text-teal-500 uppercase">
					System-Bootstrapping...
				</p>
			</div>
		{/if}
	</div>

	<div class="mb-10 grid grid-cols-1 gap-10 lg:grid-cols-2">
		<div>
			<h4 class="mb-4 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
				Konsolenausgabe
			</h4>
			<div
				class="h-40 overflow-y-auto rounded-none border border-slate-100 bg-slate-50 p-6 font-mono text-xs whitespace-pre text-slate-600 shadow-inner"
			>
				{output || 'System bereit.'}
			</div>
		</div>
		<div>
			<h4 class="mb-4 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
				Testergebnis
			</h4>
			{#if result}
				<div
					class="flex h-40 flex-col items-center justify-center rounded-none border p-8 text-center font-mono text-xs
                    {result.correct
						? 'border-green-200 bg-green-50 text-green-700'
						: 'border-rose-200 bg-rose-50 text-rose-700'}"
				>
					<span class="mb-3 text-2xl font-black"
						>{result.correct ? 'ERFOLG' : result.isLocked ? 'GESPERRT' : 'FEHLER'}</span
					>
					<p class="leading-relaxed">{result.feedback}</p>
				</div>
			{:else}
				<div
					class="flex h-40 items-center justify-center rounded-none border border-dashed border-slate-200 bg-slate-50 p-8 text-center font-mono text-xs text-slate-400 italic"
				>
					Führen Sie das Skript zur Validierung aus.
				</div>
			{/if}
		</div>
	</div>

	<button
		onclick={runCode}
		disabled={loading || !pyodideReady || task.isCompleted || task.isLocked}
		class="w-full rounded-none bg-institutional-navy py-6 font-sans text-[11px] font-bold tracking-[4px] text-white uppercase shadow-sm transition-all hover:opacity-90 disabled:opacity-20"
	>
		{loading
			? 'Skript wird ausgeführt...'
			: task.isLocked
				? 'Aufgabe gesperrt'
				: task.isCompleted
					? 'Validierung bereits abgeschlossen'
					: 'Code ausführen & prüfen'}
	</button>
</div>
