<script lang="ts">
	import type { Config } from '../types';

	let { config = $bindable() }: { config: Config } = $props();

	// Die Übungsseiten sind feste Vorlagen auf dem Server (kein eigenes HTML – Sicherheit)
	const scenarios = [
		{
			id: 'wetter',
			title: 'Die kaputte Wetterstation',
			description:
				'3 Teile: falsche API-Version im Netzwerk finden, Diagnosemodus aus dem Quelltext über localStorage einschalten, Messverlauf mit Schlüssel aus einem Antwort-Header in der Konsole abrufen.'
		},
		{
			id: 'shop',
			title: 'Der Schul-Shop',
			description:
				'3 Teile: ausgeblendeten Artikel im Inspektor finden, fehlerhafte Bestellung im Netzwerk analysieren und korrigiert per fetch() senden, Base64-Warenkorb aus dem sessionStorage dekodieren.'
		},
		{
			id: 'escape',
			title: 'Das Escape-Zimmer (Abschlusstest)',
			description:
				'4 Teile, ohne Anleitung: Text nur in einer CSS-Regel, Inhalt einer 404-Antwort, rückwärts gespeicherter Eintrag in IndexedDB, Tür per Konsole mit den ersten drei Teilen öffnen.'
		}
	];
</script>

<fieldset>
	<legend class="ed-label">Übungsseite</legend>
	<div class="mt-2 space-y-2">
		{#each scenarios as s (s.id)}
			<label class="flex items-start gap-3 border border-slate-200 p-3 text-sm">
				<input type="radio" bind:group={config.scenario} value={s.id} class="mt-1" />
				<span><b>{s.title}</b><br /><span class="text-slate-600">{s.description}</span></span>
			</label>
		{/each}
	</div>
</fieldset>
<p class="mt-3 text-xs text-slate-500">
	Jede Person bekommt eigene Codes. Die Vorschau rechts öffnet das Labor mit den Codes der Vorschau;
	„Lösung“ zeigt sie an.
</p>
