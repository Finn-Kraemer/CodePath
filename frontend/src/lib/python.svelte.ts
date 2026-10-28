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

	stop() {
		this.worker?.terminate();
		this.worker = null;
	}
}
