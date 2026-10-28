<script lang="ts">
	import { untrack } from 'svelte';
	import { api, type Schemas } from '$lib/api';

	type ModuleRow = Schemas['ModuleRow'];
	type ModuleFile = ModuleRow['module'];

	let { row, onsaved, oncancel }: { row: ModuleRow; onsaved: () => void; oncancel: () => void } =
		$props();

	// Bearbeitete Kopie beim Öffnen des Formulars; Links als Liste von Zeilen
	let form = $state<ModuleFile>(
		untrack(() => {
			const module = $state.snapshot(row.module);
			return { ...module, learnMore: module.learnMore ?? [] };
		})
	);
	let error = $state('');
	let busy = $state(false);

	function removeLink(index: number) {
		const links = [...(form.learnMore ?? [])];
		links.splice(index, 1);
		form.learnMore = links;
	}

	async function save(e: Event) {
		e.preventDefault();
		error = '';
		busy = true;
		try {
			const learnMore = (form.learnMore ?? []).filter((l) => l.title.trim() || l.url.trim());
			await api(`/api/admin/content/modules/${row.id}`, {
				method: 'PUT',
				body: { ...form, learnMore }
			});
			onsaved();
		} catch (err) {
			error = (err as Error).message;
		} finally {
			busy = false;
		}
	}
</script>

<form
	class="grid grid-cols-1 gap-3 border-b border-slate-100 bg-slate-50 p-5 md:grid-cols-4"
	onsubmit={save}
	aria-label="Modul {row.module.title} bearbeiten"
>
	<label class="ed-label md:col-span-2"
		>Titel<input class="ed-input" bind:value={form.title} required /></label
	>
	<label class="ed-label">Symbol<input class="ed-input" bind:value={form.icon} /></label>
	<label class="ed-label"
		>Reihenfolge<input class="ed-input" type="number" bind:value={form.sortOrder} /></label
	>
	<label class="ed-label md:col-span-4"
		>Beschreibung<input class="ed-input" bind:value={form.description} /></label
	>
	<label class="ed-label md:col-span-4"
		>Berufsfeld für den Berufe-Kompass
		<input
			class="ed-input"
			bind:value={form.career}
			maxlength="300"
			placeholder="z. B. Softwareentwickler/in · Fachinformatiker/in Anwendungsentwicklung"
		/></label
	>
	<fieldset class="flex flex-col gap-2 md:col-span-4">
		<legend class="ed-label">Links zum Weiterlernen (im Mitnehm-Paket)</legend>
		{#each form.learnMore ?? [] as link, i (i)}
			<div class="flex flex-wrap gap-2">
				<input
					class="ed-input mt-0 flex-1"
					placeholder="Titel"
					aria-label="Titel des Links {i + 1}"
					bind:value={link.title}
				/>
				<input
					class="ed-input mono mt-0 flex-[2]"
					type="url"
					placeholder="https://…"
					aria-label="Adresse des Links {i + 1}"
					bind:value={link.url}
				/>
				<button
					type="button"
					class="ed-button px-3 text-rose-700"
					aria-label="Link {i + 1} entfernen"
					onclick={() => removeLink(i)}>✕</button
				>
			</div>
		{/each}
		{#if (form.learnMore?.length ?? 0) < 10}
			<button
				type="button"
				class="ed-button self-start"
				onclick={() => (form.learnMore = [...(form.learnMore ?? []), { title: '', url: '' }])}
				>+ Link</button
			>
		{/if}
	</fieldset>
	{#if error}<p class="text-sm text-rose-700 md:col-span-4" role="alert">{error}</p>{/if}
	<div class="flex gap-2 md:col-span-4">
		<button class="ed-button bg-institutional-navy text-white" disabled={busy}
			>Modul speichern</button
		>
		<button type="button" class="ed-button" onclick={oncancel}>Abbrechen</button>
	</div>
</form>
