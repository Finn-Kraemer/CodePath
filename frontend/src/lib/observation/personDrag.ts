/**
 * Person per Zeiger (Maus, Stift, Finger) auf einen Platz ziehen. HTML-Drag-&-Drop geht auf Tablets nicht,
 * deshalb folgt ein „Geist“ dem Zeiger, und beim Loslassen zählt, was darunter liegt:
 * `[data-seat-id]` = Platz, `[data-unseat]` = Platz freigeben. Ein einfacher Klick löst nichts aus
 * (dafür sind die normalen Klick-Handler da).
 */
export function personDrag(
	e: PointerEvent,
	label: string,
	drop: (seatId: string | null) => void
): void {
	if (e.button !== 0) return;
	const startX = e.clientX;
	const startY = e.clientY;
	let ghost: HTMLElement | null = null;
	let over: Element | null = null;

	function target(x: number, y: number): Element | null {
		const el = document.elementFromPoint(x, y);
		return el?.closest('[data-seat-id], [data-unseat]') ?? null;
	}

	function mark(el: Element | null) {
		if (el === over) return;
		over?.removeAttribute('data-drop-over');
		el?.setAttribute('data-drop-over', '');
		over = el;
	}

	function move(ev: PointerEvent) {
		if (!ghost) {
			if (Math.hypot(ev.clientX - startX, ev.clientY - startY) < 6) return;
			ghost = document.createElement('div');
			ghost.textContent = label;
			ghost.setAttribute('aria-hidden', 'true');
			ghost.className =
				'pointer-events-none fixed z-50 -translate-x-1/2 -translate-y-1/2 border-2 border-amber-500 bg-amber-100 px-3 py-1 text-sm font-bold text-institutional-navy shadow-lg';
			document.body.append(ghost);
		}
		ghost.style.left = `${ev.clientX}px`;
		ghost.style.top = `${ev.clientY}px`;
		mark(target(ev.clientX, ev.clientY));
	}

	function cleanup() {
		window.removeEventListener('pointermove', move);
		window.removeEventListener('pointerup', up);
		window.removeEventListener('pointercancel', cleanup);
		mark(null);
		ghost?.remove();
	}

	function up(ev: PointerEvent) {
		const dragged = ghost !== null;
		cleanup();
		if (!dragged) return;
		const el = target(ev.clientX, ev.clientY);
		if (!el) return;
		const seat = (el as HTMLElement | SVGElement).dataset.seatId;
		drop(seat ?? null);
	}

	window.addEventListener('pointermove', move);
	window.addEventListener('pointerup', up);
	window.addEventListener('pointercancel', cleanup);
}
