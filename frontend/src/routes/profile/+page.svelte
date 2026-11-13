<script lang="ts">
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';
	import { api, type Compass } from '$lib/api';
	import CompassCard from '$lib/report/CompassCard.svelte';

	interface Profile {
		username: string;
		displayName: string;
		role: string;
		totalPoints: number;
		completedTasksCount: number;
	}

	let profile = $state<Profile | null>(null);
	let newDisplayName = $state('');
	let newPassword = $state('');
	let confirmPassword = $state('');
	let loading = $state(true);
	let saving = $state(false);
	let changingPassword = $state(false);
	let error = $state('');
	let success = $state('');
	let certificatesEnabled = $state(false);
	let compass = $state<Compass | null>(null);

	async function fetchProfile() {
		try {
			const [profileRes, certRes] = await Promise.all([
				auth.apiFetch('/api/users/profile'),
				auth.apiFetch('/api/common/settings/certificates')
			]);

			if (profileRes.ok) {
				profile = await profileRes.json();
				newDisplayName = profile?.displayName || '';
			}
			if (certRes.ok) {
				const data = await certRes.json();
				certificatesEnabled = data.enabled;
			}
			compass = await api<Compass>('/api/compass').catch(() => null);
		} catch (e) {
			error = 'Profil konnte nicht geladen werden';
		} finally {
			loading = false;
		}
	}

	async function handleSave() {
		saving = true;
		error = '';
		success = '';
		try {
			const res = await auth.apiFetch('/api/auth/me/display-name', {
				method: 'PATCH',
				body: JSON.stringify({ displayName: newDisplayName })
			});
			if (res.ok) {
				success = 'Profil erfolgreich aktualisiert.';
				if (profile) profile.displayName = newDisplayName;
			} else {
				throw new Error();
			}
		} catch (e) {
			error = 'Fehler bei der Profilaktualisierung.';
		} finally {
			saving = false;
		}
	}

	async function handleChangePassword() {
		if (newPassword !== confirmPassword) {
			error = 'Passwörter stimmen nicht überein.';
			return;
		}
		if (newPassword.length < 8) {
			error = 'Passwort muss mindestens 8 Zeichen lang sein.';
			return;
		}

		changingPassword = true;
		error = '';
		success = '';
		try {
			const res = await auth.apiFetch('/api/auth/me/password', {
				method: 'PATCH',
				body: JSON.stringify({ password: newPassword })
			});
			if (res.ok) {
				success = 'Passwort erfolgreich geändert.';
				newPassword = '';
				confirmPassword = '';
			} else {
				throw new Error();
			}
		} catch (e) {
			error = 'Fehler beim Ändern des Passworts.';
		} finally {
			changingPassword = false;
		}
	}

	async function handleDownloadReport() {
		error = '';
		success = '';
		try {
			const res = await auth.apiFetch('/api/reports/my-report');
			if (res.ok) {
				const blob = await res.blob();
				const url = window.URL.createObjectURL(blob);
				const a = document.createElement('a');
				a.href = url;
				a.download = `CodePath_Zeugnis_${profile?.username}.pdf`;
				document.body.appendChild(a);
				a.click();
				window.URL.revokeObjectURL(url);
				document.body.removeChild(a);
				success = 'Zeugnis wurde erfolgreich generiert.';
			} else {
				error = 'Zeugnis konnte nicht generiert werden.';
			}
		} catch (e) {
			error = 'Fehler beim Herunterladen.';
		}
	}

	onMount(fetchProfile);
</script>

