<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { auth } from '$lib/auth.svelte';

	let { task, moduleSlug, supportUsed = false } = $props();

	let editorElement = $state<HTMLElement | null>(null);
	let editor: any = null;
	let result = $state<{ correct: boolean; feedback: string; isLocked?: boolean } | null>(null);
	let loading = $state(false);

	onMount(async () => {
		// Load Monaco
		if (!(window as any).monaco) {
			const loaderScript = document.createElement('script');
			loaderScript.src = 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs/loader.min.js';
			loaderScript.onload = () => {
				(window as any).require.config({
					paths: { vs: 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs' }
				});
				(window as any).require(['vs/editor/editor.main'], () => {
					initEditor();
				});
			};
			document.head.appendChild(loaderScript);
		} else {
			initEditor();
		}
	});

	function initEditor() {
		if (!editorElement || !(window as any).monaco) return;
		editor = (window as any).monaco.editor.create(editorElement, {
			value: task.config.starterSql || '-- SELECT * FROM users;',
			language: 'sql',
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
	});

	async function handleSubmit() {
		if (!editor || task.isLocked) return;
		loading = true;
		const query = editor.getValue();

		try {
			const res = await auth.apiFetch(`/api/modules/${moduleSlug}/${task.slug}/submit`, {
				method: 'POST',
				body: JSON.stringify({ 
					payload: { result: query }, // Sending the query as 'result' for the simplified backend check
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
</script>

<div class="border border-slate-200 bg-white p-10 shadow-sm rounded-none">
	<h3 class="mb-8 font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase">
		SQL-Datenbank Abfrage:
	</h3>

	<div class="mb-10">
		<div
			bind:this={editorElement}
			class="h-64 w-full border-4 border-slate-800 bg-slate-900 shadow-inner rounded-none"
		></div>
	</div>

	{#if result}
		<div
			class="mb-8 border p-6 font-mono text-xs rounded-none
            {result.correct ? 'border-green-200 bg-green-50 text-green-700' : 'border-rose-200 bg-rose-50 text-rose-700'}"
		>
			<div class="flex items-center gap-4">
				<span class="text-xl font-bold">{result.correct ? '✓' : (result.isLocked ? '🔒' : '✗')}</span>
				<div>
					<span class="font-bold uppercase tracking-widest">{result.correct ? 'ERGEBNIS:' : 'HINWEIS:'}</span> {result.feedback}
				</div>
			</div>
		</div>
	{/if}

	<button
		onclick={handleSubmit}
		disabled={loading || task.isCompleted || task.isLocked}
		class="w-full bg-institutional-navy py-6 font-sans text-[11px] font-bold tracking-[4px] text-white uppercase shadow-sm transition-all hover:opacity-90 disabled:opacity-20 rounded-none"
	>
		{loading ? 'Query wird ausgeführt...' : task.isLocked ? 'Aufgabe gesperrt' : task.isCompleted ? 'Abfrage bereits erfolgreich validiert' : 'SQL-Query ausführen'}
	</button>
</div>
