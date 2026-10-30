<script lang="ts">
	import { onMount } from 'svelte';
	import { beforeNavigate, goto } from '$app/navigation';
	import { page } from '$app/state';
	import {
		api,
		type CourseSummary,
		type PathAdmin,
		type PathNode,
		type PathSummary,
		type PathView,
		type Schemas,
		type StudentOverview
	} from '$lib/api';
	import {
		canvasSize,
		choiceBranches,
		edgePath,
		incomingSlot,
		layered,
		NODE_H,
		NODE_W,
		snap
	} from '$lib/pathway/geometry';
	import PathMap from '$lib/pathway/PathMap.svelte';
	import { exportUrl } from '$lib/transfer';

	type ModuleRow = Schemas['ModuleRow'];
	type Edge = Schemas['Edge'];
	type Kind = PathNode['kind'];
	type ChoiceNodeStats = Schemas['ChoiceNodeStats'];

	const id = $derived(Number(page.params.id));

	let saved = $state<PathAdmin | null>(null);
	let choiceStats = $state<ChoiceNodeStats[]>([]);
	let resetCourseProgress = $state<Record<string, boolean>>({});
	let form = $state({
		title: '',
		slug: '',
		description: '',
		published: false,
		enforce: false,
		templateOnly: false,
		courseId: 0,
		lockOthers: false,
		unlockRestAfter: ''
	});
	let allPaths = $state<PathSummary[]>([]);
	let courses = $state<CourseSummary[]>([]);
	let students = $state<StudentOverview[]>([]);
	let previewStudent = $state('');
	/** Was das letzte Speichern in die Modul-Steuerung übernommen hat */
	let applied = $state<string[] | null>(null);
	let nodes = $state<PathNode[]>([]);
	let edges = $state<Edge[]>([]);
	let catalog = $state<ModuleRow[]>([]);
	let error = $state('');
	let message = $state('');
	let busy = $state(false);

	let selectedId = $state<string | null>(null);
	let selectedEdge = $state<string | null>(null);
	let connecting = $state(false);
	let connectFrom = $state<string | null>(null);
	let preview = $state<PathView | null>(null);

	let draft = $state<{
		kind: Kind;
		module: string;
		task: string;
		path: string;
		label: string;
		min: number;
		points: number;
	}>({
		kind: 'MODULE',
		module: '',
		task: '',
		path: '',
		label: '',
		min: 1,
		points: 10
	});

	const selected = $derived(nodes.find((n) => n.id === selectedId) ?? null);
	const size = $derived(canvasSize(nodes, 1100, 560));
	const byId = $derived(new Map(nodes.map((n) => [n.id, n])));
	const modulesBySlug = $derived(new Map(catalog.map((m) => [m.module.slug, m])));
	const pathsBySlug = $derived(new Map(allPaths.map((p) => [p.slug, p])));
	const availableSubpaths = $derived(allPaths.filter((p) => p.id !== id));
	const current = $derived(JSON.stringify({ ...form, graph: { nodes, edges } }));
	const savedState = $derived(
		saved
			? JSON.stringify({
					title: saved.title,
					slug: saved.slug,
					description: saved.description,
					published: saved.published,
					enforce: saved.enforce ?? false,
					templateOnly: saved.templateOnly ?? false,
					courseId: saved.courseId ?? 0,
					lockOthers: saved.lockOthers,
					unlockRestAfter: saved.unlockRestAfter ?? '',
					graph: saved.graph
				})
			: ''
	);
	const dirty = $derived(!!saved && current !== savedState);
	const filteredStudents = $derived(
		form.courseId ? students.filter((s) => s.courseId === form.courseId) : students
	);
	const outsideMode = $derived(
		form.unlockRestAfter ? 'AFTER' : form.lockOthers ? 'LOCKED' : 'FREE'
	);

	onMount(async () => {
		await loadEditor(id);
	});

	$effect(() => {
		const targetId = id;
		if (saved && saved.id !== targetId) {
			loadEditor(targetId);
		}
	});

	async function loadEditor(targetId: number) {
		try {
			const [path, pathList, modules, courseList, studentList, stats] = await Promise.all([
				api<PathAdmin>(`/api/admin/learning-paths/${targetId}`),
				api<PathSummary[]>('/api/admin/learning-paths'),
				api<ModuleRow[]>('/api/admin/content'),
				api<CourseSummary[]>('/api/admin/courses'),
				api<StudentOverview[]>('/api/admin/students'),
				api<ChoiceNodeStats[]>(`/api/admin/learning-paths/${targetId}/choice-stats`).catch(
					() => []
				)
			]);
			allPaths = pathList;
			catalog = modules;
			courses = courseList;
			students = studentList;
			choiceStats = stats;
			selectedId = null;
			selectedEdge = null;
			preview = null;
			apply(path);
			draft.module = catalog[0]?.module.slug ?? '';
			draft.path = pathList.find((p) => p.id !== targetId)?.slug ?? '';
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function refreshChoiceStats() {
		try {
			const courseQuery = form.courseId ? `?courseId=${form.courseId}` : '';
			choiceStats = await api<ChoiceNodeStats[]>(
				`/api/admin/learning-paths/${id}/choice-stats${courseQuery}`
			);
		} catch {
			// ignorieren
		}
	}

	async function runCourseAction(body: {
		action: 'RESET_CHOICE' | 'SET_CHOICE' | 'UNLOCK_BRANCH' | 'UNLOCK_NODE';
		pathSlug?: string;
		nodeId: string;
		to?: string;
		resetProgress?: boolean;
	}) {
		error = '';
		busy = true;
		try {
			const res = await api<{ affected: number }>(`/api/admin/learning-paths/${id}/course-actions`, {
				method: 'POST',
				body: {
					...body,
					courseId: form.courseId || undefined
				}
			});
			await refreshChoiceStats();
			if (preview) await showPreview();
			flash(`Kurs-Aktion für ${res.affected} Schüler/innen ausgeführt.`);
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	beforeNavigate((navigation) => {
		if (dirty && !confirm('Ungespeicherte Änderungen verwerfen?')) navigation.cancel();
	});

	function apply(path: PathAdmin) {
		// Gleiche Schlüsselreihenfolge wie beim Vergleich, damit "ungespeichert" stimmt
		const graph = {
			nodes: path.graph.nodes.map(normalize),
			edges: path.graph.edges.map(normalizeEdge)
		};
		saved = { ...path, graph };
		form = {
			title: path.title,
			slug: path.slug,
			description: path.description ?? '',
			published: path.published,
			enforce: path.enforce ?? false,
			templateOnly: path.templateOnly ?? false,
			courseId: path.courseId ?? 0,
			lockOthers: path.lockOthers,
			unlockRestAfter: path.unlockRestAfter ?? ''
		};
		nodes = structuredClone(graph.nodes);
		edges = structuredClone(graph.edges);
	}

	function normalizeEdge(e: Partial<Edge> & { from: string; to: string }): Edge {
		const edge: Record<string, unknown> = {
			from: e.from,
			to: e.to,
			required: !!e.required
		};
		if (e.label?.trim()) edge.label = e.label.trim();
		if (e.hint?.trim()) edge.hint = e.hint.trim();
		return edge as unknown as Edge;
	}

	function normalize(
		n: Partial<PathNode> & { id: string; kind: Kind; x: number; y: number }
	): PathNode {
		const node: Record<string, unknown> = { id: n.id, kind: n.kind };
		if (n.module) node.module = n.module;
		if (n.task) node.task = n.task;
		if (n.kind === 'MODULE' && n.tasks?.length) node.tasks = n.tasks;
		if (n.kind === 'PATH' && n.path) node.path = n.path;
		if (n.label) node.label = n.label;
		if (n.kind === 'OR') node.min = Math.max(1, n.min ?? 1);
		if (n.kind === 'BONUS') node.points = Math.max(1, Math.min(1000, n.points ?? 10));
		node.x = n.x;
		node.y = n.y;
		return node as unknown as PathNode;
	}

	function flash(text: string) {
		message = text;
		setTimeout(() => (message = ''), 3000);
	}

	async function save() {
		error = '';
		busy = true;
		try {
			const updated = await api<PathAdmin>(`/api/admin/learning-paths/${id}`, {
				method: 'PUT',
				body: {
					...form,
					graph: { nodes: nodes.map(normalize), edges: edges.map(normalizeEdge) }
				}
			});
			apply(updated);
			applied = form.courseId && form.published ? updated.applied : null;
			flash('Gespeichert.');
			await refreshChoiceStats();
			if (preview) await showPreview();
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	async function remove() {
		if (!saved || !confirm(`Lernpfad „${saved.title}“ löschen?`)) return;
		try {
			await api(`/api/admin/learning-paths/${id}`, { method: 'DELETE' });
			saved = null;
			goto('/admin/learning-paths');
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function showPreview() {
		if (!saved) return;
		try {
			const query = previewStudent ? `?student=${encodeURIComponent(previewStudent)}` : '';
			preview = await api<PathView>(`/api/learning-paths/${saved.slug}${query}`);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	function toggleEnforce(checked: boolean) {
		form.enforce = checked;
		if (checked && edges.length > 0 && edges.every((e) => !e.required)) {
			edges = edges.map((e) => ({ ...e, required: true }));
		}
	}

	function setOutsideMode(mode: string) {
		if (mode === 'FREE') {
			form.lockOthers = false;
			form.unlockRestAfter = '';
		} else if (mode === 'LOCKED') {
			form.lockOthers = true;
			form.unlockRestAfter = '';
		} else if (mode === 'AFTER') {
			form.lockOthers = true;
			form.unlockRestAfter = nodes[nodes.length - 1]?.id ?? '';
		}
	}

	// ------------------------------------------------------------ Stationen

	function label(n: PathNode): string {
		if (n.label) return n.label;
		if (n.kind === 'CHOICE') return 'Weiche';
		if (n.kind === 'BONUS') return 'Bonus';
		if (n.kind === 'AND') return 'UND-Block';
		if (n.kind === 'OR') {
			const min = n.min ?? 1;
			return min > 1 ? `ODER-Block (mind. ${min})` : 'ODER-Block';
		}
		if (n.kind === 'PATH') {
			return pathsBySlug.get(n.path ?? '')?.title ?? (n.path ? `⚠ ${n.path} fehlt` : 'Baustein');
		}
		const module = modulesBySlug.get(n.module);
		if (n.kind === 'MODULE') {
			if (!module) return `⚠ ${n.module} fehlt`;
			return n.tasks?.length
				? `${module.module.title} (${n.tasks.length} Aufgaben)`
				: module.module.title;
		}
		const task = module?.tasks.find((t) => t.slug === n.task);
		return task?.title ?? `⚠ ${n.module}/${n.task} fehlt`;
	}

	function icon(n: PathNode): string {
		if (n.kind === 'MILESTONE') return '🏁';
		if (n.kind === 'CHOICE') return '🔀';
		if (n.kind === 'PATH') return '🧩';
		if (n.kind === 'BONUS') return '⭐';
		if (n.kind === 'AND') return '&';
		if (n.kind === 'OR') return `≥${n.min ?? 1}`;
		return modulesBySlug.get(n.module)?.module.icon ?? '';
	}

	const KIND_LABELS: Record<Kind, string> = {
		MODULE: 'Modul',
		TASK: 'Aufgabe',
		MILESTONE: 'Meilenstein',
		AND: 'Logik-Block: UND',
		OR: 'Logik-Block: ODER',
		CHOICE: 'Entweder-oder-Weiche',
		PATH: 'Lernpfad-Baustein',
		BONUS: 'Bonus-Station'
	};

	const BRANCH_PALETTE = [
		'border-sky-400 bg-sky-50/70',
		'border-violet-400 bg-violet-50/70',
		'border-teal-400 bg-teal-50/70',
		'border-amber-400 bg-amber-50/70'
	];

	/** Exklusive Zweig-Einfärbung für Weichen (Stationen hinter dem Zusammenführungspunkt bleiben neutral). */
	const branchStyles = $derived.by(() => {
		const map = new Map<string, string>();
		let colorIdx = 0;
		for (const n of nodes) {
			if (n.kind !== 'CHOICE') continue;
			const branches = choiceBranches(edges, n.id);
			for (const [, exclusiveIds] of branches) {
				const style = BRANCH_PALETTE[colorIdx % BRANCH_PALETTE.length];
				colorIdx++;
				for (const id of exclusiveIds) {
					map.set(id, style);
				}
			}
		}
		return map;
	});

	function freeId(): string {
		let i = nodes.length + 1;
		while (byId.has(`n${i}`)) i++;
		return `n${i}`;
	}

	const draftTasks = $derived(
		(modulesBySlug.get(draft.module)?.tasks ?? []).filter((t) => !t.archived && t.currentVersion)
	);

	function addNode(e: Event) {
		e.preventDefault();
		error = '';
		if (draft.kind === 'MILESTONE' && !draft.label.trim()) {
			error = 'Ein Meilenstein braucht eine Beschriftung.';
			return;
		}
		if (draft.kind === 'BONUS' && !draft.label.trim()) {
			error = 'Eine Bonus-Station braucht eine Beschriftung.';
			return;
		}
		if (draft.kind === 'TASK' && !draft.task) {
			error = 'Bitte eine Aufgabe wählen.';
			return;
		}
		if (draft.kind === 'PATH' && !draft.path) {
			error = 'Bitte einen Lernpfad-Baustein wählen.';
			return;
		}
		const isStation = draft.kind === 'MODULE' || draft.kind === 'TASK';
		const anchor = selected;
		const node: PathNode = normalize({
			id: freeId(),
			kind: draft.kind,
			module: isStation ? draft.module : '',
			task: draft.kind === 'TASK' ? draft.task : '',
			tasks: [],
			path: draft.kind === 'PATH' ? draft.path : undefined,
			label: draft.label.trim(),
			min: draft.kind === 'OR' ? Math.max(1, draft.min || 1) : undefined,
			points: draft.kind === 'BONUS' ? Math.max(1, Math.min(1000, draft.points || 10)) : undefined,
			x: anchor ? anchor.x : 60,
			y: anchor
				? anchor.y + NODE_H + 70
				: snap(Math.max(-NODE_H, ...nodes.map((n) => n.y)) + NODE_H + 70)
		});
		nodes = [...nodes, node];
		// An die gewählte Station anhängen – so entsteht ein Pfad Schritt für Schritt
		if (anchor) {
			edges = [
				...edges,
				normalizeEdge({
					from: anchor.id,
					to: node.id,
					required: form.enforce || anchor.kind === 'CHOICE',
					label: anchor.kind === 'CHOICE' ? label(node) : undefined
				})
			];
		}
		selectedId = node.id;
		draft.label = '';
		draft.min = 1;
		draft.points = 10;
	}

	/** Veröffentlichte, nicht archivierte Aufgaben eines Moduls in ihrer Reihenfolge */
	function moduleTasks(moduleSlug: string) {
		return (modulesBySlug.get(moduleSlug)?.tasks ?? []).filter(
			(t) => !t.archived && t.currentVersion
		);
	}

	/** Aufgaben des Moduls, die schon eine andere Station dieses Lernpfads nutzt */
	function usedElsewhere(node: PathNode): string[] {
		return nodes
			.filter((n) => n.id !== node.id && n.module === node.module)
			.flatMap((n) =>
				n.kind === 'TASK'
					? [n.task]
					: n.tasks?.length
						? n.tasks
						: moduleTasks(n.module).map((t) => t.slug)
			);
	}

	function updateNode(nodeId: string, change: Partial<PathNode>) {
		nodes = nodes.map((n) => (n.id === nodeId ? normalize({ ...n, ...change }) : n));
	}

	/** Alle Aufgaben oder nur eine Auswahl; beim Umschalten auf Auswahl erst die noch nicht genutzten */
	function setPartial(node: PathNode, partial: boolean) {
		if (!partial) return updateNode(node.id, { tasks: [] });
		const taken = usedElsewhere(node);
		const free = moduleTasks(node.module)
			.map((t) => t.slug)
			.filter((slug) => !taken.includes(slug));
		updateNode(node.id, {
			tasks: free.length ? free : moduleTasks(node.module).map((t) => t.slug)
		});
	}

	function toggleTask(node: PathNode, slug: string, on: boolean) {
		const order = moduleTasks(node.module).map((t) => t.slug);
		const current = (node.tasks ?? []).filter((t) => t !== slug).concat(on ? [slug] : []);
		const tasks = order.filter((t) => current.includes(t));
		if (!tasks.length) {
			error = 'Mindestens eine Aufgabe auswählen – oder „Alle Aufgaben“.';
			return;
		}
		updateNode(node.id, { tasks });
	}

	/** Die noch nicht genutzten Aufgaben des Moduls als neue Station darunter anhängen (z. B. „Teil 2“) */
	function splitRest(node: PathNode) {
		const taken = [...usedElsewhere(node), ...(node.tasks ?? [])];
		const rest = moduleTasks(node.module)
			.map((t) => t.slug)
			.filter((slug) => !taken.includes(slug));
		if (!rest.length) {
			error = 'Alle Aufgaben dieses Moduls sind schon verteilt.';
			return;
		}
		const title = modulesBySlug.get(node.module)?.module.title ?? node.module;
		const part = nodes.filter((n) => n.module === node.module && n.kind === 'MODULE').length + 1;
		const added = normalize({
			id: freeId(),
			kind: 'MODULE',
			module: node.module,
			task: '',
			tasks: rest,
			label: `${title} ${part}`,
			x: node.x,
			y: node.y + NODE_H + 70
		});
		if (!node.label && node.tasks?.length) updateNode(node.id, { label: `${title} 1` });
		nodes = [...nodes, added];
		selectedId = added.id;
	}

	function removeNode(nodeId: string) {
		nodes = nodes.filter((n) => n.id !== nodeId);
		edges = edges.filter((e) => e.from !== nodeId && e.to !== nodeId);
		if (selectedId === nodeId) selectedId = null;
		if (form.unlockRestAfter === nodeId) form.unlockRestAfter = '';
	}

	function setLabel(value: string) {
		if (!selected) return;
		const nodeId = selected.id;
		nodes = nodes.map((n) => (n.id === nodeId ? normalize({ ...n, label: value }) : n));
	}

	// ------------------------------------------------------------ Verbindungen

	const edgeKey = (e: Edge) => `${e.from}>${e.to}`;

	/** Würde die Verbindung einen Kreis schließen? (Gibt es schon einen Weg von to nach from?) */
	function wouldCycle(from: string, to: string): boolean {
		const stack = [to];
		const seen: string[] = [];
		while (stack.length) {
			const current = stack.pop()!;
			if (current === from) return true;
			if (seen.includes(current)) continue;
			seen.push(current);
			for (const e of edges) if (e.from === current) stack.push(e.to);
		}
		return false;
	}

	function connect(from: string, to: string) {
		error = '';
		if (from === to) return;
		if (edges.some((e) => e.from === from && e.to === to)) return;
		if (wouldCycle(from, to)) {
			error = 'Diese Verbindung würde einen Kreis bilden.';
			return;
		}
		const fromNode = byId.get(from);
		const toNode = byId.get(to);
		edges = [
			...edges,
			normalizeEdge({
				from,
				to,
				required: form.enforce || fromNode?.kind === 'CHOICE',
				label: fromNode?.kind === 'CHOICE' && toNode ? label(toNode) : undefined
			})
		];
	}

	function setEdgeRequired(key: string, required: boolean) {
		edges = edges.map((e) => (edgeKey(e) === key ? { ...e, required } : e));
	}

	function updateEdge(key: string, change: Partial<Edge>) {
		edges = edges.map((e) => (edgeKey(e) === key ? normalizeEdge({ ...e, ...change }) : e));
	}

	function removeEdge(key: string) {
		edges = edges.filter((e) => edgeKey(e) !== key);
		if (selectedEdge === key) selectedEdge = null;
	}

	// ------------------------------------------------------------ Ziehen

	let drag: {
		id: string;
		startX: number;
		startY: number;
		x: number;
		y: number;
		moved: boolean;
	} | null = null;

	function pointerDown(e: PointerEvent, node: PathNode) {
		if (connecting) return;
		drag = {
			id: node.id,
			startX: e.clientX,
			startY: e.clientY,
			x: node.x,
			y: node.y,
			moved: false
		};
		(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
	}

	function pointerMove(e: PointerEvent) {
		if (!drag) return;
		const dx = e.clientX - drag.startX;
		const dy = e.clientY - drag.startY;
		if (!drag.moved && Math.abs(dx) + Math.abs(dy) < 4) return;
		drag.moved = true;
		const target = drag;
		nodes = nodes.map((n) =>
			n.id === target.id ? { ...n, x: snap(target.x + dx), y: snap(target.y + dy) } : n
		);
	}

	function pointerUp() {
		drag = null;
	}

	function clickNode(node: PathNode) {
		selectedEdge = null;
		if (connecting) {
			if (!connectFrom) connectFrom = node.id;
			else {
				connect(connectFrom, node.id);
				connectFrom = null;
			}
			return;
		}
		selectedId = node.id;
	}

	function keyMove(e: KeyboardEvent, node: PathNode) {
		const step = e.shiftKey ? 100 : 20;
		const delta: Record<string, [number, number]> = {
			ArrowLeft: [-step, 0],
			ArrowRight: [step, 0],
			ArrowUp: [0, -step],
			ArrowDown: [0, step]
		};
		if (e.key === 'Enter' || e.key === ' ') {
			e.preventDefault();
			clickNode(node);
		} else if (delta[e.key]) {
			e.preventDefault();
			const [dx, dy] = delta[e.key];
			nodes = nodes.map((n) =>
				n.id === node.id ? { ...n, x: snap(n.x + dx), y: snap(n.y + dy) } : n
			);
		} else if (e.key === 'Delete') {
			removeNode(node.id);
		}
	}

	function arrange() {
		const positions = layered(nodes, edges);
		nodes = nodes.map((n) => ({ ...n, ...(positions.get(n.id) ?? { x: n.x, y: n.y }) }));
	}
</script>

<svelte:window
	onbeforeunload={(e) => {
		if (dirty) e.preventDefault();
	}}
/>

<svelte:head><title>Lernpfad bearbeiten | CodePath</title></svelte:head>

<div class="flex flex-col gap-6 pb-24">
	<header class="border-b border-slate-200 pb-6">
		<a
			href="/admin/learning-paths"
			class="mb-4 inline-block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
			>← Alle Lernpfade</a
		>
		<h1 class="font-sans text-3xl font-extrabold tracking-tight text-institutional-navy uppercase">
			{form.title || 'Lernpfad'}
		</h1>
	</header>

	{#if message}<p
			class="text-[11px] font-bold tracking-widest text-teal-700 uppercase"
			role="status"
		>
			{message}
		</p>{/if}
	{#if error}<div class="border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
			{error}
		</div>{/if}

	{#if saved}
		<section class="grid grid-cols-1 gap-4 border border-slate-200 bg-white p-5 md:grid-cols-4">
			<label class="ed-label">Titel<input class="ed-input" bind:value={form.title} /></label>
			<label class="ed-label"
				>Adresse<input class="ed-input mono" bind:value={form.slug} pattern="[a-z0-9-]+" /></label
			>
			<label class="ed-label md:col-span-2"
				>Beschreibung<input class="ed-input" bind:value={form.description} maxlength="500" /></label
			>
			<label class="ed-label md:col-span-2"
				>Gilt für
				<select class="ed-input" bind:value={form.courseId}>
					<option value={0}>Alle Kurse (ändert keine Freigaben)</option>
					{#each courses as course (course.id)}
						<option value={course.id}
							>Kurs {course.name}{course.status === 'ENDED' ? ' (beendet)' : ''}</option
						>
					{/each}
				</select>
			</label>
			<div class="flex flex-col gap-2 md:col-span-2">
				<span class="ed-label">Alles außerhalb des Lernpfads</span>
				<div class="flex flex-wrap gap-2">
					<select
						class="ed-input mt-0 flex-1"
						aria-label="Alles außerhalb des Lernpfads"
						value={outsideMode}
						onchange={(e) => setOutsideMode((e.currentTarget as HTMLSelectElement).value)}
					>
						<option value="FREE">Frei (folgt der Modul-Steuerung)</option>
						<option value="LOCKED">Gesperrt (nur Lernpfad sichtbar)</option>
						<option value="AFTER">Frei ab Station …</option>
					</select>
					{#if outsideMode === 'AFTER'}
						<select
							class="ed-input mt-0 flex-1"
							aria-label="Frei ab Station"
							bind:value={form.unlockRestAfter}
						>
							{#each nodes as n (n.id)}
								<option value={n.id}>ab „{label(n)}“</option>
							{/each}
						</select>
					{/if}
				</div>
				{#if form.courseId}
					<span class="text-xs text-slate-500"
						>Veröffentlicht übernimmt jedes Speichern die Module und Aufgaben dieses Lernpfads in
						die <a href="/admin/modules" class="underline">Modul-Steuerung</a> des Kurses.</span
					>
				{/if}
			</div>
			<div class="flex flex-wrap items-center gap-4 md:col-span-4">
				<label class="flex items-center gap-2 text-sm"
					><input type="checkbox" bind:checked={form.published} /> Veröffentlicht (für Schüler sichtbar)</label
				>
				<label class="flex items-center gap-2 text-sm font-bold text-institutional-navy"
					><input
						type="checkbox"
						checked={form.enforce}
						onchange={(e) => toggleEnforce((e.currentTarget as HTMLInputElement).checked)}
					/> Verbindlich (Pflicht-Vorgänger müssen erledigt sein)</label
				>
				<label class="flex items-center gap-2 text-sm text-violet-900"
					><input type="checkbox" bind:checked={form.templateOnly} /> Nur als Baustein (nicht einzeln
					in der Schüler-Auswahl)</label
				>
				<span class="flex-1"></span>
				<button class="ed-button" onclick={() => (preview ? (preview = null) : showPreview())}
					>{preview ? 'Vorschau schließen' : 'Vorschau'}</button
				>
				<a class="ed-button" href={exportUrl(['learningPaths'], [saved.slug])} download
					>Exportieren</a
				>
				<button class="ed-button text-rose-700" onclick={remove}>Löschen</button>
				<button
					class="ed-button bg-institutional-navy text-white"
					onclick={save}
					disabled={busy || !dirty}>{dirty ? 'Speichern' : 'Gespeichert'}</button
				>
			</div>
			{#if saved.usedIn?.length}
				<p class="text-xs text-slate-500 md:col-span-4">
					🧩 Wird als Baustein eingebunden in: <strong>{saved.usedIn.join(', ')}</strong>
				</p>
			{/if}
		</section>

		{#if saved.warnings?.length}
			<section
				class="border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900"
				role="status"
			>
				<p class="font-bold">Hinweise zum Lernpfad:</p>
				<ul class="mt-1 list-disc pl-5">
					{#each saved.warnings as w, i (i)}<li>{w}</li>{/each}
				</ul>
			</section>
		{/if}

		{#if applied}
			<section
				class="border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900"
				role="status"
			>
				{#if applied.length}
					<p class="font-bold">In die Modul-Steuerung übernommen:</p>
					<ul class="mt-1 list-disc pl-5">
						{#each applied as line, i (i)}<li>{line}</li>{/each}
					</ul>
				{:else}
					<p>Die Modul-Steuerung des Kurses entsprach schon diesem Lernpfad.</p>
				{/if}
			</section>
		{/if}

		{#if preview}
			<section class="flex flex-col gap-3 border border-slate-200 bg-white p-4">
				<div class="flex flex-wrap items-center justify-between gap-3">
					<p class="text-xs text-slate-500">
						Vorschau der gespeicherten Fassung. Wähle optional ein Schülerkonto, um dessen Stand und
						Sperren zu prüfen.
					</p>
					<label class="flex items-center gap-2 text-xs font-bold text-institutional-navy">
						Als Schüler ansehen:
						<select
							class="border border-slate-200 bg-white px-2 py-1 text-xs font-normal"
							bind:value={previewStudent}
							onchange={showPreview}
						>
							<option value="">Eigener Stand (Lehrkraft)</option>
							{#each filteredStudents as s (s.username)}
								<option value={s.username}
									>{s.displayName} ({s.username}){s.courseName ? ` · ${s.courseName}` : ''}</option
								>
							{/each}
						</select>
					</label>
				</div>
				<PathMap path={preview} student={previewStudent || undefined} />
			</section>
		{/if}

		<div class="grid grid-cols-1 gap-6 xl:grid-cols-[1fr_20rem]">
			<section class="flex min-w-0 flex-col gap-3">
				<div class="flex flex-wrap items-center gap-2">
					<button
						class="ed-button {connecting ? 'bg-institutional-gold text-white' : ''}"
						aria-pressed={connecting}
						onclick={() => {
							connecting = !connecting;
							connectFrom = null;
						}}>{connecting ? 'Verbinden beenden' : 'Verbinden'}</button
					>
					<button class="ed-button" onclick={arrange}>Automatisch anordnen</button>
					<span class="text-xs text-slate-500">
						{#if connecting}
							{connectFrom
								? `Jetzt die Station anklicken, die nach „${label(byId.get(connectFrom)!)}“ kommt.`
								: 'Erst die Station anklicken, mit der es losgeht.'}
						{:else}
							Stationen ziehen (oder mit Pfeiltasten verschieben), anklicken zum Bearbeiten. Linien
							anklicken zum Löschen.
						{/if}
					</span>
				</div>
				<div
					class="overflow-auto border border-slate-200"
					style="max-height: 70vh; background-color: #f8fafc; background-image: radial-gradient(#cbd5e1 1px, transparent 1px); background-size: 20px 20px"
				>
					<div
						class="relative"
						style="width: {size.width}px; height: {size.height}px"
						role="application"
						aria-label="Lernpfad-Karte"
						onpointermove={pointerMove}
						onpointerup={pointerUp}
					>
						<svg class="absolute inset-0" width={size.width} height={size.height}>
							<defs>
								<marker
									id="edit-arrow"
									viewBox="0 0 10 10"
									refX="8"
									refY="5"
									markerWidth="7"
									markerHeight="7"
									orient="auto-start-reverse"
								>
									<path d="M 0 0 L 10 5 L 0 10 z" class="fill-slate-500" />
								</marker>
							</defs>
							{#each edges as edge (edgeKey(edge))}
								{@const from = byId.get(edge.from)}
								{@const to = byId.get(edge.to)}
								{#if from && to}
									{@const isGateTarget = to.kind === 'AND' || to.kind === 'OR'}
									{@const slot = isGateTarget ? incomingSlot(edges, byId, edge) : { index: 0, count: 1 }}
									{@const d = edgePath(from, to, slot.index, slot.count)}
									<path
										{d}
										fill="none"
										stroke-width="2.5"
										class={selectedEdge === edgeKey(edge) ? 'stroke-rose-500' : 'stroke-slate-400'}
										stroke-dasharray={edge.required ? undefined : '6 5'}
										marker-end="url(#edit-arrow)"
									/>
									<!-- Breite, unsichtbare Linie zum Anklicken -->
									<path
										{d}
										fill="none"
										stroke="transparent"
										stroke-width="14"
										class="cursor-pointer"
										role="button"
										tabindex="-1"
										aria-label="Verbindung {label(from)} nach {label(to)}"
										onclick={() => {
											selectedEdge = edgeKey(edge);
											selectedId = null;
										}}
										onkeydown={() => {}}
									/>
								{/if}
							{/each}
						</svg>
						{#each nodes as node (node.id)}
							{@const isGate = node.kind === 'AND' || node.kind === 'OR'}
							{@const branchStyle = branchStyles.get(node.id)}
							{@const subpathInfo = node.kind === 'PATH' ? pathsBySlug.get(node.path ?? '') : undefined}
							<div
								class="absolute flex cursor-grab touch-none flex-col justify-between border-2 p-3 select-none active:cursor-grabbing {node.kind ===
								'MILESTONE'
									? 'rounded-full border-dashed border-institutional-gold bg-white text-center'
									: node.kind === 'CHOICE'
										? 'border-institutional-navy bg-amber-50/70'
										: node.kind === 'BONUS'
											? (branchStyle ?? 'border-amber-400 bg-amber-50/80')
											: node.kind === 'PATH'
												? (branchStyle ?? 'border-violet-400 bg-violet-50/50')
												: isGate
													? 'border-institutional-navy bg-slate-50'
													: (branchStyle ?? 'border-slate-300 bg-white')} {selectedId === node.id
									? 'ring-4 ring-institutional-navy/30'
									: ''} {connectFrom === node.id ? 'ring-4 ring-institutional-gold/60' : ''}"
								style="left: {node.x}px; top: {node.y}px; width: {NODE_W}px; height: {NODE_H}px"
								role="button"
								tabindex="0"
								aria-label="{KIND_LABELS[node.kind]}: {label(node)}"
								aria-pressed={selectedId === node.id}
								onpointerdown={(e) => pointerDown(e, node)}
								onclick={() => clickNode(node)}
								onkeydown={(e) => keyMove(e, node)}
							>
								<div
									class="flex items-start gap-2 {node.kind === 'MILESTONE' ? 'justify-center' : ''}"
								>
									{#if isGate}
										<span
											class="flex h-7 w-7 shrink-0 items-center justify-center border-2 border-slate-900 bg-white font-mono text-xs font-black text-institutional-navy"
										>
											{icon(node)}
										</span>
									{:else}
										<span class="text-lg leading-none">{icon(node)}</span>
									{/if}
									<span class="line-clamp-2 text-sm leading-tight font-bold text-institutional-navy"
										>{label(node)}</span
									>
								</div>
								<span
									class="font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase"
									>{node.kind === 'PATH' && subpathInfo
										? `${KIND_LABELS[node.kind]} · ${subpathInfo.stations} Stationen`
										: node.kind === 'BONUS'
											? `Bonus · +${node.points ?? 10} P.`
											: KIND_LABELS[node.kind]}</span
								>
							</div>
						{/each}
					</div>
				</div>
			</section>

			<aside class="flex flex-col gap-4">
				{#if selectedEdge}
					{@const edge = edges.find((e) => edgeKey(e) === selectedEdge)}
					{#if edge}
						{@const fromNode = byId.get(edge.from)}
						<section class="flex flex-col gap-3 border border-slate-200 bg-white p-4">
							<h2 class="ed-label">Verbindung</h2>
							<p class="text-sm">
								{label(fromNode!)} → {label(byId.get(edge.to)!)}
							</p>
							{#if fromNode?.kind === 'CHOICE'}
								<label class="ed-label">
									Beschriftung des Wegs (Pflicht)
									<input
										class="ed-input"
										maxlength="80"
										value={edge.label ?? ''}
										oninput={(e) =>
											updateEdge(selectedEdge!, {
												label: (e.currentTarget as HTMLInputElement).value
											})}
									/>
								</label>
								<label class="ed-label">
									Kurzbeschreibung (optional)
									<input
										class="ed-input"
										maxlength="200"
										placeholder="z. B. schwieriger – dafür +50 Punkte"
										value={edge.hint ?? ''}
										oninput={(e) =>
											updateEdge(selectedEdge!, {
												hint: (e.currentTarget as HTMLInputElement).value
											})}
									/>
								</label>
							{/if}
							<fieldset class="flex flex-col gap-1 text-sm">
								<legend class="ed-label">Art der Verbindung</legend>
								<label class="flex items-center gap-2">
									<input
										type="radio"
										name="edge-required"
										checked={!!edge.required}
										onchange={() => setEdgeRequired(selectedEdge!, true)}
									/>
									Pflicht (durchgezogen)
								</label>
								<label class="flex items-center gap-2">
									<input
										type="radio"
										name="edge-required"
										checked={!edge.required}
										onchange={() => setEdgeRequired(selectedEdge!, false)}
									/>
									Empfehlung (gestrichelt)
								</label>
							</fieldset>
							<button class="ed-button text-rose-700" onclick={() => removeEdge(selectedEdge!)}
								>Verbindung löschen</button
							>
						</section>
					{/if}
				{/if}

				{#if selected}
					{@const incomingCount = edges.filter((e) => e.to === selected.id).length}
					{@const targetSubpath = selected.kind === 'PATH' ? pathsBySlug.get(selected.path ?? '') : undefined}
					<section
						class="flex flex-col gap-3 border border-slate-200 bg-white p-4"
						aria-label="Station bearbeiten"
					>
						<h2 class="ed-label">{KIND_LABELS[selected.kind]}</h2>
						{#if selected.kind === 'MODULE' || selected.kind === 'TASK'}
							<p class="font-mono text-xs text-slate-500">
								{selected.module}{selected.task ? `/${selected.task}` : ''}
							</p>
						{/if}
						{#if selected.kind === 'PATH'}
							<p class="font-mono text-xs text-slate-500">
								/{selected.path}
								{#if targetSubpath}· {targetSubpath.stations} Stationen{/if}
							</p>
							{#if targetSubpath}
								<a
									class="ed-button text-center"
									href="/admin/learning-paths/{targetSubpath.id}"
									>Baustein bearbeiten →</a
								>
							{/if}
						{/if}
						{#if selected.kind === 'AND' || selected.kind === 'OR'}
							<p class="text-xs text-slate-600">
								{incomingCount} eingehende Verbindung{incomingCount === 1 ? '' : 'en'}
								{#if incomingCount < 2}
									<span class="font-bold text-amber-700">(mindestens 2 erforderlich)</span>
								{/if}
							</p>
						{/if}
						{#if selected.kind === 'OR'}
							<label class="ed-label">
								Mindestens erfüllte Eingänge (von {Math.max(2, incomingCount)})
								<input
									type="number"
									class="ed-input"
									min="1"
									max={Math.max(2, incomingCount)}
									value={selected.min ?? 1}
									oninput={(e) =>
										updateNode(selected.id, {
											min: Math.max(1, Number((e.currentTarget as HTMLInputElement).value) || 1)
										})}
								/>
							</label>
						{/if}
						{#if selected.kind === 'BONUS'}
							<label class="ed-label">
								Bonuspunkte (1–1000)
								<input
									type="number"
									class="ed-input"
									min="1"
									max="1000"
									value={selected.points ?? 10}
									oninput={(e) =>
										updateNode(selected.id, {
											points: Math.max(1, Math.min(1000, Number((e.currentTarget as HTMLInputElement).value) || 10))
										})}
								/>
							</label>
						{/if}
						{#if selected.kind === 'MODULE'}
							{@const node = selected}
							{@const taken = usedElsewhere(node)}
							<fieldset class="flex flex-col gap-2">
								<legend class="ed-label">Aufgaben dieser Station</legend>
								<label class="flex items-center gap-2 text-sm"
									><input
										type="radio"
										name="partial"
										checked={!node.tasks?.length}
										onchange={() => setPartial(node, false)}
									/> Alle Aufgaben des Moduls</label
								>
								<label class="flex items-center gap-2 text-sm"
									><input
										type="radio"
										name="partial"
										checked={!!node.tasks?.length}
										onchange={() => setPartial(node, true)}
									/> Nur bestimmte Aufgaben</label
								>
								{#if node.tasks?.length}
									<ul
										class="flex max-h-64 flex-col gap-1 overflow-auto border border-slate-100 p-2"
										aria-label="Aufgaben auswählen"
									>
										{#each moduleTasks(node.module) as t, i (t.slug)}
											<li>
												<label class="flex items-start gap-2 text-xs">
													<input
														type="checkbox"
														checked={node.tasks.includes(t.slug)}
														onchange={(e) =>
															toggleTask(
																node,
																t.slug,
																(e.currentTarget as HTMLInputElement).checked
															)}
													/>
													<span
														>{i + 1}. {t.title}{#if taken.includes(t.slug)}<span
																class="text-slate-400"
															>
																· schon in anderer Station</span
															>{/if}</span
													>
												</label>
											</li>
										{/each}
									</ul>
									<button class="ed-button" onclick={() => splitRest(node)}
										>Restliche Aufgaben als neue Station</button
									>
								{/if}
							</fieldset>
						{/if}
						<label class="ed-label"
							>{selected.kind === 'MILESTONE' || selected.kind === 'BONUS'
								? 'Beschriftung'
								: 'Eigene Beschriftung (optional)'}
							<input
								class="ed-input"
								value={selected.label ?? ''}
								maxlength="80"
								oninput={(e) => setLabel((e.currentTarget as HTMLInputElement).value)}
							/></label
						>
						<div class="flex flex-col gap-2">
							<span class="ed-label">Danach kommt</span>
							{#each edges.filter((e) => e.from === selected.id) as edge (edgeKey(edge))}
								<div class="flex flex-col gap-1 border border-slate-100 bg-slate-50/60 p-2 text-sm">
									<div class="flex items-center justify-between gap-2">
										<span class="font-medium">→ {label(byId.get(edge.to)!)}</span>
										<button
											class="text-xs text-rose-700"
											onclick={() => removeEdge(edgeKey(edge))}
											aria-label="Verbindung zu {label(byId.get(edge.to)!)} löschen">✕</button
										>
									</div>
									{#if selected.kind === 'CHOICE'}
										<input
											class="ed-input text-xs"
											placeholder="Beschriftung des Wegs (Pflicht)"
											aria-label="Weg-Beschriftung nach {label(byId.get(edge.to)!)}"
											maxlength="80"
											value={edge.label ?? ''}
											oninput={(e) =>
												updateEdge(edgeKey(edge), {
													label: (e.currentTarget as HTMLInputElement).value
												})}
										/>
										<input
											class="ed-input text-xs"
											placeholder="Kurzbeschreibung (optional)"
											aria-label="Weg-Kurzbeschreibung nach {label(byId.get(edge.to)!)}"
											maxlength="200"
											value={edge.hint ?? ''}
											oninput={(e) =>
												updateEdge(edgeKey(edge), {
													hint: (e.currentTarget as HTMLInputElement).value
												})}
										/>
									{/if}
								</div>
							{/each}
							<select
								class="ed-input"
								aria-label="Verbindung hinzufügen"
								value=""
								onchange={(e) => {
									const target = (e.currentTarget as HTMLSelectElement).value;
									if (target && selected) connect(selected.id, target);
									(e.currentTarget as HTMLSelectElement).value = '';
								}}
							>
								<option value="">+ Verbindung zu …</option>
								{#each nodes.filter((n) => n.id !== selected.id) as n (n.id)}
									<option value={n.id}>{label(n)}</option>
								{/each}
							</select>
						</div>
						{#if selected.kind !== 'CHOICE'}
							<button
								type="button"
								class="ed-button bg-white"
								disabled={busy}
								onclick={() =>
									runCourseAction({
										action: 'UNLOCK_NODE',
										pathSlug: saved?.slug,
										nodeId: selected.id
									})}
							>
								Station für ganzen Kurs freischalten
							</button>
						{/if}
						<button class="ed-button text-rose-700" onclick={() => removeNode(selected.id)}
							>Station entfernen</button
						>
					</section>
				{/if}

				{#if choiceStats.length > 0}
					<section class="flex flex-col gap-3 border border-slate-200 bg-white p-4">
						<h2 class="ed-label">Weichen-Übersicht im Kurs</h2>
						{#each choiceStats as stat (`${stat.pathSlug}:${stat.nodeId}`)}
							{@const statKey = `${stat.pathSlug}:${stat.nodeId}`}
							<div class="space-y-2 border border-slate-200 bg-slate-50/60 p-3 text-xs">
								<div class="flex flex-wrap items-center justify-between gap-2">
									<span class="font-bold text-institutional-navy">🔀 {stat.nodeLabel}</span>
									{#if stat.subpath}
										<span
											class="border border-violet-200 bg-violet-50 px-1.5 py-0.5 font-mono text-[9px] font-bold text-violet-800 uppercase"
										>
											🧩 {stat.pathTitle}
										</span>
									{/if}
								</div>
								<p class="font-mono text-[10px] text-slate-600">
									{#each stat.branches ?? [] as b, idx (b.to)}
										{idx > 0 ? ', ' : ''}{b.count}× {b.label}
									{/each}
									· {stat.openCount}× noch offen
								</p>
								<div class="space-y-1.5 pt-1">
									{#each stat.branches ?? [] as b (b.to)}
										<div
											class="flex flex-wrap items-center justify-between gap-2 border border-slate-200 bg-white px-2.5 py-1.5"
										>
											<span class="font-semibold text-slate-700"
												>{b.label} ({b.count}/{stat.totalStudents})</span
											>
											<div class="flex gap-1">
												<button
													type="button"
													disabled={busy}
													class="border border-slate-200 px-2 py-0.5 font-mono text-[9px] font-bold text-institutional-navy uppercase hover:border-institutional-navy"
													onclick={() =>
														runCourseAction({
															action: 'SET_CHOICE',
															pathSlug: stat.pathSlug,
															nodeId: stat.nodeId,
															to: b.to
														})}
												>
													Für Kurs wählen
												</button>
												<button
													type="button"
													disabled={busy}
													class="border border-slate-200 px-2 py-0.5 font-mono text-[9px] font-bold text-sky-700 uppercase hover:border-sky-500"
													onclick={() =>
														runCourseAction({
															action: 'UNLOCK_BRANCH',
															pathSlug: stat.pathSlug,
															nodeId: stat.nodeId,
															to: b.to
														})}
												>
													+ Für Kurs frei
												</button>
											</div>
										</div>
									{/each}
								</div>
								<div class="flex flex-wrap items-center justify-between gap-2 pt-1">
									<label class="flex items-center gap-1.5 text-[11px] text-slate-600">
										<input
											type="checkbox"
											checked={!!resetCourseProgress[statKey]}
											onchange={(e) =>
												(resetCourseProgress[statKey] = (
													e.currentTarget as HTMLInputElement
												).checked)}
										/>
										<span>Fortschritt zurücksetzen</span>
									</label>
									<button
										type="button"
										disabled={busy}
										class="border border-rose-200 bg-white px-2.5 py-1 font-mono text-[9px] font-bold text-rose-700 uppercase hover:bg-rose-50"
										onclick={() =>
											runCourseAction({
												action: 'RESET_CHOICE',
												pathSlug: stat.pathSlug,
												nodeId: stat.nodeId,
												resetProgress: !!resetCourseProgress[statKey]
											})}
									>
										Alle Wahlen zurücksetzen
									</button>
								</div>
							</div>
						{/each}
					</section>
				{/if}

				<form class="flex flex-col gap-3 border border-slate-200 bg-white p-4" onsubmit={addNode}>
					<h2 class="ed-label">Station hinzufügen</h2>
					<select class="ed-input" bind:value={draft.kind} aria-label="Art der Station">
						<option value="MODULE">Modul</option>
						<option value="TASK">Einzelne Aufgabe</option>
						<option value="PATH">Lernpfad-Baustein (🧩)</option>
						<option value="BONUS">Bonus-Station (⭐)</option>
						<option value="MILESTONE">Meilenstein</option>
						<option value="CHOICE">Entweder-oder-Weiche (🔀)</option>
						<option value="AND">Logik-Block · UND (&amp;)</option>
						<option value="OR">Logik-Block · ODER (≥1)</option>
					</select>
					{#if draft.kind === 'MODULE' || draft.kind === 'TASK'}
						<select
							class="ed-input"
							bind:value={draft.module}
							aria-label="Modul"
							onchange={() => (draft.task = '')}
						>
							{#each catalog as m (m.module.slug)}
								<option value={m.module.slug}>{m.module.icon} {m.module.title}</option>
							{/each}
						</select>
					{/if}
					{#if draft.kind === 'TASK'}
						<select class="ed-input" bind:value={draft.task} aria-label="Aufgabe">
							<option value="">Aufgabe wählen …</option>
							{#each draftTasks as t (t.slug)}<option value={t.slug}>{t.title}</option>{/each}
						</select>
					{/if}
					{#if draft.kind === 'PATH'}
						<select class="ed-input" bind:value={draft.path} aria-label="Lernpfad-Baustein">
							<option value="">Baustein wählen …</option>
							{#each availableSubpaths as p (p.slug)}
								<option value={p.slug}>🧩 {p.title} ({p.stations} Stationen)</option>
							{/each}
						</select>
					{/if}
					{#if draft.kind === 'OR'}
						<label class="ed-label">
							Mindestens erfüllte Eingänge
							<input
								type="number"
								class="ed-input"
								min="1"
								max="10"
								aria-label="Mindestens erfüllte Eingänge"
								bind:value={draft.min}
							/>
						</label>
					{/if}
					{#if draft.kind === 'BONUS'}
						<label class="ed-label">
							Bonuspunkte (1–1000)
							<input
								type="number"
								class="ed-input"
								min="1"
								max="1000"
								aria-label="Bonuspunkte"
								bind:value={draft.points}
							/>
						</label>
					{/if}
					{#if draft.kind === 'MILESTONE' || draft.kind === 'BONUS' || draft.kind === 'CHOICE' || draft.kind === 'AND' || draft.kind === 'OR' || draft.kind === 'PATH'}
						<input
							class="ed-input"
							placeholder={draft.kind === 'MILESTONE'
								? 'z. B. Grundlagen geschafft!'
								: draft.kind === 'BONUS'
									? 'z. B. Fleißaufgabe gelöst!'
									: draft.kind === 'CHOICE'
										? 'z. B. Cloud oder Web?'
										: 'Beschriftung (optional)'}
							aria-label="Beschriftung"
							maxlength="80"
							bind:value={draft.label}
						/>
					{/if}
					<button class="ed-button bg-white">Hinzufügen</button>
					<p class="text-xs text-slate-400">
						Ist eine Station gewählt, wird die neue darunter angehängt und verbunden.
					</p>
				</form>
			</aside>
		</div>
	{/if}
</div>
