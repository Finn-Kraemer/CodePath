/**
 * Klassenraum: Tische, Lehrerpult und Tafel auf einer Fläche. Die Geometrie der Sitzplätze ist dieselbe wie
 * in backend/.../observation/RoomLayout.java (PDF), damit Bildschirm und Ausdruck übereinstimmen.
 */
import type { Schemas } from '$lib/api';

export type RoomLayout = Schemas['RoomLayout'];
export type RoomItem = Schemas['RoomItem'];
export type RoomSeat = Schemas['RoomSeat'];
export type Side = RoomSeat['side'];
export type ItemKind = RoomItem['kind'];
export type BoardView = Schemas['BoardView'];
export type PersonView = Schemas['PersonView'];
export type CriterionView = Schemas['CriterionView'];
export type ObservationOption = Schemas['ObservationOption'];

/** Angaben aus dem Formular für ein Kriterium (POST/PUT …/criteria). */
export interface CriterionInput {
	name: string;
	kind: 'NUMBER' | 'CHOICE';
	min: number | null;
	max: number | null;
	options: ObservationOption[];
	/** Auswahlliste: mehrere Optionen gleichzeitig */
	multiple: boolean;
}

export const ROOM_WIDTH = 1200;
export const ROOM_HEIGHT = 800;
export const SEAT_WIDTH = 76;
export const SEAT_HEIGHT = 28;
export const SEAT_GAP = 4;
export const GRID = 10;
export const MAX_SEATS_PER_SIDE = 8;
export const SIDES: Side[] = ['TOP', 'BOTTOM', 'LEFT', 'RIGHT'];
export const SIDE_LABEL: Record<Side, string> = {
	TOP: 'oben',
	BOTTOM: 'unten',
	LEFT: 'links',
	RIGHT: 'rechts'
};

export interface Point {
	x: number;
	y: number;
}

export function uid(prefix: string): string {
	return `${prefix}${Math.random().toString(36).slice(2, 9)}`;
}

export function snap(value: number): number {
	return Math.round(value / GRID) * GRID;
}

/** Neues Element mitten in den Raum (bzw. an die angegebene Stelle). */
export function newItem(
	kind: ItemKind,
	seats: Partial<Record<Side, number>> = {},
	at?: Point
): RoomItem {
	const size =
		kind === 'BOARD'
			? { width: 360, height: 20 }
			: kind === 'TEACHER'
				? { width: 160, height: 70 }
				: { width: Math.max(1, seats.BOTTOM ?? 0, seats.TOP ?? 0) * 80, height: 60 };
	const item: RoomItem = {
		id: uid('t'),
		kind,
		label: '',
		x: snap((at?.x ?? ROOM_WIDTH / 2) - size.width / 2),
		y: snap((at?.y ?? ROOM_HEIGHT / 2) - size.height / 2),
		width: size.width,
		height: size.height,
		rotation: 0,
		seats: []
	};
	return withSeatCounts(item, seats);
}

/** Anzahl Plätze je Seite. */
export function seatCounts(item: RoomItem): Record<Side, number> {
	const counts: Record<Side, number> = { TOP: 0, BOTTOM: 0, LEFT: 0, RIGHT: 0 };
	for (const s of item.seats ?? []) counts[s.side]++;
	return counts;
}

/**
 * Setzt die Anzahl Plätze je Seite. Bestehende Plätze behalten ihre ID (und damit ihre Zuweisung);
 * überzählige fallen hinten weg.
 */
export function withSeatCounts(item: RoomItem, counts: Partial<Record<Side, number>>): RoomItem {
	if (item.kind !== 'TABLE') return { ...item, seats: [] };
	const current = seatCounts(item);
	const seats: RoomSeat[] = [];
	for (const side of SIDES) {
		const want = Math.max(0, Math.min(MAX_SEATS_PER_SIDE, counts[side] ?? current[side]));
		const existing = (item.seats ?? []).filter((s) => s.side === side).slice(0, want);
		seats.push(...existing);
		for (let i = existing.length; i < want; i++) seats.push({ id: uid('s'), side });
	}
	return { ...item, seats };
}

/** Mittelpunkt eines Platzes relativ zur linken oberen Ecke des ungedrehten Tisches. */
export function localCenter(item: RoomItem, seat: RoomSeat): Point {
	const same = item.seats.filter((s) => s.side === seat.side);
	const along = (same.findIndex((s) => s.id === seat.id) + 0.5) / same.length;
	switch (seat.side) {
		case 'TOP':
			return { x: item.width * along, y: -SEAT_GAP - SEAT_HEIGHT / 2 };
		case 'BOTTOM':
			return { x: item.width * along, y: item.height + SEAT_GAP + SEAT_HEIGHT / 2 };
		case 'LEFT':
			return { x: -SEAT_GAP - SEAT_WIDTH / 2, y: item.height * along };
		case 'RIGHT':
			return { x: item.width + SEAT_GAP + SEAT_WIDTH / 2, y: item.height * along };
	}
}

/** Mittelpunkt eines Platzes auf der Fläche, nach Drehung des Tisches um seine Mitte. */
export function seatCenter(item: RoomItem, seat: RoomSeat): Point {
	const local = localCenter(item, seat);
	const cx = item.width / 2;
	const cy = item.height / 2;
	const rad = (item.rotation * Math.PI) / 180;
	const dx = local.x - cx;
	const dy = local.y - cy;
	return {
		x: item.x + cx + dx * Math.cos(rad) - dy * Math.sin(rad),
		y: item.y + cy + dx * Math.sin(rad) + dy * Math.cos(rad)
	};
}

export interface SeatRef {
	id: string;
	label: string;
	itemId: string;
}

