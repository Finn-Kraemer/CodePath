<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type PathView, type NodeView } from '$lib/api';
	import { auth } from '$lib/auth.svelte';
	import { canvasSize, edgePath, incomingSlot, NODE_H, NODE_W } from './geometry';

	let {
		path,
		student,
		onupdate
	}: {
		path: PathView;
		student?: string;
		onupdate?: (updated: PathView) => void;
	} = $props();

	let choosing = $state(false);
	let choiceError = $state('');
	let breadcrumbs = $state<PathView[]>([]);
	let activeSubpath = $state<PathView | null>(null);
	let lastRootSlug = $state('');
	let activeGateNode = $state<NodeView | null>(null);

	// Zoom, Pan & Viewport
	let viewportEl = $state<HTMLDivElement | null>(null);
	let zoom = $state(1);
	let panX = $state(0);
	let panY = $state(0);
	let isDragging = $state(false);
	let dragStartX = 0;
	let dragStartY = 0;
	let initialPanX = 0;
	let initialPanY = 0;
	let viewMode = $state<'map' | 'list'>('map');
	let isMobile = $state(false);

	$effect(() => {
		if (path.slug !== lastRootSlug) {
			lastRootSlug = path.slug;
			breadcrumbs = [];
			activeSubpath = null;
		}
	});

	const currentPath = $derived(activeSubpath ?? path);
	const byId = $derived(new Map(currentPath.nodes.map((n) => [n.id, n])));
	const size = $derived(canvasSize(currentPath.nodes, 320, 200));
	const openChoices = $derived(
		currentPath.nodes.filter(
			(n) => n.kind === 'CHOICE' && n.state === 'AVAILABLE' && n.options?.length
		)
	);

	const STATE_LABELS: Record<string, string> = {
		DONE: 'Erledigt',
		AVAILABLE: 'Jetzt dran',
		BLOCKED: 'Noch gesperrt',
		UPCOMING: 'Später',
		CLOSED: 'Zeit abgelaufen',
		LOCKED: 'Gesperrt',
		EXCLUDED: 'nicht gewählt',
		SKIPPED: 'übersprungen (Fast-Track)'
	};

	const STATE_STYLES: Record<string, string> = {
		DONE: 'border-emerald-300 bg-emerald-50',
		AVAILABLE: 'border-institutional-navy bg-white shadow-md',
		BLOCKED: 'border-amber-300 bg-amber-50/40 opacity-85',
		UPCOMING: 'border-slate-200 bg-white',
		CLOSED: 'border-slate-200 bg-slate-50 opacity-70',
		LOCKED: 'border-dashed border-slate-300 bg-slate-50 opacity-60',
		EXCLUDED: 'border-dashed border-slate-300 bg-slate-100/80 opacity-60 grayscale',
		SKIPPED: 'border-dashed border-sky-300 bg-sky-50/70 text-slate-700'
	};

	function conditionBadge(cond?: {
		rule?: string;
		failedAttemptsThreshold?: number;
		scoreThreshold?: number;
	}): string | null {
		if (!cond || !cond.rule) return null;
		if (cond.rule === 'PERFECT_FIRST_TRY') return '⚡ 1. Versuch';
		if (cond.rule === 'STRUGGLED') {
			const fails = cond.failedAttemptsThreshold ?? 3;
			return `🛟 ≥ ${fails} Versuche`;
		}
		if (cond.rule === 'SCORE_AT_LEAST') {
			return `≥ ${Math.round(cond.scoreThreshold ?? 0)} %`;
		}
		if (cond.rule === 'DEFAULT') return 'Normalweg';
		return null;
	}

	onMount(() => {
		if (typeof window !== 'undefined') {
			isMobile = window.innerWidth < 640;
			if (isMobile) viewMode = 'list';
		}
		setTimeout(() => {
			centerOnRecommendedOrFit();
		}, 80);
	});

	$effect(() => {
		void currentPath.slug;
		if (viewportEl) {
			setTimeout(() => {
				centerOnRecommendedOrFit();
			}, 50);
		}
	});

	function centerOnRecommendedOrFit(force = false) {
		if (!viewportEl) return;
		const vpWidth = viewportEl.clientWidth || 800;
		const vpHeight = viewportEl.clientHeight || 500;
		const targetNode =
			currentPath.nodes.find((n) => n.recommended) ??
			currentPath.nodes.find((n) => n.state === 'AVAILABLE') ??
			currentPath.nodes[0];

		// Passt die ganze Karte lesbar hinein, alles zeigen – sonst auf die aktuelle Station
		const fits = Math.min((vpWidth - 40) / size.width, (vpHeight - 40) / size.height) >= 0.6;
		if ((fits && !force) || !targetNode) {
			fitToScreen();
		} else {
			zoom = 1;
			panX = Math.round(vpWidth / 2 - (targetNode.x + NODE_W / 2));
			panY = Math.round(vpHeight / 2 - (targetNode.y + NODE_H / 2));
		}
	}

	function fitToScreen() {
		if (!viewportEl) return;
		const vpWidth = viewportEl.clientWidth || 800;
		const vpHeight = viewportEl.clientHeight || 500;
		const pad = 40;
		const scaleX = (vpWidth - pad) / Math.max(size.width, 100);
		const scaleY = (vpHeight - pad) / Math.max(size.height, 100);
		const fitScale = Math.min(scaleX, scaleY);
		zoom = Math.min(Math.max(+fitScale.toFixed(2), 0.35), 1);
		panX = Math.round((vpWidth - size.width * zoom) / 2);
		panY = Math.round((vpHeight - size.height * zoom) / 2);
	}

	function zoomIn() {
		const newZoom = Math.min(2.5, +(zoom + 0.15).toFixed(2));
		zoomTowardsCenter(newZoom);
	}

	function zoomOut() {
		const newZoom = Math.max(0.35, +(zoom - 0.15).toFixed(2));
		zoomTowardsCenter(newZoom);
	}

	function zoomTowardsCenter(newZoom: number) {
		if (!viewportEl) {
			zoom = newZoom;
			return;
		}
		const vpWidth = viewportEl.clientWidth || 800;
		const vpHeight = viewportEl.clientHeight || 500;
		const centerX = vpWidth / 2;
		const centerY = vpHeight / 2;
		panX = Math.round(centerX - (centerX - panX) * (newZoom / zoom));
		panY = Math.round(centerY - (centerY - panY) * (newZoom / zoom));
		zoom = newZoom;
	}

	/** Zoomen nur mit Strg/⌘ – sonst scrollt das Mausrad wie gewohnt die Seite */
	function handleWheel(e: WheelEvent) {
		if (!e.ctrlKey && !e.metaKey) return;
		e.preventDefault();
		const delta = -e.deltaY * 0.0015;
		const newZoom = Math.min(2.5, Math.max(0.35, +(zoom + delta).toFixed(2)));
		if (viewportEl) {
			const rect = viewportEl.getBoundingClientRect();
			const mouseX = e.clientX - rect.left;
			const mouseY = e.clientY - rect.top;
			panX = Math.round(mouseX - (mouseX - panX) * (newZoom / zoom));
			panY = Math.round(mouseY - (mouseY - panY) * (newZoom / zoom));
		}
		zoom = newZoom;
	}

	function handlePointerDown(e: PointerEvent) {
		if (e.button !== 0) return;
		const target = e.target as HTMLElement | null;
		if (target?.closest('button, a, input, select')) return;

		isDragging = true;
		dragStartX = e.clientX;
		dragStartY = e.clientY;
		initialPanX = panX;
		initialPanY = panY;
		try {
			(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
		} catch {}
	}

	function handlePointerMove(e: PointerEvent) {
		if (!isDragging) return;
		panX = initialPanX + (e.clientX - dragStartX);
		panY = initialPanY + (e.clientY - dragStartY);
	}

	function handlePointerUp(e: PointerEvent) {
		if (isDragging) {
			isDragging = false;
			try {
				(e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
			} catch {}
		}
	}

	async function openSubpath(subSlug: string) {
		choiceError = '';
		try {
			const query = student ? `?student=${encodeURIComponent(student)}` : '';
			const sub = await api<PathView>(`/api/learning-paths/${subSlug}${query}`);
			breadcrumbs = [...breadcrumbs, currentPath];
			activeSubpath = sub;
		} catch (e) {
			choiceError = (e as Error).message;
		}
	}

	async function goBackTo(index: number) {
		choiceError = '';
		const target = breadcrumbs[index];
		breadcrumbs = breadcrumbs.slice(0, index);
		try {
			const query = student ? `?student=${encodeURIComponent(student)}` : '';
			const refreshed = await api<PathView>(`/api/learning-paths/${target.slug}${query}`);
			if (index === 0) {
				activeSubpath = null;
				path = refreshed;
				onupdate?.(refreshed);
			} else {
				activeSubpath = refreshed;
			}
		} catch {
			activeSubpath = index === 0 ? null : target;
		}
	}

	async function chooseBranch(nodeId: string, to: string, optionLabel: string) {
		if (
			!confirm(
				`Möchtest du den Weg „${optionLabel}“ wählen? Die anderen Wege sind danach gesperrt.`
			)
		) {
			return;
		}
		choiceError = '';
		choosing = true;
		try {
			const updated = await api<PathView>(`/api/learning-paths/${currentPath.slug}/choices`, {
				method: 'POST',
				body: { nodeId, to }
			});
			if (activeSubpath) {
				activeSubpath = updated;
				const rootUpdated = await api<PathView>(`/api/learning-paths/${path.slug}`);
				path = rootUpdated;
				onupdate?.(rootUpdated);
			} else {
				path = updated;
				onupdate?.(updated);
			}
		} catch (e) {
			choiceError = (e as Error).message;
		} finally {
			choosing = false;
		}
	}
</script>

{#if breadcrumbs.length > 0}
	{@const parent = breadcrumbs[breadcrumbs.length - 1]}
	<div
		class="mb-3 flex flex-wrap items-center justify-between gap-3 border border-violet-200 bg-violet-50/70 px-4 py-2.5 text-xs"
		role="navigation"
		aria-label="Baustein-Navigation"
	>
		<div class="flex items-center gap-2">
			<button
				type="button"
				class="font-mono text-[10px] font-black tracking-widest text-institutional-navy uppercase hover:underline"
				onclick={() => goBackTo(breadcrumbs.length - 1)}
			>
				← Zurück zu {parent.title}
			</button>
			<span class="text-slate-400">·</span>
			<span class="font-bold text-violet-900">🧩 {currentPath.title}</span>
		</div>
		<span class="font-mono text-[10px] font-bold text-violet-800 uppercase">
			{currentPath.stationsDone}/{currentPath.stations} erledigt
		</span>
	</div>
{/if}

{#if choiceError}
	<div class="mb-3 border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700" role="alert">
		{choiceError}
	</div>
{/if}

{#each openChoices as choiceNode (choiceNode.id)}
	<div
		class="mb-4 border-2 border-institutional-navy bg-white p-5 shadow-sm"
		role="region"
		aria-label="Weiche: {choiceNode.label}"
	>
		<div class="mb-3 flex items-center gap-2">
			<span class="text-xl">🔀</span>
			<div>
				<h3 class="text-sm font-black tracking-wide text-institutional-navy uppercase">
					{choiceNode.label}: Wähle deinen Weg
				</h3>
				<p class="text-xs text-slate-500">
					{student
						? 'Vorschau: Die Wahl trifft die Schülerin bzw. der Schüler selbst (oder du über „Wahl setzen“).'
						: 'Entscheide dich für einen Weg. Die anderen Wege sind danach gesperrt.'}
				</p>
			</div>
		</div>
		<div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
			{#each choiceNode.options ?? [] as opt (opt.to)}
				<div class="flex flex-col justify-between gap-3 border border-slate-200 bg-slate-50/60 p-4">
					<div>
						<div class="flex items-start justify-between gap-2">
							<span class="block text-sm font-bold text-institutional-navy">{opt.label}</span>
							{#if opt.bonusPoints && opt.bonusPoints > 0}
								<span
									class="shrink-0 rounded bg-amber-100 px-1.5 py-0.5 font-mono text-[10px] font-bold text-amber-800"
								>
									+{opt.bonusPoints} P.
								</span>
							{/if}
						</div>
						{#if opt.hint}
							<span class="mt-1 block text-xs text-slate-600">{opt.hint}</span>
						{/if}
					</div>
					{#if !student}
						<button
							type="button"
							class="bg-institutional-navy px-4 py-2 text-[10px] font-black tracking-widest text-white uppercase transition-colors hover:bg-slate-800 disabled:opacity-50"
							disabled={choosing}
							onclick={() => chooseBranch(choiceNode.id, opt.to, opt.label)}
						>
							Diesen Weg nehmen
						</button>
					{/if}
				</div>
			{/each}
		</div>
	</div>
{/each}

<div class="mb-3 flex flex-wrap items-center justify-between gap-2">
	<!-- View Switcher (Karte / Liste) -->
	<div class="inline-flex rounded border border-slate-200 bg-white p-0.5 text-xs font-semibold">
		<button
			type="button"
			class="rounded px-3 py-1 transition-colors {viewMode === 'map'
				? 'bg-institutional-navy text-white shadow-sm'
				: 'text-slate-600 hover:text-slate-900'}"
			onclick={() => {
				viewMode = 'map';
				setTimeout(centerOnRecommendedOrFit, 50);
			}}
		>
			🗺️ Karte
		</button>
		<button
			type="button"
			class="rounded px-3 py-1 transition-colors {viewMode === 'list'
				? 'bg-institutional-navy text-white shadow-sm'
				: 'text-slate-600 hover:text-slate-900'}"
			onclick={() => (viewMode = 'list')}
		>
			📋 Liste
		</button>
	</div>

	<!-- Canvas Controls only visible when viewMode === 'map' -->
	{#if viewMode === 'map'}
		<div
			class="inline-flex items-center gap-1 rounded border border-slate-200 bg-white p-1 text-xs shadow-sm"
			role="toolbar"
			aria-label="Karten-Steuerung"
		>
			<button
				type="button"
				class="rounded px-2.5 py-1 font-bold text-slate-700 transition-colors hover:bg-slate-100"
				onclick={zoomOut}
				title="Verkleinern (−)"
				aria-label="Verkleinern"
			>
				−
			</button>
			<span class="min-w-12 text-center font-mono text-[11px] font-semibold text-slate-500">
				{Math.round(zoom * 100)}%
			</span>
			<button
				type="button"
				class="rounded px-2.5 py-1 font-bold text-slate-700 transition-colors hover:bg-slate-100"
				onclick={zoomIn}
				title="Vergrößern (+)"
				aria-label="Vergrößern"
			>
				+
			</button>
			<div class="mx-1 h-4 w-px bg-slate-200"></div>
			<button
				type="button"
				class="rounded px-2.5 py-1 font-medium text-slate-700 transition-colors hover:bg-slate-100"
				onclick={fitToScreen}
				title="Auf Bildschirm einpassen"
			>
				⛶ Einpassen
			</button>
			<button
				type="button"
				class="rounded px-2.5 py-1 font-medium text-slate-700 transition-colors hover:bg-slate-100"
				onclick={() => centerOnRecommendedOrFit(true)}
				title="Aktuelle Station zentrieren"
			>
				🎯 Fokus
			</button>
			<span class="hidden px-2 text-[10px] text-slate-400 md:inline"
				>Strg + Mausrad: zoomen · ziehen: verschieben</span
			>
		</div>
	{/if}
</div>

{#if currentPath.nodes.some((n) => n.state === 'SKIPPED')}
	<div
		class="mb-3 flex items-center gap-2 rounded border border-sky-300 bg-sky-50 px-4 py-2 text-xs text-sky-900 shadow-sm"
		data-testid="fast-track-banner"
	>
		<span class="text-base">⚡</span>
		<span
			><strong>Fast-Track freigeschaltet!</strong> Du hast eine Abkürzung genommen. Übersprungene Stationen
			können freiwillig bearbeitet werden.</span
		>
	</div>
{/if}

{#if viewMode === 'list'}
	<div class="flex flex-col gap-3" data-testid="path-list">
		{#each currentPath.nodes.filter((n) => !(n.kind === 'REMEDIAL' && n.state === 'EXCLUDED')) as node (node.id)}
			{@const done = node.state === 'DONE'}
			{@const available = node.state === 'AVAILABLE'}
			{@const blocked = node.state === 'BLOCKED'}
			{@const excluded = node.state === 'EXCLUDED'}
			<div
				class="border-2 p-4 transition-all {STATE_STYLES[node.state]} {node.recommended
					? 'ring-4 ring-institutional-gold/40'
					: ''}"
			>
				<div class="flex items-start justify-between gap-3">
					<div class="flex items-start gap-3">
						<span class="text-2xl leading-none">
							{#if node.kind === 'MILESTONE'}
								{done ? '🏆' : blocked ? '🔒' : '🏁'}
							{:else if node.kind === 'CHOICE'}
								{blocked ? '🔒' : '🔀'}
							{:else if node.kind === 'BONUS'}
								⭐
							{:else if node.kind === 'AND' || node.kind === 'OR'}
								{node.icon}
							{:else if node.kind === 'REMEDIAL'}
								🛟
							{:else if node.kind === 'PATH'}
								{blocked || excluded ? '🔒' : '🧩'}
							{:else}
								{blocked || excluded ? '🔒' : node.icon}
							{/if}
						</span>
						<div>
							<div class="flex items-center gap-2">
								<h4 class="font-bold text-institutional-navy">
									{node.label}
								</h4>
								{#if node.boss}
									<span
										class="rounded bg-amber-200 px-1.5 py-0.5 font-mono text-[9px] font-black text-amber-900 uppercase"
									>
										👑 Boss
									</span>
								{/if}
								{#if node.recommended}
									<span
										class="rounded bg-amber-100 px-1.5 py-0.5 font-mono text-[9px] font-black text-amber-800 uppercase"
									>
										Empfohlen
									</span>
								{/if}
							</div>
							{#if node.kind === 'AND' || node.kind === 'OR'}
								{@const needed = node.kind === 'OR' ? (node.min ?? 1) : node.total}
								<p class="mt-0.5 font-mono text-xs text-slate-600">
									{node.done} von {needed} erfüllt
								</p>
							{:else if node.kind === 'MODULE' && node.total > 0}
								<p class="mt-0.5 font-mono text-xs text-slate-600">
									{node.done}/{node.total} Aufgaben erledigt
								</p>
							{:else if node.kind === 'PATH' && node.total > 0}
								<p class="mt-0.5 font-mono text-xs text-slate-600">
									{node.done}/{node.total} Stationen erledigt
								</p>
							{/if}
							{#if node.reason}
								<p class="mt-1 text-xs text-amber-700">{node.reason}</p>
							{/if}
							{#if node.gateProgress}
								<div class="mt-2 rounded border border-amber-200 bg-amber-50/60 p-2 text-xs">
									<div class="flex items-center justify-between gap-2 font-mono text-[11px]">
										<span class="font-bold text-slate-700">
											Punkte-Tor ({node.gateProgress.earnedPoints}/{node.gateProgress.maxPoints} P. =
											{node.gateProgress.percent} %)
										</span>
										<span
											class="font-bold {node.gateProgress.satisfied
												? 'text-emerald-700'
												: 'text-amber-800'}"
										>
											Gefordert: ≥ {node.requirement?.minPercent}%
										</span>
									</div>
									<div class="mt-1 flex items-center justify-between gap-2">
										{#if node.gateProgress.missingPoints > 0}
											<span class="font-bold text-amber-700"
												>Noch {node.gateProgress.missingPoints} Punkte nötig</span
											>
										{:else}
											<span class="font-bold text-emerald-700">Schwelle erreicht</span>
										{/if}
										<button
											type="button"
											class="text-[11px] font-bold text-institutional-navy hover:underline"
											onclick={() => (activeGateNode = node)}
										>
											Details & offene Aufgaben ({node.gateProgress.openTasks?.length ?? 0}) →
										</button>
									</div>
								</div>
							{/if}
						</div>
					</div>
					<div class="flex shrink-0 flex-col items-end gap-1">
						<span
							class="rounded px-2 py-0.5 font-mono text-[10px] font-bold tracking-wider uppercase {done
								? 'bg-emerald-100 text-emerald-800'
								: available
									? 'bg-institutional-navy text-white'
									: blocked
										? 'bg-amber-100 text-amber-800'
										: 'bg-slate-100 text-slate-600'}"
						>
							{STATE_LABELS[node.state] ?? node.state}
						</span>
						{#if node.points && node.points > 0}
							<span class="font-mono text-xs font-bold text-amber-700">+{node.points} P.</span>
						{/if}
					</div>
				</div>

				{#if node.kind === 'CHOICE' && node.state === 'AVAILABLE' && node.options?.length}
					<div class="mt-3 grid grid-cols-1 gap-2 border-t border-slate-200 pt-3 sm:grid-cols-2">
						{#each node.options as opt (opt.to)}
							<div class="flex flex-col justify-between gap-2 border border-slate-200 bg-white p-3">
								<div class="flex items-start justify-between gap-1">
									<span class="text-xs font-bold text-institutional-navy">{opt.label}</span>
									{#if opt.bonusPoints && opt.bonusPoints > 0}
										<span
											class="rounded bg-amber-100 px-1 font-mono text-[9px] font-bold text-amber-800"
										>
											+{opt.bonusPoints} P.
										</span>
									{/if}
								</div>
								{#if opt.hint}
									<span class="text-[11px] text-slate-500">{opt.hint}</span>
								{/if}
								{#if !student}
									<button
										type="button"
										class="mt-1 bg-institutional-navy px-3 py-1.5 text-[10px] font-black tracking-wider text-white uppercase hover:bg-slate-800 disabled:opacity-50"
										disabled={choosing}
										onclick={() => chooseBranch(node.id, opt.to, opt.label)}
									>
										Wählen
									</button>
								{/if}
							</div>
						{/each}
					</div>
				{:else if node.kind === 'PATH' && node.path && node.href}
					<div class="mt-3 border-t border-slate-200 pt-2">
						<button
							type="button"
							class="w-full bg-violet-700 px-3 py-2 text-center text-xs font-bold tracking-wide text-white uppercase hover:bg-violet-800"
							onclick={() => openSubpath(node.path!)}
						>
							Lernpfad-Baustein öffnen →
						</button>
					</div>
				{:else if node.href}
					<div class="mt-3 border-t border-slate-200 pt-2">
						<a
							href={node.href}
							class="block w-full bg-institutional-navy px-3 py-2 text-center text-xs font-bold tracking-wide text-white uppercase hover:bg-slate-800"
						>
							Öffnen →
						</a>
					</div>
				{/if}
			</div>
		{/each}
	</div>
{:else}
	<div
		bind:this={viewportEl}
		role="region"
		aria-label="Lernpfad-Netzwerkkarte"
		class="relative h-[calc(100vh-320px)] max-h-[820px] min-h-[480px] w-full cursor-grab touch-none overflow-hidden border border-slate-200 bg-slate-50/60 select-none active:cursor-grabbing"
		data-testid="path-map"
		onpointerdown={handlePointerDown}
		onpointermove={handlePointerMove}
		onpointerup={handlePointerUp}
		onpointercancel={handlePointerUp}
		onwheel={handleWheel}
	>
		<div
			class="relative transition-transform duration-75 ease-out"
			style="width: {size.width}px; height: {size.height}px; transform: translate({panX}px, {panY}px) scale({zoom}); transform-origin: 0 0;"
		>
			<svg
				class="pointer-events-none absolute inset-0"
				width={size.width}
				height={size.height}
				aria-hidden="true"
			>
				<defs>
					<marker
						id="arrow-done"
						viewBox="0 0 10 10"
						refX="8"
						refY="5"
						markerWidth="7"
						markerHeight="7"
						orient="auto-start-reverse"
					>
						<path d="M 0 0 L 10 5 L 0 10 z" class="fill-emerald-500" />
					</marker>
					<marker
						id="arrow-open"
						viewBox="0 0 10 10"
						refX="8"
						refY="5"
						markerWidth="7"
						markerHeight="7"
						orient="auto-start-reverse"
					>
						<path d="M 0 0 L 10 5 L 0 10 z" class="fill-slate-400" />
					</marker>
				</defs>
				{#each currentPath.edges as edge (edge.from + '>' + edge.to)}
					{@const from = byId.get(edge.from)}
					{@const to = byId.get(edge.to)}
					{#if from && to && !(from.kind === 'REMEDIAL' && from.state === 'EXCLUDED') && !(to.kind === 'REMEDIAL' && to.state === 'EXCLUDED')}
						{@const done = from.state === 'DONE'}
						{@const required = !!currentPath.enforce && !!edge.required}
						{@const isGateTarget = to.kind === 'AND' || to.kind === 'OR'}
						{@const slot = isGateTarget
							? incomingSlot(currentPath.edges, byId, edge)
							: { index: 0, count: 1 }}
						<path
							d={edgePath(from, to, slot.index, slot.count)}
							fill="none"
							stroke-width="2.5"
							class={done
								? 'stroke-emerald-500'
								: required
									? 'stroke-slate-400'
									: 'stroke-slate-300'}
							stroke-dasharray={done || required ? undefined : '6 5'}
							marker-end="url(#{done ? 'arrow-done' : 'arrow-open'})"
						/>
						{#if edge.condition && (student || auth.user?.role === 'ADMIN')}
							<!-- Bedingungen sieht nur die Lehrkraft in der Vorschau – niemand soll sich bloßgestellt fühlen -->
							{@const cBadge = conditionBadge(edge.condition)}
							{#if cBadge}
								{@const midX = (from.x + to.x) / 2 + NODE_W / 2}
								{@const midY = (from.y + to.y) / 2 + NODE_H / 2 - 12}
								<g transform="translate({midX}, {midY})">
									<rect
										x="-46"
										y="-9"
										width="92"
										height="18"
										rx="4"
										class="fill-white stroke-slate-300 shadow-sm"
										stroke-width="1"
									/>
									<text
										x="0"
										y="3"
										text-anchor="middle"
										font-size="8.5"
										font-family="sans-serif"
										font-weight="bold"
										class="pointer-events-none fill-slate-700">{cBadge}</text
									>
								</g>
							{/if}
						{/if}
					{/if}
				{/each}
			</svg>

			{#each currentPath.nodes.filter((n) => !(n.kind === 'REMEDIAL' && n.state === 'EXCLUDED')) as node (node.id)}
				{@const style = `left: ${node.x}px; top: ${node.y}px; width: ${NODE_W}px; height: ${NODE_H}px`}
				{#if node.kind === 'MILESTONE'}
					{@const isGate = !!node.requirement}
					{#if isGate}
						<button
							type="button"
							onclick={() => (activeGateNode = node)}
							class="absolute flex cursor-pointer flex-col items-center justify-center gap-1 rounded-full border-2 border-amber-500 bg-amber-50/80 px-4 text-center ring-2 ring-amber-400/50 transition-all hover:shadow-md"
							{style}
							title={node.reason ?? 'Punkte-Tor Details'}
						>
							<span class="text-lg">
								{node.state === 'DONE' ? '🏆' : '🚪'}
							</span>
							<span
								class="max-w-full truncate text-xs font-black tracking-wide text-institutional-navy uppercase"
							>
								{node.label}
							</span>
							{#if node.gateProgress}
								<div
									class="flex items-center gap-1 font-mono text-[9px] font-bold {node.gateProgress
										.satisfied
										? 'text-emerald-700'
										: 'text-amber-800'}"
								>
									<span>{node.gateProgress.percent}% / {node.requirement?.minPercent}%</span>
									{#if node.gateProgress.missingPoints > 0}
										<span class="rounded bg-amber-200 px-1 text-[8px]"
											>-{node.gateProgress.missingPoints}P</span
										>
									{/if}
								</div>
							{/if}
						</button>
					{:else}
						<div
							class="absolute flex flex-col items-center justify-center gap-1 rounded-full border-2 px-4 text-center {node.state ===
							'DONE'
								? 'border-institutional-gold bg-amber-50'
								: node.state === 'BLOCKED'
									? 'border-amber-300 bg-amber-50/40 opacity-85'
									: 'border-dashed border-slate-300 bg-white'}"
							{style}
							title={node.reason ?? undefined}
						>
							<span class="text-lg">
								{node.state === 'DONE' ? '🏆' : node.state === 'BLOCKED' ? '🔒' : '🏁'}
							</span>
							<span
								class="max-w-full truncate text-xs font-black tracking-wide text-institutional-navy uppercase"
							>
								{node.label}
							</span>
						</div>
					{/if}
				{:else if node.kind === 'CHOICE'}
					<div
						class="absolute flex flex-col justify-between border-2 p-3 {node.state === 'DONE'
							? 'border-emerald-400 bg-emerald-50'
							: node.state === 'AVAILABLE'
								? 'border-institutional-navy bg-white shadow-md ring-4 ring-institutional-gold/40'
								: 'border-amber-300 bg-amber-50/50'}"
						{style}
						title={node.reason ?? undefined}
						aria-label="Weiche {node.label}: {node.reason ?? STATE_LABELS[node.state]}"
					>
						<div class="flex items-center gap-2">
							<span class="text-xl leading-none">{node.state === 'BLOCKED' ? '🔒' : '🔀'}</span>
							<span
								class="line-clamp-2 text-xs font-black tracking-wide text-institutional-navy uppercase"
							>
								{node.label}
							</span>
						</div>
						<span
							class="line-clamp-1 font-mono text-[9px] font-bold tracking-widest uppercase {node.state ===
							'DONE'
								? 'text-emerald-700'
								: node.state === 'AVAILABLE'
									? 'text-institutional-navy'
									: 'text-amber-700'}"
						>
							{node.reason ?? STATE_LABELS[node.state]}
						</span>
					</div>
				{:else if node.kind === 'BONUS'}
					<div
						class="absolute flex flex-col justify-between border-2 p-3 {node.state === 'DONE'
							? 'border-amber-400 bg-amber-50 shadow-sm'
							: node.state === 'BLOCKED'
								? 'border-amber-300 bg-amber-50/40 opacity-85'
								: 'border-amber-300 bg-white'}"
						{style}
						title={node.reason ?? undefined}
						aria-label="Bonus-Station {node.label}: {node.reason ?? `+${node.points ?? 10} Punkte`}"
					>
						<div class="flex items-start justify-between gap-2">
							<div class="flex items-center gap-2">
								<span class="text-xl leading-none">⭐</span>
								<span class="line-clamp-2 text-sm leading-tight font-bold text-institutional-navy">
									{node.label}
								</span>
							</div>
							<span
								class="shrink-0 rounded bg-amber-100 px-1.5 py-0.5 font-mono text-[10px] font-bold text-amber-800"
							>
								+{node.points ?? 10} P.
							</span>
						</div>
						<div class="flex items-center justify-between gap-2">
							<span
								class="line-clamp-1 font-mono text-[9px] font-bold tracking-widest uppercase {node.state ===
								'DONE'
									? 'text-amber-700'
									: 'text-slate-400'}"
							>
								{node.reason ??
									(node.state === 'DONE'
										? `+${node.points ?? 10} erhalten`
										: STATE_LABELS[node.state])}
							</span>
						</div>
					</div>
				{:else if node.kind === 'AND' || node.kind === 'OR'}
					{@const needed = node.kind === 'OR' ? (node.min ?? 1) : node.total}
					<div
						class="absolute flex flex-col justify-between border-2 p-3 {node.state === 'DONE'
							? 'border-emerald-400 bg-emerald-50'
							: node.state === 'BLOCKED'
								? 'border-amber-300 bg-amber-50/50'
								: 'border-slate-300 bg-white'}"
						{style}
						title={node.reason ?? undefined}
						aria-label="{node.label}: {node.done} von {needed} erfüllt ({node.reason ?? ''})"
					>
						<div class="flex items-center gap-2.5">
							<span
								class="flex h-8 w-8 shrink-0 items-center justify-center border-2 border-slate-900 bg-white font-mono text-sm font-black text-institutional-navy"
							>
								{node.icon}
							</span>
							<div class="min-w-0">
								<span
									class="block truncate text-xs font-black tracking-wide text-institutional-navy uppercase"
								>
									{node.label}
								</span>
								<span class="block font-mono text-[10px] font-bold text-slate-600">
									{node.done} von {needed} erfüllt
								</span>
							</div>
						</div>
						<span
							class="line-clamp-1 font-mono text-[9px] font-bold tracking-widest uppercase {node.state ===
							'DONE'
								? 'text-emerald-700'
								: 'text-amber-700'}"
						>
							{node.reason ?? STATE_LABELS[node.state]}
						</span>
					</div>
				{:else if node.kind === 'PATH'}
					{@const canOpen = !!node.path && !!node.href}
					<svelte:element
						this={canOpen ? 'button' : 'div'}
						role={canOpen ? 'button' : 'group'}
						type={canOpen ? 'button' : undefined}
						onclick={canOpen ? () => openSubpath(node.path!) : undefined}
						class="absolute flex flex-col justify-between border-2 p-3 text-left transition-all {STATE_STYLES[
							node.state
						]} {canOpen
							? 'cursor-pointer hover:-translate-y-0.5 hover:shadow-lg'
							: ''} {node.recommended ? 'ring-4 ring-institutional-gold/40' : ''}"
						{style}
						title={node.reason ?? undefined}
						aria-label="{node.label}: {node.state === 'EXCLUDED'
							? `${node.label} – nicht gewählt`
							: `${node.done}/${node.total} erledigt`}"
					>
						<div class="flex items-start gap-2">
							<span class="text-xl leading-none"
								>{node.state === 'LOCKED' || node.state === 'BLOCKED' || node.state === 'EXCLUDED'
									? '🔒'
									: '🧩'}</span
							>
							<span class="line-clamp-2 text-sm leading-tight font-bold text-institutional-navy"
								>{node.state === 'EXCLUDED' ? `${node.label} – nicht gewählt` : node.label}</span
							>
						</div>
						<div class="flex items-center justify-between gap-2">
							<span
								class="line-clamp-1 font-mono text-[9px] font-bold tracking-widest uppercase {node.recommended
									? 'text-institutional-gold'
									: node.state === 'DONE'
										? 'text-emerald-700'
										: node.state === 'BLOCKED'
											? 'text-amber-700'
											: 'text-violet-700'}"
							>
								{node.recommended
									? '★ Empfohlen'
									: node.state === 'BLOCKED' && node.reason
										? node.reason
										: canOpen
											? `${node.done}/${node.total} erledigt`
											: STATE_LABELS[node.state]}
							</span>
							{#if node.total > 0}
								<span class="shrink-0 font-mono text-[9px] text-slate-400"
									>{node.done}/{node.total}</span
								>
							{/if}
						</div>
						{#if node.total > 0}
							<div class="absolute inset-x-0 bottom-0 h-1 bg-slate-100">
								<div
									class="h-full bg-emerald-500"
									style="width: {(node.done / node.total) * 100}%"
								></div>
							</div>
						{/if}
					</svelte:element>
				{:else}
					{@const isBoss = !!node.boss}
					{@const hasGate = !!node.requirement && !!node.gateProgress}
					<svelte:element
						this={node.href ? 'a' : 'div'}
						href={node.href ?? undefined}
						class="absolute flex flex-col justify-between border-2 p-3 transition-all {isBoss
							? node.state === 'DONE'
								? 'border-amber-400 bg-amber-50 shadow-sm ring-2 ring-amber-300'
								: 'border-purple-600 bg-purple-50/40 shadow-sm ring-2 ring-purple-400/40'
							: STATE_STYLES[node.state]} {node.href
							? 'hover:-translate-y-0.5 hover:shadow-lg'
							: ''} {node.recommended ? 'ring-4 ring-institutional-gold/40' : ''}"
						{style}
						title={node.reason ?? undefined}
						aria-label="{node.label}: {node.state === 'EXCLUDED'
							? `${node.label} – nicht gewählt`
							: (node.reason ?? STATE_LABELS[node.state])}"
					>
						{#if isBoss}
							<div
								class="mb-1 flex w-full items-center justify-between gap-1 border-b border-purple-200/60 pb-1"
							>
								<span
									class="inline-flex items-center gap-1 rounded bg-amber-200/90 px-1.5 py-0.5 font-mono text-[9px] font-black text-amber-900 uppercase"
								>
									👑 Boss
								</span>
								{#if hasGate && node.gateProgress}
									<button
										type="button"
										onclick={(e) => {
											e.preventDefault();
											e.stopPropagation();
											activeGateNode = node;
										}}
										class="cursor-pointer rounded bg-purple-100 px-1.5 py-0.5 font-mono text-[9px] font-bold text-purple-900 hover:bg-purple-200"
										title="Punkte-Details anzeigen"
									>
										{node.gateProgress.percent}% / {node.requirement?.minPercent}% ℹ
									</button>
								{/if}
							</div>
						{/if}
						<div class="flex items-start gap-2">
							<span class="text-xl leading-none"
								>{node.state === 'LOCKED' || node.state === 'BLOCKED' || node.state === 'EXCLUDED'
									? '🔒'
									: node.icon}</span
							>
							<span class="line-clamp-2 text-sm leading-tight font-bold text-institutional-navy"
								>{node.state === 'EXCLUDED' ? `${node.label} – nicht gewählt` : node.label}</span
							>
						</div>
						<div class="flex items-center justify-between gap-2">
							<span
								class="line-clamp-1 font-mono text-[9px] font-bold tracking-widest uppercase {node.recommended
									? 'text-institutional-gold'
									: node.state === 'DONE'
										? 'text-emerald-700'
										: node.state === 'BLOCKED'
											? 'text-amber-700'
											: 'text-slate-400'}"
							>
								{node.recommended
									? '★ Empfohlen'
									: node.state === 'BLOCKED' && node.reason
										? node.reason
										: STATE_LABELS[node.state]}
							</span>
							{#if hasGate && !isBoss && node.gateProgress}
								<button
									type="button"
									onclick={(e) => {
										e.preventDefault();
										e.stopPropagation();
										activeGateNode = node;
									}}
									class="shrink-0 cursor-pointer rounded bg-amber-100 px-1.5 py-0.5 font-mono text-[9px] font-bold text-amber-900 hover:bg-amber-200"
									title="Punkte-Details anzeigen"
								>
									{node.gateProgress.percent}% / {node.requirement?.minPercent}%
								</button>
							{:else if node.kind === 'MODULE' && node.total > 0}
								<span class="shrink-0 font-mono text-[9px] text-slate-400"
									>{node.done}/{node.total}</span
								>
							{/if}
						</div>
						{#if node.kind === 'MODULE' && node.total > 0}
							<div class="absolute inset-x-0 bottom-0 h-1 bg-slate-100">
								<div
									class="h-full bg-emerald-500"
									style="width: {(node.done / node.total) * 100}%"
								></div>
							</div>
						{/if}
					</svelte:element>
				{/if}
			{/each}
		</div>
	</div>
{/if}

{#if activeGateNode && activeGateNode.gateProgress}
	<div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
		<div class="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-lg bg-white p-5 shadow-2xl">
			<div class="flex items-start justify-between gap-3 border-b border-slate-200 pb-3">
				<div>
					<div class="flex items-center gap-2">
						<span class="text-2xl">{activeGateNode.boss ? '👑' : '🚪'}</span>
						<h3 class="text-lg font-bold text-institutional-navy">{activeGateNode.label}</h3>
					</div>
					<p class="mt-0.5 font-mono text-xs text-slate-500">
						{activeGateNode.boss ? 'Boss-Aufgabe' : 'Meilenstein-Gate'} – Freischaltung ab Punkte-Schwelle
					</p>
				</div>
				<button
					type="button"
					class="cursor-pointer rounded p-1 text-lg leading-none text-slate-400 hover:bg-slate-100 hover:text-slate-600"
					onclick={() => (activeGateNode = null)}
					aria-label="Schließen"
				>
					✕
				</button>
			</div>

			<div class="mt-4 space-y-4">
				<div class="rounded-lg border border-slate-200 bg-slate-50 p-3">
					<div class="flex items-center justify-between font-mono text-xs font-bold">
						<span class="text-slate-700">Fortschritt</span>
						<span
							class={activeGateNode.gateProgress.satisfied ? 'text-emerald-700' : 'text-amber-800'}
						>
							{activeGateNode.gateProgress.percent} % / Mind. {activeGateNode.requirement
								?.minPercent} %
						</span>
					</div>
					<div class="mt-2 h-2.5 w-full overflow-hidden rounded-full bg-slate-200">
						<div
							class="h-full {activeGateNode.gateProgress.satisfied
								? 'bg-emerald-500'
								: 'bg-amber-500'} transition-all"
							style="width: {Math.min(100, activeGateNode.gateProgress.percent)}%"
						></div>
					</div>
					<div class="mt-2 flex items-center justify-between text-xs text-slate-600">
						<span
							>Punkte im Bereich: <strong
								>{activeGateNode.gateProgress.earnedPoints} von {activeGateNode.gateProgress
									.maxPoints} P.</strong
							></span
						>
						{#if activeGateNode.gateProgress.missingPoints > 0}
							<span class="font-bold text-amber-700"
								>Noch {activeGateNode.gateProgress.missingPoints} P. fehlen</span
							>
						{/if}
					</div>
				</div>

				{#if activeGateNode.gateProgress.satisfied}
					<div
						class="rounded-md border border-emerald-300 bg-emerald-50 p-3 text-xs text-emerald-800"
					>
						<strong>✔ Freigeschaltet:</strong> Die Punkte-Schwelle wurde erreicht. Einmal freigeschaltet
						bleibt diese Station offen.
					</div>
				{:else if activeGateNode.gateProgress.unreachable}
					<div class="rounded-md border border-rose-300 bg-rose-50 p-3 text-xs text-rose-800">
						<strong>⚠ Schwelle unerreichbar:</strong>
						<p class="mt-1">
							Selbst wenn alle verbleibenden Aufgaben gelöst werden, können höchstens {activeGateNode
								.gateProgress.earnedPoints +
								(activeGateNode.gateProgress.openTasks?.reduce(
									(acc, t) => acc + t.potentialPoints,
									0
								) ?? 0)} Punkte erreicht werden ({activeGateNode.gateProgress.percent} % erreicht, gefordert:
							{activeGateNode.requirement?.minPercent} %).
						</p>
						{#if activeGateNode.requirement?.fallback === 'TEACHER'}
							<p class="mt-2 font-bold">
								Wende dich an deine Lehrkraft, um diesen Pfad freizuschalten.
							</p>
						{:else if activeGateNode.requirement?.fallback === 'OPEN'}
							<p class="mt-2 font-bold">
								Rückfall-Regel: Das Tor öffnet sich automatisch als Notfall-Öffnung.
							</p>
						{:else if activeGateNode.requirement?.fallback === 'REMEDIAL'}
							<p class="mt-2 font-bold">
								Rückfall-Regel: Schließe die Stützaufgabe ab, um dieses Tor zu öffnen.
							</p>
						{/if}
					</div>
				{:else}
					<div class="rounded-md border border-amber-300 bg-amber-50 p-3 text-xs text-amber-800">
						Noch {activeGateNode.gateProgress.missingPoints} Punkte fehlen bis zur Freischaltung.
					</div>
				{/if}

				<div>
					<h4 class="mb-2 text-xs font-black text-institutional-navy uppercase">
						Hier kannst du noch Punkte holen:
					</h4>
					{#if activeGateNode.gateProgress.openTasks && activeGateNode.gateProgress.openTasks.length > 0}
						<ul class="max-h-56 space-y-2 overflow-y-auto pr-1">
							{#each activeGateNode.gateProgress.openTasks as t}
								<li
									class="flex items-center justify-between gap-3 rounded border border-slate-200 bg-slate-50 p-2.5 transition-colors hover:bg-white"
								>
									<div class="min-w-0">
										<p class="truncate text-xs font-bold text-slate-800">{t.title}</p>
										<p class="font-mono text-[10px] text-slate-500">Modul: {t.moduleSlug}</p>
									</div>
									<div class="flex shrink-0 items-center gap-2">
										<span
											class="rounded bg-amber-100 px-2 py-0.5 font-mono text-[10px] font-bold text-amber-800"
										>
											+{t.potentialPoints} P.
										</span>
										<a
											href="/modules/{t.moduleSlug}/{t.taskSlug}"
											class="rounded bg-institutional-navy px-2 py-1 text-[10px] font-bold text-white hover:bg-slate-800"
										>
											Öffnen →
										</a>
									</div>
								</li>
							{/each}
						</ul>
					{:else}
						<p
							class="rounded border border-dashed border-slate-200 p-2 text-xs text-slate-500 italic"
						>
							Keine weiteren offenen Aufgaben im Punkte-Bereich verfügbar.
						</p>
					{/if}
				</div>

				<div class="flex justify-end border-t border-slate-200 pt-2">
					<button
						type="button"
						class="cursor-pointer rounded bg-slate-200 px-4 py-1.5 text-xs font-bold tracking-wider text-slate-800 uppercase hover:bg-slate-300"
						onclick={() => (activeGateNode = null)}
					>
						Schließen
					</button>
				</div>
			</div>
		</div>
	</div>
{/if}
