import { describe, expect, it } from 'vitest';
import { slugify } from './slug';

describe('slugify', () => {
	it('macht aus Titeln gültige Adressen', () => {
		expect(slugify('Über uns & Kontakt')).toBe('ueber-uns-kontakt');
		expect(slugify('  Größe: 100% ')).toBe('groesse-100');
		expect(slugify('Café')).toBe('cafe');
		expect(slugify('!!!')).toBe('');
	});
});
