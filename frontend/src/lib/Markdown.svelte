<script lang="ts">
	import { mount, unmount } from 'svelte';
	import { renderMarkdown } from '$lib/markdown';
	import VisualizerBlock from '$lib/visualizer/VisualizerBlock.svelte';

	let { source = '', class: className = '' }: { source?: string | null; class?: string } = $props();

	let html = $derived(renderMarkdown(source ?? ''));
	let container = $state<HTMLElement | null>(null);

	// Code-Blöcke ```schritte werden zu einem Code-Visualizer (nach dem Bereinigen, als Svelte-Komponente)
	$effect(() => {
		void html;
		if (!container) return;
		const mounted: ReturnType<typeof mount>[] = [];
		for (const code of container.querySelectorAll('pre > code.language-schritte')) {
			const pre = code.parentElement!;
			const target = document.createElement('div');
			pre.replaceWith(target);
			mounted.push(mount(VisualizerBlock, { target, props: { code: code.textContent ?? '' } }));
		}
		return () => mounted.forEach((component) => unmount(component));
	});
</script>

<!-- Einzige Stelle mit {@html}: Markdown wird vorher mit DOMPurify bereinigt -->
<div bind:this={container} class="markdown {className}">{@html html}</div>
