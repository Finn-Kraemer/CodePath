<script lang="ts">
	/**
	 * Sammel-Export „je Person“ (Backend: platform.pdf.PdfBundles): alle Dateien als ZIP, als eine PDF
	 * oder direkt zum Drucken im neuen Tab. „Beidseitig“ fügt Leerseiten ein, damit jede Person auf
	 * einem neuen Blatt beginnt.
	 */
	let { url, label, testid }: { url: string; label: string; testid?: string } = $props();

	let duplex = $state(false);

	function href(params: Record<string, string>) {
		const all = duplex && params.format === 'pdf' ? { ...params, duplex: 'true' } : params;
		const query = new URLSearchParams(all).toString();
		return `${url}${url.includes('?') ? '&' : '?'}${query}`;
	}
</script>

<div class="flex flex-wrap items-center gap-2" role="group" aria-label={label} data-testid={testid}>
	<span class="font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
		>{label}</span
	>
	<a class="ed-button" href={href({ format: 'zip' })} download>ZIP</a>
	<a class="ed-button" href={href({ format: 'pdf' })} download title="Alle in einer PDF-Datei"
		>Eine PDF</a
	>
	<a
		class="ed-button"
		href={href({ format: 'pdf', inline: 'true' })}
		target="_blank"
		rel="noopener"
		title="Öffnet alle in einer PDF zum Drucken">Drucken</a
	>
	<label
		class="flex items-center gap-1 text-xs text-slate-500"
		title="Jede Person beginnt auf einem neuen Blatt"
	>
		<input type="checkbox" bind:checked={duplex} /> beidseitig
	</label>
</div>
