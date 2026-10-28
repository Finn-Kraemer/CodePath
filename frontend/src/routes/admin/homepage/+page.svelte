<script lang="ts">
	import PageEditor from '$lib/homepage/PageEditor.svelte';
	import { DEFAULT_HOMEPAGE } from '$lib/homepage/types';
</script>

<PageEditor base="/api/admin/homepage" fallback={DEFAULT_HOMEPAGE} kind="homepage">
	{#snippet header()}
		<div class="mb-2 flex items-center gap-3 text-institutional-navy/40">
			<a href="/admin" class="transition-colors hover:text-institutional-gold">
				<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase">Admin Dashboard</span
				>
			</a>
			<span class="text-slate-300">/</span>
			<span class="font-mono text-[10px] font-bold tracking-[4px] text-institutional-gold uppercase"
				>Startseite</span
			>
		</div>
		<h1
			class="font-sans text-5xl leading-none font-black tracking-tighter text-institutional-navy uppercase"
		>
			Startseite
		</h1>
		<p class="mt-3 max-w-2xl text-sm text-slate-500">
			Abschnitte hinzufügen, umsortieren und Texte ändern. Ein Entwurf ist nur hier sichtbar; erst
			„Veröffentlichen“ ändert die Startseite für alle.
		</p>
	{/snippet}
</PageEditor>
