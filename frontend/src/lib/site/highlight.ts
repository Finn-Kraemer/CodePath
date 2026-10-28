/** Suchwörter wie im Backend: klein, mindestens zwei Zeichen, ohne Doppelte. */
export function searchTerms(query: string): string[] {
	return [
		...new Set(
			query
				.toLowerCase()
				.split(/\s+/)
				.filter((t) => t.length >= 2)
		)
	];
}

/** Zerlegt einen Text in Abschnitte mit und ohne Treffer – zum Hervorheben ohne {@html}. */
export function segments(text: string, terms: string[]): { text: string; match: boolean }[] {
	if (!terms.length) return [{ text, match: false }];
	const pattern = new RegExp(`(${terms.map(escape).join('|')})`, 'gi');
	return text
		.split(pattern)
		.filter((part) => part !== '')
		.map((part) => ({ text: part, match: terms.includes(part.toLowerCase()) }));
}

const MARK_CLASS = 'search-hit';

/** Markiert Treffer im gerenderten Artikel (nur Textknoten) und liefert die erste Markierung. */
export function highlight(container: HTMLElement, terms: string[]): HTMLElement | null {
	clearHighlights(container);
	if (!terms.length) return null;
	const pattern = new RegExp(terms.map(escape).join('|'), 'gi');
	const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
	const nodes: Text[] = [];
	while (walker.nextNode()) nodes.push(walker.currentNode as Text);
	let first: HTMLElement | null = null;
	for (const node of nodes) {
		const text = node.data;
		pattern.lastIndex = 0;
		if (!pattern.test(text)) continue;
		const fragment = document.createDocumentFragment();
		let last = 0;
		pattern.lastIndex = 0;
		for (const m of text.matchAll(pattern)) {
			const at = m.index ?? 0;
			fragment.append(text.slice(last, at));
			const mark = document.createElement('mark');
			mark.className = MARK_CLASS;
			mark.textContent = m[0];
			fragment.append(mark);
			first ??= mark;
			last = at + m[0].length;
		}
		fragment.append(text.slice(last));
		node.replaceWith(fragment);
	}
	return first;
}

export function clearHighlights(container: HTMLElement) {
	for (const mark of container.querySelectorAll(`mark.${MARK_CLASS}`)) {
		mark.replaceWith(document.createTextNode(mark.textContent ?? ''));
	}
	container.normalize();
}

function escape(term: string): string {
	return term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
