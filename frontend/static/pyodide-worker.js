// Python im Browser zum Ausprobieren. Bewertet wird auf dem Server.
// Pyodide ist lokal gebündelt (scripts/copy-vendor.mjs), damit Code-Aufgaben auch ohne Internet laufen.
importScripts('/vendor/pyodide/pyodide.js');

let pyodide;

async function loadPyodideAndPackages() {
	pyodide = await loadPyodide({ indexURL: '/vendor/pyodide/' });
	self.postMessage({ type: 'ready' });
}

const pyodideReadyPromise = loadPyodideAndPackages();

self.onmessage = async (event) => {
	await pyodideReadyPromise;
	const { code, type, stdin } = event.data;
	if (type !== 'run') return;

	try {
		pyodide.globals.set('__codepath_stdin', stdin || '');
		await pyodide.runPythonAsync(`
import sys, io
sys.stdout = io.StringIO()
sys.stdin = io.StringIO(__codepath_stdin)
`);
		await pyodide.runPythonAsync(code);
		const stdout = pyodide.runPython('sys.stdout.getvalue()');
		self.postMessage({ type: 'result', stdout });
	} catch (error) {
		const lines = String(error.message || error).trim().split('\n');
		self.postMessage({ type: 'error', error: lines[lines.length - 1] });
	}
};