/** Tische von vorne links nach hinten rechts (Reihen auf 40 Einheiten gerundet), wie im Backend. */
export function tablesInOrder(layout: RoomLayout): RoomItem[] {
	return layout.items
		.filter((i) => i.kind === 'TABLE')
		.map((item) => ({
			item,
			row: Math.round((item.y + item.height / 2) / 40),
			col: item.x + item.width / 2
		}))
		.sort((a, b) => a.row - b.row || a.col - b.col)
		.map((e) => e.item);
}

/** Alle Plätze in Sitzplan-Reihenfolge mit Bezeichnung („Tisch 2 · 1“). */
export function seatsInOrder(layout: RoomLayout): SeatRef[] {
	const result: SeatRef[] = [];
	tablesInOrder(layout).forEach((item, index) => {
		const table = item.label || `Tisch ${index + 1}`;
		item.seats.forEach((seat, i) => {
			result.push({
				id: seat.id,
				itemId: item.id,
				label: item.seats.length === 1 ? table : `${table} · ${i + 1}`
			});
		});
	});
	return result;
}

/** Bezeichnung des Tisches für die Anzeige im Raum. */
export function itemLabel(layout: RoomLayout, item: RoomItem): string {
	if (item.label) return item.label;
	if (item.kind === 'BOARD') return 'Tafel';
	if (item.kind === 'TEACHER') return 'Lehrkraft';
	return `Tisch ${tablesInOrder(layout).findIndex((t) => t.id === item.id) + 1}`;
}

export type SortMode = 'seat' | 'name';

/** Nach Sitzplan (Personen ohne Platz am Ende, alphabetisch) oder alphabetisch. */
export function sortPeople(people: PersonView[], layout: RoomLayout, mode: SortMode): PersonView[] {
	const byName = (a: PersonView, b: PersonView) =>
		a.name.localeCompare(b.name, 'de', { sensitivity: 'base' }) || a.id - b.id;
	if (mode === 'name') return [...people].sort(byName);
	const order = new Map(seatsInOrder(layout).map((s, i) => [s.id, i]));
	const rank = (p: PersonView) =>
		p.seatId != null ? (order.get(p.seatId) ?? Number.MAX_SAFE_INTEGER) : Number.MAX_SAFE_INTEGER;
	return [...people].sort((a, b) => rank(a) - rank(b) || byName(a, b));
}

/** Kurzname für die Anzeige auf dem Platz. */
export function shortName(name: string): string {
	const n = name.trim();
	return n.length > 11 ? `${n.slice(0, 10)}…` : n;
}

/** „1 – Neugier …“ */
export function optionLabel(option: ObservationOption): string {
	return option.description ? `${option.value} – ${option.description}` : option.value;
}

/** Erklärung eines Kriteriums für Tooltip und Kopfzeile. */
export function criterionHint(criterion: CriterionView): string {
	if (criterion.kind === 'CHOICE')
		return (
			(criterion.multiple ? 'Mehrere möglich:\n' : '') +
			criterion.options.map(optionLabel).join('\n')
		);
	const min = criterion.min;
	const max = criterion.max;
	if (min != null && max != null) return `Zahl von ${min} bis ${max}`;
	if (min != null) return `Zahl ab ${min}`;
	if (max != null) return `Zahl bis ${max}`;
	return 'Zahl';
}

/** Umgebendes Rechteck aller Elemente samt Plätzen (für den zugeschnittenen Sitzplan). */
export function bounds(
	layout: RoomLayout,
	pad = 30
): { x: number; y: number; width: number; height: number } {
	let minX = Infinity;
	let minY = Infinity;
	let maxX = -Infinity;
	let maxY = -Infinity;
	const add = (item: RoomItem, x: number, y: number) => {
		const cx = item.width / 2;
		const cy = item.height / 2;
		const rad = (item.rotation * Math.PI) / 180;
		const px = item.x + cx + (x - cx) * Math.cos(rad) - (y - cy) * Math.sin(rad);
		const py = item.y + cy + (x - cx) * Math.sin(rad) + (y - cy) * Math.cos(rad);
		minX = Math.min(minX, px);
		minY = Math.min(minY, py);
		maxX = Math.max(maxX, px);
		maxY = Math.max(maxY, py);
	};
	for (const item of layout.items) {
		for (const [x, y] of [
			[0, 0],
			[item.width, 0],
			[0, item.height],
			[item.width, item.height]
		])
			add(item, x, y);
		for (const seat of item.seats) {
			const c = localCenter(item, seat);
			add(item, c.x - SEAT_WIDTH / 2, c.y - SEAT_HEIGHT / 2);
			add(item, c.x + SEAT_WIDTH / 2, c.y + SEAT_HEIGHT / 2);
		}
	}
	if (!Number.isFinite(minX)) return { x: 0, y: 0, width: ROOM_WIDTH, height: ROOM_HEIGHT };
	return {
		x: minX - pad,
		y: minY - pad,
		width: maxX - minX + 2 * pad,
		height: maxY - minY + 2 * pad
	};
}

/** Gewählte Werte einer Mehrfachauswahl („1, 2“ → ["1", "2"]). */
export function splitValues(value: string | null | undefined): string[] {
	return (value ?? '')
		.split(',')
		.map((v) => v.trim())
		.filter((v) => v !== '');
}

/** Option an- oder abwählen; Ergebnis in Reihenfolge der Optionen, wie es der Server speichert. */
export function toggleValue(
	criterion: CriterionView,
	current: string | null | undefined,
	value: string
): string {
	const chosen = new Set(splitValues(current));
	if (chosen.has(value)) chosen.delete(value);
	else chosen.add(value);
	return criterion.options
		.map((o) => o.value)
		.filter((v) => chosen.has(v))
		.join(', ');
}
