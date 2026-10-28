/**
 * Startseite als Liste von Blöcken. Die Lehrkraft bearbeitet sie unter Admin → Startseite; das
 * Backend speichert das JSON nur (Entwurf / veröffentlicht) und prüft die Form. Ohne veröffentlichte
 * Fassung gilt DEFAULT_HOMEPAGE.
 */

export type Theme = 'light' | 'muted' | 'dark';

export type Card = { icon: string; title: string; text: string };
export type Step = { marker: string; title: string; text: string };
export type GradeRow = { range: string; label: string; grade: string };
/** Frei wählbarer Button: Ziel ist eine interne Seite („/seiten/…“) oder eine externe Adresse. */
export type LinkButton = { label: string; href: string; newTab: boolean };

type Base = { id: string; hidden?: boolean; theme: Theme; badge: string; title: string };

export type HeroBlock = Base & {
	type: 'hero';
	highlight: string;
	text: string;
	buttons: boolean;
	links: LinkButton[];
};
export type TextBlock = Base & { type: 'text'; body: string; align: 'left' | 'center' };
export type ListBlock = Base & { type: 'list'; items: string[]; align: 'left' | 'center' };
export type CardsBlock = Base & { type: 'cards'; intro: string; items: Card[] };
export type SplitBlock = Base & {
	type: 'split';
	highlight: string;
	lead: string;
	body: string;
	tags: string[];
	boxTitle: string;
	boxItems: string[];
};
export type TimelineBlock = Base & { type: 'timeline'; steps: Step[] };
export type GradesBlock = Base & {
	type: 'grades';
	body: string;
	boxTitle: string;
	rows: GradeRow[];
};
export type ImageBlock = Base & { type: 'image'; media: string; alt: string; caption: string };
export type CtaBlock = Base & {
	type: 'cta';
	highlight: string;
	buttons: boolean;
	links: LinkButton[];
};

export type Block =
	| HeroBlock
	| TextBlock
	| ListBlock
	| CardsBlock
	| SplitBlock
	| TimelineBlock
	| GradesBlock
	| ImageBlock
	| CtaBlock;

export type BlockType = Block['type'];
export type HomepageContent = { blocks: Block[] };

/** Kurze IDs für Blöcke; crypto.randomUUID gibt es im Schulnetz ohne HTTPS nicht. */
export function blockId(): string {
	return Math.random().toString(36).slice(2, 10);
}

/** Felder für das Editor-Formular, je Blocktyp. */
export type Field =
	| { key: string; label: string; kind: 'text' | 'textarea' | 'markdown' | 'bool' | 'media' }
	| { key: string; label: string; kind: 'select'; options: { value: string; label: string }[] }
	| { key: string; label: string; kind: 'strings'; placeholder?: string }
	| { key: string; label: string; kind: 'links' }
	| {
			key: string;
			label: string;
			kind: 'objects';
			fields: { key: string; label: string; wide?: boolean }[];
	  };

const THEME: Field = {
	key: 'theme',
	label: 'Hintergrund',
	kind: 'select',
	options: [
		{ value: 'light', label: 'Hell' },
		{ value: 'muted', label: 'Grau' },
		{ value: 'dark', label: 'Dunkelblau' }
	]
};
const ALIGN: Field = {
	key: 'align',
	label: 'Ausrichtung',
	kind: 'select',
	options: [
		{ value: 'center', label: 'Zentriert' },
		{ value: 'left', label: 'Linksbündig' }
	]
};
const BADGE: Field = { key: 'badge', label: 'Kleine Überzeile', kind: 'text' };
const TITLE: Field = { key: 'title', label: 'Überschrift', kind: 'text' };
const HIGHLIGHT: Field = { key: 'highlight', label: 'Hervorgehobener Teil (gold)', kind: 'text' };
const BUTTONS: Field = {
	key: 'buttons',
	label: 'Standard-Buttons anzeigen (Login/Registrieren bzw. „Zu den Lernmodulen“)',
	kind: 'bool'
};
const LINKS: Field = { key: 'links', label: 'Eigene Buttons', kind: 'links' };

export const BLOCK_TYPES: Record<
	BlockType,
	{ label: string; description: string; fields: Field[]; create: () => Block }
