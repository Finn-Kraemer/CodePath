<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { auth } from '$lib/auth.svelte';
	import { ADMIN_GROUPS } from '$lib/admin/nav';

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

<div class="flex flex-col gap-8 pb-16">
	<header class="flex flex-wrap items-end justify-between gap-4 border-b border-slate-200 pb-5">
		<div>
			<p class="font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
				Leitstand
			</p>
			<h1 class="text-3xl font-extrabold tracking-tight text-institutional-navy">Übersicht</h1>
		</div>
		<p class="text-sm text-slate-500">Alles für den Unterricht auf einen Blick.</p>
	</header>

	{#if error}
		<div class="border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
			{error}
		</div>
	{/if}

	<!-- Lage: was gerade Aufmerksamkeit braucht -->
	<section class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="Lage">
		<a
			href="/admin/students"
			class="flex flex-col gap-1 border border-slate-200 bg-white p-5 transition-colors hover:border-institutional-navy"
		>
			<span class="text-xs font-semibold text-slate-500">Teilnehmende</span>
			<span class="text-3xl font-black text-institutional-navy"
				>{loading ? '–' : studentsCount}</span
			>
			<span class="text-xs text-institutional-navy underline">Liste öffnen</span>
		</a>
		<a
			href="/admin/submissions"
			class="flex flex-col gap-1 border bg-white p-5 transition-colors hover:border-institutional-navy {pendingSubmissionsCount >
			0
				? 'border-amber-300 bg-amber-50/60'
				: 'border-slate-200'}"
		>
			<span class="text-xs font-semibold text-slate-500">Offene Abgaben</span>
			<span
				class="text-3xl font-black {pendingSubmissionsCount > 0
					? 'text-amber-700'
					: 'text-institutional-navy'}">{loading ? '–' : pendingSubmissionsCount}</span
			>
			<span class="text-xs text-institutional-navy underline"
				>{pendingSubmissionsCount > 0 ? 'Jetzt bewerten' : 'Alles bewertet'}</span
			>
		</a>
		<div class="flex flex-col gap-2 border border-slate-200 bg-white p-5">
			<span class="text-xs font-semibold text-slate-500">Teilnahmebestätigungen</span>
			<span class="text-sm font-bold {certificatesEnabled ? 'text-teal-700' : 'text-slate-600'}">
				{certificatesEnabled ? 'Für Schüler freigegeben' : 'Gesperrt'}
			</span>
			<button
				type="button"
				onclick={toggleCertificates}
				class="mt-auto border px-3 py-1.5 text-xs font-bold transition-colors {certificatesEnabled
					? 'border-slate-300 text-slate-700 hover:border-rose-400 hover:text-rose-700'
					: 'border-institutional-navy bg-institutional-navy text-white hover:bg-slate-800'}"
			>
				{certificatesEnabled ? 'Sperren' : 'Freigeben'}
			</button>
		</div>
		<div class="flex flex-col gap-2 border border-slate-200 bg-white p-5">
			<span class="text-xs font-semibold text-slate-500">Feedback &amp; Sitzungen</span>
			<span class="text-sm font-bold {feedbackEnabled ? 'text-teal-700' : 'text-slate-600'}">
				Feedback {feedbackEnabled ? 'aktiv' : 'aus'}
			</span>
			<button
				type="button"
				onclick={logoutAllStudents}
				class="mt-auto border border-slate-300 px-3 py-1.5 text-xs font-bold text-slate-700 transition-colors hover:border-rose-400 hover:text-rose-700"
			>
				Alle Schüler abmelden
			</button>
			{#if sessionsMessage}<span class="text-xs text-slate-500">{sessionsMessage}</span>{/if}
		</div>
	</section>

	{#each ADMIN_GROUPS as group (group.title)}
		<section class="flex flex-col gap-3" aria-labelledby="group-{group.title}">
			<h2
				id="group-{group.title}"
				class="font-mono text-[11px] font-bold tracking-widest text-slate-400 uppercase"
			>
				{group.title}
			</h2>
			<div class="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
				{#each group.links as link (link.href)}
					<a
						href={link.href}
						class="group flex items-start gap-3 border border-slate-200 bg-white p-4 transition-colors hover:border-institutional-navy"
					>
						<span class="text-2xl leading-none" aria-hidden="true">{link.icon}</span>
						<span class="flex flex-col gap-0.5">
							<span class="font-bold text-institutional-navy group-hover:underline"
								>{link.title}</span
							>
							<span class="text-xs text-slate-500">{link.description}</span>
						</span>
					</a>
				{/each}
			</div>
		</section>
	{/each}
</div>
