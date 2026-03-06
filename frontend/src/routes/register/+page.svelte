<script lang="ts">
	import { auth } from '$lib/auth.svelte';
	import { goto } from '$app/navigation';

	let username = $state('');
	let password = $state('');
	let confirmPassword = $state('');
	let error = $state('');
	let loading = $state(false);

	async function handleRegister(e: SubmitEvent) {
		e.preventDefault();

		if (password !== confirmPassword) {
			error = 'Passwörter stimmen nicht überein';
			return;
		}

		loading = true;
		error = '';

		try {
			const res = await fetch('/api/auth/register', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ username, password })
			});

			if (!res.ok) {
				const data = await res.json();
				throw new Error(data.message || 'Registrierung fehlgeschlagen');
			}

			const data = await res.json();
			auth.login(data.token, { username: data.username, role: data.role });
			goto('/modules');
		} catch (err: any) {
			error = err.message;
		} finally {
			loading = false;
		}
	}
</script>

<div class="mx-auto mt-12 max-w-lg">
	<div class="border border-slate-200 bg-white p-12 shadow-sm rounded-none">
		<div class="mb-12 text-center">
			<h1 class="font-sans text-3xl font-extrabold tracking-tight text-institutional-navy uppercase">
				Registrierung
			</h1>
			<p class="mt-3 text-[10px] font-bold tracking-[4px] text-slate-400 uppercase">
				Neuen Benutzerzugang erstellen
			</p>
		</div>

		{#if error}
			<div
				class="mb-10 border border-red-200 bg-red-50 p-6 font-mono text-xs text-red-600 rounded-none"
			>
				<span class="font-bold uppercase tracking-widest">Hinweis:</span> {error}
			</div>
		{/if}

		<form class="space-y-8" onsubmit={handleRegister}>
			<div>
				<label
					for="username"
					class="mb-3 block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
					>Nutzername</label
				>
				<input
					bind:value={username}
					type="text"
					id="username"
					required
					minlength="3"
					maxlength="30"
					class="w-full border-slate-200 bg-slate-50 px-5 py-4 transition-all outline-none focus:border-institutional-navy focus:ring-0 rounded-none"
					placeholder="max.mustermann"
				/>
			</div>

			<div>
				<label
					for="password"
					class="mb-3 block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
					>Passwort</label
				>
				<input
					bind:value={password}
					type="password"
					id="password"
					required
					minlength="6"
					class="w-full border-slate-200 bg-slate-50 px-5 py-4 transition-all outline-none focus:border-institutional-navy focus:ring-0 rounded-none"
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
					required
					class="w-full border-slate-200 bg-slate-50 px-5 py-4 transition-all outline-none focus:border-institutional-navy focus:ring-0 rounded-none"
				/>
			</div>

			<button
				type="submit"
				disabled={loading}
				class="w-full bg-institutional-gold py-5 font-sans text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:opacity-95 disabled:opacity-50 rounded-none"
			>
				{loading ? 'Konto wird erstellt...' : 'Registrieren'}
			</button>
		</form>

		<div class="mt-12 border-t border-slate-100 pt-10 text-center">
			<p class="text-sm text-slate-500">
				Bereits registriert? <a
					href="/login"
					class="font-bold text-institutional-navy transition-all hover:underline">Zum Login-Bereich</a
				>
			</p>
		</div>
	</div>
</div>
