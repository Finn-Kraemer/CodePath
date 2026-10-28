<script lang="ts">
	import { onMount } from 'svelte';
	import { beforeNavigate, goto } from '$app/navigation';
	import { page } from '$app/state';
	import {
		api,
		type CourseSummary,
		type PathAdmin,
		type PathNode,
		type PathView,
		type Schemas
	} from '$lib/api';
	import { canvasSize, edgePath, layered, NODE_H, NODE_W, snap } from '$lib/pathway/geometry';
	import PathMap from '$lib/pathway/PathMap.svelte';
	import { exportUrl } from '$lib/transfer';

	type ModuleRow = Schemas['ModuleRow'];
	type Edge = Schemas['Edge'];
	type Kind = PathNode['kind'];

	const id = $derived(Number(page.params.id));

	let saved = $state<PathAdmin | null>(null);
	let form = $state({
		title: '',
		slug: '',
		description: '',
		published: false,
		courseId: 0,
		lockOthers: false
	});
	let courses = $state<CourseSummary[]>([]);
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

	let draft = $state<{ kind: Kind; module: string; task: string; label: string }>({
		kind: 'MODULE',
		module: '',
		task: '',
		label: ''
	});

	const selected = $derived(nodes.find((n) => n.id === selectedId) ?? null);
	const size = $derived(canvasSize(nodes, 1100, 560));
	const byId = $derived(new Map(nodes.map((n) => [n.id, n])));
	const modulesBySlug = $derived(new Map(catalog.map((m) => [m.module.slug, m])));
	const current = $derived(JSON.stringify({ ...form, graph: { nodes, edges } }));
	const savedState = $derived(
		saved
			? JSON.stringify({
					title: saved.title,
					slug: saved.slug,
					description: saved.description,
					published: saved.published,
					courseId: saved.courseId ?? 0,
					lockOthers: saved.lockOthers,
					graph: saved.graph
				})
			: ''
	);
	const dirty = $derived(!!saved && current !== savedState);

	onMount(async () => {
		try {
			const [path, modules, courseList] = await Promise.all([
				api<PathAdmin>(`/api/admin/learning-paths/${id}`),
				api<ModuleRow[]>('/api/admin/content'),
				api<CourseSummary[]>('/api/admin/courses')
			]);
			catalog = modules;
			courses = courseList;
			apply(path);
			draft.module = catalog[0]?.module.slug ?? '';
		} catch (e) {
			error = (e as Error).message;
		}
	});

	beforeNavigate((navigation) => {
		if (dirty && !confirm('Ungespeicherte Änderungen verwerfen?')) navigation.cancel();
	});

	function apply(path: PathAdmin) {
		// Gleiche Schlüsselreihenfolge wie beim Vergleich, damit "ungespeichert" stimmt
		const graph = {
			nodes: path.graph.nodes.map(normalize),
			edges: path.graph.edges.map((e) => ({ from: e.from, to: e.to }))
		};
		saved = { ...path, graph };
		form = {
			title: path.title,
			slug: path.slug,
			description: path.description ?? '',
			published: path.published,
			courseId: path.courseId ?? 0,
			lockOthers: path.lockOthers
		};
		nodes = structuredClone(graph.nodes);
		edges = structuredClone(graph.edges);
	}

	function normalize(n: PathNode): PathNode {
		const node: Record<string, unknown> = { id: n.id, kind: n.kind };
		if (n.module) node.module = n.module;
		if (n.task) node.task = n.task;
		if (n.kind === 'MODULE' && n.tasks?.length) node.tasks = n.tasks;
		if (n.label) node.label = n.label;
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
				body: { ...form, graph: { nodes: nodes.map(normalize), edges } }
			});
			apply(updated);
			applied = form.courseId && form.published ? updated.applied : null;
			flash('Gespeichert.');
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
			preview = await api<PathView>(`/api/learning-paths/${saved.slug}`);
		} catch (e) {
			error = (e as Error).message;
		}
	}

	// ------------------------------------------------------------ Stationen

	function label(n: PathNode): string {
		if (n.label) return n.label;
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
		return modulesBySlug.get(n.module)?.module.icon ?? '';
	}

	const KIND_LABELS: Record<Kind, string> = {
		MODULE: 'Modul',
		TASK: 'Aufgabe',
		MILESTONE: 'Meilenstein'
	};

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
		if (draft.kind === 'TASK' && !draft.task) {
			error = 'Bitte eine Aufgabe wählen.';
			return;
		}
		const anchor = selected;
		const node: PathNode = normalize({
			id: freeId(),
			kind: draft.kind,
			module: draft.kind === 'MILESTONE' ? '' : draft.module,
			task: draft.kind === 'TASK' ? draft.task : '',
			tasks: [],
			label: draft.kind === 'MILESTONE' ? draft.label.trim() : '',
			x: anchor ? anchor.x : 60,
			y: anchor
				? anchor.y + NODE_H + 70
				: snap(Math.max(-NODE_H, ...nodes.map((n) => n.y)) + NODE_H + 70)
		} as PathNode);
		nodes = [...nodes, node];
		// An die gewählte Station anhängen – so entsteht ein Pfad Schritt für Schritt
		if (anchor) edges = [...edges, { from: anchor.id, to: node.id }];
		selectedId = node.id;
		draft.label = '';
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
		} as PathNode);
		if (!node.label && node.tasks?.length) updateNode(node.id, { label: `${title} 1` });
		nodes = [...nodes, added];
		selectedId = added.id;
	}

	function removeNode(nodeId: string) {
		nodes = nodes.filter((n) => n.id !== nodeId);
		edges = edges.filter((e) => e.from !== nodeId && e.to !== nodeId);
		if (selectedId === nodeId) selectedId = null;
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
		edges = [...edges, { from, to }];
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
			<div class="flex flex-col justify-end gap-1 text-xs text-slate-500 md:col-span-2">
				{#if form.courseId}
					<label class="flex items-center gap-2 text-sm text-institutional-navy"
						><input type="checkbox" bind:checked={form.lockOthers} /> Module, die nicht im Lernpfad vorkommen,
						für den Kurs sperren</label
					>
					<span
						>Veröffentlicht übernimmt jedes Speichern die Module und Aufgaben dieses Lernpfads in
						die <a href="/admin/modules" class="underline">Modul-Steuerung</a> des Kurses.</span
					>
				{/if}
			</div>
			<div class="flex flex-wrap items-center gap-3 md:col-span-4">
				<label class="flex items-center gap-2 text-sm"
					><input type="checkbox" bind:checked={form.published} /> Veröffentlicht (für Schüler sichtbar)</label
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
		</section>

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
			<section class="flex flex-col gap-2">
				<p class="text-xs text-slate-500">
					Vorschau mit deinem eigenen Stand (gespeicherte Fassung). Schüler sehen gesperrte Module
					als gesperrt.
				</p>
				<PathMap path={preview} />
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
									{@const d = edgePath(from, to)}
									<path
										{d}
										fill="none"
										stroke-width="2.5"
										class={selectedEdge === edgeKey(edge) ? 'stroke-rose-500' : 'stroke-slate-400'}
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
							<div
								class="absolute flex cursor-grab touch-none flex-col justify-between border-2 bg-white p-3 select-none active:cursor-grabbing {node.kind ===
								'MILESTONE'
									? 'rounded-full border-dashed border-institutional-gold text-center'
									: 'border-slate-300'} {selectedId === node.id
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
									<span class="text-lg leading-none">{icon(node)}</span>
									<span class="line-clamp-2 text-sm leading-tight font-bold text-institutional-navy"
										>{label(node)}</span
									>
								</div>
								<span
									class="font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase"
									>{KIND_LABELS[node.kind]}</span
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
						<section class="flex flex-col gap-3 border border-slate-200 bg-white p-4">
							<h2 class="ed-label">Verbindung</h2>
							<p class="text-sm">
								{label(byId.get(edge.from)!)} → {label(byId.get(edge.to)!)}
							</p>
							<button class="ed-button text-rose-700" onclick={() => removeEdge(selectedEdge!)}
								>Verbindung löschen</button
							>
						</section>
					{/if}
				{/if}

				{#if selected}
					<section
						class="flex flex-col gap-3 border border-slate-200 bg-white p-4"
						aria-label="Station bearbeiten"
					>
						<h2 class="ed-label">{KIND_LABELS[selected.kind]}</h2>
						{#if selected.kind !== 'MILESTONE'}
							<p class="font-mono text-xs text-slate-500">
								{selected.module}{selected.task ? `/${selected.task}` : ''}
							</p>
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
							>{selected.kind === 'MILESTONE' ? 'Beschriftung' : 'Eigene Beschriftung (optional)'}
							<input
								class="ed-input"
								value={selected.label ?? ''}
								maxlength="80"
								oninput={(e) => setLabel((e.currentTarget as HTMLInputElement).value)}
							/></label
						>
						<div class="flex flex-col gap-1">
							<span class="ed-label">Danach kommt</span>
							{#each edges.filter((e) => e.from === selected.id) as edge (edgeKey(edge))}
								<span class="flex items-center justify-between gap-2 text-sm">
									→ {label(byId.get(edge.to)!)}
									<button
										class="text-xs text-rose-700"
										onclick={() => removeEdge(edgeKey(edge))}
										aria-label="Verbindung zu {label(byId.get(edge.to)!)} löschen">✕</button
									>
								</span>
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
						<button class="ed-button text-rose-700" onclick={() => removeNode(selected.id)}
							>Station entfernen</button
						>
					</section>
				{/if}

				<form class="flex flex-col gap-3 border border-slate-200 bg-white p-4" onsubmit={addNode}>
					<h2 class="ed-label">Station hinzufügen</h2>
					<select class="ed-input" bind:value={draft.kind} aria-label="Art der Station">
						<option value="MODULE">Modul</option>
						<option value="TASK">Einzelne Aufgabe</option>
						<option value="MILESTONE">Meilenstein</option>
					</select>
					{#if draft.kind !== 'MILESTONE'}
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
					{#if draft.kind === 'MILESTONE'}
						<input
							class="ed-input"
							placeholder="z. B. Grundlagen geschafft!"
							aria-label="Beschriftung des Meilensteins"
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
