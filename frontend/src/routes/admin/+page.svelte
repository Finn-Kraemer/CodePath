<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { auth } from '$lib/auth.svelte';

	interface Student {
		username: string;
		displayName: string;
		totalPoints: number;
		completedTasks: number;
	}

	let students = $state<Student[]>([]);
	let loading = $state(true);
	let error = $state('');
	let pollInterval: any;

	async function fetchStudents() {
		try {
			const res = await auth.apiFetch('/api/admin/students');
			if (res.ok) {
				const data = await res.json();
				students = data.sort((a: Student, b: Student) => b.totalPoints - a.totalPoints);
			} else {
				error = 'Fehler beim Laden der Schüler';
			}
		} catch {
			error = 'Netzwerkfehler beim Laden der Schüler';
		} finally {
			loading = false;
		}
	}

	onMount(() => {
		fetchStudents();
		pollInterval = setInterval(fetchStudents, 10000);
	});

	onDestroy(() => {
		if (pollInterval) clearInterval(pollInterval);
	});
</script>

<div class="flex flex-col gap-12">
	<header class="flex flex-col items-start justify-between gap-6 border-b border-slate-200 pb-8 md:flex-row md:items-center">
		<div>
			<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
				Administration
			</h1>
			<p class="mt-2 text-slate-500">Zentrale Verwaltung der Kursteilnehmer und Lerninhalte.</p>
		</div>
		<div class="flex flex-wrap gap-4">
			<a
				href="/admin/modules"
				class="border border-slate-200 bg-white px-6 py-3 text-[10px] font-bold tracking-widest text-institutional-navy uppercase transition-all hover:bg-slate-50 rounded-none"
				>Module Steuern</a
			>
			<a
				href="/admin/submissions"
				class="bg-institutional-navy px-6 py-3 text-[10px] font-bold tracking-widest text-white uppercase shadow-sm transition-all hover:opacity-90 rounded-none"
				>Abgaben Prüfen</a
			>
		</div>
	</header>

	{#if error}
		<div class="border border-red-200 bg-red-50 p-8 text-red-700 font-mono text-sm rounded-none">
			{error}
		</div>
	{/if}

	{#if loading && students.length === 0}
		<div class="flex justify-center py-24">
			<div class="h-12 w-12 border-4 border-slate-200 border-t-institutional-navy animate-spin"></div>
		</div>
	{:else}
		<div class="border border-slate-200 bg-white shadow-sm rounded-none overflow-x-auto">
			<table class="w-full border-collapse text-left">
				<thead>
					<tr class="border-b border-slate-200 bg-slate-50">
						<th class="px-8 py-5 font-mono text-[10px] font-bold tracking-[2px] text-slate-400 uppercase">Rang</th>
						<th class="px-8 py-5 font-mono text-[10px] font-bold tracking-[2px] text-slate-400 uppercase">Teilnehmer</th>
						<th class="px-8 py-5 font-mono text-[10px] font-bold tracking-[2px] text-slate-400 uppercase text-right">Punkte</th>
						<th class="px-8 py-5 font-mono text-[10px] font-bold tracking-[2px] text-slate-400 uppercase text-center">Aufgaben</th>
						<th class="px-8 py-5 font-mono text-[10px] font-bold tracking-[2px] text-slate-400 uppercase text-right">Verwaltung</th>
					</tr>
				</thead>
				<tbody class="divide-y divide-slate-100">
					{#each students as student, i (student.username)}
						<tr class="transition-colors hover:bg-slate-50">
							<td class="px-8 py-6 font-mono text-sm font-bold text-slate-400">#{i + 1}</td>
							<td class="px-8 py-6">
								<p class="font-sans text-sm font-bold text-institutional-navy">
									{student.displayName || student.username}
								</p>
								<p class="mt-1 font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase">
									@{student.username}
								</p>
							</td>
							<td class="px-8 py-6 text-right">
								<span class="font-mono text-sm font-black text-institutional-navy">{student.totalPoints}</span>
							</td>
							<td class="px-8 py-6 text-center">
								<span class="font-mono text-xs font-bold text-slate-600">{student.completedTasks}</span>
							</td>
							<td class="px-8 py-6 text-right">
								<a
									href={`/admin/students/${student.username}`}
									class="text-[10px] font-black tracking-widest text-institutional-gold uppercase hover:underline"
									>Details & Historie</a
								>
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
	{/if}
</div>
