<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { auth } from '$lib/auth.svelte';

	let studentsCount = $state(0);
	let pendingSubmissionsCount = $state(0);
	let feedbackEnabled = $state(false);
	let certificatesEnabled = $state(false);
	let loading = $state(true);
	let error = $state('');
	let pollInterval: any;

	async function fetchData() {
		try {
			const [studentsRes, submissionsRes, feedbackRes, certsRes] = await Promise.all([
				auth.apiFetch('/api/admin/students'),
				auth.apiFetch('/api/admin/submissions'),
				auth.apiFetch('/api/feedback/status'),
				auth.apiFetch('/api/admin/settings/certificates')
			]);

			if (studentsRes.ok) {
				const data = await studentsRes.json();
				studentsCount = data.length;
			}
			if (submissionsRes.ok) {
				const data = await submissionsRes.json();
				pendingSubmissionsCount = data.length;
			}
			if (feedbackRes.ok) {
				const data = await feedbackRes.json();
				feedbackEnabled = data.enabled;
			}
			if (certsRes.ok) {
				const data = await certsRes.json();
				certificatesEnabled = data.enabled;
			}
		} catch (e) {
			console.error('Fetch error', e);
			error = 'Fehler beim Laden der Dashboard-Daten';
		} finally {
			loading = false;
		}
	}

	async function toggleCertificates() {
		try {
			const res = await auth.apiFetch('/api/admin/settings/certificates/toggle', { method: 'PUT' });
			if (res.ok) {
				const data = await res.json();
				certificatesEnabled = data.enabled;
			}
		} catch {
			error = 'Fehler beim Umschalten der Zeugnisfunktion';
		}
	}

	onMount(() => {
		fetchData();
		pollInterval = setInterval(fetchData, 30000);
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
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
				</svg>
				<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase">Control Center</span>
			</div>
			<h1 class="font-sans text-6xl font-black tracking-tighter text-institutional-navy uppercase leading-none">
				Dashboard
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

	{#if error}
		<div class="p-6 border border-rose-200 bg-rose-50 text-rose-600 font-mono text-xs uppercase tracking-widest">
			{error}
		</div>
	{/if}

	<!-- Main Grid Layout -->
	<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
		
		<!-- Stats Card: Students -->
		<div class="bg-white border border-slate-200 p-10 shadow-sm relative overflow-hidden group">
			<div class="absolute top-0 right-0 p-4 opacity-5 group-hover:opacity-10 transition-opacity">
				<svg xmlns="http://www.w3.org/2000/svg" class="h-24 w-24" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
				</svg>
			</div>
			<span class="font-mono text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-4 block">Accounts</span>
			<div class="flex items-end gap-4 mb-8">
				<span class="font-sans text-6xl font-black text-institutional-navy leading-none">{studentsCount}</span>
				<span class="font-mono text-[10px] text-slate-400 pb-1">Teilnehmer</span>
			</div>
			<a href="/admin/students" class="inline-block bg-institutional-navy px-6 py-3 text-[10px] font-bold tracking-widest text-white uppercase hover:bg-institutional-gold transition-colors">Teilnehmerliste öffnen</a>
		</div>

		<!-- Stats Card: Submissions -->
		<div class="bg-white border border-slate-200 p-10 shadow-sm relative overflow-hidden group">
			<div class="absolute top-0 right-0 p-4 opacity-5 group-hover:opacity-10 transition-opacity">
				<svg xmlns="http://www.w3.org/2000/svg" class="h-24 w-24" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
				</svg>
			</div>
			<span class="font-mono text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-4 block">Prüfungszentrum</span>
			<div class="flex items-end gap-4 mb-8">
				<span class="font-sans text-6xl font-black {pendingSubmissionsCount > 0 ? 'text-institutional-gold' : 'text-institutional-navy'} leading-none">{pendingSubmissionsCount}</span>
				<span class="font-mono text-[10px] text-slate-400 pb-1">Offen</span>
			</div>
			<a href="/admin/submissions" class="inline-block border-2 border-institutional-navy px-6 py-3 text-[10px] font-bold tracking-widest text-institutional-navy uppercase hover:bg-slate-50 transition-colors">Abgaben prüfen</a>
		</div>

		<!-- Settings Card: Certificates -->
		<div class="bg-slate-900 p-10 shadow-xl relative overflow-hidden flex flex-col justify-between">
			<div>
				<span class="font-mono text-[10px] font-bold text-slate-500 uppercase tracking-widest mb-4 block">System-Status</span>
				<h3 class="text-white font-sans text-2xl font-black uppercase mb-2">Zeugnisfunktion</h3>
				<p class="text-slate-400 text-xs mb-8">Steuert die Verfügbarkeit der PDF-Zertifikate für alle Teilnehmer.</p>
			</div>
			
			<button 
				onclick={toggleCertificates}
				class="w-full py-4 font-mono text-[11px] font-black uppercase tracking-[3px] transition-all border-2
				{certificatesEnabled ? 'bg-teal-600 border-teal-600 text-white' : 'bg-transparent border-slate-700 text-slate-400 hover:border-slate-500'}"
			>
				{certificatesEnabled ? 'Sperren' : 'Freigeben'}
			</button>
		</div>

	</div>

	<!-- Navigation Grid -->
	<section class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
		<a href="/admin/modules" class="p-8 bg-white border border-slate-200 hover:border-institutional-navy transition-all group flex flex-col gap-4">
			<div class="text-slate-300 group-hover:text-institutional-navy transition-colors">
				<svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
				</svg>
			</div>
			<span class="font-sans text-[11px] font-black uppercase tracking-[3px] text-institutional-navy">Modul-Steuerung</span>
		</a>

		<a href="/admin/announcement" class="p-8 bg-white border border-slate-200 hover:border-institutional-gold transition-all group flex flex-col gap-4">
			<div class="text-slate-300 group-hover:text-institutional-gold transition-colors">
				<svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z" />
				</svg>
			</div>
			<span class="font-sans text-[11px] font-black uppercase tracking-[3px] text-institutional-navy">Live-Mitteilung</span>
		</a>

		<a href="/admin/feedback" class="p-8 bg-white border border-slate-200 hover:border-teal-600 transition-all group flex flex-col gap-4">
			<div class="text-slate-300 group-hover:text-teal-600 transition-colors">
				<svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
				</svg>
			</div>
			<span class="font-sans text-[11px] font-black uppercase tracking-[3px] text-institutional-navy">Feedback</span>
		</a>

		<a href="/projector" target="_blank" class="p-8 bg-white border border-slate-200 hover:border-institutional-navy transition-all group flex flex-col gap-4">
			<div class="text-slate-300 group-hover:text-slate-900 transition-colors">
				<svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
				</svg>
			</div>
			<span class="font-sans text-[11px] font-black uppercase tracking-[3px] text-institutional-navy">Beamer-Modus</span>
		</a>
	</section>
</div>
