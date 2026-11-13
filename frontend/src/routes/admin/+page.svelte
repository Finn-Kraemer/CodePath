<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { auth } from '$lib/auth.svelte';
	import { realtime } from '$lib/realtime.svelte';
	import { ADMIN_ICONS, ADMIN_NAV } from '$lib/admin/nav';
	import type { HelpQueueEntry, StudentOverview, TaskStatistic } from '$lib/api';

	let students = $state<StudentOverview[]>([]);
	let pendingSubmissionsCount = $state(0);
	let helpOpen = $state<HelpQueueEntry[]>([]);
	let stats = $state<TaskStatistic[]>([]);
	let feedbackEnabled = $state(false);
	let certificatesEnabled = $state(false);
	let loading = $state(true);
	let error = $state('');
	let sessionsMessage = $state('');
	let pollInterval: ReturnType<typeof setInterval> | undefined;

	async function json<T>(url: string, fallback: T): Promise<T> {
		const res = await auth.apiFetch(url);
		return res.ok ? ((await res.json()) as T) : fallback;
	}

	async function fetchData() {
		try {
			const [s, subs, help, st, feedback, certs] = await Promise.all([
				json<StudentOverview[]>('/api/admin/students', []),
				json<unknown[]>('/api/admin/submissions', []),
				json<{ open: HelpQueueEntry[] }>('/api/admin/help', { open: [] }),
				json<TaskStatistic[]>('/api/admin/statistics/tasks', []),
				json<{ enabled: boolean }>('/api/feedback/status', { enabled: false }),
				json<{ enabled: boolean }>('/api/admin/settings/certificates', { enabled: false })
			]);
			students = s;
			pendingSubmissionsCount = subs.length;
			helpOpen = help.open ?? [];
			stats = st;
			feedbackEnabled = feedback.enabled;
			certificatesEnabled = certs.enabled;
			error = '';
		} catch (e) {
			console.error('Fetch error', e);
			error = 'Fehler beim Laden der Dashboard-Daten';
		} finally {
			loading = false;
		}
	}

	// Neue Hilfe-Rufe sofort zeigen
	$effect(() => {
		if (realtime.helpOpen !== null && realtime.helpOpen !== helpOpen.length) fetchData();
	});

	async function toggleCertificates() {
		try {
			const res = await auth.apiFetch('/api/admin/settings/certificates/toggle', { method: 'PUT' });
			if (res.ok) certificatesEnabled = (await res.json()).enabled;
		} catch {
			error = 'Fehler beim Umschalten der Zeugnisfunktion';
		}
	}

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

	const solvedTotal = $derived(students.reduce((sum, s) => sum + (s.completedTasks ?? 0), 0));
	const avgSolved = $derived(students.length ? solvedTotal / students.length : 0);
	const oldestWait = $derived(Math.max(0, ...helpOpen.map((h) => h.waitingSeconds ?? 0)));
	const top = $derived(
		[...students].sort((a, b) => (b.totalPoints ?? 0) - (a.totalPoints ?? 0)).slice(0, 5)
	);
	// Wo hakt es? Aufgaben mit den meisten Fehlversuchen
	const struggles = $derived(
		stats
			.filter((t) => (t.failedAttempts ?? 0) > 0)
			.sort((a, b) => (b.failedAttempts ?? 0) - (a.failedAttempts ?? 0))
			.slice(0, 5)
	);
	const maxFailed = $derived(Math.max(1, ...struggles.map((t) => t.failedAttempts ?? 0)));
	const quickLinks = ADMIN_NAV.flatMap((g) => g.links).filter((l) => l.quick);

	function wait(seconds: number): string {
		if (seconds < 60) return 'gerade eben';
		const m = Math.round(seconds / 60);
		return m < 60 ? `${m} Min.` : `${Math.floor(m / 60)} Std. ${m % 60} Min.`;
	}

	function greeting(): string {
		const h = new Date().getHours();
		return h < 11 ? 'Guten Morgen' : h < 17 ? 'Guten Tag' : 'Guten Abend';
	}
</script>

<svelte:head><title>Leitstand | CodePath</title></svelte:head>

