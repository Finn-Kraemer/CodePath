/** Maße und Linienführung der Lernpfad-Karte – gemeinsam für Schüleransicht und Editor. */

export const NODE_W = 200;
export const NODE_H = 90;
export const PADDING = 40;
export const GRID = 20;

export interface Point {
	x: number;
	y: number;
}

export interface Box {
	x: number;
	y: number;
}

/** Größe der Zeichenfläche, damit alle Stationen hineinpassen */
export function canvasSize(nodes: Box[], minWidth = 0, minHeight = 0) {
	const width = Math.max(minWidth, ...nodes.map((n) => n.x + NODE_W + PADDING));
	const height = Math.max(minHeight, ...nodes.map((n) => n.y + NODE_H + PADDING));
	return { width, height };
}

/**
 * Pfad einer Verbindung als weiche Kurve. Liegt das Ziel eher darunter, läuft die Linie von unten nach
 * oben, sonst von der Seite zur Seite.
 */
export function edgePath(from: Box, to: Box): string {
	const fc = { x: from.x + NODE_W / 2, y: from.y + NODE_H / 2 };
	const tc = { x: to.x + NODE_W / 2, y: to.y + NODE_H / 2 };
	const dx = tc.x - fc.x;
	const dy = tc.y - fc.y;
	let a: Point, b: Point, c1: Point, c2: Point;
	if (Math.abs(dy) * 2 >= Math.abs(dx)) {
		const down = dy >= 0;
		a = { x: fc.x, y: down ? from.y + NODE_H : from.y };
		b = { x: tc.x, y: down ? to.y - 6 : to.y + NODE_H + 6 };
		const bend = Math.max(30, Math.abs(b.y - a.y) / 2);
		c1 = { x: a.x, y: a.y + (down ? bend : -bend) };
		c2 = { x: b.x, y: b.y - (down ? bend : -bend) };
	} else {
		const right = dx >= 0;
		a = { x: right ? from.x + NODE_W : from.x, y: fc.y };
		b = { x: right ? to.x - 6 : to.x + NODE_W + 6, y: tc.y };
		const bend = Math.max(30, Math.abs(b.x - a.x) / 2);
		c1 = { x: a.x + (right ? bend : -bend), y: a.y };
		c2 = { x: b.x - (right ? bend : -bend), y: b.y };
	}
	return `M ${a.x} ${a.y} C ${c1.x} ${c1.y}, ${c2.x} ${c2.y}, ${b.x} ${b.y}`;
}

export function snap(value: number): number {
	return Math.max(0, Math.round(value / GRID) * GRID);
}

/**
 * Ordnet Stationen in Reihen nach ihrer Tiefe (längster Weg vom Start) – für "Automatisch anordnen" im Editor.
 * Stationen in einem Kreis (sollte nicht vorkommen) landen in der letzten Reihe.
 */
export function layered<T extends { id: string }>(
	nodes: T[],
	edges: { from: string; to: string }[]
): Map<string, Point> {
	const depth = new Map<string, number>(nodes.map((n) => [n.id, 0]));
	const incoming = new Map<string, number>(nodes.map((n) => [n.id, 0]));
	for (const e of edges) incoming.set(e.to, (incoming.get(e.to) ?? 0) + 1);
	const queue = nodes.filter((n) => incoming.get(n.id) === 0).map((n) => n.id);
	const seen = new Set<string>();
	while (queue.length) {
		const id = queue.shift()!;
		seen.add(id);
		for (const e of edges.filter((e) => e.from === id)) {
			depth.set(e.to, Math.max(depth.get(e.to) ?? 0, (depth.get(id) ?? 0) + 1));
			const left = (incoming.get(e.to) ?? 1) - 1;
			incoming.set(e.to, left);
			if (left === 0) queue.push(e.to);
		}
	}
	const maxDepth = Math.max(0, ...depth.values());
	for (const n of nodes) if (!seen.has(n.id)) depth.set(n.id, maxDepth + 1);
	const rows = new Map<number, string[]>();
	for (const n of nodes) {
		const d = depth.get(n.id) ?? 0;
		rows.set(d, [...(rows.get(d) ?? []), n.id]);
	}
	const widest = Math.max(1, ...[...rows.values()].map((r) => r.length));
	const positions = new Map<string, Point>();
	for (const [d, ids] of rows) {
		const offset = ((widest - ids.length) * (NODE_W + 60)) / 2;
		ids.forEach((id, i) =>
			positions.set(id, {
				x: snap(PADDING + offset + i * (NODE_W + 60)),
				y: PADDING + d * (NODE_H + 70)
			})
		);
	}
	return positions;
}
