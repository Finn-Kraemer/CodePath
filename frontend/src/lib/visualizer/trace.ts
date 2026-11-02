/**
 * Aufzeichnung des Code-Visualizers (Worker-Nachricht `trace`, siehe static/pyodide-worker.js).
 * Reines Lernwerkzeug: Die Schritte bleiben im Browser, bewertet wird nur auf dem Server.
 */

/** Einfacher Wert (Zahl, Text, Wahrheitswert, None), Funktion oder sonstiges Objekt als repr() */
export type Primitive = { k: 'p'; t: string; r: string };
export type Opaque = { k: 'fn' | 'o'; t?: string; r: string };
/** Verweis auf eine Liste, ein Tupel, eine Menge oder ein Dict im Heap des Schritts */
export type Ref = { k: 'ref'; id: string };
export type Value = Primitive | Opaque | Ref;

export type HeapObject = {
	k: 'list' | 'tuple' | 'set' | 'frozenset' | 'dict';
	/** Elemente; bei Dicts Paare aus Schlüssel und Wert */
	items: (Value | [Value, Value])[];
	/** Weitere, aus Platzgründen weggelassene Elemente */
	more: number;
};

export type Frame = { func: string; line: number; locals: Record<string, Value> };

export type TraceStep = {
	/** Zeile, die Python als Nächstes ausführt (bei return/exception: die aktuelle) */
	line: number;
	event: 'line' | 'call' | 'return' | 'exception';
	stack: Frame[];
	heap: Record<string, HeapObject>;
	/** Länge der Ausgabe bis zu diesem Schritt */
	out: number;
	returnValue?: Value;
	exception?: string;
};

export type Trace = {
	steps: TraceStep[];
	stdout: string;
	/** Abgebrochen wegen Schrittlimit oder Zeitlimit */
	stopped: 'steps' | 'time' | null;
	error: string | null;
};

export const DEFAULT_MAX_STEPS = 1000;

/** Kurzer Text für einen Wert, z. B. `3`, `'Hallo'`, `[3, 1, 2]`. */
export function formatValue(
	value: Value | undefined,
	heap: Record<string, HeapObject>,
	depth = 0
): string {
	if (!value) return '';
	if (value.k !== 'ref') return value.r;
	const object = heap[value.id];
	if (!object) return '…';
	if (depth > 2) return '…';
	const more = object.more > 0 ? `, … (+${object.more})` : '';
	if (object.k === 'dict') {
		const parts = (object.items as [Value, Value][]).map(
			([key, item]) => `${formatValue(key, heap, depth + 1)}: ${formatValue(item, heap, depth + 1)}`
		);
		return `{${parts.join(', ')}${more}}`;
	}
	const parts = (object.items as Value[]).map((item) => formatValue(item, heap, depth + 1));
	if (object.k === 'tuple') return `(${parts.join(', ')}${parts.length === 1 ? ',' : ''}${more})`;
	if (object.k === 'set' || object.k === 'frozenset')
		return parts.length ? `{${parts.join(', ')}${more}}` : 'set()';
	return `[${parts.join(', ')}${more}]`;
}

/** Welche Variablen (über alle Rahmen) zeigen auf dasselbe Objekt? Nur Objekte mit mindestens zwei Namen. */
export function aliases(step: TraceStep): Map<string, string[]> {
	const byId = new Map<string, string[]>();
	step.stack.forEach((frame, depth) => {
		for (const [name, value] of Object.entries(frame.locals)) {
			if (value.k !== 'ref') continue;
			const label = step.stack.length > 1 && depth > 0 ? `${name} (${frame.func})` : name;
			byId.set(value.id, [...(byId.get(value.id) ?? []), label]);
		}
	});
	for (const [id, names] of byId) if (names.length < 2) byId.delete(id);
	return byId;
}

/** Fortlaufende Nummer je Objekt (statt der langen Python-IDs), in der Reihenfolge des ersten Auftretens. */
export function objectNumbers(steps: TraceStep[]): Map<string, number> {
	const numbers = new Map<string, number>();
	for (const step of steps) {
		for (const frame of step.stack) {
			for (const value of Object.values(frame.locals)) {
				if (value.k === 'ref' && !numbers.has(value.id)) numbers.set(value.id, numbers.size + 1);
			}
		}
		for (const id of Object.keys(step.heap))
			if (!numbers.has(id)) numbers.set(id, numbers.size + 1);
	}
	return numbers;
}

