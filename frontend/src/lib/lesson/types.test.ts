import { describe, expect, it } from 'vitest';
import { clock, emptyStep, remaining } from './types';

describe('Stundenablauf', () => {
	it('legt leere Schritte mit sinnvollen Vorgaben an', () => {
		expect(emptyStep('RELEASE').action).toBe('UNLOCK');
		expect(emptyStep('EVALUATION').action).toBe('OPEN');
	});

	it('rechnet die Restzeit eines Schritts', () => {
		const start = '2026-11-12T10:00:00';
		const now = new Date(start).getTime() + 90_000;
		expect(remaining(start, 5, now)).toBe(210);
		expect(remaining(start, null, now)).toBeNull();
		expect(clock(210)).toBe('3:30');
		expect(clock(-65)).toBe('−1:05');
	});
});
