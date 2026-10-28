// Monaco liegt lokal unter /vendor/monaco (scripts/copy-vendor.mjs) – kein CDN.
let loading: Promise<any> | null = null;

export function loadMonaco(): Promise<any> {
	const w = window as any;
	if (w.monaco) return Promise.resolve(w.monaco);
	if (loading) return loading;
	loading = new Promise((resolve, reject) => {
		const script = document.createElement('script');
		script.src = '/vendor/monaco/vs/loader.js';
		script.onload = () => {
			w.require.config({ paths: { vs: '/vendor/monaco/vs' } });
			w.require(['vs/editor/editor.main'], () => resolve(w.monaco), reject);
		};
		script.onerror = reject;
		document.head.appendChild(script);
	});
	return loading;
}

export interface EditorOptions {
	value: string;
	language: 'python' | 'sql';
	readOnly?: boolean;
	onChange?: (value: string) => void;
}

export async function createEditor(element: HTMLElement, options: EditorOptions) {
	const monaco = await loadMonaco();
	const editor = monaco.editor.create(element, {
		value: options.value,
		language: options.language,
		theme: 'vs-dark',
		minimap: { enabled: false },
		fontSize: 14,
		automaticLayout: true,
		readOnly: options.readOnly ?? false,
		scrollBeyondLastLine: false
	});
	if (options.onChange) editor.onDidChangeModelContent(() => options.onChange!(editor.getValue()));
	return editor;
}
