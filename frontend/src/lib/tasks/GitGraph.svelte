<script lang="ts">
	import type { GitSimulator } from './git';
	import { graphLayout } from './git-graph';

	// version ändert sich nach jedem Befehl – das Git-Modell selbst ist nicht reaktiv
	let { git, version }: { git: GitSimulator; version: number } = $props();

	const ROW = 30;
	const LANE = 18;
	const PAD = 14;
	const COLORS = ['#0d9488', '#7c3aed', '#d97706', '#e11d48', '#2563eb', '#65a30d'];

	const graph = $derived.by(() => {
		void version;
		return graphLayout(git);
	});
	const width = $derived(PAD * 2 + (graph.lanes - 1) * LANE);
	const x = (lane: number) => PAD + lane * LANE;
	const y = (row: number) => ROW / 2 + row * ROW;

	function path(e: (typeof graph.edges)[number]) {
		const [x1, y1, x2, y2] = [x(e.fromLane), y(e.from), x(e.toLane), y(e.to)];
		if (x1 === x2) return `M${x1},${y1} L${x2},${y2}`;
		// erster Elternteil: in der eigenen Spur nach unten, kurz vor dem Ziel einbiegen
		if (e.first)
			return `M${x1},${y1} L${x1},${y2 - ROW} C${x1},${y2 - ROW / 3} ${x2},${y2 - ROW / 2} ${x2},${y2}`;
		// zweiter Elternteil (Merge): sofort abzweigen, dann gerade weiter
		return `M${x1},${y1} C${x1},${y1 + ROW / 2} ${x2},${y1 + ROW / 3} ${x2},${y1 + ROW} L${x2},${y2}`;
	}

	const badge = {
		head: 'bg-teal-600 text-white',
		branch: 'bg-slate-200 text-slate-700',
		remote: 'bg-violet-100 text-violet-700',
		tag: 'bg-amber-100 text-amber-800'
	};
</script>

<div class="border border-slate-200 bg-white" aria-label="Commit-Verlauf">
	<div
		class="flex items-center justify-between border-b border-slate-200 px-4 py-2 text-[10px] font-black tracking-[3px] text-slate-500 uppercase"
	>
		<span>Commit-Verlauf</span>
		<span class="font-mono tracking-normal normal-case"
			>Branch: <b class="text-teal-700">{git.head}</b>{git.merging ? ' · Konflikt offen' : ''}</span
		>
	</div>
	{#if graph.rows.length === 0}
		<p class="px-4 py-6 text-sm text-slate-500">
			Noch keine Commits. Mit <code class="font-mono">git add</code> und
			<code class="font-mono">git commit -m "…"</code> entsteht der erste.
		</p>
	{:else}
		<div class="relative max-h-72 overflow-auto">
			<svg
				class="absolute top-0 left-0"
				{width}
				height={graph.rows.length * ROW}
				aria-hidden="true"
			>
				{#each graph.edges as e, i (i)}
					<path
						d={path(e)}
						fill="none"
						stroke={COLORS[e.toLane % COLORS.length]}
						stroke-width="2"
					/>
				{/each}
				{#each graph.rows as r, i (r.id)}
					<circle
						cx={x(r.lane)}
						cy={y(i)}
						r="5"
						fill={r.refs.some((ref) => ref.kind === 'head')
							? COLORS[r.lane % COLORS.length]
							: 'white'}
						stroke={COLORS[r.lane % COLORS.length]}
						stroke-width="2"
					/>
				{/each}
			</svg>
			<ol style="padding-left: {width + 4}px">
				{#each graph.rows as r (r.id)}
					<li
						class="flex items-center gap-2 overflow-hidden pr-4 font-mono text-xs whitespace-nowrap"
						style="height: {ROW}px"
					>
						<span class="text-amber-700">{r.id}</span>
						{#each r.refs as ref (ref.label)}
							<span class="px-1.5 py-0.5 text-[10px] font-bold {badge[ref.kind]}"
								>{ref.kind === 'head' ? 'HEAD → ' : ''}{ref.label}</span
							>
						{/each}
						<span class="truncate text-slate-800">{r.message}</span>
						<span class="text-slate-400">– {r.author}</span>
					</li>
				{/each}
			</ol>
		</div>
	{/if}
</div>
