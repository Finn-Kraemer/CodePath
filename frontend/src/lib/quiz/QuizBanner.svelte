<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { api, type QuizState } from '$lib/api';
	import { realtime } from '$lib/realtime.svelte';

	/** Hinweis für Schüler, solange eine Quiz-Runde läuft. */
	let active = $state(false);
	let title = $state('');

	async function load() {
		try {
			const quiz = (await api<QuizState>('/api/quiz/current')).quiz;
			active = !!quiz;
			title = quiz?.title ?? '';
		} catch {
			active = false;
		}
	}

	onMount(load);

	$effect(() => {
		if (realtime.quizTrigger > 0) load();
	});
</script>

{#if active && page.url.pathname !== '/quiz'}
	<a
		href="/quiz"
		class="block bg-institutional-gold px-4 py-3 text-center font-mono text-xs font-black tracking-widest text-institutional-navy uppercase hover:brightness-95"
		data-testid="quiz-banner"
	>
		🎯 Quiz läuft{title ? `: ${title}` : ''} – jetzt mitmachen →
	</a>
{/if}
