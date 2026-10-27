<script lang="ts">
	import { lines, type Config } from '../types';

	let { config = $bindable() }: { config: Config } = $props();
</script>

<div class="grid grid-cols-2 gap-4">
	<label class="ed-label"
		>Ordner am Anfang (je Zeile)
		<textarea
			class="ed-input mono"
			rows="4"
			value={(config.initialDirectories ?? []).join('\n')}
			onchange={(e) => (config.initialDirectories = lines(e.currentTarget.value))}
		></textarea>
	</label>
	<label class="ed-label"
		>Erwartete Ordner
		<textarea
			class="ed-input mono"
			rows="4"
			value={(config.expectedDirectories ?? []).join('\n')}
			onchange={(e) => (config.expectedDirectories = lines(e.currentTarget.value))}
		></textarea>
	</label>
	<label class="ed-label col-span-2"
		>Erwartete Dateien (Pfad je Zeile, z. B. website/index.html)
		<textarea
			class="ed-input mono"
			rows="4"
			value={(config.expectedFiles ?? []).join('\n')}
			onchange={(e) => (config.expectedFiles = lines(e.currentTarget.value))}
		></textarea>
	</label>
</div>
<label class="mt-3 flex items-center gap-2 text-sm"
	><input type="checkbox" bind:checked={config.allowExtra} /> Zusätzliche Dateien und Ordner erlauben</label
>
