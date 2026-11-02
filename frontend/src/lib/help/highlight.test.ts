import { describe, expect, it } from 'vitest';
import { highlight } from './highlight';

describe('highlight', () => {
	it('hebt Python hervor', () => {
		const html = highlight('def f():\n    return 1', 'CODE');
		expect(html).toContain('<span class="hljs-keyword">def</span>');
		expect(html).toContain('<span class="hljs-number">1</span>');
	});

	it('lässt kein HTML aus dem Entwurf durch', () => {
		const html = highlight('print("<img src=x onerror=alert(1)>")', 'CODE');
		expect(html).not.toContain('<img');
		expect(html).toContain('&lt;img');
	});
});
