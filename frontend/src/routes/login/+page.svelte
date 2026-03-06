<script lang="ts">
	import { auth } from '$lib/auth.svelte';
	import { goto } from '$app/navigation';

	let username = $state('');
	let password = $state('');
	let error = $state('');
	let loading = $state(false);

	async function handleLogin(e: SubmitEvent) {
		e.preventDefault();
		loading = true;
		error = '';

		try {
			const res = await fetch('/api/auth/login', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ username, password })
			});

			if (!res.ok) {
				const data = await res.json();
				throw new Error(data.message || 'Login fehlgeschlagen');
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
				Login
			</h1>
			<p class="mt-3 text-[10px] font-bold tracking-[4px] text-slate-400 uppercase">
				CodePath
			</p>
		</div>

		{#if error}
			<div
				class="mb-10 border border-red-200 bg-red-50 p-6 font-mono text-xs text-red-600 rounded-none"
			>
				<span class="font-bold uppercase tracking-widest">Fehler:</span> {error}
			</div>
		{/if}

		<form class="space-y-8" onsubmit={handleLogin}>
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
					class="w-full border-slate-200 bg-slate-50 px-5 py-4 transition-all outline-none focus:border-institutional-navy focus:ring-0 rounded-none"
				/>
			</div>

			<button
				type="submit"
				disabled={loading}
				class="w-full bg-institutional-navy py-5 font-sans text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:opacity-95 disabled:opacity-50 rounded-none"
			>
				{loading ? 'Verifizierung...' : 'Anmelden'}
			</button>
		</form>

		<div class="mt-12 border-t border-slate-100 pt-10 text-center">
			<p class="text-sm text-slate-500">
				Noch kein Zugang? <a
					href="/register"
					class="font-bold text-institutional-gold transition-all hover:underline"
					>Konto beantragen</a
				>
			</p>
		</div>
	</div>

	<p class="mt-10 text-center font-mono text-[9px] tracking-[4px] text-slate-400 uppercase">
		Zertifizierte End-to-End Verschlüsselung
	</p>
</div>
