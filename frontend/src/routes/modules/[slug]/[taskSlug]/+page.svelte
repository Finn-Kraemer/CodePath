<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { auth } from '$lib/auth.svelte';
	import MultipleChoice from './MultipleChoice.svelte';
	import FillBlank from './FillBlank.svelte';
	import FillCode from './FillCode.svelte';
	import CodeTask from './CodeTask.svelte';
	import PracticeTask from './PracticeTask.svelte';

	interface Task {
		slug: string;
		title: string;
		story: string;
		description: string;
		type: string;
		difficulty: string;
		points: number;
		config: any;
		isCompleted: boolean;
		isLocked: boolean;
		supportUsed: boolean;
	}

	let task = $state<Task | null>(null);
	let loading = $state(true);
	let error = $state('');
	let supportUsed = $state(false);

	const moduleSlug = page.params.slug;
	const taskSlug = page.params.taskSlug;

	onMount(async () => {
		try {
			const res = await auth.apiFetch(`/api/modules/${moduleSlug}/${taskSlug}`);
			if (res.status === 403) {
				import('$app/navigation').then(nav => nav.goto('/modules'));
				return;
			}
			if (!res.ok) throw new Error('Aufgabe konnte nicht geladen werden');
			task = await res.json();
			if (task?.supportUsed) supportUsed = true;
		} catch (err: any) {
			error = err.message;
		} finally {
			loading = false;
		}
	});
</script>

