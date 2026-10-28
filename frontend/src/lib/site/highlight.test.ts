// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { clearHighlights, highlight, searchTerms, segments } from './highlight';

describe('Suche', () => {
	it('zerlegt die Anfrage wie das Backend', () => {
		expect(searchTerms('  Git  COMMIT a git ')).toEqual(['git', 'commit']);
	});

	it('markiert Treffer im Ausschnitt ohne HTML', () => {
		expect(segments('Ein Commit in Git', ['git', 'commit'])).toEqual([
			{ text: 'Ein ', match: false },
			{ text: 'Commit', match: true },
			{ text: ' in ', match: false },
			{ text: 'Git', match: true }
		]);
		expect(segments('a+b (c)', ['(c)'])).toEqual([
			{ text: 'a+b ', match: false },
			{ text: '(c)', match: true }
		]);
	});

	it('markiert Treffer im Artikel und entfernt sie wieder', () => {
		const div = document.createElement('div');
		div.innerHTML = '<p>Mit <strong>git commit</strong> speichern. Git!</p><pre>git log</pre>';
		const first = highlight(div, ['git']);
		expect(first?.textContent).toBe('git');
		expect(div.querySelectorAll('mark')).toHaveLength(3);
		expect(div.querySelector('strong')?.textContent).toBe('git commit');
		clearHighlights(div);
		expect(div.querySelectorAll('mark')).toHaveLength(0);
		expect(div.innerHTML).toBe(
			'<p>Mit <strong>git commit</strong> speichern. Git!</p><pre>git log</pre>'
		);
	});
});
