import { describe, expect, it } from 'vitest';
import { isExternal, safeHref } from './types';

describe('safeHref', () => {
	it('lässt interne Pfade und Web-Adressen durch', () => {
		expect(safeHref('/seiten/ablauf')).toBe('/seiten/ablauf');
		expect(safeHref(' https://example.org/a?b=1 ')).toBe('https://example.org/a?b=1');
		expect(safeHref('mailto:lehrer@schule.de')).toBe('mailto:lehrer@schule.de');
		expect(safeHref('#agenda')).toBe('#agenda');
	});

	it('ergänzt https:// bei Adressen ohne Protokoll', () => {
		expect(safeHref('www.schule.de')).toBe('https://www.schule.de');
		expect(safeHref('schule.de/kontakt')).toBe('https://schule.de/kontakt');
	});

	it('verwirft leere und unsichere Ziele', () => {
		expect(safeHref('')).toBeNull();
		expect(safeHref('javascript:alert(1)')).toBeNull();
		expect(safeHref('//evil.example')).toBeNull();
		expect(safeHref('data:text/html,hi')).toBeNull();
	});
});

describe('isExternal', () => {
	it('unterscheidet interne und externe Ziele', () => {
		expect(isExternal('/modules')).toBe(false);
		expect(isExternal('https://example.org')).toBe(true);
	});
});
