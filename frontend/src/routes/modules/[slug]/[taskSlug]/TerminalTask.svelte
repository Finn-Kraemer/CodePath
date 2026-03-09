<script lang="ts">
	import { onMount, tick } from 'svelte';
	import { auth } from '$lib/auth.svelte';

	let { task, moduleSlug, supportUsed = false } = $props();

	let history = $state<{ type: 'cmd' | 'out' | 'err'; text: string }[]>([]);
	let currentInput = $state('');
	let terminalElement = $state<HTMLElement | null>(null);
	let result = $state<{ correct: boolean; feedback: string; isLocked?: boolean } | null>(null);
	let loading = $state(false);

	// Virtual state
	let currentPath = $state(['~']);
	let fs = $state<Set<string>>(new Set(task.config.initialFs || []));

	onMount(() => {
		history = [{ type: 'out', text: 'CodePath Terminal v1.1.0 - Geben Sie "help" für Befehle ein.' }];
	});

	function getDisplayPath() {
		return currentPath.join('/');
	}

	function getFullPath(name: string) {
		const prefix = currentPath.slice(1).join('/');
		return prefix ? `${prefix}/${name}` : name;
	}

	async function handleKeyDown(e: KeyboardEvent) {
		if (e.key === 'Enter') {
			const cmd = currentInput.trim();
			if (!cmd) return;

			history = [...history, { type: 'cmd', text: cmd }];
			processCommand(cmd);
			currentInput = '';
			await tick();
			if (terminalElement) terminalElement.scrollTop = terminalElement.scrollHeight;
		}
	}

	function processCommand(cmd: string) {
		const parts = cmd.split(/\s+/);
		const action = parts[0].toLowerCase();
		const args = parts.slice(1);

		switch (action) {
			case 'help':
				history = [...history, { type: 'out', text: 'Verfügbare Befehle: ls, cd, mkdir, touch, rm, pwd, clear, submit' }];
				break;
			case 'ls':
				const prefix = currentPath.slice(1).join('/');
				const visible = Array.from(fs).filter(item => {
					if (!prefix) return !item.includes('/');
					return item.startsWith(prefix + '/') && !item.slice(prefix.length + 1).includes('/');
				}).map(item => prefix ? item.slice(prefix.length + 1) : item);
				
				history = [...history, { type: 'out', text: visible.join('  ') || '(leer)' }];
				break;
			case 'pwd':
				history = [...history, { type: 'out', text: getDisplayPath() }];
				break;
			case 'cd':
				const target = args[0];
				if (!target || target === '~') {
					currentPath = ['~'];
				} else if (target === '..') {
					if (currentPath.length > 1) currentPath = currentPath.slice(0, -1);
				} else {
					const pathToCheck = getFullPath(target);
					// In our simple simulation, directories end with / or we just check if any file starts with it
					const exists = Array.from(fs).some(item => item === pathToCheck || item.startsWith(pathToCheck + '/'));
					if (exists) {
						currentPath = [...currentPath, target];
					} else {
						history = [...history, { type: 'err', text: `cd: Verzeichnis nicht gefunden: ${target}` }];
					}
				}
				break;
			case 'mkdir':
			case 'touch':
				if (args[0]) {
					const newPath = getFullPath(args[0]);
					fs.add(newPath);
					history = [...history, { type: 'out', text: `Erstellt: ${args[0]}` }];
				}
				break;
			case 'rm':
				if (args[0]) {
					const pathToRemove = getFullPath(args[0]);
					if (fs.has(pathToRemove)) {
						fs.delete(pathToRemove);
						history = [...history, { type: 'out', text: `Gelöscht: ${args[0]}` }];
					} else {
						history = [...history, { type: 'err', text: `rm: Datei oder Verzeichnis nicht gefunden: ${args[0]}` }];
					}
				}
				break;
			case 'clear':
				history = [];
				break;
			case 'submit':
				handleSubmit();
				break;
			default:
				history = [...history, { type: 'err', text: `Befehl nicht gefunden: ${action}` }];
		}
	}

	async function handleSubmit() {
		if (task.isLocked) return;
		loading = true;
		
		// Create a "state string" representing the FS content (all files/folders)
		const finalState = Array.from(fs).sort().join(',');

		try {
			const res = await auth.apiFetch(`/api/modules/${moduleSlug}/${task.slug}/submit`, {
				method: 'POST',
				body: JSON.stringify({ 
					payload: { finalState },
					supportUsed
				})
			});
			const data = await res.json();
			result = { correct: data.isCorrect, feedback: data.feedback, isLocked: data.isLocked };
			
			if (data.isCorrect) {
				task.isCompleted = true;
				history = [...history, { type: 'out', text: 'VALIDIERUNG ERFOLGREICH!' }];
			} else {
				history = [...history, { type: 'err', text: 'VALIDIERUNG FEHLGESCHLAGEN: ' + data.feedback }];
                if (data.isLocked) task.isLocked = true;
			}
		} catch (e) {
			history = [...history, { type: 'err', text: 'Systemfehler bei der Übermittlung.' }];
		} finally {
			loading = false;
		}
	}
</script>

<div class="border border-slate-200 bg-white p-10 shadow-sm rounded-none">
	<h3 class="mb-8 font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase text-balance">
		Terminal Simulator:
	</h3>

	<div
		bind:this={terminalElement}
		class="mb-8 h-80 w-full overflow-y-auto border-4 border-slate-800 bg-slate-900 p-6 font-mono text-xs leading-relaxed text-teal-400 rounded-none shadow-inner"
	>
		{#each history as line}
			<div class="mb-1">
				{#if line.type === 'cmd'}
					<span class="text-institutional-gold font-bold">visitor@codepath:{getDisplayPath()}$</span> {line.text}
				{:else if line.type === 'err'}
					<span class="text-rose-500">{line.text}</span>
				{:else}
					{line.text}
				{/if}
			</div>
		{/each}
		
		{#if !task.isCompleted && !task.isLocked}
			<div class="flex items-center gap-2">
				<span class="text-institutional-gold font-bold">visitor@codepath:{getDisplayPath()}$</span>
				<input
					bind:value={currentInput}
					onkeydown={handleKeyDown}
					type="text"
					class="flex-1 bg-transparent text-teal-400 outline-none border-0 p-0 m-0 focus:ring-0"
					autofocus
				/>
			</div>
		{/if}
	</div>

	<div class="flex flex-col md:flex-row justify-between items-center gap-6">
		<p class="text-[10px] text-slate-400 uppercase tracking-widest font-bold italic">Tippen Sie "submit" zum Absenden</p>
		<button
			onclick={handleSubmit}
			disabled={loading || task.isCompleted || task.isLocked}
			class="bg-institutional-navy px-10 py-4 font-sans text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:opacity-90 disabled:opacity-20 rounded-none"
		>
			{loading ? 'Validierung...' : task.isLocked ? 'Terminal gesperrt' : task.isCompleted ? 'Aufgabe gelöst' : 'Lösung einreichen'}
		</button>
	</div>
</div>
