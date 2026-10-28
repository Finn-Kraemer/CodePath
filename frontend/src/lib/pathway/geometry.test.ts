import { describe, expect, it } from 'vitest';
import { canvasSize, edgePath, layered, NODE_H, NODE_W, snap } from './geometry';

describe('Lernpfad-Karte', () => {
	it('macht die Fläche groß genug für alle Stationen', () => {
		expect(canvasSize([{ x: 100, y: 300 }])).toEqual({
			width: 100 + NODE_W + 40,
			height: 300 + NODE_H + 40
		});
	});

	it('führt Linien nach unten von unten nach oben, nach rechts von Seite zu Seite', () => {
		expect(edgePath({ x: 0, y: 0 }, { x: 0, y: 200 })).toMatch(
			new RegExp(`^M ${NODE_W / 2} ${NODE_H} `)
		);
		expect(edgePath({ x: 0, y: 0 }, { x: 600, y: 0 })).toMatch(
			new RegExp(`^M ${NODE_W} ${NODE_H / 2} `)
		);
	});

	it('rastet auf das Raster ein und bleibt im sichtbaren Bereich', () => {
		expect(snap(29)).toBe(20);
		expect(snap(31)).toBe(40);
		expect(snap(-50)).toBe(0);
	});

	it('ordnet Stationen nach ihrer Tiefe in Reihen', () => {
		const nodes = [{ id: 'a' }, { id: 'b' }, { id: 'c' }, { id: 'd' }];
		const edges = [
			{ from: 'a', to: 'b' },
			{ from: 'a', to: 'c' },
			{ from: 'b', to: 'd' },
			{ from: 'c', to: 'd' }
		];
		const pos = layered(nodes, edges);
		expect(pos.get('a')!.y).toBeLessThan(pos.get('b')!.y);
		expect(pos.get('b')!.y).toBe(pos.get('c')!.y);
		expect(pos.get('b')!.x).not.toBe(pos.get('c')!.x);
		expect(pos.get('d')!.y).toBeGreaterThan(pos.get('b')!.y);
	});
});
