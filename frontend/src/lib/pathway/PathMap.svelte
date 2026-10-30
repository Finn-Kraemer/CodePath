<script lang="ts">
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
		EXCLUDED: 'nicht gewählt'
	};

	const STATE_STYLES: Record<string, string> = {
		DONE: 'border-emerald-300 bg-emerald-50',
		AVAILABLE: 'border-institutional-navy bg-white shadow-md',
		BLOCKED: 'border-amber-300 bg-amber-50/40 opacity-85',
		UPCOMING: 'border-slate-200 bg-white',
		CLOSED: 'border-slate-200 bg-slate-50 opacity-70',
		LOCKED: 'border-dashed border-slate-300 bg-slate-50 opacity-60',
		EXCLUDED: 'border-dashed border-slate-300 bg-slate-100/80 opacity-60 grayscale'
	};

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

<div class="overflow-x-auto border border-slate-200 bg-slate-50/60" data-testid="path-map">
	<div class="relative" style="width: {size.width}px; height: {size.height}px">
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
				{#if from && to}
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
				{/if}
			{/each}
		</svg>

		{#each currentPath.nodes as node (node.id)}
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
