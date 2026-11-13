import { describe, expect, it } from 'vitest';
import {
	newItem,
	seatCenter,
	seatCounts,
	seatsInOrder,
	sortPeople,
	withSeatCounts,
	type PersonView,
	type RoomItem
} from './room';

function table(id: string, x: number, y: number, seats: string[]): RoomItem {
	return {
		id,
		kind: 'TABLE',
		label: '',
		x,
		y,
		width: 160,
		height: 60,
		rotation: 0,
		seats: seats.map((s) => ({ id: s, side: 'BOTTOM' }))
	};
}

function person(id: number, name: string, seatId: string | null): PersonView {
	return { id, name, seatId, ratings: {} } as unknown as PersonView;
}

describe('Klassenraum', () => {
	it('verteilt Plätze gleichmäßig unter dem Tisch', () => {
		const t = table('t', 100, 200, ['a', 'b']);
		expect(seatCenter(t, t.seats[0])).toEqual({ x: 140, y: 200 + 60 + 4 + 14 });
		expect(seatCenter(t, t.seats[1])).toEqual({ x: 220, y: 278 });
	});

	it('dreht Plätze mit dem Tisch um seine Mitte', () => {
		const t = { ...table('t', 100, 200, ['a']), rotation: 180 };
		const c = seatCenter(t, t.seats[0]);
		expect(c.x).toBeCloseTo(180);
		expect(c.y).toBeCloseTo(200 - 18);
	});

	it('behält Platz-IDs beim Ändern der Anzahl', () => {
		const t = table('t', 0, 0, ['a', 'b']);
		const more = withSeatCounts(t, { BOTTOM: 3, TOP: 1 });
		expect(seatCounts(more)).toEqual({ TOP: 1, BOTTOM: 3, LEFT: 0, RIGHT: 0 });
		expect(more.seats.filter((s) => s.side === 'BOTTOM').map((s) => s.id)).toEqual(
			expect.arrayContaining(['a', 'b'])
		);
		const fewer = withSeatCounts(more, { BOTTOM: 1 });
		expect(fewer.seats.filter((s) => s.side === 'BOTTOM').map((s) => s.id)).toEqual(['a']);
		expect(withSeatCounts(newItem('BOARD'), { BOTTOM: 2 }).seats).toEqual([]);
	});

	it('nummeriert Tische von vorne links nach hinten rechts', () => {
		const layout = {
			items: [
				table('hinten', 100, 400, ['h1']),
				table('rechts', 600, 100, ['r1', 'r2']),
				table('links', 100, 100, ['l1'])
			]
		};
		expect(seatsInOrder(layout).map((s) => s.label)).toEqual([
			'Tisch 1',
			'Tisch 2 · 1',
			'Tisch 2 · 2',
			'Tisch 3'
		]);
	});

	it('sortiert nach Sitzplan oder alphabetisch', () => {
		const layout = { items: [table('t1', 0, 0, ['a', 'b'])] };
		const people = [person(1, 'Zoe', 'a'), person(2, 'Anna', null), person(3, 'mia', 'b')];
		expect(sortPeople(people, layout, 'seat').map((p) => p.name)).toEqual(['Zoe', 'mia', 'Anna']);
		expect(sortPeople(people, layout, 'name').map((p) => p.name)).toEqual(['Anna', 'mia', 'Zoe']);
	});
});
