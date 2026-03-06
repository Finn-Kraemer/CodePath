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

<div class="flex flex-col gap-10 pb-24">
	<!-- Header Section -->
	<header class="flex flex-col gap-4">
		<div class="flex items-center gap-3 text-institutional-navy/40">
			<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
				<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
				<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
			</svg>
			<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase">System-Administration</span>
		</div>
		<h1 class="font-sans text-5xl font-black tracking-tighter text-institutional-navy uppercase">
			Dashboard
		</h1>
	</header>

	<!-- Quick Stats Bar -->
	<div class="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-4">
		<div class="border border-slate-200 bg-white p-6 shadow-sm rounded-none">
			<p class="font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase mb-2">Kursteilnehmer</p>
			<div class="flex items-baseline gap-2">
				<span class="text-3xl font-black text-institutional-navy">{students.length}</span>
				<span class="text-[10px] font-bold text-slate-400 uppercase">Aktiv</span>
			</div>
		</div>
		<div class="border border-slate-200 bg-white p-6 shadow-sm rounded-none">
			<p class="font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase mb-2">Offene Prüfungen</p>
			<div class="flex items-baseline gap-2">
				<span class="text-3xl font-black {pendingSubmissionsCount > 0 ? 'text-institutional-gold' : 'text-slate-200'}">
					{pendingSubmissionsCount}
				</span>
				<a href="/admin/submissions" class="text-[10px] font-bold text-institutional-navy uppercase hover:underline">Prüfen →</a>
			</div>
		</div>
		<div class="border border-slate-200 bg-white p-6 shadow-sm rounded-none">
			<p class="font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase mb-2">Feedback-Status</p>
			<div class="flex items-center gap-3">
				<div class="h-2 w-2 rounded-full {feedbackEnabled ? 'bg-teal-500 animate-pulse' : 'bg-slate-300'}"></div>
				<span class="font-sans text-xs font-bold uppercase {feedbackEnabled ? 'text-teal-600' : 'text-slate-400'}">
					{feedbackEnabled ? 'Geöffnet' : 'Gesperrt'}
				</span>
			</div>
		</div>
		<div class="border border-slate-200 bg-institutional-navy p-6 shadow-sm rounded-none">
			<p class="font-mono text-[9px] font-bold tracking-widest text-white/40 uppercase mb-2">System-Integrität</p>
			<span class="font-sans text-xs font-bold text-white uppercase tracking-widest">Online / Stabil</span>
		</div>
	</div>

	<!-- Main Content Grid -->
	<div class="grid grid-cols-1 gap-10 lg:grid-cols-3">
		<!-- Left: Participants -->
		<div class="lg:col-span-2 space-y-8">
			<div class="flex items-center justify-between border-b border-slate-100 pb-4">
				<h2 class="font-sans text-xl font-bold tracking-widest text-institutional-navy uppercase">Teilnehmer-Ranking</h2>
				<span class="font-mono text-[9px] text-slate-400 uppercase">Letztes Update: {new Date().toLocaleTimeString()}</span>
			</div>
			
			{#if loading && students.length === 0}
				<div class="flex justify-center py-24 border border-dashed border-slate-200">
					<div class="h-10 w-10 border-2 border-slate-200 border-t-institutional-navy animate-spin"></div>
				</div>
			{:else}
				<div class="border border-slate-200 bg-white shadow-md rounded-none overflow-hidden">
					<table class="w-full border-collapse text-left text-sm">
						<thead>
							<tr class="bg-slate-50 border-b border-slate-200">
								<th class="px-6 py-4 font-mono text-[10px] font-bold text-slate-400 uppercase tracking-widest">Rang</th>
								<th class="px-6 py-4 font-mono text-[10px] font-bold text-slate-400 uppercase tracking-widest">Nutzer</th>
								<th class="px-6 py-4 font-mono text-[10px] font-bold text-slate-400 uppercase tracking-widest text-right">Punkte</th>
								<th class="px-6 py-4 font-mono text-[10px] font-bold text-slate-400 uppercase tracking-widest text-right">Aktion</th>
							</tr>
						</thead>
						<tbody class="divide-y divide-slate-100">
							{#each students as student, i (student.username)}
								<tr class="hover:bg-slate-50 transition-colors group">
									<td class="px-6 py-5 font-mono font-bold text-slate-300">#{i + 1}</td>
									<td class="px-6 py-5">
										<div class="flex flex-col">
											<span class="font-bold text-institutional-navy">{student.displayName || student.username}</span>
											<span class="text-[10px] font-mono text-slate-400">@{student.username}</span>
										</div>
									</td>
									<td class="px-6 py-5 text-right font-mono font-black text-institutional-navy">
										{student.totalPoints}
									</td>
									<td class="px-6 py-5 text-right">
										<a href={`/admin/students/${student.username}`} class="text-[9px] font-black uppercase tracking-widest text-institutional-gold opacity-0 group-hover:opacity-100 transition-opacity hover:underline">
											Akte öffnen
										</a>
									</td>
								</tr>
							{/each}
						</tbody>
					</table>
				</div>
			{/if}
		</div>

		<!-- Right: Controls & Announcements -->
		<div class="space-y-10">
			<!-- Quick Actions -->
			<section class="space-y-6">
				<h2 class="font-sans text-xs font-black tracking-[4px] text-slate-400 uppercase">Schnellzugriff</h2>
				<div class="grid grid-cols-1 gap-3">
					<a href="/admin/modules" class="flex items-center justify-between border border-slate-200 bg-white p-5 hover:border-institutional-navy transition-all group rounded-none shadow-sm">
						<span class="font-sans text-[10px] font-bold tracking-widest uppercase text-slate-600 group-hover:text-institutional-navy">Lernmodule steuern</span>
						<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-slate-300 group-hover:text-institutional-navy" fill="none" viewBox="0 0 24 24" stroke="currentColor">
							<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
						</svg>
					</a>
					<a href="/admin/submissions" class="flex items-center justify-between border border-slate-200 bg-white p-5 hover:border-institutional-gold transition-all group rounded-none shadow-sm">
						<span class="font-sans text-[10px] font-bold tracking-widest uppercase text-slate-600 group-hover:text-institutional-gold">Prüfungszentrum</span>
						{#if pendingSubmissionsCount > 0}
							<span class="bg-institutional-gold px-2 py-0.5 text-[9px] font-bold text-white">{pendingSubmissionsCount}</span>
						{:else}
							<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-slate-300 group-hover:text-institutional-gold" fill="none" viewBox="0 0 24 24" stroke="currentColor">
								<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
							</svg>
						{/if}
					</a>
					<a href="/admin/feedback" class="flex items-center justify-between border border-slate-200 bg-white p-5 hover:border-teal-600 transition-all group rounded-none shadow-sm">
						<span class="font-sans text-[10px] font-bold tracking-widest uppercase text-slate-600 group-hover:text-teal-600">Feedback-Zentrum</span>
						<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-slate-300 group-hover:text-teal-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
							<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
						</svg>
					</a>
				</div>
			</section>

			<!-- Live Editor -->
			<section class="border border-slate-200 bg-slate-50 p-8 rounded-none shadow-inner">
				<h2 class="mb-6 font-sans text-xs font-black tracking-[4px] text-institutional-navy uppercase">Kursmitteilung</h2>
				
				<div class="space-y-6">
					<textarea
						bind:value={announcement}
						placeholder="Wichtige Info für alle..."
						class="w-full border border-slate-200 bg-white p-4 font-sans text-xs text-slate-700 focus:border-institutional-navy focus:outline-none rounded-none transition-all shadow-sm"
						rows="4"
					></textarea>
					
					<div class="space-y-3">
						<p class="font-mono text-[8px] font-bold tracking-widest text-slate-400 uppercase">Anzeigeort:</p>
						<div class="flex flex-wrap gap-2">
							{#each ['HEADER', 'INFO_PAGE', 'BOTH'] as mode}
								<button
									onclick={() => displayMode = mode}
									class="px-3 py-1.5 font-mono text-[8px] font-bold tracking-widest uppercase transition-all border
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
						class="w-full bg-institutional-navy py-4 font-sans text-[9px] font-bold tracking-[3px] text-white uppercase transition-all hover:opacity-90 disabled:opacity-50 rounded-none shadow-md"
					>
						{announcementLoading ? 'Sende...' : 'Live Übertragen'}
					</button>
					
					{#if announcementSuccess}
						<p class="text-center text-[8px] font-bold text-teal-600 uppercase tracking-widest animate-fade-in">Erfolgreich veröffentlicht!</p>
					{/if}
				</div>
			</section>
		</div>
	</div>
</div>

<style>
	@keyframes fade-in {
		from { opacity: 0; transform: translateY(5px); }
		to { opacity: 1; transform: translateY(0); }
	}
	.animate-fade-in {
		animation: fade-in 0.3s ease-out forwards;
	}
</style>
