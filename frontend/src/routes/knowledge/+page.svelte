<script lang="ts">
	let selectedCategory = $state('python');

	const content = {
		python: {
			title: 'Python Programmierung',
			icon: '🐍',
			topics: [
				{
					name: 'Variablen',
					text: 'Variablen sind wie Boxen, in denen man Daten speichern kann. Man gibt ihnen einen Namen und weist ihnen mit = einen Wert zu.',
					example: 'name = "Max"\nalter = 15\nist_bereit = True'
				},
				{
					name: 'Datentypen',
					text: 'Es gibt verschiedene Arten von Daten:\n- String (Text): "Hallo"\n- Integer (Ganzzahl): 42\n- Float (Kommazahl): 3.14\n- Boolean (Wahrheitswert): True oder False',
					example: ''
				},
				{
					name: 'Funktionen',
					text: 'Funktionen sind Code-Blöcke, die eine bestimmte Aufgabe erledigen. Sie starten mit "def" und können Werte mit "return" zurückgeben.',
					example: 'def verdopple(n):\n    return n * 2\n\nprint(verdopple(10)) # Gibt 20 aus'
				},
				{
					name: 'Schleifen (Loops)',
					text: 'Mit einer for-Schleife kannst du Code mehrmals ausführen.',
					example: 'for i in range(5):\n    print("Hallo") # Wird 5x gedruckt'
				}
			]
		},
		web: {
			title: 'Web-Entwicklung',
			icon: '🌐',
			topics: [
				{
					name: 'HTML Grundlagen',
					text: 'HTML strukturiert die Webseite. Alles wird in "Tags" geschrieben.',
					example: '<h1>Große Überschrift</h1>\n<p>Ein Textabsatz</p>\n<a href="url">Ein Link</a>'
				},
				{
					name: 'CSS (Styling)',
					text: 'CSS bestimmt das Aussehen (Farben, Abstände, Schriftarten).',
					example: 'h1 {\n    color: blue;\n    font-size: 24px;\n}'
				}
			]
		},
		db: {
			title: 'Datenbanken (SQL)',
			icon: '🗄️',
			topics: [
				{
					name: 'Was ist SQL?',
					text: 'SQL ist die Sprache, um mit Datenbanken zu sprechen. Die wichtigste Abfrage ist SELECT.',
					example: 'SELECT name, alter FROM schueler WHERE alter > 14;'
				},
				{
					name: 'Primärschlüssel (Primary Key)',
					text: 'Ein Feld, das jeden Datensatz in einer Tabelle eindeutig identifiziert (z.B. eine ID). Es darf niemals doppelt vorkommen.',
					example: ''
				}
			]
		},
		security: {
			title: 'IT-Sicherheit',
			icon: '🔒',
			topics: [
				{
					name: 'Sichere Passwörter',
					text: 'Ein Passwort sollte mindestens 12 Zeichen haben und Großbuchstaben, Kleinbuchstaben, Zahlen und Sonderzeichen mischen.',
					example: 'Gut: Gf8!m2_XpL99\nSchlecht: passwort123'
				},
				{
					name: 'Phishing',
					text: 'Betrugsversuche per E-Mail. Achte auf:\n- Falsche Absender-Adresse\n- Dringender Handlungsbedarf\n- Fehlerhafte Grammatik\n- Seltsame Links',
					example: ''
				}
			]
		},
		digitaltechnik: {
			title: 'Digitaltechnik',
			icon: '📟',
			topics: [
				{
					name: 'Logikgatter',
					text: 'Logikgatter sind die Grundbausteine von Computern. Sie verarbeiten Signale (0 oder 1).\n- UND (AND): Ausgang ist 1, wenn BEIDE Eingänge 1 sind.\n- ODER (OR): Ausgang ist 1, wenn MINDESTENS ein Eingang 1 ist.\n- NICHT (NOT): Kehrt das Signal um (0 wird 1, 1 wird 0).',
					example: ''
				},
				{
					name: 'Binärsystem',
					text: 'Computer verstehen nur zwei Zustände: Strom an (1) oder Strom aus (0). Zahlen werden daher im Binärsystem dargestellt.\nStellenwerte: ... 8, 4, 2, 1.',
					example: 'Dezimal 5 = 4 + 1 = 101 binär\nDezimal 10 = 8 + 2 = 1010 binär'
				},
				{
					name: 'Hexadezimalsystem',
					text: 'Da Binärzahlen sehr lang werden, nutzt man oft Hexadezimalzahlen (Basis 16: 0-9 und A-F). Eine Hex-Ziffer entspricht genau 4 Bits.',
					example: 'Binär 1111 = Dezimal 15 = Hex F'
				}
			]
		}
	};
</script>

<div class="mx-auto max-w-7xl">
	<header class="mb-12 border-b border-slate-200 pb-8">
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Wissensdatenbank
		</h1>
		<p class="mt-4 text-slate-500">Zentrale Informationsquelle für IT-Konzepte und Syntax-Referenzen.</p>
	</header>

	<div class="grid grid-cols-1 gap-12 lg:grid-cols-4">
		<!-- Sidebar Navigation -->
		<div class="space-y-1 lg:col-span-1">
			{#each Object.entries(content) as [id, cat] (id)}
				<button
					onclick={() => (selectedCategory = id)}
					class="flex w-full items-center gap-4 border-l-4 p-5 font-sans text-xs font-bold tracking-widest uppercase transition-all
                    {selectedCategory === id
						? 'border-institutional-gold bg-amber-50 text-institutional-gold shadow-sm'
						: 'border-transparent text-slate-500 hover:bg-slate-50 hover:text-slate-700'}"
				>
					<span class="text-xl grayscale">{cat.icon}</span>
					{cat.title}
				</button>
			{/each}
		</div>

		<!-- Content Area -->
		<div class="lg:col-span-3">
			<div class="border border-slate-200 bg-white p-12 shadow-sm rounded-none">
				<div class="mb-16 flex items-center gap-6">
					<span class="text-5xl">{content[selectedCategory as keyof typeof content].icon}</span>
					<h2 class="font-sans text-3xl font-extrabold text-institutional-navy uppercase tracking-tight">
						{content[selectedCategory as keyof typeof content].title}
					</h2>
				</div>

				<div class="space-y-16">
					{#each content[selectedCategory as keyof typeof content].topics as topic}
						<section class="border-b border-slate-100 pb-12 last:border-0 last:pb-0">
							<h3 class="mb-6 font-sans text-xl font-bold text-institutional-navy">
								{topic.name}
							</h3>
							<p class="mb-8 whitespace-pre-wrap text-sm leading-relaxed text-slate-600">
								{topic.text}
							</p>
							{#if topic.example}
								<div class="border-4 border-slate-800 bg-slate-900 p-6 shadow-inner rounded-none">
									<div class="mb-4 flex items-center justify-between border-b border-white/10 pb-3">
										<span class="font-mono text-[9px] font-bold tracking-widest text-slate-500 uppercase">Beispiel-Syntax</span>
										<div class="flex gap-1">
											<div class="h-2 w-2 bg-red-500"></div>
											<div class="h-2 w-2 bg-amber-500"></div>
											<div class="h-2 w-2 bg-green-500"></div>
										</div>
									</div>
									<pre class="font-mono text-xs text-teal-400"><code>{topic.example}</code></pre>
								</div>
							{/if}
						</section>
					{/each}
				</div>
			</div>
		</div>
	</div>
</div>
