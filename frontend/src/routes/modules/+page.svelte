<script lang="ts">
	import PathIcon from '$lib/pathway/PathIcon.svelte';
	import { onMount } from 'svelte';
	import {
		api,
		type ModuleSummary,
		type PathLink,
		type PathView,
		type Recommendation
	} from '$lib/api';
	import NextTask from '$lib/pathway/NextTask.svelte';
	import PathMap from '$lib/pathway/PathMap.svelte';
	import { pathFocus } from '$lib/pathway/focus.svelte';
	import { auth } from '$lib/auth.svelte';
	import { realtime } from '$lib/realtime.svelte';

	const VIEW_KEY = 'codepath.modules.view';

	let modules = $state<ModuleSummary[]>([]);
	let loading = $state(true);
	let now = $state(new Date());
	let next = $state<Recommendation | null>(null);
	let paths = $state<PathLink[]>([]);
	let pathSlug = $state('');
	let path = $state<PathView | null>(null);
	/** CodePath-Karte oder Liste; die Wahl merkt sich der Browser */
	let view = $state<'map' | 'list'>('map');
	let lastTrigger = 0;

	const isFocus = $derived(
		!!(path?.hideOverview || paths.some((p) => p.hideOverview)) && auth.user?.role !== 'ADMIN'
	);
	const teacherNotice = $derived(
		!!(path?.hideOverview || paths.some((p) => p.hideOverview)) && auth.user?.role === 'ADMIN'
	);

	$effect(() => {
		pathFocus.set(isFocus);
		if (isFocus) {
			view = 'map';
		}
	});

	$effect(() => {
		const trigger = realtime.leaderboardUpdateTrigger;
		if (trigger > lastTrigger) {
			lastTrigger = trigger;
			fetchModules();
		}
	});

	// Update now every second for countdowns
	onMount(() => {
		try {
			if (localStorage.getItem(VIEW_KEY) === 'list') view = 'list';
		} catch {
			// Ohne Speicher gilt die Karte
		}
		fetchModules();
		const interval = setInterval(() => (now = new Date()), 1000);
		return () => clearInterval(interval);
	});

	async function fetchModules() {
		try {
			[modules, next, paths] = await Promise.all([
				api<ModuleSummary[]>('/api/modules'),
				api<Recommendation | undefined>('/api/learning/next').then((r) => r ?? null),
				api<PathLink[]>('/api/codepaths')
			]);
			if (paths.length) await showPath(paths[0].slug);
		} catch (e) {
			console.error(e);
		} finally {
			loading = false;
		}
	}

	async function showPath(slug: string) {
		pathSlug = slug;
		path = await api<PathView>(`/api/codepaths/${slug}`);
	}

	function setView(value: 'map' | 'list') {
		view = value;
		try {
			localStorage.setItem(VIEW_KEY, value);
		} catch {
			// egal
		}
	}

	function getTimeLeft(deadline?: string) {
		if (!deadline) return '';
		const diff = new Date(deadline).getTime() - now.getTime();
		if (diff <= 0) return 'Abgelaufen';

		const h = Math.floor(diff / 3600000);
		const m = Math.floor((diff % 3600000) / 60000);
		const s = Math.floor((diff % 60000) / 1000);

		return `${h}h ${m}m ${s}s`;
	}

	function isExpired(deadline?: string) {
		if (!deadline) return false;
		return new Date(deadline).getTime() <= now.getTime();
	}
</script>

