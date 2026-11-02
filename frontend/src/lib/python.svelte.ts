import type { Trace } from '$lib/visualizer/trace';

/**
 * Führt Python im Browser aus (Pyodide im Web Worker). Dient nur zum Ausprobieren –
 * bewertet wird auf dem Server.
 */
export class PythonRunner {
	private worker: Worker | null = null;
	ready = $state(false);

	start() {
		this.worker?.terminate();
		this.ready = false;
		this.worker = new Worker('/pyodide-worker.js');
		this.worker.addEventListener('message', (event) => {
			if (event.data.type === 'ready') this.ready = true;
		});
	}

	run(code: string, stdin = '', timeoutMs = 5000): Promise<{ stdout: string; error?: string }> {
		if (!this.worker) this.start();
		const worker = this.worker!;
		return new Promise((resolve) => {
			const timer = setTimeout(() => {
				// Endlosschleife: Worker beenden und neu starten
				worker.removeEventListener('message', onMessage);
				this.start();
				resolve({ stdout: '', error: 'Zeitlimit überschritten – gibt es eine Endlosschleife?' });
			}, timeoutMs);
			const onMessage = (event: MessageEvent) => {
				if (event.data.type !== 'result' && event.data.type !== 'error') return;
				clearTimeout(timer);
				worker.removeEventListener('message', onMessage);
				resolve(
					event.data.type === 'error'
						? { stdout: '', error: event.data.error }
						: { stdout: event.data.stdout }
				);
			};
			worker.addEventListener('message', onMessage);
			worker.postMessage({ type: 'run', code, stdin });
		});
	}

	/**
	 * Zeichnet die Ausführung Schritt für Schritt auf (Code-Visualizer). Endlosschleifen beendet das
	 * Schrittlimit im Tracer; hängt der Worker trotzdem (z. B. `except BaseException` um eine
	 * Endlosschleife), wird er nach `timeoutMs` neu gestartet.
	 */
	trace(
		code: string,
		stdin = '',
		limits: { maxSteps?: number; maxSeconds?: number } = {},
		timeoutMs = 15_000
	): Promise<Trace> {
		if (!this.worker) this.start();
		const worker = this.worker!;
		return new Promise((resolve) => {
			const timer = setTimeout(() => {
				worker.removeEventListener('message', onMessage);
				this.start();
				resolve({
					steps: [],
					stdout: '',
					stopped: 'time',
					error: 'Zeitlimit überschritten – gibt es eine Endlosschleife?'
				});
			}, timeoutMs);
			const onMessage = (event: MessageEvent) => {
				if (event.data.type !== 'trace' && event.data.type !== 'error') return;
				clearTimeout(timer);
				worker.removeEventListener('message', onMessage);
				if (event.data.type === 'error') {
					resolve({ steps: [], stdout: '', stopped: null, error: event.data.error });
					return;
				}
				const { steps, stdout, stopped, error } = event.data;
				resolve({ steps, stdout, stopped, error });
			};
			worker.addEventListener('message', onMessage);
			worker.postMessage({ type: 'trace', code, stdin, limits });
		});
	}

	stop() {
		this.worker?.terminate();
		this.worker = null;
	}
}