> = {
	hero: {
		label: 'Titelbereich',
		description: 'Große Überschrift mit Einleitung und Buttons, meist ganz oben.',
		fields: [
			BADGE,
			TITLE,
			HIGHLIGHT,
			{ key: 'text', label: 'Einleitung', kind: 'textarea' },
			BUTTONS,
			LINKS,
			THEME
		],
		create: () => ({
			id: blockId(),
			type: 'hero',
			theme: 'dark',
			badge: '',
			title: 'Neue Überschrift',
			highlight: '',
			text: '',
			buttons: true,
			links: []
		})
	},
	text: {
		label: 'Textabschnitt',
		description: 'Überschrift mit freiem Text (Markdown: **fett**, Listen, Links).',
		fields: [
			BADGE,
			TITLE,
			{ key: 'body', label: 'Text (Markdown)', kind: 'markdown' },
			ALIGN,
			THEME
		],
		create: () => ({
			id: blockId(),
			type: 'text',
			theme: 'light',
			badge: '',
			title: 'Neuer Abschnitt',
			body: '',
			align: 'center'
		})
	},
	list: {
		label: 'Aufzählung',
		description: 'Überschrift mit kurzen Punkten, z. B. „Wer bin ich?“.',
		fields: [
			BADGE,
			TITLE,
			{ key: 'items', label: 'Punkte', kind: 'strings', placeholder: 'Neuer Punkt' },
			ALIGN,
			THEME
		],
		create: () => ({
			id: blockId(),
			type: 'list',
			theme: 'light',
			badge: '',
			title: 'Neue Liste',
			items: [''],
			align: 'center'
		})
	},
	cards: {
		label: 'Karten',
		description: 'Kacheln mit Symbol, Titel und Text, z. B. Themen der Veranstaltung.',
		fields: [
			BADGE,
			TITLE,
			{ key: 'intro', label: 'Einleitung', kind: 'textarea' },
			{
				key: 'items',
				label: 'Karten',
				kind: 'objects',
				fields: [
					{ key: 'icon', label: 'Symbol (Emoji)' },
					{ key: 'title', label: 'Titel' },
					{ key: 'text', label: 'Text', wide: true }
				]
			},
			THEME
		],
		create: () => ({
			id: blockId(),
			type: 'cards',
			theme: 'muted',
			badge: '',
			title: 'Neue Karten',
			intro: '',
			items: [{ icon: '✨', title: '', text: '' }]
		})
	},
	split: {
		label: 'Text mit Infobox',
		description: 'Links Text und Stichworte, rechts eine Box mit Liste.',
		fields: [
			BADGE,
			TITLE,
			HIGHLIGHT,
			{ key: 'lead', label: 'Hervorgehobener Satz', kind: 'text' },
			{ key: 'body', label: 'Text (Markdown)', kind: 'markdown' },
			{ key: 'tags', label: 'Stichworte', kind: 'strings', placeholder: 'Stichwort' },
			{ key: 'boxTitle', label: 'Titel der Box', kind: 'text' },
			{ key: 'boxItems', label: 'Punkte in der Box', kind: 'strings', placeholder: 'Punkt' },
			THEME
		],
		create: () => ({
			id: blockId(),
			type: 'split',
			theme: 'dark',
			badge: '',
			title: 'Neuer Abschnitt',
			highlight: '',
			lead: '',
			body: '',
			tags: [],
			boxTitle: '',
			boxItems: []
		})
	},
	timeline: {
		label: 'Ablauf',
		description: 'Zeitstrahl mit Schritten, z. B. der Tagesablauf.',
		fields: [
			BADGE,
			TITLE,
			{
				key: 'steps',
				label: 'Schritte',
				kind: 'objects',
				fields: [
					{ key: 'marker', label: 'Markierung (z. B. 01, ⭐, 09:00)' },
					{ key: 'title', label: 'Titel' },
					{ key: 'text', label: 'Text', wide: true }
				]
			},
			THEME
		],
		create: () => ({
			id: blockId(),
			type: 'timeline',
			theme: 'light',
			badge: '',
			title: 'Ablauf',
			steps: [{ marker: '01', title: '', text: '' }]
		})
	},
	grades: {
		label: 'Text mit Tabelle',
		description: 'Links Text, rechts eine dreispaltige Tabelle, z. B. der Notenspiegel.',
		fields: [
			BADGE,
			TITLE,
			{ key: 'body', label: 'Text (Markdown)', kind: 'markdown' },
			{ key: 'boxTitle', label: 'Titel der Tabelle', kind: 'text' },
			{
				key: 'rows',
				label: 'Zeilen',
				kind: 'objects',
				fields: [
					{ key: 'range', label: 'Links' },
					{ key: 'label', label: 'Mitte' },
					{ key: 'grade', label: 'Rechts' }
				]
			},
			THEME
		],
		create: () => ({
			id: blockId(),
			type: 'grades',
			theme: 'muted',
			badge: '',
			title: 'Neue Tabelle',
			body: '',
			boxTitle: '',
			rows: [{ range: '', label: '', grade: '' }]
		})
	},
	image: {
		label: 'Bild',
		description: 'Ein hochgeladenes Bild mit Bildunterschrift.',
		fields: [
			BADGE,
			TITLE,
			{ key: 'media', label: 'Bild', kind: 'media' },
			{ key: 'alt', label: 'Beschreibung für Screenreader', kind: 'text' },
			{ key: 'caption', label: 'Bildunterschrift', kind: 'text' },
			THEME
		],
		create: () => ({
			id: blockId(),
			type: 'image',
			theme: 'light',
			badge: '',
			title: '',
			media: '',
			alt: '',
			caption: ''
		})
	},
	cta: {
		label: 'Aufruf mit Buttons',
		description: 'Kurze Aufforderung mit Buttons, meist ganz unten.',
		fields: [BADGE, TITLE, HIGHLIGHT, BUTTONS, LINKS, THEME],
		create: () => ({
			id: blockId(),
			type: 'cta',
			theme: 'dark',
			badge: '',
			title: 'Starte jetzt mit dem',
			highlight: 'Lernen',
			buttons: true,
			links: []
		})
	}
};

