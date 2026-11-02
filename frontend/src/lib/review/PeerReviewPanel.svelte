<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type PeerState } from '$lib/api';
	import PracticeReview from '$lib/components/editor/PracticeReview.svelte';

	/**
	 * Peer-Review: Nach der eigenen Abgabe bewertet man anonym zwei Abgaben anderer nach der Rubrik
	 * und sieht die Rückmeldungen, die man selbst bekommen hat. Keine Punkte – nur zum Lernen.
	 */
	let { moduleSlug, taskSlug }: { moduleSlug: string; taskSlug: string } = $props();

	let peer = $state<PeerState | null>(null);
	let drafts = $state<Record<number, { scores: Record<string, number>; comment: string }>>({});
	let error = $state('');
	let busy = $state(false);
	const base = $derived(`/api/peer-reviews/${moduleSlug}/${taskSlug}`);

	function adopt(state: PeerState) {
		peer = state;
		for (const a of state.assignments) {
			if (!drafts[a.id]) {
				drafts[a.id] = {
					scores: Object.fromEntries(state.rubric.map((c) => [c.id, a.scores?.[c.id] ?? 0])),
					comment: a.comment ?? ''
				};
			}
		}
	}

	async function load() {
		try {
			adopt(await api<PeerState>(base));
		} catch (e) {
			error = (e as Error).message;
		}
	}

	onMount(load);

	async function assign() {
		busy = true;
		error = '';
		try {
			adopt(await api<PeerState>(`${base}/assign`, { method: 'POST' }));
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	async function send(id: number) {
		busy = true;
		error = '';
		try {
			adopt(
				await api<PeerState>(`/api/peer-reviews/assignments/${id}`, {
					method: 'PUT',
					body: drafts[id]
				})
			);
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	const total = (id: number) =>
		Object.values(drafts[id]?.scores ?? {}).reduce((a, b) => a + (Number(b) || 0), 0);
</script>

{#if peer?.enabled}
	<section class="mt-8 border border-slate-200 bg-white p-8 shadow-sm" data-testid="peer-review">
		<h2
			class="mb-2 font-mono text-[10px] font-black tracking-widest text-institutional-navy uppercase"
		>
			Peer-Review
		</h2>
		<p class="mb-6 text-sm text-slate-600">
			Bewerte zwei Abgaben deiner Mitschüler nach derselben Rubrik wie die Lehrkraft – anonym, ohne
			Punkte. So siehst du andere Lösungen und lernst, worauf es ankommt.
		</p>

		{#if peer.received.length}
			<div
				class="mb-8 border-l-4 border-institutional-gold bg-amber-50/60 p-4"
				data-testid="peer-received"
			>
				<p class="mb-2 text-sm font-bold text-institutional-navy">
					Rückmeldungen zu deiner Abgabe ({peer.received.length})
				</p>
				<ul class="space-y-2 text-sm">
					{#each peer.received as feedback, i (i)}
						<li>
							<span class="font-mono font-bold">{feedback.total} / {peer.maxPoints}</span>
							{#if feedback.comment}– „{feedback.comment}“{/if}
						</li>
					{/each}
				</ul>
			</div>
		{/if}

		{#each peer.assignments as assignment, n (assignment.id)}
			<article class="mb-8 border-t border-slate-100 pt-6" data-testid="peer-assignment">
				<p class="mb-3 font-mono text-xs font-bold text-slate-500">
					Abgabe {n + 1}
					{#if assignment.submitted}<span class="ml-2 text-green-700">✓ bewertet</span>{/if}
				</p>
				<PracticeReview content={assignment.content} />
				{#if assignment.code}
					<pre
						class="mt-3 max-h-80 overflow-auto bg-slate-900 p-4 font-mono text-xs text-slate-100">{assignment.code}</pre>
				{/if}
				{#if drafts[assignment.id]}
					<div class="mt-4 space-y-3">
						{#each peer.rubric as criterion (criterion.id)}
							<label class="flex items-center justify-between gap-4 text-sm">
								<span>{criterion.label}</span>
								<span class="flex items-center gap-2 font-mono">
									<input
										type="number"
										min="0"
										max={criterion.points}
										class="w-20 border border-slate-300 px-2 py-1"
										bind:value={drafts[assignment.id].scores[criterion.id]}
										aria-label={`${criterion.label} (Abgabe ${n + 1})`}
									/>
									/ {criterion.points}
								</span>
							</label>
						{/each}
						<label class="block text-sm"
							>Was ist gut, was fehlt noch? (optional)
							<textarea
								class="mt-1 h-20 w-full border border-slate-300 p-2"
								maxlength="1000"
								bind:value={drafts[assignment.id].comment}
							></textarea>
						</label>
						<button
							type="button"
							class="bg-institutional-navy px-6 py-3 font-mono text-[10px] font-black tracking-widest text-white uppercase disabled:opacity-40"
							disabled={busy}
							onclick={() => send(assignment.id)}
						>
							{assignment.submitted ? 'Bewertung ändern' : 'Bewertung abschicken'} ({total(
								assignment.id
							)} / {peer.maxPoints})
						</button>
					</div>
				{/if}
			</article>
		{/each}

		{#if peer.canReview}
			<button
				type="button"
				class="border-2 border-institutional-navy px-6 py-3 font-mono text-[10px] font-black tracking-widest text-institutional-navy uppercase disabled:opacity-40"
				disabled={busy}
				onclick={assign}
			>
				{peer.assignments.length ? 'Weitere Abgabe bewerten' : 'Abgaben anderer bewerten'}
			</button>
		{:else if peer.reason && peer.assignments.length < 2}
			<p class="text-sm text-slate-500">{peer.reason}</p>
		{/if}
		{#if error}<p class="mt-3 text-sm text-rose-700" role="alert">{error}</p>{/if}
	</section>
{/if}
