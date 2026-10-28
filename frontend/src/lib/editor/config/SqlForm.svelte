<script lang="ts">
	import type { Config } from '../types';

	let { config = $bindable() }: { config: Config } = $props();
</script>

<label class="ed-label"
	>Schema und Testdaten (CREATE TABLE …; INSERT …)<textarea
		class="ed-input mono"
		rows="7"
		bind:value={config.schemaSql}
	></textarea></label
>
<p class="mt-1 text-xs text-slate-500">
	Tipp: Testdaten so wählen, dass naheliegende falsche Abfragen ein anderes Ergebnis liefern.
</p>
<label class="ed-label mt-3"
	>Musterlösung (SELECT)<textarea class="ed-input mono" rows="3" bind:value={config.solutionSql}
	></textarea></label
>
<label class="ed-label mt-3"
	>Startvorgabe im Editor<textarea class="ed-input mono" rows="2" bind:value={config.starterSql}
	></textarea></label
>
<label class="mt-3 flex items-center gap-2 text-sm"
	><input type="checkbox" bind:checked={config.orderMatters} /> Reihenfolge der Zeilen muss stimmen (ORDER
	BY)</label
>