{#snippet icon(name: keyof typeof ADMIN_ICONS, cls = 'h-[18px] w-[18px]')}
	<svg
		class={cls}
		fill="none"
		viewBox="0 0 24 24"
		stroke="currentColor"
		stroke-width="2"
		stroke-linecap="round"
		stroke-linejoin="round"
		aria-hidden="true"><path d={ADMIN_ICONS[name]} /></svg
	>
{/snippet}

{#snippet kpi(
	href: string,
	label: string,
	value: string,
	sub: string,
	name: keyof typeof ADMIN_ICONS,
	tone: string,
	subTone = 'text-slate-500'
)}
	<a
		{href}
		class="ui-card flex flex-col gap-2.5 p-5 transition-shadow hover:border-slate-300 hover:shadow-md"
	>
		<span class="flex items-center justify-between">
			<span class="text-sm font-semibold text-slate-600">{label}</span>
			<span class="flex h-8 w-8 items-center justify-center rounded-[var(--radius-ui-sm)] {tone}"
				>{@render icon(name, 'h-4 w-4')}</span
			>
		</span>
		<span class="text-3xl leading-none font-extrabold tracking-tight text-institutional-navy"
			>{loading ? '–' : value}</span
		>
		<span class="text-[13px] {subTone}">{sub}</span>
	</a>
{/snippet}

