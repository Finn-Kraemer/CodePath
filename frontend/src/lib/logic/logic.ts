/**
 * Logikschaltungen im Browser: nur für die Live-Vorschau (Wahrheitstabelle, Schaltplan).
 * Bewertet wird auf dem Server (LogicCircuitHandler) mit denselben Regeln.
 */

export type GateType = 'AND' | 'OR' | 'NOT' | 'XOR' | 'NAND' | 'NOR' | 'XNOR';
export type Gate = { id: string; type: GateType; inputs: string[] };
export type Circuit = { gates: Gate[]; output: string };

export const GATE_TYPES: GateType[] = ['AND', 'OR', 'NOT', 'XOR', 'NAND', 'NOR', 'XNOR'];
export const MAX_GATES = 20;

/** Deutsche Namen und Schaltzeichen nach DIN EN 60617 (im Unterricht üblich). */
export const GATE_INFO: Record<GateType, { name: string; symbol: string; negated: boolean }> = {
	AND: { name: 'UND', symbol: '&', negated: false },
	OR: { name: 'ODER', symbol: '≥1', negated: false },
	NOT: { name: 'NICHT', symbol: '1', negated: true },
	XOR: { name: 'XOR', symbol: '=1', negated: false },
	NAND: { name: 'NAND', symbol: '&', negated: true },
	NOR: { name: 'NOR', symbol: '≥1', negated: true },
	XNOR: { name: 'XNOR', symbol: '=1', negated: true }
};

export function arity(type: GateType): number {
	return type === 'NOT' ? 1 : 2;
}

export function apply(type: GateType, a: boolean, b: boolean): boolean {
	switch (type) {
		case 'AND':
			return a && b;
		case 'OR':
			return a || b;
		case 'NOT':
			return !a;
		case 'XOR':
			return a !== b;
		case 'NAND':
			return !(a && b);
		case 'NOR':
			return !(a || b);
		case 'XNOR':
			return a === b;
	}
}

/** Belegung der Eingänge in Zeile index; der erste Eingang ist das höchstwertige Bit. */
export function row(inputs: string[], index: number): Record<string, boolean> {
	const values: Record<string, boolean> = {};
	inputs.forEach((name, i) => (values[name] = ((index >> (inputs.length - 1 - i)) & 1) === 1));
	return values;
}

/** Probleme der Schaltung, wie sie auch der Server meldet. Leer = auswertbar. */
export function check(
	circuit: Circuit,
	inputs: string[],
	allowed: string[] = GATE_TYPES,
	maxGates = MAX_GATES
): string[] {
	const problems: string[] = [];
	const limit = Math.min(maxGates, MAX_GATES);
	if (circuit.gates.length > limit) problems.push(`Höchstens ${limit} Gatter`);
	const known = new Set(inputs);
	for (const gate of circuit.gates) {
		if (!/^[a-z][a-z0-9]{0,9}$/.test(gate.id) || known.has(gate.id)) {
			problems.push(`Gatter '${gate.id}' braucht einen eindeutigen Namen`);
			continue;
		}
		if (!allowed.includes(gate.type)) problems.push(`Gatter ${gate.id}: Typ nicht erlaubt`);
		else if (gate.inputs.length !== arity(gate.type))
			problems.push(`Gatter ${gate.id} braucht ${arity(gate.type)} Eingang/Eingänge`);
		for (const input of gate.inputs) {
			if (!known.has(input)) problems.push(`Gatter ${gate.id}: Eingang fehlt`);
		}
		known.add(gate.id);
	}
	if (!known.has(circuit.output)) problems.push('Der Ausgang ist mit nichts verbunden');
	return problems;
}

/** Werte aller Eingänge und Gatter für eine Belegung (Schaltung muss gültig sein). */
export function signals(
	circuit: Circuit,
	inputs: string[],
	index: number
): Record<string, boolean> {
	const values = row(inputs, index);
	for (const gate of circuit.gates) {
		values[gate.id] = apply(gate.type, values[gate.inputs[0]], values[gate.inputs[1]] ?? false);
	}
	return values;
}

