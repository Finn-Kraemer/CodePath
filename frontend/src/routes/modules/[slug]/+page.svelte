<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
	import { api, ApiError, type ModuleDetail } from '$lib/api';
	import { DIFFICULTY_LABELS, STATUS_LABELS, TYPE_LABELS } from '$lib/tasks/labels';

	const slug = page.params.slug || '';
	let detail = $state<ModuleDetail | null>(null);
	let error = $state('');

	onMount(async () => {
		try {
			detail = await api<ModuleDetail>(`/api/modules/${slug}`);
		} catch (e) {
			if (e instanceof ApiError && e.status === 403) goto('/modules');
			else error = (e as Error).message;
		}
	});

	const difficultyColors: Record<string, string> = {
		EASY: 'border-green-200 text-green-700 bg-green-50',
		MEDIUM: 'border-amber-200 text-amber-700 bg-amber-50',
		HARD: 'border-rose-200 text-rose-700 bg-rose-50'
	};

	function tone(status?: string, locked?: boolean, blocked?: boolean) {
		if (status === 'SOLVED') return 'border-green-200 bg-green-50/30';
		if (status === 'PENDING_REVIEW') return 'border-blue-200 bg-blue-50/30';
		if (blocked) return 'border-amber-200 bg-amber-50/30 opacity-80';
		if (locked) return 'border-red-200 bg-red-50/30';
		return 'border-slate-200 bg-white shadow-sm hover:border-institutional-navy';
	}
</script>

<svelte:head><title>{detail?.module?.title ?? 'Modul'} | CodePath</title></svelte:head>

<div class="mx-auto max-w-5xl">
	<div class="mb-12 border-b border-slate-200 pb-8">
		<a
			href="/modules"
			class="mb-6 inline-flex font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
		>
			← Zurück zur Übersicht
		</a>
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			{detail?.module?.icon ?? ''}
			{detail?.module?.title ?? ''}
		</h1>
		{#if detail?.module}
			<p class="mt-4 text-slate-500">{detail.module.description}</p>
			<p class="mt-2 font-mono text-xs text-slate-400">
				{detail.module.completedTasks}/{detail.module.totalTasks} gelöst · {detail.module
					.pointsEarned} von {detail.module.maxPoints} Punkten
			</p>
		{/if}
	</div>

	{#if error}
		<div class="border border-red-200 bg-red-50 p-8 font-mono text-sm text-red-700">{error}</div>
	{:else if !detail}
		<div class="flex justify-center py-24">
			<div
				class="h-12 w-12 animate-spin border-4 border-slate-200 border-t-institutional-navy"
			></div>
		</div>
	{:else}
		<ol class="space-y-4">
			{#each detail.tasks ?? [] as task, i (task.slug)}
				<li>
					<svelte:element
						this={task.isBlocked ? 'div' : 'a'}
						href={task.isBlocked ? undefined : `/modules/${slug}/${task.slug}`}
						class="group flex items-center justify-between rounded-none border p-6 transition-all {tone(
							task.status,
							task.isLocked,
							task.isBlocked
						)}"
					>
						<div class="flex items-center gap-6">
							<div
								class="flex h-12 w-12 items-center justify-center font-mono text-[11px] font-black {task.status ===
								'SOLVED'
									? 'bg-green-600 text-white'
									: task.isBlocked
										? 'bg-amber-100 text-amber-800'
										: 'bg-slate-100 text-slate-500 group-hover:bg-institutional-navy group-hover:text-white'}"
							>
								{task.status === 'SOLVED' ? '✓' : task.isBlocked ? '🔒' : i + 1}
							</div>
							<div>
								<h2
									class="font-sans text-lg font-bold tracking-tight text-institutional-navy uppercase"
								>
									{task.title}
								</h2>
								<div class="mt-2 flex flex-wrap items-center gap-3">
									<span
										class="border px-2 py-0.5 font-mono text-[9px] font-bold tracking-widest uppercase {difficultyColors[
											task.difficulty ?? ''
										]}"
									>
										{DIFFICULTY_LABELS[task.difficulty ?? ''] ?? task.difficulty}
									</span>
									<span
										class="font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase"
										>{TYPE_LABELS[task.type ?? ''] ?? task.type}</span
									>
								</div>
							</div>
						</div>
						<div class="text-right font-mono text-[10px] font-black tracking-widest uppercase">
							<div class="text-slate-500">{task.pointsEarned}/{task.points} P.</div>
							<div
								class="mt-1 {task.status === 'SOLVED'
									? 'text-green-600'
									: task.isBlocked
										? 'text-amber-700'
										: 'text-slate-400'}"
							>
								{task.isBlocked
									? (task.blockedReason ?? 'Erst Vorgänger abschließen')
									: task.isLocked && task.status !== 'FAILED'
										? 'Gesperrt'
										: STATUS_LABELS[task.status ?? 'OPEN']}
							</div>
						</div>
					</svelte:element>
				</li>
			{/each}
		</ol>
	{/if}
</div>
