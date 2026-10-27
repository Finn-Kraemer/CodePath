<script lang="ts">
	import { onDestroy, onMount } from 'svelte';
	import SubmitButton from './SubmitButton.svelte';
	import { config, draftKey, solution, type TaskProps } from './types';
	import { createEditor } from '$lib/monaco';
	import { loadDraft, saveDraft } from '$lib/drafts';

	let { task, disabled, busy, result, submit }: TaskProps = $props();

	const cfg = $derived(config<{ starterSql: string; tables: string[] }>(task));
	const sol = $derived(solution<{ query: string }>(task));
	const table = $derived(
		result?.details?.result as
			| { columns: string[]; rows: unknown[][]; rowCount: number }
			| undefined
	);

	let element = $state<HTMLElement | null>(null);
	let editor: any = null;
	let query = $state('');

	onMount(async () => {
		query =
			(task.lastAnswer?.query as string | undefined) ??
			loadDraft<string>(draftKey(task)) ??
			cfg.starterSql ??
			'';
		if (element) {
			editor = await createEditor(element, {
				value: query,
				language: 'sql',
				readOnly: disabled,
				onChange: (value) => {
					query = value;
					saveDraft(draftKey(task), value);
				}
			});
		}
	});

	onDestroy(() => editor?.dispose());
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	{#if cfg.tables?.length}
		<p class="mb-4 font-mono text-xs text-slate-500">Tabellen: {cfg.tables.join(', ')}</p>
	{/if}
	<div
		bind:this={element}
		class="mb-6 h-56 w-full border-4 border-slate-800 bg-slate-900"
		aria-label="SQL-Editor"
	></div>
	{#if table}
		<div class="mb-6 overflow-x-auto">
			<p class="mb-2 font-mono text-[10px] text-slate-400 uppercase">
				Dein Ergebnis ({table.rowCount} Zeilen)
			</p>
			<table class="w-full border-collapse font-mono text-xs">
				<thead
					><tr
						>{#each table.columns as column, i (i)}<th class="border bg-slate-100 p-2 text-left"
								>{column}</th
							>{/each}</tr
					></thead
				>
				<tbody>
					{#each table.rows as row, r (r)}
						<tr
							>{#each row as cell, c (c)}<td class="border p-2">{cell ?? 'NULL'}</td>{/each}</tr
						>
					{/each}
				</tbody>
			</table>
		</div>
	{/if}
	{#if sol}
		<pre class="mb-6 bg-green-50 p-4 font-mono text-xs text-green-800">{sol.query}</pre>
	{/if}
	<SubmitButton
		{busy}
		disabled={disabled || !query.trim()}
		label="Abfrage ausführen"
		onclick={() => submit({ query })}
	/>
</div>
