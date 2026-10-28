/**
 * Dateisystem-Modell für Terminal-Aufgaben – gleiche Regeln wie TerminalSimulator im Backend.
 * Im Browser zeigt es die Ausgaben an; bewertet wird, was der Server aus der Befehlsfolge nachspielt.
 */
export class TerminalSimulator {
	directories = new Set<string>();
	files = new Map<string, string>();
	cwd: string[] = [];

	constructor(initialDirectories: string[] = [], initialFiles: Record<string, string> = {}) {
		for (const d of initialDirectories) this.mkdirs(normalize(d));
		for (const [path, content] of Object.entries(initialFiles)) {
			const p = normalize(path);
			this.mkdirs(parent(p));
			this.files.set(p, content ?? '');
		}
	}

	prompt(): string {
		return '~' + (this.cwd.length ? '/' + this.cwd.join('/') : '');
	}

	execute(line: string): string {
		const args = tokenize(line);
		if (args.length === 0) return '';
		const [cmd, ...rest] = args;
		const redirect = rest.indexOf('>') >= 0 ? rest.indexOf('>') : rest.indexOf('>>');
		if (cmd === 'echo' && redirect >= 0 && redirect + 1 < rest.length) {
			return this.writeFile(
				rest[redirect + 1],
				rest.slice(0, redirect).join(' ') + '\n',
				rest[redirect] === '>>'
			);
		}
		switch (cmd) {
			case 'pwd':
				return this.prompt();
			case 'ls':
				return this.ls(rest[0] ?? '.');
			case 'cd':
				return this.cd(rest[0] ?? '~');
			case 'mkdir':
				return this.mkdir(rest);
			case 'touch':
				return this.touch(rest);
			case 'rm':
				return this.rm(rest);
			case 'cat':
				return this.cat(rest);
			case 'echo':
				return rest.join(' ');
			case 'mv':
				return this.move(rest, true);
			case 'cp':
				return this.move(rest, false);
			case 'help':
				return 'Befehle: ls, cd, pwd, mkdir [-p], touch, rm [-r], cat, echo "text" > datei, mv, cp, clear';
			case 'clear':
			case 'submit':
				return '';
			default:
				return `${cmd}: Befehl nicht gefunden`;
		}
	}

	private ls(target: string): string {
		const dir = this.resolve(target);
		if (!this.isDirectory(dir)) return `ls: ${target}: nicht gefunden`;
		const prefix = dir === '' ? '' : dir + '/';
		const entries = new Set<string>();
		for (const d of this.directories)
			if (isChild(prefix, d)) entries.add(d.slice(prefix.length) + '/');
		for (const f of this.files.keys()) if (isChild(prefix, f)) entries.add(f.slice(prefix.length));
		return [...entries].sort().join('  ');
	}

	private cd(target: string): string {
		const dir = this.resolve(target);
		if (!this.isDirectory(dir)) return `cd: ${target}: Verzeichnis nicht gefunden`;
		this.cwd = dir === '' ? [] : dir.split('/');
		return '';
	}

	private mkdir(args: string[]): string {
		const parents = args.includes('-p');
		const out: string[] = [];
		for (const arg of args) {
			if (arg.startsWith('-')) continue;
			const path = this.resolve(arg);
			if (path === '' || this.exists(path)) {
				if (!parents) out.push(`mkdir: ${arg}: existiert bereits`);
			} else if (!parents && !this.isDirectory(parent(path))) {
				out.push(`mkdir: ${arg}: übergeordneter Ordner fehlt`);
			} else {
				this.mkdirs(path);
			}
		}
		return out.join('\n');
	}

	private touch(args: string[]): string {
		const out: string[] = [];
		for (const arg of args) {
			const path = this.resolve(arg);
			if (this.isDirectory(path)) continue;
			if (!this.isDirectory(parent(path))) out.push(`touch: ${arg}: Ordner fehlt`);
			else if (!this.files.has(path)) this.files.set(path, '');
		}
		return out.join('\n');
	}

	private rm(args: string[]): string {
		const recursive = args.some((a) => a.startsWith('-') && a.includes('r'));
		const out: string[] = [];
		for (const arg of args) {
			if (arg.startsWith('-')) continue;
			const path = this.resolve(arg);
			if (this.files.delete(path)) continue;
			if (this.isDirectory(path) && path !== '') {
				if (!recursive) {
					out.push(`rm: ${arg}: ist ein Ordner (rm -r verwenden)`);
					continue;
				}
				const prefix = path + '/';
				for (const d of [...this.directories])
					if (d === path || d.startsWith(prefix)) this.directories.delete(d);
				for (const f of [...this.files.keys()]) if (f.startsWith(prefix)) this.files.delete(f);
			} else {
				out.push(`rm: ${arg}: nicht gefunden`);
			}
		}
		return out.join('\n');
	}

