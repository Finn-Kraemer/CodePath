// frontend/static/pyodide-worker.js
// Lokal gebündelt (scripts/copy-vendor.mjs), damit Code-Aufgaben auch ohne Internet laufen
importScripts("/vendor/pyodide/pyodide.js");

let pyodide;

async function loadPyodideAndPackages() {
    pyodide = await loadPyodide({ indexURL: "/vendor/pyodide/" });
    self.postMessage({ type: 'ready' });
}

let pyodideReadyPromise = loadPyodideAndPackages();

self.onmessage = async (event) => {
    await pyodideReadyPromise;
    const { code, type } = event.data;

    if (type === 'run') {
        try {
            await pyodide.runPythonAsync(`
                import sys
                import io
                sys.stdout = io.StringIO()
            `);
            await pyodide.runPythonAsync(code);
            const stdout = pyodide.runPython('sys.stdout.getvalue()');
            self.postMessage({ type: 'result', stdout });
        } catch (error) {
            self.postMessage({ type: 'error', error: error.message });
        }
    }
};