/** Die bisherige, fest eingebaute Startseite – Ausgangspunkt für den Editor. */
export const DEFAULT_HOMEPAGE: HomepageContent = {
	blocks: [
		{
			id: 'hero',
			type: 'hero',
			theme: 'dark',
			badge: 'Berufsorientierungstag IT 2026',
			title: 'The Path to',
			highlight: 'Code',
			text: 'Willkommen zu deinem Deep-Dive in die Welt der Informationstechnik. Heute lernst du nicht aus Büchern, sondern arbeitest direkt an den Systemen, die unsere digitale Welt antreiben.',
			buttons: true,
			links: []
		},
		{
			id: 'about',
			type: 'list',
			theme: 'light',
			badge: 'Hintergrund',
			title: 'Wer bin ich?',
			items: [
				'22 Jahre alt & Informatikstudent',
				'Fokus auf moderne Softwareentwicklung & Systemarchitektur'
			],
			align: 'center'
		},
		{
			id: 'job',
			type: 'split',
			theme: 'dark',
			badge: 'Orientierung',
			title: 'Beruf Informatiker,',
			highlight: 'was ist das?',
			lead: 'Es gibt nicht "den einen" Beruf in der IT.',
			body: 'Das Feld der Informatik ist so vielfältig wie kaum ein anderes. Ob du direkt an der Hardware arbeitest, komplexe Algorithmen entwickelst oder digitale Erlebnisse gestaltest – deine Möglichkeiten sind nahezu grenzenlos.',
			tags: [
				'Embedded Systems',
				'Internet Engineering',
				'Medieninformatik',
				'Wirtschaftsinformatik'
			],
			boxTitle: 'Mögliche Arbeitsgebiete',
			boxItems: [
				'Softwareentwicklung und Programmierung',
				'Konzeption und Realisierung von IT-Infrastrukturen',
				'Beratungstätigkeit in IT-Fragen',
				'Netzwerke und Betriebssysteme',
				'Eingebettete Systeme (Hardware & Software)',
				'Benutzerservice und Support',
				'Administration von Datenbanken',
				'Customizing von ERP-Systemen',
				'Entwicklung von Multimedia-Anwendungen',
				'Audio- und Video-Verarbeitung',
				'Entwicklung von Lernsystemen'
			]
		},
		{
			id: 'agenda',
			type: 'cards',
			theme: 'muted',
			badge: 'Die Agenda',
			title: 'Veranstaltungs-Übersicht',
			intro:
				'Der Berufsorientierungstag im Bereich IT. Hier lernt ihr den Berufsbereich Informatiker kennen. In der Lernplattform werden viele verschiedene Themen beleuchtet.',
			items: [
				{
					icon: '💾',
					title: 'Grundlagen & Logik',
					text: 'Verständnis für binäre Systeme, digitale Logikgatter und die Architektur von Prozessoren als Basis jeder Software.'
				},
				{
					icon: '🛠️',
					title: 'Software Engineering',
					text: 'Einstieg in Python. Wir lösen reale Probleme durch Algorithmen und lernen saubere Code-Strukturen kennen.'
				},
				{
					icon: '🔒',
					title: 'Cyber Security',
					text: 'Sicherheitslücken identifizieren, Kryptografie anwenden und verstehen, wie man Systeme gegen Angriffe härtet.'
				}
			]
		},
		{
			id: 'timeline',
			type: 'timeline',
			theme: 'light',
			badge: 'Chronologie',
			title: 'Geplanter Ablauf',
			steps: [
				{
					marker: '01',
					title: 'Vorstellung und Theorie',
					text: 'Einführung in das Programm, Kennenlernen der Plattform und theoretische Grundlagen der modernen Informationstechnik.'
				},
				{
					marker: '02',
					title: 'Block 1: Grundlagen',
					text: 'Einstieg in die Praxis. Fokus auf algorithmisches Denken, Hardware-Logik und die erste Jagd nach Leaderboard-Punkten.'
				},
				{
					marker: '...',
					title: 'Block n: Freies Praxisarbeiten',
					text: 'Individuelle Schwerpunkte und finale Herausforderungen. Anwendung des gelernten Wissens auf reale IT-Szenarien.'
				},
				{
					marker: '⭐',
					title: 'Zusammenfassung und Zeugnisse',
					text: 'Zusammenfassung der Ergebnisse, Feedback-Runde und feierliche Siegerehrung der punktbesten Teilnehmer.'
				}
			]
		},
		{
			id: 'grades',
			type: 'grades',
			theme: 'muted',
			badge: 'Leistungsnachweis',
			title: 'Dein freiwilliger Leistungsnachweis',
			body: 'Am Ende des Berufsorientierungstages erhältst du ein individuelles Zeugnis als PDF-Dokument. Dieses Dokument bescheinigt dir deine erfolgreiche Teilnahme und schlüsselt detailliert auf, welche Kompetenzen du in den verschiedenen IT-Modulen erworben hast.',
			boxTitle: 'Notenspiegel (IHK-Standard)',
			rows: [
				{ range: '100% - 92%', label: 'Sehr Gut', grade: 'Note 1' },
				{ range: '91% - 81%', label: 'Gut', grade: 'Note 2' },
				{ range: '80% - 67%', label: 'Befriedigend', grade: 'Note 3' },
				{ range: '66% - 50%', label: 'Ausreichend', grade: 'Note 4' },
				{ range: '49% - 30%', label: 'Mangelhaft', grade: 'Note 5' },
				{ range: '< 30%', label: 'Ungenügend', grade: 'Note 6' }
			]
		},
		{
			id: 'cta',
			type: 'cta',
			theme: 'dark',
			badge: '',
			title: 'Starte jetzt mit dem',
			highlight: 'Lernen',
			buttons: true,
			links: []
		}
	]
};

