<script lang="ts">
	import MediaPicker from '$lib/components/MediaPicker.svelte';
	import { mediaUrl } from '$lib/media';
	import { nextId, type Config } from '../types';

	type Target = { id: string; x: number; y: number; w: number; h: number; label?: string };
	let { config = $bindable() }: { config: Config } = $props();

	const targets = $derived((config.targets ?? []) as Target[]);
	let start = $state<{ x: number; y: number } | null>(null);
	let draft = $state<Target | null>(null);

	function point(event: PointerEvent) {
		const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
		const clamp = (v: number) => Math.min(100, Math.max(0, Math.round(v * 10) / 10));
		return {
			x: clamp(((event.clientX - rect.left) / rect.width) * 100),
			y: clamp(((event.clientY - rect.top) / rect.height) * 100)
		};
	}

	function down(event: PointerEvent) {
		(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
		start = point(event);
		draft = null;
	}

	function move(event: PointerEvent) {
		if (!start) return;
		const p = point(event);
		draft = {
			id: '',
			x: Math.min(start.x, p.x),
			y: Math.min(start.y, p.y),
			w: Math.round(Math.abs(p.x - start.x) * 10) / 10,
			h: Math.round(Math.abs(p.y - start.y) * 10) / 10
		};
	}

	function up() {
		if (draft && draft.w >= 1 && draft.h >= 1) {
			config.targets = [...targets, { ...draft, id: nextId(targets, 'z'), label: '' }];
		}
		start = null;
		draft = null;
	}
</script>

<p class="ed-label">Bild</p>
{#if config.image}
	<p class="mt-1 font-mono text-[10px] break-all text-slate-400">{config.image}</p>
{/if}
<MediaPicker
	label={config.image ? 'Anderes Bild wählen' : 'Bild wählen oder hochladen'}
	onselect={(m) => (config.image = m.key)}
/>

<label class="ed-label mt-3"
	>Bildbeschreibung für Screenreader (ohne die Lösung zu verraten)<input
		class="ed-input"
		bind:value={config.alt}
	/></label
>

{#if config.image}
	<p class="ed-label mt-4">Zielbereiche: mit der Maus Rechtecke aufziehen</p>
	<div
		class="relative mt-2 cursor-crosshair touch-none select-none"
		role="application"
		aria-label="Zielbereiche zeichnen"
		onpointerdown={down}
		onpointermove={move}
		onpointerup={up}
	>
		<img src={mediaUrl(config.image)} alt="" class="block w-full" draggable="false" />
		{#each targets as t, i (t.id)}
			<div
				class="pointer-events-none absolute border-2 border-green-600 bg-green-400/25"
				style={`left:${t.x}%;top:${t.y}%;width:${t.w}%;height:${t.h}%`}
			>
				<span class="bg-green-700 px-1 text-[10px] font-bold text-white">{i + 1}</span>
			</div>
		{/each}
		{#if draft}
			<div
				class="pointer-events-none absolute border-2 border-dashed border-amber-600 bg-amber-300/30"
				style={`left:${draft.x}%;top:${draft.y}%;width:${draft.w}%;height:${draft.h}%`}
			></div>
		{/if}
	</div>

	<div class="mt-3 space-y-2">
		{#each targets as t, i (t.id)}
			<div class="flex items-center gap-2">
				<span class="w-6 font-mono text-xs font-bold text-green-700">{i + 1}</span>
				<input
					class="ed-input mt-0"
					bind:value={t.label}
					placeholder="Erklärung nach dem Lösen, z. B. „Passwort am Monitor“"
					aria-label={`Bezeichnung Zielbereich ${i + 1}`}
				/>
				<button
					type="button"
					class="ed-button"
					onclick={() => (config.targets = targets.filter((_, j) => j !== i))}
					aria-label={`Zielbereich ${i + 1} entfernen`}>✕</button
				>
			</div>
		{/each}
	</div>
{/if}

<label class="mt-3 flex items-center gap-2 text-sm">
	<input
		type="checkbox"
		checked={config.showCount !== false}
		onchange={(e) => (config.showCount = e.currentTarget.checked)}
	/> Anzahl der gesuchten Stellen verraten
</label>
