<script lang="ts">
	import { auth } from '$lib/auth.svelte';
    import { onMount } from 'svelte';

    let mounted = $state(false);
    onMount(() => {
        mounted = true;
    });
</script>

<div class="flex flex-col overflow-x-hidden">
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

		<div class="container-institutional relative z-10">
			<div class="max-w-4xl {mounted ? 'animate-in fade-in slide-in-from-left-8 duration-700' : 'opacity-0'}">
				<span class="mb-6 inline-block font-mono text-[10px] font-bold tracking-[5px] text-institutional-gold uppercase">
					Berufsorientierungstag IT 2026
				</span>
				<h1 class="mb-8 font-sans text-4xl font-extrabold tracking-tight sm:text-7xl uppercase leading-tight">
					The Path to <span class="text-institutional-gold">Code</span>
				</h1>
				<p class="mb-12 text-xl leading-relaxed text-slate-300 max-w-2xl">
					Willkommen zu deinem Deep-Dive in die Welt der Informationstechnik. Heute lernst du nicht aus Büchern, sondern arbeitest direkt an den Systemen, die unsere digitale Welt antreiben.
				</p>
				<div class="flex flex-wrap gap-6">
					{#if !auth.isAuthenticated}
						<a
							href="/login"
							class="bg-institutional-gold px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:bg-amber-600 hover:shadow-xl rounded-none"
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
							class="bg-institutional-gold px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:bg-amber-600 hover:shadow-xl rounded-none"
						>
							Zu den Lernmodulen
						</a>
					{/if}
				</div>
			</div>
		</div>
        
        <!-- Decorative Element -->
        <div class="absolute right-0 bottom-0 opacity-10 hidden lg:block pointer-events-none">
            <svg class="h-[600px] w-[600px] text-institutional-gold translate-x-1/3 translate-y-1/4" viewBox="0 0 200 200" fill="currentColor">
                <path d="M44.7,-76.4C58.8,-69.2,71.8,-59.1,79.6,-46.2C87.4,-33.3,90.1,-17.7,89.1,-2.5C88.1,12.7,83.4,27.5,75.3,40.5C67.2,53.5,55.7,64.7,42.2,72.4C28.7,80.1,13.2,84.3,-1.8,87.4C-16.8,90.5,-33.6,92.5,-47.1,86.1C-60.6,79.7,-70.8,64.9,-78.3,49.8C-85.8,34.7,-90.6,19.3,-90.1,4.1C-89.6,-11.1,-83.8,-26.1,-75.3,-39.2C-66.8,-52.3,-55.6,-63.5,-42.5,-71.3C-29.4,-79.1,-14.7,-83.5,0.4,-84.2C15.5,-84.9,30.6,-83.6,44.7,-76.4Z" transform="translate(100 100)" />
            </svg>
        </div>
	</section>

	<!-- Wer bin ich (Lehrperson) -->
	<section class="bg-white py-32 border-b border-slate-100">
		<div class="container-institutional text-center">
			<div class="max-w-3xl mx-auto {mounted ? 'animate-in fade-in slide-in-from-bottom-8 duration-700' : 'opacity-0'}">
				<span class="mb-4 inline-block font-mono text-[10px] font-bold tracking-[4px] text-slate-400 uppercase">Hintergrund</span>
				<h2 class="mb-12 font-sans text-5xl font-black text-institutional-navy uppercase tracking-tighter leading-none">Wer bin ich?</h2>
				<div class="prose prose-slate max-w-none text-slate-500">
					<ul class="space-y-6 list-none p-0 inline-block text-left w-full md:w-auto">
						<li class="flex items-start gap-4 text-lg">
							<span class="text-institutional-gold font-bold text-xl shrink-0">//</span>
							<span>22 Jahre alt & Informatikstudent</span>
						</li>
						<li class="flex items-start gap-4 text-lg">
							<span class="text-institutional-gold font-bold text-xl shrink-0">//</span>
							<span>Fokus auf moderne Softwareentwicklung & Systemarchitektur</span>
						</li>
						<li class="flex items-start gap-4 text-lg">
							<span class="text-institutional-gold font-bold text-xl shrink-0">//</span>
							<span>Praxisorientiertes Mentoring auf Augenhöhe</span>
						</li>
						<li class="flex items-start gap-4 text-lg">
							<span class="text-institutional-gold font-bold text-xl shrink-0">//</span>
							<span>Unterstützung bei technischen Hürden im Workshop</span>
						</li>
					</ul>
				</div>
			</div>
		</div>
	</section>

	<!-- Veranstaltungsübersicht -->
	<section class="bg-slate-50 py-32 relative">
		<div class="container-institutional">
			<div class="max-w-3xl mb-20">
				<span class="mb-4 inline-block font-mono text-[10px] font-bold tracking-[4px] text-institutional-gold uppercase">Die Agenda</span>
				<h2 class="font-sans text-5xl font-black text-institutional-navy uppercase tracking-tighter mb-6 leading-none">Veranstaltungs-Übersicht</h2>
				<p class="text-slate-500 text-lg leading-relaxed">Dieser Berufsorientierungstag ist als Intensiv-Workshop konzipiert. Wir decken die gesamte Breite der modernen Informationstechnik ab – von der Hardware-Ebene bis zur Cloud-Infrastruktur.</p>
			</div>

			<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
				<div class="bg-white border border-slate-200 p-10 shadow-sm hover:border-institutional-navy hover:shadow-xl transition-all duration-300 group">
					<div class="h-16 w-16 bg-slate-50 flex items-center justify-center text-3xl mb-8 group-hover:scale-110 transition-transform">💾</div>
					<h3 class="font-sans text-xl font-bold text-institutional-navy uppercase mb-4 tracking-tight">Grundlagen & Logik</h3>
					<p class="text-slate-500 leading-relaxed">Verständnis für binäre Systeme, digitale Logikgatter und die Architektur von Prozessoren als Basis jeder Software.</p>
				</div>
				<div class="bg-white border border-slate-200 p-10 shadow-sm hover:border-institutional-navy hover:shadow-xl transition-all duration-300 group">
					<div class="h-16 w-16 bg-slate-50 flex items-center justify-center text-3xl mb-8 group-hover:scale-110 transition-transform">🛠️</div>
					<h3 class="font-sans text-xl font-bold text-institutional-navy uppercase mb-4 tracking-tight">Software Engineering</h3>
					<p class="text-slate-500 leading-relaxed">Einstieg in Python. Wir lösen reale Probleme durch Algorithmen und lernen saubere Code-Strukturen kennen.</p>
				</div>
				<div class="bg-white border border-slate-200 p-10 shadow-sm hover:border-institutional-navy hover:shadow-xl transition-all duration-300 group">
					<div class="h-16 w-16 bg-slate-50 flex items-center justify-center text-3xl mb-8 group-hover:scale-110 transition-transform">🔒</div>
					<h3 class="font-sans text-xl font-bold text-institutional-navy uppercase mb-4 tracking-tight">Cyber Security</h3>
					<p class="text-slate-500 leading-relaxed">Sicherheitslücken identifizieren, Kryptografie anwenden und verstehen, wie man Systeme gegen Angriffe härtet.</p>
				</div>
			</div>
		</div>
	</section>

	<!-- Geplanter Ablauf (Timeline) -->
	<section class="bg-white py-32 overflow-hidden">
		<div class="container-institutional">
			<div class="text-center mb-24">
				<span class="mb-4 inline-block font-mono text-[10px] font-bold tracking-[4px] text-slate-400 uppercase">Chronologie</span>
				<h2 class="font-sans text-5xl font-black text-institutional-navy uppercase tracking-tighter leading-none">Geplanter Ablauf</h2>
			</div>

			<div class="relative max-w-5xl mx-auto">
				<!-- Vertical Line -->
				<div class="absolute left-4 md:left-1/2 top-0 bottom-0 w-[2px] bg-slate-100 -translate-x-1/2"></div>

				<div class="space-y-24 relative">
					<!-- Step 1 -->
					<div class="relative flex flex-col md:flex-row items-center gap-8 md:gap-12 group">
						<div class="md:w-1/2 text-left md:text-right order-2 md:order-1 ml-12 md:ml-0">
							<h3 class="font-sans text-2xl font-black text-institutional-navy uppercase leading-tight">Vorstellung und Theorie</h3>
							<p class="mt-4 text-slate-500 text-sm leading-relaxed">Einführung in das Programm, Kennenlernen der Plattform und theoretische Grundlagen der modernen Informationstechnik.</p>
						</div>
						<div class="absolute left-4 md:left-1/2 z-10 flex h-10 w-10 md:h-12 md:w-12 -translate-x-1/2 items-center justify-center bg-institutional-navy text-white font-mono font-bold shadow-lg border-4 border-white">01</div>
						<div class="md:w-1/2 order-3"></div>
					</div>

					<!-- Step 2 -->
					<div class="relative flex flex-col md:flex-row items-center gap-8 md:gap-12 group">
						<div class="md:w-1/2 order-1 hidden md:block"></div>
						<div class="absolute left-4 md:left-1/2 z-10 flex h-10 w-10 md:h-12 md:w-12 -translate-x-1/2 items-center justify-center bg-institutional-navy text-white font-mono font-bold shadow-lg border-4 border-white">02</div>
						<div class="md:w-1/2 text-left order-2 md:order-3 ml-12 md:ml-0">
							<h3 class="font-sans text-2xl font-black text-institutional-navy uppercase leading-tight">Block 1: Grundlagen</h3>
							<p class="mt-4 text-slate-500 text-sm leading-relaxed">Einstieg in die Praxis. Fokus auf algorithmisches Denken, Hardware-Logik und die erste Jagd nach Leaderboard-Punkten.</p>
						</div>
					</div>

					<!-- Step 3 -->
					<div class="relative flex flex-col md:flex-row items-center gap-8 md:gap-12 group">
						<div class="md:w-1/2 text-left md:text-right order-2 md:order-1 ml-12 md:ml-0">
							<h3 class="font-sans text-2xl font-black text-institutional-navy uppercase leading-tight">Block 2: Vertiefung</h3>
							<p class="mt-4 text-slate-500 text-sm leading-relaxed">Fortgeschrittene Themen wie Datenmodellierung, Web-Technologien und Systemarchitektur in intensiven Arbeitsphasen.</p>
						</div>
						<div class="absolute left-4 md:left-1/2 z-10 flex h-10 w-10 md:h-12 md:w-12 -translate-x-1/2 items-center justify-center bg-institutional-navy text-white font-mono font-bold shadow-lg border-4 border-white">03</div>
						<div class="md:w-1/2 order-3"></div>
					</div>

					<!-- Step 4 -->
					<div class="relative flex flex-col md:flex-row items-center gap-8 md:gap-12 group">
						<div class="md:w-1/2 order-1 hidden md:block"></div>
						<div class="absolute left-4 md:left-1/2 z-10 flex h-10 w-10 md:h-12 md:w-12 -translate-x-1/2 items-center justify-center bg-slate-800 text-white font-mono font-bold shadow-lg border-4 border-white">...</div>
						<div class="md:w-1/2 text-left order-2 md:order-3 ml-12 md:ml-0">
							<h3 class="font-sans text-2xl font-black text-institutional-navy uppercase leading-tight">Block n: Praxis-Transfer</h3>
							<p class="mt-4 text-slate-500 text-sm leading-relaxed">Individuelle Schwerpunkte und finale Herausforderungen. Anwendung des gelernten Wissens auf reale IT-Szenarien.</p>
						</div>
					</div>

					<!-- Step 5 -->
					<div class="relative flex flex-col md:flex-row items-center gap-8 md:gap-12 group">
						<div class="md:w-1/2 text-left md:text-right order-2 md:order-1 ml-12 md:ml-0">
							<h3 class="font-sans text-2xl font-black text-institutional-navy uppercase leading-tight">Debriefing & Awarding</h3>
							<p class="mt-4 text-slate-500 text-sm leading-relaxed">Zusammenfassung der Ergebnisse, Feedback-Runde und feierliche Siegerehrung der punktbesten Teilnehmer.</p>
						</div>
						<div class="absolute left-4 md:left-1/2 z-10 flex h-10 w-10 md:h-12 md:w-12 -translate-x-1/2 items-center justify-center bg-institutional-gold text-white font-mono font-bold shadow-lg border-4 border-white">🏆</div>
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
					<h2 class="font-sans text-5xl font-black text-institutional-navy uppercase tracking-tighter mb-8 leading-none">Dein offizieller Leistungsnachweis</h2>
					<p class="text-slate-500 text-lg leading-relaxed mb-10">
						Am Ende des Berufsorientierungstages erhältst du ein individuelles Zeugnis als PDF-Dokument. Dieses Dokument bescheinigt dir deine erfolgreiche Teilnahme und schlüsselt detailliert auf, welche Kompetenzen du in den verschiedenen IT-Modulen erworben hast.
					</p>
					
					<div class="grid grid-cols-1 sm:grid-cols-2 gap-8">
						<div class="flex flex-col gap-4 p-6 bg-white border border-slate-200 shadow-sm">
							<div class="h-8 w-8 rounded-none border-2 border-institutional-navy flex items-center justify-center text-xs font-bold text-institutional-navy">1</div>
							<div>
								<p class="font-sans font-bold text-institutional-navy uppercase text-sm tracking-tight mb-2">Modulbasierte Bewertung</p>
								<p class="text-slate-500 text-xs leading-relaxed">Nur vom Dozenten freigeschaltete Kursinhalte fließen in die Endnote ein.</p>
							</div>
						</div>
						<div class="flex flex-col gap-4 p-6 bg-white border border-slate-200 shadow-sm">
							<div class="h-8 w-8 rounded-none border-2 border-institutional-navy flex items-center justify-center text-xs font-bold text-institutional-navy">2</div>
							<div>
								<p class="font-sans font-bold text-institutional-navy uppercase text-sm tracking-tight mb-2">Präzisions-Metrik</p>
								<p class="text-slate-500 text-xs leading-relaxed">Gesperrte Aufgaben (durch zu viele Fehlversuche) zählen mit 0 Punkten und wirken sich auf die Note aus.</p>
							</div>
						</div>
					</div>
				</div>

				<div class="bg-white border border-slate-200 p-10 shadow-2xl relative overflow-hidden">
					<div class="absolute top-0 right-0 w-32 h-32 bg-institutional-gold/5 -rotate-45 translate-x-16 -translate-y-16"></div>
					<h3 class="font-sans text-xs font-black text-slate-400 uppercase tracking-[3px] mb-8 border-b border-slate-100 pb-4">Notenspiegel (IHK-Standard)</h3>
					
					<div class="space-y-4 font-mono">
						{#each [
							{ range: '100% - 92%', label: 'Sehr Gut', grade: '1' },
							{ range: '91% - 81%', label: 'Gut', grade: '2' },
							{ range: '80% - 67%', label: 'Befriedigend', grade: '3' },
							{ range: '66% - 50%', label: 'Ausreichend', grade: '4' },
							{ range: '49% - 30%', label: 'Mangelhaft', grade: '5' },
							{ range: '< 30%', label: 'Ungenügend', grade: '6' }
						] as level}
							<div class="grid grid-cols-3 items-center text-[11px] border-b border-slate-50 pb-2">
								<span class="text-slate-400">{level.range}</span>
								<span class="font-bold text-institutional-navy uppercase text-center">{level.label}</span>
								<span class="text-institutional-gold font-black text-right">Note {level.grade}</span>
							</div>
						{/each}
					</div>

					<div class="mt-8 bg-slate-50 p-6 border-l-4 border-institutional-gold shadow-inner">
						<p class="text-[10px] text-slate-500 italic leading-relaxed">Das Zeugnis kann jederzeit im persönlichen Profil unter "Abschluss-Dokumente" abgerufen werden, sobald die erste Aufgabe validiert wurde.</p>
					</div>
				</div>
			</div>
		</div>
	</section>

	<!-- Call to Action -->
	<section class="bg-institutional-navy py-32 text-white text-center relative overflow-hidden">
        <div class="absolute top-0 left-0 w-full h-full opacity-5 pointer-events-none">
            <svg class="h-full w-full" fill="none" viewBox="0 0 100 100" preserveAspectRatio="none">
                <path d="M0 100 L100 0" stroke="currentColor" stroke-width="0.1" />
                <path d="M0 0 L100 100" stroke="currentColor" stroke-width="0.1" />
            </svg>
        </div>
		<div class="container-institutional relative z-10">
			<h2 class="font-sans text-5xl font-black uppercase mb-12 tracking-tighter leading-none">Bereit für die <span class="text-institutional-gold">Herausforderung?</span></h2>
			{#if !auth.isAuthenticated}
				<div class="flex flex-col sm:flex-row justify-center gap-6">
					<a href="/register" class="bg-white text-institutional-navy px-12 py-6 text-[11px] font-bold tracking-[4px] uppercase hover:bg-slate-100 hover:shadow-2xl transition-all rounded-none">Jetzt registrieren</a>
					<a href="/login" class="border-2 border-white/30 text-white px-12 py-6 text-[11px] font-bold tracking-[4px] uppercase hover:bg-white/10 transition-all rounded-none">Anmelden</a>
				</div>
			{:else}
				<a href="/modules" class="bg-institutional-gold text-white px-16 py-6 text-[11px] font-bold tracking-[4px] uppercase hover:bg-amber-600 transition-all hover:shadow-2xl rounded-none shadow-lg inline-block">Direkt zu den Aufgaben</a>
			{/if}
		</div>
	</section>
</div>

<style>
	:global(html) {
		scroll-behavior: smooth;
	}
    
    @keyframes fade-in {
        from { opacity: 0; }
        to { opacity: 1; }
    }
    
    @keyframes slide-in-from-left {
        from { transform: translateX(-20px); opacity: 0; }
        to { transform: translateX(0); opacity: 1; }
    }
    
    @keyframes slide-in-from-bottom {
        from { transform: translateY(20px); opacity: 0; }
        to { transform: translateY(0); opacity: 1; }
    }
    
    .animate-in {
        animation-fill-mode: forwards;
    }
    
    .fade-in {
        animation-name: fade-in;
    }
    
    .slide-in-from-left-8 {
        animation-name: slide-in-from-left;
    }
    
    .slide-in-from-bottom-8 {
        animation-name: slide-in-from-bottom;
    }
</style>
