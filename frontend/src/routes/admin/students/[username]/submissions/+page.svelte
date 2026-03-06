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

	async function approve(id: number) {
		try {
			const res = await auth.apiFetch(`/api/admin/submissions/${id}/approve`, {
				method: 'PUT'
			});
			if (res.ok) {
				submissions = submissions.filter((s) => s.id !== id);
			}
		} catch (e) {
			console.error(e);
		}
	}

	async function reject(id: number) {
		if (!confirm('Möchten Sie diese Abgabe wirklich ablehnen? Sie wird gelöscht und der Teilnehmer kann erneut einreichen.')) {
			return;
		}
		try {
			const res = await auth.apiFetch(`/api/admin/submissions/${id}/reject`, {
				method: 'DELETE'
			});
			if (res.ok) {
				submissions = submissions.filter((s) => s.id !== id);
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
			Einzelleistungs-Prüfung
		</h1>
		<p class="mt-4 text-slate-500">
			Überprüfung der praktischen Abgaben für Teilnehmer <span class="font-bold text-institutional-navy"
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
				Keine ausstehenden Abgaben für diesen Teilnehmer vorhanden.
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
						<p class="font-mono text-[10px] font-bold text-slate-400 uppercase tracking-widest">
							Eingang: {new Date(s.submittedAt).toLocaleString('de-DE')}
						</p>
					</div>

					<!-- Content Preview -->
					<div class="p-8">
						<span class="block mb-4 font-mono text-[9px] font-bold text-slate-400 uppercase tracking-[3px]">Abgegebene Ausarbeitung</span>
						<PracticeReview content={s.content} />
					</div>

					<!-- Actions -->
					<div class="bg-slate-50 border-t border-slate-100 p-8 flex justify-end gap-4">
						<button
							onclick={() => reject(s.id)}
							class="border border-red-200 bg-white px-8 py-4 text-[10px] font-bold tracking-[3px] text-red-600 uppercase transition-all hover:bg-red-50 rounded-none"
						>
							Ablehnen / Zurücksetzen
						</button>
						<button
							onclick={() => approve(s.id)}
							class="bg-teal-700 px-8 py-4 text-[10px] font-bold tracking-[3px] text-white uppercase shadow-md transition-all hover:bg-teal-800 rounded-none"
						>
							Validieren & Freigeben
						</button>
					</div>
				</div>
			{/each}
		</div>
	{/if}
</div>
