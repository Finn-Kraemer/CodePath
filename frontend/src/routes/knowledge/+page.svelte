<script lang="ts">
	import { onMount, tick } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { api, type ArticleSummary, type ArticleView, type SearchHit } from '$lib/api';
	import { auth } from '$lib/auth.svelte';
	import Markdown from '$lib/Markdown.svelte';
	import { highlight, searchTerms, segments } from '$lib/site/highlight';

	let articles = $state<ArticleSummary[]>([]);
	let article = $state<ArticleView | null>(null);
	let loading = $state(true);
	let error = $state('');
	let query = $state(page.url.searchParams.get('suche') ?? '');
	let hits = $state<SearchHit[] | null>(null);
	let searching = $state(false);
	let articleBody = $state<HTMLElement | null>(null);

	const terms = $derived(searchTerms(query));

	// Gewählter Artikel steht in der Adresse (?artikel=git), damit Links darauf zeigen können
	const selected = $derived(page.url.searchParams.get('artikel') ?? articles[0]?.slug ?? '');

	onMount(async () => {
		try {
			articles = await api<ArticleSummary[]>('/api/knowledge');
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	});

	$effect(() => {
		const slug = selected;
		if (!slug) return;
		article = null;
		api<ArticleView>(`/api/knowledge/${encodeURIComponent(slug)}`)
			.then((a) => (article = a))
			.catch((e) => (error = (e as Error).message));
	});

	function choose(slug: string) {
		error = '';
		const search = terms.length ? `&suche=${encodeURIComponent(query.trim())}` : '';
		goto(`?artikel=${encodeURIComponent(slug)}${search}`, {
			replaceState: true,
			noScroll: true,
			keepFocus: true
		});
	}

	// Suche mit kurzer Verzögerung, damit nicht jeder Tastendruck eine Anfrage auslöst
	$effect(() => {
		const q = query.trim();
		if (searchTerms(q).length === 0) {
			hits = null;
			return;
		}
		searching = true;
		const timer = setTimeout(async () => {
			try {
				hits = await api<SearchHit[]>(`/api/search/knowledge?q=${encodeURIComponent(q)}`);
			} catch (e) {
				error = (e as Error).message;
			} finally {
				searching = false;
			}
		}, 250);
		return () => clearTimeout(timer);
	});

	// Suchwörter im geöffneten Artikel markieren und zur ersten Fundstelle springen
	$effect(() => {
		const current = article;
		const words = terms;
		if (!current || !articleBody) return;
		const container = articleBody;
		tick().then(() => {
			const first = highlight(container, words);
			first?.scrollIntoView({ block: 'center', behavior: 'smooth' });
		});
	});
</script>

<div class="mx-auto max-w-7xl">
	<header
		class="mb-12 flex flex-wrap items-end justify-between gap-4 border-b border-slate-200 pb-8"
	>
		<div>
			<h1
				class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase"
			>
				Wissensdatenbank
			</h1>
			<p class="mt-4 text-slate-500">
				Zentrale Informationsquelle für IT-Konzepte und Syntax-Referenzen.
			</p>
		</div>
		{#if auth.user?.role === 'ADMIN'}
			<a
				href={`/admin/knowledge${selected ? `?artikel=${selected}` : ''}`}
				class="ed-button bg-white">Artikel bearbeiten</a
			>
		{/if}
	</header>

	{#if loading}
		<div class="flex justify-center py-24">
			<div
				class="h-12 w-12 animate-spin border-4 border-slate-200 border-t-institutional-navy"
			></div>
		</div>
	{:else}
		<div class="grid grid-cols-1 gap-12 lg:grid-cols-4">
			<div
				class="space-y-4 lg:sticky lg:top-24 lg:col-span-1 lg:max-h-[calc(100vh-7rem)] lg:self-start lg:overflow-y-auto"
			>
				<div class="relative">
					<input
						type="search"
						bind:value={query}
						placeholder="Wiki durchsuchen …"
						aria-label="Wissensdatenbank durchsuchen"
						class="w-full border border-slate-200 bg-white py-3 pr-3 pl-10 text-sm focus:border-institutional-navy focus:outline-none"
						onkeydown={(e) => {
							if (e.key === 'Enter' && hits?.length) choose(hits[0].slug);
							if (e.key === 'Escape') query = '';
						}}
					/>
					<svg
						class="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-slate-400"
						fill="none"
						viewBox="0 0 24 24"
						stroke="currentColor"
						aria-hidden="true"
						><path
							stroke-linecap="round"
							stroke-linejoin="round"
							stroke-width="2"
							d="M21 21l-4.35-4.35M17 11A6 6 0 115 11a6 6 0 0112 0z"
						/></svg
					>
				</div>

				{#if hits !== null}
					<section aria-label="Suchergebnisse" aria-live="polite">
						<p class="mb-2 font-mono text-[10px] tracking-widest text-slate-400 uppercase">
							{searching
								? 'Suche …'
								: `${hits.length} ${hits.length === 1 ? 'Treffer' : 'Treffer'}`}
						</p>
						{#if !searching && hits.length === 0}
							<p class="border border-dashed border-slate-300 p-4 text-sm text-slate-400">
								Nichts gefunden. Andere Wörter probieren?
							</p>
						{/if}
						<ul class="space-y-2">
							{#each hits as hit (hit.slug)}
								<li>
									<button
										onclick={() => choose(hit.slug)}
										aria-current={selected === hit.slug}
										class="w-full border p-3 text-left transition-colors {selected === hit.slug
											? 'border-institutional-gold bg-amber-50'
											: 'border-slate-200 bg-white hover:border-institutional-navy'}"
									>
										<span class="flex items-center gap-2 text-sm font-bold text-institutional-navy">
											<span>{hit.icon}</span>
											<span
												>{#each segments(hit.title, terms) as part, i (i)}{#if part.match}<mark
															class="search-hit">{part.text}</mark
														>{:else}{part.text}{/if}{/each}</span
											>
										</span>
										<span class="mt-1 block text-xs leading-relaxed text-slate-500"
											>{#each segments(hit.snippet, terms) as part, i (i)}{#if part.match}<mark
														class="search-hit">{part.text}</mark
													>{:else}{part.text}{/if}{/each}</span
										>
									</button>
								</li>
							{/each}
						</ul>
					</section>
				{:else}
					<nav class="space-y-1" aria-label="Artikel">
						{#each articles as entry (entry.slug)}
							<button
								onclick={() => choose(entry.slug)}
								aria-current={selected === entry.slug}
								class="flex w-full items-center justify-start gap-4 border-l-4 p-5 text-left font-sans text-xs font-bold tracking-widest uppercase transition-all
                        {selected === entry.slug
									? 'border-institutional-gold bg-amber-50 text-institutional-gold shadow-sm'
									: 'border-transparent text-slate-500 hover:bg-slate-50 hover:text-slate-700'}"
							>
								<span class="text-xl grayscale">{entry.icon}</span>
								<span class="flex-1">{entry.title}</span>
								{#if !entry.published}
									<span class="font-mono text-[8px] text-slate-400">Entwurf</span>
								{/if}
							</button>
						{/each}

						{#if articles.length === 0}
							<p class="p-5 font-mono text-[10px] text-slate-400 uppercase italic">
								Warte auf Freischaltung der Inhalte...
							</p>
						{/if}
					</nav>
				{/if}
			</div>

			<div class="lg:col-span-3">
				{#if error}
					<p class="mb-4 text-sm text-rose-700" role="alert">{error}</p>
				{/if}
				{#if article}
					<div class="rounded-none border border-slate-200 bg-white p-6 shadow-sm sm:p-12">
						<div class="mb-16 flex items-center gap-6">
							<span class="text-5xl">{article.icon}</span>
							<h2
								class="font-sans text-3xl font-extrabold tracking-tight text-institutional-navy uppercase"
							>
								{article.title}
							</h2>
						</div>
						<div bind:this={articleBody}>
							<Markdown
								source={article.body}
								class="knowledge prose max-w-none prose-slate prose-headings:text-institutional-navy prose-a:text-institutional-gold"
							/>
						</div>
					</div>
				{:else if articles.length}
					<div class="flex h-64 items-center justify-center border border-dashed border-slate-300">
						<p class="text-slate-400 italic">Lädt …</p>
					</div>
				{/if}
			</div>
		</div>
	{/if}
</div>

<style>
	:global(.knowledge pre) {
		background-color: #0f172a !important;
		padding: 1.5rem !important;
		border: 4px solid #1e293b !important;
		border-radius: 0 !important;
	}

	:global(.knowledge code) {
		color: #2dd4bf !important;
		font-family:
			ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
			monospace !important;
	}
</style>
