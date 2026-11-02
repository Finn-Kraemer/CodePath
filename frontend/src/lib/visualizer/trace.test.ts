import { describe, expect, it } from 'vitest';
import {
	aliases,
	findBreakpoint,
	variableAt,
	changedItems,
	changedVariables,
	describeStep,
	formatValue,
	objectNumbers,
	stopNotice,
	type Trace
} from './trace';
// Echte Aufzeichnungen des Tracers aus static/pyodide-worker.js (Python 3.12), IDs gekürzt
import rekursion from './fixtures/rekursion.json';
import tausch from './fixtures/tausch.json';

const recursion = rekursion.trace as Trace;
const swap = tausch.trace as Trace;

describe('Aufzeichnung', () => {
	it('enthält nur Schülercode, keinen Tracer', () => {
		for (const step of [...recursion.steps, ...swap.steps]) {
			for (const frame of step.stack) {
				expect(frame.func).not.toMatch(/codepath|trace/);
				for (const name of Object.keys(frame.locals)) expect(name.startsWith('__')).toBe(false);
			}
		}
		expect(recursion.stdout).toBe('[3, 1, 2, 6]\n');
		expect(recursion.error).toBeNull();
	});

	it('zeigt die Rekursion als Aufrufstapel', () => {
		const depths = recursion.steps.map((s) => s.stack.length);
		expect(Math.max(...depths)).toBe(4);
		const deepest = recursion.steps.find((s) => s.stack.length === 4)!;
		expect(deepest.stack.map((f) => f.func)).toEqual([
			'<module>',
			'fakultaet',
			'fakultaet',
			'fakultaet'
		]);
		expect(deepest.stack.map((f) => f.locals.n?.k === 'p' && f.locals.n.r).slice(1)).toEqual([
			'3',
			'2',
			'1'
		]);
		const returns = recursion.steps.filter((s) => s.event === 'return' && s.returnValue);
		expect(returns.map((s) => formatValue(s.returnValue, s.heap))).toEqual(['1', '2', '6']);
	});
});

describe('formatValue', () => {
	it('formatiert Listen, Tupel, Dicts und Mengen', () => {
		const heap = {
			a: { k: 'list' as const, items: [{ k: 'p' as const, t: 'int', r: '1' }], more: 2 },
			b: { k: 'tuple' as const, items: [{ k: 'p' as const, t: 'str', r: "'x'" }], more: 0 },
			c: {
				k: 'dict' as const,
				items: [
					[
						{ k: 'p' as const, t: 'str', r: "'k'" },
						{ k: 'ref' as const, id: 'a' }
					] as [never, never]
				],
				more: 0
			},
			d: { k: 'set' as const, items: [], more: 0 }
		};
		expect(formatValue({ k: 'ref', id: 'a' }, heap)).toBe('[1, … (+2)]');
		expect(formatValue({ k: 'ref', id: 'b' }, heap)).toBe("('x',)");
		expect(formatValue({ k: 'ref', id: 'c' }, heap)).toBe("{'k': [1, … (+2)]}");
		expect(formatValue({ k: 'ref', id: 'd' }, heap)).toBe('set()');
		expect(formatValue({ k: 'p', t: 'str', r: "'Hallo'" }, heap)).toBe("'Hallo'");
	});
});

describe('Aliasing', () => {
	it('erkennt zwei Namen für dieselbe Liste', () => {
		const step = recursion.steps.find((s) => 'kopie' in s.stack[0].locals)!;
		const groups = [...aliases(step).values()];
		expect(groups).toEqual([['zahlen', 'kopie']]);
		expect(objectNumbers(recursion.steps).size).toBe(1);
	});

	it('meldet die Änderung über den anderen Namen', () => {
		const index = recursion.steps.findIndex(
			(s, i) =>
				i > 0 &&
				formatValue(s.stack[0].locals.zahlen, s.heap) === '[3, 1, 2, 6]' &&
				formatValue(recursion.steps[i - 1].stack[0].locals.zahlen, recursion.steps[i - 1].heap) ===
					'[3, 1, 2]'
		);
		expect(index).toBeGreaterThan(0);
		const text = describeStep(recursion.steps[index - 1], recursion.steps[index]);
		expect(text).toContain('zahlen ist jetzt [3, 1, 2, 6]');
	});
});

describe('Änderungen je Schritt', () => {
	it('markiert neue und geänderte Variablen', () => {
		expect([...changedVariables(swap.steps[0], swap.steps[1])[0]]).toEqual(['liste']);
		expect([...changedVariables(swap.steps[5], swap.steps[6])[0]]).toEqual(['i']);
		expect(describeStep(swap.steps[5], swap.steps[6])).toEqual(['i ist jetzt 1']);
	});

	it('markiert getauschte Listenelemente', () => {
		const id = (swap.steps[3].stack[0].locals.liste as { id: string }).id;
		expect([...changedItems(swap.steps[2], swap.steps[3], id)]).toEqual([0, 1]);
		expect(describeStep(swap.steps[2], swap.steps[3])).toEqual(['liste ist jetzt [1, 3]']);
		expect(changedItems(swap.steps[3], swap.steps[4], id).size).toBe(0);
	});

	it('beschreibt Aufrufe und Rückgaben', () => {
		const call = recursion.steps.findIndex((s) => s.event === 'call');
		expect(describeStep(recursion.steps[call - 1], recursion.steps[call])).toEqual([
			'Aufruf von fakultaet(n = 3)'
		]);
		const last = recursion.steps.findLastIndex((s) => s.event === 'return' && s.returnValue);
		expect(describeStep(recursion.steps[last - 1], recursion.steps[last])[0]).toBe(
			'fakultaet gibt 6 zurück'
		);
	});
});

describe('stopNotice', () => {
	it('erklärt den Abbruch nach dem Schrittlimit', () => {
		const trace: Trace = { steps: [], stdout: '', stopped: 'steps', error: null };
		expect(stopNotice(trace, 1000)).toBe(
			'Nach 1000 Schritten angehalten – läuft deine Schleife endlos?'
		);
		expect(stopNotice({ ...trace, stopped: null }, 1000)).toBeNull();
	});
});

describe('Haltepunkt (Vorhersage-Aufgaben)', () => {
	it('findet das n-te Erreichen einer Zeile und liest Variablen', () => {
		// tausch: Zeile 6 (i += 1) wird zweimal erreicht
		const first = findBreakpoint(swap.steps, { line: 6 });
		const second = findBreakpoint(swap.steps, { line: 6, hit: 2 });
		expect(first).toBe(5);
		expect(second).toBe(7);
		expect(variableAt(swap.steps[second], 'i')).toBe('1');
		expect(variableAt(swap.steps[second], 'liste')).toBe('[1, 3]');
		expect(variableAt(swap.steps[second], 'gibtsnicht')).toBeUndefined();
		expect(findBreakpoint(swap.steps, { line: 6, hit: 3 })).toBe(-1);
	});

	it('sucht Variablen vom innersten Aufruf nach außen', () => {
		const deepest = recursion.steps.find((s) => s.stack.length === 4)!;
		expect(variableAt(deepest, 'n')).toBe('1');
		expect(variableAt(deepest, 'zahlen')).toBe('[3, 1, 2]');
	});
});
