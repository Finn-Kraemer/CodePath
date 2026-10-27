<script lang="ts">
	let { output = '', error = '' }: { output?: string; error?: string } = $props();
</script>

<div
	class="h-40 overflow-y-auto rounded-none border border-slate-100 bg-slate-50 p-6 font-mono text-xs whitespace-pre-wrap {error
		? 'text-rose-700'
		: 'text-slate-600'}"
	aria-live="polite"
>
	{error || output || 'Noch keine Ausgabe.'}
</div>