<div class="flex flex-col gap-6 pb-12">
	<header class="flex flex-wrap items-end justify-between gap-4">
		<div>
			<p class="ui-eyebrow">Leitstand</p>
			<h1 class="ui-title mt-1">{greeting()}</h1>
			<p class="mt-1.5 flex flex-wrap items-center gap-2 text-[15px] text-slate-600">
				{#if realtime.isConnected}
					<span class="inline-flex items-center gap-1.5 font-semibold text-emerald-700">
						<span class="h-2 w-2 rounded-full bg-emerald-500 shadow-[0_0_0_4px_#d1fae5]"></span>Live
					</span>
					·
				{/if}
				{students.length}
				{students.length === 1 ? 'Teilnehmer:in' : 'Teilnehmende'} in deinen Kursen
			</p>
		</div>
		<div class="flex flex-wrap gap-2">
			<a href="/projector" target="_blank" class="ui-btn">
				{@render icon('projector', 'h-4 w-4')} Beamer öffnen
			</a>
			<a href="/admin/announcement" class="ui-btn ui-btn-primary">
				{@render icon('megaphone', 'h-4 w-4')} Mitteilung senden
			</a>
		</div>
	</header>

	{#if error}
		<div class="ui-card border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
			{error}
		</div>
	{/if}

	<section aria-label="Kennzahlen" class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
		{@render kpi(
			'/admin/students',
			'Teilnehmende',
			String(students.length),
			`${solvedTotal} Aufgaben gelöst`,
			'users',
			'bg-sky-100 text-sky-700'
		)}
		{@render kpi(
			'/admin/submissions',
			'Offene Abgaben',
			String(pendingSubmissionsCount),
			pendingSubmissionsCount ? 'warten auf Bewertung' : 'alles bewertet',
			'check',
			'bg-amber-100 text-amber-700',
			pendingSubmissionsCount ? 'font-semibold text-amber-800' : 'text-slate-500'
		)}
		{@render kpi(
			'/admin/help',
			'Hilfe-Rufe',
			String(helpOpen.length),
			helpOpen.length ? `längster wartet ${wait(oldestWait)}` : 'niemand wartet',
			'help',
			'bg-rose-100 text-rose-700',
			helpOpen.length ? 'font-semibold text-rose-700' : 'text-slate-500'
		)}
		{@render kpi(
			'/admin/statistics',
			'Ø gelöste Aufgaben',
			avgSolved.toLocaleString('de-DE', { maximumFractionDigits: 1 }),
			'je Teilnehmer:in',
			'chart',
			'bg-emerald-100 text-emerald-700'
		)}
	</section>

	<div class="grid grid-cols-1 gap-6 xl:grid-cols-5">
		<!-- Hilfe-Rufe -->
		<section class="ui-card overflow-hidden xl:col-span-3" aria-labelledby="h-help">
			<div class="flex items-center gap-3 px-5 py-4">
				<h2 id="h-help" class="flex-1 text-[17px] font-bold">
					Hilfe-Rufe
					{#if helpOpen.length}<span class="ui-chip ui-chip-danger ml-2 align-middle"
							>{helpOpen.length} offen</span
						>{/if}
				</h2>
				<a href="/admin/help" class="text-sm font-semibold hover:text-institutional-gold"
					>Warteschlange →</a
				>
			</div>
			{#if helpOpen.length === 0}
				<p class="border-t border-slate-100 px-5 py-8 text-center text-sm text-slate-500">
					Gerade braucht niemand Hilfe.
				</p>
			{:else}
				<ul>
					{#each helpOpen.slice(0, 5) as h (h.id)}
						<li class="flex items-center gap-3 border-t border-slate-100 px-5 py-3">
							<span
								class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-rose-100 text-sm font-bold text-rose-800"
								aria-hidden="true"
								>{(h.displayName ?? h.username ?? '?').charAt(0).toUpperCase()}</span
							>
							<span class="min-w-0 flex-1">
								<span class="block text-[15px] font-semibold">{h.displayName ?? h.username}</span>
								<span class="block truncate text-[13px] text-slate-500"
									>{h.moduleTitle} › {h.taskTitle}</span
								>
							</span>
							<span
								class="font-mono text-xs font-bold {(h.waitingSeconds ?? 0) > 300
									? 'text-rose-700'
									: 'text-slate-500'}">{wait(h.waitingSeconds ?? 0)}</span
							>
						</li>
					{/each}
				</ul>
			{/if}
		</section>

		<!-- Rangliste -->
		<section class="ui-card overflow-hidden xl:col-span-2" aria-labelledby="h-top">
			<div class="flex items-center gap-3 px-5 py-4">
				<h2 id="h-top" class="flex-1 text-[17px] font-bold">Vorne dabei</h2>
				<a href="/admin/students" class="text-sm font-semibold hover:text-institutional-gold"
					>Alle →</a
				>
			</div>
			{#if top.length === 0}
				<p class="border-t border-slate-100 px-5 py-8 text-center text-sm text-slate-500">
					Noch keine Teilnehmenden.
				</p>
			{:else}
				<ol>
					{#each top as s, i (s.username)}
						<li class="flex items-center gap-3 border-t border-slate-100 px-5 py-2.5">
							<span class="w-5 font-mono text-xs font-bold text-slate-500">{i + 1}.</span>
							<a
								href="/admin/students/{s.username}"
								class="min-w-0 flex-1 truncate text-[15px] font-semibold hover:text-institutional-gold"
								>{s.displayName ?? s.username}</a
							>
							<span class="text-xs text-slate-500">{s.completedTasks} gelöst</span>
							<span class="ui-chip ui-chip-now font-mono">{s.totalPoints} P.</span>
						</li>
					{/each}
				</ol>
			{/if}
		</section>
	</div>

	<div class="grid grid-cols-1 gap-6 xl:grid-cols-5">
		<!-- Schnellzugriff -->
		<section class="ui-card p-5 xl:col-span-3" aria-labelledby="h-quick">
			<h2 id="h-quick" class="mb-4 text-[17px] font-bold">Schnellzugriff</h2>
			<ul class="grid grid-cols-1 gap-3 sm:grid-cols-2">
				{#each quickLinks as link (link.href)}
					<li>
						<a
							href={link.href}
							class="flex h-full items-start gap-3 rounded-[var(--radius-ui-sm)] border border-slate-200 p-3.5 transition-colors hover:border-slate-400 hover:bg-slate-50"
						>
							<span
								class="flex h-9 w-9 shrink-0 items-center justify-center rounded-[var(--radius-ui-sm)] bg-slate-100 text-institutional-navy"
								>{@render icon(link.icon)}</span
							>
							<span class="min-w-0">
								<span class="block text-[15px] font-semibold">{link.title}</span>
								<span class="block text-[13px] leading-snug text-slate-500">{link.text}</span>
							</span>
						</a>
					</li>
				{/each}
			</ul>
		</section>

		<!-- Wo hakt es? -->
		<section class="ui-card p-5 xl:col-span-2" aria-labelledby="h-struggle">
			<div class="mb-4 flex items-center">
				<h2 id="h-struggle" class="flex-1 text-[17px] font-bold">Wo hakt es?</h2>
				<a href="/admin/statistics" class="text-sm font-semibold hover:text-institutional-gold"
					>Lernanalyse →</a
				>
			</div>
			{#if struggles.length === 0}
				<p class="py-6 text-center text-sm text-slate-500">Noch keine Fehlversuche.</p>
			{:else}
				<ul class="flex flex-col gap-3.5">
					{#each struggles as t (t.taskId)}
						<li>
							<div class="mb-1.5 flex justify-between gap-3 text-[13.5px]">
								<span class="min-w-0 truncate"
									><span class="font-semibold">{t.title}</span>
									<span class="text-slate-500">· {t.moduleTitle}</span></span
								>
								<span class="shrink-0 font-mono text-xs text-slate-600"
									>{t.failedAttempts} Fehlversuche</span
								>
							</div>
							<div class="ui-bar h-2">
								<span
									class="!bg-amber-500"
									style="width: {Math.round(((t.failedAttempts ?? 0) / maxFailed) * 100)}%"
								></span>
							</div>
						</li>
					{/each}
				</ul>
			{/if}
		</section>
	</div>

	<!-- Einstellungen -->
	<section class="ui-card overflow-hidden" aria-labelledby="h-system">
		<h2 id="h-system" class="px-5 py-4 text-[17px] font-bold">Einstellungen &amp; System</h2>
		<ul class="divide-y divide-slate-100 border-t border-slate-100">
			<li class="flex flex-wrap items-center gap-4 px-5 py-4">
				<div class="min-w-0 flex-1">
					<p class="font-semibold">Teilnahmebestätigungen (PDF)</p>
					<p class="text-sm text-slate-500">
						Schülerinnen und Schüler können ihr Zertifikat herunterladen.
					</p>
				</div>
				<button
					type="button"
					role="switch"
					aria-checked={certificatesEnabled}
					aria-label="Teilnahmebestätigungen freigeben"
					onclick={toggleCertificates}
					class="relative h-[26px] w-11 shrink-0 rounded-full transition-colors {certificatesEnabled
						? 'bg-emerald-600'
						: 'bg-slate-300'}"
				>
					<span
						class="absolute top-[3px] h-5 w-5 rounded-full bg-white shadow transition-all {certificatesEnabled
							? 'left-[21px]'
							: 'left-[3px]'}"
					></span>
				</button>
			</li>
			<li class="flex flex-wrap items-center gap-4 px-5 py-4">
				<div class="min-w-0 flex-1">
					<p class="font-semibold">Feedback</p>
					<p class="text-sm text-slate-500">
						Rückmeldungen der Schülerinnen und Schüler einsammeln und lesen.
					</p>
				</div>
				<span class="ui-chip {feedbackEnabled ? 'ui-chip-done' : ''}"
					>{feedbackEnabled ? 'Aktiv' : 'Aus'}</span
				>
				<a class="ui-btn" href="/admin/feedback">Verwalten</a>
			</li>
			<li class="flex flex-wrap items-center gap-4 px-5 py-4">
				<div class="min-w-0 flex-1">
					<p class="font-semibold">Import &amp; Export</p>
					<p class="text-sm text-slate-500">
						Inhalte, CodePaths, Seiten und Einstellungen als ZIP sichern oder einspielen.
					</p>
				</div>
				<a class="ui-btn" href="/admin/transfer">Öffnen</a>
			</li>
			<li class="flex flex-wrap items-center gap-4 bg-rose-50/60 px-5 py-4">
				<div class="min-w-0 flex-1">
					<p class="font-semibold text-rose-900">Alle Schüler abmelden</p>
					<p class="text-sm text-rose-900/80">
						Beendet alle Sitzungen von Schülerinnen und Schülern, z. B. am Stundenende.
					</p>
					{#if sessionsMessage}
						<p class="mt-1 text-sm text-emerald-700" role="status">{sessionsMessage}</p>
					{/if}
				</div>
				<button class="ui-btn ui-btn-danger" onclick={logoutAllStudents}>Abmelden …</button>
			</li>
		</ul>
	</section>
</div>
