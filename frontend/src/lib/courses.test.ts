import { describe, expect, it } from 'vitest';
import { nextCourseName } from './courses.svelte';

describe('nextCourseName', () => {
	it('erhöht die erste Zahl', () => {
		expect(nextCourseName('9a')).toBe('10a');
		expect(nextCourseName('Informatik 9b')).toBe('Informatik 10b');
		expect(nextCourseName('Q1 2025')).toBe('Q2 2025');
	});

	it('hängt ohne Zahl einen Hinweis an', () => {
		expect(nextCourseName('AG Robotik')).toBe('AG Robotik (neu)');
	});
});
