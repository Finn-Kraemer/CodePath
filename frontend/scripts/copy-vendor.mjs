// Kopiert Monaco und Pyodide aus node_modules nach static/vendor, damit CodePath ohne CDN
// (also auch im Schulnetz ohne Internet) läuft. Läuft automatisch vor dev und build.
import { cpSync, existsSync, mkdirSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const target = join(process.cwd(), 'static', 'vendor');

function packageDir(name) {
	return dirname(require.resolve(`${name}/package.json`));
}

rmSync(target, { recursive: true, force: true });
mkdirSync(target, { recursive: true });

cpSync(join(packageDir('monaco-editor'), 'min', 'vs'), join(target, 'monaco', 'vs'), {
	recursive: true
});

const pyodide = packageDir('pyodide');
mkdirSync(join(target, 'pyodide'), { recursive: true });
for (const file of [
	'pyodide.js',
	'pyodide.asm.js',
	'pyodide.asm.wasm',
	'python_stdlib.zip',
	'pyodide-lock.json'
]) {
	const source = join(pyodide, file);
	if (!existsSync(source)) throw new Error(`Pyodide-Datei fehlt: ${source}`);
	cpSync(source, join(target, 'pyodide', file));
}

console.log('Vendor-Dateien nach static/vendor kopiert');
