<script lang="ts">
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';

	let announcement = $state('');
	let displayMode = $state('HEADER');
	let loading = $state(false);
	let success = $state(false);
	let error = $state('');
	let hasFetched = false;

	async function fetchData() {
		if (hasFetched) return;
		try {
			const res = await auth.apiFetch('/api/common/announcement');
			if (res.ok) {
				const data = await res.json();
				if (!announcement) {
					announcement = data.content || '';
				}
				displayMode = data.displayMode || 'HEADER';
				hasFetched = true;
			}
		} catch {
			error = 'Fehler beim Laden der aktuellen Ankündigung';
		}
	}

	async function updateAnnouncement() {
		loading = true;
		success = false;
		error = '';
		try {
			const res = await auth.apiFetch('/api/admin/announcement', {
				method: 'PUT',
				body: JSON.stringify({ content: announcement, displayMode: displayMode })
			});
			if (res.ok) {
				success = true;
				setTimeout(() => (success = false), 3000);
			} else {
				error = 'Fehler beim Aktualisieren';
			}
		} catch {
			error = 'Netzwerkfehler beim Aktualisieren';
		} finally {
			loading = false;
		}
	}

	onMount(fetchData);

	const modes = [
		{
			value: 'HEADER',
			label: 'Oben auf jeder Seite',
			hint: 'Banner unter der Menüleiste, sofort für alle sichtbar'
		},
		{ value: 'INFO_PAGE', label: 'Nur auf der Info-Seite', hint: 'Unaufdringlich, zum Nachlesen' },
		{ value: 'BOTH', label: 'Überall', hint: 'Banner und Info-Seite' }
	];

	const templates = [
		'In 10 Minuten ist Pause – speichert bitte vorher eure Aufgaben.',
		'Bitte jetzt abgeben.',
		'Gleich startet die Quiz-Runde!',
		'Alle bitte einmal zum Beamer schauen.'
	];
</script>

<svelte:head><title>Live-Mitteilung | CodePath</title></svelte:head>

<div class="flex flex-col gap-6 pb-12">
	<header>
		<p class="ui-eyebrow">Unterricht</p>
		<h1 class="ui-title mt-1">Live-Mitteilung</h1>
		<p class="mt-1.5 text-[15px] text-slate-600">
			Erscheint sofort bei allen angemeldeten Schülerinnen und Schülern.
		</p>
	</header>

	<div class="grid grid-cols-1 gap-6 xl:grid-cols-2">
		<section class="ui-card flex flex-col gap-5 p-6" aria-label="Mitteilung verfassen">
			<div>
				<label class="ed-label" for="announcement-text">Nachricht</label>
				<textarea
					id="announcement-text"
					class="ed-input min-h-36"
					rows="5"
					maxlength="500"
					bind:value={announcement}
					placeholder="z. B. In 10 Minuten ist Pause – speichert bitte vorher eure Aufgaben."
				></textarea>
				<p class="mt-1 text-right font-mono text-xs text-slate-500">{announcement.length} / 500</p>
			</div>

			<div>
				<p class="ed-label mb-2">Vorlagen</p>
				<div class="flex flex-wrap gap-2">
					{#each templates as t (t)}
						<button
							type="button"
							class="rounded-full border border-slate-300 bg-white px-3 py-1.5 text-[13px] text-slate-700 hover:bg-slate-50"
							onclick={() => (announcement = t)}>{t}</button
						>
					{/each}
				</div>
			</div>

			<fieldset>
				<legend class="ed-label mb-2">Wo anzeigen?</legend>
				<div class="flex flex-col gap-2">
					{#each modes as m (m.value)}
						<label
							class="flex cursor-pointer items-start gap-3 rounded-[var(--radius-ui-sm)] border p-3.5 {displayMode ===
							m.value
								? 'border-institutional-navy ring-1 ring-institutional-navy'
								: 'border-slate-200 hover:border-slate-300'}"
						>
							<input
								type="radio"
								name="display-mode"
								value={m.value}
								bind:group={displayMode}
								class="mt-0.5 text-institutional-navy"
							/>
							<span>
								<span class="block text-[15px] font-semibold">{m.label}</span>
								<span class="block text-[13px] text-slate-500">{m.hint}</span>
							</span>
						</label>
					{/each}
				</div>
			</fieldset>

			{#if error}
				<p class="rounded-[var(--radius-ui-sm)] bg-rose-50 p-3 text-sm text-rose-700" role="alert">
					{error}
				</p>
			{/if}
			{#if success}
				<p
					class="rounded-[var(--radius-ui-sm)] bg-emerald-50 p-3 text-sm text-emerald-800"
					role="status"
				>
					Mitteilung ist live.
				</p>
			{/if}

			<div class="flex flex-wrap gap-2 border-t border-slate-100 pt-4">
				<button
					class="ui-btn ui-btn-primary flex-1"
					onclick={updateAnnouncement}
					disabled={loading}
				>
					{loading ? 'Wird gesendet …' : 'Live veröffentlichen'}
				</button>
				<button
					class="ui-btn"
					onclick={() => {
						announcement = '';
						updateAnnouncement();
					}}
					disabled={loading}>Mitteilung entfernen</button
				>
			</div>
		</section>

		<section class="flex flex-col gap-3" aria-label="Vorschau">
			<p class="ui-eyebrow">So sehen es Schülerinnen und Schüler</p>
			<div class="ui-card overflow-hidden shadow-sm">
				<div class="flex h-12 items-center gap-3 border-b border-slate-200 px-4">
					<span class="h-6 w-6 rounded bg-institutional-navy"></span>
					<span class="text-sm font-extrabold">CodePath</span>
					<span class="flex-1"></span>
					<span class="h-2 w-14 rounded-full bg-slate-200"></span>
					<span class="h-2 w-10 rounded-full bg-slate-200"></span>
				</div>
				{#if displayMode !== 'INFO_PAGE' && announcement.trim()}
					<div class="flex items-start gap-3 border-b border-amber-200 bg-amber-50 px-4 py-3">
						<span
							class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-institutional-gold text-xs text-white"
							aria-hidden="true">!</span
						>
						<p class="min-w-0 flex-1 text-sm text-amber-950">
							<span class="block font-semibold">Mitteilung der Lehrkraft</span>
							<span class="whitespace-pre-line">{announcement}</span>
						</p>
					</div>
				{/if}
				<div class="flex flex-col gap-2.5 p-4">
					<span class="h-3.5 w-2/5 rounded-full bg-slate-200"></span>
					<span class="h-2 w-4/5 rounded-full bg-slate-100"></span>
					<span class="h-2 w-3/5 rounded-full bg-slate-100"></span>
					<div class="mt-2 grid grid-cols-3 gap-2">
						<span class="h-16 rounded border border-slate-200"></span>
						<span class="h-16 rounded border border-slate-200"></span>
						<span class="h-16 rounded border border-slate-200"></span>
					</div>
				</div>
			</div>
			{#if displayMode !== 'HEADER'}
				<p class="rounded-[var(--radius-ui-sm)] bg-sky-50 p-3 text-[13px] text-sky-900">
					Die Nachricht steht außerdem oben auf der Info-Seite.
				</p>
			{/if}
		</section>
	</div>
</div>
