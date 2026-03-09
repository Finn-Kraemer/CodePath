// frontend/static/pyodide-worker.js
importScripts("https://cdn.jsdelivr.net/pyodide/v0.25.0/full/pyodide.js");

let pyodide;

async function loadPyodideAndPackages() {
    pyodide = await loadPyodide();
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
