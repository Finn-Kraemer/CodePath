<!-- frontend/src/lib/components/editor/PracticeReview.svelte -->
<script lang="ts">
	import DOMPurify from 'dompurify';
	import './editor.css';

	let { content = '' } = $props<{ content?: string }>();
	let sanitized = $derived(DOMPurify.sanitize(content));
</script>

<div
	class="tiptap-content max-h-[500px] overflow-auto rounded-none border border-slate-100 bg-slate-50 p-6 shadow-inner"
>
	{@html sanitized}
</div>
