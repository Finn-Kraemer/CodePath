<script lang="ts">
	import { lines, type Config } from '../types';

	let { config = $bindable() }: { config: Config } = $props();

	// Strukturierte Teile (Startdateien, Git, Prüfungen, Varianten) werden als JSON bearbeitet
	const jsonFields = [
		{
			key: 'initialFiles',
			label: 'Dateien am Anfang (JSON: {"pfad": "inhalt"})',
			empty: {}
		},
		{
			key: 'git',
			label:
				'Vorbereitetes Git-Repository (JSON: {"root": "app", "history": [{"message", "author", "branch", "files"}], "remote": true | [...]})',
			empty: null
		},
		{
			key: 'gitChecks',
			label:
				'Git-Prüfungen (JSON-Liste, z. B. [{"check": "commits", "min": 2}]; check: repo, commits, committed, neverCommitted, branch, head, merged, clean, pushed, tag, ignored, author)',
			empty: null
		},
		{
			key: 'variants',
			label:
				'Varianten je Person (JSON-Liste, z. B. [{"ip": "10.0.0.7"}, {"ip": "10.0.0.9"}]; Platzhalter {{ip}}, immer verfügbar: {{code}})',
			empty: null
		}
	] as const;

	let texts = $state<Record<string, string>>(
		Object.fromEntries(
			jsonFields.map((f) => [
				f.key,
				config[f.key] === undefined ? '' : JSON.stringify(config[f.key], null, 2)
			])
		)
	);
	let errors = $state<Record<string, string>>({});

	function applyJson(key: string, empty: unknown) {
		const text = texts[key].trim();
		try {
			if (text === '') {
				if (empty === null) delete config[key];
				else config[key] = empty;
			} else {
				config[key] = JSON.parse(text);
			}
			errors[key] = '';
		} catch (e) {
			errors[key] = (e as Error).message;
		}
	}

	function setList(key: string, value: string) {
		const list = lines(value);
		if (list.length === 0) delete config[key];
		else config[key] = list;
	}

	function setText(key: string, value: string) {
		if (value.trim() === '') delete config[key];
		else config[key] = value.trim();
	}
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
	<label class="ed-label"
		>Erwartete Dateien (Pfad je Zeile, z. B. website/index.html)
		<textarea
			class="ed-input mono"
			rows="4"
			value={(config.expectedFiles ?? []).join('\n')}
			onchange={(e) => (config.expectedFiles = lines(e.currentTarget.value))}
		></textarea>
	</label>
	<label class="ed-label"
		>Muss am Ende fehlen (Pfad je Zeile)
		<textarea
			class="ed-input mono"
			rows="4"
			value={(config.absent ?? []).join('\n')}
			onchange={(e) => setList('absent', e.currentTarget.value)}
		></textarea>
	</label>
	<label class="ed-label"
		>Frage für das Antwortfeld (leer = kein Antwortfeld)
		<input
			class="ed-input"
			value={config.answerLabel ?? ''}
			onchange={(e) => setText('answerLabel', e.currentTarget.value)}
		/>
	</label>
	<label class="ed-label"
		>Richtige Antworten (je Zeile; Groß-/Kleinschreibung egal)
		<textarea
			class="ed-input mono"
			rows="2"
			value={(Array.isArray(config.answer)
				? config.answer
				: config.answer
					? [config.answer]
					: []
			).join('\n')}
			onchange={(e) => setList('answer', e.currentTarget.value)}
		></textarea>
	</label>
	<label class="ed-label col-span-2"
		>Musterlösung (ein Befehl je Zeile – Pflicht bei Antwortfeld, Git oder „muss fehlen“)
		<textarea
			class="ed-input mono"
			rows="5"
			value={(config.solution ?? []).join('\n')}
			onchange={(e) => setList('solution', e.currentTarget.value)}
		></textarea>
	</label>
	{#each jsonFields as f (f.key)}
		<label class="ed-label col-span-2"
			>{f.label}
			<textarea
				class="ed-input mono text-xs"
				rows="5"
				bind:value={texts[f.key]}
				onblur={() => applyJson(f.key, f.empty)}
			></textarea>
			{#if errors[f.key]}<span class="mt-1 block text-xs text-rose-600">{errors[f.key]}</span>{/if}
		</label>
	{/each}
</div>
<label class="mt-3 flex items-center gap-2 text-sm"
	><input type="checkbox" bind:checked={config.allowExtra} /> Zusätzliche Dateien und Ordner erlauben</label
>
