<script lang="ts">
	import { depths, GATE_INFO, type Circuit } from './logic';

	let {
		circuit,
		inputs,
		values = null
	}: { circuit: Circuit; inputs: string[]; values?: Record<string, boolean> | null } = $props();

	const COL = 140;
	const ROW = 84;
	const GATE = 56;

	// Spalten nach Tiefe, innerhalb der Spalte in Reihenfolge – reicht für Schulschaltungen
	const layout = $derived.by(() => {
		// Nur Gatter zeichnen, deren Quellen schon bekannt sind (unfertige Verbindungen fallen weg)
		const known: string[] = [...inputs];
		const valid = circuit.gates.filter((g) => {
			const ok = g.inputs.every((i) => known.includes(i));
			if (ok) known.push(g.id);
			return ok;
		});
		const depth = depths({ gates: valid, output: circuit.output }, inputs);
		const pos: Record<string, { x: number; y: number }> = {};
		inputs.forEach((name, r) => (pos[name] = { x: 30, y: 50 + r * ROW }));
		// Spalte für Spalte: jedes Gatter auf die mittlere Höhe seiner (schon platzierten) Quellen,
		// dann Mindestabstand – so laufen Leitungen selten durch Gatter
		const columns: string[][] = [];
		for (const gate of valid) (columns[depth[gate.id]] ??= []).push(gate.id);
		for (let c = 1; c < columns.length; c++) {
			const column = columns[c] ?? [];
			for (const id of column) {
				const gate = valid.find((g) => g.id === id)!;
				const ys = gate.inputs.map((i) => pos[i].y);
				pos[id] = { x: 30 + c * COL, y: ys.reduce((sum, y) => sum + y, 0) / ys.length };
			}
			column.sort((x, y) => pos[x].y - pos[y].y);
			for (let i = 1; i < column.length; i++) {
				const above = pos[column[i - 1]].y;
				if (pos[column[i]].y < above + ROW) pos[column[i]].y = above + ROW;
			}
		}
		const lastColumn = Math.max(1, columns.length);
		const bottom = Math.max(...Object.values(pos).map((p) => p.y));
		return {
			valid,
			pos,
			width: 70 + lastColumn * COL,
			height: bottom + 50,
			outX: 30 + lastColumn * COL
		};
	});

	function outPoint(name: string) {
		const p = layout.pos[name];
		if (inputs.includes(name)) return { x: p.x + 18, y: p.y };
		const gate = circuit.gates.find((g) => g.id === name)!;
		return { x: p.x + GATE + (GATE_INFO[gate.type].negated ? 10 : 0), y: p.y };
	}

	function inPoint(gateId: string, index: number, count: number) {
		const p = layout.pos[gateId];
		return { x: p.x, y: count === 1 ? p.y : p.y + (index === 0 ? -14 : 14) };
	}

	function wire(from: { x: number; y: number }, to: { x: number; y: number }) {
		const dx = Math.max(24, (to.x - from.x) / 2);
		return `M ${from.x} ${from.y} C ${from.x + dx} ${from.y}, ${to.x - dx} ${to.y}, ${to.x} ${to.y}`;
	}

	function color(name: string) {
		if (!values || !(name in values)) return 'var(--wire, #64748b)';
		return values[name] ? '#f59e0b' : '#94a3b8';
	}

	const description = $derived(
		circuit.gates.length
			? `Schaltung mit ${circuit.gates.length} Gattern: ` +
					circuit.gates
						.map((g) => `${g.id} = ${GATE_INFO[g.type].name}(${g.inputs.join(', ')})`)
						.join('; ') +
					(circuit.output ? `; Ausgang Y = ${circuit.output}` : '')
			: 'Noch keine Gatter'
	);
</script>

<div class="overflow-x-auto border border-slate-200 bg-white">
	<svg
		width={layout.width}
		height={layout.height}
		viewBox={`0 0 ${layout.width} ${layout.height}`}
		role="img"
		aria-label={description}
		class="block"
	>
		{#each layout.valid as gate (gate.id)}
			{#each gate.inputs as source, i (i)}
				{#if layout.pos[source]}
					<path
						d={wire(outPoint(source), inPoint(gate.id, i, gate.inputs.length))}
						fill="none"
						stroke={color(source)}
						stroke-width={values?.[source] ? 3 : 2}
					/>
				{/if}
			{/each}
		{/each}
		{#if circuit.output && layout.pos[circuit.output]}
			<path
				d={wire(outPoint(circuit.output), { x: layout.outX, y: layout.pos[circuit.output].y })}
				fill="none"
				stroke={color(circuit.output)}
				stroke-width={values?.[circuit.output] ? 3 : 2}
			/>
			<g transform={`translate(${layout.outX + 4}, ${layout.pos[circuit.output].y})`}>
				<circle
					r="16"
					fill={values?.[circuit.output] ? '#fde68a' : '#f1f5f9'}
					stroke="#0f172a"
					stroke-width="2"
				/>
				<text text-anchor="middle" dy="5" font-size="15" font-weight="700" fill="#0f172a">Y</text>
			</g>
		{/if}

		{#each inputs as name (name)}
			{@const p = layout.pos[name]}
			<g transform={`translate(${p.x}, ${p.y})`}>
				<circle
					r="16"
					fill={values?.[name] ? '#fde68a' : '#f1f5f9'}
					stroke="#0f172a"
					stroke-width="2"
				/>
				<text text-anchor="middle" dy="5" font-size="15" font-weight="700" fill="#0f172a"
					>{name}</text
				>
				{#if values}
					<text x="0" y="32" text-anchor="middle" font-size="11" fill="#475569"
						>{values[name] ? 1 : 0}</text
					>
				{/if}
			</g>
		{/each}

		{#each layout.valid as gate (gate.id)}
			{@const p = layout.pos[gate.id]}
			{@const info = GATE_INFO[gate.type]}
			<g transform={`translate(${p.x}, ${p.y - GATE / 2})`}>
				<rect width={GATE} height={GATE} fill="white" stroke="#0f172a" stroke-width="2" />
				<text
					x={GATE / 2}
					y={GATE / 2 + 6}
					text-anchor="middle"
					font-size="17"
					font-weight="700"
					fill="#0f172a">{info.symbol}</text
				>
				{#if info.negated}
					<circle
						cx={GATE + 5}
						cy={GATE / 2}
						r="5"
						fill="white"
						stroke="#0f172a"
						stroke-width="2"
					/>
				{/if}
				<text x={GATE / 2} y="-6" text-anchor="middle" font-size="11" fill="#64748b">{gate.id}</text
				>
			</g>
		{/each}
	</svg>
</div>
