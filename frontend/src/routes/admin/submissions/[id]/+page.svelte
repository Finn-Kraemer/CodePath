<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
	import { api, type SubmissionDetail } from '$lib/api';
	import PracticeReview from '$lib/components/editor/PracticeReview.svelte';
	import PythonWorkbench from '$lib/components/PythonWorkbench.svelte';
	import Markdown from '$lib/Markdown.svelte';

	const id = page.params.id;
	let submission = $state<SubmissionDetail | null>(null);
	let scores = $state<Record<string, number>>({});
	let comment = $state('');
	let lockTask = $state(false);
	let error = $state('');
	let busy = $state(false);

	const total = $derived(Object.values(scores).reduce((a, b) => a + (Number(b) || 0), 0));

	onMount(async () => {
		try {
			submission = await api<SubmissionDetail>(`/api/admin/submissions/${id}`);
			comment = submission.comment ?? '';
			for (const c of submission.rubric) scores[c.id] = submission.scores?.[c.id] ?? c.points;
		} catch (e) {
			error = (e as Error).message;
		}
	});

	async function approve() {
		busy = true;
		try {
			submission = await api<SubmissionDetail>(`/api/admin/submissions/${id}/approve`, {
				method: 'PUT',
				body: { scores, comment }
			});
			goto('/admin/submissions');
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	async function reject() {
		if (!comment.trim()) {
			error = 'Bitte einen Kommentar schreiben, damit klar ist, was zu überarbeiten ist.';
			return;
		}
		busy = true;
		try {
			await api(`/api/admin/submissions/${id}/reject`, {
				method: 'PUT',
				body: { comment, lockTask }
			});
			goto('/admin/submissions');
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}
</script>

<div class="mx-auto flex max-w-6xl flex-col gap-10 pb-24">
	<a
		href="/admin/submissions"
		class="font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
		>← Zurück zu den Abgaben</a
	>
	{#if error}<div class="border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
			{error}
		</div>{/if}
	{#if submission}
		<header class="border-b border-slate-200 pb-6">
			<p class="font-mono text-[10px] tracking-widest text-slate-400 uppercase">
				{submission.moduleTitle}
			</p>
			<h1
				class="font-sans text-3xl font-extrabold tracking-tight text-institutional-navy uppercase"
			>
				{submission.taskTitle}
			</h1>
			<p class="mt-2 text-sm text-slate-500">
				{submission.displayName} (@{submission.username}) · {new Date(
					submission.submittedAt
				).toLocaleString('de-DE')} · Status: {submission.status}
				{#if submission.hintsUsed > 0}· {submission.hintsUsed} Hinweis(e) genutzt (Punktabzug wird automatisch
					berechnet){/if}
			</p>
		</header>

		<div class="grid grid-cols-1 gap-10 lg:grid-cols-5">
			<div class="space-y-6 lg:col-span-3">
				<details class="border border-slate-200 bg-white p-6 text-sm">
					<summary class="cursor-pointer font-bold">Aufgabenstellung</summary>
					<Markdown source={submission.instructions} class="mt-4" />
				</details>
				<PracticeReview content={submission.content} />
				{#if submission.code}
					<section class="border border-slate-200 bg-white p-6" aria-label="Abgegebener Code">
						<p
							class="mb-3 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
						>
							Abgegebener Code – hier ausprobieren, Änderungen werden nicht gespeichert
						</p>
						<PythonWorkbench initialCode={submission.code} />
					</section>
				{/if}
			</div>

			<div class="space-y-6 lg:col-span-2">
				<div class="border border-slate-200 bg-white p-6">
					<h2
						class="mb-4 font-mono text-[10px] font-black tracking-widest text-slate-400 uppercase"
					>
						Bewertung nach Rubrik
					</h2>
					<div class="space-y-4">
						{#each submission.rubric as criterion (criterion.id)}
							<label class="block text-sm">
								<span class="flex justify-between"
									><span>{criterion.label}</span><span class="font-mono text-slate-400"
										>max. {criterion.points}</span
									></span
								>
								<input
									type="number"
									min="0"
									max={criterion.points}
									bind:value={scores[criterion.id]}
									class="mt-1 w-full border-slate-200 font-mono"
								/>
							</label>
						{/each}
					</div>
					<p class="mt-4 border-t border-slate-100 pt-4 text-right font-mono text-sm font-bold">
						{total} / {submission.maxPoints} Punkte
					</p>
				</div>
				<label class="block text-sm">
					<span class="font-mono text-[10px] font-black tracking-widest text-slate-400 uppercase"
						>Kommentar für die Schülerin, den Schüler</span
					>
					<textarea bind:value={comment} rows="4" class="mt-2 w-full border-slate-200"></textarea>
				</label>
				<button
					onclick={approve}
					disabled={busy}
					class="w-full bg-green-700 py-4 text-xs font-black tracking-[3px] text-white uppercase disabled:opacity-50"
					>Annehmen ({total} Punkte)</button
				>
				<div class="border border-rose-200 p-4">
					<label class="mb-3 flex items-center gap-2 text-sm"
						><input type="checkbox" bind:checked={lockTask} /> Aufgabe danach endgültig sperren</label
					>
					<button
						onclick={reject}
						disabled={busy || submission.status === 'APPROVED'}
						class="w-full border-2 border-rose-500 py-3 text-xs font-black tracking-[3px] text-rose-600 uppercase disabled:opacity-40"
						>Zur Überarbeitung zurückgeben</button
					>
				</div>
			</div>
		</div>
	{/if}
</div>
