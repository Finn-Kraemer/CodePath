<script lang="ts">
	import { onMount } from 'svelte';
	import HomepageView from '$lib/homepage/HomepageView.svelte';
	import { DEFAULT_HOMEPAGE, normalize, type HomepageContent } from '$lib/homepage/types';

	// Inhalt pflegt die Lehrkraft unter Admin → Startseite; ohne Veröffentlichung gilt die Standardseite
	let content = $state<HomepageContent | null>(null);

	onMount(async () => {
		try {
			const res = await fetch('/api/public/homepage', { credentials: 'include' });
			const data = res.ok ? await res.json() : null;
			content = normalize(data?.content) ?? DEFAULT_HOMEPAGE;
		} catch {
			content = DEFAULT_HOMEPAGE;
		}
	});
</script>

{#if content}
	<HomepageView {content} />
{:else}
	<!-- Bis die Startseite geladen ist: dunkle Fläche statt kurz aufblitzender Standardtexte -->
	<div class="min-h-[70vh] bg-institutional-navy" aria-busy="true"></div>
{/if}

<style>
	:global(html) {
		scroll-behavior: smooth;
	}
</style>
