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
					Willkommen beim IT-Projekttag
				</h1>
				<p class="mb-12 text-xl leading-relaxed text-slate-300">
					Heute schlüpfst du in die Rollen echter IT-Profis. Von Softwareentwicklung über Netzwerksysteme bis hin zur Datensicherheit – lerne die Praxis kennen, ohne trockene Theorie.
				</p>
				<div class="flex flex-wrap gap-6">
					{#if !auth.isAuthenticated}
						<a
							href="/login"
							class="bg-institutional-gold px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:opacity-90 rounded-none"
						>
							System-Login
						</a>
						<a
							href="/register"
							class="border-2 border-white/20 px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase transition-all hover:bg-white/10 rounded-none"
						>
							Schüler-Account erstellen
						</a>
					{:else}
						<a
							href="/modules"
							class="bg-institutional-gold px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:opacity-90 rounded-none"
						>
							Zu den Lernmodulen
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
						>Inhaltsmodule</span
					>
				</div>
				<div class="flex flex-col gap-3">
					<span class="font-sans text-6xl font-black text-institutional-navy">Praxis</span>
					<span class="text-[10px] font-bold tracking-[4px] text-slate-400 uppercase"
						>Echter Programmcode</span
					>
				</div>
				<div class="flex flex-col gap-3">
					<span class="font-sans text-6xl font-black text-institutional-navy">🏆</span>
					<span class="text-[10px] font-bold tracking-[4px] text-slate-400 uppercase"
						>Live-Wettbewerb</span
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
							Ablauf des Tages
						</span>
						<h2 class="mb-8 font-sans text-4xl font-extrabold text-institutional-navy">
							Die Theorie ist kurz, die Praxis im Fokus.
						</h2>
						<p class="mb-10 text-lg leading-relaxed text-slate-500">
							Deine Lehrkraft leitet jedes neue Thema kurz ein und schaltet dann das entsprechende Modul auf dieser Plattform frei. Danach bist du gefragt: Du analysierst Probleme, reparierst Skripte und planst IT-Strukturen. Jede erfolgreich gelöste Aufgabe bringt dir Punkte auf der klasseninternen Rangliste.
						</p>
						<div class="flex flex-wrap gap-6">
							<div class="border border-slate-200 bg-slate-50 px-6 py-3 font-mono text-[10px] text-slate-500 uppercase tracking-widest rounded-none">
								Algorithmen (Python)
							</div>
							<div class="border border-slate-200 bg-slate-50 px-6 py-3 font-mono text-[10px] text-slate-500 uppercase tracking-widest rounded-none">
								Hardware & Logik
							</div>
							<div class="border border-slate-200 bg-slate-50 px-6 py-3 font-mono text-[10px] text-slate-500 uppercase tracking-widest rounded-none">
								Datenmodellierung (SQL)
							</div>
							<div class="border border-slate-200 bg-slate-50 px-6 py-3 font-mono text-[10px] text-slate-500 uppercase tracking-widest rounded-none">
								Netzwerktopologien
							</div>
							<div class="border border-slate-200 bg-slate-50 px-6 py-3 font-mono text-[10px] text-slate-500 uppercase tracking-widest rounded-none">
								Kryptografie & Security
							</div>
						</div>
					</div>
					<div class="grid grid-cols-2 gap-6">
						<div class="flex flex-col justify-center items-center h-64 border border-slate-100 bg-slate-50 shadow-inner rounded-none gap-3 p-6">
							<span class="text-4xl mb-4">💻</span>
							<span class="text-[10px] font-bold tracking-[3px] text-slate-600 uppercase text-center">Integrierter<br/>Code-Editor</span>
							<p class="text-xs text-slate-400 text-center mt-2">Du brauchst keine lokale Installation.</p>
						</div>
						<div class="mt-12 flex flex-col justify-center items-center h-64 border border-slate-100 bg-slate-50 shadow-inner rounded-none gap-3 p-6">
							<span class="text-4xl mb-4">📚</span>
							<span class="text-[10px] font-bold tracking-[3px] text-slate-600 uppercase text-center">Zentrales<br/>Wissens-Wiki</span>
							<p class="text-xs text-slate-400 text-center mt-2">Lies Konzepte jederzeit nach.</p>
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
				<h2 class="font-sans text-4xl font-extrabold text-institutional-navy">Einblick in die Themengebiete</h2>
				<p class="mt-4 text-slate-500 tracking-wide">Ein Querschnitt durch die fundamentalen Disziplinen der Informatik.</p>
			</div>
			<div class="mx-auto max-w-4xl space-y-6">
				{#each [
					{ emoji: '🐍', title: 'Programmierung', desc: 'Grundlagen der Logik: Bringe Computern bei, Aufgaben automatisiert und fehlerfrei mit Python zu erledigen.' },
					{ emoji: '🗄️', title: 'Datenbanken', desc: 'Informationsmanagement: Lerne, wie moderne Apps Millionen von Nutzerdaten effizient speichern und abfragen.' },
					{ emoji: '☁️', title: 'Cloud & Netzwerke', desc: 'Infrastruktur: Finde heraus, wie Datenpakete um die Welt reisen und wie Serverfarmen das Internet am Laufen halten.' },
					{ emoji: '🔒', title: 'IT-Sicherheit', desc: 'Schutz im digitalen Raum: Analysiere Angriffsvektoren wie Phishing und baue robuste Abwehrmechanismen auf.' }
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
							<span class="block text-[10px] font-bold tracking-[2px] text-slate-400 uppercase">Modul-Vorschau</span>
						</div>
					</div>
				{/each}
			</div>
		</div>
	</section>
</div>