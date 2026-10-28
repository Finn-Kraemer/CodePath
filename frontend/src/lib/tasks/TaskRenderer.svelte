<script lang="ts">
	import MultipleChoice from './MultipleChoice.svelte';
	import FillBlank from './FillBlank.svelte';
	import Sorting from './Sorting.svelte';
	import Code from './Code.svelte';
	import Sql from './Sql.svelte';
	import Terminal from './Terminal.svelte';
	import Practice from './Practice.svelte';
	import Matching from './Matching.svelte';
	import Parsons from './Parsons.svelte';
	import NumberConvert from './NumberConvert.svelte';
	import Hotspot from './Hotspot.svelte';
	import ImageHotspot from './ImageHotspot.svelte';
	import LogicCircuit from './LogicCircuit.svelte';
	import type { TaskProps } from './types';

	let props: TaskProps = $props();
</script>

{#if props.task.type === 'MULTIPLE_CHOICE'}
	<MultipleChoice {...props} />
{:else if props.task.type === 'FILL_BLANK'}
	<FillBlank {...props} />
{:else if props.task.type === 'FILL_CODE'}
	<FillBlank {...props} code />
{:else if props.task.type === 'SORTING'}
	<Sorting {...props} />
{:else if props.task.type === 'CODE'}
	<Code {...props} />
{:else if props.task.type === 'SQL'}
	<Sql {...props} />
{:else if props.task.type === 'TERMINAL'}
	<Terminal {...props} />
{:else if props.task.type === 'PRACTICE'}
	<Practice {...props} />
{:else if props.task.type === 'MATCHING'}
	<Matching {...props} />
{:else if props.task.type === 'PARSONS'}
	<Parsons {...props} />
{:else if props.task.type === 'NUMBER_CONVERT'}
	<NumberConvert {...props} />
{:else if props.task.type === 'HOTSPOT'}
	<Hotspot {...props} />
{:else if props.task.type === 'IMAGE_HOTSPOT'}
	<ImageHotspot {...props} />
{:else if props.task.type === 'LOGIC_CIRCUIT'}
	<LogicCircuit {...props} />
{:else}
	<p class="border border-amber-200 bg-amber-50 p-6 text-sm">
		Dieser Aufgabentyp wird noch nicht angezeigt: {props.task.type}
	</p>
{/if}
