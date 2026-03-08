<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { auth } from '$lib/auth.svelte';

	interface Task {
		slug: string;
		title: string;
		description: string;
		type: string;
		difficulty: string;
		points: number;
		isCompleted: boolean;
		isLocked: boolean;
		supportUsed: boolean;
	}

	let tasks = $state<Task[]>([]);
	let loading = $state(true);
	let error = $state('');

	const difficultyPriority: Record<string, number> = {
		EASY: 1,
		MEDIUM: 2,
		HARD: 3
	};

	let sortedTasks = $derived(
		[...tasks].sort((a, b) => {
			const priorityA = difficultyPriority[a.difficulty] || 99;
			const priorityB = difficultyPriority[b.difficulty] || 99;
			return priorityA - priorityB;
		})
	);

	const slug = page.params.slug || '';

	onMount(async () => {
		try {
			const res = await auth.apiFetch(`/api/modules/${slug}`);
			if (res.status === 403) {
				import('$app/navigation').then(nav => nav.goto('/modules'));
				return;
			}
			if (!res.ok) throw new Error('Aufgaben konnten nicht geladen werden');
			tasks = await res.json();
		} catch (err: any) {
			error = err.message;
		} finally {
			loading = false;
		}
	});

	const difficultyColors: Record<string, string> = {
		EASY: 'border-green-200 text-green-700 bg-green-50',
		MEDIUM: 'border-amber-200 text-amber-700 bg-amber-50',
		HARD: 'border-rose-200 text-rose-700 bg-rose-50'
	};
</script>

<div class="mx-auto max-w-5xl">
	<div class="mb-12 border-b border-slate-200 pb-8">
		<a
			href="/modules"
			class="mb-6 inline-flex items-center font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase transition-colors hover:text-institutional-navy"
		>
			← Zurück zur Übersicht
		</a>
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Modul: {slug.replace('-', ' ')}
		</h1>
	</div>

	{#if loading}
		<div class="flex justify-center py-24">
			<div class="h-12 w-12 border-4 border-slate-200 border-t-institutional-navy animate-spin"></div>
		</div>
	{:else if error}
		<div class="border border-red-200 bg-red-50 p-8 text-red-700 font-mono text-sm">
			<span class="font-bold">FEHLER:</span> {error}
		</div>
	{:else}
		<div class="space-y-4">
			{#each sortedTasks as task (task.slug)}
				<a
					href={`/modules/${slug}/${task.slug}`}
					class="group flex items-center justify-between border p-8 transition-all rounded-none
                    {task.isLocked
						? 'border-red-200 bg-red-50/30'
						: task.isCompleted
							? task.supportUsed
								? 'border-amber-200 bg-amber-50/30'
								: 'border-green-200 bg-green-50/30'
							: 'border-slate-200 bg-white hover:border-institutional-navy shadow-sm'}"
				>
					<div class="flex items-center gap-8">
						<div
							class="flex h-14 w-14 items-center justify-center font-mono text-[11px] font-black transition-colors rounded-none
                            {task.isLocked
								? 'bg-red-600 text-white'
								: task.isCompleted
									? task.supportUsed
										? 'bg-amber-500 text-white'
										: 'bg-green-600 text-white'
									: 'bg-slate-100 text-slate-400 group-hover:bg-institutional-navy group-hover:text-white'}"
						>
							{#if task.isLocked}
								<svg xmlns="http://www.w3.org/2000/svg" class="h-7 w-7" viewBox="0 0 20 20" fill="currentColor">
									<path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
								</svg>
							{:else if task.isCompleted}
								<svg xmlns="http://www.w3.org/2000/svg" class="h-7 w-7" viewBox="0 0 20 20" fill="currentColor">
									<path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" />
								</svg>
							{:else}
								{task.points}
							{/if}
						</div>

						<div>
							<h3 class="font-sans text-lg font-bold tracking-tight text-institutional-navy uppercase transition-colors group-hover:text-amber-700">
								{task.title}
							</h3>
							<div class="mt-2 flex items-center gap-4">
								<span class="border px-2 py-0.5 font-mono text-[9px] font-bold tracking-widest uppercase {difficultyColors[task.difficulty]}">
									{task.difficulty}
								</span>
								<span class="font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase italic">
									{task.type.replace('_', ' ')}
								</span>
								{#if task.supportUsed && task.isCompleted}
									<span class="bg-amber-100 text-amber-700 font-mono text-[8px] font-bold px-2 py-0.5 tracking-wider uppercase">Punktabzug aktiv</span>
								{/if}
							</div>
						</div>
					</div>

					<div class="flex items-center gap-4">
						<span class="font-sans text-[10px] font-black tracking-widest uppercase transition-colors
                            {task.isLocked ? 'text-red-600' : task.isCompleted ? (task.supportUsed ? 'text-amber-600' : 'text-green-600') : 'text-slate-300 group-hover:text-institutional-navy'}">
							{task.isLocked ? 'Gesperrt' : task.isCompleted ? 'Abgeschlossen' : 'Aufgabe lösen →'}
						</span>
					</div>
				</a>
			{/each}
		</div>
	{/if}
</div>
