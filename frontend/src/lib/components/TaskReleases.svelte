<script lang="ts">
	import { api, type TaskReleaseView } from '$lib/api';
	import { courseSelection } from '$lib/courses.svelte';
	import { TYPE_LABELS } from '$lib/tasks/labels';

	/** Einzelne Aufgaben eines Moduls für den gewählten Kurs freigeben oder sperren. */
	let { moduleId, onchange }: { moduleId: number; onchange?: () => void } = $props();

	let tasks = $state<TaskReleaseView[] | null>(null);
	let error = $state('');
	let saving = $state<number | null>(null);

	async function load() {
		error = '';
		try {
			tasks = await api<TaskReleaseView[]>(
				`/api/admin/modules/${moduleId}/tasks${courseSelection.query()}`
			);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function setMode(task: TaskReleaseView, mode: string) {
		saving = task.id;
		error = '';
		try {
			const updated = await api<TaskReleaseView>(
				`/api/admin/modules/${moduleId}/tasks/${task.id}/release?mode=${mode}${courseSelection.query('&')}`,
				{ method: 'PUT' }
			);
			tasks = (tasks ?? []).map((t) => (t.id === updated.id ? updated : t));
			onchange?.();
		} catch (e) {
			error = (e as Error).message;
		} finally {
			saving = null;
		}
	}
</script>

<details
	class="border-t border-slate-100"
	ontoggle={(e) => {
		if (e.currentTarget.open && !tasks) load();
	}}
>
	<summary
		class="cursor-pointer px-8 py-4 font-mono text-[10px] font-bold tracking-widest text-slate-500 uppercase hover:text-institutional-navy"
	>
		Einzelne Aufgaben
	</summary>
	<div class="px-8 pb-6">
		{#if error}<p class="mb-2 text-xs text-rose-700" role="alert">{error}</p>{/if}
		{#if !tasks}
			<p class="text-xs text-slate-400">Lädt …</p>
		{:else}
			<ol class="divide-y divide-slate-100">
				{#each tasks as task, i (task.id)}
					<li class="flex items-center gap-3 py-2 text-xs" data-task={task.slug}>
						<span class="w-6 font-mono text-slate-400">{i + 1}</span>
						<span class="flex-1 {task.visible ? 'text-slate-700' : 'text-slate-400 line-through'}">
							{task.title}
							<span class="ml-1 font-mono text-[9px] text-slate-400 uppercase"
								>{TYPE_LABELS[task.type] ?? task.type} · {task.points} P.</span
							>
						</span>
						<select
							aria-label="Freigabe {task.title}"
							class="border border-slate-200 bg-white px-2 py-1 text-xs"
							value={task.mode ?? 'MODULE'}
							disabled={saving === task.id}
							onchange={(e) => setMode(task, e.currentTarget.value)}
						>
							<option value="MODULE">Wie Modul</option>
							<option value="SHOWN">Freigegeben</option>
							<option value="HIDDEN">Gesperrt</option>
						</select>
					</li>
				{/each}
			</ol>
			<p class="mt-3 text-[10px] text-slate-400 italic">
				„Freigegeben“ zeigt die Aufgabe auch bei gesperrtem Modul, „Gesperrt“ blendet sie aus.
				Punkte und Teilnahmebestätigung zählen nur sichtbare Aufgaben.
			</p>
		{/if}
	</div>
</details>
