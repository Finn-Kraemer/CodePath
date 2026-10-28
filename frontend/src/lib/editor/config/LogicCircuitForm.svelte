<script lang="ts">
	import CircuitBuilder from '$lib/logic/CircuitBuilder.svelte';
	import {
		expressionTable,
		GATE_INFO,
		GATE_TYPES,
		MAX_GATES,
		type GateType
	} from '$lib/logic/logic';
	import type { Config } from '../types';

	let { config = $bindable() }: { config: Config } = $props();

	config.solution ??= { gates: [], output: '' };
	const inputs = $derived((config.inputs ?? []) as string[]);
	const allowed = $derived(
		((config.allowedGates ?? []) as GateType[]).length
			? (config.allowedGates as GateType[])
			: GATE_TYPES
	);

	const preview = $derived.by(() => {
		try {
			return { table: expressionTable(config.expression ?? '', inputs), error: '' };
		} catch (e) {
			return { table: null, error: (e as Error).message };
		}
	});

	function setInputs(value: string) {
		config.inputs = value
			.split(/[\s,;]+/)
			.map((v) => v.trim().toUpperCase())
			.filter((v) => /^[A-Z]$/.test(v))
			.filter((v, i, all) => all.indexOf(v) === i)
			.slice(0, 4);
	}

	function toggleGate(type: GateType, on: boolean) {
		config.allowedGates = GATE_TYPES.filter((t) => (t === type ? on : allowed.includes(t)));
	}
</script>

<div class="grid gap-3 md:grid-cols-2">
	<label class="ed-label"
		>Eingänge (1–4 Großbuchstaben)<input
			class="ed-input mono"
			value={inputs.join(', ')}
			onchange={(e) => setInputs(e.currentTarget.value)}
		/></label
	>
	<label class="ed-label"
		>Höchstens Gatter<input
			type="number"
			class="ed-input"
			min="1"
			max={MAX_GATES}
			bind:value={config.maxGates}
		/></label
	>
</div>

<label class="ed-label mt-3"
	>Ausdruck (z. B. <code>(A OR B) AND NOT C</code>; auch &amp;, |, !, ^)<input
		class="ed-input mono"
		bind:value={config.expression}
	/></label
>
{#if preview.error}
	<p class="mt-1 text-xs text-rose-700">{preview.error}</p>
{/if}
<label class="mt-2 flex items-center gap-2 text-sm">
	<input
		type="checkbox"
		checked={config.showExpression !== false}
		onchange={(e) => (config.showExpression = e.currentTarget.checked)}
	/> Ausdruck den Schülern zeigen (sonst nur die Wahrheitstabelle)
</label>

<fieldset class="mt-4">
	<legend class="ed-label">Erlaubte Gatter</legend>
	<div class="mt-1 flex flex-wrap gap-3 text-sm">
		{#each GATE_TYPES as type (type)}
			<label class="flex items-center gap-1"
				><input
					type="checkbox"
					checked={allowed.includes(type)}
					onchange={(e) => toggleGate(type, e.currentTarget.checked)}
				/>
				{GATE_INFO[type].name}</label
			>
		{/each}
	</div>
</fieldset>

<p class="ed-label mt-4">Musterschaltung (für Probelauf und Lösungsansicht)</p>
{#if inputs.length}
	<div class="mt-2">
		<CircuitBuilder
			bind:circuit={config.solution}
			{inputs}
			allowedGates={allowed}
			maxGates={config.maxGates ?? MAX_GATES}
			expected={preview.table}
		/>
	</div>
{/if}
