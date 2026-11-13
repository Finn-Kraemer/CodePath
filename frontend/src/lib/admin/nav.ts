/** Menü des Admin-Bereichs: Seitennavigation und Schnellzugriff im Leitstand nutzen dieselbe Liste. */

export type AdminIcon = keyof typeof ADMIN_ICONS;

export type AdminLink = {
	href: string;
	title: string;
	text: string;
	icon: AdminIcon;
	/** Zähler neben dem Menüpunkt */
	badge?: 'help' | 'submissions';
	/** Im Leitstand-Schnellzugriff zeigen */
	quick?: boolean;
	external?: boolean;
};

export type AdminGroup = { title: string; links: AdminLink[] };

/** Linien-Icons (24er-Raster, Strich), passend zu PathIcon */
export const ADMIN_ICONS = {
	home: 'M3 11l9-7 9 7v9H3zM9 20v-6h6v6',
	help: 'M21 12a8 8 0 01-11.6 7.1L4 20l1-4.6A8 8 0 1121 12z',
	check: 'M9 11l3 3 8-8M20 12v7a1 1 0 01-1 1H5a1 1 0 01-1-1V5a1 1 0 011-1h11',
	quiz: 'M3 5h18v11H3zM8 21h8M12 16v5',
	users:
		'M16 20v-1a4 4 0 00-4-4H6a4 4 0 00-4 4v1M9 11a4 4 0 100-8 4 4 0 000 8M22 20v-1a4 4 0 00-3-3.9M16 3.1a4 4 0 010 7.8',
	student: 'M12 12a4 4 0 100-8 4 4 0 000 8zM4 21a8 8 0 0116 0',
	grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
	chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
	megaphone: 'M3 11v2a1 1 0 001 1h3l6 5V5L7 10H4a1 1 0 00-1 1zM17 9a4 4 0 010 6',
	feedback: 'M4 4h16v12H8l-4 4zM8 9h8M8 12h5',
	edit: 'M4 20h4L19 9l-4-4L4 16zM14 6l4 4',
	map: 'M9 4L3 6v14l6-2 6 2 6-2V4l-6 2zM9 4v14M15 6v14',
	book: 'M4 5a2 2 0 012-2h14v16H6a2 2 0 00-2 2zM4 5v16',
	page: 'M14 3H6v18h12V7zM14 3v4h4M9 13h6M9 17h6',
	image: 'M4 4h16v16H4zM4 16l5-5 4 4 3-3 4 4M15 9h.01',
	transfer: 'M4 16v3h16v-3M12 4v11M8 11l4 4 4-4',
	calendar: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4M9 15l2 2 4-4',
	lesson: 'M4 5h16v14H4zM10 9l5 3-5 3z',
	printer:
		'M7 17H5a2 2 0 01-2-2v-4a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2h-2M7 9V4h10v5M7 14h10v6H7z',
	projector: 'M3 5h18v11H3zM8 21h8M12 16v5M10 9l4 2-4 2z',
	eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 15a3 3 0 100-6 3 3 0 000 6',
	logout: 'M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10',
	menu: 'M4 7h16M4 12h16M4 17h16',
	close: 'M6 18L18 6M6 6l12 12'
} as const;

export const ADMIN_NAV: AdminGroup[] = [
	{
		title: 'Unterricht',
		links: [
			{ href: '/admin', title: 'Leitstand', text: 'Überblick über den Kurs', icon: 'home' },
			{
				href: '/admin/help',
				title: 'Hilfe-Rufe',
				text: 'Wer hängt fest? Warteschlange mit Aufgabe und Entwurf',
				icon: 'help',
				badge: 'help',
				quick: true
			},
			{
				href: '/admin/submissions',
				title: 'Abgaben prüfen',
				text: 'Praxisabgaben nach Rubrik bewerten',
				icon: 'check',
				badge: 'submissions',
				quick: true
			},
			{
				href: '/admin/quiz',
				title: 'Quiz-Runde',
				text: 'Live-Quiz aus Auswahl-Aufgaben für den Beamer',
				icon: 'quiz',
				quick: true
			},
			{
				href: '/admin/courses',
				title: 'Kurse & Konten',
				text: 'Kurse, Beitrittscodes und Konten verwalten',
				icon: 'users',
				quick: true
			},
			{
				href: '/admin/lessons',
				title: 'Stundenabläufe',
				text: 'Stunde vorab planen, mit einem Klick starten und weiterschalten',
				icon: 'lesson',
				quick: true
			},
			{
				href: '/admin/attendance',
				title: 'Anwesenheit',
				text: 'Termine erfassen, Abweichungen anklicken, Quote und Export',
				icon: 'calendar'
			},
			{
				href: '/admin/students',
				title: 'Teilnehmende',
				text: 'Fortschritt einzelner Schüler:innen',
				icon: 'student'
			},
			{
				href: '/admin/modules',
				title: 'Modul-Steuerung',
				text: 'Module je Kurs freigeben, sperren, Fristen setzen',
				icon: 'grid',
				quick: true
			},
			{
				href: '/admin/statistics',
				title: 'Lernanalyse',
				text: 'Fortschritt und Schwierigkeiten der Klasse',
				icon: 'chart',
				quick: true
			},
			{
				href: '/admin/announcement',
				title: 'Live-Mitteilung',
				text: 'Hinweis an alle angemeldeten Schüler:innen senden',
				icon: 'megaphone'
			},
			{
				href: '/admin/feedback',
				title: 'Feedback & Bewertung',
				text: 'Selbsteinschätzung, Unterrichtsbewertung, Schüler bewerten',
				icon: 'feedback'
			}
		]
	},
	{
		title: 'Inhalte',
		links: [
			{
				href: '/admin/content',
				title: 'Aufgaben & Module',
				text: 'Aufgaben erstellen, prüfen, veröffentlichen',
				icon: 'edit',
				quick: true
			},
			{
				href: '/admin/codepaths',
				title: 'CodePaths',
				text: 'Lernwege als Karte zeichnen',
				icon: 'map',
				quick: true
			},
			{
				href: '/admin/worksheets',
				title: 'Aufgabenblätter',
				text: 'Arbeitsblätter und Hausaufgaben als PDF',
				icon: 'printer'
			},
			{
				href: '/admin/knowledge',
				title: 'Wissensdatenbank',
				text: 'Artikel zum Nachschlagen pflegen',
				icon: 'book'
			}
		]
	},
	{
		title: 'Website',
		links: [
			{
				href: '/admin/homepage',
				title: 'Startseite',
				text: 'Inhalte der Startseite gestalten',
				icon: 'home'
			},
			{
				href: '/admin/pages',
				title: 'Eigene Seiten',
				text: 'Zusätzliche Seiten und Menüeinträge',
				icon: 'page'
			},
			{
				href: '/admin/media',
				title: 'Medien',
				text: 'Hochgeladene Bilder ansehen, umbenennen, aufräumen',
				icon: 'image'
			},
			{
				href: '/admin/transfer',
				title: 'Export & Import',
				text: 'Inhalte, CodePaths und Seiten als ZIP sichern',
				icon: 'transfer'
			}
		]
	}
];

/** Ist der Menüpunkt für den aktuellen Pfad aktiv? Der Leitstand nur exakt. */
export function isAdminLinkActive(href: string, pathname: string): boolean {
	if (href === '/admin') return pathname === '/admin' || pathname === '/admin/';
	return pathname === href || pathname.startsWith(href + '/');
}
