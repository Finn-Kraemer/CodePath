import { describe, expect, it } from 'vitest';
import { check, depths, expressionTable, truthTable, type Circuit } from './logic';

const xor: Circuit = {
	gates: [
		{ id: 'g1', type: 'OR', inputs: ['A', 'B'] },
		{ id: 'g2', type: 'AND', inputs: ['A', 'B'] },
		{ id: 'g3', type: 'NOT', inputs: ['g2'] },
		{ id: 'g4', type: 'AND', inputs: ['g1', 'g3'] }
	],
	output: 'g4'
};

describe('Logikschaltungen', () => {
	it('wertet Schaltungen zeilenweise aus', () => {
		expect(truthTable(xor, ['A', 'B'])).toEqual([false, true, true, false]);
		expect(depths(xor, ['A', 'B'])).toMatchObject({ A: 0, g1: 1, g2: 1, g3: 2, g4: 3 });
	});

	it('meldet unvollständige Schaltungen wie der Server', () => {
		expect(truthTable({ gates: [], output: '' }, ['A'])).toBeNull();
		expect(
			check({ gates: [{ id: 'g1', type: 'NOT', inputs: ['g2'] }], output: 'g1' }, ['A'])
		).toContain('Gatter g1: Eingang fehlt');
		expect(check(xor, ['A', 'B'], ['AND', 'OR'])).toContain('Gatter g3: Typ nicht erlaubt');
		expect(check(xor, ['A', 'B'], undefined, 3)).toContain('Höchstens 3 Gatter');
	});

	it('rechnet Ausdrücke mit Rangfolge und Ersatzzeichen', () => {
		expect(expressionTable('A XOR B', ['A', 'B'])).toEqual([false, true, true, false]);
		expect(expressionTable('A & B | !C', ['A', 'B', 'C'])).toEqual(
			expressionTable('(A AND B) OR (NOT C)', ['A', 'B', 'C'])
		);
		expect(() => expressionTable('A AND', ['A'])).toThrow('endet zu früh');
		expect(() => expressionTable('A AND D', ['A'])).toThrow("Unbekannter Eingang 'D'");
	});
});