/** Ausgabe je Zeile, oder null, solange die Schaltung unvollständig ist. */
export function truthTable(circuit: Circuit, inputs: string[]): boolean[] | null {
	if (check(circuit, inputs).length) return null;
	return Array.from(
		{ length: 1 << inputs.length },
		(_, i) => signals(circuit, inputs, i)[circuit.output]
	);
}

/** Spalte je Gatter für den Schaltplan: Eingänge 0, jedes Gatter eins rechts von seiner tiefsten Quelle. */
export function depths(circuit: Circuit, inputs: string[]): Record<string, number> {
	const depth: Record<string, number> = {};
	for (const name of inputs) depth[name] = 0;
	for (const gate of circuit.gates) {
		depth[gate.id] = 1 + Math.max(0, ...gate.inputs.map((i) => depth[i] ?? 0));
	}
	return depth;
}

export function nextGateId(circuit: Circuit): string {
	let n = circuit.gates.length + 1;
	while (circuit.gates.some((g) => g.id === `g${n}`)) n++;
	return `g${n}`;
}

// Ausdrücke (für die Vorschau im Editor) ---------------------------------------------------

type Expr = (values: Record<string, boolean>) => boolean;

const OPS: Record<string, { level: number; gate: GateType }> = {
	OR: { level: 0, gate: 'OR' },
	'|': { level: 0, gate: 'OR' },
	'+': { level: 0, gate: 'OR' },
	'∨': { level: 0, gate: 'OR' },
	NOR: { level: 0, gate: 'NOR' },
	XOR: { level: 1, gate: 'XOR' },
	'^': { level: 1, gate: 'XOR' },
	'⊕': { level: 1, gate: 'XOR' },
	XNOR: { level: 1, gate: 'XNOR' },
	AND: { level: 2, gate: 'AND' },
	'&': { level: 2, gate: 'AND' },
	'*': { level: 2, gate: 'AND' },
	'·': { level: 2, gate: 'AND' },
	'∧': { level: 2, gate: 'AND' },
	NAND: { level: 2, gate: 'NAND' }
};

/** Wahrheitstabelle eines Ausdrucks; wirft Error mit verständlicher Meldung. Rangfolge NOT > AND > XOR > OR. */
export function expressionTable(text: string, inputs: string[]): boolean[] {
	const tokens = text.match(/[A-Za-z0-9_]+|\S/g) ?? [];
	if (!tokens.length) throw new Error('Ausdruck fehlt');
	let pos = 0;
	const peek = () => (tokens[pos] ?? '').toUpperCase();

	function level(n: number): Expr {
		if (n > 2) return unary(0);
		let left = level(n + 1);
		while (OPS[peek()]?.level === n) {
			const gate = OPS[tokens[pos++].toUpperCase()].gate;
			const right = level(n + 1);
			const l = left;
			left = (v) => apply(gate, l(v), right(v));
		}
		return left;
	}

	function unary(depth: number): Expr {
		if (depth > 50) throw new Error('Ausdruck zu tief verschachtelt');
		const token = tokens[pos++];
		if (token === undefined) throw new Error('Ausdruck endet zu früh');
		const upper = token.toUpperCase();
		if (upper === 'NOT' || token === '!' || token === '¬') {
			const inner = unary(depth + 1);
			return (v) => !inner(v);
		}
		if (token === '(') {
			const inner = level(0);
			if (tokens[pos++] !== ')') throw new Error('Schließende Klammer fehlt');
			return inner;
		}
		if (token === '0' || token === '1') return () => token === '1';
		if (!inputs.includes(token)) throw new Error(`Unbekannter Eingang '${token}'`);
		return (v) => v[token];
	}

	const expr = level(0);
	if (pos !== tokens.length) throw new Error(`Unerwartet: '${tokens[pos]}'`);
	return Array.from({ length: 1 << inputs.length }, (_, i) => expr(row(inputs, i)));
}
