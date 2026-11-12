<script lang="ts">
	import { api } from '$lib/api';

	/** Leitfragen fürs Lerntagebuch eines Moduls (Lehrkraft), eine je Zeile. */
	let { moduleId }: { moduleId: number } = $props();

	let text = $state<string | null>(null);
	let message = $state('');

	async function load() {
		const res = await api<{ prompts: string[] }>(`/api/admin/modules/${moduleId}/journal-prompts`);
		text = res.prompts.join('\n');
	}

	async function save() {
		message = '';
		try {
			const res = await api<{ prompts: string[] }>(
				`/api/admin/modules/${moduleId}/journal-prompts`,
				{ method: 'PUT', body: { prompts: text ?? '' } }
			);
			text = res.prompts.join('\n');
			message = 'Gespeichert.';
		} catch (e) {
			message = (e as Error).message;
		}
	}
</script>

<details
	class="border-t border-slate-100"
	ontoggle={(e) => {
		if (e.currentTarget.open && text === null) load();
	}}
>
	<summary
		class="cursor-pointer px-8 py-4 font-mono text-[10px] font-bold tracking-widest text-slate-500 uppercase hover:text-institutional-navy"
	>
		Leitfragen fürs Lerntagebuch
	</summary>
	<div class="space-y-2 px-8 pb-6 text-xs">
		<p class="text-slate-500">
			Erscheinen über dem Lerntagebuch dieses Moduls, eine Frage je Zeile. Die Notizen selbst siehst
			du nicht – nur, was Schülerinnen und Schüler mit dir teilen (Schülerakte).
		</p>
		<textarea
			bind:value={text}
			rows="3"
			maxlength="2000"
			placeholder="Was hast du heute gelernt? (eine Frage je Zeile)"
			class="w-full border-slate-200 text-xs"
			aria-label="Leitfragen"
		></textarea>
		<button
			type="button"
			onclick={save}
			class="bg-institutional-navy px-4 py-2 text-[10px] font-black tracking-widest text-white uppercase"
			>Speichern</button
		>
		{#if message}<span class="ml-2 text-slate-500" role="status">{message}</span>{/if}
	</div>
</details>
