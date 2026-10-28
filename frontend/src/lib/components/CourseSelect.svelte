<script lang="ts">
	import { onMount } from 'svelte';
	import { courseSelection } from '$lib/courses.svelte';

	let { onchange }: { onchange?: () => void } = $props();

	onMount(async () => {
		if (courseSelection.courses.length === 0) await courseSelection.load();
		onchange?.();
	});
</script>

{#if courseSelection.courses.length === 0}
	<a
		href="/admin/courses"
		class="border border-amber-200 bg-amber-50 px-4 py-2 text-xs text-amber-800"
		>Noch kein Kurs – hier anlegen</a
	>
{:else}
	<label
		class="flex items-center gap-3 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
	>
		Kurs
		<select
			value={courseSelection.selectedId}
			onchange={(e) => {
				courseSelection.select(Number((e.currentTarget as HTMLSelectElement).value));
				onchange?.();
			}}
			class="border-slate-200 bg-white py-2 font-sans text-sm text-institutional-navy normal-case"
		>
			{#each courseSelection.courses as course (course.id)}
				<option value={course.id}
					>{course.name}{course.status === 'ENDED' ? ' (beendet)' : ''}</option
				>
			{/each}
		</select>
	</label>
{/if}