<div class="flex flex-col gap-6">
	{#if teacherNotice}
		<div
			class="ui-card flex items-center gap-3 border-sky-200 bg-sky-50 px-5 py-4 text-sm text-sky-900"
			role="status"
		>
			<PathIcon name="target" size={22} />
			<div>
				<p class="font-bold">Fokus-Modus aktiv</p>
				<p class="mt-0.5 text-[13px] text-sky-800">
					Schülerinnen und Schüler sehen in diesem Kurs nur den CodePath – die Gesamtübersicht der
					Module und die Pfadauswahl sind für sie ausgeblendet.
				</p>
			</div>
		</div>
	{/if}

	<header class="flex flex-wrap items-end justify-between gap-4">
		<div>
			<h1 class="ui-title sm:text-4xl">{isFocus ? 'CodePath' : 'Lernmodule'}</h1>
			<p class="mt-2 text-base text-slate-600">
				{isFocus
					? 'Folge deinem CodePath, um alle Stationen abzuschließen.'
					: paths.length
						? 'Jedes Modul ist ein Berufsfeld. Folge deinem CodePath oder wähle selbst.'
						: 'Jedes Modul ist ein Berufsfeld. Wähle eins aus, um loszulegen.'}
			</p>
		</div>
		{#if !isFocus && paths.length && modules.length}
			<div
				class="flex overflow-hidden rounded-[var(--radius-ui-sm)] border border-slate-300 bg-white"
				role="group"
				aria-label="Ansicht"
			>
				<button
					class="flex min-h-10 items-center gap-2 rounded-none px-4 text-sm font-semibold {view ===
					'map'
						? 'bg-institutional-navy text-white'
						: 'text-slate-600 hover:bg-slate-50'}"
					aria-pressed={view === 'map'}
					onclick={() => setView('map')}><PathIcon name="map" size={16} />CodePath</button
				>
				<button
					class="flex min-h-10 items-center gap-2 rounded-none border-l border-slate-300 px-4 text-sm font-semibold {view ===
					'list'
						? 'bg-institutional-navy text-white'
						: 'text-slate-600 hover:bg-slate-50'}"
					aria-pressed={view === 'list'}
					onclick={() => setView('list')}><PathIcon name="list" size={16} />Liste</button
				>
			</div>
		{/if}
	</header>

	{#if realtime.lastPathNotice && realtime.lastPathNotice.username === auth.user?.username}
		<div class="ui-card flex items-center justify-between gap-4 px-5 py-4 text-sm" role="status">
			<span>{realtime.lastPathNotice.message}</span>
			<button
				type="button"
				class="text-sm font-semibold text-slate-500 hover:text-institutional-navy"
				onclick={() => realtime.clearPathNotice()}
			>
				Ausblenden
			</button>
		</div>
	{/if}

	{#if next}
		<NextTask {next} />
	{/if}

	{#if loading}
		<div class="flex justify-center py-24">
			<div
				class="h-12 w-12 animate-spin rounded-full border-4 border-slate-200 border-t-institutional-navy"
			></div>
		</div>
	{:else if modules.length === 0 && !path}
		<div class="ui-card border-dashed p-16 text-center">
			<p class="text-slate-500">Aktuell sind keine Module zur Bearbeitung freigeschaltet.</p>
		</div>
	{:else if (view === 'map' || isFocus) && path}
		{@const pct = path.stations ? Math.round(((path.stationsDone ?? 0) / path.stations) * 100) : 0}
		<section class="flex flex-col gap-4" aria-label="CodePath">
			<div class="ui-card flex flex-wrap items-center gap-x-8 gap-y-4 px-6 py-5">
				<div class="min-w-0 flex-1 basis-80">
					<p class="ui-eyebrow">Dein CodePath</p>
					<h2 class="mt-1 text-2xl font-extrabold tracking-tight text-institutional-navy">
						{path.title}
					</h2>
					{#if path.description}<p class="mt-1 text-[15px] text-slate-600">
							{path.description}
						</p>{/if}
				</div>
				<div class="flex items-center gap-5">
					<div class="relative h-16 w-16 shrink-0">
						<svg
							viewBox="0 0 64 64"
							class="h-16 w-16 -rotate-90"
							role="img"
							aria-label="Fortschritt {pct} Prozent"
						>
							<circle cx="32" cy="32" r="26" fill="none" stroke="#e2e8f0" stroke-width="7" />
							<circle
								cx="32"
								cy="32"
								r="26"
								fill="none"
								stroke="#047857"
								stroke-width="7"
								stroke-linecap="round"
								stroke-dasharray="{(pct / 100) * 163.4} 163.4"
							/>
						</svg>
						<span
							class="absolute inset-0 flex items-center justify-center text-sm font-extrabold"
							aria-hidden="true">{pct}%</span
						>
					</div>
					<div>
						<p class="ui-eyebrow">Stationen</p>
						<p class="text-xl font-extrabold">
							{path.stationsDone}
							<span class="text-sm font-semibold text-slate-500">/ {path.stations}</span>
						</p>
					</div>
					{#if paths.length > 1 && !isFocus}
						<select
							class="ed-input mt-0 w-auto"
							aria-label="CodePath wählen"
							value={pathSlug}
							onchange={(e) => showPath((e.currentTarget as HTMLSelectElement).value)}
						>
							{#each paths as p (p.slug)}<option value={p.slug}>{p.title}</option>{/each}
						</select>
					{/if}
				</div>
			</div>
			<PathMap
				{path}
				onupdate={(updated) => {
					path = updated;
					fetchModules();
				}}
			/>
		</section>
	{:else}
		<div class="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
			{#each modules as mod (mod.slug)}
				{@const expired = isExpired(mod.availableUntil)}
				{@const done = mod.totalTasks > 0 && mod.completedTasks === mod.totalTasks}
				{@const started = (mod.completedTasks ?? 0) > 0}
				{@const pct = Math.round(((mod.completedTasks ?? 0) / (mod.totalTasks || 1)) * 100)}
				<article
					class="ui-card flex flex-col gap-4 p-6 transition-shadow {expired
						? 'border-dashed bg-slate-50'
						: 'hover:border-slate-300 hover:shadow-lg'}"
				>
					<div class="flex items-start gap-3">
						<span
							class="flex h-11 w-11 shrink-0 items-center justify-center rounded-[var(--radius-ui-sm)] {done
								? 'bg-emerald-100 text-emerald-700'
								: started
									? 'bg-institutional-navy text-white'
									: 'bg-slate-100 text-institutional-navy'}"
						>
							<PathIcon emoji={mod.icon} title={mod.title} size={22} />
						</span>
						<h3 class="min-w-0 flex-1 pt-2 text-lg leading-tight font-bold text-institutional-navy">
							{mod.title}
						</h3>
						<span
							class="ui-chip mt-1.5 {expired
								? 'ui-chip-danger'
								: done
									? 'ui-chip-done'
									: started
										? 'ui-chip-now'
										: ''}"
							>{expired ? 'Abgelaufen' : done ? 'Erledigt' : started ? 'In Arbeit' : 'Neu'}</span
						>
					</div>
					<p class="flex-1 text-[15px] leading-relaxed text-slate-600">{mod.description}</p>
					<div class="flex flex-col gap-1.5">
						<div class="flex justify-between text-[13px] text-slate-600">
							<span>{mod.completedTasks} von {mod.totalTasks} Aufgaben</span>
							<span class="font-mono text-xs">{mod.pointsEarned}/{mod.maxPoints} P.</span>
						</div>
						<div class="ui-bar">
							<span class={done ? '' : '!bg-institutional-gold'} style="width: {pct}%"></span>
						</div>
					</div>
					<div class="flex items-center justify-between gap-3 border-t border-slate-100 pt-4">
						{#if mod.availableUntil}
							<span
								class="flex items-center gap-2 font-mono text-xs font-bold {expired
									? 'text-rose-700'
									: 'text-amber-700'}"
							>
								<span
									class="h-2 w-2 rounded-full {expired
										? 'bg-rose-500'
										: 'animate-pulse bg-amber-500'}"
								></span>
								{expired ? 'Zeit abgelaufen' : `Noch ${getTimeLeft(mod.availableUntil)}`}
							</span>
						{:else}
							<span></span>
						{/if}
						<a
							href={`/modules/${mod.slug}`}
							class="ui-btn {expired ? '' : started && !done ? 'ui-btn-primary' : ''}"
						>
							{expired ? 'Ansehen' : 'Öffnen'}
						</a>
					</div>
				</article>
			{/each}
		</div>
	{/if}
</div>