/** Rahmen eines Schritts über die Aufruftiefe dem vorherigen Schritt zuordnen. */
function previousFrame(
	prev: TraceStep | undefined,
	step: TraceStep,
	index: number
): Frame | undefined {
	const candidate = prev?.stack[index];
	return candidate && candidate.func === step.stack[index].func ? candidate : undefined;
}

/** Namen der Variablen je Rahmen, deren Wert sich gegenüber dem vorherigen Schritt geändert hat. */
export function changedVariables(prev: TraceStep | undefined, step: TraceStep): Set<string>[] {
	return step.stack.map((frame, index) => {
		const changed = new Set<string>();
		const before = previousFrame(prev, step, index);
		if (!before || !prev) return changed;
		for (const [name, value] of Object.entries(frame.locals)) {
			const old = before.locals[name];
			if (!old || formatValue(old, prev.heap) !== formatValue(value, step.heap)) changed.add(name);
		}
		return changed;
	});
}

/** Indizes der Elemente eines Heap-Objekts, die sich gegenüber dem vorherigen Schritt geändert haben. */
export function changedItems(
	prev: TraceStep | undefined,
	step: TraceStep,
	id: string
): Set<number> {
	const changed = new Set<number>();
	const now = step.heap[id];
	const before = prev?.heap[id];
	if (!now || !before || !prev) return changed;
	now.items.forEach((item, i) => {
		const old = before.items[i];
		const text = (v: Value | [Value, Value] | undefined, heap: Record<string, HeapObject>) =>
			v === undefined
				? undefined
				: Array.isArray(v)
					? `${formatValue(v[0], heap)}: ${formatValue(v[1], heap)}`
					: formatValue(v, heap);
		if (text(old, prev.heap) !== text(item, step.heap)) changed.add(i);
	});
	return changed;
}

/**
 * Änderungen als Text (Barrierefreiheit: nicht nur farblich), z. B.
 * „`i` ist jetzt 3“, „Aufruf von fakultaet(n = 3)“, „fakultaet gibt 6 zurück“.
 */
export function describeStep(prev: TraceStep | undefined, step: TraceStep): string[] {
	const lines: string[] = [];
	const top = step.stack[step.stack.length - 1];
	if (step.event === 'call' && top) {
		const args = Object.entries(top.locals)
			.map(([name, value]) => `${name} = ${formatValue(value, step.heap)}`)
			.join(', ');
		lines.push(`Aufruf von ${top.func}(${args})`);
		return lines;
	}
	if (step.event === 'return' && top && step.returnValue) {
		lines.push(`${top.func} gibt ${formatValue(step.returnValue, step.heap)} zurück`);
	}
	if (step.event === 'exception' && step.exception) {
		lines.push(`Fehler: ${step.exception}`);
	}
	const changed = changedVariables(prev, step);
	step.stack.forEach((frame, index) => {
		for (const name of changed[index]) {
			const where = step.stack.length > 1 && index > 0 ? ` (in ${frame.func})` : '';
			lines.push(`${name}${where} ist jetzt ${formatValue(frame.locals[name], step.heap)}`);
		}
	});
	// Listen, die sich ohne neue Zuweisung geändert haben (z. B. append, Tausch)
	if (prev) {
		const mentioned = new Set<string>();
		step.stack.forEach((frame, index) => {
			for (const [name, value] of Object.entries(frame.locals)) {
				if (value.k !== 'ref' || changed[index].has(name) || mentioned.has(value.id)) continue;
				const before = prev.heap[value.id];
				if (before && formatValue(value, prev.heap) !== formatValue(value, step.heap)) {
					mentioned.add(value.id);
					lines.push(`${name} ist jetzt ${formatValue(value, step.heap)}`);
				}
			}
		});
	}
	return lines;
}

/** Hinweis, wenn die Aufzeichnung vorzeitig endete. */
export function stopNotice(trace: Trace, maxSteps: number): string | null {
	if (trace.stopped === 'steps')
		return `Nach ${maxSteps} Schritten angehalten – läuft deine Schleife endlos?`;
	if (trace.stopped === 'time')
		return 'Zeitlimit erreicht – das Programm braucht zu lange. Läuft eine Schleife endlos?';
	return null;
}
