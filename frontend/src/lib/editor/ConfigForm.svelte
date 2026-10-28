<script lang="ts">
	import MultipleChoiceForm from './config/MultipleChoiceForm.svelte';
	import BlanksForm from './config/BlanksForm.svelte';
	import CodeForm from './config/CodeForm.svelte';
	import SortingForm from './config/SortingForm.svelte';
	import SqlForm from './config/SqlForm.svelte';
	import TerminalForm from './config/TerminalForm.svelte';
	import PracticeForm from './config/PracticeForm.svelte';
	import MatchingForm from './config/MatchingForm.svelte';
	import ParsonsForm from './config/ParsonsForm.svelte';
	import NumberConvertForm from './config/NumberConvertForm.svelte';
	import HotspotForm from './config/HotspotForm.svelte';
	import ImageHotspotForm from './config/ImageHotspotForm.svelte';
	import LogicCircuitForm from './config/LogicCircuitForm.svelte';
	import DevToolsForm from './config/DevToolsForm.svelte';
	import JsonForm from './config/JsonForm.svelte';
	import type { Config } from './types';

	let {
		type,
		config = $bindable(),
		points
	}: { type: string; config: Config; points: number } = $props();
	let raw = $state(false);
</script>

<div class="mb-2 flex justify-end">
	<label class="flex items-center gap-2 text-xs text-slate-500"
		><input type="checkbox" bind:checked={raw} /> als JSON bearbeiten</label
	>
</div>
{#if raw}
	<JsonForm bind:config />
{:else if type === 'MULTIPLE_CHOICE'}
	<MultipleChoiceForm bind:config />
{:else if type === 'FILL_BLANK'}
	<BlanksForm bind:config />
{:else if type === 'FILL_CODE'}
	<BlanksForm bind:config code />
{:else if type === 'CODE'}
	<CodeForm bind:config />
{:else if type === 'SORTING'}
	<SortingForm bind:config />
{:else if type === 'SQL'}
	<SqlForm bind:config />
{:else if type === 'TERMINAL'}
	<TerminalForm bind:config />
{:else if type === 'PRACTICE'}
	<PracticeForm bind:config {points} />
{:else if type === 'MATCHING'}
	<MatchingForm bind:config />
{:else if type === 'PARSONS'}
	<ParsonsForm bind:config />
{:else if type === 'NUMBER_CONVERT'}
	<NumberConvertForm bind:config />
{:else if type === 'HOTSPOT'}
	<HotspotForm bind:config />
{:else if type === 'IMAGE_HOTSPOT'}
	<ImageHotspotForm bind:config />
{:else if type === 'LOGIC_CIRCUIT'}
	<LogicCircuitForm bind:config />
{:else if type === 'DEVTOOLS'}
	<DevToolsForm bind:config />
{:else}
	<JsonForm bind:config />
{/if}
