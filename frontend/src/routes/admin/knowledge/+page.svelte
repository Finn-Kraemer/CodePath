<script lang="ts">
	import { onMount } from 'svelte';
	import { beforeNavigate } from '$app/navigation';
	import { page } from '$app/state';
	import { api, type ArticleAdmin, type ModuleSummary } from '$lib/api';
	import MediaPicker from '$lib/components/MediaPicker.svelte';
	import Markdown from '$lib/Markdown.svelte';
	import { mediaUrl } from '$lib/media';
	import { slugify } from '$lib/site/slug';

	let articles = $state<ArticleAdmin[]>([]);
	let modules = $state<ModuleSummary[]>([]);
	let selectedId = $state<number | null>(null);
	/** Bearbeitete Kopie des gewählten Artikels */
	let form = $state<ArticleAdmin | null>(null);
	let loading = $state(true);
	let busy = $state(false);
	let message = $state('');
	let error = $state('');
	let newTitle = $state('');
	let textarea = $state<HTMLTextAreaElement | null>(null);

	const original = $derived(articles.find((a) => a.id === selectedId) ?? null);
	const dirty = $derived(!!form && !!original && JSON.stringify(form) !== JSON.stringify(original));

	onMount(async () => {
		try {
			[articles, modules] = await Promise.all([
				api<ArticleAdmin[]>('/api/admin/knowledge'),
				api<ModuleSummary[]>('/api/modules')
			]);
			const wanted = page.url.searchParams.get('artikel');
			const first = articles.find((a) => a.slug === wanted) ?? articles[0];
			if (first) select(first.id);
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	});

	beforeNavigate((navigation) => {
		if (dirty && !confirm('Ungespeicherte Änderungen verwerfen?')) navigation.cancel();
	});

	function select(id: number) {
		if (dirty && !confirm('Ungespeicherte Änderungen verwerfen?')) return;
		selectedId = id;
		const a = articles.find((x) => x.id === id);
		form = a ? structuredClone($state.snapshot(a)) : null;
		error = '';
	}

	function flash(text: string) {
		message = text;
		setTimeout(() => (message = ''), 3000);
	}

	function replace(updated: ArticleAdmin) {
		articles = articles.map((a) => (a.id === updated.id ? updated : a));
		if (selectedId === updated.id) form = structuredClone(updated);
	}

	async function save() {
		if (!form) return;
		busy = true;
		error = '';
		try {
			replace(
				await api<ArticleAdmin>(`/api/admin/knowledge/${form.id}`, {
					method: 'PUT',
					body: {
						slug: form.slug,
						title: form.title,
						icon: form.icon,
						body: form.body,
						moduleSlug: form.moduleSlug ?? '',
						published: form.published
					}
				})
			);
			flash('Gespeichert.');
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	async function create(event: SubmitEvent) {
		event.preventDefault();
		error = '';
		try {
			const created = await api<ArticleAdmin>('/api/admin/knowledge', {
				method: 'POST',
				body: { title: newTitle, slug: slugify(newTitle) }
			});
			articles = [...articles, created];
			newTitle = '';
			select(created.id);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function remove() {
		if (!form) return;
		if (!confirm(`Artikel „${form.title}“ endgültig löschen?`)) return;
		const id = form.id;
		try {
			await api(`/api/admin/knowledge/${id}`, { method: 'DELETE' });
			articles = articles.filter((a) => a.id !== id);
			form = null;
			selectedId = null;
			if (articles[0]) select(articles[0].id);
			flash('Artikel gelöscht.');
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function move(i: number, delta: number) {
		const j = i + delta;
		if (j < 0 || j >= articles.length) return;
		[articles[i], articles[j]] = [articles[j], articles[i]];
		try {
			await api('/api/admin/knowledge/order', { method: 'PUT', body: articles.map((a) => a.id) });
		} catch (e) {
			error = (e as Error).message;
		}
	}

	/** Bild an der Cursorposition in den Markdown-Text einfügen */
	function insertImage(key: string, name: string | null | undefined) {
		insert(`![${name ?? 'Bild'}](${mediaUrl(key)})`);
	}

	/** Link auf einen anderen Artikel; markierter Text wird zum Linktext */
	function insertArticleLink(slug: string) {
		if (!form || !slug) return;
		const target = articles.find((a) => a.slug === slug);
		const start = textarea?.selectionStart ?? form.body.length;
		const end = textarea?.selectionEnd ?? start;
		const label = form.body.slice(start, end) || target?.title || slug;
		form.body =
			form.body.slice(0, start) + `[${label}](/knowledge?artikel=${slug})` + form.body.slice(end);
	}

	function insert(snippet: string) {
		if (!form) return;
		const at = textarea?.selectionStart ?? form.body.length;
		form.body = form.body.slice(0, at) + snippet + form.body.slice(at);
	}
</script>

<svelte:window
	onbeforeunload={(e) => {
		if (dirty) e.preventDefault();
	}}
/>

<div class="flex flex-col gap-8 pb-24">
	<header class="border-b border-slate-200 pb-8">
		<div class="mb-2 flex items-center gap-3 text-institutional-navy/40">
			<a href="/admin" class="transition-colors hover:text-institutional-gold">
				<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase">Admin Dashboard</span
				>
			</a>
			<span class="text-slate-300">/</span>
			<span class="font-mono text-[10px] font-bold tracking-[4px] text-institutional-gold uppercase"
				>Wissensdatenbank</span
			>
		</div>
		<h1
			class="font-sans text-5xl leading-none font-black tracking-tighter text-institutional-navy uppercase"
		>
			Wissensdatenbank
		</h1>
		<p class="mt-3 max-w-2xl text-sm text-slate-500">
			Artikel in Markdown schreiben und ordnen. Ein Artikel mit Modul erscheint für Schülerinnen und
			Schüler erst, wenn das Modul freigegeben ist; neue Artikel sind zuerst Entwurf.
		</p>
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

	{#if loading}
		<p class="text-sm text-slate-400">Lädt …</p>
	{:else}
		<div class="grid grid-cols-1 gap-8 xl:grid-cols-[20rem_1fr]">
			<aside class="flex flex-col gap-4">
				<ol class="flex flex-col border border-slate-200 bg-white">
					{#each articles as article, i (article.id)}
						<li
							class="flex items-center gap-1 border-b border-slate-100 p-1 last:border-0 {selectedId ===
							article.id
								? 'bg-amber-50'
								: ''}"
						>
							<button
								class="flex min-w-0 flex-1 items-center gap-2 p-2 text-left"
								onclick={() => select(article.id)}
								aria-current={selectedId === article.id}
							>
								<span>{article.icon}</span>
								<span class="truncate text-sm font-bold text-institutional-navy"
									>{article.title}</span
								>
								{#if !article.published}
									<span class="font-mono text-[8px] text-slate-400 uppercase">Entwurf</span>
								{/if}
							</button>
							<button
								class="ed-button px-2"
								onclick={() => move(i, -1)}
								disabled={i === 0}
								aria-label="Nach oben">↑</button
							>
							<button
								class="ed-button px-2"
								onclick={() => move(i, 1)}
								disabled={i === articles.length - 1}
								aria-label="Nach unten">↓</button
							>
						</li>
					{/each}
				</ol>
				<form class="flex gap-2" onsubmit={create}>
					<input
						class="ed-input mt-0"
						placeholder="Titel des neuen Artikels"
						aria-label="Titel des neuen Artikels"
						bind:value={newTitle}
						required
					/>
					<button class="ed-button bg-white" disabled={!slugify(newTitle)}>Anlegen</button>
				</form>
			</aside>

			{#if form}
				<section class="flex min-w-0 flex-col gap-4" aria-label="Artikel bearbeiten">
					<div class="grid grid-cols-1 gap-4 border border-slate-200 bg-white p-4 md:grid-cols-4">
						<label class="ed-label md:col-span-2"
							>Titel
							<input class="ed-input" bind:value={form.title} required />
						</label>
						<label class="ed-label"
							>Symbol (Emoji)
							<input class="ed-input" bind:value={form.icon} maxlength="16" />
						</label>
						<label class="ed-label"
							>Adresse
							<input class="ed-input mono" bind:value={form.slug} />
						</label>
						<label class="ed-label md:col-span-2"
							>Sichtbar für Schüler
							<select
								class="ed-input"
								value={form.moduleSlug ?? ''}
								onchange={(e) => (form!.moduleSlug = e.currentTarget.value)}
							>
								<option value="">Immer (Grundlagen)</option>
								{#each modules as module (module.slug)}
									<option value={module.slug}>Wenn Modul „{module.title}“ freigegeben ist</option>
								{/each}
							</select>
						</label>
						<label
							class="flex items-center gap-2 self-end pb-2 text-sm text-slate-700 md:col-span-2"
						>
							<input type="checkbox" bind:checked={form.published} />
							Veröffentlicht (sonst nur für dich sichtbar)
						</label>
					</div>

					<div class="grid grid-cols-1 gap-4 2xl:grid-cols-2">
						<div class="flex flex-col">
							<label class="ed-label" for="article-body">Text (Markdown)</label>
							<textarea
								id="article-body"
								bind:this={textarea}
								class="ed-input mono min-h-[60vh] flex-1"
								bind:value={form.body}
							></textarea>
							<p class="mt-1 text-[11px] text-slate-400">
								# Überschrift, **fett**, `Code`, ```-Blöcke für längeren Code, - Listen,
								[Link](https://…)
							</p>
							<div class="flex flex-wrap items-start gap-4">
								<MediaPicker
									label="Bild einfügen"
									onselect={(m) => insertImage(m.key, m.originalName)}
								/>
								<label class="ed-label mt-2"
									>Artikel verlinken
									<select
										class="ed-input mt-1"
										value=""
										onchange={(e) => {
											insertArticleLink(e.currentTarget.value);
											e.currentTarget.value = '';
										}}
									>
										<option value="">Artikel wählen … (markierter Text wird Linktext)</option>
										{#each articles.filter((a) => a.id !== form?.id) as other (other.id)}
											<option value={other.slug}>{other.icon} {other.title}</option>
										{/each}
									</select>
								</label>
							</div>
						</div>
						<div>
							<p class="ed-label">Vorschau</p>
							<div class="mt-1 max-h-[70vh] overflow-y-auto border border-slate-200 bg-white p-6">
								<Markdown
									source={form.body}
									class="prose max-w-none prose-slate prose-headings:text-institutional-navy"
								/>
							</div>
						</div>
					</div>

					<div class="flex flex-wrap items-center gap-3 border-t border-slate-200 pt-4">
						<button
							class="bg-institutional-navy px-6 py-3 text-[11px] font-bold tracking-[3px] text-white uppercase disabled:opacity-50"
							onclick={save}
							disabled={busy || !dirty}>Speichern</button
						>
						<button
							class="ed-button"
							onclick={() => original && (form = structuredClone($state.snapshot(original)))}
							disabled={!dirty}>Verwerfen</button
						>
						<a class="ed-button" href={`/knowledge?artikel=${original?.slug}`}>Ansehen</a>
						<button class="ed-button ml-auto text-rose-700" onclick={remove}>Löschen</button>
					</div>
				</section>
			{/if}
		</div>
	{/if}
</div>
