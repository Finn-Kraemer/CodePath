<script lang="ts">
	import PracticeEditor from '$lib/components/editor/PracticeEditor.svelte';
	import Markdown from '$lib/Markdown.svelte';
	import SubmitButton from './SubmitButton.svelte';
	import { config, draftKey, type TaskProps } from './types';
	import { clearDraft, loadDraft, saveDraft } from '$lib/drafts';

	let { task, disabled, busy, submit }: TaskProps = $props();

	type Criterion = { id: string; label: string; points: number };
	const cfg = $derived(
		config<{ instructions: string; submissionNote: string; rubric: Criterion[] }>(task)
	);
	const pending = $derived(task.status === 'PENDING_REVIEW');
	const initial =
		(task.lastAnswer?.content as string | undefined) ?? loadDraft<string>(draftKey(task)) ?? '';
	let content = $state(initial);

	async function send() {
		await submit({ content });
		clearDraft(draftKey(task));
	}
</script>

<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
	<div class="mb-8 border-l-4 border-amber-400 bg-amber-50 p-6 text-sm text-amber-900">
		<Markdown source={cfg.instructions} />
		{#if cfg.submissionNote}<p class="mt-4 text-xs italic">{cfg.submissionNote}</p>{/if}
	</div>
	<div class="mb-8">
		<p class="mb-3 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
			Bewertet wird nach
		</p>
		<ul class="space-y-1 text-sm">
			{#each cfg.rubric as criterion (criterion.id)}
				<li class="flex justify-between border-b border-slate-100 py-1">
					<span>{criterion.label}</span><span class="font-mono text-slate-500"
						>{criterion.points} P.</span
					>
				</li>
			{/each}
		</ul>
	</div>
	{#if pending}
		<div
			class="mb-8 border border-blue-200 bg-blue-50 p-6 text-xs font-bold tracking-widest text-blue-700 uppercase"
		>
			Abgabe liegt bei der Lehrkraft zur Bewertung
		</div>
	{/if}
	<div class="mb-8">
		<PracticeEditor
			initialContent={initial}
			disabled={disabled || pending}
			onchange={(html) => {
				content = html;
				saveDraft(draftKey(task), html);
			}}
		/>
	</div>
	<SubmitButton
		{busy}
		disabled={disabled || pending || !content.replace(/<[^>]*>/g, '').trim()}
		label="Zur Bewertung einreichen"
		onclick={send}
	/>
</div>
