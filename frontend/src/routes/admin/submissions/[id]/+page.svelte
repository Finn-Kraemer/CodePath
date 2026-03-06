<!-- frontend/src/routes/admin/submissions/[id]/+page.svelte -->
<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { auth } from '$lib/auth.svelte';
	import { goto } from '$app/navigation';
	import PracticeReview from '$lib/components/editor/PracticeReview.svelte';

	const id = page.params.id;

	interface Submission {
		id: number;
		username: string;
		displayName: string;
		taskTitle: string;
		moduleTitle: string;
		submittedAt: string;
		content: string;
	}

	let submission = $state<Submission | null>(null);
	let loading = $state(true);

	async function fetchSubmission() {
		try {
			const res = await auth.apiFetch(`/api/admin/submissions/${id}`);
			if (res.ok) {
				submission = await res.json();
			}
		} catch (e) {
			console.error(e);
		} finally {
			loading = false;
		}
	}

	async function approve() {
		if (!submission) return;
		try {
			const res = await auth.apiFetch(`/api/admin/submissions/${submission.id}/approve`, {
				method: 'PUT'
			});
			if (res.ok) {
				goto('/admin/submissions');
			}
		} catch (e) {
			console.error(e);
		}
	}

	async function reject() {
		if (!submission) return;
		if (!confirm('Möchten Sie diese Abgabe wirklich ablehnen? Sie wird gelöscht und der Teilnehmer kann erneut einreichen.')) {
			return;
		}
		try {
			const res = await auth.apiFetch(`/api/admin/submissions/${submission.id}/reject`, {
				method: 'DELETE'
			});
			if (res.ok) {
				goto('/admin/submissions');
			}
		} catch (e) {
			console.error(e);
		}
	}

	onMount(fetchSubmission);
</script>

<div class="flex flex-col gap-12">
	<header class="border-b border-slate-200 pb-8">
		<a
			href="/admin/submissions"
			class="mb-6 inline-block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase transition-colors hover:text-institutional-navy"
			>← Zurück zum Prüfungszentrum</a
		>
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Leistungsnachweis Prüfen
		</h1>
	</header>

	{#if loading}
		<div class="flex justify-center py-24">
			<div class="h-12 w-12 border-4 border-slate-200 border-t-institutional-navy animate-spin"></div>
		</div>
	{:else if !submission}
		<div class="border border-dashed border-slate-200 bg-white p-24 text-center rounded-none">
			<p class="font-serif text-sm text-slate-400 italic">Abgabe wurde nicht gefunden oder bereits bearbeitet.</p>
		</div>
	{:else}
		<div class="flex flex-col border border-slate-200 bg-white shadow-sm rounded-none overflow-hidden">
			<!-- Header Section -->
			<div class="bg-slate-50 border-b border-slate-100 p-8 flex items-center justify-between">
				<div>
					<div class="mb-2 flex items-center gap-3">
						<span class="font-sans text-lg font-black text-institutional-navy uppercase"
							>{submission.displayName || submission.username}</span
						>
						<span class="font-mono text-[10px] font-bold text-slate-400 uppercase tracking-widest"
							>@{submission.username}</span
						>
					</div>
					<p class="text-xs text-slate-500">
						Aufgabe: <span class="font-bold tracking-tight text-slate-700 uppercase"
							>{submission.taskTitle}</span
						>
						in <span class="font-bold tracking-tight text-slate-700 uppercase"
							>{submission.moduleTitle}</span
						>
					</p>
				</div>
				<p class="font-mono text-[10px] font-bold text-slate-400 uppercase tracking-widest">
					Eingang: {new Date(submission.submittedAt).toLocaleString('de-DE')}
				</p>
			</div>

			<!-- Content Preview -->
			<div class="p-8">
				<span class="block mb-4 font-mono text-[9px] font-bold text-slate-400 uppercase tracking-[3px]">Abgegebene Ausarbeitung</span>
				<PracticeReview content={submission.content} />
			</div>

			<!-- Actions -->
			<div class="bg-slate-50 border-t border-slate-100 p-8 flex justify-end gap-4">
				<button
					onclick={reject}
					class="border border-red-200 bg-white px-8 py-4 text-[10px] font-bold tracking-[3px] text-red-600 uppercase transition-all hover:bg-red-50 rounded-none"
				>
					Ablehnen / Zurücksetzen
				</button>
				<button
					onclick={approve}
					class="bg-teal-700 px-8 py-4 text-[10px] font-bold tracking-[3px] text-white uppercase shadow-md transition-all hover:bg-teal-800 rounded-none"
				>
					Validieren & Freigeben
				</button>
			</div>
		</div>
	{/if}
</div>
