<!-- frontend/src/routes/admin/submissions/+page.svelte -->
<script lang="ts">
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';
	import PracticeReview from '$lib/components/editor/PracticeReview.svelte';

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
			const res = await auth.apiFetch('/api/admin/submissions');
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
				submissions = submissions.filter((s) => s.id !== id);
			}
		} catch (e) {
			console.error(e);
		}
	}

	async function reject(id: number) {
		if (
			!confirm(
				'Möchten Sie diese Abgabe wirklich ablehnen? Die Abgabe geht zur Überarbeitung zurück.'
			)
		) {
			return;
		}
		try {
			const res = await auth.apiFetch(`/api/admin/submissions/${id}/reject`, {
				method: 'PUT',
				body: JSON.stringify({ comment: 'Bitte überarbeiten.' })
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
		<h1 class="font-sans text-2xl md:text-3xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Prüfungszentrum
		</h1>
		<p class="mt-4 text-slate-500">Ausstehende praktische Abgaben zur manuellen Validierung.</p>
	</header>

	{#if loading}
		<div class="flex justify-center py-24">
			<div
				class="h-12 w-12 animate-spin border-4 border-slate-200 border-t-institutional-navy"
			></div>
		</div>
	{:else if submissions.length === 0}
		<div class="rounded-none border border-dashed border-slate-200 bg-white p-24 text-center">
			<p class="font-serif text-sm text-slate-400 italic">
				Aktuell liegen keine offenen Abgaben vor.
			</p>
		</div>
	{:else}
		<div class="grid grid-cols-1 gap-4">
			{#each submissions as s (s.id)}
				<div
					class="flex items-center justify-between rounded-none border border-slate-200 bg-white p-10 shadow-sm"
				>
					<div>
						<div class="mb-3 flex items-center gap-3">
							<span class="font-sans text-sm font-black text-institutional-navy uppercase"
								>{s.displayName || s.username}</span
							>
							<span class="font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase"
								>@{s.username}</span
							>
						</div>
						<p class="text-xs text-slate-500">
							Aufgabe: <span class="font-bold tracking-tight text-slate-700 uppercase"
								>{s.taskTitle}</span
							>
							in
							<span class="font-bold tracking-tight text-slate-700 uppercase">{s.moduleTitle}</span>
						</p>
						<p class="mt-4 font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase">
							Eingang: {new Date(s.submittedAt).toLocaleString('de-DE')}
						</p>
					</div>

					<div class="flex gap-4">
						<a
							href={`/admin/submissions/${s.id}`}
							class="rounded-none border border-institutional-navy px-8 py-4 text-[10px] font-bold tracking-[3px] text-institutional-navy uppercase transition-all hover:bg-slate-50"
						>
							Abgabe öffnen
						</a>
						<button
							onclick={() => approve(s.id, true)}
							class="rounded-none border border-amber-500 px-6 py-4 text-[10px] font-bold tracking-[3px] text-amber-600 uppercase transition-all hover:bg-amber-500 hover:text-white"
						>
							Halbe Punkte
						</button>
						<button
							onclick={() => approve(s.id, false)}
							class="rounded-none bg-institutional-navy px-8 py-4 text-[10px] font-bold tracking-[3px] text-white uppercase shadow-md transition-all hover:opacity-90"
						>
							Volle Punkte
						</button>
					</div>
				</div>
			{/each}
		</div>
	{/if}
</div>
