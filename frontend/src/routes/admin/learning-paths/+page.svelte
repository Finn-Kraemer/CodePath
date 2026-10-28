<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { api, type PathAdmin, type PathSummary } from '$lib/api';
	import { slugify } from '$lib/site/slug';
	import { exportUrl } from '$lib/transfer';

	let paths = $state<PathSummary[]>([]);
	let loading = $state(true);
	let error = $state('');
	let newTitle = $state('');

	onMount(load);

	async function load() {
		try {
			paths = await api<PathSummary[]>('/api/admin/learning-paths');
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	}

	async function create(e: Event) {
		e.preventDefault();
		error = '';
		try {
			const created = await api<PathAdmin>('/api/admin/learning-paths', {
				method: 'POST',
				body: { title: newTitle, slug: slugify(newTitle) }
			});
			goto(`/admin/learning-paths/${created.id}`);
		} catch (err) {
			error = (err as Error).message;
		}
	}

	async function move(i: number, delta: number) {
		const j = i + delta;
		if (j < 0 || j >= paths.length) return;
		[paths[i], paths[j]] = [paths[j], paths[i]];
		try {
			await api('/api/admin/learning-paths/order', { method: 'PUT', body: paths.map((p) => p.id) });
		} catch (err) {
			error = (err as Error).message;
		}
	}

	const firstPublished = $derived(paths.find((p) => p.published)?.id);
</script>

<svelte:head><title>Lernpfade | CodePath</title></svelte:head>

<div class="flex flex-col gap-8 pb-24">
	<header class="border-b border-slate-200 pb-8">
		<a
			href="/admin"
			class="mb-6 inline-block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
			>← Zurück zur Administration</a
		>
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Lernpfade
		</h1>
		<p class="mt-4 max-w-3xl text-slate-500">
			Zeichne eine Lernpfad-Karte aus Modulen, einzelnen Aufgaben und Meilensteinen. Schülerinnen
			und Schüler sehen sie in der Modulübersicht mit ihrem Stand. Der erste veröffentlichte
			Lernpfad bestimmt die <strong>empfohlene nächste Aufgabe</strong>; ohne Lernpfad empfiehlt
			CodePath die nächste offene Aufgabe in der Reihenfolge der Module.
		</p>
		<div class="mt-6 flex flex-wrap gap-3">
			<a class="ed-button" href={exportUrl(['learningPaths'])} download>Alle exportieren</a>
			<a class="ed-button" href="/admin/transfer">Importieren …</a>
		</div>
	</header>

	{#if error}
		<div class="border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
			{error}
		</div>
	{/if}

	{#if loading}
		<p class="text-sm text-slate-400">Lädt …</p>
	{:else}
		{#if paths.length === 0}
			<p class="border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">
				Noch kein Lernpfad. Ein neuer Lernpfad startet mit allen Modulen als Kette – zum Umbauen.
			</p>
		{:else}
			<ol class="flex flex-col border border-slate-200 bg-white">
				{#each paths as path, i (path.id)}
					<li class="flex flex-wrap items-center gap-3 border-b border-slate-100 p-4 last:border-0">
						<a href="/admin/learning-paths/{path.id}" class="min-w-0 flex-1">
							<span class="block font-bold text-institutional-navy">{path.title}</span>
							<span class="block text-xs text-slate-400"
								>{path.stations} Stationen · /{path.slug}</span
							>
						</a>
						{#if path.id === firstPublished}
							<span
								class="bg-amber-100 px-2 py-1 font-mono text-[9px] font-bold text-amber-800 uppercase"
								>bestimmt die Empfehlung</span
							>
						{/if}
						<span
							class="px-2 py-1 font-mono text-[9px] font-bold uppercase {path.published
								? 'bg-emerald-100 text-emerald-800'
								: 'bg-slate-100 text-slate-500'}"
							>{path.published ? 'Veröffentlicht' : 'Entwurf'}</span
						>
						<button
							class="ed-button px-2"
							onclick={() => move(i, -1)}
							disabled={i === 0}
							aria-label="Nach oben">↑</button
						>
						<button
							class="ed-button px-2"
							onclick={() => move(i, 1)}
							disabled={i === paths.length - 1}
							aria-label="Nach unten">↓</button
						>
					</li>
				{/each}
			</ol>
		{/if}
		<form class="flex max-w-xl gap-2" onsubmit={create}>
			<input
				class="ed-input mt-0"
				placeholder="Titel des neuen Lernpfads"
				aria-label="Titel des neuen Lernpfads"
				bind:value={newTitle}
				required
			/>
			<button class="ed-button bg-white" disabled={!slugify(newTitle)}>Anlegen</button>
		</form>
	{/if}
</div>
