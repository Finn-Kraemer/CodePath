<script lang="ts">
	import { onDestroy, onMount } from 'svelte';
	import { api, type HelpQueue, type HelpQueueEntry } from '$lib/api';
	import { realtime } from '$lib/realtime.svelte';
	import { HELP_CATEGORY_LABELS, waitingTime } from '$lib/help';
	import { highlight } from '$lib/help/highlight';
	import { OUTCOME_LABELS } from '$lib/tasks/labels';

	/** Ab dieser Wartezeit wird ein Ruf hervorgehoben */
	const LONG_WAIT_SECONDS = 5 * 60;

	let queue = $state<HelpQueue | null>(null);
	let error = $state('');
	let busy = $state<number | null>(null);
	let messages = $state<Record<number, string>>({});
	let messageOpen = $state<number | null>(null);
	let timer: ReturnType<typeof setInterval> | undefined;

	async function load() {
		try {
			queue = await api<HelpQueue>('/api/admin/help');
			error = '';
		} catch (e) {
			error = (e as Error).message;
		}
	}

	onMount(() => {
		load();
		// Wartezeiten aktuell halten
		timer = setInterval(load, 30_000);
	});

	onDestroy(() => clearInterval(timer));

	$effect(() => {
		if (realtime.helpTrigger > 0) load();
	});

	async function act(entry: HelpQueueEntry, action: 'claim' | 'resolve') {
		busy = entry.id;
		try {
			await api(`/api/admin/help/${entry.id}/${action}`, { method: 'POST' });
			await load();
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = null;
		}
	}

	async function sendMessage(entry: HelpQueueEntry) {
		const text = (messages[entry.id] ?? '').trim();
		if (!text) return;
		busy = entry.id;
		try {
			await api(`/api/admin/help/${entry.id}/message`, { method: 'POST', body: { text } });
			messages[entry.id] = '';
			messageOpen = null;
			await load();
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = null;
		}
	}

	const RESOLUTION_LABELS: Record<string, string> = {
		TEACHER: 'erledigt',
		SELF_SOLVED: 'hat sich selbst gelöst',
		CANCELLED: 'hat sich erledigt (selbst abgebrochen)'
	};

	const button =
		'border px-4 py-2 font-mono text-[10px] font-bold tracking-widest uppercase disabled:opacity-40';
</script>

<svelte:head><title>Hilfe-Rufe | CodePath</title></svelte:head>

