<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type Schemas } from '$lib/api';
	import Markdown from '$lib/Markdown.svelte';

	type SharedEntry = Schemas['SharedEntry'];

	/** Lerntagebuch-Einträge, die eine Person mit der Lehrkraft geteilt hat. */
	let { username }: { username: string } = $props();

	let entries = $state<SharedEntry[]>([]);

	onMount(async () => {
		try {
			entries = await api<SharedEntry[]>(
				`/api/admin/journal/shared?username=${encodeURIComponent(username)}`
			);
		} catch {
			entries = [];
		}
	});
</script>

{#if entries.length > 0}
	<section class="border border-slate-200 bg-white p-8 shadow-sm" data-testid="shared-journal">
		<h3 class="mb-4 font-sans text-sm font-black tracking-widest text-slate-400 uppercase">
			Geteilt aus dem Lerntagebuch
		</h3>
		<ul class="space-y-4">
			{#each entries as s (s.entry.id)}
				<li class="border-l-4 border-sky-400 pl-4">
					<p class="mb-1 text-xs font-bold text-institutional-navy">
						{s.entry.moduleTitle}{s.entry.taskTitle ? ` › ${s.entry.taskTitle}` : ''}
						<span class="font-normal text-slate-400"
							>· {new Date(s.entry.updatedAt).toLocaleString('de-DE', {
								dateStyle: 'short',
								timeStyle: 'short'
							})}</span
						>
					</p>
					<div class="prose prose-sm max-w-none"><Markdown source={s.entry.text} /></div>
				</li>
			{/each}
		</ul>
	</section>
{/if}
