<script lang="ts">
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';
	import { siteNavigation } from '$lib/site/navigation.svelte';

	let loaded = $state(false);

	onMount(async () => {
		await siteNavigation.refresh();
		loaded = true;
	});
</script>

<svelte:head>
	<title>Seiten – CodePath</title>
</svelte:head>

<div class="mx-auto max-w-6xl">
	<header
		class="mb-12 flex flex-wrap items-end justify-between gap-4 border-b border-slate-200 pb-8"
	>
		<div>
			<span
				class="mb-4 inline-block font-mono text-[10px] font-bold tracking-[4px] text-institutional-gold uppercase"
				>Überblick</span
			>
			<h1
				class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase"
			>
				Seiten
			</h1>
			<p class="mt-4 text-slate-500">Alle Informationsseiten auf einen Blick.</p>
		</div>
		{#if auth.user?.role === 'ADMIN'}
			<a href="/admin/pages" class="ed-button bg-white">Seiten verwalten</a>
		{/if}
	</header>

	{#if !loaded}
		<div class="min-h-[30vh]" aria-busy="true"></div>
	{:else if siteNavigation.links.length === 0}
		<p class="border border-dashed border-slate-300 p-12 text-center text-slate-400">
			{auth.isAuthenticated
				? 'Noch keine Seiten veröffentlicht.'
				: 'Noch keine öffentlichen Seiten. Nach der Anmeldung siehst du eventuell weitere.'}
		</p>
	{:else}
		<ul class="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
			{#each siteNavigation.links as link (link.slug)}
				<li>
					<a
						href={`/seiten/${link.slug}`}
						class="group flex h-full flex-col border border-slate-200 bg-white p-8 shadow-sm transition-all hover:border-institutional-navy hover:shadow-xl"
					>
						<h2
							class="font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase"
						>
							{link.title}
						</h2>
						{#if link.description}
							<p class="mt-3 flex-1 leading-relaxed text-slate-500">{link.description}</p>
						{:else}
							<span class="flex-1"></span>
						{/if}
						<span
							class="mt-6 text-[10px] font-black tracking-widest text-institutional-gold uppercase group-hover:underline"
							>Öffnen →</span
						>
					</a>
				</li>
			{/each}
		</ul>
	{/if}
</div>
