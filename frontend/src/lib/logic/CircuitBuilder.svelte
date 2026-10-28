<script lang="ts">
	import CircuitDiagram from './CircuitDiagram.svelte';
	import {
		arity,
		check,
		GATE_INFO,
		nextGateId,
		row,
		signals,
		truthTable,
		type Circuit,
		type GateType
	} from './logic';

	let {
		circuit = $bindable(),
		inputs,
		allowedGates,
		maxGates,
		expected = null,
		disabled = false
	}: {
		circuit: Circuit;
		inputs: string[];
		allowedGates: GateType[];
		maxGates: number;
		expected?: boolean[] | null;
		disabled?: boolean;
	} = $props();

	let selectedRow = $state(0);

	const problems = $derived(check(circuit, inputs, allowedGates, maxGates));
	const actual = $derived(truthTable(circuit, inputs));
	const values = $derived(problems.length ? null : signals(circuit, inputs, selectedRow));
	const rows = $derived(Array.from({ length: 1 << inputs.length }, (_, i) => i));

	function sources(index: number): string[] {
		return [...inputs, ...circuit.gates.slice(0, index).map((g) => g.id)];
	}

	function addGate() {
		const type = allowedGates[0];
		const id = nextGateId(circuit);
		const previous = circuit.gates.at(-1)?.id;
		circuit.gates = [
			...circuit.gates,
			{
				id,
				type,
				inputs: arity(type) === 1 ? [previous ?? inputs[0]] : [inputs[0], inputs[1] ?? inputs[0]]
			}
		];
		circuit.output = id;
	}

	function setType(index: number, type: GateType) {
		const gate = circuit.gates[index];
		const ins = gate.inputs.slice(0, arity(type));
		while (ins.length < arity(type)) ins.push(sources(index)[0]);
		circuit.gates[index] = { ...gate, type, inputs: ins };
	}

	function setInput(index: number, slot: number, source: string) {
		const ins = [...circuit.gates[index].inputs];
		ins[slot] = source;
		circuit.gates[index] = { ...circuit.gates[index], inputs: ins };
	}

	function remove(index: number) {
		const removed = circuit.gates[index].id;
		circuit.gates = circuit.gates.filter((_, i) => i !== index);
		if (circuit.output === removed) circuit.output = circuit.gates.at(-1)?.id ?? '';
	}
</script>

<div class="space-y-6">
	<CircuitDiagram {circuit} {inputs} {values} />

	<div class="grid gap-6 lg:grid-cols-[1fr_auto]">
		<section aria-label="Gatter">
			<h3 class="mb-3 text-xs font-black tracking-widest text-slate-500 uppercase">
				Gatter ({circuit.gates.length} von höchstens {maxGates})
			</h3>
			<div class="space-y-2">
				{#each circuit.gates as gate, i (gate.id)}
					<div class="flex flex-wrap items-center gap-2 border border-slate-200 bg-slate-50 p-2">
						<span class="w-8 font-mono text-xs font-bold text-slate-500">{gate.id}</span>
						<select
							class="min-w-36 border border-slate-300 bg-white py-1 pr-8 pl-2 text-sm"
							value={gate.type}
							onchange={(e) => setType(i, e.currentTarget.value as GateType)}
							aria-label={`Typ von ${gate.id}`}
							{disabled}
						>
							{#each allowedGates as type (type)}
								<option value={type}>{GATE_INFO[type].name} ({GATE_INFO[type].symbol})</option>
							{/each}
						</select>
						{#each gate.inputs as input, slot (slot)}
							<select
								class="min-w-16 border border-slate-300 bg-white py-1 pr-8 pl-2 font-mono text-sm"
								value={input}
								onchange={(e) => setInput(i, slot, e.currentTarget.value)}
								aria-label={`Eingang ${slot + 1} von ${gate.id}`}
								{disabled}
							>
								{#each sources(i) as source (source)}<option value={source}>{source}</option>{/each}
							</select>
						{/each}
						<button
							type="button"
							class="ml-auto px-2 text-slate-400 hover:text-rose-600"
							onclick={() => remove(i)}
							aria-label={`${gate.id} entfernen`}
							{disabled}>✕</button
						>
					</div>
				{/each}
			</div>
			<div class="mt-3 flex flex-wrap items-center gap-3">
				<button
					type="button"
					class="border-2 border-slate-300 bg-white px-3 py-2 text-xs font-bold hover:border-slate-500"
					onclick={addGate}
					disabled={disabled || circuit.gates.length >= maxGates}>+ Gatter</button
				>
				<label class="flex items-center gap-2 text-sm font-bold"
					>Ausgang Y =
					<select
						class="min-w-16 border border-slate-300 bg-white py-1 pr-8 pl-2 font-mono text-sm"
						bind:value={circuit.output}
						{disabled}
					>
						<option value="" disabled>–</option>
						{#each sources(circuit.gates.length) as source (source)}<option value={source}
								>{source}</option
							>{/each}
					</select></label
				>
			</div>
			{#if problems.length && circuit.gates.length}
				<ul class="mt-3 list-disc pl-5 text-xs text-amber-700">
					{#each problems as problem (problem)}<li>{problem}</li>{/each}
				</ul>
			{/if}
		</section>

		<section aria-label="Wahrheitstabelle">
			<h3 class="mb-3 text-xs font-black tracking-widest text-slate-500 uppercase">
				Wahrheitstabelle
			</h3>
			<table class="border-collapse font-mono text-sm">
				<thead>
					<tr>
						{#each inputs as name (name)}<th class="border border-slate-200 px-3 py-1">{name}</th
							>{/each}
						{#if expected}<th class="border border-slate-200 bg-slate-100 px-3 py-1">Soll</th>{/if}
						<th class="border border-slate-200 bg-slate-100 px-3 py-1">Y</th>
					</tr>
				</thead>
				<tbody>
					{#each rows as index (index)}
						{@const ok = expected && actual ? expected[index] === actual[index] : null}
						<tr
							class="cursor-pointer {selectedRow === index ? 'outline-2 outline-amber-400' : ''}
							{ok === true ? 'bg-green-50' : ''} {ok === false ? 'bg-rose-50' : ''}"
							onclick={() => (selectedRow = index)}
						>
							{#each inputs as name (name)}
								<td class="border border-slate-200 px-3 py-1 text-center"
									>{row(inputs, index)[name] ? 1 : 0}</td
								>
							{/each}
							{#if expected}
								<td class="border border-slate-200 px-3 py-1 text-center font-bold"
									>{expected[index] ? 1 : 0}</td
								>
							{/if}
							<td class="border border-slate-200 px-3 py-1 text-center"
								>{actual ? (actual[index] ? 1 : 0) : '–'}{#if ok === false}<span class="sr-only">
										(falsch)</span
									>{/if}</td
							>
						</tr>
					{/each}
				</tbody>
			</table>
			<p class="mt-2 max-w-48 text-xs text-slate-500">
				Zeile anklicken: Der Schaltplan zeigt dann die Signale (gelb = 1).
			</p>
		</section>
	</div>
</div>
