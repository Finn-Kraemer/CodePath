<script lang="ts">
	import Leaderboard from '$lib/Leaderboard.svelte';
</script>

<div class="mx-auto max-w-2xl">
	<div class="mb-8">
		<h1 class="font-sans text-3xl font-extrabold tracking-tight text-slate-900">Rangliste</h1>
		<p class="text-sm text-slate-500 italic">Sieh dir an, wer aktuell die meisten Punkte hat.</p>
	</div>

	<Leaderboard />
</div>
