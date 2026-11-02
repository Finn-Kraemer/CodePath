// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { draftText, waitingTime } from './help';

describe('draftText', () => {
	it('nimmt Code, SQL und Lücken lesbar mit', () => {
		expect(draftText('print(1)')).toBe('print(1)');
		expect(draftText({ code: 'x = 1' })).toBe('x = 1');
		expect(draftText({ sql: 'SELECT 1' })).toBe('SELECT 1');
		expect(draftText(['range', 'i'])).toBe('Lücke 1: range\nLücke 2: i');
		expect(draftText({ answers: ['a'] })).toBe('Lücke 1: a');
	});

	it('macht aus HTML der Praxisabgabe Text, ohne Tags zu übernehmen', () => {
		expect(draftText('<p>a<scr<script>ipt>alert(1)</script></p>')).not.toContain('<script');
		expect(draftText('<p>Erst <strong>einlesen</strong></p><p>dann &lt;b&gt;</p>')).toBe(
			'Erst einlesen\ndann <b>'
		);
	});

	it('zeigt andere Antworten als JSON', () => {
		expect(draftText({ selected: ['b'] })).toBe('{\n  "selected": [\n    "b"\n  ]\n}');
		expect(draftText(null)).toBe('');
	});
});

describe('waitingTime', () => {
	it('zeigt Minuten und Stunden', () => {
		expect(waitingTime(30)).toBe('gerade eben');
		expect(waitingTime(300)).toBe('5 Min.');
		expect(waitingTime(4200)).toBe('1 Std. 10 Min.');
	});
});
