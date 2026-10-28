<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type PageSummary } from '$lib/api';
	import MediaPicker from '$lib/components/MediaPicker.svelte';
	import { mediaUrl } from '$lib/media';
	import { BLOCK_TYPES, safeHref, type Block, type LinkButton } from './types';

	let { block = $bindable() }: { block: Block } = $props();

	// Die Felder kommen aus BLOCK_TYPES; hier wird generisch auf sie zugegriffen
	const data = $derived(block as unknown as Record<string, unknown>);
	const fields = $derived(BLOCK_TYPES[block.type].fields);

	/** Interne Ziele für Buttons: feste Bereiche der Plattform und die eigenen Seiten */
	const ROUTES = [
		{ href: '/', label: 'Startseite' },
		{ href: '/modules', label: 'Lernmodule' },
		{ href: '/knowledge', label: 'Wissensdatenbank' },
		{ href: '/leaderboard', label: 'Bestenliste' },
		{ href: '/seiten', label: 'Seitenübersicht' },
		{ href: '/feedback', label: 'Feedback' },
		{ href: '/login', label: 'Login' },
		{ href: '/register', label: 'Registrieren' }
	];
	const CUSTOM = '__custom__';
	let pages = $state<PageSummary[]>([]);
	const targets = $derived([
		...ROUTES,
		...pages.map((p) => ({
			href: `/seiten/${p.slug}`,
			label: `Seite: ${p.title}${p.published ? '' : ' (nicht veröffentlicht)'}`
		}))
	]);

	onMount(() => {
		if (!fields.some((f) => f.kind === 'links')) return;
		api<PageSummary[]>('/api/admin/pages')
			.then((list) => (pages = list))
			.catch(() => {
				// Ohne Seitenliste bleiben die festen Ziele und die freie Eingabe
			});
	});

	/** Eigene Eingabe, wenn das Ziel keines der vorgeschlagenen internen Ziele ist */
	function isCustom(href: string): boolean {
		return href !== '' && !targets.some((t) => t.href === href);
	}

	function set(key: string, value: unknown) {
		block = { ...block, [key]: value } as Block;
	}

	function list<T>(key: string): T[] {
		return (data[key] as T[] | undefined) ?? [];
	}

	function move<T>(key: string, i: number, delta: number) {
		const items = [...list<T>(key)];
		const j = i + delta;
		if (j < 0 || j >= items.length) return;
		[items[i], items[j]] = [items[j], items[i]];
		set(key, items);
	}

	function remove(key: string, i: number) {
		set(
			key,
			list(key).filter((_, j) => j !== i)
		);
	}

	function update<T>(key: string, i: number, value: T) {
		set(
			key,
			list<T>(key).map((item, j) => (j === i ? value : item))
		);
	}
</script>

