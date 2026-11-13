<script lang="ts">
	import CriterionInput from './CriterionInput.svelte';
	import type { Answers, Criterion } from './evaluation';

	let {
		criteria,
		answers = $bindable({}),
		readonly = false,
		compare,
		compareLabel = ''
	}: {
		criteria: Criterion[];
		answers?: Answers;
		readonly?: boolean;
		/** Antworten eines anderen Bogens mit denselben Kriterien-IDs (z. B. Selbsteinschätzung) */
		compare?: Answers | null;
		compareLabel?: string;
	} = $props();

	const compareIds = $derived(new Set(Object.keys(compare ?? {})));
</script>

<div class="flex flex-col gap-3">
	{#each criteria as criterion (criterion.id)}
		<CriterionInput
			{criterion}
			bind:value={answers[criterion.id]}
			{readonly}
			compare={compare?.[criterion.id]}
			compareLabel={compare && compareIds.has(criterion.id) ? compareLabel : ''}
		/>
	{/each}
</div>
