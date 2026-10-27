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
	class="w-full rounded-none bg-institutional-navy py-5 font-sans text-xs font-black tracking-[4px] text-white uppercase shadow-lg transition-all hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-40"
>
	{busy ? 'Wird geprüft …' : label}
</button>