<div class="mx-auto max-w-7xl">
	<div class="mb-12 border-b border-slate-200 pb-8">
		<a
			href={`/modules/${moduleSlug}`}
			class="mb-6 inline-flex items-center font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase transition-colors hover:text-institutional-navy"
		>
			← Zurück zum Kursmodul
		</a>

		{#if task}
			<div class="flex flex-col items-start justify-between gap-6 md:flex-row md:items-center">
				<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
					{task.title}
				</h1>
				<div class="flex items-center gap-4">
					<span
						class="border border-institutional-navy/10 bg-slate-50 px-4 py-2 font-mono text-[10px] font-black tracking-widest text-institutional-navy uppercase rounded-none"
					>
						Wertung: {task.points} Punkte
					</span>
					{#if task.isLocked}
						<span
							class="flex items-center gap-2 border border-red-200 bg-red-50 px-4 py-2 font-mono text-[10px] font-black tracking-widest text-red-700 uppercase rounded-none"
						>
							<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
								<path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
							</svg>
							Gesperrt
						</span>
					{:else if task.isCompleted}
						<span
							class="flex items-center gap-2 border px-4 py-2 font-mono text-[10px] font-black tracking-widest uppercase rounded-none
							{task.supportUsed ? 'border-amber-200 bg-amber-50 text-amber-700' : 'border-green-200 bg-green-50 text-green-700'}"
						>
							<svg
								xmlns="http://www.w3.org/2000/svg"
								class="h-4 w-4"
								viewBox="0 0 20 20"
								fill="currentColor"
							>
								<path
									fill-rule="evenodd"
									d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
									clip-rule="evenodd"
								/>
							</svg>
							{task.supportUsed ? 'Teil-Validiert (50%)' : 'Validiert'}
						</span>
					{/if}
				</div>
			</div>
		{/if}
	</div>

	{#if loading}
		<div class="flex justify-center py-24">
			<div class="h-12 w-12 border-4 border-slate-200 border-t-institutional-navy animate-spin"></div>
		</div>
	{:else if error}
		<div class="border border-red-200 bg-red-50 p-8 text-red-700 font-mono text-sm rounded-none">
			<span class="font-bold uppercase tracking-widest">Systemfehler:</span> {error}
		</div>
	{:else if task}
		<div class="grid grid-cols-1 gap-12 lg:grid-cols-3">
			<div class="lg:col-span-2">
				{#if task.isLocked}
					<div class="mb-8 border border-red-200 bg-red-50 p-8 flex items-center gap-6 font-sans font-bold text-red-700 uppercase tracking-widest text-xs rounded-none shadow-sm">
						<svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10" viewBox="0 0 20 20" fill="currentColor">
							<path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
						</svg>
						Diese Aufgabe wurde nach zu vielen Fehlversuchen gesperrt.
					</div>
				{/if}

				<div
					class="prose prose-slate mb-12 max-w-none border border-slate-200 bg-white p-10 shadow-sm rounded-none"
				>
					<div class="mb-6 border-b border-slate-100 pb-4">
						<span class="font-mono text-[9px] font-bold tracking-[3px] text-slate-400 uppercase"
							>Aufgabenstellung</span
						>
					</div>
					
					{#if task.story}
						<div class="mb-8">
							<p class="mb-2 font-sans text-xs font-black tracking-widest text-institutional-navy uppercase">
								Kontext:
							</p>
							<div class="text-slate-600">
								{@html task.story}
							</div>
						</div>
					{/if}

					<div class="mb-4">
						<p class="mb-2 font-sans text-xs font-black tracking-widest text-institutional-navy uppercase">
							Aufgabenbeschreibung:
						</p>
						<div class="text-slate-600">
							{@html task.description}
						</div>
					</div>
				</div>

				<div class={task.isLocked ? 'opacity-50 pointer-events-none grayscale' : ''}>
					{#if task.type === 'MULTIPLE_CHOICE'}
						<MultipleChoice {task} {moduleSlug} {supportUsed} />
					{:else if task.type === 'FILL_BLANK'}
						<FillBlank {task} {moduleSlug} {supportUsed} />
					{:else if task.type === 'FILL_CODE'}
						<FillCode {task} {moduleSlug} {supportUsed} />
					{:else if task.type === 'CODE'}
						<CodeTask {task} {moduleSlug} {supportUsed} />
					{:else if task.type === 'PRACTICE'}
						<PracticeTask {task} {moduleSlug} {supportUsed} />
					{/if}
				</div>
			</div>

			<div class="space-y-8">
				<div class="bg-institutional-navy p-10 text-white shadow-sm rounded-none">
					<h3
						class="mb-6 font-sans text-[10px] font-black tracking-[4px] text-institutional-gold uppercase"
					>
						Unterstützung
					</h3>

					{#if !supportUsed && !task.isCompleted && !task.isLocked}
						<button
							onclick={() => {
								if (confirm('Wenn Sie die Unterstützung nutzen, erhalten Sie für diese Aufgabe nur noch die hälfte der Punkte. Möchten Sie fortfahren?')) {
									supportUsed = true;
								}
							}}
							class="w-full border border-institutional-gold/30 bg-white/5 py-4 font-mono text-[9px] font-bold tracking-[2px] text-institutional-gold uppercase transition-all hover:bg-white/10 rounded-none"
						>
							Hilfestellung anfordern
						</button>
						<p class="mt-4 text-[10px] text-slate-400 italic leading-relaxed">
							Hinweis: Die Inanspruchnahme reduziert die erreichbare Punktzahl um 50%.
						</p>
					{:else}
						<div class="animate-in fade-in slide-in-from-top-2 duration-500">
							<p class="font-sans text-sm leading-relaxed italic text-slate-300">
								"{task.config.support || task.config.hint ||
									'Prüfen Sie Ihre Eingaben sorgfältig, bevor Sie die Validierung starten.'}"
							</p>
							{#if supportUsed && !task.isCompleted && !task.isLocked}
								<div class="mt-6 flex items-center gap-3 border border-amber-500/30 bg-amber-500/10 p-3">
									<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-amber-500" viewBox="0 0 20 20" fill="currentColor">
										<path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd" />
									</svg>
									<span class="font-mono text-[8px] font-bold tracking-widest text-amber-500 uppercase">Punktabzug aktiv (50%)</span>
								</div>
							{/if}
						</div>
					{/if}

					<div class="mt-8 border-t border-white/10 pt-6">
						<a
							href="/knowledge"
							class="text-[10px] font-bold tracking-widest text-institutional-gold underline transition-all hover:text-white uppercase"
							>Zum Wissens-Wiki →</a
						>
					</div>
				</div>

				<div class="border border-slate-200 bg-white p-10 shadow-sm rounded-none text-center">
					<span class="block mb-2 font-mono text-[9px] font-bold text-slate-400 uppercase tracking-widest">Modul-Status</span>
					<div class="text-xl font-black text-institutional-navy uppercase tracking-tighter">
						{moduleSlug?.replace('-', ' ')}
					</div>
				</div>
			</div>
		</div>
	{/if}
</div>
