<!-- frontend/src/routes/admin/students/[username]/submissions/+page.svelte -->
<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { auth } from '$lib/auth.svelte';
	import PracticeReview from '$lib/components/editor/PracticeReview.svelte';

	const username = page.params.username;

	interface Submission {
		id: number;
		username: string;
		displayName: string;
		taskTitle: string;
		moduleTitle: string;
		submittedAt: string;
		content: string;
		status: 'PENDING' | 'APPROVED' | 'REJECTED';
		adminComment?: string;
	}

	let submissions = $state<Submission[]>([]);
	let loading = $state(true);

	async function fetchSubmissions() {
		try {
			const res = await auth.apiFetch(`/api/admin/students/${username}/submissions`);
			if (res.ok) {
				submissions = await res.json();
			}
		} catch (e) {
			console.error(e);
		} finally {
			loading = false;
		}
	}

	async function approve(id: number, halfPoints: boolean = false) {
		try {
			const res = await auth.apiFetch(`/api/admin/submissions/${id}/approve`, {
				method: 'PUT',
				body: JSON.stringify({ halfPoints })
			});
			if (res.ok) {
				const index = submissions.findIndex(s => s.id === id);
				if (index !== -1) {
					submissions[index] = { ...submissions[index], status: 'APPROVED' };
				}
			}
		} catch (e) {
			console.error(e);
		}
	}

	async function reject(id: number, lockTask: boolean = false) {
		const msg = lockTask 
			? 'Geben Sie einen Grund für die endgültige Sperrung an:' 
			: 'Möchten Sie diese Abgabe wirklich ablehnen? Geben Sie einen optionalen Grund an:';
		
		const comment = prompt(msg);
		if (comment === null) return;

		try {
			const res = await auth.apiFetch(`/api/admin/submissions/${id}/reject`, {
				method: 'DELETE',
				body: JSON.stringify({ comment, lockTask })
			});
			if (res.ok) {
				const index = submissions.findIndex(s => s.id === id);
				if (index !== -1) {
					submissions[index] = { ...submissions[index], status: 'REJECTED', adminComment: comment };
				}
			}
		} catch (e) {
			console.error(e);
		}
	}

	onMount(fetchSubmissions);
</script>

<div class="flex flex-col gap-12">
	<header class="border-b border-slate-200 pb-8">
		<a
			href={`/admin/students/${username}`}
			class="mb-6 inline-block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase transition-colors hover:text-institutional-navy"
			>← Zurück zur Teilnehmer-Akte: {username}</a
		>
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Leistungs-Nachweise
		</h1>
		<p class="mt-4 text-slate-500">
			Historie der praktischen Abgaben für Teilnehmer <span class="font-bold text-institutional-navy"
				>@{username}</span
			>.
		</p>
	</header>

	{#if loading}
		<div class="flex justify-center py-24">
			<div class="h-12 w-12 border-4 border-slate-200 border-t-institutional-navy animate-spin"></div>
		</div>
	{:else if submissions.length === 0}
		<div class="border border-dashed border-slate-200 bg-white p-24 text-center rounded-none">
			<p class="font-serif text-sm text-slate-400 italic">
				Keine Abgaben für diesen Teilnehmer vorhanden.
			</p>
		</div>
	{:else}
		<div class="grid grid-cols-1 gap-8">
			{#each submissions as s (s.id)}
				<div
					class="flex flex-col border border-slate-200 bg-white shadow-sm rounded-none overflow-hidden"
				>
					<!-- Header Section -->
					<div class="bg-slate-50 border-b border-slate-100 p-8 flex items-center justify-between">
						<div class="flex items-center gap-4">
							<div>
								<p class="text-xs text-slate-500">
									Inhalte: <span class="font-bold tracking-tight text-slate-700 uppercase"
										>{s.taskTitle}</span
									>
									in <span class="font-bold tracking-tight text-slate-700 uppercase"
										>{s.moduleTitle}</span
									>
								</p>
							</div>
							
							{#if s.status === 'APPROVED'}
								<span class="bg-teal-100 text-teal-700 text-[9px] font-bold px-2 py-0.5 tracking-wider uppercase">Validiert</span>
							{:else if s.status === 'REJECTED'}
								<span class="bg-red-100 text-red-700 text-[9px] font-bold px-2 py-0.5 tracking-wider uppercase">Abgelehnt</span>
							{:else}
								<span class="bg-amber-100 text-amber-700 text-[9px] font-bold px-2 py-0.5 tracking-wider uppercase">Ausstehend</span>
							{/if}
						</div>
						<p class="font-mono text-[10px] font-bold text-slate-400 uppercase tracking-widest">
							Eingang: {new Date(s.submittedAt).toLocaleString('de-DE')}
						</p>
					</div>

					<!-- Content Preview -->
					<div class="p-8">
						<span class="block mb-4 font-mono text-[9px] font-bold text-slate-400 uppercase tracking-[3px]">Abgegebene Ausarbeitung</span>
						<PracticeReview content={s.content} />
						
						{#if s.adminComment}
							<div class="mt-6 border-l-2 border-red-200 bg-red-50/30 p-4">
								<p class="text-[10px] font-bold text-red-600 uppercase tracking-wider mb-1">Feedback vom Dozenten:</p>
								<p class="text-sm text-slate-600 italic">"{s.adminComment}"</p>
							</div>
						{/if}
					</div>

					<!-- Actions -->
					{#if s.status === 'PENDING'}
						<div class="bg-slate-50 border-t border-slate-100 p-8 flex flex-wrap justify-end gap-4">
							<button
								onclick={() => reject(s.id, true)}
								class="border border-red-600 bg-white px-6 py-4 text-[10px] font-bold tracking-[3px] text-red-600 uppercase transition-all hover:bg-red-600 hover:text-white rounded-none"
							>
								Endgültig Sperren
							</button>
							<button
								onclick={() => reject(s.id, false)}
								class="border border-red-200 bg-white px-6 py-4 text-[10px] font-bold tracking-[3px] text-red-400 uppercase transition-all hover:bg-red-50 rounded-none"
							>
								Ablehnen (Korrektur)
							</button>
							<button
								onclick={() => approve(s.id, true)}
								class="border border-amber-500 bg-white px-6 py-4 text-[10px] font-bold tracking-[3px] text-amber-600 uppercase transition-all hover:bg-amber-500 hover:text-white rounded-none"
							>
								Halbe Punkte
							</button>
							<button
								onclick={() => approve(s.id, false)}
								class="bg-teal-700 px-8 py-4 text-[10px] font-bold tracking-[3px] text-white uppercase shadow-md transition-all hover:bg-teal-800 rounded-none"
							>
								Volle Punkte
							</button>
						</div>
					{:else}
						<div class="bg-slate-50 border-t border-slate-100 p-8 flex justify-end gap-4">
							<p class="text-[10px] font-bold text-slate-400 uppercase tracking-widest italic py-4">Bereits bearbeitet</p>
							{#if s.status === 'APPROVED'}
								<button
									onclick={() => reject(s.id, false)}
									class="border border-red-100 bg-white px-6 py-4 text-[9px] font-bold tracking-[2px] text-red-400 uppercase transition-all hover:bg-red-50 rounded-none"
								>
									Nachträglich Ablehnen
								</button>
							{/if}
						</div>
					{/if}
				</div>
			{/each}
		</div>
	{/if}
</div>
