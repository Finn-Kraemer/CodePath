// Python im Browser zum Ausprobieren. Bewertet wird auf dem Server.
// Pyodide ist lokal gebündelt (scripts/copy-vendor.mjs), damit Code-Aufgaben auch ohne Internet laufen.
importScripts('/vendor/pyodide/pyodide.js');

let pyodide;

async function loadPyodideAndPackages() {
	pyodide = await loadPyodide({ indexURL: '/vendor/pyodide/' });
	self.postMessage({ type: 'ready' });
}

const pyodideReadyPromise = loadPyodideAndPackages();

// Code-Visualizer: zeichnet die Ausführung Schritt für Schritt auf (sys.settrace).
// Verfolgt wird nur der Schülercode (Dateiname <student>), nie dieser Tracer oder Bibliotheken.
// Reines Lernwerkzeug: Ergebnis bleibt im Browser, nichts wird bewertet oder gespeichert.
// TRACER-BEGIN
const TRACER = `
import sys, io, json, time, types

class _CodepathStop(BaseException):
    pass

def _codepath_trace(code, stdin, max_steps, max_seconds, max_items, max_depth):
    FILE = '<student>'
    steps = []
    out = io.StringIO()
    state = {'stopped': None, 'deadline': time.monotonic() + max_seconds}

    def short(text, limit=80):
        return text if len(text) <= limit else text[:limit - 1] + '…'

    def encode(value, heap, depth):
        if value is None or isinstance(value, (bool, int, float, str)):
            t = type(value).__name__
            if t == 'NoneType':
                t = 'None'
            return {'k': 'p', 't': t, 'r': short(repr(value))}
        if isinstance(value, (list, tuple, set, frozenset, dict)):
            ref = str(id(value))
            if ref not in heap:
                if depth >= max_depth:
                    return {'k': 'o', 't': type(value).__name__, 'r': '…'}
                kind = type(value).__name__
                entry = {'k': kind, 'items': [], 'more': 0}
                heap[ref] = entry
                if isinstance(value, dict):
                    pairs = list(value.items())
                    for key, item in pairs[:max_items]:
                        entry['items'].append([encode(key, heap, depth + 1), encode(item, heap, depth + 1)])
                    entry['more'] = max(0, len(pairs) - max_items)
                else:
                    items = list(value)
                    if isinstance(value, (set, frozenset)):
                        try:
                            items = sorted(items)
                        except Exception:
                            pass
                    for item in items[:max_items]:
                        entry['items'].append(encode(item, heap, depth + 1))
                    entry['more'] = max(0, len(items) - max_items)
            return {'k': 'ref', 'id': ref}
        if isinstance(value, types.FunctionType):
            return {'k': 'fn', 'r': 'Funktion ' + value.__name__}
        if isinstance(value, type):
            return {'k': 'o', 't': 'class', 'r': 'Klasse ' + value.__name__}
        try:
            text = repr(value)
        except Exception:
            text = '<' + type(value).__name__ + '>'
        return {'k': 'o', 't': type(value).__name__, 'r': short(text)}

    def visible(name, value):
        if name.startswith('__') or name.startswith('_codepath'):
            return False
        return not isinstance(value, types.ModuleType)

    def record(frame, event, arg):
        if len(steps) >= max_steps:
            state['stopped'] = 'steps'
            raise _CodepathStop()
        if time.monotonic() > state['deadline']:
            state['stopped'] = 'time'
            raise _CodepathStop()
        chain = []
        f = frame
        while f is not None and f.f_code.co_filename == FILE:
            chain.append(f)
            f = f.f_back
        chain.reverse()
        heap = {}
        stack = []
        for f in chain:
            name = f.f_code.co_name
            local_vars = {}
            for key, value in list(f.f_locals.items()):
                if visible(key, value):
                    local_vars[key] = encode(value, heap, 0)
            stack.append({'func': name, 'line': f.f_lineno, 'locals': local_vars})
        step = {'line': frame.f_lineno, 'event': event, 'stack': stack, 'heap': heap,
                'out': len(out.getvalue())}
        if event == 'return' and frame.f_code.co_name != '<module>':
            step['returnValue'] = encode(arg, heap, 0)
        if event == 'exception':
            step['exception'] = arg[0].__name__ + ': ' + short(str(arg[1]), 200)
        steps.append(step)

    def local_trace(frame, event, arg):
        if event in ('line', 'return', 'exception'):
            record(frame, event, arg)
        return local_trace

    def global_trace(frame, event, arg):
        if frame.f_code.co_filename != FILE:
            return None
        # Der Start des Hauptprogramms ist kein eigener Schritt (Zeile 0)
        if frame.f_code.co_name != '<module>':
            record(frame, event, arg)
        return local_trace

    error = None
    try:
        compiled = compile(code, FILE, 'exec')
    except SyntaxError as e:
        return json.dumps({'steps': [], 'stdout': '', 'stopped': None,
                           'error': 'SyntaxError in Zeile ' + str(e.lineno) + ': ' + str(e.msg)})
    old_out, old_in = sys.stdout, sys.stdin
    sys.stdout = out
    sys.stdin = io.StringIO(stdin)
    namespace = {'__name__': '__main__'}
    try:
        sys.settrace(global_trace)
        exec(compiled, namespace)
    except _CodepathStop:
        pass
    except EOFError:
        error = 'input() wartet auf eine Eingabe, aber es sind keine Eingaben mehr übrig. Trage weitere Eingaben ein.'
    except BaseException as e:
        error = type(e).__name__ + ': ' + short(str(e), 200)
    finally:
        sys.settrace(None)
        sys.stdout, sys.stdin = old_out, old_in
    return json.dumps({'steps': steps, 'stdout': out.getvalue(), 'stopped': state['stopped'], 'error': error})
`;
// TRACER-END

let tracerLoaded = false;

async function runTrace({ code, stdin, limits }) {
	if (!tracerLoaded) {
		pyodide.runPython(TRACER);
		tracerLoaded = true;
	}
	const maxSteps = Math.max(1, Math.min(limits?.maxSteps ?? 1000, 5000));
	const maxSeconds = Math.max(1, Math.min(limits?.maxSeconds ?? 5, 20));
	const trace = pyodide.globals.get('_codepath_trace');
	try {
		const json = trace(code, stdin || '', maxSteps, maxSeconds, 50, 3);
		return JSON.parse(json);
	} finally {
		trace.destroy();
	}
}

self.onmessage = async (event) => {
	await pyodideReadyPromise;
	const { code, type, stdin } = event.data;

	if (type === 'trace') {
		try {
			const result = await runTrace(event.data);
			self.postMessage({ type: 'trace', ...result });
		} catch (error) {
			const lines = String(error.message || error).trim().split('\n');
			self.postMessage({ type: 'error', error: lines[lines.length - 1] });
		}
		return;
	}
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
