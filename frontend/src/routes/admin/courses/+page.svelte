<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type CourseSummary } from '$lib/api';
	import { auth } from '$lib/auth.svelte';
	import { courseSelection } from '$lib/courses.svelte';

	type Account = { username: string; password: string };

	let courses = $state<CourseSummary[]>([]);
	let name = $state('');
	let endsOn = $state('');
	let accounts = $state<{ course: string; list: Account[] } | null>(null);
	let prefix = $state<Record<number, string>>({});
	let count = $state<Record<number, number>>({});
	let error = $state('');
	let message = $state('');

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

	async function downloadCertificates(course: CourseSummary) {
		const res = await auth.apiFetch(`/api/admin/courses/${course.id}/certificates`);
		if (!res.ok) {
			error = 'Export fehlgeschlagen.';
			return;
		}
		const url = URL.createObjectURL(await res.blob());
		const a = document.createElement('a');
		a.href = url;
		a.download = `Teilnahmebestaetigungen_${course.name}.zip`;
		a.click();
		URL.revokeObjectURL(url);
	}

	async function endCourse(course: CourseSummary) {
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
			class="mb-6 inline-block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
			>← Zurück zur Administration</a
		>
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
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
		class="flex flex-wrap items-end gap-4 border border-slate-200 bg-white p-6"
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
			class="bg-institutional-navy px-6 py-3 text-xs font-black tracking-[3px] text-white uppercase"
			>Kurs anlegen</button
		>
	</form>

	{#if accounts}
		<section class="border-2 border-institutional-gold bg-amber-50 p-6">
			<div class="mb-4 flex items-center justify-between">
				<h2 class="font-bold">
					Neue Konten für {accounts.course} – Passwörter werden nur jetzt angezeigt
				</h2>
				<button onclick={() => window.print()} class="border px-4 py-2 text-xs uppercase"
					>Drucken</button
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
				class="border border-slate-200 bg-white p-6 {course.status === 'ENDED' ? 'opacity-60' : ''}"
			>
				<div class="flex flex-wrap items-start justify-between gap-6">
					<div>
						<h2 class="text-xl font-bold text-institutional-navy">
							{course.name}
							{course.status === 'ENDED' ? '(beendet)' : ''}
						</h2>
						<p class="text-sm text-slate-500">
							{course.members} Mitglieder{course.endsOn
								? ` · endet am ${new Date(course.endsOn).toLocaleDateString('de-DE')}`
								: ''}
						</p>
					</div>
					{#if course.status === 'ACTIVE'}
						<div class="text-right">
							<p class="font-mono text-[10px] tracking-widest text-slate-400 uppercase">
								Beitrittscode
							</p>
							<p class="font-mono text-3xl font-black tracking-[6px] text-institutional-navy">
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
							class="border-2 border-institutional-navy px-4 py-2 text-xs font-black tracking-widest text-institutional-navy uppercase"
							>Konten anlegen</button
						>
						<span class="flex-1"></span>
						<button
							onclick={() => downloadCertificates(course)}
							class="border border-slate-300 px-4 py-2 text-xs uppercase">Zeugnisse (ZIP)</button
						>
						<button
							onclick={() => endCourse(course)}
							class="border border-rose-400 px-4 py-2 text-xs text-rose-600 uppercase"
							>Kurs beenden</button
						>
					</div>
				{/if}
			</article>
		{/each}
	</section>
</div>