/** Ziele, die ein Button haben darf; alles andere (z. B. „javascript:“) wird nicht verlinkt. */
const SAFE_HREF = /^(\/(?!\/)|#|https?:\/\/|mailto:|tel:)/i;

/**
 * Bereinigt das Ziel eines Buttons: „www.beispiel.de“ wird zu „https://www.beispiel.de“,
 * unsichere oder leere Ziele ergeben null.
 */
export function safeHref(href: string): string | null {
	let value = href.trim();
	if (!value) return null;
	if (/^[\w-]+(\.[\w-]+)+(\/|$)/.test(value) && !value.includes(':')) value = `https://${value}`;
	return SAFE_HREF.test(value) ? value : null;
}

/** Externe Ziele verlassen die Plattform (anderer Host oder mailto/tel). */
export function isExternal(href: string): boolean {
	return !/^[/#]/.test(href);
}

/**
 * Macht aus gespeichertem JSON eine sichere Blockliste: unbekannte Typen fallen weg, fehlende
 * Felder kommen aus der Vorlage des Typs. So bricht die Seite nicht, wenn sich Felder ändern.
 */
export function normalize(content: unknown): HomepageContent | null {
	const blocks = (content as { blocks?: unknown } | null)?.blocks;
	if (!Array.isArray(blocks)) return null;
	return {
		blocks: blocks
			.filter(
				(b): b is Block =>
					!!b && typeof b === 'object' && Object.hasOwn(BLOCK_TYPES, (b as Block).type)
			)
			.map((b) => ({ ...BLOCK_TYPES[b.type].create(), ...b }) as Block)
	};
}
