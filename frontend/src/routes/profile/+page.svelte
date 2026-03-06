<script lang="ts">
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';

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

	async function fetchProfile() {
		try {
			const res = await auth.apiFetch('/api/users/profile');
			if (res.ok) {
				profile = await res.json();
				newDisplayName = profile?.displayName || '';
			}
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
			const res = await auth.apiFetch('/api/users/profile/display-name', {
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
		if (newPassword.length < 6) {
			error = 'Passwort muss mindestens 6 Zeichen lang sein.';
			return;
		}

		changingPassword = true;
		error = '';
		success = '';
		try {
			const res = await auth.apiFetch('/api/users/profile/password', {
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

	onMount(fetchProfile);
</script>

<div class="mx-auto max-w-4xl">
	<header class="mb-12 border-b border-slate-200 pb-8">
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Mein Profil
		</h1>
		<p class="mt-4 text-slate-500">Persönliche Einstellungen und Leistungsübersicht.</p>
	</header>

	{#if loading}
		<div class="flex justify-center py-24">
			<div class="h-12 w-12 border-4 border-slate-200 border-t-institutional-navy animate-spin"></div>
		</div>
	{:else if profile}
		<div class="space-y-12">
			<!-- Stats -->
			<div class="grid grid-cols-1 gap-6 md:grid-cols-2">
				<div class="border border-slate-200 bg-white p-10 text-center shadow-sm rounded-none">
					<p class="mb-3 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
						Gesamtpunktzahl
					</p>
					<p class="font-sans text-5xl font-black text-institutional-gold">{profile.totalPoints}</p>
				</div>
				<div class="border border-slate-200 bg-white p-10 text-center shadow-sm rounded-none">
					<p class="mb-3 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
						Abgeschlossene Aufgaben
					</p>
					<p class="font-sans text-5xl font-black text-institutional-navy">
						{profile.completedTasksCount}
					</p>
				</div>
			</div>

			<!-- Settings -->
			<div class="border border-slate-200 bg-white p-12 shadow-sm rounded-none">
				<h2 class="mb-10 font-sans text-sm font-black tracking-widest text-slate-400 uppercase">
					Benutzer-Einstellungen
				</h2>

				{#if error}
					<div
						class="mb-8 border border-rose-200 bg-rose-50 p-6 font-mono text-xs text-rose-700 rounded-none"
					>
						{error}
					</div>
				{/if}
				{#if success}
					<div
						class="mb-8 border border-green-200 bg-green-50 p-6 font-mono text-xs text-green-700 rounded-none"
					>
						{success}
					</div>
				{/if}

				<div class="space-y-10">
					<div>
						<label
							for="username"
							class="mb-3 block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
							>System-Nutzerkennung</label
						>
						<input
							type="text"
							id="username"
							value={profile.username}
							disabled
							class="w-full border-slate-100 bg-slate-50 px-5 py-4 font-mono text-sm text-slate-400 cursor-not-allowed rounded-none"
						/>
					</div>

					<div>
						<label
							for="displayName"
							class="mb-3 block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
							>Öffentlicher Anzeigename</label
						>
						<input
							bind:value={newDisplayName}
							type="text"
							id="displayName"
							class="w-full border-slate-200 bg-white px-5 py-4 font-sans text-sm font-bold text-institutional-navy outline-none focus:border-institutional-navy rounded-none transition-colors"
						/>
					</div>

					<div>
						<span
							class="mb-3 block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
							>Berechtigungsstufe</span
						>
						<span
							class="inline-flex border border-slate-200 bg-slate-50 px-4 py-2 font-mono text-[10px] font-black tracking-widest text-slate-600 uppercase rounded-none"
							>{profile.role}</span
						>
					</div>

					<button
						onclick={handleSave}
						disabled={saving || newDisplayName === profile.displayName}
						class="w-full bg-institutional-navy py-5 font-sans text-[11px] font-bold tracking-[3px] text-white uppercase shadow-lg transition-all hover:opacity-90 disabled:opacity-20 rounded-none"
					>
						{saving ? 'Synchronisierung...' : 'Profil-Daten aktualisieren'}
					</button>
				</div>
			</div>

			<!-- Password Security Section -->
			<div class="border border-slate-200 bg-white p-12 shadow-sm rounded-none">
				<h2 class="mb-10 font-sans text-sm font-black tracking-widest text-slate-400 uppercase">
					Sicherheits-Parameter
				</h2>

				<div class="space-y-10">
					<div>
						<label
							for="newPassword"
							class="mb-3 block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
							>Neues System-Passwort</label
						>
						<input
							bind:value={newPassword}
							type="password"
							id="newPassword"
							class="w-full border-slate-200 bg-white px-5 py-4 font-sans text-sm font-bold text-institutional-navy outline-none focus:border-institutional-navy rounded-none transition-colors"
						/>
					</div>

					<div>
						<label
							for="confirmPassword"
							class="mb-3 block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
							>Passwort bestätigen</label
						>
						<input
							bind:value={confirmPassword}
							type="password"
							id="confirmPassword"
							class="w-full border-slate-200 bg-white px-5 py-4 font-sans text-sm font-bold text-institutional-navy outline-none focus:border-institutional-navy rounded-none transition-colors"
						/>
					</div>

					<button
						onclick={handleChangePassword}
						disabled={changingPassword || !newPassword || !confirmPassword}
						class="w-full bg-slate-800 py-5 font-sans text-[11px] font-bold tracking-[3px] text-white uppercase shadow-lg transition-all hover:bg-slate-900 disabled:opacity-20 rounded-none"
					>
						{changingPassword ? 'Aktualisierung...' : 'System-Passwort überschreiben'}
					</button>
				</div>
			</div>
		</div>
	{/if}
</div>