<div class="mx-auto max-w-5xl">
	<header class="mb-6 flex items-center gap-4">
		<span
			class="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-indigo-100 text-2xl font-extrabold text-indigo-800"
			aria-hidden="true">{(profile?.username ?? '?').charAt(0).toUpperCase()}</span
		>
		<div>
			<h1 class="ui-title">
				{profile ? `Hallo ${profile.username}!` : 'Mein Profil'}
			</h1>
			<p class="mt-1 text-[15px] text-slate-600">Dein Fortschritt, dein Kompass und dein Konto.</p>
		</div>
	</header>

	{#if loading}
		<div class="flex justify-center py-24">
			<div
				class="h-12 w-12 animate-spin rounded-full border-4 border-slate-200 border-t-institutional-navy"
			></div>
		</div>
	{:else if profile}
		<div class="space-y-6">
			<!-- Stats -->
			<div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
				<div class="ui-card p-6">
					<p class="text-sm font-semibold text-slate-600">Punkte</p>
					<p class="mt-1 text-4xl font-extrabold tracking-tight text-institutional-gold">
						{profile.totalPoints}
					</p>
				</div>
				<div class="ui-card p-6">
					<p class="text-sm font-semibold text-slate-600">Abgeschlossene Aufgaben</p>
					<p class="mt-1 text-4xl font-extrabold tracking-tight text-institutional-navy">
						{profile.completedTasksCount}
					</p>
				</div>
			</div>

			{#if compass}
				<CompassCard {compass} />
			{/if}

			<!-- Mitnehm-Paket -->
			<section
				class="rounded-[var(--radius-ui)] bg-institutional-navy p-6 text-white md:p-8"
				aria-label="Mitnehm-Paket"
			>
				<h2 class="mb-2 text-xl font-bold">Mein Mitnehm-Paket</h2>
				<p class="mb-3 text-[15px] leading-relaxed text-slate-200">
					Dein Code, deine Ergebnisse, dein Berufe-Kompass und Links zum Weiterlernen – alles in
					einem PDF zum Behalten. Wenn der Kurs endet, wird dein Konto gelöscht. Lade dir das Paket
					vorher herunter.
				</p>
				<p class="mb-6 text-[13px] text-slate-400">
					Das PDF entsteht beim Herunterladen und wird auf dem Server nicht gespeichert.
				</p>
				<a
					href="/api/reports/take-home"
					download="Mitnehm-Paket_{profile.username}.pdf"
					class="ui-btn border-white bg-white text-institutional-navy hover:bg-amber-50"
					data-testid="take-home"
				>
					Mitnehm-Paket herunterladen (PDF)
				</a>
			</section>

			<!-- Report Card Download -->
			<div class="ui-card p-6 md:p-8">
				<h2 class="mb-2 text-xl font-bold">Abschluss-Dokumente</h2>
				<p class="mb-6 text-[15px] leading-relaxed text-slate-600">
					Laden Sie hier Ihren offiziellen Leistungsnachweis als PDF-Dokument herunter. Das Dokument
					enthält eine Übersicht Ihrer Leistungen in den einzelnen Modulen sowie eine automatisch
					berechnete Abschlussnote.
				</p>

				{#if certificatesEnabled || profile.role === 'ADMIN'}
					<button onclick={handleDownloadReport} class="ui-btn ui-btn-accent">
						<svg
							xmlns="http://www.w3.org/2000/svg"
							class="h-5 w-5"
							viewBox="0 0 24 24"
							fill="none"
							stroke="currentColor"
							stroke-width="2"
							stroke-linecap="round"
							stroke-linejoin="round"
						>
							<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
							<polyline points="7 10 12 15 17 10"></polyline>
							<line x1="12" y1="15" x2="12" y2="3"></line>
						</svg>
						Zeugnis herunterladen (PDF)
					</button>
				{:else}
					<div
						class="flex items-center gap-3 rounded-[var(--radius-ui-sm)] border border-dashed border-slate-300 bg-slate-50 p-4"
					>
						<span class="ui-chip">Noch gesperrt</span>
						<p class="text-sm text-slate-600">
							Offizielle Zeugnisse werden erst am Ende der Veranstaltung freigegeben.
						</p>
					</div>
				{/if}
			</div>

			<!-- Settings -->
			<div class=" ui-card p-6 md:p-8">
				<h2 class="mb-6 text-xl font-bold">Benutzer-Einstellungen</h2>

				{#if error}
					<div
						class="mb-6 rounded-[var(--radius-ui-sm)] border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700"
					>
						{error}
					</div>
				{/if}
				{#if success}
					<div
						class="mb-6 rounded-[var(--radius-ui-sm)] border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800"
					>
						{success}
					</div>
				{/if}

				<div class="space-y-5">
					<div>
						<label for="username" class="mb-1.5 block text-sm font-semibold text-slate-700"
							>System-Nutzerkennung</label
						>
						<input
							type="text"
							id="username"
							value={profile.username}
							disabled
							class="ed-input mono mt-0 cursor-not-allowed bg-slate-50 text-slate-500"
						/>
					</div>

					<div>
						<label for="displayName" class="mb-1.5 block text-sm font-semibold text-slate-700"
							>Öffentlicher Anzeigename</label
						>
						<input bind:value={newDisplayName} type="text" id="displayName" class="ed-input mt-0" />
					</div>

					<div>
						<span class="mb-1.5 block text-sm font-semibold text-slate-700">Berechtigungsstufe</span
						>
						<span
							class="inline-flex border border-slate-200 bg-slate-50 px-4 py-2 font-mono text-[11px] font-black tracking-wider text-slate-600 uppercase"
							>{profile.role}</span
						>
					</div>

					<button
						onclick={handleSave}
						disabled={saving || newDisplayName === profile.displayName}
						class="ui-btn ui-btn-primary w-full"
					>
						{saving ? 'Synchronisierung...' : 'Profil-Daten aktualisieren'}
					</button>
				</div>
			</div>

			<!-- Password Security Section -->
			<div class=" ui-card p-6 md:p-8">
				<h2 class="mb-6 text-xl font-bold">Sicherheits-Parameter</h2>

				<div class="space-y-5">
					<div>
						<label for="newPassword" class="mb-1.5 block text-sm font-semibold text-slate-700"
							>Neues System-Passwort</label
						>
						<input
							bind:value={newPassword}
							type="password"
							id="newPassword"
							class="ed-input mt-0"
						/>
					</div>

					<div>
						<label for="confirmPassword" class="mb-1.5 block text-sm font-semibold text-slate-700"
							>Passwort bestätigen</label
						>
						<input
							bind:value={confirmPassword}
							type="password"
							id="confirmPassword"
							class="ed-input mt-0"
						/>
					</div>

					<button
						onclick={handleChangePassword}
						disabled={changingPassword || !newPassword || !confirmPassword}
						class="ui-btn ui-btn-primary w-full"
					>
						{changingPassword ? 'Aktualisierung...' : 'System-Passwort überschreiben'}
					</button>
				</div>
			</div>
		</div>
	{/if}
</div>
