<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type Schemas } from '$lib/api';
	import { STATUS, type AttendanceStatus } from '$lib/attendance';

	type PersonView = Schemas['AttendancePersonView'];

	/** Anwesenheit einer Person: mit `username` für die Lehrkraft, sonst die eigene (falls sichtbar). */
	let { username }: { username?: string } = $props();

	let person = $state<PersonView | null>(null);

	onMount(async () => {
		try {
			if (username) {
				person = await api<PersonView>(
					`/api/admin/students/${encodeURIComponent(username)}/attendance`
				);
			} else {
				const status = await api<{ visible: boolean }>('/api/attendance/status');
				if (status.visible) person = await api<PersonView>('/api/attendance/me');
			}
		} catch {
			person = null;
		}
	});
</script>

{#if person}
	<section
		class="rounded-[var(--radius-ui)] border border-slate-200 bg-white p-8 shadow-sm"
		data-testid="attendance-summary"
	>
		<h3 class="mb-2 font-sans text-sm font-black tracking-wider text-slate-400 uppercase">
			Anwesenheit
		</h3>
		<p class="text-lg font-bold text-institutional-navy">{person.rate.text}</p>
		{#if person.entries.length > 0}
			<ul class="mt-4 space-y-1 text-xs">
				{#each person.entries as e (e.date + (e.title ?? ''))}
					<li class="flex gap-3">
						<span class="w-24 font-mono text-slate-500"
							>{new Date(e.date + 'T00:00').toLocaleDateString('de-DE')}</span
						>
						<span class="w-24 {STATUS[e.status as AttendanceStatus].cell}"
							>{STATUS[e.status as AttendanceStatus].label}</span
						>
						<span class="text-slate-500">{e.title ?? ''}{e.note ? ` – ${e.note}` : ''}</span>
					</li>
				{/each}
			</ul>
		{/if}
	</section>
{/if}
