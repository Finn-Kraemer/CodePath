<script lang="ts">
	import { onMount } from 'svelte';
	import {
		api,
		ApiError,
		type MediaCleanup,
		type MediaInfo,
		type MediaLibraryItem
	} from '$lib/api';
	import { formatBytes, mediaKinds, mediaUrl, uploadMedia } from '$lib/media';

	type Filter = 'ALL' | 'USED' | 'UNUSED' | 'TASK' | 'HOMEPAGE' | 'PAGE' | 'KNOWLEDGE';
	type Sort = 'NEWEST' | 'OLDEST' | 'LARGEST' | 'NAME';

	let items = $state<MediaLibraryItem[]>([]);
	let loading = $state(true);
	let error = $state('');
	let message = $state('');
	let query = $state('');
	let filter = $state<Filter>('ALL');
	let sort = $state<Sort>('NEWEST');
	let uploading = $state(0);
	let dragging = $state(false);
	let selectedKey = $state<string | null>(null);
	let name = $state('');
	let copied = $state('');
	let dialog = $state<HTMLDialogElement>();

	const selected = $derived(items.find((i) => i.key === selectedKey) ?? null);
	const unused = $derived(items.filter((i) => i.usages.length === 0));
	const totalBytes = $derived(items.reduce((sum, i) => sum + i.size, 0));

	const visible = $derived.by(() => {
		const q = query.trim().toLowerCase();
		const list = items.filter((item) => {
			if (filter === 'USED' && item.usages.length === 0) return false;
			if (filter === 'UNUSED' && item.usages.length > 0) return false;
			if (
				filter !== 'ALL' &&
				filter !== 'USED' &&
				filter !== 'UNUSED' &&
				!item.usages.some((u) => u.kind === filter)
			)
				return false;
			if (!q) return true;
			return (
				(item.originalName ?? '').toLowerCase().includes(q) ||
				item.key.startsWith(q) ||
				item.usages.some((u) => u.label.toLowerCase().includes(q))
			);
		});
		const byName = (i: MediaLibraryItem) => (i.originalName ?? i.key).toLowerCase();
		return list.sort((a, b) => {
			if (sort === 'OLDEST') return a.createdAt.localeCompare(b.createdAt);
			if (sort === 'LARGEST') return b.size - a.size;
			if (sort === 'NAME') return byName(a).localeCompare(byName(b), 'de');
			return b.createdAt.localeCompare(a.createdAt);
		});
	});

	async function load() {
		try {
			items = await api<MediaLibraryItem[]>('/api/admin/media/library');
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	}

	onMount(load);

	async function uploadFiles(files: FileList | File[] | null | undefined) {
		const list = Array.from(files ?? []);
		if (!list.length) return;
		error = '';
		message = '';
		const failed: string[] = [];
		let done = 0;
		uploading = list.length;
		for (const file of list) {
			try {
				await uploadMedia(file);
				done++;
			} catch (e) {
				failed.push(
					`${file.name}: ${e instanceof ApiError ? e.message : 'Hochladen fehlgeschlagen.'}`
				);
			} finally {
				uploading--;
			}
		}
		if (done) message = done === 1 ? '1 Bild hochgeladen.' : `${done} Bilder hochgeladen.`;
		if (failed.length) error = failed.join(' · ');
		await load();
	}

	function onInput(event: Event) {
		const input = event.currentTarget as HTMLInputElement;
		uploadFiles(input.files).finally(() => (input.value = ''));
	}

	function onDrop(event: DragEvent) {
		event.preventDefault();
		dragging = false;
		uploadFiles(event.dataTransfer?.files);
	}

	function open(item: MediaLibraryItem) {
		selectedKey = item.key;
		name = item.originalName ?? '';
		copied = '';
		dialog?.showModal();
	}

	function close() {
		dialog?.close();
	}

	async function rename(event: SubmitEvent) {
		event.preventDefault();
		if (!selected) return;
		error = '';
		try {
			const info = await api<MediaInfo>(`/api/admin/media/${selected.key}`, {
				method: 'PATCH',
				body: { originalName: name }
			});
			items = items.map((i) =>
				i.key === info.key ? { ...i, originalName: info.originalName } : i
			);
			name = info.originalName ?? '';
			message = 'Name gespeichert.';
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function remove(item: MediaLibraryItem) {
		if (!confirm(`Bild „${item.originalName ?? item.key}“ endgültig löschen?`)) return;
		error = '';
		try {
			await api(`/api/admin/media/${item.key}`, { method: 'DELETE' });
			items = items.filter((i) => i.key !== item.key);
			message = 'Bild gelöscht.';
			close();
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function cleanup() {
		if (
			!confirm(
				`${unused.length} unbenutzte(s) Bild(er) endgültig löschen (${formatBytes(
					unused.reduce((s, i) => s + i.size, 0)
				)})? Bilder, die in Aufgaben – auch in früheren Versionen –, auf der Startseite, in Seiten oder Wissensartikeln stecken, bleiben erhalten.`
			)
		)
			return;
		error = '';
		try {
			const result = await api<MediaCleanup>('/api/admin/media/delete-unused', { method: 'POST' });
			message = `${result.deleted} Bild(er) gelöscht, ${formatBytes(result.freedBytes)} frei.`;
			await load();
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function copy(text: string, what: string) {
		try {
			await navigator.clipboard.writeText(text);
			copied = what;
		} catch {
			copied = '';
			error = 'Kopieren nicht möglich – bitte von Hand markieren.';
		}
	}

	function markdown(item: MediaLibraryItem) {
		const alt = (item.originalName ?? 'Bild').replace(/\.[a-z]+$/i, '').replace(/[[\]]/g, '');
		return `![${alt}](${mediaUrl(item.key)})`;
	}

	function date(value: string) {
		return new Date(value).toLocaleDateString('de-DE', {
			day: '2-digit',
			month: '2-digit',
			year: 'numeric'
		});
	}
</script>

<div class="mx-auto flex max-w-6xl flex-col gap-8 pb-24">
	<header class="border-b border-slate-200 pb-8">
		<div class="mb-2 flex items-center gap-3 text-institutional-navy/40">
			<a href="/admin" class="transition-colors hover:text-institutional-gold">
				<span class="font-mono text-[11px] font-bold tracking-wider uppercase">Leitstand</span>
			</a>
			<span class="text-slate-300">/</span>
			<span class="font-mono text-[11px] font-bold tracking-wider text-institutional-gold uppercase"
				>Medien</span
			>
		</div>
		<h1
			class="font-sans text-3xl leading-none font-extrabold tracking-tight text-institutional-navy"
		>
			Medien
		</h1>
		<p class="mt-3 max-w-2xl text-sm text-slate-500">
			Alle hochgeladenen Bilder für Aufgaben, die Startseite, eigene Seiten und die
			Wissensdatenbank. Du siehst, wo jedes Bild steckt, kannst es umbenennen und Bilder löschen,
			die nirgends mehr verwendet werden.
		</p>
		{#if !loading}
			<dl class="mt-5 flex flex-wrap gap-6 font-mono text-[11px] tracking-wider uppercase">
				<div>
					<dt class="text-slate-400">Bilder</dt>
					<dd class="text-lg font-bold text-institutional-navy">{items.length}</dd>
				</div>
				<div>
					<dt class="text-slate-400">Speicher</dt>
					<dd class="text-lg font-bold text-institutional-navy">{formatBytes(totalBytes)}</dd>
				</div>
				<div>
					<dt class="text-slate-400">Unbenutzt</dt>
					<dd class="text-lg font-bold text-institutional-navy">{unused.length}</dd>
				</div>
			</dl>
		{/if}
	</header>

	{#if error}
		<p class="text-[11px] font-bold tracking-wider text-rose-700 uppercase" role="alert">
			{error}
		</p>
	{/if}
	{#if message}
		<p class="text-[11px] font-bold tracking-wider text-teal-700 uppercase" role="status">
			{message}
		</p>
	{/if}

	<label
		class="flex cursor-pointer flex-col items-center gap-2 border-2 border-dashed p-6 text-center text-sm transition-colors {dragging
			? 'border-institutional-gold bg-amber-50'
			: 'border-slate-300 bg-white hover:border-slate-400'}"
		ondragover={(e) => {
			e.preventDefault();
			dragging = true;
		}}
		ondragleave={() => (dragging = false)}
		ondrop={onDrop}
	>
		<span class="font-bold text-institutional-navy">Bilder hierher ziehen oder auswählen</span>
		<span class="text-xs text-slate-500"
			>PNG, JPEG, GIF, WebP – höchstens 2 MB und 4096 Pixel pro Seite; mehrere auf einmal möglich</span
		>
		<input
			type="file"
			class="sr-only"
			accept="image/png,image/jpeg,image/gif,image/webp"
			multiple
			onchange={onInput}
			disabled={uploading > 0}
		/>
		{#if uploading > 0}
			<span class="text-xs text-slate-500">Wird hochgeladen … (noch {uploading})</span>
		{/if}
	</label>

	<div
		class="flex flex-wrap items-end gap-4 rounded-[var(--radius-ui)] border border-slate-200 bg-white p-4"
	>
		<label class="ed-label min-w-48 flex-1"
			>Suchen
			<input
				class="ed-input"
				type="search"
				bind:value={query}
				placeholder="Dateiname oder Verwendung"
			/>
		</label>
		<label class="ed-label"
			>Anzeigen
			<select class="ed-input" bind:value={filter}>
				<option value="ALL">Alle Bilder</option>
				<option value="USED">Verwendet</option>
				<option value="UNUSED">Unbenutzt</option>
				{#each Object.entries(mediaKinds) as [kind, label] (kind)}
					<option value={kind}>{label}</option>
				{/each}
			</select>
		</label>
		<label class="ed-label"
			>Sortieren
			<select class="ed-input" bind:value={sort}>
				<option value="NEWEST">Neueste zuerst</option>
				<option value="OLDEST">Älteste zuerst</option>
				<option value="LARGEST">Größte zuerst</option>
				<option value="NAME">Name</option>
			</select>
		</label>
		<button class="ed-button text-rose-700" onclick={cleanup} disabled={unused.length === 0}
			>Unbenutzte löschen ({unused.length})</button
		>
	</div>

	{#if loading}
		<p class="text-sm text-slate-400">Lädt …</p>
	{:else if items.length === 0}
		<p class="border border-dashed border-slate-300 p-8 text-center text-sm text-slate-400">
			Noch keine Bilder hochgeladen.
		</p>
	{:else if visible.length === 0}
		<p class="border border-dashed border-slate-300 p-8 text-center text-sm text-slate-400">
			Keine Bilder für diese Auswahl.
		</p>
	{:else}
		<ul class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
			{#each visible as item (item.key)}
				<li class="flex flex-col rounded-[var(--radius-ui)] border border-slate-200 bg-white">
					<button
						type="button"
						class="flex h-36 items-center justify-center bg-slate-50 p-2 hover:bg-slate-100"
						onclick={() => open(item)}
						aria-label={`Details zu ${item.originalName ?? 'Bild'}`}
					>
						<img
							src={mediaUrl(item.key)}
							alt={item.originalName ?? 'Bild'}
							class="max-h-full max-w-full object-contain"
							loading="lazy"
						/>
					</button>
					<div class="flex flex-1 flex-col gap-1 p-3">
						<button
							type="button"
							class="truncate text-left text-sm font-bold text-institutional-navy hover:underline"
							title={item.originalName ?? item.key}
							onclick={() => open(item)}>{item.originalName ?? item.key.slice(0, 12)}</button
						>
						<p class="font-mono text-[11px] text-slate-400">
							{item.width && item.height ? `${item.width}×${item.height} · ` : ''}{formatBytes(
								item.size
							)} · {date(item.createdAt)}
						</p>
						<div
							class="mt-auto flex flex-wrap gap-1 pt-2 font-mono text-[11px] tracking-wider uppercase"
						>
							{#if item.usages.length === 0}
								<span class="bg-slate-100 px-2 py-1 text-slate-500">Unbenutzt</span>
							{:else}
								<span class="bg-teal-50 px-2 py-1 text-teal-700"
									>{item.usages.length}× verwendet</span
								>
							{/if}
						</div>
					</div>
				</li>
			{/each}
		</ul>
	{/if}
</div>

<dialog
	bind:this={dialog}
	class="m-auto w-[min(48rem,calc(100vw-2rem))] border border-slate-300 bg-white p-0 backdrop:bg-slate-900/50"
	onclose={() => (selectedKey = null)}
>
	{#if selected}
		<div class="flex flex-col gap-5 p-6">
			<div class="flex items-start justify-between gap-4">
				<h2 class="text-xl font-extrabold break-all text-institutional-navy">
					{selected.originalName ?? selected.key}
				</h2>
				<button class="ed-button" onclick={close} aria-label="Schließen">✕</button>
			</div>
			<div class="flex max-h-[50vh] items-center justify-center bg-slate-50 p-3">
				<img
					src={mediaUrl(selected.key)}
					alt={selected.originalName ?? 'Bild'}
					class="max-h-[46vh] max-w-full object-contain"
				/>
			</div>
			<p class="font-mono text-[11px] text-slate-500">
				{selected.contentType} · {selected.width && selected.height
					? `${selected.width}×${selected.height} Pixel · `
					: ''}{formatBytes(selected.size)} · hochgeladen am {date(selected.createdAt)}
			</p>

			<form class="flex flex-wrap items-end gap-3" onsubmit={rename}>
				<label class="ed-label flex-1"
					>Name
					<input class="ed-input" bind:value={name} maxlength="200" required />
				</label>
				<button class="ed-button" disabled={!name.trim() || name === (selected.originalName ?? '')}
					>Umbenennen</button
				>
			</form>

			<div class="flex flex-wrap gap-2">
				<button class="ed-button" onclick={() => copy(markdown(selected!), 'markdown')}
					>{copied === 'markdown' ? 'Kopiert ✓' : 'Markdown kopieren'}</button
				>
				<button
					class="ed-button"
					onclick={() => copy(location.origin + mediaUrl(selected!.key), 'url')}
					>{copied === 'url' ? 'Kopiert ✓' : 'Adresse kopieren'}</button
				>
				<a class="ed-button" href={mediaUrl(selected.key)} target="_blank" rel="noopener"
					>In neuem Tab öffnen</a
				>
			</div>

			<section>
				<h3 class="ed-label">Verwendet in</h3>
				{#if selected.usages.length === 0}
					<p class="mt-1 text-sm text-slate-500">Nirgends – das Bild kann gelöscht werden.</p>
				{:else}
					<ul class="mt-2 divide-y divide-slate-100 border border-slate-200">
						{#each selected.usages as usage (usage.kind + usage.href + usage.label)}
							<li class="flex items-center gap-3 px-3 py-2 text-sm">
								<span
									class="bg-slate-100 px-2 py-0.5 font-mono text-[11px] tracking-wider text-slate-500 uppercase"
									>{mediaKinds[usage.kind as keyof typeof mediaKinds] ?? usage.kind}</span
								>
								<a href={usage.href} class="min-w-0 flex-1 truncate hover:underline"
									>{usage.label}</a
								>
							</li>
						{/each}
					</ul>
				{/if}
			</section>

			<div class="flex items-center justify-between gap-3 border-t border-slate-100 pt-4">
				{#if selected.usages.length}
					<p class="text-xs text-slate-500">
						Verwendete Bilder lassen sich nicht löschen. Entferne es erst an den genannten Stellen.
					</p>
				{:else}
					<span></span>
				{/if}
				<button
					class="ed-button text-rose-700"
					onclick={() => remove(selected!)}
					disabled={selected.usages.length > 0}>Löschen</button
				>
			</div>
		</div>
	{/if}
</dialog>
