<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type HelpStatistic, type TaskStatistic } from '$lib/api';
	import { TYPE_LABELS } from '$lib/tasks/labels';

	let stats = $state<TaskStatistic[]>([]);
	let error = $state('');
	/** Hilfe-Rufe „Ich hänge fest“ je Aufgabe */
	let help = $state<Record<number, HelpStatistic>>({});
	let sortByHelp = $state(false);

	const rows = $derived(
		sortByHelp
			? [...stats].sort((a, b) => (help[b.taskId]?.calls ?? 0) - (help[a.taskId]?.calls ?? 0))
			: stats
	);

	onMount(async () => {
		try {
			const [tasks, calls] = await Promise.all([
				api<TaskStatistic[]>('/api/admin/statistics/tasks'),
				api<HelpStatistic[]>('/api/admin/help/statistics').catch(() => [] as HelpStatistic[])
			]);
			stats = tasks;
			help = Object.fromEntries(calls.map((c) => [c.taskId, c]));
		} catch (e) {
			error = (e as Error).message;
		}
	});

	function rate(s: TaskStatistic) {
		return s.students ? Math.round((s.solved / s.students) * 100) : 0;
	}

	function minutes(seconds: number) {
		return seconds ? `${Math.round(seconds / 60)} min` : '–';
	}
</script>

<svelte:head><title>Lernanalyse | CodePath</title></svelte:head>

<div class="flex flex-col gap-8 pb-24">
	<header class="border-b border-slate-200 pb-8">
		<a
			href="/admin"
			class="mb-6 inline-block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
			>← Zurück zur Administration</a
		>
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Lernanalyse
		</h1>
		<p class="mt-4 text-slate-500">
			Aufgaben mit vielen Fehlversuchen oben – sie sind vielleicht zu schwer oder missverständlich.
			Die Spalte <em>Hilfe-Rufe</em> zählt, wie oft dort „Ich hänge fest“ geklickt wurde (bis zum Kursende).
		</p>
		<label class="mt-4 flex items-center gap-2 text-sm text-slate-600">
			<input type="checkbox" bind:checked={sortByHelp} /> Nach Hilfe-Rufen sortieren
		</label>
	</header>
	{#if error}<div class="border border-red-200 bg-red-50 p-4 text-sm text-red-700">
			{error}
		</div>{/if}
	<div class="overflow-x-auto">
		<table class="w-full border-collapse text-sm">
			<thead
				class="bg-slate-50 text-left font-mono text-[10px] tracking-widest text-slate-500 uppercase"
			>
				<tr
					><th class="p-3">Modul</th><th class="p-3">Aufgabe</th><th class="p-3">Typ</th><th
						class="p-3 text-right">Schüler</th
					><th class="p-3 text-right">Versuche</th><th class="p-3 text-right">Fehlversuche</th><th
						class="p-3 text-right">Gelöst</th
					><th class="p-3 text-right">Ø Zeit</th><th class="p-3 text-right">Hilfe-Rufe</th></tr
				>
			</thead>
			<tbody>
				{#each rows as s (s.taskId)}
					<tr class="border-b border-slate-100">
						<td class="p-3 text-slate-500">{s.moduleTitle}</td>
						<td class="p-3 font-medium">{s.title}</td>
						<td class="p-3 text-slate-500">{TYPE_LABELS[s.type] ?? s.type}</td>
						<td class="p-3 text-right font-mono">{s.students}</td>
						<td class="p-3 text-right font-mono">{s.attempts}</td>
						<td
							class="p-3 text-right font-mono {s.failedAttempts > s.students * 2
								? 'font-bold text-rose-600'
								: ''}">{s.failedAttempts}</td
						>
						<td class="p-3 text-right font-mono">{rate(s)} %</td>
						<td class="p-3 text-right font-mono">{minutes(s.averageSeconds)}</td>
						<td
							class="p-3 text-right font-mono {(help[s.taskId]?.calls ?? 0) >= 3
								? 'font-bold text-amber-700'
								: ''}"
							title={help[s.taskId]
								? `${help[s.taskId].selfSolved} davon selbst gelöst`
								: 'Keine Hilfe-Rufe'}
							data-testid="help-calls">{help[s.taskId]?.calls ?? 0}</td
						>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
</div>
