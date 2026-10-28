<script lang="ts">
	import { api, ApiError } from '$lib/api';
	import type { components } from '$lib/api/schema';
	import type { Config } from '../types';

	type SolutionRun = components['schemas']['SolutionRun'];

	let { config = $bindable(), solution = '' }: { config: Config; solution?: string } = $props();

	let running = $state(false);
	let message = $state('');

	// Erwartete Ausgaben aus der Musterlösung übernehmen – läuft im Code-Runner des Servers
	async function fillFromSolution() {
		running = true;
		message = '';
		try {
			const result = await api<SolutionRun>('/api/admin/content/run-solution', {
				method: 'POST',
				body: { code: solution, tests: config.tests ?? [] }
			});
			if (!result.available) {
				message = 'Kein Code-Runner eingerichtet – Ausgaben bitte von Hand eintragen.';
				return;
			}
			const failed = result.outputs.findIndex((o) => o.status !== 'OK');
			if (failed >= 0) {
				const o = result.outputs[failed];
				message = `Test ${failed + 1}: ${o.status === 'TIMEOUT' ? 'Zeitlimit' : o.stderr.trim().split('\n').pop()}`;
				return;
			}
			config.tests = config.tests.map((t: Config, i: number) => ({
				...t,
				expectedOutput: result.outputs[i].stdout
			}));
			message = 'Ausgaben übernommen.';
		} catch (e) {
			message = e instanceof ApiError ? e.message : 'Fehler beim Ausführen.';
		} finally {
			running = false;
		}
	}
</script>

<p class="ed-label">Testfälle (versteckte prüfen, ob die Lösung allgemein ist)</p>
<p class="mt-1 text-xs text-gray-500">
	„Angehängter Code“ läuft nach der Abgabe, z. B. <code>print(funktion(5))</code> – so fällt auf, wenn
	jemand nur die sichtbare Ausgabe fest einprogrammiert.
</p>
<div class="mt-2 space-y-2">
	{#each config.tests ?? [] as test, i (i)}
		<div class="grid grid-cols-12 gap-2">
			<textarea
				class="ed-input mono col-span-3 mt-0 text-xs"
				rows="2"
				bind:value={test.stdin}
				placeholder="Eingabe (stdin)"
				aria-label="Eingabe"
			></textarea>
			<textarea
				class="ed-input mono col-span-3 mt-0 text-xs"
				rows="2"
				bind:value={test.append}
				placeholder="Angehängter Code"
				aria-label="Angehängter Code"
			></textarea>
			<textarea
				class="ed-input mono col-span-3 mt-0 text-xs"
				rows="2"
				bind:value={test.expectedOutput}
				placeholder="Erwartete Ausgabe"
				aria-label="Erwartete Ausgabe"
			></textarea>
			<label class="col-span-2 flex items-center gap-1 text-xs">
				<input
					type="checkbox"
					checked={test.hidden !== false}
					onchange={(e) => (test.hidden = e.currentTarget.checked)}
				/> versteckt
			</label>
			<button
				type="button"
				class="ed-button col-span-1"
				onclick={() => (config.tests = config.tests.filter((_: unknown, j: number) => j !== i))}
				aria-label="Test entfernen">✕</button
			>
		</div>
	{/each}
	<div class="flex flex-wrap items-center gap-2">
		<button
			type="button"
			class="ed-button"
			onclick={() => (config.tests = [...(config.tests ?? []), { stdin: '', expectedOutput: '' }])}
			>+ Testfall</button
		>
		<button
			type="button"
			class="ed-button"
			disabled={running || !solution?.trim() || !(config.tests ?? []).length}
			onclick={fillFromSolution}
			>{running ? 'Läuft …' : 'Ausgaben aus Musterlösung erzeugen'}</button
		>
		{#if message}<span class="text-xs text-gray-600" role="status">{message}</span>{/if}
	</div>
</div>
