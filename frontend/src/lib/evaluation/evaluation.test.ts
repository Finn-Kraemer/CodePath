import { describe, expect, it } from 'vitest';
import {
	comparableIds,
	formatAnswer,
	missingRequired,
	newCriterion,
	stepLabel,
	steps,
	toCsv,
	type Criterion
} from './evaluation';

const scale = {
	id: 'a',
	type: 'SCALE',
	label: 'Tempo',
	required: true,
	min: 1,
	max: 3,
	labels: ['langsam', 'gut', 'schnell']
} as Criterion;
const text = { id: 't', type: 'TEXT', label: 'Notiz' } as Criterion;
const heading = { id: 'h', type: 'HEADING', label: 'Teil 1' } as Criterion;

describe('Bewertungsbögen', () => {
	it('vergibt freie IDs und sinnvolle Vorgaben', () => {
		const c = newCriterion('SCALE', [{ ...scale, id: 'k2' }]);
		expect(c.id).toBe('k3');
		expect(c.min).toBe(1);
		expect(c.max).toBe(5);
		expect(newCriterion('CHOICE', []).options).toHaveLength(2);
	});

	it('kennt Stufen und Beschriftungen', () => {
		expect(steps(scale)).toEqual([1, 2, 3]);
		expect(stepLabel(scale, 2)).toBe('gut');
		expect(stepLabel({ ...scale, labels: undefined } as unknown as Criterion, 2)).toBe('2');
	});

	it('findet fehlende Pflichtantworten', () => {
		expect(missingRequired([heading, scale, text], {}).map((c) => c.id)).toEqual(['a']);
		expect(missingRequired([scale], { a: 2 })).toEqual([]);
	});

	it('formatiert Antworten lesbar', () => {
		expect(formatAnswer(scale, 3)).toBe('3 (schnell)');
		expect(formatAnswer({ ...scale, labels: undefined } as unknown as Criterion, 2)).toBe('2 / 3');
		expect(formatAnswer({ id: 'y', type: 'YES_NO', label: '' } as Criterion, false)).toBe('Nein');
		expect(formatAnswer({ id: 'c', type: 'CHOICE', label: '' } as Criterion, ['A', 'B'])).toBe(
			'A, B'
		);
		expect(formatAnswer(text, '')).toBe('–');
	});

	it('vergleicht nur Skalen mit gleicher ID', () => {
		expect([...comparableIds([scale, text], [scale, { ...text }])]).toEqual(['a']);
		expect(comparableIds(null, [scale]).size).toBe(0);
	});

	it('schreibt CSV mit Semikolon und Anführungszeichen', () => {
		const csv = toCsv(['Name', 'Text'], [['Lena', 'Ja; "gut"']]);
		expect(csv).toBe('﻿Name;Text\r\nLena;"Ja; ""gut"""');
	});
});
