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
	let pendingSubmissionsCount = $state(0);
	let feedbackEnabled = $state(false);
	let loading = $state(true);
	let error = $state('');
	let pollInterval: any;

	let announcement = $state('');
	let displayMode = $state('HEADER');
	let announcementLoading = $state(false);
	let announcementSuccess = $state(false);

	async function fetchData() {
		try {
			const [studentsRes, submissionsRes, feedbackRes, announcementRes] = await Promise.all([
				auth.apiFetch('/api/admin/students'),
				auth.apiFetch('/api/admin/submissions'),
				auth.apiFetch('/api/admin/feedback/status'),
				auth.apiFetch('/api/common/announcement')
			]);

			if (studentsRes.ok) {
				const data = await studentsRes.json();
				students = data.sort((a: Student, b: Student) => b.totalPoints - a.totalPoints);
			}
			if (submissionsRes.ok) {
				const data = await submissionsRes.json();
				pendingSubmissionsCount = data.length;
			}
			if (feedbackRes.ok) {
				const data = await feedbackRes.json();
				feedbackEnabled = data.enabled;
			}
			if (announcementRes.ok) {
				const data = await announcementRes.json();
				announcement = data.content || '';
				displayMode = data.displayMode || 'HEADER';
			}
		} catch (e) {
			console.error('Fetch error', e);
			error = 'Fehler beim Laden der Dashboard-Daten';
		} finally {
			loading = false;
		}
	}

	async function updateAnnouncement() {
		announcementLoading = true;
		announcementSuccess = false;
		try {
			const res = await auth.apiFetch('/api/admin/announcement', {
				method: 'PUT',
				body: JSON.stringify({ content: announcement, displayMode: displayMode })
			});
			if (res.ok) {
				announcementSuccess = true;
				setTimeout(() => (announcementSuccess = false), 3000);
			}
		} catch {
			error = 'Fehler beim Aktualisieren der Ankündigung';
		} finally {
			announcementLoading = false;
		}
	}

	onMount(() => {
		fetchData();
		pollInterval = setInterval(fetchData, 15000);
	});

	onDestroy(() => {
		if (pollInterval) clearInterval(pollInterval);
	});
</script>

