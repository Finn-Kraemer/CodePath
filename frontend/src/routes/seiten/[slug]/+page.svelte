<script lang="ts">
	import { page } from '$app/state';
	import { auth } from '$lib/auth.svelte';
	import HomepageView from '$lib/homepage/HomepageView.svelte';
	import { normalize, type HomepageContent } from '$lib/homepage/types';

	let title = $state('');
	let content = $state<HomepageContent | null>(null);
	let status = $state<'loading' | 'ok' | 'login' | 'missing'>('loading');

	$effect(() => {
		const slug = page.params.slug;
		// Nach An- oder Abmelden neu laden
		void auth.isAuthenticated;
		status = 'loading';
		fetch(`/api/public/pages/${encodeURIComponent(slug ?? '')}`, { credentials: 'include' })
			.then(async (res) => {
				if (res.status === 401) return (status = 'login');
				if (!res.ok) return (status = 'missing');
				const data = await res.json();
				title = data.title;
				content = normalize(data.content) ?? { blocks: [] };
				status = 'ok';
			})
			.catch(() => (status = 'missing'));
	});
</script>

<svelte:head>
	{#if status === 'ok'}<title>{title} – CodePath</title>{/if}
</svelte:head>

{#if status === 'ok' && content}
	<HomepageView {content} />
{:else if status === 'loading'}
	<div class="min-h-[50vh]" aria-busy="true"></div>
{:else}
	<div class="container-institutional py-32 text-center">
		<h1 class="font-sans text-4xl font-black tracking-tighter text-institutional-navy uppercase">
			{status === 'login' ? 'Bitte anmelden' : 'Seite nicht gefunden'}
		</h1>
		<p class="mt-4 text-slate-500">
			{status === 'login'
				? 'Diese Seite ist nur für angemeldete Nutzer sichtbar.'
				: 'Diese Seite gibt es nicht oder sie ist noch nicht veröffentlicht.'}
		</p>
		<a
			href={status === 'login' ? '/login' : '/'}
			class="mt-8 inline-block bg-institutional-navy px-8 py-4 text-[11px] font-bold tracking-[3px] text-white uppercase"
			>{status === 'login' ? 'Zum Login' : 'Zur Startseite'}</a
		>
	</div>
{/if}
