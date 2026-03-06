<script lang="ts">
	import { onMount } from 'svelte';

	interface MarkdownModule {
		default: any;
		metadata: {
			title: string;
			icon: string;
		};
	}

	let categories = $state<Record<string, MarkdownModule>>({});
	let selectedCategory = $state<string>('');

	onMount(async () => {
		const modules = import.meta.glob('/src/lib/knowledge/content/*.md');
		const loadedCategories: Record<string, MarkdownModule> = {};

		for (const path in modules) {
			const slug = path.split('/').pop()?.replace('.md', '') || '';
			const mod = (await modules[path]()) as MarkdownModule;
			loadedCategories[slug] = mod;
		}

		categories = loadedCategories;
		if (Object.keys(categories).length > 0) {
			selectedCategory = Object.keys(categories)[0];
		}
	});

	let currentCategory = $derived(categories[selectedCategory]);
</script>

<div class="mx-auto max-w-7xl">
	<header class="mb-12 border-b border-slate-200 pb-8">
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Wissensdatenbank
		</h1>
		<p class="mt-4 text-slate-500">Zentrale Informationsquelle für IT-Konzepte und Syntax-Referenzen.</p>
	</header>

	<div class="grid grid-cols-1 gap-12 lg:grid-cols-4">
		<!-- Sidebar Navigation -->
		<div class="space-y-1 lg:col-span-1">
			{#each Object.entries(categories) as [id, cat] (id)}
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
					<p class="text-slate-400">Wähle eine Kategorie aus oder erstelle Inhalte...</p>
				</div>
			{/if}
		</div>
	</div>
</div>

<style>
	/* Custom styling for code blocks in markdown if needed */
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