<div class="flex flex-col gap-12 pb-24 max-w-7xl mx-auto">
	<!-- Header Section -->
	<header class="flex flex-col md:flex-row md:items-end justify-between gap-6 border-b border-slate-200 pb-10">
		<div>
			<div class="flex items-center gap-3 text-institutional-navy/40 mb-2">
				<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
				</svg>
				<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase">Control Center</span>
			</div>
			<h1 class="font-sans text-6xl font-black tracking-tighter text-institutional-navy uppercase leading-none">
				Admin
			</h1>
		</div>
		
		<div class="flex gap-4">
			<div class="flex flex-col items-end">
				<span class="font-mono text-[9px] font-bold text-slate-400 uppercase tracking-widest">Feedback</span>
				<span class="font-sans text-xs font-black {feedbackEnabled ? 'text-teal-600' : 'text-rose-600'} uppercase">
					{feedbackEnabled ? 'Aktiviert' : 'Deaktiviert'}
				</span>
			</div>
			<div class="h-10 w-[1px] bg-slate-200"></div>
			<div class="flex flex-col items-end">
				<span class="font-mono text-[9px] font-bold text-slate-400 uppercase tracking-widest">Integrität</span>
				<span class="font-sans text-xs font-black text-institutional-navy uppercase">Stabil</span>
			</div>
		</div>
	</header>

	<!-- Main Grid Layout -->
	<div class="grid grid-cols-1 lg:grid-cols-12 gap-12">
		
		<!-- LEFT COLUMN: Main Controls -->
		<div class="lg:col-span-8 space-y-12">
			
			<!-- Participant Ranking -->
			<section>
				<div class="flex items-center justify-between mb-8">
					<h2 class="font-sans text-sm font-black tracking-[4px] text-institutional-navy uppercase">Teilnehmer-Ranking</h2>
					<div class="h-[1px] flex-1 mx-6 bg-slate-100"></div>
					<span class="font-mono text-[9px] text-slate-400 uppercase tracking-widest">Top {students.length}</span>
				</div>

				{#if loading && students.length === 0}
					<div class="py-24 border border-dashed border-slate-200 text-center">
						<div class="h-8 w-8 border-2 border-slate-200 border-t-institutional-navy animate-spin mx-auto mb-4"></div>
						<p class="font-mono text-[10px] text-slate-400 uppercase tracking-widest">Synchronisiere Daten...</p>
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

		<!-- RIGHT COLUMN: Sidebar Stats & Tools -->
		<div class="lg:col-span-4 space-y-12">
			
			<!-- Quick Navigation Cards -->
			<section class="space-y-4">
				<h2 class="font-sans text-[10px] font-black tracking-[4px] text-slate-400 uppercase">Navigation</h2>
				
				<div class="grid grid-cols-1 gap-3">
					<a href="/admin/submissions" class="flex items-center justify-between p-6 bg-white border border-slate-200 hover:border-institutional-gold transition-all group shadow-sm">
						<div class="flex items-center gap-4">
							<div class="p-3 bg-institutional-gold/5 text-institutional-gold group-hover:bg-institutional-gold group-hover:text-white transition-colors">
								<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
									<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
								</svg>
							</div>
							<div class="flex flex-col">
								<span class="font-sans text-[11px] font-black uppercase tracking-widest text-institutional-navy">Prüfungszentrum</span>
								<span class="font-mono text-[9px] text-slate-400 uppercase">{pendingSubmissionsCount} ausstehende Abgaben</span>
							</div>
						</div>
						{#if pendingSubmissionsCount > 0}
							<div class="h-2 w-2 rounded-full bg-institutional-gold animate-ping"></div>
						{/if}
					</a>

					<a href="/admin/modules" class="flex items-center justify-between p-6 bg-white border border-slate-200 hover:border-institutional-navy transition-all group shadow-sm">
						<div class="flex items-center gap-4">
							<div class="p-3 bg-institutional-navy/5 text-institutional-navy group-hover:bg-institutional-navy group-hover:text-white transition-colors">
								<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
									<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
								</svg>
							</div>
							<div class="flex flex-col">
								<span class="font-sans text-[11px] font-black uppercase tracking-widest text-institutional-navy">Modul-Steuerung</span>
								<span class="font-mono text-[9px] text-slate-400 uppercase">Kurse & Inhalte verwalten</span>
							</div>
						</div>
					</a>

					<a href="/admin/feedback" class="flex items-center justify-between p-6 bg-white border border-slate-200 hover:border-teal-600 transition-all group shadow-sm">
						<div class="flex items-center gap-4">
							<div class="p-3 bg-teal-600/5 text-teal-600 group-hover:bg-teal-600 group-hover:text-white transition-colors">
								<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
									<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z" />
								</svg>
							</div>
							<div class="flex flex-col">
								<span class="font-sans text-[11px] font-black uppercase tracking-widest text-institutional-navy">Feedback</span>
								<span class="font-mono text-[9px] text-slate-400 uppercase">Qualitätsmanagement</span>
							</div>
						</div>
					</a>

					<a href="/projector" target="_blank" class="flex items-center justify-between p-6 bg-white border border-slate-200 hover:border-institutional-navy transition-all group shadow-sm">
						<div class="flex items-center gap-4">
							<div class="p-3 bg-slate-900/5 text-slate-900 group-hover:bg-slate-900 group-hover:text-white transition-colors">
								<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
									<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 4v16M17 4v16M3 8h4m10 0h4M3 12h18M3 16h4m10 0h4M4 20h16a1 1 0 001-1V5a1 1 0 00-1-1H4a1 1 0 00-1 1v14a1 1 0 001 1z" />
								</svg>
							</div>
							<div class="flex flex-col">
								<span class="font-sans text-[11px] font-black uppercase tracking-widest text-institutional-navy">Beamer-Modus</span>
								<span class="font-mono text-[9px] text-slate-400 uppercase">Live-Präsentation öffnen</span>
							</div>
						</div>
						<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-slate-300 group-hover:text-institutional-navy transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor">
							<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
						</svg>
					</a>
				</div>
			</section>

			<!-- Live Messaging -->
			<section class="border-t border-slate-200 pt-10">
				<h2 class="mb-6 font-sans text-[10px] font-black tracking-[4px] text-institutional-navy uppercase">Live-Mitteilung</h2>
				
				<div class="bg-slate-50 p-8 space-y-6 shadow-inner border border-slate-100">
					<textarea
						bind:value={announcement}
						placeholder="Wichtige System-Meldung verfassen..."
						class="w-full border border-slate-200 bg-white p-4 font-sans text-xs text-slate-700 focus:border-institutional-navy focus:outline-none rounded-none transition-all shadow-sm h-32 resize-none"
					></textarea>
					
					<div class="space-y-3">
						<p class="font-mono text-[8px] font-bold tracking-widest text-slate-400 uppercase">Anzeigeort wählen:</p>
						<div class="grid grid-cols-3 gap-1">
							{#each ['HEADER', 'INFO_PAGE', 'BOTH'] as mode}
								<button
									onclick={() => displayMode = mode}
									class="py-2 font-mono text-[8px] font-bold tracking-widest uppercase transition-all border
									{displayMode === mode ? 'bg-institutional-navy text-white border-institutional-navy' : 'bg-white text-slate-400 border-slate-200 hover:border-slate-300'}"
								>
									{mode.replace('_', ' ')}
								</button>
							{/each}
						</div>
					</div>
					
					<button
						onclick={updateAnnouncement}
						disabled={announcementLoading}
						class="w-full bg-institutional-navy py-4 font-sans text-[10px] font-bold tracking-[3px] text-white uppercase transition-all hover:opacity-95 disabled:opacity-50 rounded-none shadow-md group"
					>
						<span class="flex items-center justify-center gap-2">
							{announcementLoading ? 'Übertrage...' : 'Live Veröffentlichen'}
							{#if !announcementLoading}
								<svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3 group-hover:translate-x-1 transition-transform" fill="none" viewBox="0 0 24 24" stroke="currentColor">
									<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3" />
								</svg>
							{/if}
						</span>
					</button>
					
					{#if announcementSuccess}
						<p class="text-center text-[8px] font-bold text-teal-600 uppercase tracking-widest animate-in fade-in duration-300">Signal erfolgreich gesendet!</p>
					{/if}
				</div>
			</section>
		</div>
	</div>
</div>

<style>
</style>
