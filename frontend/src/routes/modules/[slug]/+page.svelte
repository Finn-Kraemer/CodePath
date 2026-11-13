<script lang="ts">
	import { auth } from '$lib/auth.svelte';
	import JournalPanel from '$lib/journal/JournalPanel.svelte';
	import PathIcon from '$lib/pathway/PathIcon.svelte';
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
	<div class="mb-6">
		<a
			href="/modules"
			class="inline-flex items-center gap-1.5 text-sm font-semibold text-slate-600 hover:text-institutional-navy"
		>
			<svg
				class="h-4 w-4"
				viewBox="0 0 24 24"
				fill="none"
				stroke="currentColor"
				stroke-width="2.2"
				stroke-linecap="round"
				stroke-linejoin="round"
				aria-hidden="true"><path d="M15 6l-6 6 6 6" /></svg
			>
			Übersicht
		</a>
		<div class="ui-card mt-3 flex flex-wrap items-center gap-5 p-6">
			{#if detail?.module}
				<span
					class="flex h-14 w-14 shrink-0 items-center justify-center rounded-[var(--radius-ui)] bg-institutional-navy text-white"
				>
					<PathIcon emoji={detail.module.icon} title={detail.module.title} size={28} />
				</span>
			{/if}
			<div class="min-w-0 flex-1 basis-72">
				<h1 class="ui-title">{detail?.module?.title ?? ''}</h1>
				{#if detail?.module}
					<p class="mt-1 text-[15px] text-slate-600">{detail.module.description}</p>
				{/if}
			</div>
			{#if detail?.module}
				{@const pct = Math.round(
					((detail.module.completedTasks ?? 0) / (detail.module.totalTasks || 1)) * 100
				)}
				<div class="w-full sm:w-56">
					<div class="mb-1.5 flex justify-between text-[13px] text-slate-600">
						<span>{detail.module.completedTasks}/{detail.module.totalTasks} gelöst</span>
						<span class="font-mono text-xs"
							>{detail.module.pointsEarned}/{detail.module.maxPoints} P.</span
						>
					</div>
					<div class="ui-bar h-2"><span style="width: {pct}%"></span></div>
				</div>
			{/if}
		</div>
	</div>

	{#if error}
		<div class="ui-card border-rose-200 bg-rose-50 p-6 text-sm text-rose-700">{error}</div>
	{:else if !detail}
		<div class="flex justify-center py-24">
			<div
				class="h-12 w-12 animate-spin border-4 border-slate-200 border-t-institutional-navy"
			></div>
		</div>
	{:else}
		<ol class="space-y-3">
			{#each detail.tasks ?? [] as task, i (task.slug)}
				<li>
					<svelte:element
						this={task.isBlocked ? 'div' : 'a'}
						href={task.isBlocked ? undefined : `/modules/${slug}/${task.slug}`}
						class="group flex items-center justify-between gap-4 rounded-[var(--radius-ui)] border px-5 py-4 transition-all {tone(
							task.status,
							task.isLocked,
							task.isBlocked
						)}"
					>
						<div class="flex min-w-0 items-center gap-4">
							<div
								class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full font-mono text-sm font-bold {task.status ===
								'SOLVED'
									? 'bg-emerald-600 text-white'
									: task.isBlocked
										? 'bg-amber-100 text-amber-800'
										: 'bg-slate-100 text-slate-500 group-hover:bg-institutional-navy group-hover:text-white'}"
							>
								{#if task.status === 'SOLVED'}✓{:else if task.isBlocked}<PathIcon
										name="lock"
										size={14}
									/>{:else}{i + 1}{/if}
							</div>
							<div>
								<h2 class="text-base font-bold tracking-tight text-institutional-navy sm:text-lg">
									{task.title}
								</h2>
								<div class="mt-1.5 flex flex-wrap items-center gap-2">
									<span
										class="rounded-full border px-2 py-0.5 text-xs font-semibold {difficultyColors[
											task.difficulty ?? ''
										]}"
									>
										{DIFFICULTY_LABELS[task.difficulty ?? ''] ?? task.difficulty}
									</span>
									<span class="text-xs font-medium text-slate-500"
										>{TYPE_LABELS[task.type ?? ''] ?? task.type}</span
									>
									{#if task.bonus}
										<span
											class="border border-amber-300 bg-amber-50 px-2 py-0.5 font-mono text-[11px] font-bold tracking-wider text-amber-800 uppercase"
											title="Punkte zählen nicht in der Rangliste">Zusatzaufgabe</span
										>
									{:else if task.personal}
										<span
											class="border border-sky-300 bg-sky-50 px-2 py-0.5 font-mono text-[11px] font-bold tracking-wider text-sky-800 uppercase"
											>Für dich freigegeben</span
										>
									{/if}
								</div>
							</div>
						</div>
						<div class="shrink-0 text-right text-[13px] font-semibold">
							<div class="font-mono text-xs text-slate-500">
								{task.pointsEarned}/{task.points} P.
							</div>
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
	{#if detail?.module && auth.user && auth.user.role !== 'ADMIN'}
		<div class="mt-10"><JournalPanel moduleSlug={slug} /></div>
	{/if}
</div>
