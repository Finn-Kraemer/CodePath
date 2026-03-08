<script lang="ts">
	import { auth } from '$lib/auth.svelte';
</script>

<div class="flex flex-col">
	<!-- Hero Section / Einführung -->
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
				<span class="mb-6 inline-block font-mono text-[10px] font-bold tracking-[5px] text-institutional-gold uppercase">
					IT-Projekttag 2026
				</span>
				<h1 class="mb-8 font-sans text-5xl font-extrabold tracking-tight sm:text-7xl uppercase">
					The Path to <br/><span class="text-institutional-gold">Code</span>
				</h1>
				<p class="mb-12 text-xl leading-relaxed text-slate-300 max-w-2xl">
					Willkommen zu deinem Deep-Dive in die Welt der Informationstechnik. Heute lernst du nicht aus Büchern, sondern arbeitest direkt an den Systemen, die unsere digitale Welt antreiben.
				</p>
				<div class="flex flex-wrap gap-6">
					{#if !auth.isAuthenticated}
						<a
							href="/login"
							class="bg-institutional-gold px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:bg-amber-600 rounded-none"
						>
							System-Login
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
							class="bg-institutional-gold px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:bg-amber-600 rounded-none"
						>
							Zu den Lernmodulen
						</a>
					{/if}
				</div>
			</div>
		</div>
	</section>

	<!-- Wer bin ich (Lehrperson) -->
	<section class="bg-white py-32 border-b border-slate-100">
		<div class="container-institutional text-center">
			<div class="max-w-3xl mx-auto">
				<span class="mb-4 inline-block font-mono text-[10px] font-bold tracking-[4px] text-slate-400 uppercase">Hintergrund</span>
				<h2 class="mb-12 font-sans text-5xl font-black text-institutional-navy uppercase tracking-tighter">Wer bin ich?</h2>
				<div class="prose prose-slate max-w-none text-slate-500">
					<ul class="space-y-6 list-none p-0 inline-block text-left">
						<li class="flex items-start gap-4 text-lg">
							<span class="text-institutional-gold font-bold text-xl">//</span>
							<span>22 Jahre alt & Informatikstudent</span>
						</li>
						<li class="flex items-start gap-4 text-lg">
							<span class="text-institutional-gold font-bold text-xl">//</span>
							<span>Fokus auf moderne Softwareentwicklung & Systemarchitektur</span>
						</li>
						<li class="flex items-start gap-4 text-lg">
							<span class="text-institutional-gold font-bold text-xl">//</span>
							<span>Praxisorientiertes Mentoring auf Augenhöhe</span>
						</li>
						<li class="flex items-start gap-4 text-lg">
							<span class="text-institutional-gold font-bold text-xl">//</span>
							<span>Unterstützung bei technischen Hürden im Workshop</span>
						</li>
					</ul>
				</div>
			</div>
		</div>
	</section>

	<!-- Veranstaltungsübersicht -->
	<section class="bg-slate-50 py-32">
		<div class="container-institutional">
			<div class="max-w-3xl mb-20">
				<span class="mb-4 inline-block font-mono text-[10px] font-bold tracking-[4px] text-institutional-gold uppercase">Die Agenda</span>
				<h2 class="font-sans text-5xl font-black text-institutional-navy uppercase tracking-tighter mb-6">Veranstaltungs-Übersicht</h2>
				<p class="text-slate-500 text-lg">Dieser Projekttag ist als Intensiv-Workshop konzipiert. Wir decken die gesamte Breite der modernen Informationstechnik ab – von der Hardware-Ebene bis zur Cloud-Infrastruktur.</p>
			</div>

			<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
				<div class="bg-white border border-slate-200 p-10 shadow-sm hover:border-institutional-navy transition-colors">
					<div class="h-12 w-12 bg-slate-50 flex items-center justify-center text-2xl mb-8">💾</div>
					<h3 class="font-sans text-lg font-bold text-institutional-navy uppercase mb-4 tracking-tight">Grundlagen & Logik</h3>
					<p class="text-sm text-slate-500 leading-relaxed">Verständnis für binäre Systeme, digitale Logikgatter und die Architektur von Prozessoren als Basis jeder Software.</p>
				</div>
				<div class="bg-white border border-slate-200 p-10 shadow-sm hover:border-institutional-navy transition-colors">
					<div class="h-12 w-12 bg-slate-50 flex items-center justify-center text-2xl mb-8">🛠️</div>
					<h3 class="font-sans text-lg font-bold text-institutional-navy uppercase mb-4 tracking-tight">Software Engineering</h3>
					<p class="text-sm text-slate-500 leading-relaxed">Einstieg in Python. Wir lösen reale Probleme durch Algorithmen und lernen saubere Code-Strukturen kennen.</p>
				</div>
				<div class="bg-white border border-slate-200 p-10 shadow-sm hover:border-institutional-navy transition-colors">
					<div class="h-12 w-12 bg-slate-50 flex items-center justify-center text-2xl mb-8">🔒</div>
					<h3 class="font-sans text-lg font-bold text-institutional-navy uppercase mb-4 tracking-tight">Cyber Security</h3>
					<p class="text-sm text-slate-500 leading-relaxed">Sicherheitslücken identifizieren, Kryptografie anwenden und verstehen, wie man Systeme gegen Angriffe härtet.</p>
				</div>
			</div>
		</div>
	</section>

	<!-- Geplanter Ablauf (Timeline) -->
	<section class="bg-white py-32 overflow-hidden">
		<div class="container-institutional">
			<div class="text-center mb-24">
				<span class="mb-4 inline-block font-mono text-[10px] font-bold tracking-[4px] text-slate-400 uppercase">Chronologie</span>
				<h2 class="font-sans text-5xl font-black text-institutional-navy uppercase tracking-tighter">Geplanter Ablauf</h2>
			</div>

			<div class="relative max-w-5xl mx-auto">
				<!-- Vertical Line -->
				<div class="absolute left-1/2 top-0 bottom-0 w-[1px] bg-slate-200 hidden md:block"></div>

				<div class="space-y-24">
					<!-- Step 1 -->
					<div class="relative flex flex-col md:flex-row items-center gap-12 group">
						<div class="md:w-1/2 text-center md:text-right order-2 md:order-1">
							<h3 class="font-sans text-2xl font-black text-institutional-navy uppercase">Vorstellung und Theorie</h3>
							<p class="mt-4 text-slate-500 text-sm leading-relaxed">Einführung in das Programm, Kennenlernen der Plattform und theoretische Grundlagen der modernen Informationstechnik.</p>
						</div>
						<div class="relative z-10 flex h-12 w-12 items-center justify-center bg-institutional-navy text-white font-mono font-bold order-1 md:order-2 shadow-lg">01</div>
						<div class="md:w-1/2 order-3"></div>
					</div>

					<!-- Step 2 -->
					<div class="relative flex flex-col md:flex-row items-center gap-12 group">
						<div class="md:w-1/2 order-1"></div>
						<div class="relative z-10 flex h-12 w-12 items-center justify-center bg-institutional-navy text-white font-mono font-bold order-1 md:order-2 shadow-lg">02</div>
						<div class="md:w-1/2 text-center md:text-left order-2 md:order-3">
							<h3 class="font-sans text-2xl font-black text-institutional-navy uppercase">Block 1: Grundlagen</h3>
							<p class="mt-4 text-slate-500 text-sm leading-relaxed">Einstieg in die Praxis. Fokus auf algorithmisches Denken, Hardware-Logik und die erste Jagd nach Leaderboard-Punkten.</p>
						</div>
					</div>

					<!-- Step 3 -->
					<div class="relative flex flex-col md:flex-row items-center gap-12 group">
						<div class="md:w-1/2 text-center md:text-right order-2 md:order-1">
							<h3 class="font-sans text-2xl font-black text-institutional-navy uppercase">Block 2: Vertiefung</h3>
							<p class="mt-4 text-slate-500 text-sm leading-relaxed">Fortgeschrittene Themen wie Datenmodellierung, Web-Technologien und Systemarchitektur in intensiven Arbeitsphasen.</p>
						</div>
						<div class="relative z-10 flex h-12 w-12 items-center justify-center bg-institutional-navy text-white font-mono font-bold order-1 md:order-2 shadow-lg">03</div>
						<div class="md:w-1/2 order-3"></div>
					</div>

					<!-- Step 4 -->
					<div class="relative flex flex-col md:flex-row items-center gap-12 group">
						<div class="md:w-1/2 order-1"></div>
						<div class="relative z-10 flex h-12 w-12 items-center justify-center bg-slate-800 text-white font-mono font-bold order-1 md:order-2 shadow-lg">...</div>
						<div class="md:w-1/2 text-center md:text-left order-2 md:order-3">
							<h3 class="font-sans text-2xl font-black text-institutional-navy uppercase">Block n: Praxis-Transfer</h3>
							<p class="mt-4 text-slate-500 text-sm leading-relaxed">Individuelle Schwerpunkte und finale Herausforderungen. Anwendung des gelernten Wissens auf reale IT-Szenarien.</p>
						</div>
					</div>

					<!-- Step 5 -->
					<div class="relative flex flex-col md:flex-row items-center gap-12 group">
						<div class="md:w-1/2 text-center md:text-right order-2 md:order-1">
							<h3 class="font-sans text-2xl font-black text-institutional-navy uppercase">Debriefing & Awarding</h3>
							<p class="mt-4 text-slate-500 text-sm leading-relaxed">Zusammenfassung der Ergebnisse, Feedback-Runde und feierliche Siegerehrung der punktbesten Teilnehmer.</p>
						</div>
						<div class="relative z-10 flex h-12 w-12 items-center justify-center bg-institutional-gold text-white font-mono font-bold order-1 md:order-2 shadow-lg">🏆</div>
						<div class="md:w-1/2 order-3"></div>
					</div>
				</div>
			</div>
		</div>
	</section>

	<!-- Certificate & Grading Section -->
	<section class="bg-slate-50 py-32 border-t border-slate-200">
		<div class="container-institutional">
			<div class="grid grid-cols-1 lg:grid-cols-2 gap-20 items-center">
				<div>
					<span class="mb-4 inline-block font-mono text-[10px] font-bold tracking-[4px] text-institutional-gold uppercase">Erfolg messbar machen</span>
					<h2 class="font-sans text-5xl font-black text-institutional-navy uppercase tracking-tighter mb-8 text-balance">Dein offizieller Leistungsnachweis</h2>
					<p class="text-slate-500 text-lg leading-relaxed mb-8">
						Am Ende des Projekttages erhältst du ein individuelles Zeugnis als PDF-Dokument. Dieses Dokument bescheinigt dir deine erfolgreiche Teilnahme und schlüsselt detailliert auf, welche Kompetenzen du in den verschiedenen IT-Modulen erworben hast.
					</p>
					
					<div class="space-y-6">
						<div class="flex gap-4">
							<div class="shrink-0 h-6 w-6 rounded-none border-2 border-institutional-navy flex items-center justify-center text-[10px] font-bold text-institutional-navy">1</div>
							<div>
								<p class="font-sans font-bold text-institutional-navy uppercase text-sm tracking-tight">Modulbasierte Bewertung</p>
								<p class="text-slate-500 text-sm">Nur vom Dozenten freigeschaltete Kursinhalte fließen in die Endnote ein.</p>
							</div>
						</div>
						<div class="flex gap-4">
							<div class="shrink-0 h-6 w-6 rounded-none border-2 border-institutional-navy flex items-center justify-center text-[10px] font-bold text-institutional-navy">2</div>
							<div>
								<p class="font-sans font-bold text-institutional-navy uppercase text-sm tracking-tight">Präzisions-Metrik</p>
								<p class="text-slate-500 text-sm">Gesperrte Aufgaben (durch zu viele Fehlversuche) zählen mit 0 Punkten und wirken sich auf die Note aus.</p>
							</div>
						</div>
					</div>
				</div>

				<div class="bg-white border border-slate-200 p-10 shadow-xl relative overflow-hidden">
					<div class="absolute top-0 right-0 w-32 h-32 bg-institutional-gold/5 -rotate-45 translate-x-16 -translate-y-16"></div>
					<h3 class="font-sans text-xs font-black text-slate-400 uppercase tracking-[3px] mb-8 border-b border-slate-100 pb-4">Notenspiegel (IHK-Standard)</h3>
					
					<div class="space-y-4 font-mono">
						{#each [
							{ range: '100% - 92%', label: 'Sehr Gut', grade: '1' },
							{ range: '< 92% - 81%', label: 'Gut', grade: '2' },
							{ range: '< 81% - 67%', label: 'Befriedigend', grade: '3' },
							{ range: '< 67% - 50%', label: 'Ausreichend', grade: '4' },
							{ range: '< 50% - 30%', label: 'Mangelhaft', grade: '5' },
							{ range: '< 30%', label: 'Ungenügend', grade: '6' }
						] as level}
							<div class="flex items-center justify-between text-[11px] border-b border-slate-50 pb-2">
								<span class="text-slate-400">{level.range}</span>
								<span class="font-bold text-institutional-navy uppercase">{level.label}</span>
								<span class="text-institutional-gold font-black">Note {level.grade}</span>
							</div>
						{/each}
					</div>

					<div class="mt-8 bg-slate-50 p-4 border-l-4 border-institutional-gold">
						<p class="text-[10px] text-slate-500 italic">Das Zeugnis kann jederzeit im persönlichen Profil unter "Abschluss-Dokumente" abgerufen werden, sobald die erste Aufgabe validiert wurde.</p>
					</div>
				</div>
			</div>
		</div>
	</section>

	<!-- Call to Action -->
	<section class="bg-institutional-navy py-24 text-white text-center">
		<div class="container-institutional">
			<h2 class="font-sans text-4xl font-extrabold uppercase mb-10 tracking-tight">Bereit für die Herausforderung?</h2>
			{#if !auth.isAuthenticated}
				<div class="flex justify-center gap-6">
					<a href="/register" class="bg-white text-institutional-navy px-12 py-5 text-[11px] font-bold tracking-[3px] uppercase hover:bg-slate-100 transition-all rounded-none">Jetzt registrieren</a>
					<a href="/login" class="border border-white/30 text-white px-12 py-5 text-[11px] font-bold tracking-[3px] uppercase hover:bg-white/10 transition-all rounded-none">Anmelden</a>
				</div>
			{:else}
				<a href="/modules" class="bg-institutional-gold text-white px-12 py-5 text-[11px] font-bold tracking-[3px] uppercase hover:bg-amber-600 transition-all rounded-none shadow-lg">Direkt zu den Aufgaben</a>
			{/if}
		</div>
	</section>
</div>
