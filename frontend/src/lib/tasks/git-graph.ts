import type { GitSimulator } from './git';

export interface GraphRow {
	id: string;
	message: string;
	author: string;
	lane: number;
	refs: { label: string; kind: 'head' | 'branch' | 'remote' | 'tag' }[];
}

export interface GraphEdge {
	from: number;
	to: number;
	fromLane: number;
	toLane: number;
	/** Erster Elternteil läuft in der eigenen Spur, ein zweiter (Merge) zweigt sofort ab. */
	first: boolean;
}

/**
 * Ordnet alle bekannten Commits (lokal und auf dem Server) in Zeilen und Spuren an, neueste oben –
 * wie "git log --graph". Jeder Branch bekommt eine eigene Spur, solange er sich nicht mit einem anderen vereint.
 */
export function graphLayout(git: GitSimulator): {
	rows: GraphRow[];
	edges: GraphEdge[];
	lanes: number;
} {
	const starts = [...git.branches.values(), ...(git.remote?.values() ?? []), ...git.tags.values()];
	const commits = git.sorted(git.reachable(starts));
	const rowOf = new Map(commits.map((c, i) => [c.id, i]));
	const laneOf = new Map<string, number>();
	const lanes: (string | null)[] = [];
	let width = 0;

	for (const c of commits) {
		let lane = lanes.indexOf(c.id);
		if (lane < 0) lane = freeLane(lanes);
		// Andere Spuren, die auf diesen Commit warten, enden hier
		for (let i = 0; i < lanes.length; i++) if (lanes[i] === c.id) lanes[i] = null;
		laneOf.set(c.id, lane);
		lanes[lane] = c.parents[0] ?? null;
		for (const p of c.parents.slice(1)) if (!lanes.includes(p)) lanes[freeLane(lanes)] = p;
		width = Math.max(width, lanes.length);
	}

	const edges: GraphEdge[] = [];
	commits.forEach((c, i) =>
		c.parents.forEach((p, n) => {
			const to = rowOf.get(p);
			if (to !== undefined)
				edges.push({
					from: i,
					to,
					fromLane: laneOf.get(c.id)!,
					toLane: laneOf.get(p)!,
					first: n === 0
				});
		})
	);

	const rows = commits.map((c) => ({
		id: c.id,
		message: c.message.split('\n')[0],
		author: c.author,
		lane: laneOf.get(c.id)!,
		refs: refs(git, c.id)
	}));
	return { rows, edges, lanes: Math.max(width, 1) };
}

function freeLane(lanes: (string | null)[]) {
	const free = lanes.indexOf(null);
	if (free >= 0) return free;
	lanes.push(null);
	return lanes.length - 1;
}

function refs(git: GitSimulator, id: string): GraphRow['refs'] {
	const result: GraphRow['refs'] = [];
	for (const part of git.decorations(id).split(', ')) {
		if (part === '') continue;
		if (part.startsWith('HEAD -> ')) result.push({ label: part.slice(8), kind: 'head' });
		else if (part.startsWith('origin/')) result.push({ label: part, kind: 'remote' });
		else if (part.startsWith('tag: ')) result.push({ label: part.slice(5), kind: 'tag' });
		else result.push({ label: part, kind: 'branch' });
	}
	return result;
}
