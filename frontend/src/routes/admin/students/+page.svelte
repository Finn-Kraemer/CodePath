<script lang="ts">
	import { onMount } from 'svelte';
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

	async function fetchData() {
		try {
			const res = await auth.apiFetch('/api/admin/students');
			if (res.ok) {
				const data = await res.json();
				students = data.sort((a: Student, b: Student) => b.totalPoints - a.totalPoints);
			}
		} catch (e) {
			error = 'Fehler beim Laden der Teilnehmerliste';
		} finally {
			loading = false;
		}
	}

	onMount(fetchData);
</script>

<div class="flex flex-col gap-10 pb-24 max-w-7xl mx-auto">
	<header class="flex items-end justify-between border-b border-slate-200 pb-10">
		<div>
			<div class="flex items-center gap-3 text-institutional-navy/40 mb-2">
				<a href="/admin" class="hover:text-institutional-gold transition-colors">
					<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase">Admin Dashboard</span>
				</a>
				<span class="text-slate-300">/</span>
				<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase text-institutional-gold">Teilnehmer</span>
			</div>
			<h1 class="font-sans text-6xl font-black tracking-tighter text-institutional-navy uppercase leading-none">
				Teilnehmer
			</h1>
		</div>
        
        <div class="font-mono text-[10px] font-bold text-slate-400 uppercase tracking-widest">
            Gesamt: {students.length} Accounts
        </div>
	</header>

	<section>
		{#if loading}
			<div class="py-24 border border-dashed border-slate-200 text-center">
				<div class="h-8 w-8 border-2 border-slate-200 border-t-institutional-navy animate-spin mx-auto mb-4"></div>
				<p class="font-mono text-[10px] text-slate-400 uppercase tracking-widest">Lade Teilnehmerdaten...</p>
			</div>
		{:else if error}
			<div class="p-10 border border-rose-200 bg-rose-50 text-rose-600 font-mono text-xs">
				{error}
			</div>
		{:else}
			<div class="bg-white border border-slate-200 shadow-sm rounded-none overflow-hidden">
				<table class="w-full text-left">
					<thead>
						<tr class="bg-slate-50 border-b border-slate-200 font-mono text-[9px] font-black text-slate-400 uppercase tracking-widest">
							<th class="px-8 py-4 w-20">#</th>
							<th class="px-8 py-4">Teilnehmer</th>
							<th class="px-8 py-4 text-right">Aufgaben</th>
							<th class="px-8 py-4 text-right">Punkte</th>
							<th class="px-8 py-4 w-10"></th>
						</tr>
					</thead>
					<tbody class="divide-y divide-slate-100">
						{#each students as student, i (student.username)}
							<tr class="group hover:bg-slate-50/80 transition-all cursor-default">
								<td class="px-8 py-5 font-mono text-xs font-bold text-slate-300">
									{(i + 1).toString().padStart(2, '0')}
								</td>
								<td class="px-8 py-5">
									<div class="flex flex-col">
										<span class="font-sans text-sm font-bold text-institutional-navy group-hover:text-institutional-gold transition-colors capitalize">
											{student.displayName || student.username}
										</span>
										<span class="font-mono text-[10px] text-slate-400">@{student.username}</span>
									</div>
								</td>
								<td class="px-8 py-5 text-right font-mono text-xs text-slate-500">
									{student.completedTasks}
								</td>
								<td class="px-8 py-5 text-right font-mono font-black text-institutional-navy">
									{student.totalPoints}
								</td>
								<td class="px-8 py-5 text-right">
									<a href={`/admin/students/${student.username}`} class="block transform translate-x-2 opacity-0 group-hover:translate-x-0 group-hover:opacity-100 transition-all duration-200">
										<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-institutional-gold" viewBox="0 0 20 20" fill="currentColor">
											<path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd" />
										</svg>
									</a>
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		{/if}
	</section>
</div>
