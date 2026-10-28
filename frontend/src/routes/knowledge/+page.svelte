<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { api, type ArticleSummary, type ArticleView } from '$lib/api';
	import { auth } from '$lib/auth.svelte';
	import Markdown from '$lib/Markdown.svelte';

	let articles = $state<ArticleSummary[]>([]);
	let article = $state<ArticleView | null>(null);
	let loading = $state(true);
	let error = $state('');

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
		goto(`?artikel=${slug}`, { replaceState: true, noScroll: true, keepFocus: true });
	}
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
			<nav class="space-y-1 lg:col-span-1" aria-label="Artikel">
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
						<Markdown
							source={article.body}
							class="knowledge prose max-w-none prose-slate prose-headings:text-institutional-navy prose-a:text-institutional-gold"
						/>
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
