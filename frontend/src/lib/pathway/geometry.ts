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
 * Versatz eines Eingangs-Anschlusspunkts relativ zur Mitte, wenn mehrere Verbindungen an einem Gatter ankommen.
 */
export function gateInputOffset(index: number, count: number, maxSpan: number): number {
	if (count <= 1) return 0;
	const clampedIndex = Math.max(0, Math.min(index, count - 1));
	const span = Math.min(maxSpan, (count - 1) * 32);
	return Math.round(-span / 2 + (clampedIndex * span) / (count - 1));
}

/**
 * Bestimmt für eine eingehende Verbindung an einem Gatter den sortierten Anschluss-Index (von links nach rechts
 * bzw. von oben nach unten), damit sich Linien vor dem Gatter möglichst nicht kreuzen.
 */
export function incomingSlot(
	edges: { from: string; to: string }[],
	byId: Map<string, Box>,
	edge: { from: string; to: string }
): { index: number; count: number } {
	const incoming = edges
		.filter((e) => e.to === edge.to && byId.has(e.from))
		.sort((a, b) => {
			const fa = byId.get(a.from)!;
			const fb = byId.get(b.from)!;
			return fa.x - fb.x || fa.y - fb.y || a.from.localeCompare(b.from);
		});
	const index = incoming.findIndex((e) => e.from === edge.from);
	return { index: Math.max(0, index), count: Math.max(1, incoming.length) };
}

/**
 * Pfad einer Verbindung als weiche Kurve. Liegt das Ziel eher darunter, läuft die Linie von unten nach
 * oben, sonst von der Seite zur Seite. Bei Gattern mit mehreren Eingängen verteilen sich die Anschlusspunkte.
 */
export function edgePath(from: Box, to: Box, inputIndex = 0, inputCount = 1): string {
	const fc = { x: from.x + NODE_W / 2, y: from.y + NODE_H / 2 };
	const tc = { x: to.x + NODE_W / 2, y: to.y + NODE_H / 2 };
	const dx = tc.x - fc.x;
	const dy = tc.y - fc.y;
	let a: Point, b: Point, c1: Point, c2: Point;
	if (Math.abs(dy) * 2 >= Math.abs(dx)) {
		const down = dy >= 0;
		const offsetX = gateInputOffset(inputIndex, inputCount, NODE_W - 48);
		a = { x: fc.x, y: down ? from.y + NODE_H : from.y };
		b = { x: tc.x + offsetX, y: down ? to.y - 6 : to.y + NODE_H + 6 };
		const bend = Math.max(30, Math.abs(b.y - a.y) / 2);
		c1 = { x: a.x, y: a.y + (down ? bend : -bend) };
		c2 = { x: b.x, y: b.y - (down ? bend : -bend) };
	} else {
		const right = dx >= 0;
		const offsetY = gateInputOffset(inputIndex, inputCount, NODE_H - 28);
		a = { x: right ? from.x + NODE_W : from.x, y: fc.y };
		b = { x: right ? to.x - 6 : to.x + NODE_W + 6, y: tc.y + offsetY };
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

/**
 * Bestimmt für jeden ausgehenden Weg einer Weiche (CHOICE) die exklusiven Stationen, die nur von diesem
 * Weg aus erreichbar sind (ohne gemeinsame Zusammenführungspunkte dahinter).
 */
export function choiceBranches(
	edges: { from: string; to: string }[],
	choiceId: string
): Map<string, Set<string>> {
	const adj = new Map<string, string[]>();
	for (const e of edges) {
		const list = adj.get(e.from) ?? [];
		list.push(e.to);
		adj.set(e.from, list);
	}
	const reachableFrom = (start: string): Set<string> => {
		const visited = new Set<string>();
		const stack = [start];
		while (stack.length) {
			const curr = stack.pop()!;
			if (visited.has(curr)) continue;
			visited.add(curr);
			for (const next of adj.get(curr) ?? []) stack.push(next);
		}
		return visited;
	};
	const targets = edges.filter((e) => e.from === choiceId).map((e) => e.to);
	const reachByTarget = new Map<string, Set<string>>(targets.map((t) => [t, reachableFrom(t)]));
	const result = new Map<string, Set<string>>();
	for (const target of targets) {
		const exclusive = new Set(reachByTarget.get(target) ?? []);
		for (const other of targets) {
			if (other === target) continue;
			for (const id of reachByTarget.get(other) ?? []) exclusive.delete(id);
		}
		result.set(target, exclusive);
	}
	return result;
}
