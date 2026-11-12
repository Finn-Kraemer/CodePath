<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { auth } from '$lib/auth.svelte';

	let studentsCount = $state(0);
	let pendingSubmissionsCount = $state(0);
	let feedbackEnabled = $state(false);
	let certificatesEnabled = $state(false);
	let loading = $state(true);
	let error = $state('');
	let pollInterval: ReturnType<typeof setInterval> | undefined;
	let purgingSoon = $state<{ id: number; name: string; purgeAfter?: string }[]>([]);

	async function fetchData() {
		try {
			const [studentsRes, submissionsRes, feedbackRes, certsRes, purgeRes] = await Promise.all([
				auth.apiFetch('/api/admin/students'),
				auth.apiFetch('/api/admin/submissions'),
				auth.apiFetch('/api/feedback/status'),
				auth.apiFetch('/api/admin/settings/certificates'),
				auth.apiFetch('/api/admin/courses/purging-soon')
			]);
			if (purgeRes.ok) purgingSoon = await purgeRes.json();

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

	type Tile = { href: string; title: string; text: string; icon: string; external?: boolean };
	type Group = { title: string; text: string; tiles: Tile[] };

	const icons = {
		users:
			'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z',
		check:
			'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4',
		grid: 'M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z',
		chart:
			'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z',
		message:
			'M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z',
		projector:
			'M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z',
		edit: 'M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z',
		map: 'M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7',
		book: 'M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253',
		home: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6',
		page: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z',
		transfer: 'M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12',
		feedback: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z',
		printer:
			'M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z',
		image:
			'M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z'
	};

	const groups: Group[] = [
		{
			title: 'Unterricht',
			text: 'Was im laufenden Unterricht gebraucht wird.',
			tiles: [
				{
					href: '/admin/help',
					title: 'Hilfe-Rufe',
					text: 'Wer hängt fest? Warteschlange mit Aufgabe und Entwurf',
					icon: icons.message
				},
				{
					href: '/admin/quiz',
					title: 'Quiz-Runde',
					text: 'Live-Quiz aus Auswahl-Aufgaben, Auswertung am Beamer',
					icon: icons.projector
				},
				{
					href: '/admin/courses',
					title: 'Kurse & Konten',
					text: 'Kurse, Beitrittscodes und Konten verwalten',
					icon: icons.users
				},
				{
					href: '/admin/modules',
					title: 'Modul-Steuerung',
					text: 'Module je Kurs freigeben, sperren, Fristen setzen',
					icon: icons.grid
				},
				{
					href: '/admin/submissions',
					title: 'Abgaben prüfen',
					text: 'Praxisabgaben nach Rubrik bewerten',
					icon: icons.check
				},
				{
					href: '/admin/feedback',
					title: 'Feedback & Bewertung',
					text: 'Selbsteinschätzung, Unterrichtsbewertung, Schüler bewerten',
					icon: icons.feedback
				},
				{
					href: '/admin/statistics',
					title: 'Lernanalyse',
					text: 'Fortschritt und Schwierigkeiten der Klasse',
					icon: icons.chart
				},
				{
					href: '/admin/announcement',
					title: 'Live-Mitteilung',
					text: 'Hinweis an alle angemeldeten Schüler senden',
					icon: icons.message
				},
				{
					href: '/projector',
					title: 'Beamer-Modus',
					text: 'Rangliste für die Projektion (neuer Tab)',
					icon: icons.projector,
					external: true
				}
			]
		},
		{
			title: 'Inhalte',
			text: 'Aufgaben, Lernwege und Nachschlagewerk.',
			tiles: [
				{
					href: '/admin/content',
					title: 'Aufgaben & Module',
					text: 'Aufgaben erstellen, bearbeiten, veröffentlichen',
					icon: icons.edit
				},
				{
					href: '/admin/codepaths',
					title: 'CodePaths',
					text: 'Lernwege als Karte zeichnen',
					icon: icons.map
				},
				{
					href: '/admin/worksheets',
					title: 'Aufgabenblätter',
					text: 'Arbeitsblätter und Hausaufgaben als PDF',
					icon: icons.printer
				},
				{
					href: '/admin/knowledge',
					title: 'Wissensdatenbank',
					text: 'Artikel zum Nachschlagen pflegen',
					icon: icons.book
				}
			]
		},
		{
			title: 'Website',
			text: 'Öffentlich sichtbare Seiten der Plattform.',
			tiles: [
				{
					href: '/admin/homepage',
					title: 'Startseite',
					text: 'Inhalte der Startseite gestalten',
					icon: icons.home
				},
				{
					href: '/admin/pages',
					title: 'Eigene Seiten',
					text: 'Zusätzliche Seiten und Menüeinträge',
					icon: icons.page
				},
				{
					href: '/admin/media',
					title: 'Medien',
					text: 'Hochgeladene Bilder ansehen, umbenennen, aufräumen',
					icon: icons.image
				}
			]
		}
	];

	onMount(() => {
		fetchData();
		pollInterval = setInterval(fetchData, 30000);
	});

	onDestroy(() => {
		if (pollInterval) clearInterval(pollInterval);
	});
</script>

<svelte:head><title>Administration | CodePath</title></svelte:head>

{#snippet icon(path: string, cls = 'h-6 w-6')}
	<svg
		xmlns="http://www.w3.org/2000/svg"
		class={cls}
		fill="none"
		viewBox="0 0 24 24"
		stroke="currentColor"
		aria-hidden="true"
	>
		<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d={path} />
	</svg>
{/snippet}

<div class="mx-auto flex max-w-7xl flex-col gap-12 pb-24">
	<header
		class="flex flex-col justify-between gap-6 border-b border-slate-200 pb-8 md:flex-row md:items-end"
	>
		<div>
			<span
				class="font-mono text-[10px] font-bold tracking-[4px] text-institutional-navy/40 uppercase"
				>Leitstand</span
			>
			<h1
				class="mt-2 font-sans text-5xl leading-none font-black tracking-tighter text-institutional-navy uppercase"
			>
				Administration
			</h1>
		</div>

		<!-- Kennzahlen -->
		<div class="flex gap-3">
			<a
				href="/admin/students"
				class="flex min-w-36 flex-col border border-slate-200 bg-white px-5 py-3 transition-colors hover:border-institutional-navy"
			>
				<span class="font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase"
					>Teilnehmer</span
				>
				<span class="font-sans text-3xl font-black text-institutional-navy"
					>{loading ? '–' : studentsCount}</span
				>
			</a>
			<a
				href="/admin/submissions"
				class="flex min-w-36 flex-col border bg-white px-5 py-3 transition-colors hover:border-institutional-navy {pendingSubmissionsCount >
				0
					? 'border-institutional-gold'
					: 'border-slate-200'}"
			>
				<span class="font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase"
					>Offene Abgaben</span
				>
				<span
					class="font-sans text-3xl font-black {pendingSubmissionsCount > 0
						? 'text-institutional-gold'
						: 'text-institutional-navy'}">{loading ? '–' : pendingSubmissionsCount}</span
				>
			</a>
		</div>
	</header>

	{#if error}
		<div class="border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
			{error}
		</div>
	{/if}

	{#if purgingSoon.length > 0}
		<div
			class="border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900"
			role="status"
			data-testid="purging-soon"
		>
			<strong>Archivierte Kurse werden bald gelöscht:</strong>
			{#each purgingSoon as course, i (course.id)}{i > 0 ? ', ' : ' '}„{course.name}“ am {course.purgeAfter
					? new Date(course.purgeAfter).toLocaleDateString('de-DE')
					: '–'}{/each}. Jetzt noch exportieren oder unter
			<a href="/admin/courses" class="font-bold underline">Kurse &amp; Konten</a> wiederherstellen.
		</div>
	{/if}

	{#each groups as group (group.title)}
		<section class="flex flex-col gap-4" aria-labelledby="group-{group.title}">
			<div class="flex flex-wrap items-baseline gap-x-4 gap-y-1 border-b border-slate-100 pb-2">
				<h2
					id="group-{group.title}"
					class="font-sans text-sm font-black tracking-[3px] text-institutional-navy uppercase"
				>
					{group.title}
				</h2>
				<p class="text-sm text-slate-500">{group.text}</p>
			</div>
			<ul class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
				{#each group.tiles as tile (tile.href)}
					<li>
						<a
							href={tile.href}
							target={tile.external ? '_blank' : undefined}
							class="group flex h-full items-start gap-4 border border-slate-200 bg-white p-5 transition-colors hover:border-institutional-navy"
						>
							<span class="text-slate-300 transition-colors group-hover:text-institutional-navy">
								{@render icon(tile.icon)}
							</span>
							<span class="min-w-0">
								<span class="block font-bold text-institutional-navy">{tile.title}</span>
								<span class="block text-sm text-slate-500">{tile.text}</span>
							</span>
						</a>
					</li>
				{/each}
			</ul>
		</section>
	{/each}

	<!-- Einstellungen & System -->
	<section class="flex flex-col gap-4" aria-labelledby="group-system">
		<div class="flex flex-wrap items-baseline gap-x-4 gap-y-1 border-b border-slate-100 pb-2">
			<h2
				id="group-system"
				class="font-sans text-sm font-black tracking-[3px] text-institutional-navy uppercase"
			>
				Einstellungen &amp; System
			</h2>
			<p class="text-sm text-slate-500">Schalter für die ganze Plattform und Datensicherung.</p>
		</div>
		<ul class="flex flex-col divide-y divide-slate-100 border border-slate-200 bg-white">
			<li class="flex flex-wrap items-center gap-4 p-5">
				<div class="min-w-0 flex-1">
					<p class="font-bold text-institutional-navy">Teilnahmebestätigungen (PDF)</p>
					<p class="text-sm text-slate-500">
						Schülerinnen und Schüler können ihr Zertifikat herunterladen.
					</p>
				</div>
				<span
					class="px-2 py-1 font-mono text-[9px] font-bold uppercase {certificatesEnabled
						? 'bg-emerald-100 text-emerald-800'
						: 'bg-slate-100 text-slate-500'}"
					>{certificatesEnabled ? 'Freigegeben' : 'Gesperrt'}</span
				>
				<button class="ed-button" onclick={toggleCertificates}>
					{certificatesEnabled ? 'Sperren' : 'Freigeben'}
				</button>
			</li>
			<li class="flex flex-wrap items-center gap-4 p-5">
				<div class="min-w-0 flex-1">
					<p class="font-bold text-institutional-navy">Feedback-Nachrichten</p>
					<p class="text-sm text-slate-500">
						Freie Nachrichten der Klasse; Bewertungsbögen stehen unter „Feedback &amp; Bewertung“.
					</p>
				</div>
				<span
					class="px-2 py-1 font-mono text-[9px] font-bold uppercase {feedbackEnabled
						? 'bg-emerald-100 text-emerald-800'
						: 'bg-slate-100 text-slate-500'}">{feedbackEnabled ? 'Aktiv' : 'Aus'}</span
				>
				<a class="ed-button" href="/admin/feedback/messages">Verwalten</a>
			</li>
			<li class="flex flex-wrap items-center gap-4 p-5">
				<div class="min-w-0 flex-1">
					<p class="font-bold text-institutional-navy">Import &amp; Export</p>
					<p class="text-sm text-slate-500">
						Inhalte, CodePaths, Seiten und Einstellungen als ZIP sichern oder einspielen.
					</p>
				</div>
				<a class="ed-button" href="/admin/transfer">Öffnen</a>
			</li>
			<li class="flex flex-wrap items-center gap-4 p-5">
				<div class="min-w-0 flex-1">
					<p class="font-bold text-institutional-navy">Alle Schüler abmelden</p>
					<p class="text-sm text-slate-500">
						Beendet alle Sitzungen von Schülerinnen und Schülern, z. B. am Stundenende.
					</p>
					{#if sessionsMessage}
						<p class="mt-1 text-sm text-emerald-700" role="status">{sessionsMessage}</p>
					{/if}
				</div>
				<button
					class="ed-button border-rose-300 text-rose-700 hover:bg-rose-50"
					onclick={logoutAllStudents}>Abmelden</button
				>
			</li>
		</ul>
	</section>
</div>