<div class="mx-auto max-w-5xl">
	<a
		href="/admin"
		class="mb-6 inline-flex font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
		>← Leitstand</a
	>
	<div class="mb-8 flex flex-wrap items-end justify-between gap-4 border-b border-slate-200 pb-6">
		<div>
			<h1
				class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase"
			>
				Hilfe-Rufe
			</h1>
			<p class="mt-2 text-sm text-slate-500">
				Wer „Ich hänge fest“ klickt, erscheint hier – live, nur für Lehrkräfte, nicht auf dem
				Beamer.
			</p>
		</div>
		<p class="font-mono text-sm font-bold text-institutional-navy" data-testid="help-count">
			{queue?.open.length ?? 0}
			{(queue?.open.length ?? 0) === 1 ? 'wartet' : 'warten'}
		</p>
	</div>

	{#if error}
		<p class="mb-6 border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
			{error}
		</p>
	{/if}

	{#if queue && queue.open.length === 0}
		<p class="border border-slate-200 bg-white p-10 text-center text-sm text-slate-500">
			Gerade hängt niemand fest.
		</p>
	{/if}

	<ol class="space-y-6" aria-label="Warteschlange">
		{#each queue?.open ?? [] as entry (entry.id)}
			{@const long = entry.status === 'OPEN' && entry.waitingSeconds >= LONG_WAIT_SECONDS}
			<li
				class="border-2 bg-white p-6 shadow-sm {long
					? 'border-rose-400'
					: entry.status === 'CLAIMED'
						? 'border-slate-200'
						: 'border-institutional-navy'}"
				data-testid="help-entry"
				data-user={entry.username}
			>
				<div class="flex flex-wrap items-start justify-between gap-4">
					<div>
						<p class="text-lg font-extrabold text-institutional-navy">
							{entry.displayName}
							{#if entry.displayName !== entry.username}
								<span class="font-mono text-xs font-normal text-slate-400">{entry.username}</span>
							{/if}
						</p>
						<p class="text-sm text-slate-600">
							{#if entry.courseName}{entry.courseName} ·
							{/if}{entry.moduleTitle} ·
							<a
								class="font-bold underline"
								href={`/modules/${entry.moduleSlug}/${entry.taskSlug}`}
								target="_blank"
								rel="noopener">{entry.taskTitle}</a
							>
						</p>
					</div>
					<div class="text-right font-mono text-xs">
						<p class={long ? 'font-black text-rose-700' : 'text-slate-500'}>
							wartet {waitingTime(entry.waitingSeconds)}{long ? ' – länger als 5 Min.' : ''}
						</p>
						{#if entry.status === 'CLAIMED'}
							<p class="mt-1 font-bold text-green-700">{entry.claimedBy ?? 'Lehrkraft'} kommt</p>
						{/if}
					</div>
				</div>

				<div class="mt-4 flex flex-wrap gap-2 font-mono text-[10px] text-slate-600 uppercase">
					{#if entry.category}
						<span class="border border-amber-300 bg-amber-50 px-2 py-1 text-amber-800"
							>{HELP_CATEGORY_LABELS[entry.category] ?? entry.category}</span
						>
					{/if}
					<span class="border border-slate-200 px-2 py-1">Tipps: {entry.hintsUsed}</span>
					<span class="border border-slate-200 px-2 py-1">Fehlversuche: {entry.failedAttempts}</span
					>
					{#if entry.lastOutcome}
						<span class="border border-slate-200 px-2 py-1"
							>Letzte Abgabe: {OUTCOME_LABELS[entry.lastOutcome] ?? entry.lastOutcome}</span
						>
					{/if}
				</div>

				{#if entry.note}
					<p class="mt-4 border-l-4 border-slate-300 bg-slate-50 p-3 text-sm">„{entry.note}“</p>
				{/if}
				{#if entry.draft}
					<p class="mt-4 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
						Aktueller Stand
					</p>
					<!-- eslint-disable-next-line svelte/no-at-html-tags -- nur escapter Text und hljs-Spans -->
					<pre
						class="help-code mt-1 max-h-80 overflow-auto bg-slate-900 p-4 font-mono text-xs text-slate-100"><code
							>{@html highlight(entry.draft, entry.taskType)}</code
						></pre>
				{/if}
				{#if entry.lastOutput}
					<p class="mt-3 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
						Letzte Ausgabe beim Ausprobieren
					</p>
					<pre
						class="mt-1 max-h-40 overflow-auto border border-rose-100 bg-rose-50 p-3 font-mono text-xs whitespace-pre-wrap text-rose-800">{entry.lastOutput}</pre>
				{/if}
				{#if entry.lastFeedback}
					<p class="mt-3 text-xs text-slate-500">
						Rückmeldung zur letzten Abgabe: {entry.lastFeedback}
					</p>
				{/if}
				{#if entry.teacherMessage}
					<p class="mt-3 text-xs text-slate-500">Deine Nachricht: „{entry.teacherMessage}“</p>
				{/if}

				<div class="mt-5 flex flex-wrap gap-2">
					{#if entry.status === 'OPEN'}
						<button
							type="button"
							class="{button} border-institutional-navy bg-institutional-navy text-white"
							disabled={busy === entry.id}
							onclick={() => act(entry, 'claim')}>Ich komme</button
						>
					{/if}
					<button
						type="button"
						class="{button} border-green-700 text-green-700"
						disabled={busy === entry.id}
						onclick={() => act(entry, 'resolve')}>Erledigt</button
					>
					<button
						type="button"
						class="{button} border-slate-300 text-slate-600"
						onclick={() => (messageOpen = messageOpen === entry.id ? null : entry.id)}
						>Nachricht schicken</button
					>
				</div>
				{#if messageOpen === entry.id}
					<form
						class="mt-3 flex gap-2"
						onsubmit={(e) => {
							e.preventDefault();
							sendMessage(entry);
						}}
					>
						<input
							class="flex-1 border border-slate-300 px-3 py-2 text-sm"
							maxlength="280"
							placeholder="z. B. Schau dir Tipp 2 an"
							aria-label={`Nachricht an ${entry.displayName}`}
							bind:value={messages[entry.id]}
						/>
						<button
							type="submit"
							class="{button} border-institutional-navy text-institutional-navy"
							disabled={busy === entry.id}>Senden</button
						>
					</form>
				{/if}
			</li>
		{/each}
	</ol>

	{#if queue?.recent.length}
		<h2
			class="mt-12 mb-4 font-mono text-[10px] font-black tracking-widest text-slate-400 uppercase"
		>
			Zuletzt erledigt (30 Min.)
		</h2>
		<ul class="divide-y divide-slate-100 border border-slate-200 bg-white text-sm">
			{#each queue.recent as entry (entry.id)}
				<li class="flex flex-wrap justify-between gap-2 px-4 py-3" data-testid="help-recent">
					<span><strong>{entry.displayName}</strong> · {entry.taskTitle}</span>
					<span class="font-mono text-xs text-slate-500"
						>{RESOLUTION_LABELS[entry.resolution ?? ''] ?? entry.status}</span
					>
				</li>
			{/each}
		</ul>
	{/if}
</div>

<style>
	.help-code :global(.hljs-keyword),
	.help-code :global(.hljs-built_in) {
		color: #c4b5fd;
	}
	.help-code :global(.hljs-string) {
		color: #86efac;
	}
	.help-code :global(.hljs-number),
	.help-code :global(.hljs-literal) {
		color: #fcd34d;
	}
	.help-code :global(.hljs-comment) {
		color: #94a3b8;
		font-style: italic;
	}
	.help-code :global(.hljs-title),
	.help-code :global(.hljs-function) {
		color: #7dd3fc;
	}
</style>
