<script lang="ts">
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';
	import FeedbackNav from '$lib/evaluation/FeedbackNav.svelte';

	interface Feedback {
		id: number;
		username: string;
		displayName: string;
		content: string;
		submittedAt: string;
	}

	let feedbacks = $state<Feedback[]>([]);
	let enabled = $state(false);
	let loading = $state(true);
	let toggling = $state(false);

	async function fetchData() {
		try {
			const [fRes, sRes] = await Promise.all([
				auth.apiFetch('/api/admin/feedback'),
				auth.apiFetch('/api/admin/feedback/status')
			]);
			if (fRes.ok) feedbacks = await fRes.json();
			if (sRes.ok) {
				const sData = await sRes.json();
				enabled = sData.enabled;
			}
		} catch (e) {
			console.error(e);
		} finally {
			loading = false;
		}
	}

	async function toggleStatus() {
		toggling = true;
		try {
			const res = await auth.apiFetch('/api/admin/feedback/toggle', { method: 'PUT' });
			if (res.ok) {
				const data = await res.json();
				enabled = data.enabled;
			}
		} catch (e) {
			console.error(e);
		} finally {
			toggling = false;
		}
	}

	onMount(fetchData);

	function formatDate(dateStr: string) {
		return new Date(dateStr).toLocaleString('de-DE');
	}
</script>

<svelte:head><title>Feedback-Nachrichten | CodePath</title></svelte:head>

<div class="mx-auto flex max-w-7xl flex-col pb-24">
	<a
		href="/admin"
		class="mb-6 inline-flex font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
		>← Leitstand</a
	>
	<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
		Feedback &amp; Bewertung
	</h1>
	<p class="mt-2 mb-8 text-sm text-slate-500">
		Freie Nachrichten der Klasse an dich – je Person einmal, solange die Runde geöffnet ist.
	</p>
	<FeedbackNav />

	<div class="mb-8 flex flex-wrap items-center gap-6 border border-slate-200 bg-slate-50 p-6">
		<div class="flex flex-1 flex-col">
			<span class="font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase"
				>Nachrichten-Runde</span
			>
			<span
				class="font-sans text-xs font-black uppercase {enabled
					? 'text-teal-600'
					: 'text-slate-400'}"
			>
				{enabled ? 'Aktiv: Schüler können schreiben' : 'Inaktiv: Gesperrt'}
			</span>
		</div>
		<button
			onclick={toggleStatus}
			disabled={toggling}
			class="px-6 py-3 font-mono text-[10px] font-bold tracking-widest text-white uppercase disabled:opacity-50 {enabled
				? 'bg-rose-600 hover:bg-rose-700'
				: 'bg-teal-600 hover:bg-teal-700'}"
		>
			{toggling ? '…' : enabled ? 'Stoppen' : 'Starten'}
		</button>
	</div>

	{#if loading}
		<div class="flex justify-center py-24">
			<div
				class="h-12 w-12 animate-spin border-4 border-slate-200 border-t-institutional-navy"
			></div>
		</div>
	{:else}
		<div class="grid grid-cols-1 gap-8 md:grid-cols-2 lg:grid-cols-3">
			{#each feedbacks as f (f.id)}
				<div
					class="flex h-full flex-col rounded-none border border-slate-200 bg-white p-8 shadow-sm transition-all hover:shadow-md"
				>
					<div class="mb-6 flex items-center justify-between border-b border-slate-50 pb-4">
						<div class="flex flex-col">
							<span class="font-sans text-sm font-bold text-institutional-navy"
								>{f.displayName || f.username}</span
							>
							<span class="font-mono text-[9px] tracking-widest text-slate-400 uppercase"
								>@{f.username}</span
							>
						</div>
						<span class="font-mono text-[9px] text-slate-400">{formatDate(f.submittedAt)}</span>
					</div>
					<div class="prose prose-sm flex-grow">
						<p class="font-serif leading-relaxed whitespace-pre-line text-slate-600 italic">
							"{f.content}"
						</p>
					</div>
				</div>
			{:else}
				<div
					class="col-span-full border border-dashed border-slate-200 bg-slate-50 p-24 text-center"
				>
					<p class="font-mono text-sm text-slate-400 italic">Noch keine Rückmeldungen vorhanden.</p>
				</div>
			{/each}
		</div>
	{/if}
</div>
