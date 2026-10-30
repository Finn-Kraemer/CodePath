<!--
	CodePath-Zeichen: ein Weg, der ein C formt und im goldenen Ziel endet.
	Die Linie übernimmt die Textfarbe (currentColor), das Ziel ist immer Institutional Gold.
	Ohne `label` ist das Zeichen dekorativ und für Screenreader ausgeblendet.
-->
<script lang="ts">
	let { class: className = '', label }: { class?: string; label?: string } = $props();
</script>

<svg
	xmlns="http://www.w3.org/2000/svg"
	viewBox="0 0 48 48"
	class={className}
	role={label ? 'img' : undefined}
	aria-label={label}
	aria-hidden={label ? undefined : 'true'}
>
	<path d="M33 35H14V14h14" fill="none" stroke="currentColor" stroke-width="4.5" />
	<circle cx="33" cy="35" r="4" fill="currentColor" />
	<rect x="28" y="10" width="8" height="8" class="fill-institutional-gold" />
</svg>
