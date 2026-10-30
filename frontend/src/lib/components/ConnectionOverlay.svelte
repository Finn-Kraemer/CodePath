<!--
	Ladebildschirm, wenn die Verbindung hakt: Netzwerk weg, Live-Verbindung zum Server
	abgebrochen, eine Anfrage oder ein Seitenwechsel hängt. Verschwindet von selbst,
	sobald alles wieder läuft.
-->
<script lang="ts">
	import { fade } from 'svelte/transition';
	import { navigating } from '$app/state';
	import { connection } from '$lib/connection.svelte';
	import { realtime } from '$lib/realtime.svelte';

	type Status = 'offline' | 'lost' | 'slow';

	/** Kurze Verzögerung, damit kurze Aussetzer nicht flackern. */
	const SHOW_DELAY_MS = 600;
	/** Ab wann ein Seitenwechsel als langsam gilt. */
	const SLOW_NAVIGATION_MS = 1500;

	const texts: Record<Status, { title: string; detail: string }> = {
		offline: {
			title: 'Keine Netzwerkverbindung',
			detail:
				'Prüfe WLAN oder Netzwerkkabel. CodePath macht automatisch weiter, sobald die Verbindung wieder steht.'
		},
		lost: {
			title: 'Verbindung zum Server unterbrochen',
			detail: 'CodePath versucht automatisch, sie wiederherzustellen.'
		},
		slow: {
			title: 'Einen Moment bitte …',
			detail: 'Die Verbindung ist gerade langsam.'
		}
	};

	let slowNavigation = $state(false);
	$effect(() => {
		if (!navigating.to) {
			slowNavigation = false;
			return;
		}
		const timer = setTimeout(() => (slowNavigation = true), SLOW_NAVIGATION_MS);
		return () => clearTimeout(timer);
	});

	const status = $derived<Status | null>(
		!connection.online
			? 'offline'
			: realtime.isLost
				? 'lost'
				: connection.slow || slowNavigation
					? 'slow'
					: null
	);

	let shown = $state<Status | null>(null);
	$effect(() => {
		const next = status;
		if (!next) {
			shown = null;
			return;
		}
		if (shown) {
			shown = next;
			return;
		}
		const timer = setTimeout(() => (shown = next), SHOW_DELAY_MS);
		return () => clearTimeout(timer);
	});
</script>

{#if shown}
	<div
		class="fixed inset-0 z-[900] flex items-center justify-center bg-institutional-navy/95 p-6 backdrop-blur-sm"
		role="alertdialog"
		aria-modal="true"
		aria-labelledby="connection-title"
		aria-describedby="connection-detail"
		transition:fade={{ duration: 250 }}
	>
		<div class="flex max-w-md flex-col items-center gap-6 text-center">
			<svg viewBox="0 0 48 48" class="h-28 w-28" aria-hidden="true">
				<path
					d="M33 35H14V14h14"
					fill="none"
					stroke="currentColor"
					stroke-width="4.5"
					class="text-white/20"
				/>
				<path
					d="M33 35H14V14h14"
					fill="none"
					stroke="currentColor"
					stroke-width="4.5"
					pathLength="1"
					class="runner text-white"
					class:paused={shown === 'offline'}
				/>
				<circle cx="33" cy="35" r="4" class="fill-white" />
				<rect x="28" y="10" width="8" height="8" class="goal fill-institutional-gold" />
			</svg>
			<div class="flex flex-col gap-3" aria-live="polite">
				<h2
					id="connection-title"
					class="text-2xl font-black tracking-tight text-white uppercase sm:text-3xl"
				>
					{texts[shown].title}
				</h2>
				<p id="connection-detail" class="text-sm leading-relaxed text-slate-300">
					{texts[shown].detail}
				</p>
			</div>
			{#if shown !== 'slow'}
				<button
					type="button"
					class="min-h-11 rounded-none border border-white/30 px-6 py-3 text-[10px] font-black tracking-[3px] text-white uppercase transition-colors hover:bg-white hover:text-institutional-navy"
					onclick={() => location.reload()}
				>
					Seite neu laden
				</button>
			{/if}
		</div>
	</div>
{/if}

<style>
	/* Ein Lichtpunkt läuft den Weg entlang, am Ziel leuchtet das Gold auf. */
	.runner {
		stroke-dasharray: 0.25 1.5;
		animation: run 1.6s cubic-bezier(0.45, 0, 0.55, 1) infinite;
	}
	.runner.paused {
		animation: none;
		stroke-dasharray: none;
		opacity: 0;
	}
	.goal {
		animation: arrive 1.6s ease-in-out infinite;
	}
	@keyframes run {
		from {
			stroke-dashoffset: 0.25;
		}
		to {
			stroke-dashoffset: -1;
		}
	}
	@keyframes arrive {
		0%,
		55% {
			opacity: 0.35;
		}
		80% {
			opacity: 1;
		}
		100% {
			opacity: 0.35;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.runner,
		.goal {
			animation: none;
		}
	}
</style>
