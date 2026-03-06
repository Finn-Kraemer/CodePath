<script lang="ts">
	import { auth } from '$lib/auth.svelte';
</script>

<div class="flex flex-col">
	<!-- Hero Section - Full width with sharp edges -->
	<section class="relative overflow-hidden bg-institutional-navy py-32 text-white lg:py-48">
		<div class="absolute inset-0 opacity-10">
			<svg class="h-full w-full" fill="none" viewBox="0 0 400 400">
				<defs>
					<pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
						<path d="M 40 0 L 0 0 0 40" fill="none" stroke="currentColor" stroke-width="1" />
					</pattern>
				</defs>
				<rect width="100%" height="100%" fill="url(#grid)" />
			</svg>
		</div>

		<div class="container-institutional relative">
			<div class="max-w-4xl">
				<h1 class="mb-8 font-sans text-5xl font-extrabold tracking-tight sm:text-7xl">
					Entdecke die Welt der IT-Berufe
				</h1>
				<p class="mb-12 text-xl leading-relaxed text-slate-300">
					Löse echte Aufgaben, schreibe deinen ersten Code und finde heraus welcher IT-Beruf zu dir passt – in nur einem Schultag.
				</p>
				<div class="flex flex-wrap gap-6">
					{#if !auth.isAuthenticated}
						<a
							href="/login"
							class="bg-institutional-gold px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:opacity-90 rounded-none"
						>
							Jetzt einloggen
						</a>
						<a
							href="/register"
							class="border-2 border-white/20 px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase transition-all hover:bg-white/10 rounded-none"
						>
							Account erstellen
						</a>
					{:else}
						<a
							href="/modules"
							class="bg-institutional-gold px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:opacity-90 rounded-none"
						>
							Weiter zu den Modulen
						</a>
					{/if}
				</div>
			</div>
		</div>
	</section>

	<!-- Stats Section -->
	<section class="border-b border-slate-200 bg-slate-50 py-20">
		<div class="container-institutional">
			<div class="grid grid-cols-1 gap-16 text-center md:grid-cols-3">
				<div class="flex flex-col gap-3">
					<span class="font-sans text-6xl font-black text-institutional-navy">7</span>
					<span class="text-[10px] font-bold tracking-[4px] text-slate-400 uppercase"
						>IT-Berufsfelder</span
					>
				</div>
				<div class="flex flex-col gap-3">
					<span class="font-sans text-6xl font-black text-institutional-navy">50+</span>
					<span class="text-[10px] font-bold tracking-[4px] text-slate-400 uppercase"
						>Praxisaufgaben</span
					>
				</div>
				<div class="flex flex-col gap-3">
					<span class="font-sans text-6xl font-black text-institutional-navy">🏆</span>
					<span class="text-[10px] font-bold tracking-[4px] text-slate-400 uppercase"
						>Live-Rangliste</span
					>
				</div>
			</div>
		</div>
	</section>

	<!-- What awaits you Section -->
	<section class="bg-white py-32">
		<div class="container-institutional">
			<div class="border border-slate-200 p-12 lg:p-24 rounded-none">
				<div class="grid grid-cols-1 items-center gap-20 lg:grid-cols-2">
					<div>
						<span class="mb-6 inline-block text-[10px] font-bold tracking-[5px] text-institutional-gold uppercase">
							Was dich erwartet
						</span>
						<h2 class="mb-8 font-sans text-4xl font-extrabold text-institutional-navy">
							Kein Frontalunterricht – du machst einfach.
						</h2>
						<p class="mb-10 text-lg leading-relaxed text-slate-500">
							Schreibe deinen ersten <strong>Python-Code</strong>, knacke eine verschlüsselte Nachricht, entwirf eine Datenbank oder plane ein Schulnetzwerk. Jede Aufgabe bringt dir Punkte – und einen echten Einblick in einen IT-Beruf.
						</p>
						<div class="flex flex-wrap gap-6">
							<div class="border border-slate-200 bg-slate-50 px-6 py-3 font-mono text-[10px] text-slate-500 uppercase tracking-widest rounded-none">
								🐍 Programmierung
							</div>
							<div class="border border-slate-200 bg-slate-50 px-6 py-3 font-mono text-[10px] text-slate-500 uppercase tracking-widest rounded-none">
								🔒 IT-Sicherheit
							</div>
							<div class="border border-slate-200 bg-slate-50 px-6 py-3 font-mono text-[10px] text-slate-500 uppercase tracking-widest rounded-none">
								🤖 Künstliche Intelligenz
							</div>
							<div class="border border-slate-200 bg-slate-50 px-6 py-3 font-mono text-[10px] text-slate-500 uppercase tracking-widest rounded-none">
								🌐 Web-Entwicklung
							</div>
							<div class="border border-slate-200 bg-slate-50 px-6 py-3 font-mono text-[10px] text-slate-500 uppercase tracking-widest rounded-none">
								🗄️ Datenbanken
							</div>
							<div class="border border-slate-200 bg-slate-50 px-6 py-3 font-mono text-[10px] text-slate-500 uppercase tracking-widest rounded-none">
								☁️ Cloud & Netzwerke
							</div>
						</div>
					</div>
					<div class="grid grid-cols-2 gap-6">
						<div class="flex flex-col justify-center items-center h-64 border border-slate-100 bg-slate-50 shadow-inner rounded-none gap-3">
							<span class="text-4xl">💻</span>
							<span class="text-[10px] font-bold tracking-[3px] text-slate-400 uppercase text-center">Code direkt<br/>im Browser</span>
						</div>
						<div class="mt-12 flex flex-col justify-center items-center h-64 border border-slate-100 bg-slate-50 shadow-inner rounded-none gap-3">
							<span class="text-4xl">🏆</span>
							<span class="text-[10px] font-bold tracking-[3px] text-slate-400 uppercase text-center">Punkte sammeln<br/>& gewinnen</span>
						</div>
					</div>
				</div>
			</div>
		</div>
	</section>

	<!-- Module Teaser -->
	<section class="bg-slate-50 py-32">
		<div class="container-institutional">
			<div class="mb-16 text-center">
				<h2 class="font-sans text-4xl font-extrabold text-institutional-navy">Deine Module heute</h2>
				<p class="mt-4 text-slate-500 tracking-wide">Deine Lehrkraft schaltet die Module der Reihe nach frei. Los geht's!</p>
			</div>
			<div class="mx-auto max-w-4xl space-y-6">
				{#each [
					{ emoji: '🐍', title: 'Programmierung', desc: 'Variablen, Schleifen, Funktionen – schreibe deinen ersten Python-Code.' },
					{ emoji: '🔒', title: 'IT-Sicherheit', desc: 'Knacke Passwörter, erkenne Phishing und baue einen Passwort-Checker.' },
					{ emoji: '🤖', title: 'Künstliche Intelligenz', desc: 'Wie lernt eine KI? Trainiere deinen eigenen Algorithmus.' }
				] as mod}
					<div class="flex items-center justify-between border border-slate-200 bg-white p-8 transition-all hover:border-institutional-gold shadow-sm group rounded-none">
						<div class="flex items-center gap-6">
							<div class="flex h-14 w-14 items-center justify-center bg-slate-50 text-2xl group-hover:bg-institutional-gold/10 rounded-none transition-colors">
								{mod.emoji}
							</div>
							<div>
								<span class="block text-lg font-bold text-institutional-navy">{mod.title}</span>
								<span class="text-sm text-slate-400 mt-1 block">{mod.desc}</span>
							</div>
						</div>
						<div class="text-right">
							<span class="block text-[10px] font-bold tracking-[2px] text-slate-400 uppercase">+ weitere Module</span>
							<span class="text-[10px] font-bold text-institutional-gold uppercase underline tracking-widest mt-1 block group-hover:text-amber-700">
								{#if !auth.isAuthenticated}Registrieren{:else}Öffnen{/if}
							</span>
						</div>
					</div>
				{/each}
			</div>
		</div>
	</section>
</div>
