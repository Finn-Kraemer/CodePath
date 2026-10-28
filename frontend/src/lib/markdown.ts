import DOMPurify from 'dompurify';
import { marked } from 'marked';

marked.setOptions({ gfm: true, breaks: false });

/** Markdown → bereinigtes HTML. Skripte, Event-Handler und fremde Protokolle werden entfernt. */
export function renderMarkdown(source: string): string {
	const html = marked.parse(source, { async: false }) as string;
	if (typeof window === 'undefined') return '';
	return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } });
}

/** Für Abgaben aus dem Rich-Text-Editor (bereits HTML). */
export function sanitizeHtml(html: string): string {
	if (typeof window === 'undefined') return '';
	return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } });
}
