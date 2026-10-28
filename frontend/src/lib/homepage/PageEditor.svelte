<script lang="ts">
	import { onMount, type Snippet } from 'svelte';
	import { beforeNavigate } from '$app/navigation';
	import { api } from '$lib/api';
	import BlockForm from './BlockForm.svelte';
	import HomepageView from './HomepageView.svelte';
	import { BLOCK_TYPES, blockId, normalize, type BlockType, type HomepageContent } from './types';

	/**
	 * Block-Editor für die Startseite und eigene Seiten. Erwartet unter `base`:
	 * GET (published/draft), PUT base/draft, PUT base/published und DELETE –
	 * bei der Startseite auf `base` (zurück zur Standardseite), sonst auf base/published (offline nehmen).
	 */
	let {
		base,
		fallback,
		kind,
		header,
		onchange
	}: {
		base: string;
		fallback: HomepageContent;
		kind: 'homepage' | 'page';
		header: Snippet;
		/** Nach Speichern, Veröffentlichen oder Offline-Nehmen (z. B. um Seiteneinstellungen neu zu laden) */
		onchange?: () => void;
	} = $props();

	let content = $state<HomepageContent>({ blocks: [] });
	let published = $state<HomepageContent | null>(null);
	/** Stand auf dem Server (Entwurf), um ungespeicherte Änderungen zu erkennen */
	let saved = $state('');
	let selected = $state<string | null>(null);
	let adding = $state(false);
	let loading = $state(true);
	let busy = $state(false);
	let message = $state('');
	let error = $state('');

	const current = $derived(JSON.stringify(content));
	const dirty = $derived(!loading && current !== saved);
	const live = $derived(published ? JSON.stringify(published) === current : false);
	const index = $derived(content.blocks.findIndex((b) => b.id === selected));

	onMount(async () => {
		try {
			const state = await api<{ published: unknown; draft: unknown }>(base);
			published = normalize(state.published);
			content = normalize(state.draft) ?? published ?? structuredClone(fallback);
		} catch (e) {
			error = (e as Error).message;
		} finally {
			saved = JSON.stringify(content);
			loading = false;
		}
	});

	beforeNavigate((navigation) => {
		if (dirty && !confirm('Ungespeicherte Änderungen verwerfen?')) navigation.cancel();
	});

	function flash(text: string) {
		message = text;
		error = '';
		setTimeout(() => (message = ''), 3000);
	}

	async function run(action: () => Promise<void>, done: string) {
		busy = true;
		error = '';
		try {
			await action();
			flash(done);
			onchange?.();
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	function saveDraft() {
		return run(async () => {
			await api(`${base}/draft`, { method: 'PUT', body: content });
			saved = current;
		}, 'Entwurf gespeichert.');
	}

	function publish() {
		return run(async () => {
			await api(`${base}/published`, { method: 'PUT', body: content });
			saved = current;
			published = structuredClone($state.snapshot(content));
		}, 'Veröffentlicht.');
	}

	function discard() {
		if (!confirm('Alle Änderungen seit der letzten Veröffentlichung verwerfen?')) return;
		content = structuredClone($state.snapshot(published) ?? fallback);
		selected = null;
	}

	function reset() {
		if (kind === 'homepage') {
			if (
				!confirm(
					'Startseite auf die mitgelieferte Standardseite zurücksetzen? Entwurf und veröffentlichte Fassung werden gelöscht.'
				)
			)
				return;
			return run(async () => {
				await api(base, { method: 'DELETE' });
				published = null;
				content = structuredClone(fallback);
				saved = JSON.stringify(content);
				selected = null;
			}, 'Standardseite wiederhergestellt.');
		}
		if (!confirm('Seite offline nehmen? Der Entwurf bleibt erhalten.')) return;
		return run(async () => {
			await api(`${base}/published`, { method: 'DELETE' });
			published = null;
		}, 'Seite ist offline.');
	}

	function add(type: BlockType) {
		const block = BLOCK_TYPES[type].create();
		const at = index >= 0 ? index + 1 : content.blocks.length;
		content.blocks.splice(at, 0, block);
		selected = block.id;
		adding = false;
	}

	function move(i: number, delta: number) {
		const j = i + delta;
		if (j < 0 || j >= content.blocks.length) return;
		const blocks = content.blocks;
		[blocks[i], blocks[j]] = [blocks[j], blocks[i]];
	}

	function duplicate(i: number) {
		const copy = { ...structuredClone($state.snapshot(content.blocks[i])), id: blockId() };
		content.blocks.splice(i + 1, 0, copy);
		selected = copy.id;
	}

	function remove(i: number) {
		const block = content.blocks[i];
		if (!confirm(`Block „${block.title || BLOCK_TYPES[block.type].label}“ löschen?`)) return;
		content.blocks.splice(i, 1);
		if (selected === block.id) selected = null;
	}

	function select(id: string) {
		selected = selected === id ? null : id;
		adding = false;
	}
</script>

<svelte:window
	onbeforeunload={(e) => {
		if (dirty) e.preventDefault();
	}}
/>

<div class="flex flex-col gap-8 pb-24">
	<header
		class="flex flex-col gap-6 border-b border-slate-200 pb-8 lg:flex-row lg:items-end lg:justify-between"
	>
		<div>{@render header()}</div>
		<div class="flex flex-wrap items-center gap-3">
			<span class="font-mono text-[10px] tracking-widest text-slate-400 uppercase">
				{#if loading}Lädt …{:else if dirty}Ungespeicherte Änderungen{:else if live}Live{:else if published}Entwurf
					weicht von Live ab{:else if kind === 'homepage'}Standardseite aktiv{:else}Nicht
					veröffentlicht{/if}
			</span>
			<button class="ed-button bg-white" onclick={saveDraft} disabled={busy || loading || !dirty}
				>Entwurf speichern</button
			>
			<button
				class="bg-institutional-navy px-6 py-3 font-sans text-[11px] font-bold tracking-[3px] text-white uppercase shadow-lg transition-all hover:opacity-95 disabled:opacity-50"
				onclick={publish}
				disabled={busy || loading || live}>Veröffentlichen</button
			>
		</div>
	</header>

	{#if message}
		<p class="text-[11px] font-bold tracking-widest text-teal-700 uppercase" role="status">
			{message}
		</p>
	{/if}
	{#if error}
		<p class="text-[11px] font-bold tracking-widest text-rose-700 uppercase" role="alert">
			{error}
		</p>
	{/if}

	<div class="grid grid-cols-1 gap-8 xl:grid-cols-[26rem_1fr]">
		<!-- Blockliste und Formular -->
		<aside
			class="flex flex-col gap-4 xl:sticky xl:top-4 xl:max-h-[calc(100vh-2rem)] xl:self-start xl:overflow-y-auto"
		>
			<ol class="flex flex-col gap-2">
				{#each content.blocks as block, i (block.id)}
					<li
						class="border bg-white {selected === block.id
							? 'border-institutional-navy'
							: 'border-slate-200'}"
					>
						<div class="flex items-center gap-2 p-2">
							<button
								type="button"
								class="min-w-0 flex-1 text-left"
								onclick={() => select(block.id)}
								aria-expanded={selected === block.id}
							>
								<span class="block font-mono text-[9px] tracking-widest text-slate-400 uppercase"
									>{BLOCK_TYPES[block.type].label}{block.hidden ? ' · ausgeblendet' : ''}</span
								>
								<span
									class="block truncate text-sm font-bold text-institutional-navy {block.hidden
										? 'line-through opacity-50'
										: ''}">{block.title || block.badge || '(ohne Titel)'}</span
								>
							</button>
							<button
								type="button"
								class="ed-button px-2"
								onclick={() => move(i, -1)}
								disabled={i === 0}
								aria-label="Nach oben"
								title="Nach oben">↑</button
							>
							<button
								type="button"
								class="ed-button px-2"
								onclick={() => move(i, 1)}
								disabled={i === content.blocks.length - 1}
								aria-label="Nach unten"
								title="Nach unten">↓</button
							>
						</div>
						{#if selected === block.id}
							<div class="border-t border-slate-100 p-4">
								<BlockForm bind:block={content.blocks[i]} />
								<div class="mt-6 flex flex-wrap gap-2 border-t border-slate-100 pt-4">
									<button
										type="button"
										class="ed-button"
										onclick={() => (content.blocks[i].hidden = !block.hidden)}
										>{block.hidden ? 'Einblenden' : 'Ausblenden'}</button
									>
									<button type="button" class="ed-button" onclick={() => duplicate(i)}
										>Duplizieren</button
									>
									<button type="button" class="ed-button text-rose-700" onclick={() => remove(i)}
										>Löschen</button
									>
								</div>
							</div>
						{/if}
					</li>
				{/each}
			</ol>

			{#if adding}
				<div class="border border-dashed border-slate-300 bg-white p-3">
					<p class="ed-label mb-2">
						Neuer Abschnitt {index >= 0 ? 'nach dem gewählten' : 'am Ende'}
					</p>
					<div class="grid grid-cols-1 gap-2">
						{#each Object.entries(BLOCK_TYPES) as [type, def] (type)}
							<button
								type="button"
								class="border border-slate-200 p-2 text-left hover:border-institutional-navy"
								onclick={() => add(type as BlockType)}
							>
								<span class="block text-sm font-bold text-institutional-navy">{def.label}</span>
								<span class="block text-xs text-slate-500">{def.description}</span>
							</button>
						{/each}
					</div>
					<button type="button" class="ed-button mt-2" onclick={() => (adding = false)}
						>Abbrechen</button
					>
				</div>
			{:else}
				<button
					type="button"
					class="border-2 border-dashed border-slate-300 p-3 text-[11px] font-bold tracking-widest text-slate-500 uppercase hover:border-institutional-navy hover:text-institutional-navy"
					onclick={() => (adding = true)}>+ Abschnitt hinzufügen</button
				>
			{/if}

			<div class="flex flex-wrap gap-2 border-t border-slate-200 pt-4">
				<button
					type="button"
					class="ed-button"
					onclick={discard}
					disabled={busy || !published || live}>Änderungen verwerfen</button
				>
				<button
					type="button"
					class="ed-button text-rose-700"
					onclick={reset}
					disabled={busy || (kind === 'page' && !published)}
					>{kind === 'homepage' ? 'Auf Standard zurücksetzen' : 'Offline nehmen'}</button
				>
			</div>
		</aside>

		<!-- Vorschau: verkleinert, damit sie wie auf einem großen Bildschirm aussieht -->
		<section aria-label="Vorschau" class="min-w-0">
			<p class="ed-label mb-2">Vorschau – Klick auf einen Abschnitt öffnet ihn links</p>
			<div class="preview overflow-hidden border border-slate-300 bg-white shadow-sm">
				<HomepageView {content} {selected} onselect={select} />
			</div>
		</section>
	</div>
</div>

<style>
	@media (min-width: 1280px) {
		.preview {
			zoom: 0.6;
		}
	}
</style>
