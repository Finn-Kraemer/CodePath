<!-- frontend/src/lib/components/editor/PracticeReview.svelte -->
<script lang="ts">
  import DOMPurify from 'dompurify';
  import './editor.css';

  let { content = '' } = $props<{ content?: string }>();
  let sanitized = $derived(DOMPurify.sanitize(content));
</script>

<div class="tiptap-content p-6 border border-slate-100 bg-slate-50 rounded-none shadow-inner overflow-auto max-h-[500px]">
  {@html sanitized}
</div>
