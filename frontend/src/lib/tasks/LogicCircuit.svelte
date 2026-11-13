<script lang="ts">
	import CircuitBuilder from '$lib/logic/CircuitBuilder.svelte';
	import CircuitDiagram from '$lib/logic/CircuitDiagram.svelte';
	import { check, type Circuit, type GateType } from '$lib/logic/logic';
	import SubmitButton from './SubmitButton.svelte';
	import { config, solution, type TaskProps } from './types';

	let { task, disabled, busy, submit }: TaskProps = $props();

	const cfg = $derived(
		config<{
			inputs: string[];
			expected: boolean[];
			allowedGates: GateType[];
			maxGates: number;
			expression?: string;
		}>(task)
	);
	const sol = $derived(solution<{ circuit: Circuit; expression: string }>(task));

	let circuit = $state<Circuit>({ gates: [], output: '' });

	$effect.pre(() => {
		const last = task.lastAnswer as Circuit | undefined;
		circuit =
			last && Array.isArray(last.gates)
				? JSON.parse(JSON.stringify({ gates: last.gates, output: last.output ?? '' }))
				: { gates: [], output: '' };
	});

	const complete = $derived(
		check(circuit, cfg.inputs, cfg.allowedGates, cfg.maxGates).length === 0
	);
</script>

<div class="ui-card p-6 sm:p-8">
	<p class="mb-5 text-sm font-semibold text-slate-600">
		Baue eine Schaltung{cfg.expression
			? ` für Y = ${cfg.expression}`
			: ', die die Wahrheitstabelle erfüllt'}
	</p>
	<div class="mb-10">
		<CircuitBuilder
			bind:circuit
			inputs={cfg.inputs}
			allowedGates={cfg.allowedGates}
			maxGates={cfg.maxGates}
			expected={cfg.expected}
			{disabled}
		/>
	</div>
	{#if sol}
		<div class="mb-10">
			<p class="mb-2 text-xs font-black tracking-wider text-green-700 uppercase">
				Eine mögliche Lösung: Y = {sol.expression}
			</p>
			<CircuitDiagram circuit={sol.circuit} inputs={cfg.inputs} />
		</div>
	{/if}
	<SubmitButton
		{busy}
		disabled={disabled || !complete}
		label="Schaltung prüfen"
		onclick={() => submit({ gates: circuit.gates, output: circuit.output })}
	/>
</div>
