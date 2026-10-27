<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type TaskStatistic } from '$lib/api';
	import { TYPE_LABELS } from '$lib/tasks/labels';

	let stats = $state<TaskStatistic[]>([]);
	let error = $state('');

	onMount(async () => {
		try {
			stats = await api<TaskStatistic[]>('/api/admin/statistics/tasks');
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
		</p>
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
					><th class="p-3 text-right">Ø Zeit</th></tr
				>
			</thead>
			<tbody>
				{#each stats as s (s.taskId)}
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
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
</div>
