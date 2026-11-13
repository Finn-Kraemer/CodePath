<script lang="ts">
	import PdfBundle from '$lib/components/PdfBundle.svelte';
	import { onMount } from 'svelte';
	import { api, type CourseSummary } from '$lib/api';
	import { courseSelection, nextCourseName as nextName } from '$lib/courses.svelte';

	type Account = { username: string; password: string };

	let courses = $state<CourseSummary[]>([]);
	let name = $state('');
	let endsOn = $state('');
	let accounts = $state<{ course: string; list: Account[] } | null>(null);
	let prefix = $state<Record<number, string>>({});
	let count = $state<Record<number, number>>({});
	let error = $state('');
	let message = $state('');

	// Aufgeklappte Aktion je Kurs: archivieren oder ins nächste Schuljahr übernehmen
	let panel = $state<{ id: number; kind: 'archive' | 'promote' } | null>(null);
	let archiveMonths = $state(6);
	let promote = $state({
		name: '',
		endsOn: '',
		keepProgress: true,
		keepReleases: false,
		copyCodePaths: true,
		restartLeaderboard: true
	});

	const DAY = 24 * 60 * 60 * 1000;

	function date(value?: string | null) {
		return value ? new Date(value).toLocaleDateString('de-DE') : '–';
	}

	function purgeSoon(course: CourseSummary) {
		return (
			course.status === 'ARCHIVED' &&
			!!course.purgeAfter &&
			new Date(course.purgeAfter).getTime() - Date.now() < 14 * DAY
		);
	}

	function open(course: CourseSummary, kind: 'archive' | 'promote') {
		if (panel?.id === course.id && panel.kind === kind) {
			panel = null;
			return;
		}
		panel = { id: course.id, kind };
		archiveMonths = 6;
		promote = {
			name: nextName(course.name),
			endsOn: '',
			keepProgress: true,
			keepReleases: false,
			copyCodePaths: true,
			restartLeaderboard: true
		};
	}

	async function archive(course: CourseSummary) {
		if (
			!confirm(
				`Kurs „${course.name}“ archivieren? Die ${course.members} Schülerinnen und Schüler werden abgemeldet und können sich nicht mehr anmelden. In ${archiveMonths} Monaten werden alle Daten gelöscht.`
			)
		)
			return;
		error = '';
		try {
			await api(`/api/admin/courses/${course.id}/archive`, {
				method: 'POST',
				body: { months: archiveMonths }
			});
			message = `Kurs „${course.name}“ archiviert.`;
			panel = null;
			await load();
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function restore(course: CourseSummary) {
		if (
			!confirm(
				`Kurs „${course.name}“ wiederherstellen? Anmeldung und Beitrittscode gelten wieder, die Löschfrist entfällt.`
			)
		)
			return;
		error = '';
		try {
			await api(`/api/admin/courses/${course.id}/restore`, { method: 'POST' });
			message = `Kurs „${course.name}“ ist wieder aktiv.`;
			await load();
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function promoteCourse(course: CourseSummary) {
		const lines = [
			`Alle ${course.members} Mitglieder wechseln von „${course.name}“ in den neuen Kurs „${promote.name}“.`,
			promote.keepProgress
				? 'Fortschritt und Punkte bleiben erhalten.'
				: 'Fortschritt, Versuche und Punkte werden GELÖSCHT.',
			promote.keepReleases
				? 'Freigaben werden übernommen (ohne Fristen).'
				: 'Alle Module beginnen gesperrt.',
			`„${course.name}“ gilt danach als beendet.`
		];
		if (!confirm(lines.join('\n'))) return;
		error = '';
		try {
			const created = await api<CourseSummary>(`/api/admin/courses/${course.id}/promote`, {
				method: 'POST',
				body: { ...promote, endsOn: promote.endsOn || null }
			});
			message = `Neuer Kurs „${created.name}“ mit ${created.members} Mitgliedern angelegt. Beitrittscode: ${created.joinCode}`;
			panel = null;
			await load();
			courseSelection.select(created.id);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function load() {
		try {
			courses = await api<CourseSummary[]>('/api/admin/courses');
			await courseSelection.load();
		} catch (e) {
			error = (e as Error).message;
		}
	}

	onMount(load);

	async function create() {
		error = '';
		try {
			await api('/api/admin/courses', { method: 'POST', body: { name, endsOn: endsOn || null } });
			name = '';
			endsOn = '';
			await load();
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function newCode(course: CourseSummary) {
		if (!confirm('Neuen Beitrittscode erzeugen? Der alte gilt dann nicht mehr.')) return;
		await api(`/api/admin/courses/${course.id}/join-code`, { method: 'POST' });
		await load();
	}

	async function createAccounts(course: CourseSummary) {
		error = '';
		try {
			const list = await api<Account[]>(`/api/admin/courses/${course.id}/accounts`, {
				method: 'POST',
				body: { prefix: prefix[course.id] || course.name, count: count[course.id] || 1 }
			});
			accounts = { course: course.name, list };
			await load();
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function endCourse(course: CourseSummary) {
		error = '';
		const typed = prompt(
			`Kurs „${course.name}“ beenden? Alle ${course.members} Schülerkonten werden mit Fortschritt und Abgaben gelöscht. ` +
				'Zeugnisse vorher herunterladen und die Klasse an ihr Mitnehm-Paket (Profil) erinnern! Zum Bestätigen den Kursnamen eingeben:'
		);
		if (typed !== course.name) return;
		const res = await api<{ deletedAccounts: number }>(`/api/admin/courses/${course.id}/end`, {
			method: 'POST'
		});
		message = `Kurs beendet, ${res.deletedAccounts} Konten gelöscht.`;
		await load();
	}

	function joinLink(course: CourseSummary) {
		return `${location.origin}/register?code=${course.joinCode}`;
	}
</script>

<svelte:head><title>Kurse | CodePath</title></svelte:head>

<div class="flex flex-col gap-10 pb-24">
	<header class="border-b border-slate-200 pb-8">
		<a
			href="/admin"
			class="mb-6 inline-block font-mono text-[11px] font-bold tracking-wider text-slate-400 uppercase hover:text-institutional-navy"
			>← Leitstand</a
		>
		<h1 class="font-sans text-3xl font-extrabold tracking-tight text-institutional-navy">
			Kurse &amp; Konten
		</h1>
		<p class="mt-4 text-slate-500">
			Schülerinnen und Schüler treten mit dem Beitrittscode bei – oder du legst Konten gesammelt an.
		</p>
	</header>

	{#if error}<div class="border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
			{error}
		</div>{/if}
	{#if message}<div
			class="border border-green-200 bg-green-50 p-4 text-sm text-green-800"
			role="status"
		>
			{message}
		</div>{/if}

	<form
		class="flex flex-wrap items-end gap-4 rounded-[var(--radius-ui)] border border-slate-200 bg-white p-6"
		onsubmit={(e) => {
			e.preventDefault();
			create();
		}}
	>
		<label class="text-sm"
			>Name<br /><input
				bind:value={name}
				required
				placeholder="z. B. 8a Projekttag"
				class="mt-1 border-slate-200"
			/></label
		>
		<label class="text-sm"
			>Enddatum (optional)<br /><input
				type="date"
				bind:value={endsOn}
				class="mt-1 border-slate-200"
			/></label
		>
		<button
			class="rounded-[var(--radius-ui-sm)] bg-institutional-navy px-6 py-3 text-sm font-semibold text-white"
			>Kurs anlegen</button
		>
	</form>

	{#if accounts}
		<section class="border-2 border-institutional-gold bg-amber-50 p-6">
			<div class="mb-4 flex items-center justify-between">
				<h2 class="font-bold">
					Neue Konten für {accounts.course} – Passwörter werden nur jetzt angezeigt
				</h2>
				<button
					onclick={() => window.print()}
					class="rounded-[var(--radius-ui-sm)] border px-4 py-2 text-sm">Drucken</button
				>
			</div>
			<table class="w-full font-mono text-sm">
				<thead
					><tr><th class="text-left">Benutzername</th><th class="text-left">Passwort</th></tr
					></thead
				>
				<tbody
					>{#each accounts.list as a (a.username)}<tr><td>{a.username}</td><td>{a.password}</td></tr
						>{/each}</tbody
				>
			</table>
		</section>
	{/if}

	<section class="space-y-6">
		{#each courses as course (course.id)}
			<article
				class="border bg-white p-6 {course.status === 'ENDED'
					? 'border-slate-200 opacity-60'
					: course.status === 'ARCHIVED'
						? 'border-amber-300'
						: 'border-slate-200'}"
				data-testid="course-{course.id}"
			>
				<div class="flex flex-wrap items-start justify-between gap-6">
					<div>
						<h2 class="text-xl font-bold text-institutional-navy">
							{course.name}
							{course.status === 'ENDED' ? '(beendet)' : ''}
							{#if course.status === 'ARCHIVED'}
								<span
									class="ml-2 border border-amber-300 bg-amber-50 px-2 py-0.5 align-middle font-mono text-[11px] tracking-wider text-amber-800 uppercase"
									>archiviert</span
								>
							{/if}
						</h2>
						<p class="text-sm text-slate-500">
							{course.members} Mitglieder{course.endsOn
								? ` · endet am ${new Date(course.endsOn).toLocaleDateString('de-DE')}`
								: ''}{course.leaderboardSince
								? ` · Rangliste seit ${date(course.leaderboardSince)}`
								: ''}
						</p>
						{#if course.status === 'ENDED' && course.successorName}
							<p class="text-sm text-slate-500">
								Übernommen in „{course.successorName}“
							</p>
						{/if}
						{#if course.status === 'ARCHIVED'}
							<p
								class="mt-1 text-sm {purgeSoon(course)
									? 'font-bold text-rose-700'
									: 'text-amber-800'}"
							>
								Archiviert am {date(course.archivedAt)} · Schüler können sich nicht anmelden · alle Daten
								werden am {date(course.purgeAfter)} gelöscht
							</p>
						{/if}
					</div>
					{#if course.status === 'ACTIVE'}
						<div class="text-right">
							<p class="font-mono text-[11px] tracking-wider text-slate-400 uppercase">
								Beitrittscode
							</p>
							<p class="font-mono text-3xl font-black tracking-wider text-institutional-navy">
								{course.joinCode}
							</p>
							<button
								onclick={() => navigator.clipboard?.writeText(joinLink(course))}
								class="text-xs text-slate-500 underline">Link kopieren</button
							>
							·
							<button onclick={() => newCode(course)} class="text-xs text-slate-500 underline"
								>neuer Code</button
							>
						</div>
					{/if}
				</div>
				{#if course.status === 'ACTIVE'}
					<div class="mt-6 flex flex-wrap items-end gap-4 border-t border-slate-100 pt-6">
						<label class="text-sm"
							>Präfix<br /><input
								bind:value={prefix[course.id]}
								placeholder={course.name}
								class="mt-1 w-32 border-slate-200"
							/></label
						>
						<label class="text-sm"
							>Anzahl<br /><input
								type="number"
								min="1"
								max="200"
								bind:value={count[course.id]}
								placeholder="25"
								class="mt-1 w-24 border-slate-200"
							/></label
						>
						<button
							onclick={() => createAccounts(course)}
							class="rounded-[var(--radius-ui-sm)] border-2 border-institutional-navy px-4 py-2 text-sm font-semibold text-institutional-navy"
							>Konten anlegen</button
						>
					</div>
				{/if}
				{#if course.status !== 'ENDED'}
					<div class="mt-4 flex flex-wrap gap-3 border-t border-slate-100 pt-4">
						{#if course.status === 'ACTIVE'}
							<button
								onclick={() => open(course, 'archive')}
								class="rounded-[var(--radius-ui-sm)] border border-amber-400 px-4 py-2 text-sm text-amber-800"
								data-testid="archive-{course.id}">Archivieren …</button
							>
						{:else}
							<button
								onclick={() => restore(course)}
								class="rounded-[var(--radius-ui-sm)] border-2 border-institutional-navy px-4 py-2 text-sm font-semibold text-institutional-navy"
								data-testid="restore-{course.id}">Wiederherstellen</button
							>
						{/if}
						<button
							onclick={() => open(course, 'promote')}
							class="rounded-[var(--radius-ui-sm)] border border-slate-300 px-4 py-2 text-sm text-institutional-navy"
							data-testid="promote-{course.id}">Ins nächste Schuljahr …</button
						>
						<span class="flex-1"></span>
						<button
							onclick={() => endCourse(course)}
							class="rounded-[var(--radius-ui-sm)] border border-rose-400 px-4 py-2 text-sm text-rose-600"
							>{course.status === 'ARCHIVED' ? 'Jetzt löschen' : 'Kurs beenden'}</button
						>
					</div>
					{#if panel?.id === course.id && panel.kind === 'archive'}
						<div class="mt-4 space-y-3 border border-amber-200 bg-amber-50 p-4 text-sm">
							<p class="font-bold text-amber-900">Was beim Archivieren passiert</p>
							<ul class="list-disc space-y-1 pl-5 text-amber-900">
								<li>Alle Mitglieder werden abgemeldet und können sich nicht mehr anmelden.</li>
								<li>Der Beitrittscode gilt nicht mehr.</li>
								<li>
									Du siehst weiter alle Daten (Schülerakten, Abgaben, Bewertungsbögen) und kannst
									Bestätigungen und Mitnehm-Pakete exportieren.
								</li>
								<li>
									Nach Ablauf der Frist werden alle Schülerkonten mit ihren Daten gelöscht – wie
									beim Beenden. Bis dahin lässt sich der Kurs wiederherstellen.
								</li>
							</ul>
							<label class="flex items-center gap-3"
								>Löschen nach
								<select bind:value={archiveMonths} class="border-amber-300 bg-white py-1">
									{#each [1, 3, 6, 12, 24] as m (m)}<option value={m}
											>{m} {m === 1 ? 'Monat' : 'Monaten'}</option
										>{/each}
								</select></label
							>
							<button
								onclick={() => archive(course)}
								class="rounded-[var(--radius-ui-sm)] bg-amber-700 px-4 py-2 text-sm font-semibold text-white"
								data-testid="archive-confirm">Kurs archivieren</button
							>
						</div>
					{/if}
					{#if panel?.id === course.id && panel.kind === 'promote'}
						<form
							class="mt-4 space-y-3 border border-slate-200 bg-slate-50 p-4 text-sm"
							onsubmit={(e) => {
								e.preventDefault();
								promoteCourse(course);
							}}
						>
							<p class="font-bold text-institutional-navy">Ins nächste Schuljahr übernehmen</p>
							<p class="text-slate-600">
								Es entsteht ein neuer Kurs mit neuem Beitrittscode. Alle {course.members} Mitglieder wechseln
								mit ihren Konten dorthin; „{course.name}“ gilt danach als beendet. Bewertungsrunden
								bleiben beim alten Kurs.
							</p>
							<div class="flex flex-wrap gap-4">
								<label
									>Name des neuen Kurses<br /><input
										bind:value={promote.name}
										required
										maxlength="100"
										class="mt-1 border-slate-200"
										data-testid="promote-name"
									/></label
								>
								<label
									>Enddatum (optional)<br /><input
										type="date"
										bind:value={promote.endsOn}
										class="mt-1 border-slate-200"
									/></label
								>
							</div>
							<label class="flex items-start gap-2"
								><input type="checkbox" bind:checked={promote.keepProgress} class="mt-1" />
								<span
									><strong>Fortschritt und Punkte behalten</strong> – sonst werden Versuche, Fortschritt
									und Punkte aller Mitglieder gelöscht.</span
								></label
							>
							<label class="flex items-start gap-2"
								><input type="checkbox" bind:checked={promote.restartLeaderboard} class="mt-1" />
								<span
									><strong>Rangliste neu starten</strong> – sie zählt nur Punkte ab heute, bisherige Punkte
									bleiben erhalten.</span
								></label
							>
							<label class="flex items-start gap-2"
								><input type="checkbox" bind:checked={promote.keepReleases} class="mt-1" />
								<span
									><strong>Freigaben übernehmen</strong> – freigegebene Module und Aufgaben bleiben frei,
									Fristen beginnen neu. Sonst ist anfangs alles gesperrt.</span
								></label
							>
							<label class="flex items-start gap-2"
								><input type="checkbox" bind:checked={promote.copyCodePaths} class="mt-1" />
								<span
									><strong>CodePaths kopieren</strong> – die CodePaths von „{course.name}“ gibt es
									danach auch für den neuen Kurs.</span
								></label
							>
							<button
								class="rounded-[var(--radius-ui-sm)] bg-institutional-navy px-4 py-2 text-sm font-semibold text-white"
								data-testid="promote-confirm">Neuen Kurs anlegen und übernehmen</button
							>
						</form>
					{/if}
					<div class="mt-4 flex flex-col gap-2 border-t border-slate-100 pt-4">
						<PdfBundle
							url="/api/admin/courses/{course.id}/certificates"
							label="Teilnahmebestätigungen"
							testid="bundle-certificates"
						/>
						<PdfBundle
							url="/api/admin/courses/{course.id}/take-home"
							label="Mitnehm-Pakete"
							testid="bundle-take-home"
						/>
					</div>
				{/if}
			</article>
		{/each}
	</section>
</div>
