<script lang="ts">
	import { onMount, tick } from 'svelte';
	import { beforeNavigate, goto } from '$app/navigation';
	import { page } from '$app/state';
	import {
		api,
		type CourseSummary,
		type EdgeCondition,
		type GateStudentStat,
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
		PADDING,
		snap
	} from '$lib/pathway/geometry';
	import PathMap from '$lib/pathway/PathMap.svelte';
	import PathIcon, { type IconName } from '$lib/pathway/PathIcon.svelte';
	import { exportUrl } from '$lib/transfer';

	type ModuleRow = Schemas['ModuleRow'];
	type Edge = Schemas['Edge'];
	type ChoiceNodeStats = Schemas['ChoiceNodeStats'];
	/** BLOCK: frisch hineingezogener leerer Block, dessen Art noch festgelegt wird (wird nie gespeichert) */
	type EditorKind = PathNode['kind'] | 'BLOCK';
	type EditorNode = Omit<PathNode, 'kind'> & { kind: EditorKind };
	/** Wie ein Block genutzt wird – die Auswahl rechts */
	type Variant = Exclude<PathNode['kind'], 'MODULE'> | 'MODULE' | 'PART';

	const MAX_COORDINATE = 4000;
	const GAP_Y = NODE_H + 70;

	const id = $derived(Number(page.params.id));

	let saved = $state<PathAdmin | null>(null);
	let form = $state({
		title: '',
		slug: '',
		description: '',
		published: false,
		courseId: 0,
		enforce: false,
		templateOnly: false,
		hideOverview: false,
		lockOthers: false,
		unlockRestAfter: ''
	});
	let courses = $state<CourseSummary[]>([]);
	/** Alle CodePaths – für eingebundene CodePath-Bausteine */
	let allPaths = $state<PathSummary[]>([]);
	let students = $state<StudentOverview[]>([]);
	let previewStudent = $state('');
	let choiceStats = $state<ChoiceNodeStats[]>([]);
	let gateStats = $state<GateStudentStat[]>([]);
	let resetCourseProgress = $state<Record<string, boolean>>({});
	/** Was das letzte Speichern in die Modul-Steuerung übernommen hat */
	let applied = $state<string[] | null>(null);
	let nodes = $state<EditorNode[]>([]);
	let edges = $state<Edge[]>([]);
	let catalog = $state<ModuleRow[]>([]);
	let error = $state('');
	let message = $state('');
	let busy = $state(false);

	let selectedId = $state<string | null>(null);
	let selectedEdge = $state<string | null>(null);
	let preview = $state<PathView | null>(null);
	/** Stationen, für die „Teil eines Moduls“ gewählt wurde, bevor ein Modul feststand */
	let pendingPart = $state<Record<string, boolean>>({});

	const selected = $derived(nodes.find((n) => n.id === selectedId) ?? null);
	const byId = $derived(new Map(nodes.map((n) => [n.id, n])));
	const modulesBySlug = $derived(new Map(catalog.map((m) => [m.module.slug, m])));
	const pathsBySlug = $derived(new Map(allPaths.map((p) => [p.slug, p])));
	const availableSubpaths = $derived(allPaths.filter((p) => p.id !== id));
	const filteredStudents = $derived(
		form.courseId ? students.filter((s) => s.courseId === form.courseId) : students
	);
	/** Was mit allem passiert, das nicht im CodePath vorkommt */
	const outsideMode = $derived(
		form.unlockRestAfter ? 'AFTER' : form.lockOthers ? 'LOCKED' : 'FREE'
	);
	const current = $derived(JSON.stringify({ ...form, graph: { nodes, edges } }));
	const savedState = $derived(
		saved
			? JSON.stringify({
					title: saved.title,
					slug: saved.slug,
					description: saved.description,
					published: saved.published,
					courseId: saved.courseId ?? 0,
					enforce: saved.enforce ?? false,
					templateOnly: saved.templateOnly ?? false,
					hideOverview: saved.hideOverview ?? false,
					lockOthers: saved.lockOthers,
					unlockRestAfter: saved.unlockRestAfter ?? '',
					graph: saved.graph
				})
			: ''
	);
	const dirty = $derived(!!saved && current !== savedState);
	const incompleteCount = $derived(nodes.filter(incomplete).length);
	const courseName = $derived(courses.find((c) => c.id === form.courseId)?.name);

	onMount(() => loadEditor(id));

	// Wechsel zu einem anderen CodePath (z. B. „Baustein bearbeiten“) lädt den Editor neu
	$effect(() => {
		const targetId = id;
		if (saved && saved.id !== targetId) loadEditor(targetId);
	});

	async function loadEditor(targetId: number) {
		try {
			const [path, pathList, modules, courseList, studentList] = await Promise.all([
				api<PathAdmin>(`/api/admin/codepaths/${targetId}`),
				api<PathSummary[]>('/api/admin/codepaths'),
				api<ModuleRow[]>('/api/admin/content'),
				api<CourseSummary[]>('/api/admin/courses'),
				api<StudentOverview[]>('/api/admin/students').catch(() => [])
			]);
			catalog = modules;
			courses = courseList;
			allPaths = pathList;
			students = studentList;
			selectedId = null;
			selectedEdge = null;
			preview = null;
			apply(path);
			await refreshStats();
			await tick();
			fitHeight();
		} catch (e) {
			error = (e as Error).message;
		}
	}

	/** Weichen- und Punkte-Tor-Übersicht für den Kurs */
	async function refreshStats() {
		const query = form.courseId ? `?courseId=${form.courseId}` : '';
		const [c, g] = await Promise.all([
			api<ChoiceNodeStats[]>(`/api/admin/codepaths/${id}/choice-stats${query}`).catch(() => []),
			api<GateStudentStat[]>(`/api/admin/codepaths/${id}/gate-stats${query}`).catch(() => [])
		]);
		choiceStats = c;
		gateStats = g;
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
			const res = await api<{ affected: number }>(`/api/admin/codepaths/${id}/course-actions`, {
				method: 'POST',
				body: { ...body, courseId: form.courseId || undefined }
			});
			await refreshStats();
			if (preview) await showPreview();
			flash(`Für ${res.affected} Schüler/innen ausgeführt`);
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
			nodes: path.graph.nodes.map((n) => normalize(n) as PathNode),
			edges: path.graph.edges.map(normalizeEdge)
		};
		saved = { ...path, graph };
		form = {
			title: path.title,
			slug: path.slug,
			description: path.description ?? '',
			published: path.published,
			courseId: path.courseId ?? 0,
			enforce: path.enforce ?? false,
			templateOnly: path.templateOnly ?? false,
			hideOverview: path.hideOverview ?? false,
			lockOthers: path.lockOthers,
			unlockRestAfter: path.unlockRestAfter ?? ''
		};
		nodes = structuredClone(graph.nodes);
		edges = structuredClone(graph.edges);
	}

	function normalize(n: EditorNode): EditorNode {
		const node: Record<string, unknown> = { id: n.id, kind: n.kind };
		if (n.module) node.module = n.module;
		if (n.task) node.task = n.task;
		if (n.kind === 'MODULE' && n.tasks?.length) node.tasks = n.tasks;
		if (n.label) node.label = n.label;
		if (n.kind === 'PATH' && n.path) node.path = n.path;
		if (n.kind === 'OR') node.min = Math.max(1, n.min ?? 1);
		if (n.kind === 'BONUS') node.points = Math.max(1, Math.min(1000, n.points ?? 10));
		if (n.kind === 'REMEDIAL' && n.knowledgeArticle?.trim())
			node.knowledgeArticle = n.knowledgeArticle.trim();
		if (n.boss) node.boss = true;
		if (n.requirement) node.requirement = n.requirement;
		node.x = n.x;
		node.y = n.y;
		return node as unknown as EditorNode;
	}

	function normalizeEdge(e: Partial<Edge> & { from: string; to: string }): Edge {
		const edge: Record<string, unknown> = { from: e.from, to: e.to, required: !!e.required };
		if (e.label?.trim()) edge.label = e.label.trim();
		if (e.hint?.trim()) edge.hint = e.hint.trim();
		if (e.condition?.rule) {
			edge.condition = {
				rule: e.condition.rule,
				task: e.condition.task?.trim() || undefined,
				scoreThreshold: e.condition.scoreThreshold,
				failedAttemptsThreshold: e.condition.failedAttemptsThreshold,
				hintsThreshold: e.condition.hintsThreshold
			};
		}
		return edge as unknown as Edge;
	}

	/** Verbindlich: Pflicht-Vorgänger müssen erledigt sein; beim Einschalten werden alle Verbindungen Pflicht */
	function toggleEnforce(checked: boolean) {
		form.enforce = checked;
		if (!checked) form.hideOverview = false;
		if (checked && edges.length && edges.every((e) => !e.required))
			edges = edges.map((e) => ({ ...e, required: true }));
	}

	function setOutsideMode(mode: string) {
		form.lockOthers = mode !== 'FREE';
		form.unlockRestAfter = mode === 'AFTER' ? (nodes[nodes.length - 1]?.id ?? '') : '';
	}

	function flash(text: string) {
		message = text;
		setTimeout(() => (message = ''), 3000);
	}

	async function save() {
		error = '';
		const open = nodes.find(incomplete);
		if (open) {
			select(open.id);
			reveal(open);
			error = `„${label(open)}“ ist noch nicht fertig eingerichtet – rechts die Art und den Inhalt wählen.`;
			return;
		}
		busy = true;
		try {
			const updated = await api<PathAdmin>(`/api/admin/codepaths/${id}`, {
				method: 'PUT',
				body: {
					...form,
					graph: { nodes: nodes.map(normalize), edges: edges.map(normalizeEdge) }
				}
			});
			apply(updated);
			applied = form.courseId && form.published ? updated.applied : null;
			flash('Gespeichert');
			await refreshStats();
			if (preview) await showPreview();
		} catch (e) {
			error = (e as Error).message;
		} finally {
			busy = false;
		}
	}

	async function remove() {
		if (!saved || !confirm(`CodePath „${saved.title}“ löschen?`)) return;
		try {
			await api(`/api/admin/codepaths/${id}`, { method: 'DELETE' });
			saved = null;
			goto('/admin/codepaths');
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function showPreview() {
		if (!saved) return;
		try {
			const query = previewStudent ? `?student=${encodeURIComponent(previewStudent)}` : '';
			preview = await api<PathView>(`/api/codepaths/${saved.slug}${query}`);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	// ------------------------------------------------------------ Stationen

	function moduleTitle(slug: string): string {
		return modulesBySlug.get(slug)?.module.title ?? slug;
	}

	function label(n: EditorNode): string {
		if (n.label) return n.label;
		if (n.kind === 'BLOCK') return 'Neuer Block';
		if (n.kind === 'MILESTONE') return 'Meilenstein';
		if (n.kind === 'CHOICE') return 'Weiche';
		if (n.kind === 'BONUS') return 'Bonus';
		if (n.kind === 'AND') return 'UND-Block';
		if (n.kind === 'OR') return (n.min ?? 1) > 1 ? `ODER-Block (mind. ${n.min})` : 'ODER-Block';
		if (n.kind === 'PATH')
			return n.path ? (pathsBySlug.get(n.path)?.title ?? `${n.path} (fehlt)`) : 'CodePath wählen';
		if (!n.module) return n.kind === 'MODULE' ? 'Modul wählen' : 'Aufgabe wählen';
		const module = modulesBySlug.get(n.module);
		if (n.kind === 'MODULE') {
			if (!module) return `${n.module} (fehlt)`;
			return n.tasks?.length
				? `${module.module.title} (${n.tasks.length} Aufgaben)`
				: module.module.title;
		}
		if (!n.task) return `${module?.module.title ?? n.module}: Aufgabe wählen`;
		const task = module?.tasks.find((t) => t.slug === n.task);
		return task?.title ?? `${n.module}/${n.task} (fehlt)`;
	}

	/** Fehlt noch etwas, damit die Station gespeichert werden kann? */
	function incomplete(n: EditorNode): boolean {
		if (n.kind === 'BLOCK') return true;
		if (n.kind === 'MILESTONE' || n.kind === 'BONUS') return !n.label?.trim();
		if (n.kind === 'PATH') return !n.path;
		if (n.kind === 'CHOICE' || n.kind === 'AND' || n.kind === 'OR') return false;
		if (!n.module) return true;
		return (n.kind === 'TASK' || n.kind === 'REMEDIAL') && !n.task;
	}

	/** Verweist die Station auf ein Modul oder eine Aufgabe, die es nicht (mehr) gibt? */
	function missing(n: EditorNode): boolean {
		if (n.kind === 'PATH') return !!n.path && !pathsBySlug.has(n.path);
		if (n.kind !== 'MODULE' && n.kind !== 'TASK' && n.kind !== 'REMEDIAL') return false;
		if (!n.module) return false;
		const module = modulesBySlug.get(n.module);
		if (!module) return true;
		return n.kind !== 'MODULE' && !!n.task && !module.tasks.some((t) => t.slug === n.task);
	}

	function variant(n: EditorNode): Variant | null {
		if (n.kind === 'BLOCK') return null;
		if (n.kind === 'MODULE') return n.tasks?.length || pendingPart[n.id] ? 'PART' : 'MODULE';
		return n.kind;
	}

	type VariantInfo = { value: Variant; label: string; hint: string; icon: IconName };
	/** Inhalte: was Schüler bearbeiten */
	const CONTENT_VARIANTS: VariantInfo[] = [
		{ value: 'MODULE', label: 'Ganzes Modul', hint: 'Alle Aufgaben eines Moduls', icon: 'module' },
		{
			value: 'PART',
			label: 'Teil eines Moduls',
			hint: 'Nur ausgewählte Aufgaben',
			icon: 'module-part'
		},
		{ value: 'TASK', label: 'Einzelne Aufgabe', hint: 'Genau eine Aufgabe', icon: 'task' },
		{
			value: 'REMEDIAL',
			label: 'Stütz-Knoten',
			hint: 'Hilfe-Aufgabe, nur bei Schwierigkeiten (Bedingung „Schwierigkeiten“)',
			icon: 'lifebuoy'
		},
		{
			value: 'PATH',
			label: 'CodePath-Baustein',
			hint: 'Bindet einen anderen CodePath als Ganzes ein',
			icon: 'puzzle'
		}
	];
	/** Steuerung: wie der Weg verläuft */
	const FLOW_VARIANTS: VariantInfo[] = [
		{
			value: 'MILESTONE',
			label: 'Meilenstein',
			hint: 'Zwischenziel mit Beschriftung',
			icon: 'milestone'
		},
		{ value: 'BONUS', label: 'Bonus', hint: 'Bringt Extrapunkte beim Erreichen', icon: 'star' },
		{
			value: 'CHOICE',
			label: 'Weiche',
			hint: 'Schüler wählen einen von mehreren Wegen; die anderen werden gesperrt',
			icon: 'split'
		},
		{
			value: 'AND',
			label: 'UND',
			hint: 'Geht erst weiter, wenn alle eingehenden Wege erledigt sind',
			icon: 'gate'
		},
		{
			value: 'OR',
			label: 'ODER',
			hint: 'Geht weiter, sobald eine Mindestzahl eingehender Wege erledigt ist',
			icon: 'gate'
		}
	];
	const VARIANTS = [...CONTENT_VARIANTS, ...FLOW_VARIANTS];
	const usesModule = (v: Variant | null) =>
		v === 'MODULE' || v === 'PART' || v === 'TASK' || v === 'REMEDIAL';

	function variantLabel(n: EditorNode): string {
		const v = variant(n);
		return v ? VARIANTS.find((x) => x.value === v)!.label : 'Leerer Block';
	}

	function nodeIcon(n: EditorNode): { name: IconName | null; emoji: string | null } {
		if (n.kind === 'BLOCK') return { name: 'block', emoji: null };
		if (n.kind === 'MILESTONE') return { name: n.requirement ? 'door' : 'milestone', emoji: null };
		if (missing(n)) return { name: 'warning', emoji: null };
		if (n.boss) return { name: 'crown', emoji: null };
		const fixed: Partial<Record<EditorKind, IconName>> = {
			CHOICE: 'split',
			PATH: 'puzzle',
			BONUS: 'star',
			REMEDIAL: 'lifebuoy'
		};
		if (fixed[n.kind]) return { name: fixed[n.kind]!, emoji: null };
		const emoji = modulesBySlug.get(n.module)?.module.icon ?? null;
		if (!n.module || !emoji) return { name: n.kind === 'TASK' ? 'task' : 'module', emoji: null };
		return { name: null, emoji };
	}

	function freeId(): string {
		let i = nodes.length + 1;
		while (byId.has(`n${i}`)) i++;
		return `n${i}`;
	}

	/** Veröffentlichte, nicht archivierte Aufgaben eines Moduls in ihrer Reihenfolge */
	function moduleTasks(moduleSlug: string) {
		return (modulesBySlug.get(moduleSlug)?.tasks ?? []).filter(
			(t) => !t.archived && t.currentVersion
		);
	}

	/** Aufgaben des Moduls, die schon eine andere Station dieses CodePaths nutzt */
	function usedElsewhere(node: EditorNode): string[] {
		return nodes
			.filter((n) => n.id !== node.id && n.module && n.module === node.module)
			.flatMap((n) =>
				n.kind === 'TASK'
					? [n.task]
					: n.kind === 'MODULE'
						? n.tasks?.length
							? n.tasks
							: moduleTasks(n.module).map((t) => t.slug)
						: []
			);
	}

	function freeTasks(node: EditorNode): string[] {
		const taken = usedElsewhere(node);
		const all = moduleTasks(node.module).map((t) => t.slug);
		const free = all.filter((slug) => !taken.includes(slug));
		return free.length ? free : all;
	}

	function updateNode(nodeId: string, change: Partial<EditorNode>) {
		nodes = nodes.map((n) => (n.id === nodeId ? normalize({ ...n, ...change }) : n));
	}

	/** Art eines Bausteins festlegen oder wechseln; passende Angaben bleiben erhalten */
	function setVariant(node: EditorNode, v: Variant) {
		error = '';
		// Beschriftungen von Meilenstein/Bonus sind Pflicht und nur dort sinnvoll
		const labelled = (k: EditorKind | Variant) => k === 'MILESTONE' || k === 'BONUS';
		const keepLabel = labelled(node.kind) !== labelled(v) ? '' : (node.label ?? '');
		const base = { label: keepLabel, boss: false, requirement: undefined, path: '' };
		pendingPart = { ...pendingPart, [node.id]: v === 'PART' };
		if (!usesModule(v)) {
			updateNode(node.id, {
				...base,
				kind: v as EditorKind,
				module: '',
				task: '',
				tasks: [],
				path: v === 'PATH' ? (node.path ?? '') : '',
				label: v === 'MILESTONE' && !keepLabel ? 'Geschafft!' : keepLabel
			});
		} else if (v === 'TASK' || v === 'REMEDIAL') {
			updateNode(node.id, {
				...base,
				kind: v,
				tasks: [],
				task: node.kind === 'TASK' || node.kind === 'REMEDIAL' ? node.task : ''
			});
		} else {
			const withModule = { ...node, kind: 'MODULE' as const };
			updateNode(node.id, {
				...base,
				kind: 'MODULE',
				task: '',
				tasks: v === 'PART' && node.module ? freeTasks(withModule) : []
			});
		}
	}

	function setModule(node: EditorNode, slug: string) {
		const change: Partial<EditorNode> = { module: slug, task: '' };
		if (variant(node) === 'PART') change.tasks = slug ? freeTasks({ ...node, module: slug }) : [];
		updateNode(node.id, change);
	}

	function toggleTask(node: EditorNode, slug: string, on: boolean) {
		const order = moduleTasks(node.module).map((t) => t.slug);
		const chosen = (node.tasks ?? []).filter((t) => t !== slug).concat(on ? [slug] : []);
		const tasks = order.filter((t) => chosen.includes(t));
		if (!tasks.length) {
			error = 'Mindestens eine Aufgabe auswählen – oder „Ganzes Modul“.';
			return;
		}
		updateNode(node.id, { tasks });
	}

	/** Die noch nicht genutzten Aufgaben des Moduls als neue Station darunter anhängen (z. B. „Teil 2“) */
	function splitRest(node: EditorNode) {
		const taken = [...usedElsewhere(node), ...(node.tasks ?? [])];
		const rest = moduleTasks(node.module)
			.map((t) => t.slug)
			.filter((slug) => !taken.includes(slug));
		if (!rest.length) {
			error = 'Alle Aufgaben dieses Moduls sind schon verteilt.';
			return;
		}
		const title = moduleTitle(node.module);
		const part = nodes.filter((n) => n.module === node.module && n.kind === 'MODULE').length + 1;
		if (!node.label && node.tasks?.length) updateNode(node.id, { label: `${title} 1` });
		place(
			{ kind: 'MODULE', module: node.module, tasks: rest, label: `${title} ${part}` },
			{ x: node.x, y: node.y + GAP_Y },
			{ after: node.id }
		);
	}

	function removeNode(nodeId: string) {
		nodes = nodes.filter((n) => n.id !== nodeId);
		edges = edges.filter((e) => e.from !== nodeId && e.to !== nodeId);
		if (selectedId === nodeId) selectedId = null;
		if (form.unlockRestAfter === nodeId) form.unlockRestAfter = '';
	}

	function select(nodeId: string | null) {
		selectedId = nodeId;
		selectedEdge = null;
	}

	// ------------------------------------------------------------ Bausteine einsetzen

	/** Was aus der Seitenleiste gezogen oder angeklickt wird */
	type Template = {
		kind: EditorKind;
		module?: string;
		task?: string;
		tasks?: string[];
		label?: string;
		path?: string;
		points?: number;
		min?: number;
		part?: boolean;
	};

	const clampX = (x: number) => Math.min(MAX_COORDINATE - NODE_W, snap(x));
	const clampY = (y: number) => Math.min(MAX_COORDINATE - NODE_H, snap(y));

	/**
	 * Setzt einen Baustein an eine Stelle. after: hinter dieser Station anhängen und verbinden;
	 * between: in diese Verbindung einfügen.
	 */
	function place(
		template: Template,
		at: { x: number; y: number },
		target: { after?: string; between?: Edge } = {}
	) {
		error = '';
		if (template.kind === 'MODULE' && template.module && template.part) {
			template = {
				...template,
				tasks: freeTasks({ id: '', kind: 'MODULE', module: template.module } as EditorNode)
			};
		}
		const node = normalize({
			id: freeId(),
			kind: template.kind,
			module: template.module ?? '',
			task: template.task ?? '',
			tasks: template.tasks ?? [],
			label: template.label ?? '',
			path: template.path ?? '',
			points: template.points,
			min: template.min,
			...freeSpot(clampX(at.x), clampY(at.y))
		} as EditorNode);
		nodes = [...nodes, node];
		if (template.part) pendingPart = { ...pendingPart, [node.id]: true };
		if (target.between) {
			const { from, to } = target.between;
			const old = edges.find((e) => e.from === from && e.to === to)!;
			edges = [
				...edges.filter((e) => e !== old),
				// Beschriftung/Bedingung eines Wegs bleiben am ersten Abschnitt
				normalizeEdge({ ...old, to: node.id }),
				newEdge(node.id, to)
			];
		} else if (target.after) {
			edges = [...edges, newEdge(target.after, node.id)];
		}
		select(node.id);
		reveal(node);
		return node;
	}

	/** Nächster Platz ab (x, y), an dem der Baustein keine andere Station überdeckt */
	function freeSpot(x: number, y: number): { x: number; y: number } {
		const hits = (px: number, py: number) =>
			nodes.some((n) => Math.abs(n.x - px) < NODE_W + 20 && Math.abs(n.y - py) < NODE_H + 20);
		for (let row = 0; row < 20; row++) {
			const py = clampY(y + row * GAP_Y);
			for (let col = 0; col < 8; col++) {
				const px = clampX(x + col * (NODE_W + 40));
				if (!hits(px, py)) return { x: px, y: py };
			}
		}
		return { x, y };
	}

	/** Anklicken in der Seitenleiste: unter die gewählte Station hängen, sonst ans Ende */
	function addFromPalette(template: Template) {
		const anchor = selected;
		if (anchor) {
			place(template, { x: anchor.x, y: anchor.y + GAP_Y }, { after: anchor.id });
		} else {
			const bottom = Math.max(PADDING - GAP_Y, ...nodes.map((n) => n.y));
			place(template, { x: PADDING + 20, y: bottom + GAP_Y });
		}
	}

	let dragTemplate: Template | null = null;
	let dropHint = $state<{ x: number; y: number; node?: string; edge?: string } | null>(null);

	function paletteDragStart(e: DragEvent, template: Template) {
		dragTemplate = template;
		if (e.dataTransfer) {
			e.dataTransfer.effectAllowed = 'copy';
			e.dataTransfer.setData('text/plain', template.kind);
		}
	}

	function paletteDragEnd() {
		dragTemplate = null;
		dropHint = null;
	}

	function dropTarget(e: DragEvent) {
		const el = e.target as Element | null;
		const nodeEl = el?.closest?.('[data-node-id]') as HTMLElement | null;
		const edgeEl = el?.closest?.('[data-edge-key]') as SVGElement | null;
		return {
			node: nodeEl?.dataset.nodeId,
			edge: edgeEl?.getAttribute('data-edge-key') ?? undefined
		};
	}

	function canvasDragOver(e: DragEvent) {
		if (!dragTemplate) return;
		e.preventDefault();
		if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy';
		const p = canvasPoint(e);
		const target = dropTarget(e);
		dropHint = {
			x: clampX(p.x - NODE_W / 2),
			y: clampY(p.y - NODE_H / 2),
			...target
		};
	}

	function canvasDrop(e: DragEvent) {
		if (!dragTemplate) return;
		e.preventDefault();
		const template = dragTemplate;
		const p = canvasPoint(e);
		const target = dropTarget(e);
		dragTemplate = null;
		dropHint = null;
		if (target.node) {
			const anchor = byId.get(target.node);
			if (anchor) {
				const below = { x: anchor.x, y: anchor.y + GAP_Y };
				place(template, below, { after: anchor.id });
				return;
			}
		}
		if (target.edge) {
			const edge = edges.find((x) => edgeKey(x) === target.edge);
			const from = edge && byId.get(edge.from);
			const to = edge && byId.get(edge.to);
			if (edge && from && to) {
				place(template, { x: p.x - NODE_W / 2, y: p.y - NODE_H / 2 }, { between: edge });
				return;
			}
		}
		place(template, { x: p.x - NODE_W / 2, y: p.y - NODE_H / 2 });
	}

	// ------------------------------------------------------------ Verbindungen

	const edgeKey = (e: Edge) => `${e.from}>${e.to}`;

	/** Würde die Verbindung einen Kreis schließen? (Gibt es schon einen Weg von to nach from?) */
	function wouldCycle(from: string, to: string): boolean {
		const stack = [to];
		const seen: string[] = [];
		while (stack.length) {
			const next = stack.pop()!;
			if (next === from) return true;
			if (seen.includes(next)) continue;
			seen.push(next);
			for (const e of edges) if (e.from === next) stack.push(e.to);
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
		edges = [...edges, newEdge(from, to)];
	}

	/** Neue Verbindung: Pflicht bei verbindlichen CodePaths und ab Weichen, dort mit Weg-Beschriftung */
	function newEdge(from: string, to: string): Edge {
		const fromNode = byId.get(from);
		const toNode = byId.get(to);
		return normalizeEdge({
			from,
			to,
			required: form.enforce || fromNode?.kind === 'CHOICE',
			label: fromNode?.kind === 'CHOICE' && toNode ? label(toNode) : undefined
		});
	}

	function updateEdge(key: string, change: Partial<Edge>) {
		edges = edges.map((e) => (edgeKey(e) === key ? normalizeEdge({ ...e, ...change }) : e));
	}

	/** Bedingung einer Verbindung setzen; Vorgabe für die Bezugsaufgabe ist die Aufgabe der Station */
	function setCondition(key: string, rule: string, from: EditorNode | undefined) {
		const task = from?.task || from?.tasks?.[0] || '';
		const conditions: Record<string, Partial<EdgeCondition> | undefined> = {
			NONE: undefined,
			DEFAULT: { rule: 'DEFAULT' },
			PERFECT_FIRST_TRY: { rule: 'PERFECT_FIRST_TRY', task },
			STRUGGLED: { rule: 'STRUGGLED', task, failedAttemptsThreshold: 3, hintsThreshold: 2 },
			SCORE_AT_LEAST: { rule: 'SCORE_AT_LEAST', task, scoreThreshold: 80 }
		};
		updateEdge(key, { condition: conditions[rule] as EdgeCondition | undefined });
	}

	const BRANCH_PALETTE = [
		'border-sky-400 bg-sky-50/70',
		'border-violet-400 bg-violet-50/70',
		'border-teal-400 bg-teal-50/70',
		'border-amber-400 bg-amber-50/70'
	];

	/** Zweige hinter Weichen farbig absetzen (bis zum Zusammenführungspunkt) */
	const branchStyles = $derived.by(() => {
		const map: Record<string, string> = {};
		let colorIdx = 0;
		for (const n of nodes) {
			if (n.kind !== 'CHOICE') continue;
			for (const [, exclusiveIds] of choiceBranches(edges, n.id)) {
				const style = BRANCH_PALETTE[colorIdx++ % BRANCH_PALETTE.length];
				for (const nodeId of exclusiveIds) map[nodeId] = style;
			}
		}
		return map;
	});

	function removeEdge(key: string) {
		edges = edges.filter((e) => edgeKey(e) !== key);
		if (selectedEdge === key) selectedEdge = null;
	}

	// ------------------------------------------------------------ Fläche, Zoom, Ziehen

	let viewport = $state<HTMLDivElement>();
	let surface = $state<HTMLDivElement>();
	let viewW = $state(800);
	let viewH = $state(500);
	let scale = $state(1);

	const size = $derived(canvasSize(nodes, viewW / scale, viewH / scale));

	function canvasPoint(e: { clientX: number; clientY: number }) {
		const rect = surface!.getBoundingClientRect();
		return { x: (e.clientX - rect.left) / scale, y: (e.clientY - rect.top) / scale };
	}

	function zoom(factor: number) {
		scale = Math.min(1.5, Math.max(0.4, Math.round(scale * factor * 100) / 100));
	}

	function fitView() {
		if (!nodes.length) return (scale = 1);
		const w = Math.max(...nodes.map((n) => n.x + NODE_W)) + PADDING;
		const h = Math.max(...nodes.map((n) => n.y + NODE_H)) + PADDING;
		scale = Math.max(0.4, Math.min(1, viewW / w, viewH / h));
		viewport?.scrollTo({ left: 0, top: 0 });
	}

	/** Station in den sichtbaren Bereich scrollen – nur so weit wie nötig */
	async function reveal(node: EditorNode) {
		await tick();
		if (!viewport) return;
		const margin = 24;
		const { scrollLeft, scrollTop, clientWidth, clientHeight } = viewport;
		const left = node.x * scale - margin;
		const top = node.y * scale - margin;
		const right = (node.x + NODE_W) * scale + margin;
		const bottom = (node.y + NODE_H) * scale + margin;
		const x =
			left < scrollLeft
				? left
				: right > scrollLeft + clientWidth
					? right - clientWidth
					: scrollLeft;
		const y =
			top < scrollTop ? top : bottom > scrollTop + clientHeight ? bottom - clientHeight : scrollTop;
		if (x !== scrollLeft || y !== scrollTop) {
			viewport.scrollTo({ left: Math.max(0, x), top: Math.max(0, y), behavior: 'smooth' });
		}
	}

	let drag: {
		id: string;
		startX: number;
		startY: number;
		x: number;
		y: number;
		moved: boolean;
	} | null = null;
	/** Verbindung, die gerade vom Anschlusspunkt aus gezogen wird */
	let link = $state<{ from: string; x: number; y: number; over?: string } | null>(null);

	function pointerDown(e: PointerEvent, node: EditorNode) {
		if (e.button !== 0) return;
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

	function portDown(e: PointerEvent, node: EditorNode) {
		if (e.button !== 0) return;
		e.stopPropagation();
		const p = canvasPoint(e);
		link = { from: node.id, ...p };
		(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
	}

	function nodeAt(e: PointerEvent): string | undefined {
		const el = document.elementFromPoint(e.clientX, e.clientY);
		return (el?.closest('[data-node-id]') as HTMLElement | null)?.dataset.nodeId;
	}

	function pointerMove(e: PointerEvent) {
		if (link) {
			const over = nodeAt(e);
			link = { ...link, ...canvasPoint(e), over: over !== link.from ? over : undefined };
			return;
		}
		if (!drag) return;
		const dx = (e.clientX - drag.startX) / scale;
		const dy = (e.clientY - drag.startY) / scale;
		if (!drag.moved && Math.abs(dx) + Math.abs(dy) < 4) return;
		drag.moved = true;
		const target = drag;
		nodes = nodes.map((n) =>
			n.id === target.id ? { ...n, x: clampX(target.x + dx), y: clampY(target.y + dy) } : n
		);
	}

	function pointerUp(e: PointerEvent) {
		if (link) {
			const target = nodeAt(e);
			if (target && target !== link.from) connect(link.from, target);
			link = null;
		}
		drag = null;
	}

	function clickNode(node: EditorNode) {
		select(node.id);
	}

	function keyMove(e: KeyboardEvent, node: EditorNode) {
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
				n.id === node.id ? { ...n, x: clampX(n.x + dx), y: clampY(n.y + dy) } : n
			);
		} else if (e.key === 'Delete') {
			removeNode(node.id);
		} else if (e.key === 'Escape') {
			select(null);
		}
	}

	function arrange() {
		const positions = layered(nodes, edges);
		nodes = nodes.map((n) => ({ ...n, ...(positions.get(n.id) ?? { x: n.x, y: n.y }) }));
	}

	function linkPath(from: EditorNode, x: number, y: number): string {
		const a = { x: from.x + NODE_W / 2, y: from.y + NODE_H };
		const bend = Math.max(30, Math.abs(y - a.y) / 2);
		return `M ${a.x} ${a.y} C ${a.x} ${a.y + bend}, ${x} ${y - bend}, ${x} ${y}`;
	}

	// ------------------------------------------------------------ Bildschirmhöhe

	let shell = $state<HTMLDivElement>();
	let shellHeight = $state(640);

	/** Der Editor füllt den Bildschirm ab seiner Oberkante – gescrollt wird nur innen */
	function fitHeight() {
		if (!shell) return;
		const top = shell.getBoundingClientRect().top + window.scrollY;
		shellHeight = Math.max(520, window.innerHeight - top - 16);
	}

	// ------------------------------------------------------------ Seitenleiste

	let search = $state('');
	let openModule = $state<string | null>(null);

	const filteredCatalog = $derived(
		catalog.filter((m) => {
			const q = search.trim().toLowerCase();
			if (!q) return true;
			return (
				m.module.title.toLowerCase().includes(q) ||
				m.module.slug.includes(q) ||
				m.tasks.some((t) => !t.archived && t.title.toLowerCase().includes(q))
			);
		})
	);

	/** Wie oft ein Modul im Pfad vorkommt – zur Orientierung in der Liste */
	function usage(slug: string): number {
		return nodes.filter((n) => n.module === slug).length;
	}

	type Block = { template: Template; label: string; hint: string; icon: IconName };
	const BLOCKS: Block[] = [
		{
			template: { kind: 'BLOCK' },
			label: 'Leerer Block',
			hint: 'Art erst danach festlegen',
			icon: 'block'
		},
		{ template: { kind: 'MODULE' }, label: 'Ganzes Modul', hint: 'Alle Aufgaben', icon: 'module' },
		{
			template: { kind: 'MODULE', part: true },
			label: 'Teil eines Moduls',
			hint: 'Ausgewählte Aufgaben',
			icon: 'module-part'
		},
		{ template: { kind: 'TASK' }, label: 'Einzelne Aufgabe', hint: 'Genau eine', icon: 'task' },
		{
			template: { kind: 'MILESTONE', label: 'Geschafft!' },
			label: 'Meilenstein',
			hint: 'Zwischenziel',
			icon: 'milestone'
		}
	];
	/** Steuerung des Weges – in der Seitenleiste eingeklappt, damit sie kurz bleibt */
	const FLOW_BLOCKS: Block[] = [
		{
			template: { kind: 'CHOICE', label: 'Wähle deinen Weg' },
			label: 'Weiche',
			hint: 'Entweder-oder',
			icon: 'split'
		},
		{ template: { kind: 'AND' }, label: 'UND', hint: 'Alle Wege nötig', icon: 'gate' },
		{ template: { kind: 'OR', min: 1 }, label: 'ODER', hint: 'Mindestzahl Wege', icon: 'gate' },
		{
			template: { kind: 'BONUS', label: 'Bonus', points: 10 },
			label: 'Bonus',
			hint: 'Extrapunkte',
			icon: 'star'
		},
		{
			template: { kind: 'REMEDIAL' },
			label: 'Stütz-Knoten',
			hint: 'Hilfe bei Problemen',
			icon: 'lifebuoy'
		},
		{
			template: { kind: 'PATH' },
			label: 'CodePath-Baustein',
			hint: 'Anderen CodePath einbinden',
			icon: 'puzzle'
		}
	];
	let showFlow = $state(true);

	const CONDITION_LABELS: Record<string, string> = {
		DEFAULT: 'Normalweg',
		PERFECT_FIRST_TRY: '1. Versuch 100 %',
		STRUGGLED: 'Schwierigkeiten',
		SCORE_AT_LEAST: 'Mindestpunkte'
	};
</script>

<svelte:window
	onbeforeunload={(e) => {
		if (dirty) e.preventDefault();
	}}
	onresize={fitHeight}
/>

<svelte:head><title>CodePath bearbeiten | CodePath</title></svelte:head>

<div class="flex flex-col gap-3">
	<!-- Kopfzeile: alles Wichtige in einer Zeile -->
	<header class="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-slate-200 pb-3">
		<a
			href="/admin/codepaths"
			class="font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase hover:text-institutional-navy"
			>← CodePaths</a
		>
		<h1
			class="min-w-0 truncate font-sans text-xl font-extrabold tracking-tight text-institutional-navy uppercase"
		>
			{form.title || 'CodePath'}
		</h1>
		{#if saved}
			<span
				class="border px-2 py-0.5 font-mono text-[10px] font-bold tracking-widest uppercase {form.published
					? 'border-emerald-300 bg-emerald-50 text-emerald-800'
					: 'border-slate-300 text-slate-500'}"
				>{form.published ? 'Veröffentlicht' : 'Entwurf'}</span
			>
			<span class="font-mono text-[10px] tracking-widest text-slate-400 uppercase"
				>{courseName ? `Kurs ${courseName}` : 'Alle Kurse'} · {nodes.length} Stationen</span
			>
			<span class="flex-1"></span>
			{#if message}
				<span class="text-[11px] font-bold tracking-widest text-teal-700 uppercase" role="status"
					>{message}</span
				>
			{:else if dirty}
				<span class="text-[11px] font-bold tracking-widest text-amber-700 uppercase"
					>Ungespeichert</span
				>
			{/if}
			<button
				class="ed-button flex items-center gap-1.5"
				aria-pressed={!!preview}
				onclick={() => (preview ? (preview = null) : showPreview())}
				><PathIcon name="eye" size={14} />{preview ? 'Vorschau schließen' : 'Vorschau'}</button
			>
			<button
				class="ed-button flex items-center gap-1.5 {!selected && !selectedEdge
					? 'border-institutional-navy'
					: ''}"
				onclick={() => select(null)}><PathIcon name="settings" size={14} />Einstellungen</button
			>
			<button
				class="ed-button bg-institutional-navy text-white"
				onclick={save}
				disabled={busy || !dirty}>{dirty ? 'Speichern' : 'Gespeichert'}</button
			>
		{/if}
	</header>

	{#if !saved && error}
		<div class="border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
			{error}
		</div>
	{/if}

	{#if saved}
		<div
			bind:this={shell}
			class="relative grid grid-cols-1 gap-3 lg:h-(--shell-h) lg:grid-cols-[15rem_minmax(0,1fr)_19rem]"
			style="--shell-h: {shellHeight}px"
		>
			<!-- Bausteine -->
			<aside class="flex min-h-0 flex-col border border-slate-200 bg-white" aria-label="Bausteine">
				<div class="border-b border-slate-100 p-3">
					<h2 class="ed-label">Bausteine</h2>
					<ul class="mt-2 flex flex-col gap-1.5">
						{#each BLOCKS as block (block.label)}
							<li>
								<button
									type="button"
									draggable="true"
									class="group flex w-full cursor-grab items-center gap-2.5 border border-slate-200 bg-slate-50 px-2.5 py-2 text-left hover:border-institutional-navy hover:bg-white active:cursor-grabbing {block
										.template.kind === 'BLOCK'
										? 'border-dashed'
										: ''}"
									title="Auf die Fläche ziehen oder anklicken"
									ondragstart={(e) => paletteDragStart(e, block.template)}
									ondragend={paletteDragEnd}
									onclick={() => addFromPalette(block.template)}
								>
									<PathIcon name={block.icon} size={18} class="text-institutional-navy" />
									<span class="flex min-w-0 flex-1 flex-col">
										<span class="text-[13px] leading-tight font-bold text-institutional-navy"
											>{block.label}</span
										>
										<span class="text-[11px] leading-tight text-slate-500">{block.hint}</span>
									</span>
									<PathIcon
										name="grip"
										size={14}
										class="text-slate-300 group-hover:text-slate-500"
									/>
								</button>
							</li>
						{/each}
					</ul>
					<button
						type="button"
						class="mt-3 flex w-full items-center gap-1 text-left"
						aria-expanded={showFlow}
						onclick={() => (showFlow = !showFlow)}
						><PathIcon
							name="chevron"
							size={12}
							class="text-slate-400 transition-transform {showFlow ? 'rotate-90' : ''}"
						/><span class="ed-label">Steuerung</span></button
					>
					{#if showFlow}
						<ul class="mt-1.5 grid grid-cols-2 gap-1.5">
							{#each FLOW_BLOCKS as block (block.label)}
								<li>
									<button
										type="button"
										draggable="true"
										class="flex w-full cursor-grab items-center gap-1.5 border border-slate-200 bg-slate-50 px-2 py-1.5 text-left hover:border-institutional-navy hover:bg-white active:cursor-grabbing"
										title="{block.label}: {block.hint} – ziehen oder anklicken"
										ondragstart={(e) => paletteDragStart(e, block.template)}
										ondragend={paletteDragEnd}
										onclick={() => addFromPalette(block.template)}
									>
										<PathIcon name={block.icon} size={15} class="text-institutional-navy" />
										<span
											class="truncate text-[12px] leading-tight font-bold text-institutional-navy"
											>{block.label}</span
										>
									</button>
								</li>
							{/each}
						</ul>
					{/if}
				</div>
				<div class="flex min-h-0 flex-1 flex-col p-3">
					<h2 class="ed-label">Module</h2>
					<label class="relative mt-2 block">
						<span class="sr-only">Module und Aufgaben durchsuchen</span>
						<PathIcon
							name="search"
							size={14}
							class="pointer-events-none absolute top-1/2 left-2 -translate-y-1/2 text-slate-400"
						/>
						<input
							class="w-full border border-slate-200 py-1.5 pr-2 pl-7 text-sm"
							placeholder="Suchen …"
							bind:value={search}
						/>
					</label>
					<ul class="mt-2 -mr-1 flex min-h-0 flex-1 flex-col gap-0.5 overflow-y-auto pr-1">
						{#each filteredCatalog as m (m.module.slug)}
							{@const isOpen = openModule === m.module.slug || !!search.trim()}
							{@const count = usage(m.module.slug)}
							<li>
								<div class="flex items-stretch">
									<button
										type="button"
										class="px-1 text-slate-400 hover:text-institutional-navy"
										aria-expanded={isOpen}
										aria-label="Aufgaben von {m.module.title} zeigen"
										onclick={() => (openModule = isOpen ? null : m.module.slug)}
										><PathIcon
											name="chevron"
											size={14}
											class="transition-transform {isOpen ? 'rotate-90' : ''}"
										/></button
									>
									<button
										type="button"
										draggable="true"
										class="flex min-w-0 flex-1 cursor-grab items-center gap-2 px-1.5 py-1.5 text-left text-[13px] hover:bg-slate-50 active:cursor-grabbing"
										title="Ganzes Modul – ziehen oder anklicken"
										ondragstart={(e) =>
											paletteDragStart(e, { kind: 'MODULE', module: m.module.slug })}
										ondragend={paletteDragEnd}
										onclick={() => addFromPalette({ kind: 'MODULE', module: m.module.slug })}
									>
										<PathIcon
											emoji={m.module.icon}
											title={m.module.title}
											size={16}
											class="text-institutional-navy"
										/>
										<span class="min-w-0 flex-1 truncate font-semibold text-institutional-navy"
											>{m.module.title}</span
										>
										{#if count}<span
												class="font-mono text-[10px] text-slate-400"
												title="{count}× im CodePath">{count}×</span
											>{/if}
									</button>
								</div>
								{#if isOpen}
									<ul class="mb-1 ml-5 border-l border-slate-100 pl-1">
										{#each moduleTasks(m.module.slug).filter((t) => !search.trim() || t.title
													.toLowerCase()
													.includes(search.trim().toLowerCase()) || m.module.title
													.toLowerCase()
													.includes(search.trim().toLowerCase())) as t (t.slug)}
											<li>
												<button
													type="button"
													draggable="true"
													class="flex w-full cursor-grab items-center gap-2 px-1.5 py-1 text-left text-xs text-slate-600 hover:bg-slate-50 hover:text-institutional-navy active:cursor-grabbing"
													title="Einzelne Aufgabe – ziehen oder anklicken"
													ondragstart={(e) =>
														paletteDragStart(e, {
															kind: 'TASK',
															module: m.module.slug,
															task: t.slug
														})}
													ondragend={paletteDragEnd}
													onclick={() =>
														addFromPalette({ kind: 'TASK', module: m.module.slug, task: t.slug })}
												>
													<PathIcon name="task" size={13} class="text-slate-400" />
													<span class="truncate">{t.title}</span>
												</button>
											</li>
										{:else}
											<li class="px-1.5 py-1 text-xs text-slate-400">Keine Aufgaben</li>
										{/each}
									</ul>
								{/if}
							</li>
						{:else}
							<li class="px-1.5 py-2 text-xs text-slate-400">Nichts gefunden.</li>
						{/each}
					</ul>
				</div>
			</aside>

			<!-- Zeichenfläche -->
			<section class="relative flex min-h-[28rem] min-w-0 flex-col border border-slate-200">
				<div
					class="flex flex-wrap items-center gap-1 border-b border-slate-200 bg-white px-2 py-1.5"
				>
					<button
						class="tool"
						onclick={() => zoom(1 / 1.2)}
						aria-label="Verkleinern"
						title="Verkleinern"><PathIcon name="minus" size={15} /></button
					>
					<button
						class="tool w-14 font-mono text-[11px]"
						onclick={() => (scale = 1)}
						title="Auf 100 % zurück">{Math.round(scale * 100)} %</button
					>
					<button class="tool" onclick={() => zoom(1.2)} aria-label="Vergrößern" title="Vergrößern"
						><PathIcon name="plus" size={15} /></button
					>
					<button class="tool" onclick={fitView} aria-label="Alles zeigen" title="Alles zeigen"
						><PathIcon name="fit" size={15} /></button
					>
					<span class="mx-1 h-5 w-px bg-slate-200"></span>
					<button class="tool gap-1.5 px-2 text-[11px] font-bold uppercase" onclick={arrange}
						><PathIcon name="arrange" size={15} />Anordnen</button
					>
					<span class="ml-2 hidden truncate text-xs text-slate-500 md:inline">
						{#if dropHint?.node}
							Loslassen: wird unter „{label(byId.get(dropHint.node)!)}“ angehängt
						{:else if dropHint?.edge}
							Loslassen: wird in diese Verbindung eingefügt
						{:else if link}
							Auf die Station ziehen, die danach kommt
						{:else}
							Bausteine hineinziehen · am Punkt unten ziehen, um zu verbinden
						{/if}
					</span>
					{#if incompleteCount}
						<span
							class="ml-auto flex items-center gap-1 text-[11px] font-bold text-amber-700"
							title="Stationen, deren Art oder Inhalt noch fehlt"
							><PathIcon name="warning" size={14} />{incompleteCount} offen</span
						>
					{/if}
				</div>

				<div
					bind:this={viewport}
					bind:clientWidth={viewW}
					bind:clientHeight={viewH}
					class="min-h-0 flex-1 overflow-auto"
					style="background-color: #f8fafc; background-image: radial-gradient(#cbd5e1 1px, transparent 1px); background-size: {20 *
						scale}px {20 * scale}px"
				>
					<div style="width: {size.width * scale}px; height: {size.height * scale}px">
						<!-- Klick auf die leere Fläche hebt nur die Auswahl auf; per Tastatur geht das mit Esc -->
						<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
						<div
							bind:this={surface}
							class="relative origin-top-left"
							style="width: {size.width}px; height: {size.height}px; transform: scale({scale})"
							role="application"
							aria-label="CodePath-Fläche"
							onpointermove={pointerMove}
							onpointerup={pointerUp}
							ondragover={canvasDragOver}
							ondragleave={(e) => {
								if (e.currentTarget === e.target) dropHint = null;
							}}
							ondrop={canvasDrop}
							onclick={(e) => {
								if (e.target === e.currentTarget || (e.target as Element).tagName === 'svg')
									select(null);
							}}
							onkeydown={() => {}}
						>
							<svg
								class="pointer-events-none absolute inset-0"
								width={size.width}
								height={size.height}
							>
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
										{@const gateTarget = to.kind === 'AND' || to.kind === 'OR'}
										{@const slot = gateTarget
											? incomingSlot(edges, byId as Map<string, PathNode>, edge)
											: { index: 0, count: 1 }}
										{@const d = edgePath(from, to, slot.index, slot.count)}
										{@const key = edgeKey(edge)}
										<path
											{d}
											fill="none"
											stroke-dasharray={edge.required ? undefined : '6 5'}
											stroke-width={dropHint?.edge === key ? 4 : 2.5}
											class={selectedEdge === key
												? 'stroke-rose-500'
												: dropHint?.edge === key
													? 'stroke-institutional-gold'
													: 'stroke-slate-400'}
											marker-end="url(#edit-arrow)"
										/>
										<!-- Breite, unsichtbare Linie zum Anklicken und als Ziel beim Hineinziehen -->
										<path
											{d}
											fill="none"
											stroke="transparent"
											stroke-width="18"
											class="cursor-pointer"
											style="pointer-events: stroke"
											data-edge-key={key}
											role="button"
											tabindex="-1"
											aria-label="Verbindung {label(from)} nach {label(to)}"
											onclick={() => {
												selectedEdge = key;
												selectedId = null;
											}}
											onkeydown={() => {}}
										/>
									{/if}
								{/each}
								{#if link}
									{@const from = byId.get(link.from)}
									{#if from}
										<path
											d={linkPath(from, link.x, link.y)}
											fill="none"
											stroke-width="2.5"
											stroke-dasharray="6 5"
											class="stroke-institutional-gold"
										/>
									{/if}
								{/if}
							</svg>

							<!-- Beschriftung von Weichen-Wegen und Bedingungen an der Linie -->
							{#each edges as edge (edgeKey(edge))}
								{@const from = byId.get(edge.from)}
								{@const to = byId.get(edge.to)}
								{@const text = edge.condition?.rule
									? CONDITION_LABELS[edge.condition.rule]
									: from?.kind === 'CHOICE'
										? edge.label
										: ''}
								{#if from && to && text}
									<button
										type="button"
										class="absolute max-w-36 -translate-x-1/2 -translate-y-1/2 truncate border px-1.5 py-0.5 font-mono text-[9px] font-bold uppercase {edge
											.condition?.rule
											? 'border-sky-300 bg-sky-50 text-sky-800'
											: 'border-slate-300 bg-white text-slate-600'}"
										style="left: {(from.x + to.x) / 2 + NODE_W / 2}px; top: {(from.y +
											NODE_H +
											to.y) /
											2}px"
										onclick={() => {
											selectedEdge = edgeKey(edge);
											selectedId = null;
										}}>{text}</button
									>
								{/if}
							{/each}

							{#if dropHint && !dropHint.node && !dropHint.edge}
								<div
									class="pointer-events-none absolute border-2 border-dashed border-institutional-navy/40 bg-white/50"
									style="left: {dropHint.x}px; top: {dropHint.y}px; width: {NODE_W}px; height: {NODE_H}px"
								></div>
							{/if}

							{#each nodes as node (node.id)}
								{@const open = incomplete(node)}
								{@const icon = nodeIcon(node)}
								{@const isGate = node.kind === 'AND' || node.kind === 'OR'}
								<div
									data-node-id={node.id}
									class="group absolute flex cursor-grab touch-none flex-col justify-between border-2 bg-white p-3 select-none active:cursor-grabbing {node.kind ===
									'MILESTONE'
										? 'rounded-full border-institutional-gold text-center'
										: open || missing(node)
											? 'border-dashed border-amber-400 bg-amber-50/60'
											: node.kind === 'CHOICE'
												? 'border-institutional-navy bg-amber-50/70'
												: isGate
													? 'border-institutional-navy bg-slate-50'
													: node.kind === 'PATH'
														? (branchStyles[node.id] ?? 'border-violet-400 bg-violet-50/50')
														: node.kind === 'BONUS'
															? (branchStyles[node.id] ?? 'border-amber-400 bg-amber-50/80')
															: (branchStyles[node.id] ?? 'border-slate-300')} {selectedId ===
									node.id
										? 'ring-4 ring-institutional-navy/30'
										: ''} {dropHint?.node === node.id || link?.over === node.id
										? 'ring-4 ring-institutional-gold/60'
										: ''}"
									style="left: {node.x}px; top: {node.y}px; width: {NODE_W}px; height: {NODE_H}px"
									role="button"
									tabindex="0"
									aria-label="{variantLabel(node)}: {label(node)}"
									aria-pressed={selectedId === node.id}
									onpointerdown={(e) => pointerDown(e, node)}
									onclick={() => clickNode(node)}
									onkeydown={(e) => keyMove(e, node)}
								>
									<div
										class="flex items-start gap-2 {node.kind === 'MILESTONE'
											? 'justify-center'
											: ''}"
									>
										{#if isGate}
											<span
												class="flex h-6 min-w-6 shrink-0 items-center justify-center border-2 border-institutional-navy bg-white px-1 font-mono text-[11px] font-black text-institutional-navy"
												>{node.kind === 'AND' ? '&' : `≥${node.min ?? 1}`}</span
											>
										{:else}
											<PathIcon
												name={icon.name}
												emoji={icon.emoji}
												title={label(node)}
												size={18}
												class={open || missing(node)
													? 'text-amber-600'
													: node.kind === 'MILESTONE' || node.boss
														? 'text-institutional-gold'
														: 'text-institutional-navy'}
											/>
										{/if}
										<span
											class="line-clamp-2 text-sm leading-tight font-bold {open
												? 'text-slate-500 italic'
												: 'text-institutional-navy'}">{label(node)}</span
										>
									</div>
									<span
										class="font-mono text-[9px] font-bold tracking-widest uppercase {open
											? 'text-amber-700'
											: 'text-slate-400'}"
										>{open
											? `${variantLabel(node)} · einrichten`
											: node.kind === 'BONUS'
												? `Bonus · +${node.points ?? 10} P.`
												: node.kind === 'PATH' && pathsBySlug.get(node.path ?? '')
													? `Baustein · ${pathsBySlug.get(node.path ?? '')!.stations} Stationen`
													: node.requirement
														? `${variantLabel(node)} · ab ${node.requirement.minPercent ?? 80} %`
														: node.boss
															? `${variantLabel(node)} · Boss`
															: variantLabel(node)}</span
									>
									<!-- Anschlusspunkt: von hier zur nächsten Station ziehen -->
									<span
										class="absolute -bottom-2 left-1/2 h-4 w-4 -translate-x-1/2 cursor-crosshair rounded-full border-2 border-institutional-navy bg-white opacity-0 transition-opacity group-hover:opacity-100 {selectedId ===
											node.id || link
											? 'opacity-100'
											: ''}"
										role="presentation"
										title="Ziehen, um zu verbinden"
										onpointerdown={(e) => portDown(e, node)}
									></span>
								</div>
							{/each}

							{#if !nodes.length}
								<div
									class="pointer-events-none absolute inset-0 flex items-center justify-center p-8 text-center"
								>
									<div class="max-w-sm border-2 border-dashed border-slate-300 bg-white/80 p-8">
										<PathIcon name="block" size={32} class="mx-auto text-slate-400" />
										<p class="mt-3 font-bold text-institutional-navy">
											Bausteine von links hierher ziehen
										</p>
										<p class="mt-1 text-sm text-slate-500">
											Lässt du einen Baustein auf einer Station los, wird er darunter angehängt und
											verbunden.
										</p>
									</div>
								</div>
							{/if}
						</div>
					</div>
				</div>

				{#if error}
					<div
						class="absolute top-14 left-1/2 z-10 flex max-w-[90%] -translate-x-1/2 items-start gap-3 border border-rose-200 bg-rose-50 px-4 py-2.5 text-sm text-rose-700 shadow-lg"
						role="alert"
					>
						<span>{error}</span>
						<button aria-label="Meldung schließen" onclick={() => (error = '')}
							><PathIcon name="close" size={14} /></button
						>
					</div>
				{/if}
			</section>

			<!-- Eigenschaften -->
			<aside
				class="flex min-h-0 flex-col overflow-y-auto border border-slate-200 bg-white"
				aria-label="Eigenschaften"
			>
				{#if selectedEdge}
					{@const edge = edges.find((e) => edgeKey(e) === selectedEdge)}
					{#if edge}
						{@const key = selectedEdge}
						{@const fromNode = byId.get(edge.from)}
						<section class="flex flex-col gap-3 p-4" aria-label="Verbindung bearbeiten">
							<div class="flex items-center justify-between gap-2">
								<h2 class="ed-label">Verbindung</h2>
								<button
									class="text-slate-400 hover:text-institutional-navy"
									aria-label="Auswahl aufheben"
									onclick={() => (selectedEdge = null)}><PathIcon name="close" size={16} /></button
								>
							</div>
							<p class="text-sm">
								{label(fromNode!)} → {label(byId.get(edge.to)!)}
							</p>
							{#if fromNode?.kind === 'CHOICE'}
								<label class="ed-label"
									>Beschriftung des Wegs (Pflicht)
									<input
										class="ed-input"
										maxlength="80"
										value={edge.label ?? ''}
										oninput={(e) =>
											updateEdge(key, { label: (e.currentTarget as HTMLInputElement).value })}
									/>
								</label>
								<label class="ed-label"
									>Kurzbeschreibung (optional)
									<input
										class="ed-input"
										maxlength="200"
										placeholder="z. B. schwieriger – dafür +50 Punkte"
										value={edge.hint ?? ''}
										oninput={(e) =>
											updateEdge(key, { hint: (e.currentTarget as HTMLInputElement).value })}
									/>
								</label>
							{/if}
							<fieldset class="flex flex-col gap-1 text-sm">
								<legend class="ed-label mb-1">Art der Verbindung</legend>
								<label class="flex items-center gap-2"
									><input
										type="radio"
										name="edge-required"
										checked={!!edge.required}
										onchange={() => updateEdge(key, { required: true })}
									/> Pflicht (durchgezogen)</label
								>
								<label class="flex items-center gap-2"
									><input
										type="radio"
										name="edge-required"
										checked={!edge.required}
										onchange={() => updateEdge(key, { required: false })}
									/> Empfehlung (gestrichelt)</label
								>
							</fieldset>
							<fieldset class="flex flex-col gap-2 border-t border-slate-100 pt-3 text-sm">
								<legend class="ed-label mb-1">Bedingung</legend>
								<select
									class="ed-input"
									value={edge.condition?.rule ?? 'NONE'}
									onchange={(e) =>
										setCondition(key, (e.currentTarget as HTMLSelectElement).value, fromNode)}
								>
									<option value="NONE">Keine Bedingung (immer aktiv)</option>
									<option value="DEFAULT">Normalweg</option>
									<option value="PERFECT_FIRST_TRY">100 % im 1. Versuch (Fast-Track)</option>
									<option value="STRUGGLED">Schwierigkeiten (zum Stütz-Knoten)</option>
									<option value="SCORE_AT_LEAST">Mindest-Punktzahl</option>
								</select>
								{#if edge.condition && edge.condition.rule !== 'DEFAULT'}
									{@const condition = edge.condition}
									{@const candidates =
										fromNode?.kind === 'TASK' || fromNode?.kind === 'REMEDIAL'
											? moduleTasks(fromNode.module).filter((t) => t.slug === fromNode.task)
											: fromNode?.kind === 'MODULE'
												? moduleTasks(fromNode.module).filter(
														(t) => !fromNode.tasks?.length || fromNode.tasks.includes(t.slug)
													)
												: []}
									<label class="ed-label"
										>Bezugsaufgabe
										{#if candidates.length}
											<select
												class="ed-input"
												value={condition.task ?? ''}
												onchange={(e) =>
													updateEdge(key, {
														condition: {
															...condition,
															task: (e.currentTarget as HTMLSelectElement).value
														}
													})}
											>
												{#each candidates as t (t.slug)}
													<option value={t.slug}>{t.title}</option>
												{/each}
											</select>
										{:else}
											<input
												class="ed-input"
												placeholder="z. B. modul/aufgabe"
												value={condition.task ?? ''}
												oninput={(e) =>
													updateEdge(key, {
														condition: {
															...condition,
															task: (e.currentTarget as HTMLInputElement).value
														}
													})}
											/>
										{/if}
									</label>
									{#if condition.rule === 'STRUGGLED'}
										<div class="grid grid-cols-2 gap-2">
											<label class="ed-label"
												>Fehlversuche
												<input
													type="number"
													min="1"
													max="20"
													class="ed-input"
													value={condition.failedAttemptsThreshold ?? 3}
													oninput={(e) =>
														updateEdge(key, {
															condition: {
																...condition,
																failedAttemptsThreshold:
																	Number((e.currentTarget as HTMLInputElement).value) || 3
															}
														})}
												/>
											</label>
											<label class="ed-label"
												>Tipps genutzt
												<input
													type="number"
													min="1"
													max="20"
													class="ed-input"
													value={condition.hintsThreshold ?? 2}
													oninput={(e) =>
														updateEdge(key, {
															condition: {
																...condition,
																hintsThreshold:
																	Number((e.currentTarget as HTMLInputElement).value) || 2
															}
														})}
												/>
											</label>
										</div>
									{:else if condition.rule === 'SCORE_AT_LEAST'}
										<label class="ed-label"
											>Mindestpunktzahl (%)
											<input
												type="number"
												min="0"
												max="100"
												class="ed-input"
												value={condition.scoreThreshold ?? 80}
												oninput={(e) =>
													updateEdge(key, {
														condition: {
															...condition,
															scoreThreshold:
																Number((e.currentTarget as HTMLInputElement).value) || 0
														}
													})}
											/>
										</label>
									{/if}
								{/if}
								<p class="text-xs text-slate-500">
									Hat eine Station bedingte Wege, braucht sie genau einen Normalweg.
								</p>
							</fieldset>
							<button class="ed-button text-rose-700" onclick={() => removeEdge(key)}
								>Verbindung löschen</button
							>
						</section>
					{/if}
				{:else if selected}
					{@const node = selected}
					{@const v = variant(node)}
					{@const incomingCount = edges.filter((e) => e.to === node.id).length}
					<section class="flex flex-col gap-4 p-4" aria-label="Station bearbeiten">
						<div class="flex items-center justify-between gap-2">
							<h2 class="ed-label">Station</h2>
							<button
								class="text-slate-400 hover:text-institutional-navy"
								aria-label="Auswahl aufheben"
								onclick={() => select(null)}><PathIcon name="close" size={16} /></button
							>
						</div>

						<fieldset class="flex flex-col gap-1.5">
							<legend class="ed-label mb-1.5">Art</legend>
							{#each [{ title: 'Inhalt', list: CONTENT_VARIANTS }, { title: 'Steuerung', list: FLOW_VARIANTS }] as group (group.title)}
								<span
									class="mt-1 font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase"
									>{group.title}</span
								>
								<div class="grid grid-cols-2 gap-1.5">
									{#each group.list as option (option.value)}
										<button
											type="button"
											class="flex items-center gap-1.5 border px-2 py-1.5 text-left {v ===
											option.value
												? 'border-institutional-navy bg-institutional-navy text-white'
												: 'border-slate-200 text-institutional-navy hover:border-institutional-navy'}"
											aria-pressed={v === option.value}
											title={option.hint}
											onclick={() => setVariant(node, option.value)}
										>
											<PathIcon name={option.icon} size={15} />
											<span class="text-[12px] leading-tight font-bold">{option.label}</span>
										</button>
									{/each}
								</div>
							{/each}
							{#if !v}
								<p class="text-xs text-amber-700">Wähle, wofür dieser Block steht.</p>
							{:else}
								<p class="text-xs text-slate-500">
									{VARIANTS.find((o) => o.value === v)?.hint}
								</p>
							{/if}
						</fieldset>

						{#if usesModule(v)}
							<label class="ed-label"
								>Modul
								<select
									class="ed-input"
									value={node.module ?? ''}
									onchange={(e) => setModule(node, (e.currentTarget as HTMLSelectElement).value)}
								>
									<option value="">Modul wählen …</option>
									{#each catalog as m (m.module.slug)}
										<option value={m.module.slug}>{m.module.title}</option>
									{/each}
									{#if node.module && !modulesBySlug.has(node.module)}
										<option value={node.module}>{node.module} (fehlt)</option>
									{/if}
								</select>
							</label>
						{/if}

						{#if (v === 'TASK' || v === 'REMEDIAL') && node.module}
							<label class="ed-label"
								>{v === 'REMEDIAL' ? 'Stützaufgabe' : 'Aufgabe'}
								<select
									class="ed-input"
									value={node.task ?? ''}
									onchange={(e) =>
										updateNode(node.id, { task: (e.currentTarget as HTMLSelectElement).value })}
								>
									<option value="">Aufgabe wählen …</option>
									{#each moduleTasks(node.module) as t, i (t.slug)}
										<option value={t.slug}>{i + 1}. {t.title}</option>
									{/each}
								</select>
							</label>
						{/if}

						{#if v === 'REMEDIAL'}
							<label class="ed-label"
								>Wissensartikel (optional)
								<input
									class="ed-input"
									placeholder="Slug, z. B. datenbank-grundlagen"
									value={node.knowledgeArticle ?? ''}
									oninput={(e) =>
										updateNode(node.id, {
											knowledgeArticle: (e.currentTarget as HTMLInputElement).value
										})}
								/>
							</label>
							<p class="text-xs text-slate-500">
								Ein Stütz-Knoten braucht genau eine eingehende Verbindung mit der Bedingung
								„Schwierigkeiten“.
							</p>
						{/if}

						{#if v === 'PART' && node.module}
							{@const taken = usedElsewhere(node)}
							<fieldset class="flex flex-col gap-2">
								<legend class="ed-label mb-1.5">Aufgaben dieser Station</legend>
								<ul
									class="flex max-h-60 flex-col gap-1 overflow-auto border border-slate-100 p-2"
									aria-label="Aufgaben auswählen"
								>
									{#each moduleTasks(node.module) as t, i (t.slug)}
										<li>
											<label class="flex items-start gap-2 text-xs">
												<input
													type="checkbox"
													checked={node.tasks?.includes(t.slug) ?? false}
													onchange={(e) =>
														toggleTask(node, t.slug, (e.currentTarget as HTMLInputElement).checked)}
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
							</fieldset>
						{/if}

						{#if v === 'PATH'}
							{@const target = pathsBySlug.get(node.path ?? '')}
							<label class="ed-label"
								>Eingebundener CodePath
								<select
									class="ed-input"
									value={node.path ?? ''}
									onchange={(e) =>
										updateNode(node.id, { path: (e.currentTarget as HTMLSelectElement).value })}
								>
									<option value="">CodePath wählen …</option>
									{#each availableSubpaths as p (p.slug)}
										<option value={p.slug}>{p.title} ({p.stations} Stationen)</option>
									{/each}
								</select>
							</label>
							{#if target}
								<a class="ed-button text-center" href="/admin/codepaths/{target.id}"
									>Baustein bearbeiten →</a
								>
							{/if}
						{/if}

						{#if v === 'AND' || v === 'OR'}
							<p class="text-xs text-slate-600">
								{incomingCount} eingehende Verbindung{incomingCount === 1 ? '' : 'en'}
								{#if incomingCount < 2}
									<span class="font-bold text-amber-700">(mindestens 2 nötig)</span>
								{/if}
							</p>
						{/if}
						{#if v === 'OR'}
							<label class="ed-label"
								>Mindestens erfüllte Eingänge
								<input
									type="number"
									class="ed-input"
									min="1"
									max={Math.max(2, incomingCount)}
									value={node.min ?? 1}
									oninput={(e) =>
										updateNode(node.id, {
											min: Math.max(1, Number((e.currentTarget as HTMLInputElement).value) || 1)
										})}
								/>
							</label>
						{/if}
						{#if v === 'BONUS'}
							<label class="ed-label"
								>Bonuspunkte (1–1000)
								<input
									type="number"
									class="ed-input"
									min="1"
									max="1000"
									value={node.points ?? 10}
									oninput={(e) =>
										updateNode(node.id, {
											points: Number((e.currentTarget as HTMLInputElement).value) || 10
										})}
								/>
							</label>
						{/if}

						{#if v}
							<label class="ed-label"
								>{v === 'MILESTONE' || v === 'BONUS'
									? 'Beschriftung'
									: 'Eigene Beschriftung (optional)'}
								<input
									class="ed-input"
									value={node.label ?? ''}
									maxlength="80"
									placeholder={v === 'MILESTONE'
										? 'z. B. Grundlagen geschafft!'
										: v === 'BONUS'
											? 'z. B. Fleißaufgabe gelöst!'
											: v === 'CHOICE'
												? 'z. B. Cloud oder Web?'
												: ''}
									oninput={(e) =>
										updateNode(node.id, { label: (e.currentTarget as HTMLInputElement).value })}
								/></label
							>
						{/if}

						{#if v === 'MODULE' || v === 'PART' || v === 'TASK'}
							<label class="flex items-center gap-2 text-sm text-institutional-navy"
								><input
									type="checkbox"
									checked={!!node.boss}
									onchange={(e) =>
										updateNode(node.id, { boss: (e.currentTarget as HTMLInputElement).checked })}
								/>
								<PathIcon name="crown" size={14} class="text-institutional-gold" /> Boss-Aufgabe (auf
								der Karte hervorgehoben)</label
							>
						{/if}

						{#if v === 'MODULE' || v === 'PART' || v === 'TASK' || v === 'MILESTONE'}
							{@const req = node.requirement}
							<fieldset class="flex flex-col gap-2 border border-slate-200 bg-slate-50/60 p-3">
								<legend class="ed-label px-1">Punkte-Tor</legend>
								<label class="flex items-center gap-2 text-xs text-slate-800"
									><input
										type="checkbox"
										checked={!!req}
										onchange={(e) =>
											updateNode(node.id, {
												requirement: (e.currentTarget as HTMLInputElement).checked
													? {
															minPercent: 80,
															mode: 'TOTAL',
															fallback: 'TEACHER',
															fallbackRemedialNode: '',
															scope: []
														}
													: undefined
											})}
									/> Erst ab einer Mindest-Punktzahl freischalten</label
								>
								{#if req}
									{@const scopeCandidates = nodes.filter(
										(n) => n.id !== node.id && (n.kind === 'TASK' || n.kind === 'MODULE')
									)}
									<label class="ed-label"
										>Schwelle (1–100 %)
										<input
											type="number"
											class="ed-input"
											min="1"
											max="100"
											value={req.minPercent ?? 80}
											oninput={(e) =>
												updateNode(node.id, {
													requirement: {
														...req,
														minPercent: Math.max(
															1,
															Math.min(
																100,
																Number((e.currentTarget as HTMLInputElement).value) || 80
															)
														)
													}
												})}
										/>
									</label>
									{#if incomingCount >= 2}
										<label class="ed-label"
											>Berechnung
											<select
												class="ed-input"
												value={req.mode ?? 'TOTAL'}
												onchange={(e) =>
													updateNode(node.id, {
														requirement: {
															...req,
															mode: (e.currentTarget as HTMLSelectElement).value as 'TOTAL' | 'EACH'
														}
													})}
											>
												<option value="TOTAL">Gesamtpunkte aller Vorgänger</option>
												<option value="EACH">Jeder eingehende Weg einzeln</option>
											</select>
										</label>
									{/if}
									<label class="ed-label"
										>Wenn die Schwelle nicht mehr erreichbar ist
										<select
											class="ed-input"
											value={req.fallback ?? 'TEACHER'}
											onchange={(e) =>
												updateNode(node.id, {
													requirement: {
														...req,
														fallback: (e.currentTarget as HTMLSelectElement).value as
															| 'TEACHER'
															| 'OPEN'
															| 'REMEDIAL'
													}
												})}
										>
											<option value="TEACHER">Gesperrt lassen – Lehrkraft schaltet frei</option>
											<option value="OPEN">Trotzdem öffnen</option>
											<option value="REMEDIAL">Über einen Stütz-Knoten freischalten</option>
										</select>
									</label>
									{#if req.fallback === 'REMEDIAL'}
										<label class="ed-label"
											>Stütz-Knoten
											<select
												class="ed-input"
												value={req.fallbackRemedialNode ?? ''}
												onchange={(e) =>
													updateNode(node.id, {
														requirement: {
															...req,
															fallbackRemedialNode: (e.currentTarget as HTMLSelectElement).value
														}
													})}
											>
												<option value="">Stütz-Knoten wählen …</option>
												{#each nodes.filter((n) => n.kind === 'REMEDIAL') as rn (rn.id)}
													<option value={rn.id}>{label(rn)}</option>
												{/each}
											</select>
										</label>
									{/if}
									{#if scopeCandidates.length}
										<details class="text-xs">
											<summary class="cursor-pointer font-bold text-slate-600"
												>Welche Stationen zählen? ({req.scope?.length
													? `${req.scope.length} gewählt`
													: 'alle Vorgänger'})</summary
											>
											<div
												class="mt-1.5 flex max-h-36 flex-col gap-1 overflow-y-auto border border-slate-200 bg-white p-2"
											>
												{#each scopeCandidates as cand (cand.id)}
													<label class="flex items-center gap-2"
														><input
															type="checkbox"
															checked={(req.scope ?? []).includes(cand.id)}
															onchange={(e) => {
																const scope = req.scope ?? [];
																updateNode(node.id, {
																	requirement: {
																		...req,
																		scope: (e.currentTarget as HTMLInputElement).checked
																			? [...scope, cand.id]
																			: scope.filter((x) => x !== cand.id)
																	}
																});
															}}
														/>
														{label(cand)}</label
													>
												{/each}
											</div>
										</details>
									{/if}
								{/if}
							</fieldset>
						{/if}

						<div class="flex flex-col gap-1">
							<span class="ed-label">Davor</span>
							{#each edges.filter((e) => e.to === node.id) as edge (edgeKey(edge))}
								<span class="flex items-center justify-between gap-2 text-sm">
									<button
										class="truncate text-left hover:underline"
										onclick={() => select(edge.from)}>← {label(byId.get(edge.from)!)}</button
									>
									<button
										class="text-slate-400 hover:text-rose-700"
										onclick={() => removeEdge(edgeKey(edge))}
										aria-label="Verbindung von {label(byId.get(edge.from)!)} löschen"
										><PathIcon name="close" size={14} /></button
									>
								</span>
							{:else}
								<span class="text-xs text-slate-400">Startpunkt – nichts davor</span>
							{/each}
						</div>

						<div class="flex flex-col gap-1">
							<span class="ed-label">{v === 'CHOICE' ? 'Wege' : 'Danach kommt'}</span>
							{#each edges.filter((e) => e.from === node.id) as edge (edgeKey(edge))}
								<div class="flex flex-col gap-1">
									<span class="flex items-center justify-between gap-2 text-sm">
										<button
											class="truncate text-left hover:underline"
											onclick={() => {
												selectedEdge = edgeKey(edge);
												selectedId = null;
											}}
											title="Verbindung bearbeiten"
											>→ {label(byId.get(edge.to)!)}{#if edge.condition?.rule}<span
													class="ml-1 font-mono text-[10px] text-sky-700 uppercase"
													>· {CONDITION_LABELS[edge.condition.rule]}</span
												>{/if}</button
										>
										<button
											class="text-slate-400 hover:text-rose-700"
											onclick={() => removeEdge(edgeKey(edge))}
											aria-label="Verbindung zu {label(byId.get(edge.to)!)} löschen"
											><PathIcon name="close" size={14} /></button
										>
									</span>
									{#if v === 'CHOICE'}
										<input
											class="ed-input mt-0 text-xs"
											placeholder="Beschriftung des Wegs (Pflicht)"
											aria-label="Weg-Beschriftung nach {label(byId.get(edge.to)!)}"
											maxlength="80"
											value={edge.label ?? ''}
											oninput={(e) =>
												updateEdge(edgeKey(edge), {
													label: (e.currentTarget as HTMLInputElement).value
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
									if (target) connect(node.id, target);
									(e.currentTarget as HTMLSelectElement).value = '';
								}}
							>
								<option value="">+ Verbindung zu …</option>
								{#each nodes.filter((n) => n.id !== node.id) as n (n.id)}
									<option value={n.id}>{label(n)}</option>
								{/each}
							</select>
						</div>

						<div class="flex flex-col gap-2 border-t border-slate-100 pt-3">
							{#if v && v !== 'CHOICE' && saved?.published}
								<button
									type="button"
									class="ed-button"
									disabled={busy}
									title="Für alle Schüler/innen des Kurses freischalten, auch wenn die Vorgänger fehlen"
									onclick={() =>
										runCourseAction({
											action: 'UNLOCK_NODE',
											pathSlug: saved?.slug,
											nodeId: node.id
										})}>Für ganzen Kurs freischalten</button
								>
							{/if}
							<button class="ed-button text-rose-700" onclick={() => removeNode(node.id)}
								>Station entfernen</button
							>
						</div>
					</section>
				{:else}
					<section class="flex flex-col gap-3 p-4" aria-label="CodePath-Einstellungen">
						<h2 class="ed-label">CodePath</h2>
						{#if saved.warnings?.length}
							<div
								class="border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900"
								role="status"
							>
								<p class="flex items-center gap-1 font-bold">
									<PathIcon name="warning" size={13} /> Hinweise
								</p>
								<ul class="mt-1 list-disc pl-4">
									{#each saved.warnings as w, i (i)}<li>{w}</li>{/each}
								</ul>
							</div>
						{/if}
						<label class="ed-label">Titel<input class="ed-input" bind:value={form.title} /></label>
						<label class="ed-label"
							>Adresse<input
								class="ed-input mono"
								bind:value={form.slug}
								pattern="[a-z0-9-]+"
							/></label
						>
						<label class="ed-label"
							>Beschreibung<textarea
								class="ed-input"
								rows="2"
								bind:value={form.description}
								maxlength="500"
							></textarea></label
						>
						<label class="ed-label"
							>Gilt für
							<select class="ed-input" bind:value={form.courseId} onchange={() => refreshStats()}>
								<option value={0}>Alle Kurse (ändert keine Freigaben)</option>
								{#each courses as course (course.id)}
									<option value={course.id}
										>Kurs {course.name}{course.status === 'ENDED' ? ' (beendet)' : ''}</option
									>
								{/each}
							</select>
						</label>
						<label class="ed-label"
							>Alles außerhalb des CodePaths
							<select
								class="ed-input"
								value={outsideMode}
								onchange={(e) => setOutsideMode((e.currentTarget as HTMLSelectElement).value)}
							>
								<option value="FREE">Frei (folgt der Modul-Steuerung)</option>
								<option value="LOCKED">Gesperrt (nur der CodePath)</option>
								<option value="AFTER">Frei ab Station …</option>
							</select>
						</label>
						{#if outsideMode === 'AFTER'}
							<select
								class="ed-input mt-0"
								aria-label="Frei ab Station"
								bind:value={form.unlockRestAfter}
							>
								{#each nodes as n (n.id)}
									<option value={n.id}>ab „{label(n)}“</option>
								{/each}
							</select>
						{/if}
						{#if form.courseId}
							<p class="text-xs text-slate-500">
								Veröffentlicht übernimmt jedes Speichern die Module und Aufgaben dieses CodePaths in
								die <a href="/admin/modules" class="underline">Modul-Steuerung</a> des Kurses.
							</p>
						{/if}
						<div class="flex flex-col gap-1.5 border-t border-slate-100 pt-3">
							<label class="flex items-start gap-2 text-sm"
								><input
									type="checkbox"
									class="mt-0.5"
									checked={form.enforce}
									onchange={(e) => toggleEnforce((e.currentTarget as HTMLInputElement).checked)}
								/> Verbindlich (Pflicht-Vorgänger müssen erledigt sein)</label
							>
							<label class="flex items-start gap-2 text-sm {form.enforce ? '' : 'text-slate-400'}"
								><input
									type="checkbox"
									class="mt-0.5"
									bind:checked={form.hideOverview}
									disabled={!form.enforce}
								/> Fokus-Modus: Gesamtübersicht für Schüler ausblenden</label
							>
							<label class="flex items-start gap-2 text-sm"
								><input type="checkbox" class="mt-0.5" bind:checked={form.templateOnly} /> Nur als Baustein
								(nicht einzeln für Schüler)</label
							>
							<label class="flex items-start gap-2 text-sm"
								><input type="checkbox" class="mt-0.5" bind:checked={form.published} /> Veröffentlicht
								(für Schüler sichtbar)</label
							>
						</div>
						{#if saved.usedIn?.length}
							<p class="flex items-start gap-1 text-xs text-slate-500">
								<PathIcon name="puzzle" size={13} class="mt-0.5" />
								<span>Eingebunden in: <strong>{saved.usedIn.join(', ')}</strong></span>
							</p>
						{/if}

						{#if applied}
							<div
								class="border border-emerald-200 bg-emerald-50 p-3 text-xs text-emerald-900"
								role="status"
							>
								{#if applied.length}
									<p class="font-bold">In die Modul-Steuerung übernommen:</p>
									<ul class="mt-1 list-disc pl-4">
										{#each applied as line, i (i)}<li>{line}</li>{/each}
									</ul>
								{:else}
									<p>Die Modul-Steuerung des Kurses entsprach schon diesem CodePath.</p>
								{/if}
							</div>
						{/if}

						{#if choiceStats.length}
							<section class="flex flex-col gap-2 border-t border-slate-100 pt-3">
								<h3 class="ed-label">Weichen im Kurs</h3>
								{#each choiceStats as stat (`${stat.pathSlug}:${stat.nodeId}`)}
									{@const statKey = `${stat.pathSlug}:${stat.nodeId}`}
									<div
										class="flex flex-col gap-1.5 border border-slate-200 bg-slate-50/60 p-2.5 text-xs"
									>
										<span class="flex items-center gap-1 font-bold text-institutional-navy"
											><PathIcon name="split" size={13} />{stat.nodeLabel}{#if stat.subpath}<span
													class="ml-1 font-mono text-[9px] text-violet-700 uppercase"
													>· {stat.pathTitle}</span
												>{/if}</span
										>
										{#each stat.branches ?? [] as b (b.to)}
											<div
												class="flex items-center justify-between gap-2 border border-slate-200 bg-white px-2 py-1"
											>
												<span class="truncate">{b.label} ({b.count}/{stat.totalStudents})</span>
												<span class="flex shrink-0 gap-1">
													<button
														type="button"
														disabled={busy}
														class="border border-slate-200 px-1.5 py-0.5 font-mono text-[9px] font-bold uppercase hover:border-institutional-navy"
														onclick={() =>
															runCourseAction({
																action: 'SET_CHOICE',
																pathSlug: stat.pathSlug,
																nodeId: stat.nodeId,
																to: b.to
															})}>Wählen</button
													>
													<button
														type="button"
														disabled={busy}
														class="border border-slate-200 px-1.5 py-0.5 font-mono text-[9px] font-bold text-sky-700 uppercase hover:border-sky-500"
														onclick={() =>
															runCourseAction({
																action: 'UNLOCK_BRANCH',
																pathSlug: stat.pathSlug,
																nodeId: stat.nodeId,
																to: b.to
															})}>+ Frei</button
													>
												</span>
											</div>
										{/each}
										<span class="text-slate-500">{stat.openCount}× noch offen</span>
										<div class="flex items-center justify-between gap-2">
											<label class="flex items-center gap-1.5 text-[11px] text-slate-600"
												><input
													type="checkbox"
													checked={!!resetCourseProgress[statKey]}
													onchange={(e) =>
														(resetCourseProgress[statKey] = (
															e.currentTarget as HTMLInputElement
														).checked)}
												/> Fortschritt zurücksetzen</label
											>
											<button
												type="button"
												disabled={busy}
												class="border border-rose-200 bg-white px-2 py-0.5 font-mono text-[9px] font-bold text-rose-700 uppercase hover:bg-rose-50"
												onclick={() =>
													runCourseAction({
														action: 'RESET_CHOICE',
														pathSlug: stat.pathSlug,
														nodeId: stat.nodeId,
														resetProgress: !!resetCourseProgress[statKey]
													})}>Wahlen zurücksetzen</button
											>
										</div>
									</div>
								{/each}
							</section>
						{/if}

						{#if gateStats.length}
							{@const gateIds = Array.from(new Set(gateStats.map((s) => s.nodeId)))}
							<section class="flex flex-col gap-2 border-t border-slate-100 pt-3">
								<h3 class="ed-label">Punkte-Tore im Kurs</h3>
								{#each gateIds as gateId (gateId)}
									{@const stats = gateStats.filter((s) => s.nodeId === gateId)}
									{@const stuck = stats.filter((s) => s.unreachable && !s.open).length}
									<details class="border border-slate-200 bg-slate-50/60 p-2.5 text-xs">
										<summary class="cursor-pointer">
											<span class="font-bold text-institutional-navy"
												>{stats[0]?.nodeLabel ?? gateId}</span
											>
											<span class="ml-1 font-mono text-[10px] text-slate-500"
												>≥ {stats[0]?.minPercent ?? 80} % · {stats.filter((s) => s.open)
													.length}/{stats.length}
												offen{#if stuck}<span class="text-rose-700">
														· {stuck} stecken fest</span
													>{/if}</span
											>
										</summary>
										<ul class="mt-1.5 flex flex-col gap-1">
											{#each stats as st (st.username)}
												<li
													class="flex items-center justify-between gap-2 border border-slate-200 bg-white px-2 py-1"
												>
													<a href="/admin/students/{st.username}" class="truncate hover:underline"
														>{st.displayName} ({st.percent} %)</a
													>
													<span
														class="shrink-0 font-mono text-[9px] font-bold uppercase {st.open
															? 'text-emerald-700'
															: st.unreachable
																? 'text-rose-700'
																: 'text-amber-700'}"
														>{st.open
															? 'Offen'
															: st.unreachable
																? 'Unerreichbar'
																: `Noch ${st.missingPoints} P.`}</span
													>
												</li>
											{/each}
										</ul>
									</details>
								{/each}
							</section>
						{/if}

						<div class="mt-2 flex flex-wrap gap-2 border-t border-slate-100 pt-3">
							<a class="ed-button" href={exportUrl(['codepaths'], [saved.slug])} download
								>Exportieren</a
							>
							<button class="ed-button text-rose-700" onclick={remove}>CodePath löschen</button>
						</div>
						<p class="mt-2 text-xs text-slate-400">
							Eine Station oder Linie anklicken, um sie zu bearbeiten. Pfeiltasten verschieben die
							gewählte Station, Entf entfernt sie.
						</p>
					</section>
				{/if}
			</aside>

			{#if preview}
				<div
					class="absolute inset-0 z-20 flex flex-col border border-slate-300 bg-white shadow-xl"
					role="dialog"
					aria-label="Vorschau"
				>
					<div class="flex flex-wrap items-center gap-3 border-b border-slate-200 px-4 py-2">
						<h2 class="ed-label">Vorschau</h2>
						<span class="text-xs text-slate-500">Gespeicherte Fassung.</span>
						<label class="flex items-center gap-2 text-xs font-bold text-institutional-navy"
							>Ansehen als
							<select
								class="border border-slate-200 bg-white px-2 py-1 text-xs font-normal"
								bind:value={previewStudent}
								onchange={showPreview}
							>
								<option value="">Eigener Stand (Lehrkraft)</option>
								{#each filteredStudents as s (s.username)}
									<option value={s.username}
										>{s.displayName} ({s.username}){s.courseName
											? ` · ${s.courseName}`
											: ''}</option
									>
								{/each}
							</select>
						</label>
						<span class="flex-1"></span>
						<button class="ed-button" onclick={() => (preview = null)}>Schließen</button>
					</div>
					<div class="min-h-0 flex-1 overflow-auto p-4">
						<PathMap path={preview} student={previewStudent || undefined} />
					</div>
				</div>
			{/if}
		</div>
	{/if}
</div>

<style>
	.tool {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		height: 1.75rem;
		min-width: 1.75rem;
		color: rgb(30 41 59);
	}
	.tool:hover {
		background: rgb(241 245 249);
	}
</style>
