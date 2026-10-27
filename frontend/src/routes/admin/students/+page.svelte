<script lang="ts">
	import { auth } from '$lib/auth.svelte';
	import CourseSelect from '$lib/components/CourseSelect.svelte';
	import { courseSelection } from '$lib/courses.svelte';

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
			const res = await auth.apiFetch('/api/admin/students' + courseSelection.query());
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
</script>

<div class="mx-auto flex max-w-7xl flex-col gap-10 pb-24">
	<header class="flex items-end justify-between border-b border-slate-200 pb-10">
		<div>
			<div class="mb-2 flex items-center gap-3 text-institutional-navy/40">
				<a href="/admin" class="transition-colors hover:text-institutional-gold">
					<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase"
						>Admin Dashboard</span
					>
				</a>
				<span class="text-slate-300">/</span>
				<span
					class="font-mono text-[10px] font-bold tracking-[4px] text-institutional-gold uppercase"
					>Teilnehmer</span
				>
			</div>
			<h1
				class="font-sans text-6xl leading-none font-black tracking-tighter text-institutional-navy uppercase"
			>
				Teilnehmer
			</h1>
		</div>

		<div class="flex flex-col items-end gap-3">
			<CourseSelect onchange={fetchData} />
			<div class="font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
				Gesamt: {students.length} Accounts
			</div>
		</div>
	</header>

	<section>
		{#if loading}
			<div class="border border-dashed border-slate-200 py-24 text-center">
				<div
					class="mx-auto mb-4 h-8 w-8 animate-spin border-2 border-slate-200 border-t-institutional-navy"
				></div>
				<p class="font-mono text-[10px] tracking-widest text-slate-400 uppercase">
					Lade Teilnehmerdaten...
				</p>
			</div>
		{:else if error}
			<div class="border border-rose-200 bg-rose-50 p-10 font-mono text-xs text-rose-600">
				{error}
			</div>
		{:else}
			<div class="overflow-hidden rounded-none border border-slate-200 bg-white shadow-sm">
				<table class="w-full text-left">
					<thead>
						<tr
							class="border-b border-slate-200 bg-slate-50 font-mono text-[9px] font-black tracking-widest text-slate-400 uppercase"
						>
							<th class="w-20 px-8 py-4">#</th>
							<th class="px-8 py-4">Teilnehmer</th>
							<th class="px-8 py-4 text-right">Aufgaben</th>
							<th class="px-8 py-4 text-right">Punkte</th>
							<th class="w-10 px-8 py-4"></th>
						</tr>
					</thead>
					<tbody class="divide-y divide-slate-100">
						{#each students as student, i (student.username)}
							<tr class="group cursor-default transition-all hover:bg-slate-50/80">
								<td class="px-8 py-5 font-mono text-xs font-bold text-slate-300">
									{(i + 1).toString().padStart(2, '0')}
								</td>
								<td class="px-8 py-5">
									<div class="flex flex-col">
										<span
											class="font-sans text-sm font-bold text-institutional-navy capitalize transition-colors group-hover:text-institutional-gold"
										>
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
									<a
										href={`/admin/students/${student.username}`}
										class="block translate-x-2 transform opacity-0 transition-all duration-200 group-hover:translate-x-0 group-hover:opacity-100"
									>
										<svg
											xmlns="http://www.w3.org/2000/svg"
											class="h-5 w-5 text-institutional-gold"
											viewBox="0 0 20 20"
											fill="currentColor"
										>
											<path
												fill-rule="evenodd"
												d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
												clip-rule="evenodd"
											/>
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
