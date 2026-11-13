<script lang="ts">
	import MediaPicker from '$lib/components/MediaPicker.svelte';
	import { mediaUrl } from '$lib/media';
	import { isTask, type WorksheetBlock } from './types';

	/** Formular für einen Block eines Aufgabenblatts; ändert den Block direkt (bind:block). */
	let { block = $bindable() }: { block: WorksheetBlock } = $props();

	const formatHint =
		'Formatierung: **fett**, *kursiv*, `Code`, Zeilen mit „- “ oder „1. “ als Liste.';

	function move<T>(list: T[], i: number, delta: number) {
		const j = i + delta;
		if (j < 0 || j >= list.length) return;
		[list[i], list[j]] = [list[j], list[i]];
	}
</script>

{#snippet arrows(list: unknown[], i: number)}
	<button
		type="button"
		class="ed-button px-2"
		onclick={() => move(list, i, -1)}
		disabled={i === 0}
		aria-label="Nach oben">↑</button
	>
	<button
		type="button"
		class="ed-button px-2"
		onclick={() => move(list, i, 1)}
		disabled={i === list.length - 1}
		aria-label="Nach unten">↓</button
	>
	<button
		type="button"
		class="ed-button px-2 text-rose-700"
		onclick={() => list.splice(i, 1)}
		aria-label="Entfernen">×</button
	>
{/snippet}

<div class="flex flex-col gap-4">
	{#if 'prompt' in block}
		<label class="ed-label"
			>Arbeitsauftrag{block.type === 'table' ? ' (leer = keine nummerierte Aufgabe)' : ''}
			<textarea class="ed-input" rows="3" bind:value={block.prompt}></textarea>
		</label>
		{#if block.type !== 'table' || block.prompt.trim()}
			<div class="grid grid-cols-[8rem_1fr] gap-3">
				<label class="ed-label"
					>Punkte
					<input
						class="ed-input"
						type="number"
						min="0"
						max="100"
						step="0.5"
						bind:value={block.points}
					/>
				</label>
				<label class="ed-label"
					>Hinweis (klein, kursiv)
					<input class="ed-input" bind:value={block.hint} placeholder="z. B. Tipp: Denke an …" />
				</label>
			</div>
		{/if}
	{/if}

	{#if block.type === 'heading'}
		<label class="ed-label"
			>Text
			<input class="ed-input" bind:value={block.text} />
		</label>
		<label class="ed-label"
			>Größe
			<select class="ed-input" bind:value={block.level}>
				<option value={1}>Groß (Abschnitt)</option>
				<option value={2}>Klein (Unterabschnitt, in Akzentfarbe)</option>
			</select>
		</label>
	{:else if block.type === 'text'}
		<label class="ed-label"
			>Text
			<textarea class="ed-input" rows="6" bind:value={block.text}></textarea>
		</label>
		<p class="text-xs text-slate-500">{formatHint}</p>
	{:else if block.type === 'box'}
		<div class="grid grid-cols-2 gap-3">
			<label class="ed-label"
				>Überschrift
				<input class="ed-input" bind:value={block.title} />
			</label>
			<label class="ed-label"
				>Stil
				<select class="ed-input" bind:value={block.style}>
					<option value="info">Info (blau)</option>
					<option value="tip">Tipp (grün)</option>
					<option value="important">Achtung (rot)</option>
					<option value="neutral">Neutral (grau)</option>
					<option value="accent">Akzentfarbe</option>
				</select>
			</label>
		</div>
		<label class="ed-label"
			>Text
			<textarea class="ed-input" rows="4" bind:value={block.text}></textarea>
		</label>
		<p class="text-xs text-slate-500">{formatHint}</p>
	{:else if block.type === 'task'}
		<div class="grid grid-cols-2 gap-3">
			<label class="ed-label"
				>Antwortfeld
				<select class="ed-input" bind:value={block.answer}>
					<option value="lines">Linien</option>
					<option value="box">Leerer Kasten</option>
					<option value="grid">Kästchen (5 mm)</option>
					<option value="none">Kein Platz</option>
				</select>
			</label>
			{#if block.answer !== 'none'}
				<label class="ed-label"
					>Höhe (Zeilen)
					<input class="ed-input" type="number" min="1" max="40" bind:value={block.size} />
				</label>
			{/if}
		</div>
		{#if block.parts.length === 0}
			<label class="ed-label"
				>Musterlösung (nur in der Lösung, rot)
				<textarea class="ed-input" rows="3" bind:value={block.solution}></textarea>
			</label>
		{/if}
		<div>
			<p class="ed-label">Teilaufgaben a), b), …</p>
			<div class="mt-2 flex flex-col gap-3">
				{#each block.parts as part, i (i)}
					<div class="border border-slate-200 p-2">
						<div class="flex items-start gap-2">
							<span class="pt-2 font-bold text-institutional-navy"
								>{String.fromCharCode(97 + i)})</span
							>
							<div class="flex min-w-0 flex-1 flex-col gap-2">
								<textarea
									class="ed-input mt-0"
									rows="2"
									placeholder="Teilaufgabe"
									bind:value={part.text}
								></textarea>
								<input
									class="ed-input mt-0"
									placeholder="Lösung (optional)"
									bind:value={part.solution}
								/>
							</div>
							<div class="flex gap-1">{@render arrows(block.parts, i)}</div>
						</div>
					</div>
				{/each}
			</div>
			<button
				type="button"
				class="ed-button mt-2"
				onclick={() => block.type === 'task' && block.parts.push({ text: '', solution: '' })}
				>+ Teilaufgabe</button
			>
		</div>
	{:else if block.type === 'choice'}
		<label class="ed-label"
			>Spalten
			<select class="ed-input" bind:value={block.columns}>
				{#each [1, 2, 3, 4] as n (n)}<option value={n}>{n}</option>{/each}
			</select>
		</label>
		<div>
			<p class="ed-label">Antworten (Haken = richtig, wird nur in der Lösung angekreuzt)</p>
			<div class="mt-2 flex flex-col gap-2">
				{#each block.options as option, i (i)}
					<div class="flex items-center gap-2">
						<input type="checkbox" bind:checked={option.correct} aria-label="Richtig" />
						<input class="ed-input mt-0" bind:value={option.text} />
						{@render arrows(block.options, i)}
					</div>
				{/each}
			</div>
			<button
				type="button"
				class="ed-button mt-2"
				onclick={() => block.type === 'choice' && block.options.push({ text: '', correct: false })}
				>+ Antwort</button
			>
		</div>
	{:else if block.type === 'gap'}
		<label class="ed-label"
			>Text mit Lücken
			<textarea class="ed-input" rows="6" bind:value={block.text}></textarea>
		</label>
		<p class="text-xs text-slate-500">
			Lücken in doppelte eckige Klammern: <code>[[Wort]]</code>. Gleichwertige Antworten mit
			<code>|</code> trennen, z. B. <code>[[Variable|Speicher]]</code> – die erste steht in der Lösung.
		</p>
		<label class="flex items-center gap-2 text-sm text-slate-700">
			<input type="checkbox" bind:checked={block.wordBank} />
			Wortspeicher über dem Text anzeigen (alphabetisch)
		</label>
		{#if block.wordBank}
			<label class="ed-label"
				>Zusätzliche falsche Wörter (eins pro Zeile)
				<textarea class="ed-input" rows="2" bind:value={block.distractors}></textarea>
			</label>
		{/if}
	{:else if block.type === 'truefalse'}
		<div>
			<p class="ed-label">Aussagen (Haken = richtig)</p>
			<div class="mt-2 flex flex-col gap-2">
				{#each block.statements as statement, i (i)}
					<div class="flex items-center gap-2">
						<input type="checkbox" bind:checked={statement.correct} aria-label="Richtig" />
						<input class="ed-input mt-0" bind:value={statement.text} />
						{@render arrows(block.statements, i)}
					</div>
				{/each}
			</div>
			<button
				type="button"
				class="ed-button mt-2"
				onclick={() =>
					block.type === 'truefalse' && block.statements.push({ text: '', correct: true })}
				>+ Aussage</button
			>
		</div>
		<div class="grid grid-cols-2 gap-3">
			<label class="ed-label"
				>Spalte „richtig“
				<input class="ed-input" bind:value={block.trueLabel} placeholder="richtig" />
			</label>
			<label class="ed-label"
				>Spalte „falsch“
				<input class="ed-input" bind:value={block.falseLabel} placeholder="falsch" />
			</label>
		</div>
	{:else if block.type === 'matching'}
		<div>
			<p class="ed-label">Paare (rechte Spalte wird im PDF gemischt)</p>
			<div class="mt-2 flex flex-col gap-2">
				{#each block.pairs as pair, i (i)}
					<div class="flex items-center gap-2">
						<input class="ed-input mt-0" placeholder="Begriff" bind:value={pair.left} />
						<span class="text-slate-400">↔</span>
						<input class="ed-input mt-0" placeholder="passt zu" bind:value={pair.right} />
						{@render arrows(block.pairs, i)}
					</div>
				{/each}
			</div>
			<button
				type="button"
				class="ed-button mt-2"
				onclick={() => block.type === 'matching' && block.pairs.push({ left: '', right: '' })}
				>+ Paar</button
			>
		</div>
	{:else if block.type === 'order'}
		<div>
			<p class="ed-label">Schritte in der richtigen Reihenfolge (im PDF gemischt)</p>
			<div class="mt-2 flex flex-col gap-2">
				{#each block.items as _, i (i)}
					<div class="flex items-center gap-2">
						<span class="w-6 text-right font-mono text-xs text-slate-400">{i + 1}.</span>
						<input class="ed-input mt-0" bind:value={block.items[i]} />
						{@render arrows(block.items, i)}
					</div>
				{/each}
			</div>
			<button
				type="button"
				class="ed-button mt-2"
				onclick={() => block.type === 'order' && block.items.push('')}>+ Schritt</button
			>
		</div>
	{:else if block.type === 'table'}
		{@const columns = Math.max(1, ...block.rows.map((r) => r.length))}
		<label class="flex items-center gap-2 text-sm text-slate-700">
			<input type="checkbox" bind:checked={block.header} />
			Erste Zeile ist Kopfzeile
		</label>
		<div class="overflow-x-auto">
			<table class="w-full border-collapse">
				<tbody>
					{#each block.rows as row, r (r)}
						<tr>
							{#each Array.from({ length: columns }, (_, c) => c) as c (c)}
								<td class="p-0.5">
									<input
										class="ed-input mt-0 min-w-24 px-2 py-1 {block.header && r === 0
											? 'font-bold'
											: ''}"
										value={row[c] ?? ''}
										oninput={(e) => (row[c] = e.currentTarget.value)}
									/>
								</td>
							{/each}
							<td class="p-0.5">
								<button
									type="button"
									class="ed-button px-2 text-rose-700"
									aria-label="Zeile entfernen"
									disabled={block.rows.length <= 1}
									onclick={() => block.type === 'table' && block.rows.splice(r, 1)}>×</button
								>
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
		<div class="flex flex-wrap gap-2">
			<button
				type="button"
				class="ed-button"
				onclick={() => block.type === 'table' && block.rows.push(Array(columns).fill(''))}
				>+ Zeile</button
			>
			<button
				type="button"
				class="ed-button"
				onclick={() => block.type === 'table' && block.rows.forEach((r) => r.push(''))}
				>+ Spalte</button
			>
			<button
				type="button"
				class="ed-button"
				disabled={columns <= 1}
				onclick={() =>
					block.type === 'table' && block.rows.forEach((r) => r.length >= columns && r.pop())}
				>− Spalte</button
			>
		</div>
		<p class="text-xs text-slate-500">
			Zellen mit <code>[[Antwort]]</code> bleiben im Schülerblatt leer und zeigen in der Lösung die Antwort.
		</p>
		{#if isTask(block)}
			<label class="ed-label"
				>Zusätzliche Lösung unter der Tabelle (optional)
				<textarea class="ed-input" rows="2" bind:value={block.solution}></textarea>
			</label>
		{/if}
	{:else if block.type === 'code'}
		<label class="ed-label"
			>Überschrift (optional, z. B. Dateiname)
			<input class="ed-input" bind:value={block.caption} />
		</label>
		<label class="ed-label"
			>Code
			<textarea class="ed-input mono" rows="8" spellcheck="false" bind:value={block.code}
			></textarea>
		</label>
		<label class="flex items-center gap-2 text-sm text-slate-700">
			<input type="checkbox" bind:checked={block.lineNumbers} />
			Zeilennummern
		</label>
		<label class="flex items-center gap-2 text-sm text-slate-700">
			<input type="checkbox" bind:checked={block.gaps} />
			Lückencode: <code>[[…]]</code> wird zur Lücke
		</label>
	{:else if block.type === 'image'}
		{#if block.key}
			<img src={mediaUrl(block.key)} alt="" class="max-h-40 border border-slate-200" />
		{/if}
		<MediaPicker
			label={block.key ? 'Anderes Bild wählen' : 'Bild wählen'}
			onselect={(m) => block.type === 'image' && (block.key = m.key)}
		/>
		<div class="grid grid-cols-2 gap-3">
			<label class="ed-label"
				>Breite: {block.width} %
				<input
					class="mt-2 w-full"
					type="range"
					min="10"
					max="100"
					step="5"
					bind:value={block.width}
				/>
			</label>
			<label class="ed-label"
				>Ausrichtung
				<select class="ed-input" bind:value={block.align}>
					<option value="left">Links</option>
					<option value="center">Mitte</option>
					<option value="right">Rechts</option>
				</select>
			</label>
		</div>
		<label class="ed-label"
			>Bildunterschrift
			<input class="ed-input" bind:value={block.caption} />
		</label>
		<p class="text-xs text-slate-500">WebP-Bilder können im PDF fehlen – PNG oder JPEG nehmen.</p>
	{:else if block.type === 'space'}
		<label class="ed-label"
			>Höhe (Zeilen)
			<input class="ed-input" type="number" min="1" max="40" bind:value={block.size} />
		</label>
	{:else if block.type === 'divider' || block.type === 'pagebreak'}
		<p class="text-sm text-slate-500">Keine Einstellungen.</p>
	{/if}

	{#if 'prompt' in block}
		<p class="text-xs text-slate-500">{formatHint}</p>
	{/if}

	<label class="ed-label"
		>Sichtbar in
		<select class="ed-input" bind:value={block.show}>
			<option value="both">Schülerblatt und Lösung</option>
			<option value="student">Nur Schülerblatt</option>
			<option value="solution">Nur Lösung (z. B. Erwartungshorizont)</option>
		</select>
	</label>
</div>