{#snippet rowTools(key: string, i: number, count: number)}
	<div class="flex shrink-0 gap-1">
		<button
			type="button"
			class="ed-button px-2"
			onclick={() => move(key, i, -1)}
			disabled={i === 0}
			aria-label="Nach oben">↑</button
		>
		<button
			type="button"
			class="ed-button px-2"
			onclick={() => move(key, i, 1)}
			disabled={i === count - 1}
			aria-label="Nach unten">↓</button
		>
		<button
			type="button"
			class="ed-button px-2 text-rose-700"
			onclick={() => remove(key, i)}
			aria-label="Entfernen">✕</button
		>
	</div>
{/snippet}

<div class="space-y-5">
	{#each fields as field (field.key)}
		{#if field.kind === 'text'}
			<label class="ed-label"
				>{field.label}
				<input
					class="ed-input"
					value={(data[field.key] as string) ?? ''}
					oninput={(e) => set(field.key, e.currentTarget.value)}
				/>
			</label>
		{:else if field.kind === 'textarea' || field.kind === 'markdown'}
			<label class="ed-label"
				>{field.label}
				<textarea
					class="ed-input min-h-28 {field.kind === 'markdown' ? 'mono' : ''}"
					value={(data[field.key] as string) ?? ''}
					oninput={(e) => set(field.key, e.currentTarget.value)}
				></textarea>
			</label>
			{#if field.kind === 'markdown'}
				<p class="-mt-4 text-[11px] text-slate-400">
					**fett**, *kursiv*, [Link](https://…), Listen mit „- “ am Zeilenanfang
				</p>
			{/if}
		{:else if field.kind === 'bool'}
			<label class="flex items-center gap-2 text-sm text-slate-700">
				<input
					type="checkbox"
					checked={!!data[field.key]}
					onchange={(e) => set(field.key, e.currentTarget.checked)}
				/>
				{field.label}
			</label>
		{:else if field.kind === 'select'}
			<label class="ed-label"
				>{field.label}
				<select
					class="ed-input"
					value={data[field.key] as string}
					onchange={(e) => set(field.key, e.currentTarget.value)}
				>
					{#each field.options as option (option.value)}
						<option value={option.value}>{option.label}</option>
					{/each}
				</select>
			</label>
		{:else if field.kind === 'media'}
			<div>
				<p class="ed-label">{field.label}</p>
				{#if data[field.key]}
					<img
						src={mediaUrl(data[field.key] as string)}
						alt=""
						class="mt-2 max-h-40 border border-slate-200"
					/>
					<button
						type="button"
						class="ed-button mt-2 text-rose-700"
						onclick={() => set(field.key, '')}>Bild entfernen</button
					>
				{/if}
				<MediaPicker
					label={data[field.key] ? 'Anderes Bild wählen' : 'Bild wählen'}
					onselect={(m) => set(field.key, m.key)}
				/>
			</div>
		{:else if field.kind === 'strings'}
			{@const items = list<string>(field.key)}
			<div>
				<p class="ed-label">{field.label}</p>
				<div class="mt-2 space-y-2">
					{#each items as item, i (i)}
						<div class="flex items-center gap-2">
							<input
								class="ed-input mt-0"
								value={item}
								placeholder={field.placeholder}
								aria-label={`${field.label} ${i + 1}`}
								oninput={(e) => update(field.key, i, e.currentTarget.value)}
							/>
							{@render rowTools(field.key, i, items.length)}
						</div>
					{/each}
				</div>
				<button type="button" class="ed-button mt-2" onclick={() => set(field.key, [...items, ''])}
					>+ Hinzufügen</button
				>
			</div>
		{:else if field.kind === 'links'}
			{@const items = list<LinkButton>(field.key)}
			<div>
				<p class="ed-label">{field.label}</p>
				<div class="mt-2 space-y-3">
					{#each items as item, i (i)}
						{@const custom = isCustom(item.href)}
						<div class="border border-slate-200 bg-slate-50 p-3">
							<div class="mb-2 flex items-center justify-between">
								<span class="font-mono text-[10px] text-slate-400">Button {i + 1}</span>
								{@render rowTools(field.key, i, items.length)}
							</div>
							<div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
								<label class="ed-label"
									>Beschriftung
									<input
										class="ed-input"
										value={item.label}
										oninput={(e) => update(field.key, i, { ...item, label: e.currentTarget.value })}
									/>
								</label>
								<label class="ed-label"
									>Ziel
									<select
										class="ed-input"
										value={custom ? CUSTOM : item.href}
										onchange={(e) => {
											const value = e.currentTarget.value;
											update(field.key, i, {
												...item,
												href: value === CUSTOM ? 'https://' : value,
												newTab: value === CUSTOM ? true : item.newTab
											});
										}}
									>
										<option value="" disabled>– Ziel wählen –</option>
										<optgroup label="Intern">
											{#each targets as target (target.href)}
												<option value={target.href}>{target.label}</option>
											{/each}
										</optgroup>
										<option value={CUSTOM}>Externe Adresse oder eigener Pfad …</option>
									</select>
								</label>
								{#if custom}
									<label class="ed-label sm:col-span-2"
										>Adresse
										<input
											class="ed-input mono"
											value={item.href}
											placeholder="https://beispiel.de oder /modules/…"
											oninput={(e) =>
												update(field.key, i, { ...item, href: e.currentTarget.value })}
										/>
									</label>
									{#if item.href && item.href !== 'https://' && !safeHref(item.href)}
										<p class="-mt-1 text-[11px] text-rose-700 sm:col-span-2">
											Ungültiges Ziel – erlaubt sind https://…, mailto:…, tel:… oder interne Pfade
											mit „/“.
										</p>
									{/if}
								{/if}
								<label class="flex items-center gap-2 text-sm text-slate-700 sm:col-span-2">
									<input
										type="checkbox"
										checked={item.newTab}
										onchange={(e) =>
											update(field.key, i, { ...item, newTab: e.currentTarget.checked })}
									/>
									In neuem Tab öffnen
								</label>
							</div>
						</div>
					{/each}
				</div>
				<button
					type="button"
					class="ed-button mt-2"
					onclick={() => set(field.key, [...items, { label: '', href: '', newTab: false }])}
					>+ Button hinzufügen</button
				>
			</div>
		{:else if field.kind === 'objects'}
			{@const items = list<Record<string, string>>(field.key)}
			<div>
				<p class="ed-label">{field.label}</p>
				<div class="mt-2 space-y-3">
					{#each items as item, i (i)}
						<div class="border border-slate-200 bg-slate-50 p-3">
							<div class="mb-2 flex items-center justify-between">
								<span class="font-mono text-[10px] text-slate-400">#{i + 1}</span>
								{@render rowTools(field.key, i, items.length)}
							</div>
							<div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
								{#each field.fields as sub (sub.key)}
									<label class="ed-label {sub.wide ? 'sm:col-span-2' : ''}"
										>{sub.label}
										{#if sub.wide}
											<textarea
												class="ed-input min-h-16"
												value={item[sub.key] ?? ''}
												oninput={(e) =>
													update(field.key, i, { ...item, [sub.key]: e.currentTarget.value })}
											></textarea>
										{:else}
											<input
												class="ed-input"
												value={item[sub.key] ?? ''}
												oninput={(e) =>
													update(field.key, i, { ...item, [sub.key]: e.currentTarget.value })}
											/>
										{/if}
									</label>
								{/each}
							</div>
						</div>
					{/each}
				</div>
				<button
					type="button"
					class="ed-button mt-2"
					onclick={() =>
						set(field.key, [
							...items,
							Object.fromEntries(field.fields.map((sub) => [sub.key, '']))
						])}>+ Hinzufügen</button
				>
			</div>
		{/if}
	{/each}
</div>
