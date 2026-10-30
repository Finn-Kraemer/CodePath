/** Bereiche des Leitstands, nach Aufgaben der Lehrkraft gruppiert */
export type AdminLink = { href: string; title: string; description: string; icon: string };
export type AdminGroup = { title: string; links: AdminLink[] };

export const ADMIN_GROUPS: AdminGroup[] = [
	{
		title: 'Unterricht',
		links: [
			{
				href: '/admin/courses',
				title: 'Kurse & Konten',
				description: 'Kurse anlegen, Beitrittscodes, Schülerkonten',
				icon: '👥'
			},
			{
				href: '/admin/modules',
				title: 'Modul-Steuerung',
				description: 'Module und Aufgaben freigeben, Fristen setzen',
				icon: '🎚️'
			},
			{
				href: '/admin/learning-paths',
				title: 'Lernpfade',
				description: 'Wege durch die Module zeichnen und vorgeben',
				icon: '🗺️'
			},
			{
				href: '/admin/students',
				title: 'Teilnehmende',
				description: 'Stand, Punkte und Lernpfad je Person',
				icon: '🧑‍🎓'
			},
			{
				href: '/admin/submissions',
				title: 'Abgaben prüfen',
				description: 'Praxisabgaben nach Rubrik bewerten',
				icon: '✅'
			},
			{
				href: '/admin/announcement',
				title: 'Live-Mitteilung',
				description: 'Hinweis an alle im Kopf oder auf der Info-Seite',
				icon: '📣'
			},
			{
				href: '/projector',
				title: 'Beamer-Modus',
				description: 'Rangliste und Stand für die Klasse zeigen',
				icon: '📽️'
			}
		]
	},
	{
		title: 'Inhalte',
		links: [
			{
				href: '/admin/content',
				title: 'Aufgaben & Module',
				description: 'Aufgaben bearbeiten, veröffentlichen, archivieren',
				icon: '✏️'
			},
			{
				href: '/admin/knowledge',
				title: 'Wissensdatenbank',
				description: 'Erklär-Artikel schreiben und verknüpfen',
				icon: '📖'
			},
			{
				href: '/admin/homepage',
				title: 'Startseite',
				description: 'Öffentliche Startseite gestalten',
				icon: '🏠'
			},
			{
				href: '/admin/pages',
				title: 'Eigene Seiten',
				description: 'Weitere Seiten und Menüpunkte',
				icon: '📄'
			}
		]
	},
	{
		title: 'Auswertung',
		links: [
			{
				href: '/admin/statistics',
				title: 'Lernanalyse',
				description: 'Schwierige Aufgaben und Fortschritt der Klasse',
				icon: '📊'
			},
			{
				href: '/admin/feedback',
				title: 'Feedback',
				description: 'Rückmeldungen der Schüler lesen und freischalten',
				icon: '💬'
			}
		]
	},
	{
		title: 'System',
		links: [
			{
				href: '/admin/transfer',
				title: 'Import & Export',
				description: 'Inhalte und Einstellungen sichern oder übernehmen',
				icon: '📦'
			}
		]
	}
];

/** Der Bereich, zu dem eine Adresse gehört (längster passender Präfix) */
export function activeAdminLink(pathname: string): AdminLink | undefined {
	return ADMIN_GROUPS.flatMap((g) => g.links)
		.filter((l) => pathname === l.href || pathname.startsWith(l.href + '/'))
		.sort((a, b) => b.href.length - a.href.length)[0];
}
