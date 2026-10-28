<script lang="ts">
	import { exportUrl } from '$lib/transfer';
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { api, type PageSummary } from '$lib/api';
	import { siteNavigation } from '$lib/site/navigation.svelte';
	import { slugify } from '$lib/site/slug';

	let pages = $state<PageSummary[]>([]);
	let loading = $state(true);
	let error = $state('');
	let title = $state('');
	let slug = $state('');
	let slugTouched = $state(false);

	const suggested = $derived(slugTouched ? slug : slugify(title));

	async function load() {
		try {
			pages = await api<PageSummary[]>('/api/admin/pages');
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	}

	onMount(load);

	async function create(event: SubmitEvent) {
		event.preventDefault();
		error = '';
		try {
			const page = await api<PageSummary>('/api/admin/pages', {
				method: 'POST',
				body: { title, slug: suggested }
			});
			goto(`/admin/pages/${page.id}`);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function move(i: number, delta: number) {
		const j = i + delta;
		if (j < 0 || j >= pages.length) return;
		[pages[i], pages[j]] = [pages[j], pages[i]];
		try {
			await api('/api/admin/pages/order', { method: 'PUT', body: pages.map((p) => p.id) });
			siteNavigation.refresh();
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function remove(page: PageSummary) {
		if (!confirm(`Seite „${page.title}“ endgültig löschen?`)) return;
		try {
			await api(`/api/admin/pages/${page.id}`, { method: 'DELETE' });
			pages = pages.filter((p) => p.id !== page.id);
			siteNavigation.refresh();
		} catch (e) {
			error = (e as Error).message;
		}
	}
</script>

<div class="mx-auto flex max-w-5xl flex-col gap-10 pb-24">
	<header class="border-b border-slate-200 pb-8">
		<div class="mb-2 flex items-center gap-3 text-institutional-navy/40">
			<a href="/admin" class="transition-colors hover:text-institutional-gold">
				<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase">Admin Dashboard</span
				>
			</a>
			<span class="text-slate-300">/</span>
			<span class="font-mono text-[10px] font-bold tracking-[4px] text-institutional-gold uppercase"
				>Seiten</span
			>
		</div>
		<h1
			class="font-sans text-5xl leading-none font-black tracking-tighter text-institutional-navy uppercase"
		>
			Seiten
		</h1>
		<p class="mt-3 max-w-2xl text-sm text-slate-500">
			Eigene Seiten wie „Anfahrt“, „Ablauf“ oder „Kontakt“ – aufgebaut aus denselben Abschnitten wie
			die <a href="/admin/homepage" class="underline">Startseite</a>. Veröffentlichte Seiten
			erscheinen im Menü unter „Seiten“ und in der
			<a href="/seiten" class="underline">Übersicht</a>; einzelne kannst du zusätzlich direkt in die
			Menüleiste holen.
		</p>
		<div class="mt-4 flex flex-wrap gap-3">
			<a class="ed-button" href={exportUrl(['pages'])} download>Exportieren</a>
			<a class="ed-button" href="/admin/transfer">Importieren …</a>
		</div>
	</header>

	{#if error}
		<p class="text-[11px] font-bold tracking-widest text-rose-700 uppercase" role="alert">
			{error}
		</p>
	{/if}

	<form
		class="grid grid-cols-1 items-end gap-4 border border-slate-200 bg-white p-6 md:grid-cols-[1fr_1fr_auto]"
		onsubmit={create}
	>
		<label class="ed-label"
			>Titel der neuen Seite
			<input class="ed-input" bind:value={title} required maxlength="200" />
		</label>
		<label class="ed-label"
			>Adresse (/seiten/…)
			<input
				class="ed-input mono"
				value={suggested}
				oninput={(e) => {
					slugTouched = true;
					slug = e.currentTarget.value;
				}}
				required
				pattern="[a-z0-9]+(-[a-z0-9]+)*"
			/>
		</label>
		<button
			class="bg-institutional-navy px-6 py-3 text-[11px] font-bold tracking-[3px] text-white uppercase disabled:opacity-50"
			disabled={!title.trim() || !suggested}>Anlegen</button
		>
	</form>

	{#if loading}
		<p class="text-sm text-slate-400">Lädt …</p>
	{:else if pages.length === 0}
		<p class="border border-dashed border-slate-300 p-8 text-center text-sm text-slate-400">
			Noch keine eigenen Seiten.
		</p>
	{:else}
		<ul class="divide-y divide-slate-100 border border-slate-200 bg-white">
			{#each pages as page, i (page.id)}
				<li class="flex flex-wrap items-center gap-4 p-4">
					<div class="min-w-0 flex-1">
						<a
							href={`/admin/pages/${page.id}`}
							class="font-bold text-institutional-navy hover:underline">{page.title}</a
						>
						<p class="font-mono text-[11px] text-slate-400">/seiten/{page.slug}</p>
					</div>
					<div class="flex flex-wrap gap-2 font-mono text-[9px] tracking-widest uppercase">
						{#if page.published}
							<span class="bg-teal-50 px-2 py-1 text-teal-700"
								>{page.changed ? 'Live · Entwurf geändert' : 'Live'}</span
							>
						{:else}
							<span class="bg-slate-100 px-2 py-1 text-slate-500">Entwurf</span>
						{/if}
						<span class="bg-slate-100 px-2 py-1 text-slate-500"
							>{page.visibility === 'PUBLIC' ? 'Öffentlich' : 'Nur angemeldet'}</span
						>
						{#if page.showInNav}<span class="bg-amber-50 px-2 py-1 text-amber-700"
								>In der Menüleiste</span
							>{/if}
					</div>
					<div class="flex gap-1">
						<button
							class="ed-button px-2"
							onclick={() => move(i, -1)}
							disabled={i === 0}
							aria-label="Nach oben">↑</button
						>
						<button
							class="ed-button px-2"
							onclick={() => move(i, 1)}
							disabled={i === pages.length - 1}
							aria-label="Nach unten">↓</button
						>
						{#if page.published}
							<a class="ed-button" href={`/seiten/${page.slug}`} target="_blank">Ansehen</a>
						{/if}
						<a class="ed-button" href={`/admin/pages/${page.id}`}>Bearbeiten</a>
						<button class="ed-button text-rose-700" onclick={() => remove(page)}>Löschen</button>
					</div>
				</li>
			{/each}
		</ul>
	{/if}
</div>
