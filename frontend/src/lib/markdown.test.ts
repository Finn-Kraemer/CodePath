// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { renderMarkdown } from './markdown';

describe('renderMarkdown', () => {
	it('rendert Code, Listen und Tabellen', () => {
		const html = renderMarkdown('**fett** `code`\n\n- a\n- b\n\n| x | y |\n|---|---|\n| 1 | 2 |');
		expect(html).toContain('<strong>fett</strong>');
		expect(html).toContain('<code>code</code>');
		expect(html).toContain('<li>a</li>');
		expect(html).toContain('<td>1</td>');
	});

	it('entfernt Skripte und Event-Handler', () => {
		const html = renderMarkdown(
			'<img src=x onerror="alert(1)"><script>alert(2)</script>[x](javascript:alert(3))'
		);
		expect(html).not.toContain('onerror');
		expect(html).not.toContain('<script');
		expect(html).not.toContain('javascript:');
	});
});
