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

	let sessionsMessage = $state('');

	async function logoutAllStudents() {
		if (
			!confirm(
				'Alle Schülerinnen und Schüler abmelden? Ungespeicherte Eingaben gehen dabei verloren.'
			)
		)
			return;
		const res = await auth.apiFetch('/api/admin/sessions/logout-students', { method: 'POST' });
		if (res.ok) {
			const data = await res.json();
			sessionsMessage = `${data.sessionsEnded} Sitzung(en) beendet.`;
		} else {
			error = 'Abmelden fehlgeschlagen';
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

<div class="mx-auto flex max-w-7xl flex-col gap-12 pb-24">
	<!-- Header Section -->
	<header
		class="flex flex-col justify-between gap-6 border-b border-slate-200 pb-10 md:flex-row md:items-end"
	>
		<div>
			<div class="mb-2 flex items-center gap-3 text-institutional-navy/40">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-4 w-4"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
					/>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
					/>
				</svg>
				<span class="font-mono text-[10px] font-bold tracking-[4px] uppercase">Control Center</span>
			</div>
			<h1
				class="font-sans text-6xl leading-none font-black tracking-tighter text-institutional-navy uppercase"
			>
				Dashboard
			</h1>
		</div>

		<div class="flex gap-4">
			<div class="flex flex-col items-end">
				<span class="font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase"
					>Feedback</span
				>
				<span
					class="font-sans text-xs font-black {feedbackEnabled
						? 'text-teal-600'
						: 'text-rose-600'} uppercase"
				>
					{feedbackEnabled ? 'Aktiviert' : 'Deaktiviert'}
				</span>
			</div>
			<div class="h-10 w-[1px] bg-slate-200"></div>
			<div class="flex flex-col items-end">
				<span class="font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase"
					>Integrität</span
				>
				<span class="font-sans text-xs font-black text-institutional-navy uppercase">Stabil</span>
			</div>
		</div>
	</header>

	{#if error}
		<div
			class="border border-rose-200 bg-rose-50 p-6 font-mono text-xs tracking-widest text-rose-600 uppercase"
		>
			{error}
		</div>
	{/if}

	<!-- Main Grid Layout -->
	<div class="grid grid-cols-1 gap-8 md:grid-cols-2 lg:grid-cols-3">
		<!-- Stats Card: Students -->
		<div class="group relative overflow-hidden border border-slate-200 bg-white p-10 shadow-sm">
			<div class="absolute top-0 right-0 p-4 opacity-5 transition-opacity group-hover:opacity-10">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-24 w-24"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"
					/>
				</svg>
			</div>
			<span
				class="mb-4 block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
				>Accounts</span
			>
			<div class="mb-8 flex items-end gap-4">
				<span class="font-sans text-6xl leading-none font-black text-institutional-navy"
					>{studentsCount}</span
				>
				<span class="pb-1 font-mono text-[10px] text-slate-400">Teilnehmer</span>
			</div>
			<a
				href="/admin/students"
				class="inline-block bg-institutional-navy px-6 py-3 text-[10px] font-bold tracking-widest text-white uppercase transition-colors hover:bg-institutional-gold"
				>Teilnehmerliste öffnen</a
			>
		</div>

		<!-- Stats Card: Submissions -->
		<div class="group relative overflow-hidden border border-slate-200 bg-white p-10 shadow-sm">
			<div class="absolute top-0 right-0 p-4 opacity-5 transition-opacity group-hover:opacity-10">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-24 w-24"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4"
					/>
				</svg>
			</div>
			<span
				class="mb-4 block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
				>Prüfungszentrum</span
			>
			<div class="mb-8 flex items-end gap-4">
				<span
					class="font-sans text-6xl font-black {pendingSubmissionsCount > 0
						? 'text-institutional-gold'
						: 'text-institutional-navy'} leading-none">{pendingSubmissionsCount}</span
				>
				<span class="pb-1 font-mono text-[10px] text-slate-400">Offen</span>
			</div>
			<a
				href="/admin/submissions"
				class="inline-block border-2 border-institutional-navy px-6 py-3 text-[10px] font-bold tracking-widest text-institutional-navy uppercase transition-colors hover:bg-slate-50"
				>Abgaben prüfen</a
			>
		</div>

		<!-- Settings Card: Certificates -->
		<div class="relative flex flex-col justify-between overflow-hidden bg-slate-900 p-10 shadow-xl">
			<div>
				<span
					class="mb-4 block font-mono text-[10px] font-bold tracking-widest text-slate-500 uppercase"
					>System-Status</span
				>
				<h3 class="mb-2 font-sans text-2xl font-black text-white uppercase">Zeugnisfunktion</h3>
				<p class="mb-8 text-xs text-slate-400">
					Steuert die Verfügbarkeit der PDF-Zertifikate für alle Teilnehmer.
				</p>
			</div>

			<button
				onclick={toggleCertificates}
				class="w-full border-2 py-4 font-mono text-[11px] font-black tracking-[3px] uppercase transition-all
				{certificatesEnabled
					? 'border-teal-600 bg-teal-600 text-white'
					: 'border-slate-700 bg-transparent text-slate-400 hover:border-slate-500'}"
			>
				{certificatesEnabled ? 'Sperren' : 'Freigeben'}
			</button>
			<button
				onclick={logoutAllStudents}
				class="mt-4 w-full border-2 border-slate-700 py-3 font-mono text-[10px] font-black tracking-[3px] text-slate-400 uppercase transition-all hover:border-rose-500 hover:text-rose-400"
			>
				Alle Schüler abmelden
			</button>
			{#if sessionsMessage}
				<p class="mt-3 font-mono text-[10px] text-slate-400">{sessionsMessage}</p>
			{/if}
		</div>
	</div>

	<!-- Navigation Grid -->
	<section class="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-4">
		<a
			href="/admin/courses"
			class="group flex flex-col gap-4 border border-slate-200 bg-white p-8 transition-all hover:border-institutional-navy"
		>
			<div class="text-slate-300 transition-colors group-hover:text-institutional-navy">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-8 w-8"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z"
					/>
				</svg>
			</div>
			<span
				class="font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase"
				>Kurse &amp; Konten</span
			>
		</a>

		<a
			href="/admin/content"
			class="group flex flex-col gap-4 border border-slate-200 bg-white p-8 transition-all hover:border-institutional-navy"
		>
			<div class="text-slate-300 transition-colors group-hover:text-institutional-navy">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-8 w-8"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"
					/>
				</svg>
			</div>
			<span
				class="font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase"
				>Inhalte</span
			>
		</a>

		<a
			href="/admin/homepage"
			class="group flex flex-col gap-4 border border-slate-200 bg-white p-8 transition-all hover:border-institutional-navy"
		>
			<div class="text-slate-300 transition-colors group-hover:text-institutional-navy">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-8 w-8"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"
					/>
				</svg>
			</div>
			<span
				class="font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase"
				>Startseite</span
			>
		</a>

		<a
			href="/admin/pages"
			class="group flex flex-col gap-4 border border-slate-200 bg-white p-8 transition-all hover:border-institutional-navy"
		>
			<div class="text-slate-300 transition-colors group-hover:text-institutional-navy">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-8 w-8"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
					/>
				</svg>
			</div>
			<span
				class="font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase"
				>Seiten</span
			>
		</a>

		<a
			href="/admin/knowledge"
			class="group flex flex-col gap-4 border border-slate-200 bg-white p-8 transition-all hover:border-institutional-navy"
		>
			<div class="text-slate-300 transition-colors group-hover:text-institutional-navy">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-8 w-8"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"
					/>
				</svg>
			</div>
			<span
				class="font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase"
				>Wissensdatenbank</span
			>
		</a>

		<a
			href="/admin/learning-paths"
			class="group flex flex-col gap-4 border border-slate-200 bg-white p-8 transition-all hover:border-institutional-navy"
		>
			<div class="text-slate-300 transition-colors group-hover:text-institutional-navy">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-8 w-8"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7"
					/>
				</svg>
			</div>
			<span
				class="font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase"
				>Lernpfade</span
			>
		</a>

		<a
			href="/admin/transfer"
			class="group flex flex-col gap-4 border border-slate-200 bg-white p-8 transition-all hover:border-institutional-navy"
		>
			<div class="text-slate-300 transition-colors group-hover:text-institutional-navy">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-8 w-8"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"
					/>
				</svg>
			</div>
			<span
				class="font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase"
				>Import &amp; Export</span
			>
		</a>

		<a
			href="/admin/statistics"
			class="group flex flex-col gap-4 border border-slate-200 bg-white p-8 transition-all hover:border-institutional-navy"
		>
			<div class="text-slate-300 transition-colors group-hover:text-institutional-navy">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-8 w-8"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
					/>
				</svg>
			</div>
			<span
				class="font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase"
				>Lernanalyse</span
			>
		</a>

		<a
			href="/admin/modules"
			class="group flex flex-col gap-4 border border-slate-200 bg-white p-8 transition-all hover:border-institutional-navy"
		>
			<div class="text-slate-300 transition-colors group-hover:text-institutional-navy">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-8 w-8"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"
					/>
				</svg>
			</div>
			<span
				class="font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase"
				>Modul-Steuerung</span
			>
		</a>

		<a
			href="/admin/announcement"
			class="group flex flex-col gap-4 border border-slate-200 bg-white p-8 transition-all hover:border-institutional-gold"
		>
			<div class="text-slate-300 transition-colors group-hover:text-institutional-gold">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-8 w-8"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z"
					/>
				</svg>
			</div>
			<span
				class="font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase"
				>Live-Mitteilung</span
			>
		</a>

		<a
			href="/admin/feedback"
			class="group flex flex-col gap-4 border border-slate-200 bg-white p-8 transition-all hover:border-teal-600"
		>
			<div class="text-slate-300 transition-colors group-hover:text-teal-600">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-8 w-8"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
					/>
				</svg>
			</div>
			<span
				class="font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase"
				>Feedback</span
			>
		</a>

		<a
			href="/projector"
			target="_blank"
			class="group flex flex-col gap-4 border border-slate-200 bg-white p-8 transition-all hover:border-institutional-navy"
		>
			<div class="text-slate-300 transition-colors group-hover:text-slate-900">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					class="h-8 w-8"
					fill="none"
					viewBox="0 0 24 24"
					stroke="currentColor"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						stroke-width="2"
						d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"
					/>
				</svg>
			</div>
			<span
				class="font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase"
				>Beamer-Modus</span
			>
		</a>
	</section>
</div>
