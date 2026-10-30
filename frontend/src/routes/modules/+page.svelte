<script lang="ts">
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
	/** Karte oder Stationsliste des Lernpfads oder alle Module; die Wahl merkt sich der Browser */
	let view = $state<'map' | 'stations' | 'modules'>('map');
	let lastTrigger = 0;

	const isFocus = $derived(
		!!(path?.hideOverview || paths.some((p) => p.hideOverview)) && auth.user?.role !== 'ADMIN'
	);
	const teacherNotice = $derived(
		!!(path?.hideOverview || paths.some((p) => p.hideOverview)) && auth.user?.role === 'ADMIN'
	);

	$effect(() => {
		pathFocus.set(isFocus);
		if (isFocus && view === 'modules') {
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
			const stored = localStorage.getItem(VIEW_KEY);
			if (stored === 'stations' || stored === 'modules') view = stored;
			else if (stored === 'list') view = 'modules';
			else if (window.innerWidth < 640) view = 'stations';
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
				api<PathLink[]>('/api/learning-paths')
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
		path = await api<PathView>(`/api/learning-paths/${slug}`);
	}

	const progress = $derived(
		path && path.stations ? Math.round((path.stationsDone / path.stations) * 100) : 0
	);
	const views = $derived([
		{ id: 'map', label: 'Karte' },
		{ id: 'stations', label: 'Stationen' },
		...(isFocus ? [] : [{ id: 'modules', label: 'Alle Module' }])
	] as { id: 'map' | 'stations' | 'modules'; label: string }[]);

	function setView(value: 'map' | 'stations' | 'modules') {
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
			class="flex items-center gap-3 border-l-4 border-sky-600 bg-sky-50 px-4 py-2.5 text-sm text-sky-900"
			role="status"
		>
			<span aria-hidden="true">🎯</span>
			<span
				><strong>Fokus-Modus aktiv:</strong> Schülerinnen und Schüler sehen in diesem Kurs nur den Lernpfad.</span
			>
		</div>
	{/if}

	<header class="flex flex-wrap items-end justify-between gap-4">
		<div>
			<h1 class="text-2xl font-extrabold tracking-tight text-institutional-navy md:text-3xl">
				{isFocus ? 'Dein Lernpfad' : 'Lernmodule'}
			</h1>
			<p class="mt-1 text-sm text-slate-500">
				{isFocus
					? 'Arbeite die Stationen der Reihe nach ab.'
					: path
						? 'Folge deinem Lernpfad oder wähle selbst ein Modul.'
						: 'Wähle ein Modul aus, um mit den Aufgaben zu beginnen.'}
			</p>
		</div>
		{#if path}
			<div class="flex border border-slate-200 bg-white p-0.5" role="group" aria-label="Ansicht">
				{#each views as v (v.id)}
					<button
						type="button"
						class="px-3.5 py-1.5 text-xs font-bold transition-colors {view === v.id
							? 'bg-institutional-navy text-white'
							: 'text-slate-600 hover:text-institutional-navy'}"
						aria-pressed={view === v.id}
						onclick={() => setView(v.id)}>{v.label}</button
					>
				{/each}
			</div>
		{/if}
	</header>

	{#if realtime.lastPathNotice && realtime.lastPathNotice.username === auth.user?.username}
		<div
			class="flex items-center justify-between gap-4 border-l-4 border-institutional-navy bg-slate-50 px-4 py-2.5 text-sm text-institutional-navy"
			role="status"
		>
			<span>{realtime.lastPathNotice.message}</span>
			<button
				type="button"
				class="text-xs font-bold text-slate-500 hover:text-institutional-navy"
				onclick={() => realtime.clearPathNotice()}
			>
				Ausblenden
			</button>
		</div>
	{/if}

	{#if next}
		<NextTask {next} compact />
	{/if}

	{#if loading}
		<div class="flex justify-center py-24">
			<div
				class="h-12 w-12 animate-spin border-4 border-slate-200 border-t-institutional-navy"
			></div>
		</div>
	{:else if modules.length === 0 && !path}
		<div class="rounded-none border border-dashed border-slate-200 bg-white p-24 text-center">
			<p class="font-serif text-sm text-slate-400 italic">
				Aktuell sind keine Module zur Bearbeitung freigeschaltet.
			</p>
		</div>
	{:else if view !== 'modules' && path}
		<section class="flex flex-col gap-3" aria-label="Lernpfad">
			<div
				class="flex flex-wrap items-center justify-between gap-x-6 gap-y-2 border border-slate-200 bg-white px-4 py-3"
			>
				<div class="min-w-0">
					<div class="flex flex-wrap items-center gap-2">
						<h2 class="text-lg font-bold text-institutional-navy">{path.title}</h2>
						{#if paths.length > 1 && !isFocus}
							<select
								class="border border-slate-200 bg-white px-2 py-1 text-xs"
								aria-label="Lernpfad wählen"
								value={pathSlug}
								onchange={(e) => showPath((e.currentTarget as HTMLSelectElement).value)}
							>
								{#each paths as p (p.slug)}<option value={p.slug}>{p.title}</option>{/each}
							</select>
						{/if}
					</div>
					{#if path.description}<p class="text-sm text-slate-500">{path.description}</p>{/if}
				</div>
				<div class="flex min-w-48 flex-1 items-center gap-3 sm:max-w-xs">
					<div
						class="h-2 flex-1 bg-slate-100"
						role="progressbar"
						aria-valuenow={progress}
						aria-valuemin={0}
						aria-valuemax={100}
						aria-label="Fortschritt im Lernpfad"
					>
						<div class="h-full bg-emerald-500 transition-all" style="width: {progress}%"></div>
					</div>
					<span class="shrink-0 font-mono text-xs font-bold text-slate-600"
						>{path.stationsDone}/{path.stations} Stationen</span
					>
				</div>
			</div>
			<PathMap
				{path}
				hideViewSwitch
				viewMode={view === 'stations' ? 'list' : 'map'}
				onupdate={(updated) => {
					path = updated;
					fetchModules();
				}}
			/>
		</section>
	{:else}
		<div class="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
			{#each modules as mod (mod.slug)}
				<div
					class="relative flex flex-col rounded-none border border-slate-200 bg-white shadow-sm transition-all
                    {isExpired(mod.availableUntil)
						? 'opacity-80 grayscale'
						: 'hover:border-institutional-navy hover:shadow-md'}"
				>
					{#if isExpired(mod.availableUntil)}
						<div class="pointer-events-none absolute inset-x-0 top-4 z-10 flex justify-center">
							<div
								class="-rotate-3 border-2 border-white bg-rose-600 px-4 py-2 font-mono text-[10px] font-black tracking-[3px] text-white uppercase shadow-xl"
							>
								Bearbeitungszeit Abgelaufen
							</div>
						</div>
					{/if}

					<div class="flex-grow p-5">
						<div class="mb-3 flex items-center justify-between">
							<span
								class="text-4xl grayscale transition-all {isExpired(mod.availableUntil)
									? ''
									: 'hover:grayscale-0'}">{mod.icon}</span
							>
							{#if mod.completedTasks === mod.totalTasks && mod.totalTasks > 0}
								<span
									class="rounded-none bg-green-100 px-3 py-1 font-mono text-[9px] font-black tracking-widest text-green-700 uppercase"
									>Abgeschlossen</span
								>
							{/if}
						</div>
						<h3
							class="mb-3 font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase"
						>
							{mod.title}
						</h3>
						<p class="text-sm leading-relaxed text-slate-500">
							{mod.description}
						</p>
					</div>

					<!-- Progress & Action -->
					<div
						class="flex items-center justify-between gap-3 border-t border-slate-100 bg-slate-50/50 px-5 py-4"
					>
						<div class="flex flex-col gap-4">
							<div class="flex items-center gap-4">
								<div class="h-1.5 w-32 overflow-hidden rounded-none bg-slate-200">
									<div
										class="h-full bg-institutional-navy transition-all duration-1000"
										style="width: {((mod.completedTasks ?? 0) / (mod.totalTasks || 1)) * 100}%"
									></div>
								</div>
								<span
									class="font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
								>
									{mod.completedTasks}/{mod.totalTasks} · {mod.pointsEarned}/{mod.maxPoints} P.
								</span>
							</div>

							{#if mod.availableUntil}
								<div class="flex items-center gap-2">
									<div
										class="h-2 w-2 rounded-full {isExpired(mod.availableUntil)
											? 'bg-rose-500'
											: 'animate-pulse bg-amber-500'}"
									></div>
									<span
										class="font-mono text-[9px] font-bold tracking-widest uppercase {isExpired(
											mod.availableUntil
										)
											? 'text-rose-600'
											: 'text-amber-600'}"
									>
										{isExpired(mod.availableUntil)
											? 'Zeit abgelaufen'
											: `Noch: ${getTimeLeft(mod.availableUntil)}`}
									</span>
								</div>
							{/if}
						</div>

						<a
							href={`/modules/${mod.slug}`}
							class="rounded-none bg-institutional-navy px-8 py-3 text-[10px] font-black tracking-[3px] text-white uppercase shadow-sm transition-all hover:bg-slate-800"
						>
							{isExpired(mod.availableUntil) ? 'Ansehen' : 'Öffnen'}
						</a>
					</div>
				</div>
			{/each}
		</div>
	{/if}
</div>
