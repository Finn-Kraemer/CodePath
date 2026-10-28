<script lang="ts">
	import { api, ApiError, type MediaInfo } from '$lib/api';
	import { mediaUrl, uploadMedia } from '$lib/media';

	let {
		label = 'Bild wählen',
		onselect
	}: { label?: string; onselect: (media: MediaInfo) => void } = $props();

	let open = $state(false);
	let items = $state<MediaInfo[]>([]);
	let busy = $state(false);
	let error = $state('');

	async function toggle() {
		open = !open;
		error = '';
		if (open) {
			try {
				items = await api<MediaInfo[]>('/api/admin/media');
			} catch (e) {
				error = (e as Error).message;
			}
		}
	}

	async function upload(event: Event) {
		const input = event.currentTarget as HTMLInputElement;
		const file = input.files?.[0];
		if (!file) return;
		busy = true;
		error = '';
		try {
			const media = await uploadMedia(file);
			choose(media);
		} catch (e) {
			error = e instanceof ApiError ? e.message : 'Hochladen fehlgeschlagen.';
		} finally {
			busy = false;
			input.value = '';
		}
	}

	function choose(media: MediaInfo) {
		onselect(media);
		open = false;
	}
</script>

<div class="mt-2">
	<button type="button" class="ed-button" onclick={toggle} aria-expanded={open}>{label}</button>
	{#if open}
		<div class="mt-2 border border-slate-200 bg-slate-50 p-3">
			<label class="ed-label"
				>Neues Bild hochladen (PNG, JPEG, GIF, WebP – höchstens 2 MB)
				<input
					type="file"
					accept="image/png,image/jpeg,image/gif,image/webp"
					class="mt-1 block text-sm"
					onchange={upload}
					disabled={busy}
				/>
			</label>
			{#if busy}<p class="mt-2 text-xs text-slate-500">Wird hochgeladen …</p>{/if}
			{#if error}<p class="mt-2 text-xs text-rose-700" role="alert">{error}</p>{/if}
			{#if items.length}
				<p class="ed-label mt-3">Oder ein vorhandenes Bild</p>
				<div class="mt-2 grid max-h-64 grid-cols-3 gap-2 overflow-y-auto md:grid-cols-4">
					{#each items as item (item.key)}
						<button
							type="button"
							class="border border-slate-200 bg-white p-1 hover:border-slate-500"
							onclick={() => choose(item)}
							title={item.originalName ?? item.key}
						>
							<img
								src={mediaUrl(item.key)}
								alt={item.originalName ?? 'Bild'}
								class="h-20 w-full object-contain"
								loading="lazy"
							/>
							<span class="block truncate text-[10px] text-slate-500"
								>{item.originalName ?? item.key.slice(0, 12)}</span
							>
						</button>
					{/each}
				</div>
			{/if}
		</div>
	{/if}
</div>
