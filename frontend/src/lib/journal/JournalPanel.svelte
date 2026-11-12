<script lang="ts">
	import { onDestroy } from 'svelte';
	import { api, type Schemas } from '$lib/api';
	import { clearDraft, loadDraft, saveDraft } from '$lib/drafts';

	type ModuleJournal = Schemas['ModuleJournal'];
	type Entry = Schemas['JournalEntryView'];

	/**
	 * „Mein Lerntagebuch“: private Notiz zum Modul oder – mit `taskSlug` – zur Aufgabe. Speichert
	 * automatisch kurz nach dem Tippen; ein lokaler Entwurf schützt vor kurzen Verbindungsabbrüchen.
	 */
	let { moduleSlug, taskSlug = null }: { moduleSlug: string; taskSlug?: string | null } = $props();

	let journal = $state<ModuleJournal | null>(null);
	let text = $state('');
	let shared = $state(false);
	let saved = $state('');
	let saveState = $state<'idle' | 'dirty' | 'saving' | 'saved' | 'error'>('idle');
	let error = $state('');
	let timer: ReturnType<typeof setTimeout> | undefined;

	const draftKey = () => `journal:${moduleSlug}:${taskSlug ?? ''}`;

	function own(j: ModuleJournal): Entry | undefined {
		return j.entries.find((e) => (e.taskSlug ?? null) === (taskSlug ?? null));
	}

	async function load() {
		try {
			journal = await api<ModuleJournal>(`/api/journal/modules/${moduleSlug}`);
			const entry = own(journal);
			saved = entry?.text ?? '';
			shared = entry?.shared ?? false;
			const draft = loadDraft<string>(draftKey());
			text = draft !== null && draft !== saved ? draft : saved;
			saveState = text !== saved ? 'dirty' : 'idle';
			if (saveState === 'dirty') schedule();
		} catch (e) {
			error = (e as Error).message;
		}
	}

	function used() {
		if (!journal) return text.length;
		return (
			journal.entries
				.filter((e) => (e.taskSlug ?? null) !== (taskSlug ?? null))
				.reduce((sum, e) => sum + e.text.length, 0) + text.length
		);
	}

	function schedule() {
		saveState = 'dirty';
		saveDraft(draftKey(), text);
		clearTimeout(timer);
		timer = setTimeout(save, 1200);
	}

	async function save() {
		clearTimeout(timer);
		saveState = 'saving';
		error = '';
		try {
			await api(`/api/journal/modules/${moduleSlug}`, {
				method: 'PUT',
				body: { taskSlug, text, shared }
			});
			saved = text;
			clearDraft(draftKey());
			saveState = 'saved';
			if (journal) journal = await api<ModuleJournal>(`/api/journal/modules/${moduleSlug}`);
		} catch (e) {
			saveState = 'error';
			error = (e as Error).message;
		}
	}

	onDestroy(() => {
		if (saveState === 'dirty') save();
		clearTimeout(timer);
	});
</script>

<details
	class="border border-slate-200 bg-white shadow-sm"
	data-testid="journal-panel"
	ontoggle={(e) => {
		if (e.currentTarget.open && !journal) load();
	}}
>
	<summary
		class="cursor-pointer px-6 py-4 font-mono text-[10px] font-bold tracking-widest text-institutional-navy uppercase"
	>
		Mein Lerntagebuch{taskSlug ? ' zu dieser Aufgabe' : ''}
	</summary>
	<div class="space-y-3 px-6 pb-6 text-sm">
		<p class="text-xs text-slate-500">
			Nur für dich: Merksätze, Gedanken, offene Fragen. Die Lehrkraft sieht deine Notizen nicht –
			außer du teilst sie. Sie landen in deinem Mitnehm-Paket.
		</p>
		{#if journal}
			{#if journal.prompts.length > 0}
				<ul class="list-disc space-y-1 pl-5 text-xs text-slate-600">
					{#each journal.prompts as prompt (prompt)}<li>{prompt}</li>{/each}
				</ul>
			{/if}
			<textarea
				bind:value={text}
				oninput={schedule}
				onblur={() => saveState === 'dirty' && save()}
				rows="6"
				maxlength={journal.maxChars}
				placeholder="Markdown geht: # Überschrift, - Liste, **fett**"
				class="w-full border-slate-200 font-mono text-xs"
				aria-label="Notiz im Lerntagebuch"
				data-testid="journal-text"
			></textarea>
			<div class="flex flex-wrap items-center justify-between gap-3 text-xs">
				<label class="flex items-center gap-2"
					><input
						type="checkbox"
						bind:checked={shared}
						onchange={() => text.trim() && save()}
						data-testid="journal-shared"
					/> Mit der Lehrkraft teilen (z. B. als Frage)</label
				>
				<span
					class={saveState === 'error' ? 'text-rose-700' : 'text-slate-400'}
					role="status"
					data-testid="journal-state"
				>
					{#if saveState === 'saving'}Speichert …{:else if saveState === 'saved'}Gespeichert{:else if saveState === 'dirty'}Nicht
						gespeichert{:else if saveState === 'error'}{error}{/if}
					· {used()} / {journal.maxChars} Zeichen
				</span>
			</div>
		{:else if error}
			<p class="text-xs text-rose-700" role="alert">{error}</p>
		{:else}
			<p class="text-xs text-slate-400">Lädt …</p>
		{/if}
	</div>
</details>
