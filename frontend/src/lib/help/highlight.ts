import { createLowlight, common } from 'lowlight';

type Node = {
	type: string;
	value?: string;
	properties?: { className?: string[] };
	children?: Node[];
};

const lowlight = createLowlight(common);

function escape(text: string) {
	return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function render(node: Node): string {
	if (node.type === 'text') return escape(node.value ?? '');
	const inner = (node.children ?? []).map(render).join('');
	const classes = (node.properties?.className ?? []).filter((c) => /^hljs-[\w-]+$/.test(c));
	return node.type === 'element' && classes.length
		? `<span class="${classes.join(' ')}">${inner}</span>`
		: inner;
}

/**
 * Syntaxhervorhebung als HTML für die Entwurfs-Vorschau der Lehrkraft. Ausgegeben werden nur
 * escapter Text und Spans mit hljs-Klassen – der Entwurf kann also kein HTML einschleusen.
 */
export function highlight(code: string, taskType?: string | null): string {
	const language = taskType === 'SQL' ? 'sql' : taskType === 'TERMINAL' ? 'bash' : 'python';
	try {
		return render(lowlight.highlight(language, code) as unknown as Node);
	} catch {
		return escape(code);
	}
}
