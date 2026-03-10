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
			<div
				class="max-w-4xl {mounted
					? 'animate-in fade-in slide-in-from-left-8 duration-700'
					: 'opacity-0'}"
			>
				<span
					class="mb-6 inline-block font-mono text-[10px] font-bold tracking-[5px] text-institutional-gold uppercase"
				>
					Berufsorientierungstag IT 2026
				</span>
				<h1
					class="mb-8 font-sans text-4xl leading-tight font-extrabold tracking-tight uppercase sm:text-7xl"
				>
					The Path to <span class="text-institutional-gold">Code</span>
				</h1>
				<p class="mb-12 max-w-2xl text-xl leading-relaxed text-slate-300">
					Willkommen zu deinem Deep-Dive in die Welt der Informationstechnik. Heute lernst du nicht
					aus Büchern, sondern arbeitest direkt an den Systemen, die unsere digitale Welt antreiben.
				</p>
				<div class="flex flex-wrap gap-6">
					{#if !auth.isAuthenticated}
						<a
							href="/login"
							class="rounded-none bg-institutional-gold px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:bg-amber-600 hover:shadow-xl"
						>
							Login
						</a>
						<a
							href="/register"
							class="rounded-none border-2 border-white/20 px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase transition-all hover:bg-white/10"
						>
							Account erstellen
						</a>
					{:else}
						<a
							href="/modules"
							class="rounded-none bg-institutional-gold px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:bg-amber-600 hover:shadow-xl"
						>
							Zu den Lernmodulen
						</a>
					{/if}
				</div>
			</div>
		</div>

		<!-- Decorative Element -->
		<div class="pointer-events-none absolute right-0 bottom-0 hidden opacity-10 lg:block">
			<svg
				class="h-[600px] w-[600px] translate-x-1/3 translate-y-1/4 text-institutional-gold"
				viewBox="0 0 200 200"
				fill="currentColor"
			>
				<path
					d="M44.7,-76.4C58.8,-69.2,71.8,-59.1,79.6,-46.2C87.4,-33.3,90.1,-17.7,89.1,-2.5C88.1,12.7,83.4,27.5,75.3,40.5C67.2,53.5,55.7,64.7,42.2,72.4C28.7,80.1,13.2,84.3,-1.8,87.4C-16.8,90.5,-33.6,92.5,-47.1,86.1C-60.6,79.7,-70.8,64.9,-78.3,49.8C-85.8,34.7,-90.6,19.3,-90.1,4.1C-89.6,-11.1,-83.8,-26.1,-75.3,-39.2C-66.8,-52.3,-55.6,-63.5,-42.5,-71.3C-29.4,-79.1,-14.7,-83.5,0.4,-84.2C15.5,-84.9,30.6,-83.6,44.7,-76.4Z"
					transform="translate(100 100)"
				/>
			</svg>
		</div>
	</section>

	<!-- Wer bin ich (Lehrperson) -->
	<section class="border-b border-slate-100 bg-white py-32">
		<div class="container-institutional text-center">
			<div
				class="mx-auto max-w-3xl {mounted
					? 'animate-in fade-in slide-in-from-bottom-8 duration-700'
					: 'opacity-0'}"
			>
				<span
					class="mb-4 inline-block font-mono text-[10px] font-bold tracking-[4px] text-slate-400 uppercase"
					>Hintergrund</span
				>
				<h2
					class="mb-12 font-sans text-5xl leading-none font-black tracking-tighter text-institutional-navy uppercase"
				>
					Wer bin ich?
				</h2>
				<div class="prose max-w-none text-slate-500 prose-slate">
					<ul class="inline-block w-full list-none space-y-6 p-0 text-left md:w-auto">
						<li class="flex items-start gap-4 text-lg">
							<span class="shrink-0 text-xl font-bold text-institutional-gold">//</span>
							<span>22 Jahre alt & Informatikstudent</span>
						</li>
						<li class="flex items-start gap-4 text-lg">
							<span class="shrink-0 text-xl font-bold text-institutional-gold">//</span>
							<span>Fokus auf moderne Softwareentwicklung & Systemarchitektur</span>
						</li>
					</ul>
				</div>
			</div>
		</div>
	</section>

	<!-- Beruf Informatiker Section -->
	<section class="bg-institutional-navy py-32 text-white">
		<div class="container-institutional">
			<div class="grid grid-cols-1 items-center gap-16 lg:grid-cols-2">
				<div>
					<span
						class="mb-4 inline-block font-mono text-[10px] font-bold tracking-[4px] text-institutional-gold uppercase"
						>Orientierung</span
					>
					<h2 class="mb-8 font-sans text-5xl leading-none font-black tracking-tighter uppercase">
						Beruf Informatiker,<br /><span class="text-institutional-gold">was ist das?</span>
					</h2>
					<p class="mb-8 text-xl leading-relaxed font-bold text-slate-300">
						Es gibt nicht "den einen" Beruf in der IT.
					</p>
					<p class="mb-10 leading-relaxed text-slate-400">
						Das Feld der Informatik ist so vielfältig wie kaum ein anderes. Ob du direkt an der
						Hardware arbeitest, komplexe Algorithmen entwickelst oder digitale Erlebnisse gestaltest
						– deine Möglichkeiten sind nahezu grenzenlos.
					</p>

					<div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
						{#each ['Embedded Systems', 'Internet Engineering', 'Medieninformatik', 'Wirtschaftsinformatik'] as field}
							<div
								class="border border-white/10 bg-white/5 p-4 font-mono text-[11px] tracking-wider uppercase"
							>
								<span class="mr-2 text-institutional-gold">/</span>
								{field}
							</div>
						{/each}
					</div>
				</div>

				<div class="border border-white/10 bg-white/5 p-10 backdrop-blur-sm">
					<h3
						class="mb-8 border-b border-white/10 pb-4 font-sans text-sm font-black tracking-[3px] text-institutional-gold uppercase"
					>
						Mögliche Arbeitsgebiete
					</h3>
					<ul class="space-y-4 font-sans text-xs text-slate-300">
						<li class="flex items-center gap-3">
							<div class="h-1 w-1 bg-institutional-gold"></div>
							Softwareentwicklung und Programmierung
						</li>
						<li class="flex items-center gap-3">
							<div class="h-1 w-1 bg-institutional-gold"></div>
							Konzeption und Realisierung von IT-Infrastrukturen
						</li>
						<li class="flex items-center gap-3">
							<div class="h-1 w-1 bg-institutional-gold"></div>
							Beratungstätigkeit in IT-Fragen
						</li>
						<li class="flex items-center gap-3">
							<div class="h-1 w-1 bg-institutional-gold"></div>
							Netzwerke und Betriebssysteme
						</li>
						<li class="flex items-center gap-3">
							<div class="h-1 w-1 bg-institutional-gold"></div>
							Eingebettete Systeme (Hardware & Software)
						</li>
						<li class="flex items-center gap-3">
							<div class="h-1 w-1 bg-institutional-gold"></div>
							Benutzerservice und Support
						</li>
						<li class="flex items-center gap-3">
							<div class="h-1 w-1 bg-institutional-gold"></div>
							Administration von Datenbanken
						</li>
						<li class="flex items-center gap-3">
							<div class="h-1 w-1 bg-institutional-gold"></div>
							Customizing von ERP-Systemen
						</li>
						<li class="flex items-center gap-3">
							<div class="h-1 w-1 bg-institutional-gold"></div>
							Entwicklung von Multimedia-Anwendungen
						</li>
						<li class="flex items-center gap-3">
							<div class="h-1 w-1 bg-institutional-gold"></div>
							Audio- und Video-Verarbeitung
						</li>
						<li class="flex items-center gap-3">
							<div class="h-1 w-1 bg-institutional-gold"></div>
							Entwicklung von Lernsystemen
						</li>
					</ul>
				</div>
			</div>
		</div>
	</section>

	<!-- Veranstaltungsübersicht -->
	<section class="relative bg-slate-50 py-32">
		<div class="container-institutional">
			<div class="mb-20 max-w-3xl">
				<span
					class="mb-4 inline-block font-mono text-[10px] font-bold tracking-[4px] text-institutional-gold uppercase"
					>Die Agenda</span
				>
				<h2
					class="mb-6 font-sans text-5xl leading-none font-black tracking-tighter text-institutional-navy uppercase"
				>
					Veranstaltungs-Übersicht
				</h2>
				<p class="text-lg leading-relaxed text-slate-500">
					Der Berufsorientierungstag im Bereich IT. Hier lernt ihr den Berufsbereich Informatiker
					kennen. In der Lernplattform werden viele verschiedene Themen beleuchtet.
				</p>
			</div>

			<div class="grid grid-cols-1 gap-8 md:grid-cols-2 lg:grid-cols-3">
				<div
					class="group border border-slate-200 bg-white p-10 shadow-sm transition-all duration-300 hover:border-institutional-navy hover:shadow-xl"
				>
					<div
						class="mb-8 flex h-16 w-16 items-center justify-center bg-slate-50 text-3xl transition-transform group-hover:scale-110"
					>
						💾
					</div>
					<h3
						class="mb-4 font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase"
					>
						Grundlagen & Logik
					</h3>
					<p class="leading-relaxed text-slate-500">
						Verständnis für binäre Systeme, digitale Logikgatter und die Architektur von Prozessoren
						als Basis jeder Software.
					</p>
				</div>
				<div
					class="group border border-slate-200 bg-white p-10 shadow-sm transition-all duration-300 hover:border-institutional-navy hover:shadow-xl"
				>
					<div
						class="mb-8 flex h-16 w-16 items-center justify-center bg-slate-50 text-3xl transition-transform group-hover:scale-110"
					>
						🛠️
					</div>
					<h3
						class="mb-4 font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase"
					>
						Software Engineering
					</h3>
					<p class="leading-relaxed text-slate-500">
						Einstieg in Python. Wir lösen reale Probleme durch Algorithmen und lernen saubere
						Code-Strukturen kennen.
					</p>
				</div>
				<div
					class="group border border-slate-200 bg-white p-10 shadow-sm transition-all duration-300 hover:border-institutional-navy hover:shadow-xl"
				>
					<div
						class="mb-8 flex h-16 w-16 items-center justify-center bg-slate-50 text-3xl transition-transform group-hover:scale-110"
					>
						🔒
					</div>
					<h3
						class="mb-4 font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase"
					>
						Cyber Security
					</h3>
					<p class="leading-relaxed text-slate-500">
						Sicherheitslücken identifizieren, Kryptografie anwenden und verstehen, wie man Systeme
						gegen Angriffe härtet.
					</p>
				</div>
			</div>
		</div>
	</section>

	<!-- Geplanter Ablauf (Timeline) -->
	<section class="overflow-hidden bg-white py-32">
		<div class="container-institutional">
			<div class="mb-24 text-center">
				<span
					class="mb-4 inline-block font-mono text-[10px] font-bold tracking-[4px] text-slate-400 uppercase"
					>Chronologie</span
				>
				<h2
					class="font-sans text-5xl leading-none font-black tracking-tighter text-institutional-navy uppercase"
				>
					Geplanter Ablauf
				</h2>
			</div>

			<div class="relative mx-auto max-w-5xl">
				<!-- Vertical Line -->
				<div
					class="absolute top-0 bottom-0 left-4 w-[2px] -translate-x-1/2 bg-slate-100 md:left-1/2"
				></div>

				<div class="relative space-y-24">
					<!-- Step 1 -->
					<div class="group relative flex flex-col items-center gap-8 md:flex-row md:gap-12">
						<div class="order-2 ml-12 text-left md:order-1 md:ml-0 md:w-1/2 md:text-right">
							<h3
								class="font-sans text-2xl leading-tight font-black text-institutional-navy uppercase"
							>
								Vorstellung und Theorie
							</h3>
							<p class="mt-4 text-sm leading-relaxed text-slate-500">
								Einführung in das Programm, Kennenlernen der Plattform und theoretische Grundlagen
								der modernen Informationstechnik.
							</p>
						</div>
						<div
							class="absolute left-4 z-10 flex h-10 w-10 -translate-x-1/2 items-center justify-center border-4 border-white bg-institutional-navy font-mono font-bold text-white shadow-lg md:left-1/2 md:h-12 md:w-12"
						>
							01
						</div>
						<div class="order-3 md:w-1/2"></div>
					</div>

					<!-- Step 2 -->
					<div class="group relative flex flex-col items-center gap-8 md:flex-row md:gap-12">
						<div class="order-1 hidden md:block md:w-1/2"></div>
						<div
							class="absolute left-4 z-10 flex h-10 w-10 -translate-x-1/2 items-center justify-center border-4 border-white bg-institutional-navy font-mono font-bold text-white shadow-lg md:left-1/2 md:h-12 md:w-12"
						>
							02
						</div>
						<div class="order-2 ml-12 text-left md:order-3 md:ml-0 md:w-1/2">
							<h3
								class="font-sans text-2xl leading-tight font-black text-institutional-navy uppercase"
							>
								Block 1: Grundlagen
							</h3>
							<p class="mt-4 text-sm leading-relaxed text-slate-500">
								Einstieg in die Praxis. Fokus auf algorithmisches Denken, Hardware-Logik und die
								erste Jagd nach Leaderboard-Punkten.
							</p>
						</div>
					</div>

					<!-- Step 3 -->
					<div class="group relative flex flex-col items-center gap-8 md:flex-row md:gap-12">
						<div class="order-2 ml-12 text-left md:order-1 md:ml-0 md:w-1/2 md:text-right">
							<h3
								class="font-sans text-2xl leading-tight font-black text-institutional-navy uppercase"
							>
								Block n: Freies Praxisarbeiten
							</h3>
							<p class="mt-4 text-sm leading-relaxed text-slate-500">
								Individuelle Schwerpunkte und finale Herausforderungen. Anwendung des gelernten
								Wissens auf reale IT-Szenarien.
							</p>
						</div>
						<div
							class="absolute left-4 z-10 flex h-10 w-10 -translate-x-1/2 items-center justify-center border-4 border-white bg-slate-800 font-mono font-bold text-white shadow-lg md:left-1/2 md:h-12 md:w-12"
						>
							...
						</div>
						<div class="order-3 md:w-1/2"></div>
					</div>

					<!-- Step 4 -->
					<div class="group relative flex flex-col items-center gap-8 md:flex-row md:gap-12">
						<div class="order-1 hidden md:block md:w-1/2"></div>
						<div
							class="absolute left-4 z-10 flex h-10 w-10 -translate-x-1/2 items-center justify-center border-4 border-white bg-institutional-gold font-mono font-bold text-white shadow-lg md:left-1/2 md:h-12 md:w-12"
						>
							⭐
						</div>
						<div class="order-2 ml-12 text-left md:order-3 md:ml-0 md:w-1/2">
							<h3
								class="font-sans text-2xl leading-tight font-black text-institutional-navy uppercase"
							>
								Zusammenfassung und Zeugnisse
							</h3>
							<p class="mt-4 text-sm leading-relaxed text-slate-500">
								Zusammenfassung der Ergebnisse, Feedback-Runde und feierliche Siegerehrung der
								punktbesten Teilnehmer.
							</p>
						</div>
					</div>
				</div>
			</div>
		</div>
	</section>

	<!-- Certificate & Grading Section -->
	<section class="border-t border-slate-200 bg-slate-50 py-32">
		<div class="container-institutional">
			<div class="grid grid-cols-1 items-center gap-20 lg:grid-cols-2">
				<div>
					<span
						class="mb-4 inline-block font-mono text-[10px] font-bold tracking-[4px] text-slate-400 uppercase"
						><span class="text-[8px] opacity-60">Leistungsnachweis</span></span
					>
					<h2
						class="mb-8 font-sans text-5xl leading-none font-black tracking-tighter text-institutional-navy uppercase"
					>
						Dein <span class="text-3xl text-gray-300">freiwilliger</span> Leistungsnachweis
					</h2>
					<p class="mb-10 text-lg leading-relaxed text-slate-500">
						Am Ende des Berufsorientierungstages erhältst du ein individuelles Zeugnis als
						PDF-Dokument. Dieses Dokument bescheinigt dir deine erfolgreiche Teilnahme und
						schlüsselt detailliert auf, welche Kompetenzen du in den verschiedenen IT-Modulen
						erworben hast.
					</p>
				</div>

				<div class="relative overflow-hidden border border-slate-200 bg-white p-10 shadow-2xl">
					<div
						class="absolute top-0 right-0 h-32 w-32 translate-x-16 -translate-y-16 -rotate-45 bg-institutional-gold/5"
					></div>
					<h3
						class="mb-8 border-b border-slate-100 pb-4 font-sans text-xs font-black tracking-[3px] text-slate-400 uppercase"
					>
						Notenspiegel (IHK-Standard)
					</h3>

					<div class="space-y-4 font-mono">
						{#each [{ range: '100% - 92%', label: 'Sehr Gut', grade: '1' }, { range: '91% - 81%', label: 'Gut', grade: '2' }, { range: '80% - 67%', label: 'Befriedigend', grade: '3' }, { range: '66% - 50%', label: 'Ausreichend', grade: '4' }, { range: '49% - 30%', label: 'Mangelhaft', grade: '5' }, { range: '< 30%', label: 'Ungenügend', grade: '6' }] as level}
							<div class="grid grid-cols-3 items-center border-b border-slate-50 pb-2 text-[11px]">
								<span class="text-slate-400">{level.range}</span>
								<span class="text-center font-bold text-institutional-navy uppercase"
									>{level.label}</span
								>
								<span class="text-right font-black text-institutional-gold">Note {level.grade}</span
								>
							</div>
						{/each}
					</div>
				</div>
			</div>
		</div>
	</section>

	<!-- Call to Action -->
	<section class="relative overflow-hidden bg-institutional-navy py-32 text-center text-white">
		<div class="pointer-events-none absolute top-0 left-0 h-full w-full opacity-5">
			<svg class="h-full w-full" fill="none" viewBox="0 0 100 100" preserveAspectRatio="none">
				<path d="M0 100 L100 0" stroke="currentColor" stroke-width="0.1" />
				<path d="M0 0 L100 100" stroke="currentColor" stroke-width="0.1" />
			</svg>
		</div>
		<div class="container-institutional relative z-10">
			<h2 class="mb-12 font-sans text-5xl leading-none font-black tracking-tighter uppercase">
				Starte jetzt mit dem <span class="text-institutional-gold">Lernen</span>
			</h2>
			{#if !auth.isAuthenticated}
				<div class="flex flex-col justify-center gap-6 sm:flex-row">
					<a
						href="/register"
						class="rounded-none bg-white px-12 py-6 text-[11px] font-bold tracking-[4px] text-institutional-navy uppercase transition-all hover:bg-slate-100 hover:shadow-2xl"
						>Jetzt registrieren</a
					>
					<a
						href="/login"
						class="rounded-none border-2 border-white/30 px-12 py-6 text-[11px] font-bold tracking-[4px] text-white uppercase transition-all hover:bg-white/10"
						>Anmelden</a
					>
				</div>
			{:else}
				<a
					href="/modules"
					class="inline-block rounded-none bg-institutional-gold px-16 py-6 text-[11px] font-bold tracking-[4px] text-white uppercase shadow-lg transition-all hover:bg-amber-600 hover:shadow-2xl"
					>Direkt zu den Aufgaben</a
				>
			{/if}
		</div>
	</section>
</div>

<style>
	:global(html) {
		scroll-behavior: smooth;
	}

	@keyframes fade-in {
		from {
			opacity: 0;
		}
		to {
			opacity: 1;
		}
	}

	@keyframes slide-in-from-left {
		from {
			transform: translateX(-20px);
			opacity: 0;
		}
		to {
			transform: translateX(0);
			opacity: 1;
		}
	}

	@keyframes slide-in-from-bottom {
		from {
			transform: translateY(20px);
			opacity: 0;
		}
		to {
			transform: translateY(0);
			opacity: 1;
		}
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
