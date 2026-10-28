<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { api, type PageSummary } from '$lib/api';
	import PageEditor from '$lib/homepage/PageEditor.svelte';
	import { siteNavigation } from '$lib/site/navigation.svelte';

	const id = $derived(page.params.id);

	let summary = $state<PageSummary | null>(null);
	let title = $state('');
	let slug = $state('');
	let visibility = $state<'PUBLIC' | 'LOGGED_IN'>('LOGGED_IN');
	let showInNav = $state(false);
	let settingsMessage = $state('');
	let settingsError = $state('');

	/** Lädt den Stand der Seite; die Formularfelder nur beim ersten Mal, damit Eingaben nicht verloren gehen */
	async function load() {
		const detail = await api<{ page: PageSummary }>(`/api/admin/pages/${id}`);
		if (summary) summary = detail.page;
		else apply(detail.page);
	}

	function apply(p: PageSummary) {
		summary = p;
		title = p.title;
		slug = p.slug;
		visibility = p.visibility;
		showInNav = p.showInNav;
	}

	onMount(() => {
		load().catch((e) => (settingsError = (e as Error).message));
	});

	async function saveSettings(event: SubmitEvent) {
		event.preventDefault();
		settingsError = '';
		try {
			apply(
				await api<PageSummary>(`/api/admin/pages/${id}`, {
					method: 'PUT',
					body: { title, slug, visibility, showInNav }
				})
			);
			settingsMessage = 'Einstellungen gespeichert.';
			setTimeout(() => (settingsMessage = ''), 3000);
			siteNavigation.refresh();
		} catch (e) {
			settingsError = (e as Error).message;
		}
	}
</script>

{#key id}
	<PageEditor
		base={`/api/admin/pages/${id}`}
		fallback={{ blocks: [] }}
		kind="page"
		onchange={() => {
			load();
			siteNavigation.refresh();
		}}
	>
		{#snippet header()}
			<div class="mb-2 flex items-center gap-3 text-institutional-navy/40">
				<a href="/admin" class="transition-colors hover:text-institutional-gold">
					<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase"
						>Admin Dashboard</span
					>
				</a>
				<span class="text-slate-300">/</span>
				<a href="/admin/pages" class="transition-colors hover:text-institutional-gold">
					<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase">Seiten</span>
				</a>
				<span class="text-slate-300">/</span>
				<span
					class="font-mono text-[10px] font-bold tracking-[4px] text-institutional-gold uppercase"
					>{summary?.title ?? '…'}</span
				>
			</div>
			<h1
				class="font-sans text-5xl leading-none font-black tracking-tighter text-institutional-navy uppercase"
			>
				{summary?.title ?? 'Seite'}
			</h1>
			{#if summary?.published}
				<a
					href={`/seiten/${summary.slug}`}
					target="_blank"
					class="mt-2 inline-block font-mono text-xs text-slate-500 underline"
					>/seiten/{summary.slug}</a
				>
			{/if}

			{#if summary}
				<form
					class="mt-6 grid max-w-3xl grid-cols-1 gap-4 border border-slate-200 bg-white p-4 sm:grid-cols-2"
					onsubmit={saveSettings}
					aria-label="Seiteneinstellungen"
				>
					<label class="ed-label"
						>Titel (Menü und Browser-Tab)
						<input class="ed-input" bind:value={title} required maxlength="200" />
					</label>
					<label class="ed-label"
						>Adresse (/seiten/…)
						<input
							class="ed-input mono"
							bind:value={slug}
							required
							pattern="[a-z0-9]+(-[a-z0-9]+)*"
						/>
					</label>
					<label class="ed-label"
						>Wer darf die Seite sehen?
						<select class="ed-input" bind:value={visibility}>
							<option value="LOGGED_IN">Nur angemeldete Nutzer</option>
							<option value="PUBLIC">Alle, auch ohne Anmeldung</option>
						</select>
					</label>
					<label class="flex items-center gap-2 self-end pb-2 text-sm text-slate-700">
						<input type="checkbox" bind:checked={showInNav} />
						Im Menü anzeigen (sobald veröffentlicht)
					</label>
					<div class="flex items-center gap-4 sm:col-span-2">
						<button class="ed-button bg-white">Einstellungen speichern</button>
						{#if settingsMessage}<span class="text-xs text-teal-700" role="status"
								>{settingsMessage}</span
							>{/if}
						{#if settingsError}<span class="text-xs text-rose-700" role="alert"
								>{settingsError}</span
							>{/if}
					</div>
				</form>
			{/if}
		{/snippet}
	</PageEditor>
{/key}
