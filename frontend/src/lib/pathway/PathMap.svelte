<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type PathView } from '$lib/api';
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

	function conditionBadge(cond?: { rule?: string; failedAttemptsThreshold?: number; scoreThreshold?: number }): string | null {
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

	function centerOnRecommendedOrFit() {
		if (!viewportEl) return;
		const vpWidth = viewportEl.clientWidth || 800;
		const vpHeight = viewportEl.clientHeight || 500;
		const targetNode =
			currentPath.nodes.find((n) => n.recommended) ??
			currentPath.nodes.find((n) => n.state === 'AVAILABLE') ??
			currentPath.nodes[0];

		if (targetNode) {
			zoom = 1;
			panX = Math.round(vpWidth / 2 - (targetNode.x + NODE_W / 2));
			panY = Math.round(vpHeight / 2 - (targetNode.y + NODE_H / 2));
		} else {
			fitToScreen();
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
		zoom = Math.min(Math.max(+fitScale.toFixed(2), 0.35), 1.2);
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

	function handleWheel(e: WheelEvent) {
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
					Entscheide dich für einen Weg. Die anderen Wege sind danach gesperrt.
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
								<span class="shrink-0 rounded bg-amber-100 px-1.5 py-0.5 font-mono text-[10px] font-bold text-amber-800">
									+{opt.bonusPoints} P.
								</span>
							{/if}
						</div>
						{#if opt.hint}
							<span class="mt-1 block text-xs text-slate-600">{opt.hint}</span>
						{/if}
					</div>
					<button
						type="button"
						class="bg-institutional-navy px-4 py-2 text-[10px] font-black tracking-widest text-white uppercase transition-colors hover:bg-slate-800 disabled:opacity-50"
						disabled={choosing}
						onclick={() => chooseBranch(choiceNode.id, opt.to, opt.label)}
					>
						Diesen Weg nehmen
					</button>
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
			class="px-3 py-1 rounded transition-colors {viewMode === 'map'
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
			class="px-3 py-1 rounded transition-colors {viewMode === 'list'
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
				class="rounded px-2.5 py-1 font-bold text-slate-700 hover:bg-slate-100 transition-colors"
				onclick={zoomOut}
				title="Verkleinern (−)"
				aria-label="Verkleinern"
			>
				−
			</button>
			<span class="min-w-12 text-center font-mono text-[11px] text-slate-500 font-semibold">
				{Math.round(zoom * 100)}%
			</span>
			<button
				type="button"
				class="rounded px-2.5 py-1 font-bold text-slate-700 hover:bg-slate-100 transition-colors"
				onclick={zoomIn}
				title="Vergrößern (+)"
				aria-label="Vergrößern"
			>
				+
			</button>
			<div class="h-4 w-px bg-slate-200 mx-1"></div>
			<button
				type="button"
				class="rounded px-2.5 py-1 font-medium text-slate-700 hover:bg-slate-100 transition-colors"
				onclick={fitToScreen}
				title="Auf Bildschirm einpassen"
			>
				⛶ Einpassen
			</button>
			<button
				type="button"
				class="rounded px-2.5 py-1 font-medium text-slate-700 hover:bg-slate-100 transition-colors"
				onclick={centerOnRecommendedOrFit}
				title="Aktuelle Station zentrieren"
			>
				🎯 Fokus
			</button>
		</div>
	{/if}
</div>

{#if currentPath.nodes.some((n) => n.state === 'SKIPPED')}
	<div class="mb-3 flex items-center gap-2 rounded border border-sky-300 bg-sky-50 px-4 py-2 text-xs text-sky-900 shadow-sm" data-testid="fast-track-banner">
		<span class="text-base">⚡</span>
		<span><strong>Fast-Track freigeschaltet!</strong> Du hast eine Abkürzung genommen. Übersprungene Stationen können freiwillig bearbeitet werden.</span>
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
								{#if node.recommended}
									<span class="rounded bg-amber-100 px-1.5 py-0.5 font-mono text-[9px] font-black text-amber-800 uppercase">
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
						</div>
					</div>
					<div class="flex flex-col items-end gap-1 shrink-0">
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
										<span class="rounded bg-amber-100 px-1 font-mono text-[9px] font-bold text-amber-800">
											+{opt.bonusPoints} P.
										</span>
									{/if}
								</div>
								{#if opt.hint}
									<span class="text-[11px] text-slate-500">{opt.hint}</span>
								{/if}
								<button
									type="button"
									class="mt-1 bg-institutional-navy px-3 py-1.5 text-[10px] font-black tracking-wider text-white uppercase hover:bg-slate-800 disabled:opacity-50"
									disabled={choosing}
									onclick={() => chooseBranch(node.id, opt.to, opt.label)}
								>
									Wählen
								</button>
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
		class="relative overflow-hidden border border-slate-200 bg-slate-50/60 select-none touch-none cursor-grab active:cursor-grabbing min-h-[480px] h-[calc(100vh-320px)] max-h-[820px] w-full"
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
						{#if edge.condition}
							{@const cBadge = conditionBadge(edge.condition)}
							{#if cBadge}
								{@const midX = (from.x + to.x) / 2 + NODE_W / 2}
								{@const midY = (from.y + to.y) / 2 + NODE_H / 2 - 12}
								<g transform="translate({midX}, {midY})">
									<rect x="-46" y="-9" width="92" height="18" rx="4" class="fill-white stroke-slate-300 shadow-sm" stroke-width="1" />
									<text x="0" y="3" text-anchor="middle" font-size="8.5" font-family="sans-serif" font-weight="bold" class="fill-slate-700 pointer-events-none">{cBadge}</text>
								</g>
							{/if}
						{/if}
					{/if}
				{/each}
			</svg>

			{#each currentPath.nodes.filter((n) => !(n.kind === 'REMEDIAL' && n.state === 'EXCLUDED')) as node (node.id)}
				{@const style = `left: ${node.x}px; top: ${node.y}px; width: ${NODE_W}px; height: ${NODE_H}px`}
				{#if node.kind === 'MILESTONE'}
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
						<span class="text-lg"
							>{node.state === 'DONE' ? '🏆' : node.state === 'BLOCKED' ? '🔒' : '🏁'}</span
						>
						<span class="text-xs font-black tracking-wide text-institutional-navy uppercase"
							>{node.label}</span
						>
					</div>
				{:else if node.kind === 'CHOICE'}
					<div
						class="absolute flex flex-col justify-between border-2 p-3 {node.state === 'DONE'
							? 'border-emerald-400 bg-emerald-50'
							: node.state === 'AVAILABLE'
								? 'border-institutional-navy bg-white ring-4 ring-institutional-gold/40 shadow-md'
								: 'border-amber-300 bg-amber-50/50'}"
						{style}
						title={node.reason ?? undefined}
						aria-label="Weiche {node.label}: {node.reason ?? STATE_LABELS[node.state]}"
					>
						<div class="flex items-center gap-2">
							<span class="text-xl leading-none">{node.state === 'BLOCKED' ? '🔒' : '🔀'}</span>
							<span class="line-clamp-2 text-xs font-black tracking-wide text-institutional-navy uppercase">
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
							<span class="shrink-0 rounded bg-amber-100 px-1.5 py-0.5 font-mono text-[10px] font-bold text-amber-800">
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
								{node.reason ?? (node.state === 'DONE' ? `+${node.points ?? 10} erhalten` : STATE_LABELS[node.state])}
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
								<span class="block truncate text-xs font-black tracking-wide text-institutional-navy uppercase">
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
						type={canOpen ? 'button' : undefined}
						onclick={canOpen ? () => openSubpath(node.path!) : undefined}
						class="absolute flex flex-col justify-between border-2 p-3 text-left transition-all {STATE_STYLES[
							node.state
						]} {canOpen ? 'cursor-pointer hover:-translate-y-0.5 hover:shadow-lg' : ''} {node.recommended
							? 'ring-4 ring-institutional-gold/40'
							: ''}"
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
					<svelte:element
						this={node.href ? 'a' : 'div'}
						href={node.href ?? undefined}
						class="absolute flex flex-col justify-between border-2 p-3 transition-all {STATE_STYLES[
							node.state
						]} {node.href ? 'hover:-translate-y-0.5 hover:shadow-lg' : ''} {node.recommended
							? 'ring-4 ring-institutional-gold/40'
							: ''}"
						{style}
						title={node.reason ?? undefined}
						aria-label="{node.label}: {node.state === 'EXCLUDED'
							? `${node.label} – nicht gewählt`
							: (node.reason ?? STATE_LABELS[node.state])}"
					>
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
							{#if node.kind === 'MODULE' && node.total > 0}
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
