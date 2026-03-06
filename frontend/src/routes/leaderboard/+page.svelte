<script lang="ts">
	import Leaderboard from '$lib/Leaderboard.svelte';
</script>

<div class="mx-auto max-w-2xl">
	<div class="mb-8">
		<h1 class="font-sans text-3xl font-black tracking-tight text-slate-900 uppercase">
			Rangliste
		</h1>
		<p class="text-sm italic text-slate-500">Sieh dir an, wer aktuell die meisten Punkte hat.</p>
	</div>

	<Leaderboard />
</div>
