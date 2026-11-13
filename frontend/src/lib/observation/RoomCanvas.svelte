<script lang="ts">
	import {
		bounds,
		ROOM_HEIGHT,
		ROOM_WIDTH,
		SEAT_HEIGHT,
		SEAT_WIDTH,
		itemLabel,
		localCenter,
		seatsInOrder,
		shortName,
		snap,
		type PersonView,
		type RoomItem,
		type RoomLayout,
		type RoomSeat
	} from './room';
	import { personDrag } from './personDrag';

	let {
		layout,
		people,
		mode,
		selectedItemId = $bindable(null),
		selectedSeatId = null,
		armedPersonId = null,
		onchange,
		onseat,
		onassign
	}: {
		layout: RoomLayout;
		people: PersonView[];
		/** edit = Tische bearbeiten, assign = Plätze zuweisen */
		mode: 'edit' | 'assign';
		selectedItemId?: string | null;
		selectedSeatId?: string | null;
		/** Person, die mit dem nächsten Klick auf einen Platz gesetzt wird */
		armedPersonId?: number | null;
		onchange: (layout: RoomLayout) => void;
		onseat: (seatId: string) => void;
		onassign: (personId: number, seatId: string | null) => void;
	} = $props();

	let svg = $state<SVGSVGElement | null>(null);
	/** Beim Zuweisen nur der eingerichtete Teil des Raums – größere Plätze, besser zu treffen */
	const view = $derived(
		mode === 'assign' && layout.items.length > 0
			? bounds(layout)
			: { x: 0, y: 0, width: ROOM_WIDTH, height: ROOM_HEIGHT }
	);
	const bySeat = $derived(new Map(people.filter((p) => p.seatId).map((p) => [p.seatId!, p])));
	const seatLabels = $derived(new Map(seatsInOrder(layout).map((s) => [s.id, s.label])));

	/** Text nie auf dem Kopf: ab 90° Drehung um 180° gewendet */
	function flip(item: RoomItem): number {
		const r = ((item.rotation % 360) + 360) % 360;
		return r > 90 && r <= 270 ? 180 : 0;
	}

	function point(e: PointerEvent) {
		const ctm = svg?.getScreenCTM();
		if (!svg || !ctm) return { x: 0, y: 0 };
		const p = new DOMPoint(e.clientX, e.clientY).matrixTransform(ctm.inverse());
		return { x: p.x, y: p.y };
	}

	let drag: { id: string; dx: number; dy: number; moved: boolean; sx: number; sy: number } | null =
		null;

	function itemDown(e: PointerEvent, item: RoomItem) {
		if (mode !== 'edit' || e.button !== 0) return;
		e.stopPropagation();
		selectedItemId = item.id;
		const p = point(e);
		drag = { id: item.id, dx: p.x - item.x, dy: p.y - item.y, moved: false, sx: p.x, sy: p.y };
		(e.currentTarget as Element).setPointerCapture(e.pointerId);
	}

	function itemMove(e: PointerEvent) {
		if (!drag) return;
		const p = point(e);
		if (!drag.moved && Math.hypot(p.x - drag.sx, p.y - drag.sy) < 4) return;
		drag.moved = true;
		const d = drag;
		update(d.id, (item) => ({
			...item,
			x: clamp(snap(p.x - d.dx), -item.width / 2, ROOM_WIDTH - item.width / 2),
			y: clamp(snap(p.y - d.dy), -item.height / 2, ROOM_HEIGHT - item.height / 2)
		}));
	}

	function itemUp() {
		drag = null;
	}

	function clamp(v: number, min: number, max: number) {
		return Math.max(min, Math.min(max, v));
	}

	function update(id: string, change: (item: RoomItem) => RoomItem) {
		onchange({ items: layout.items.map((i) => (i.id === id ? change(i) : i)) });
	}

	function itemKey(e: KeyboardEvent, item: RoomItem) {
		if (mode !== 'edit') return;
		const step = e.shiftKey ? 50 : 10;
		const moves: Record<string, [number, number]> = {
			ArrowLeft: [-step, 0],
			ArrowRight: [step, 0],
			ArrowUp: [0, -step],
			ArrowDown: [0, step]
		};
		if (moves[e.key]) {
			e.preventDefault();
			const [dx, dy] = moves[e.key];
			update(item.id, (i) => ({ ...i, x: i.x + dx, y: i.y + dy }));
		} else if (e.key === 'r' || e.key === 'R') {
			e.preventDefault();
			update(item.id, (i) => ({ ...i, rotation: (i.rotation + (e.shiftKey ? 345 : 15)) % 360 }));
		} else if (e.key === 'Delete' || e.key === 'Backspace') {
			e.preventDefault();
			onchange({ items: layout.items.filter((i) => i.id !== item.id) });
			selectedItemId = null;
		} else if (e.key === 'Enter' || e.key === ' ') {
			e.preventDefault();
			selectedItemId = item.id;
		}
	}

	function seatDown(e: PointerEvent, seat: RoomSeat) {
		if (mode !== 'assign') return;
		const person = bySeat.get(seat.id);
		if (!person) return;
		e.stopPropagation();
		personDrag(e, person.name, (target) => {
			if (target !== seat.id) onassign(person.id, target);
		});
	}

	function seatLabel(seat: RoomSeat): string {
		const person = bySeat.get(seat.id);
		return `${seatLabels.get(seat.id) ?? 'Platz'}: ${person ? person.name : 'frei'}`;
	}

	function fill(item: RoomItem) {
		if (item.kind === 'BOARD') return 'fill-slate-800';
		if (item.kind === 'TEACHER') return 'fill-slate-200';
		return 'fill-slate-100';
	}
