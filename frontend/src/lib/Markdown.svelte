<script lang="ts">
	import { renderMarkdown } from '$lib/markdown';

	let { source = '', class: className = '' }: { source?: string | null; class?: string } = $props();

	let html = $derived(renderMarkdown(source ?? ''));
</script>

<!-- Einzige Stelle mit {@html}: Markdown wird vorher mit DOMPurify bereinigt -->
<div class="markdown {className}">{@html html}</div>
