import { describe, expect, it } from 'vitest';
import { ADMIN_ICONS, ADMIN_NAV, isAdminLinkActive } from './nav';

describe('Admin-Menü', () => {
	it('markiert den Leitstand nur auf /admin selbst', () => {
		expect(isAdminLinkActive('/admin', '/admin')).toBe(true);
		expect(isAdminLinkActive('/admin', '/admin/content')).toBe(false);
	});

	it('markiert Unterseiten, aber keine Pfade mit gleichem Anfang', () => {
		expect(isAdminLinkActive('/admin/content', '/admin/content/42')).toBe(true);
		expect(isAdminLinkActive('/admin/pages', '/admin/pages')).toBe(true);
		expect(isAdminLinkActive('/admin/students', '/admin/studentsX')).toBe(false);
	});

	it('nutzt nur bekannte Icons und eindeutige Ziele', () => {
		const links = ADMIN_NAV.flatMap((g) => g.links);
		for (const l of links) expect(ADMIN_ICONS[l.icon]).toBeTruthy();
		expect(new Set(links.map((l) => l.href)).size).toBe(links.length);
	});
});
