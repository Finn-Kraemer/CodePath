<script lang="ts">
	let {
		onclick,
		disabled = false,
		busy = false,
		label = 'Antwort prüfen'
	}: { onclick: () => void; disabled?: boolean; busy?: boolean; label?: string } = $props();
</script>

<button
	{onclick}
	disabled={disabled || busy}
	class="ui-btn ui-btn-primary min-h-[3.25rem] w-full text-base"
>
	{busy ? 'Wird geprüft …' : label}
</button>
