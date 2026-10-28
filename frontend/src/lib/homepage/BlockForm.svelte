<script lang="ts">
	import MediaPicker from '$lib/components/MediaPicker.svelte';
	import { mediaUrl } from '$lib/media';
	import { BLOCK_TYPES, type Block } from './types';

	let { block = $bindable() }: { block: Block } = $props();

	// Die Felder kommen aus BLOCK_TYPES; hier wird generisch auf sie zugegriffen
	const data = $derived(block as unknown as Record<string, unknown>);
	const fields = $derived(BLOCK_TYPES[block.type].fields);

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