	private cat(args: string[]): string {
		return args
			.map((arg) => this.files.get(this.resolve(arg)) ?? `cat: ${arg}: nicht gefunden\n`)
			.join('')
			.trimEnd();
	}

	private writeFile(target: string, text: string, append: boolean): string {
		const path = this.resolve(target);
		if (this.isDirectory(path)) return `echo: ${target}: ist ein Ordner`;
		if (!this.isDirectory(parent(path))) return `echo: ${target}: Ordner fehlt`;
		this.files.set(path, append ? (this.files.get(path) ?? '') + text : text);
		return '';
	}

	private move(args: string[], removeSource: boolean): string {
		const name = removeSource ? 'mv' : 'cp';
		const paths = args.filter((a) => !a.startsWith('-'));
		if (paths.length !== 2) return `${name}: Quelle und Ziel angeben`;
		const source = this.resolve(paths[0]);
		let target = this.resolve(paths[1]);
		if (this.isDirectory(target)) target = (target === '' ? '' : target + '/') + last(source);
		if (this.files.has(source)) {
			if (!this.isDirectory(parent(target))) return `${name}: Zielordner fehlt`;
			this.files.set(target, this.files.get(source)!);
			if (removeSource) this.files.delete(source);
			return '';
		}
		if (this.isDirectory(source) && source !== '') {
			if (!removeSource && !args.includes('-r'))
				return `cp: ${paths[0]}: ist ein Ordner (cp -r verwenden)`;
			if (target === source || target.startsWith(source + '/'))
				return `${name}: Ziel liegt in der Quelle`;
			const from = source + '/';
			for (const d of [...this.directories])
				if (d === source || d.startsWith(from))
					this.directories.add(target + d.slice(source.length));
			for (const [f, c] of [...this.files])
				if (f.startsWith(from)) this.files.set(target + '/' + f.slice(from.length), c);
			if (removeSource) {
				for (const d of [...this.directories])
					if (d === source || d.startsWith(from)) this.directories.delete(d);
				for (const f of [...this.files.keys()]) if (f.startsWith(from)) this.files.delete(f);
			}
			return '';
		}
		return `${name}: ${paths[0]}: nicht gefunden`;
	}

	private mkdirs(path: string) {
		if (path === '') return;
		let current = '';
		for (const part of path.split('/')) {
			current = current ? current + '/' + part : part;
			this.directories.add(current);
		}
	}

	private exists(path: string) {
		return this.isDirectory(path) || this.files.has(path);
	}

	private isDirectory(path: string) {
		return path === '' || this.directories.has(path);
	}

	private resolve(target: string): string {
		let parts: string[] = [];
		let t = target;
		if (t === '~' || t.startsWith('~/') || t.startsWith('/')) t = t.replace(/^~?\/?/, '');
		else parts = [...this.cwd];
		for (const part of t.split('/')) {
			if (part === '' || part === '.') continue;
			if (part === '..') parts.pop();
			else parts.push(part);
		}
		return parts.join('/');
	}
}

function normalize(path: string) {
	return path
		.split('/')
		.filter((p) => p !== '' && p !== '.')
		.join('/');
}

function parent(path: string) {
	const i = path.lastIndexOf('/');
	return i < 0 ? '' : path.slice(0, i);
}

function last(path: string) {
	const i = path.lastIndexOf('/');
	return i < 0 ? path : path.slice(i + 1);
}

function isChild(prefix: string, path: string) {
	return path.startsWith(prefix) && path !== prefix && path.indexOf('/', prefix.length) < 0;
}

/** Zerlegt eine Zeile in Wörter; Anführungszeichen halten Leerzeichen zusammen. */
export function tokenize(line: string): string[] {
	const tokens: string[] = [];
	let current = '';
	let quote: string | null = null;
	let inToken = false;
	for (const c of line.trim()) {
		if (quote) {
			if (c === quote) quote = null;
			else current += c;
		} else if (c === '"' || c === "'") {
			quote = c;
			inToken = true;
		} else if (/\s/.test(c)) {
			if (inToken) {
				tokens.push(current);
				current = '';
				inToken = false;
			}
		} else {
			current += c;
			inToken = true;
		}
	}
	if (inToken) tokens.push(current);
	return tokens;
}
