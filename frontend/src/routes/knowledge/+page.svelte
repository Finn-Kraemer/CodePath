<script lang="ts">
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';

	interface MarkdownModule {
		default: any;
		metadata: {
			title: string;
			icon: string;
		};
	}

	interface ApiModule {
		slug: string;
		isUnlocked: boolean;
	}

	let allMarkdownCategories = $state<Record<string, MarkdownModule>>({});
	let unlockedModuleSlugs = $state<Set<string>>(new Set());
	let selectedCategory = $state<string>('');
	let loading = $state(true);

	// Basic articles that are always visible
	const basicArticles = ['linux', 'algorithms', 'git'];

	onMount(async () => {
		try {
			// 1. Load all markdown files from local directory
			const mdFiles = import.meta.glob('/src/lib/knowledge/content/*.md');
			const loadedMarkdown: Record<string, MarkdownModule> = {};

			for (const path in mdFiles) {
				const slug = path.split('/').pop()?.replace('.md', '') || '';
				const mod = (await mdFiles[path]()) as MarkdownModule;
				loadedMarkdown[slug] = mod;
			}
			allMarkdownCategories = loadedMarkdown;

			// 2. Fetch module status from Backend API
			const res = await auth.apiFetch('/api/modules');
			if (res.ok) {
				const modulesData: ApiModule[] = await res.json();
				unlockedModuleSlugs = new Set(
					modulesData.filter((m) => m.isUnlocked).map((m) => m.slug)
				);
			}
		} catch (e) {
			console.error('Failed to load knowledge base data', e);
		} finally {
			loading = false;
			// Select the first available category after filtering
			if (filteredCategoryEntries.length > 0) {
				selectedCategory = filteredCategoryEntries[0][0];
			}
		}
	});

	// Reactive filtering logic
	let filteredCategoryEntries = $derived(
		Object.entries(allMarkdownCategories).filter(([slug]) => {
			// Admins see everything
			if (auth.user?.role === 'ADMIN') return true;
			// Basics are always visible
			if (basicArticles.includes(slug)) return true;
			// Otherwise only if unlocked in Backend
			return unlockedModuleSlugs.has(slug);
		})
	);

	let currentCategory = $derived(allMarkdownCategories[selectedCategory]);
</script>

<div class="mx-auto max-w-7xl">
	<header class="mb-12 border-b border-slate-200 pb-8">
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Wissensdatenbank
		</h1>
		<p class="mt-4 text-slate-500">Zentrale Informationsquelle für IT-Konzepte und Syntax-Referenzen.</p>
	</header>

	{#if loading}
		<div class="flex justify-center py-24">
			<div class="h-12 w-12 border-4 border-slate-200 border-t-institutional-navy animate-spin"></div>
		</div>
	{:else}
		<div class="grid grid-cols-1 gap-12 lg:grid-cols-4">
			<!-- Sidebar Navigation -->
			<div class="space-y-1 lg:col-span-1">
				{#each filteredCategoryEntries as [id, cat] (id)}
					<button
						onclick={() => (selectedCategory = id)}
						class="flex w-full items-center justify-start text-left gap-4 border-l-4 p-5 font-sans text-xs font-bold tracking-widest uppercase transition-all
                        {selectedCategory === id
							? 'border-institutional-gold bg-amber-50 text-institutional-gold shadow-sm'
							: 'border-transparent text-slate-500 hover:bg-slate-50 hover:text-slate-700'}"
					>
						<span class="text-xl grayscale">{cat.metadata.icon}</span>
						{cat.metadata.title}
					</button>
				{/each}

				{#if filteredCategoryEntries.length === 0}
					<p class="p-5 font-mono text-[10px] text-slate-400 uppercase italic">
						Warte auf Freischaltung der Inhalte...
					</p>
				{/if}
			</div>

			<!-- Content Area -->
			<div class="lg:col-span-3">
				{#if currentCategory}
					{@const Content = currentCategory.default}
					<div class="border border-slate-200 bg-white p-12 shadow-sm rounded-none">
						<div class="mb-16 flex items-center gap-6">
							<span class="text-5xl">{currentCategory.metadata.icon}</span>
							<h2 class="font-sans text-3xl font-extrabold text-institutional-navy uppercase tracking-tight">
								{currentCategory.metadata.title}
							</h2>
						</div>

						<article class="prose prose-slate max-w-none prose-headings:text-institutional-navy prose-a:text-institutional-gold">
							<Content />
						</article>
					</div>
				{:else}
					<div class="flex h-64 items-center justify-center border border-dashed border-slate-300">
						<p class="text-slate-400 italic">Wähle eine Kategorie aus oder warte auf Freischaltung.</p>
					</div>
				{/if}
			</div>
		</div>
	{/if}
</div>

<style>
	/* Custom styling for code blocks in markdown */
	:global(.prose pre) {
		background-color: #0f172a !important;
		padding: 1.5rem !important;
		border: 4px solid #1e293b !important;
		border-radius: 0 !important;
	}

	:global(.prose code) {
		color: #2dd4bf !important;
		font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
			monospace !important;
	}
</style>