</script>

<div class="relative w-full overflow-hidden border border-slate-200 bg-white">
	<svg
		bind:this={svg}
		viewBox="{view.x} {view.y} {view.width} {view.height}"
		style="max-height: 75vh"
		class="block h-auto w-full touch-none select-none"
		role="application"
		aria-label={mode === 'edit' ? 'Klassenraum bearbeiten' : 'Sitzplan'}
		data-testid="room-canvas"
		onpointerdown={() => {
			if (mode === 'edit') selectedItemId = null;
		}}
	>
		<defs>
			<pattern id="room-grid" width="40" height="40" patternUnits="userSpaceOnUse">
				<path d="M 40 0 L 0 0 0 40" fill="none" class="stroke-slate-100" stroke-width="1" />
			</pattern>
		</defs>
		<rect
			x={Math.min(0, view.x)}
			y={Math.min(0, view.y)}
			width={Math.max(ROOM_WIDTH, view.x + view.width) - Math.min(0, view.x)}
			height={Math.max(ROOM_HEIGHT, view.y + view.height) - Math.min(0, view.y)}
			fill="url(#room-grid)"
		/>

		{#each layout.items as item (item.id)}
			{@const selected = mode === 'edit' && selectedItemId === item.id}
			<g
				transform="translate({item.x} {item.y}) rotate({item.rotation} {item.width /
					2} {item.height / 2})"
			>
				<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
				<g
					class={mode === 'edit' ? 'cursor-move outline-none' : ''}
					role={mode === 'edit' ? 'button' : undefined}
					tabindex={mode === 'edit' ? 0 : undefined}
					aria-label={mode === 'edit' ? `${itemLabel(layout, item)} auswählen` : undefined}
					aria-pressed={mode === 'edit' ? selected : undefined}
					data-item-id={item.id}
					onpointerdown={(e) => itemDown(e, item)}
					onpointermove={itemMove}
					onpointerup={itemUp}
					onpointercancel={itemUp}
					onkeydown={(e) => itemKey(e, item)}
				>
					<rect
						width={item.width}
						height={item.height}
						rx="4"
						class="{fill(item)} {selected
							? 'stroke-amber-500'
							: 'stroke-institutional-navy'} transition-colors"
						stroke-width={selected ? 4 : 1.5}
					/>
					<text
						x={item.width / 2}
						y={item.height / 2}
						transform="rotate({flip(item)} {item.width / 2} {item.height / 2})"
						text-anchor="middle"
						dominant-baseline="central"
						class="pointer-events-none text-[15px] font-bold {item.kind === 'BOARD'
							? 'fill-white'
							: 'fill-slate-500'}">{itemLabel(layout, item)}</text
					>
				</g>
				{#each item.seats as seat (seat.id)}
					{@const c = localCenter(item, seat)}
					{@const person = bySeat.get(seat.id)}
					{@const active = selectedSeatId === seat.id}
					<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
					<g
						data-seat-id={seat.id}
						class="group {mode === 'assign' ? 'cursor-pointer outline-none' : ''}"
						role={mode === 'assign' ? 'button' : undefined}
						tabindex={mode === 'assign' ? 0 : undefined}
						aria-label={mode === 'assign' ? seatLabel(seat) : undefined}
						aria-pressed={mode === 'assign' ? active : undefined}
						data-testid="seat-{seat.id}"
						onpointerdown={(e) => seatDown(e, seat)}
						onclick={() => mode === 'assign' && onseat(seat.id)}
						onkeydown={(e) => {
							if (mode === 'assign' && (e.key === 'Enter' || e.key === ' ')) {
								e.preventDefault();
								onseat(seat.id);
							}
						}}
					>
						<rect
							x={c.x - SEAT_WIDTH / 2}
							y={c.y - SEAT_HEIGHT / 2}
							width={SEAT_WIDTH}
							height={SEAT_HEIGHT}
							rx="14"
							class="seat {person ? 'fill-amber-100' : 'fill-white'} {active
								? 'stroke-amber-500'
								: armedPersonId != null && !person
									? 'stroke-emerald-500'
									: 'stroke-slate-300'} group-focus-visible:stroke-institutional-navy"
							stroke-width={active ? 4 : armedPersonId != null && !person ? 2.5 : 1.5}
							stroke-dasharray={person ? undefined : '4 3'}
						/>
						<text
							x={c.x}
							y={c.y}
							transform="rotate({flip(item)} {c.x} {c.y})"
							text-anchor="middle"
							dominant-baseline="central"
							class="pointer-events-none text-[13px] {person
								? 'fill-institutional-navy font-bold'
								: 'fill-slate-300'}">{person ? shortName(person.name) : 'frei'}</text
						>
					</g>
				{/each}
			</g>
		{/each}
		{#if layout.items.length === 0}
			<text
				x={ROOM_WIDTH / 2}
				y={ROOM_HEIGHT / 2}
				text-anchor="middle"
				class="fill-slate-400 text-[22px]"
				>Noch leer – unter „Raum bearbeiten“ Tische hinzufügen.</text
			>
		{/if}
	</svg>
</div>

<style>
	:global([data-drop-over]) .seat {
		stroke: var(--color-emerald-500, #10b981);
		stroke-width: 5;
	}
</style>
