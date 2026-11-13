<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type HelpState, type TaskDetail } from '$lib/api';
	import { realtime } from '$lib/realtime.svelte';
	import { HELP_CATEGORIES, currentDraft } from '$lib/help';

	/**
	 * Stiller Hilfe-Ruf „Ich hänge fest“: Nur die Lehrkraft sieht ihn – keine Anzeige für Mitschüler,
	 * kein Ton, nichts auf dem Beamer. Der aktuelle Stand der Antwort geht mit, wird aber nicht bewertet.
	 */
	let { task, moduleSlug, taskSlug }: { task: TaskDetail; moduleSlug: string; taskSlug: string } =
		$props();

	let help = $state<HelpState | null>(null);
	let open = $state(false);
	let category = $state('');
	let note = $state('');
	let busy = $state(false);
	let error = $state('');

	const request = $derived(help?.request ?? null);
	const forThisTask = $derived(
		!!request && request.moduleSlug === moduleSlug && request.taskSlug === taskSlug
	);

	async function load() {
		try {
			help = await api<HelpState>('/api/help');
		} catch {
			help = null;
		}
	}

	onMount(load);

	// Live: Lehrkraft kommt, erledigt, schreibt – oder die Warteschlange rückt auf
	$effect(() => {
		if (realtime.helpTrigger > 0) load();
	});

	async function send() {
		busy = true;
		error = '';
		try {
			help = await api<HelpState>('/api/help', {
				method: 'POST',
				body: {
					moduleSlug,
					taskSlug,
					category: category || null,
					note: note.trim() || null,
					...currentDraft(task)
				}
			});
			open = false;
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	async function cancel() {
		busy = true;
		error = '';
		try {
			help = await api<HelpState>('/api/help', { method: 'DELETE' });
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	const small =
		'flex min-h-10 w-full items-center justify-center gap-2 rounded-[var(--radius-ui-sm)] border px-3 text-sm font-semibold disabled:opacity-40';
</script>

<div class="ui-card p-5 text-sm" data-testid="help">
	{#if request && forThisTask}
		<div role="status" aria-live="polite">
			{#if request.status === 'CLAIMED'}
				<p class="font-bold text-institutional-navy">Deine Lehrkraft ist unterwegs.</p>
			{:else}
				<p class="font-bold text-institutional-navy">Deine Lehrkraft weiß Bescheid.</p>
				<p class="mt-1 text-xs text-slate-500" data-testid="help-position">
					{request.ahead === 0
						? 'Du bist als Nächstes dran.'
						: request.ahead === 1
							? '1 Person vor dir'
							: `${request.ahead} Personen vor dir`}
				</p>
			{/if}
			{#if request.teacherMessage}
				<p
					class="mt-3 border-l-4 border-institutional-gold bg-amber-50 p-3 text-sm"
					data-testid="help-message"
				>
					<span
						class="block font-mono text-[11px] font-black tracking-wider text-amber-700 uppercase"
						>Nachricht deiner Lehrkraft</span
					>{request.teacherMessage}
				</p>
			{/if}
		</div>
		<div class="mt-4 grid grid-cols-2 gap-2">
			<button
				type="button"
				class="{small} border-slate-300 text-slate-600 hover:border-institutional-navy"
				disabled={busy}
				onclick={send}
				title="Schickt deinen aktuellen Stand noch einmal mit">Stand senden</button
			>
			<button
				type="button"
				class="{small} border-institutional-navy text-institutional-navy hover:bg-slate-50"
				disabled={busy}
				onclick={cancel}>Hat sich erledigt</button
			>
		</div>
	{:else if open}
		<form
			onsubmit={(e) => {
				e.preventDefault();
				send();
			}}
		>
			<fieldset>
				<legend class="mb-2 font-bold text-institutional-navy">Was klappt nicht?</legend>
				<div class="space-y-1">
					{#each HELP_CATEGORIES as option (option.value)}
						<label class="flex items-center gap-2 text-xs">
							<input type="radio" name="help-category" value={option.value} bind:group={category} />
							{option.label}
						</label>
					{/each}
				</div>
			</fieldset>
			<label class="mt-3 block text-xs text-slate-500"
				>Notiz (optional)
				<textarea
					class="mt-1 h-20 w-full border border-slate-200 p-2 text-sm"
					maxlength="280"
					bind:value={note}
				></textarea>
			</label>
			<p class="mt-1 text-[11px] text-slate-400">
				Nur deine Lehrkraft sieht das – zusammen mit deinem aktuellen Stand. Es zählt nicht als
				Versuch.
			</p>
			<div class="mt-3 grid grid-cols-2 gap-2">
				<button
					type="button"
					class="{small} border-slate-300 text-slate-500"
					onclick={() => (open = false)}>Abbrechen</button
				>
				<button
					type="submit"
					class="{small} border-institutional-navy bg-institutional-navy text-white"
					disabled={busy}>Hilfe rufen</button
				>
			</div>
		</form>
	{:else}
		{#if request}
			<p class="mb-3 text-xs text-slate-500">
				Dein Hilfe-Ruf gilt für „{request.taskTitle}“. Ein neuer Ruf ersetzt ihn.
			</p>
		{/if}
		<button
			type="button"
			class="{small} border-slate-300 text-slate-600 hover:border-institutional-navy hover:text-institutional-navy"
			onclick={() => (open = true)}
		>
			✋ Ich hänge fest
		</button>
	{/if}
	{#if error}
		<p class="mt-3 text-xs text-rose-700" role="alert">{error}</p>
	{/if}
</div>
