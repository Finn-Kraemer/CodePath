import DOMPurify from 'dompurify';
import { marked } from 'marked';

marked.setOptions({ gfm: true, breaks: false });

// Bilder nur aus der eigenen Medienablage: CodePath läuft offline, und fremde Bilder könnten
// Schülerinnen und Schüler beim Laden verfolgen.
let hooked = false;
function registerHooks() {
	if (hooked) return;
	hooked = true;
	DOMPurify.addHook('afterSanitizeAttributes', (node) => {
		if (node.nodeName !== 'IMG') return;
		const src = node.getAttribute('src') ?? '';
		if (!/^\/api\/media\/[0-9a-f]{64}\.(png|jpg|gif|webp)$/.test(src)) {
			node.remove();
			return;
		}
		node.setAttribute('loading', 'lazy');
	});
}

/** Markdown → bereinigtes HTML. Skripte, Event-Handler und fremde Protokolle werden entfernt. */
export function renderMarkdown(source: string): string {
	const html = marked.parse(source, { async: false }) as string;
	if (typeof window === 'undefined') return '';
	registerHooks();
	return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } });
}

/** Für Abgaben aus dem Rich-Text-Editor (bereits HTML). */
export function sanitizeHtml(html: string): string {
	if (typeof window === 'undefined') return '';
	registerHooks();
	return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } });
}
