<script lang="ts">
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';
	import { api, type Schemas } from '$lib/api';
	import { formatDate } from '$lib/evaluation/evaluation';

	type MyRound = Schemas['MyRound'];

	let rounds = $state<MyRound[]>([]);
	let content = $state('');
	let status = $state<{ enabled: boolean; alreadySubmitted: boolean } | null>(null);
	let loading = $state(true);
	let submitting = $state(false);
	let error = $state('');

	async function load() {
		try {
			const [list, res] = await Promise.all([
				api<MyRound[]>('/api/evaluations'),
				auth.apiFetch('/api/feedback/status')
			]);
			rounds = list;
			if (res.ok) status = await res.json();
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	}

	async function handleSubmit() {
		if (!content.trim()) return;
		if (!confirm('Nach dem Absenden kannst du deine Nachricht nicht mehr ändern. Abschicken?'))
			return;
		submitting = true;
		error = '';
		try {
			await api('/api/feedback', { method: 'POST', body: { content } });
			if (status) status.alreadySubmitted = true;
		} catch (e) {
			error = (e as Error).message;
		} finally {
			submitting = false;
		}
	}

	function todo(r: MyRound): number {
		if (!r.acceptingAnswers) return 0;
		return (r.hasSelf && !r.selfDone ? 1 : 0) + (r.hasLesson && !r.lessonDone ? 1 : 0);
	}

	onMount(load);
</script>

<svelte:head><title>Feedback | CodePath</title></svelte:head>

<div class="mx-auto max-w-3xl py-12">
	<header class="mb-10 border-b border-slate-200 pb-8">
		<h1 class="font-sans text-3xl font-extrabold tracking-tight text-institutional-navy">
			Feedback
		</h1>
		<p class="mt-2 text-slate-500">
			Schätze dich selbst ein, bewerte den Unterricht und lies die Rückmeldung deiner Lehrkraft.
		</p>
	</header>

	{#if error}
		<p class="mb-6 border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
			{error}
		</p>
	{/if}

	{#if loading}
		<div class="flex justify-center py-24">
			<div
				class="h-12 w-12 animate-spin border-4 border-slate-200 border-t-institutional-navy"
			></div>
		</div>
	{:else}
		<section aria-labelledby="rounds" class="mb-12">
			<h2
				id="rounds"
				class="mb-4 font-sans text-sm font-black tracking-wider text-institutional-navy uppercase"
			>
				Bewertungsbögen
			</h2>
			{#if rounds.length === 0}
				<p
					class="border border-dashed border-slate-200 bg-slate-50 p-10 text-center text-slate-400"
				>
					Zurzeit gibt es keine Bewertungsbögen.
				</p>
			{:else}
				<ul class="flex flex-col gap-3">
					{#each rounds as round (round.id)}
						{@const open = todo(round)}
						<li>
							<a
								href="/feedback/{round.id}"
								class="group flex flex-wrap items-center gap-4 border bg-white p-5 transition-colors hover:border-institutional-navy {open
									? 'border-institutional-gold'
									: 'border-slate-200'}"
							>
								<div class="min-w-0 flex-1">
									<p class="font-bold text-institutional-navy">{round.title}</p>
									<p class="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">
										{#if round.hasSelf}
											<span>{round.selfDone ? '✓' : '○'} Selbsteinschätzung</span>
										{/if}
										{#if round.hasLesson}
											<span>{round.lessonDone ? '✓' : '○'} Unterrichtsbewertung</span>
										{/if}
										{#if round.teacherState === 'VISIBLE'}
											<span class="font-bold text-emerald-700"
												>Rückmeldung der Lehrkraft ist da</span
											>
										{/if}
									</p>
								</div>
								<span
									class="px-2 py-1 font-mono text-[11px] font-bold uppercase {round.acceptingAnswers
										? open
											? 'bg-amber-100 text-amber-800'
											: 'bg-emerald-100 text-emerald-800'
										: 'bg-slate-100 text-slate-500'}"
								>
									{round.acceptingAnswers ? (open ? `${open} offen` : 'Erledigt') : 'Abgeschlossen'}
								</span>
								{#if round.acceptingAnswers && round.closesAt}
									<span class="w-full font-mono text-[11px] text-slate-400"
										>Bis {formatDate(round.closesAt)}</span
									>
								{/if}
							</a>
						</li>
					{/each}
				</ul>
			{/if}
		</section>

		<section aria-labelledby="message">
			<h2
				id="message"
				class="mb-4 font-sans text-sm font-black tracking-wider text-institutional-navy uppercase"
			>
				Nachricht an die Lehrkraft
			</h2>
			{#if !status?.enabled}
				<p
					class="border border-dashed border-slate-200 bg-slate-50 p-10 text-center text-slate-400"
				>
					Die Feedback-Nachricht ist zurzeit geschlossen.
				</p>
			{:else if status.alreadySubmitted}
				<p
					class="border border-green-200 bg-green-50 p-6 text-center text-sm font-bold text-green-800"
				>
					Danke! Deine Nachricht ist angekommen.
				</p>
			{:else}
				<div class="rounded-[var(--radius-ui)] border border-slate-200 bg-white p-6">
					<p class="mb-4 text-sm text-slate-500">
						Deine Nachricht wird deinem Profil zugeordnet und kann nur einmal abgeschickt werden.
					</p>
					<label for="feedback" class="ed-label">Deine Nachricht</label>
					<textarea
						id="feedback"
						bind:value={content}
						maxlength="5000"
						placeholder="Was hat dir gefallen? Was können wir besser machen?"
						class="ed-input min-h-48"
						disabled={submitting}
					></textarea>
					<button
						onclick={handleSubmit}
						disabled={submitting || !content.trim()}
						class="mt-4 w-full rounded-[var(--radius-ui-sm)] bg-institutional-navy py-4 text-sm font-semibold text-white disabled:opacity-30"
					>
						{submitting ? 'Wird gesendet …' : 'Nachricht abschicken'}
					</button>
				</div>
			{/if}
		</section>
	{/if}
</div>
