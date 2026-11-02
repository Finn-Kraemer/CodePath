<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type QuizState, type QuizView } from '$lib/api';
	import { realtime } from '$lib/realtime.svelte';
	import Markdown from '$lib/Markdown.svelte';

	/** Mitspielen bei der Quiz-Runde: Antwort antippen, bis zur Auflösung änderbar. Keine Punkte. */
	let quiz = $state<QuizView | null>(null);
	let loaded = $state(false);
	let chosen = $state<string[]>([]);
	let error = $state('');
	let lastKey = '';

	const COLORS = ['bg-rose-500', 'bg-sky-500', 'bg-amber-400', 'bg-emerald-500', 'bg-violet-500'];

	async function load() {
		try {
			quiz = (await api<QuizState>('/api/quiz/current')).quiz ?? null;
		} catch {
			quiz = null;
		}
		loaded = true;
		// Neue Frage: Auswahl zurücksetzen bzw. eigene Antwort übernehmen
		const key = quiz ? `${quiz.id}-${quiz.index}` : '';
		if (key !== lastKey) {
			chosen = quiz?.myAnswer ?? [];
			lastKey = key;
		}
	}

	onMount(load);

	$effect(() => {
		if (realtime.quizTrigger > 0) load();
	});

	async function send(selected: string[]) {
		if (!quiz) return;
		error = '';
		try {
			quiz =
				(
					await api<QuizState>('/api/quiz/current/answer', {
						method: 'POST',
						body: { index: quiz.index, selected }
					})
				).quiz ?? null;
		} catch (e) {
			error = (e as Error).message;
			await load();
		}
	}

	function pick(id: string) {
		if (!quiz || quiz.phase !== 'QUESTION') return;
		if (quiz.multiple) {
			chosen = chosen.includes(id) ? chosen.filter((c) => c !== id) : [...chosen, id];
		} else {
			chosen = [id];
			send(chosen);
		}
	}
</script>

<svelte:head><title>Quiz | CodePath</title></svelte:head>

<div class="mx-auto max-w-2xl" data-testid="quiz-player">
	{#if !loaded}
		<p class="text-center text-slate-500">Lädt …</p>
	{:else if !quiz}
		<div class="border border-slate-200 bg-white p-10 text-center">
			<p class="text-lg font-bold text-institutional-navy">Gerade läuft kein Quiz.</p>
			<p class="mt-2 text-sm text-slate-500">
				Sobald deine Lehrkraft eins startet, erscheint es hier von selbst.
			</p>
		</div>
	{:else}
		<p class="mb-4 font-mono text-xs font-bold tracking-widest text-slate-400 uppercase">
			{quiz.title}
			{#if quiz.phase !== 'LOBBY'}· Frage {quiz.index + 1} von {quiz.total}{/if}
			· {quiz.myScore} richtig
		</p>
		{#if quiz.phase === 'LOBBY'}
			<div class="border-2 border-institutional-navy bg-white p-10 text-center">
				<p class="text-2xl font-black text-institutional-navy uppercase">Du bist dabei!</p>
				<p class="mt-2 text-slate-500">Schau zum Beamer – gleich kommt die erste Frage.</p>
			</div>
		{:else}
			<div class="mb-6 border border-slate-200 bg-white p-6 text-lg font-bold">
				<Markdown source={quiz.question ?? ''} />
			</div>
			<div class="grid grid-cols-1 gap-3">
				{#each quiz.options ?? [] as option, i (option.id)}
					{@const mine = chosen.includes(option.id)}
					{@const right = quiz.phase === 'REVEAL' && quiz.correct?.includes(option.id)}
					<button
						type="button"
						onclick={() => pick(option.id)}
						disabled={quiz.phase !== 'QUESTION'}
						aria-pressed={mine}
						class="flex items-center gap-4 border-4 p-4 text-left text-lg font-bold transition-colors {mine
							? 'border-institutional-navy bg-slate-50'
							: 'border-slate-200 bg-white'} {right ? 'border-emerald-500!' : ''}"
					>
						<span
							class="flex h-10 w-10 shrink-0 items-center justify-center text-white {COLORS[
								i % COLORS.length
							]}">{option.label}</span
						>
						<span class="flex-1">{option.text}</span>
						{#if quiz.phase === 'REVEAL'}
							<span class="font-mono text-sm text-slate-500"
								>{quiz.distribution?.[option.id] ?? 0}</span
							>
						{/if}
						{#if mine}<span class="text-sm">(deine Wahl)</span>{/if}
					</button>
				{/each}
			</div>
			{#if quiz.multiple && quiz.phase === 'QUESTION'}
				<button
					type="button"
					class="mt-4 w-full bg-institutional-navy py-4 font-mono text-xs font-black tracking-widest text-white uppercase disabled:opacity-40"
					disabled={!chosen.length}
					onclick={() => send(chosen)}>Antwort senden</button
				>
			{/if}
			<p
				class="mt-6 text-center text-sm"
				role="status"
				aria-live="polite"
				data-testid="quiz-feedback"
			>
				{#if quiz.phase === 'QUESTION'}
					{quiz.myAnswer
						? 'Antwort gespeichert – du kannst sie bis zur Auflösung ändern.'
						: 'Tippe auf eine Antwort.'}
				{:else if quiz.myAnswerCorrect}
					<span class="font-bold text-emerald-700">Richtig! 🎉</span>
				{:else if quiz.myAnswer}
					<span class="font-bold text-rose-700">Leider nicht – die richtige ist markiert.</span>
				{:else}
					<span class="text-slate-500">Diesmal keine Antwort.</span>
				{/if}
			</p>
		{/if}
		{#if error}<p class="mt-4 text-sm text-rose-700" role="alert">{error}</p>{/if}
	{/if}
</div>
