<script lang="ts">
	import type { PathView } from '$lib/api';
	import { canvasSize, edgePath, NODE_H, NODE_W } from './geometry';

	let { path }: { path: PathView } = $props();

	const byId = $derived(new Map(path.nodes.map((n) => [n.id, n])));
	const size = $derived(canvasSize(path.nodes, 320, 200));

	const STATE_LABELS: Record<string, string> = {
		DONE: 'Erledigt',
		AVAILABLE: 'Jetzt dran',
		UPCOMING: 'Später',
		CLOSED: 'Zeit abgelaufen',
		LOCKED: 'Gesperrt'
	};

	const STATE_STYLES: Record<string, string> = {
		DONE: 'border-emerald-300 bg-emerald-50',
		AVAILABLE: 'border-institutional-navy bg-white shadow-md',
		UPCOMING: 'border-slate-200 bg-white',
		CLOSED: 'border-slate-200 bg-slate-50 opacity-70',
		LOCKED: 'border-dashed border-slate-300 bg-slate-50 opacity-60'
	};
</script>

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
			{#each path.edges as edge (edge.from + '>' + edge.to)}
				{@const from = byId.get(edge.from)}
				{@const to = byId.get(edge.to)}
				{#if from && to}
					{@const done = from.state === 'DONE'}
					<path
						d={edgePath(from, to)}
						fill="none"
						stroke-width="2.5"
						class={done ? 'stroke-emerald-500' : 'stroke-slate-300'}
						stroke-dasharray={done ? undefined : '6 5'}
						marker-end="url(#{done ? 'arrow-done' : 'arrow-open'})"
					/>
				{/if}
			{/each}
		</svg>

		{#each path.nodes as node (node.id)}
			{@const style = `left: ${node.x}px; top: ${node.y}px; width: ${NODE_W}px; height: ${NODE_H}px`}
			{#if node.kind === 'MILESTONE'}
				<div
					class="absolute flex flex-col items-center justify-center gap-1 rounded-full border-2 px-4 text-center {node.state ===
					'DONE'
						? 'border-institutional-gold bg-amber-50'
						: 'border-dashed border-slate-300 bg-white'}"
					{style}
				>
					<span class="text-lg">{node.state === 'DONE' ? '🏆' : '🏁'}</span>
					<span class="text-xs font-black tracking-wide text-institutional-navy uppercase"
						>{node.label}</span
					>
				</div>
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
					aria-label="{node.label}: {STATE_LABELS[node.state]}"
				>
					<div class="flex items-start gap-2">
						<span class="text-xl leading-none">{node.state === 'LOCKED' ? '🔒' : node.icon}</span>
						<span class="line-clamp-2 text-sm leading-tight font-bold text-institutional-navy"
							>{node.label}</span
						>
					</div>
					<div class="flex items-center justify-between gap-2">
						<span
							class="font-mono text-[9px] font-bold tracking-widest uppercase {node.recommended
								? 'text-institutional-gold'
								: node.state === 'DONE'
									? 'text-emerald-700'
									: 'text-slate-400'}"
						>
							{node.recommended ? '★ Empfohlen' : STATE_LABELS[node.state]}
						</span>
						{#if node.kind === 'MODULE' && node.total > 0}
							<span class="font-mono text-[9px] text-slate-400">{node.done}/{node.total}</span>
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
