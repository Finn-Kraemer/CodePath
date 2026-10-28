import { GitSimulator, type GitSetup } from './git';
import { HELP, MAN } from './terminal-help';

/**
 * Dateisystem-Modell mit Shell für Terminal-Aufgaben – gleiche Regeln wie TerminalSimulator im Backend.
 * Im Browser zeigt es die Ausgaben an; bewertet wird, was der Server aus der Befehlsfolge nachspielt.
 * Beide Modelle prüfen dieselben Fälle (terminal-cases.json).
 */
export const MAX_FILES = 500;
export const MAX_FILE_SIZE = 20_000;
export const MAX_OUTPUT = 50_000;

export interface Out {
	out: string;
	err: string;
}

const ok = (out: string): Out => ({ out, err: '' });
const fail = (err: string): Out => ({ out: '', err });

interface Token {
	text: string;
	op: boolean;
}

export class TerminalSimulator {
	directories = new Set<string>();
	files = new Map<string, string>();
	cwd: string[] = [];
	history: string[] = [];
	git: GitSimulator = new GitSimulator(this);

	constructor(
		initialDirectories: string[] = [],
		initialFiles: Record<string, string> = {},
		gitSetup?: GitSetup | null
	) {
		for (const d of initialDirectories) this.mkdirs(normalize(d));
		for (const [path, content] of Object.entries(initialFiles)) {
			const p = normalize(path);
			this.mkdirs(parent(p));
			this.files.set(p, content ?? '');
		}
		if (gitSetup && Object.keys(gitSetup).length > 0) this.git.setup(gitSetup);
	}

	prompt(): string {
		return '~' + (this.cwd.length ? '/' + this.cwd.join('/') : '');
	}

	/** Führt eine Befehlszeile aus (mit | und > / >>) und gibt die Bildschirmausgabe zurück. */
	execute(line: string): string {
		this.history.push(line);
		const tokens = lex(line);
		if (tokens.length === 0) return '';

		const segments: string[][] = [];
		let current: string[] = [];
		let target: string | null = null;
		let append = false;
		for (let i = 0; i < tokens.length; i++) {
			const t = tokens[i];
			if (!t.op) {
				current.push(t.text);
			} else if (t.text === '|') {
				if (current.length === 0) return 'Syntaxfehler: unerwartetes |';
				segments.push(current);
				current = [];
			} else {
				if (i + 1 >= tokens.length || tokens[i + 1].op)
					return `Syntaxfehler: Dateiname nach ${t.text} fehlt`;
				if (i + 2 < tokens.length) return `Syntaxfehler: ${t.text} gehört ans Ende der Zeile`;
				target = tokens[i + 1].text;
				append = t.text === '>>';
				break;
			}
		}
		if (current.length === 0) return 'Syntaxfehler: Befehl fehlt';
		segments.push(current);

		let stdin: string | null = null;
		const errors: string[] = [];
		for (const segment of segments) {
			const result = this.run(segment, stdin);
			if (result.err) errors.push(result.err);
			stdin = result.out;
		}
		let out = stdin ?? '';
		if (target !== null) {
			const error = this.writeFile(target, out === '' ? '' : out + '\n', append);
			if (error) errors.push(error);
			out = '';
		}
		const screen = errors.map(trimEnd);
		if (out) screen.push(out);
		return screen.join('\n');
	}

	private run(args: string[], stdin: string | null): Out {
		const [cmd, ...rest] = args;
		switch (cmd) {
			case 'pwd':
				return ok(this.prompt());
			case 'ls':
				return this.ls(rest);
			case 'cd':
				return this.cd(rest[0] ?? '~');
			case 'mkdir':
				return this.mkdir(rest);
			case 'touch':
				return this.touch(rest);
			case 'rm':
				return this.rm(rest);
			case 'cat':
				return this.cat(rest, stdin);
			case 'echo':
				return ok(rest.join(' '));
			case 'mv':
				return this.move(rest, true);
			case 'cp':
				return this.move(rest, false);
			case 'head':
				return this.headTail(rest, stdin, true);
			case 'tail':
				return this.headTail(rest, stdin, false);
			case 'grep':
				return this.grep(rest, stdin);
			case 'wc':
				return this.wc(rest, stdin);
			case 'sort':
				return this.sort(rest, stdin);
			case 'uniq':
				return this.uniq(rest, stdin);
			case 'find':
				return this.find(rest);
			case 'history':
				return ok(this.history.map((h, i) => `${pad(String(i + 1), 4)}  ${h}`).join('\n'));
			case 'help':
				return ok(HELP);
			case 'man':
				return this.man(rest);
			case 'git':
				return this.git.run(rest);
			case 'clear':
			case 'submit':
				return ok('');
			default:
				return fail(`${cmd}: Befehl nicht gefunden – "help" zeigt alle Befehle`);
		}
	}

	private ls(args: string[]): Out {
		const all = args.some((a) => a.startsWith('-') && a.includes('a'));
		const targets = args.filter((a) => !a.startsWith('-'));
		const target = targets[0] ?? '.';
		const dir = this.resolve(target);
		if (this.files.has(dir)) return ok(last(dir));
		if (!this.isDirectory(dir)) return fail(`ls: ${target}: nicht gefunden`);
		const prefix = dir === '' ? '' : dir + '/';
		const entries = new Set<string>();
		for (const d of this.directories)
			if (isChild(prefix, d)) entries.add(d.slice(prefix.length) + '/');
		for (const f of this.files.keys()) if (isChild(prefix, f)) entries.add(f.slice(prefix.length));
		if (all && dir === this.git.root) entries.add('.git/');
		return ok(
			[...entries]
				.filter((e) => all || !e.startsWith('.'))
				.sort()
				.join('  ')
		);
	}

	private cd(target: string): Out {
		const dir = this.resolve(target);
		if (!this.isDirectory(dir)) return fail(`cd: ${target}: Verzeichnis nicht gefunden`);
		this.cwd = dir === '' ? [] : dir.split('/');
		return ok('');
	}

	private mkdir(args: string[]): Out {
		const parents = args.includes('-p');
		let err = '';
		for (const arg of args) {
			if (arg.startsWith('-')) continue;
			const path = this.resolve(arg);
			if (path === '' || this.exists(path)) {
				if (!parents) err += `mkdir: ${arg}: existiert bereits\n`;
			} else if (!parents && !this.isDirectory(parent(path))) {
				err += `mkdir: ${arg}: übergeordneter Ordner fehlt\n`;
			} else {
				this.mkdirs(path);
			}
		}
		return fail(err);
	}

	private touch(args: string[]): Out {
		let err = '';
		for (const arg of args) {
			const path = this.resolve(arg);
			if (this.isDirectory(path) || this.files.has(path)) continue;
			if (!this.isDirectory(parent(path))) err += `touch: ${arg}: Ordner fehlt\n`;
			else if (this.files.size >= MAX_FILES) err += 'touch: zu viele Dateien\n';
			else this.files.set(path, '');
		}
		return fail(err);
	}

	private rm(args: string[]): Out {
		const recursive = args.some((a) => a.startsWith('-') && a.includes('r'));
		let err = '';
		for (const arg of args) {
			if (arg.startsWith('-')) continue;
			const path = this.resolve(arg);
			if (this.files.delete(path)) continue;
			if (this.isDirectory(path) && path !== '') {
				if (!recursive) {
					err += `rm: ${arg}: ist ein Ordner (rm -r verwenden)\n`;
					continue;
				}
				const prefix = path + '/';
				for (const d of [...this.directories])
					if (d === path || d.startsWith(prefix)) this.directories.delete(d);
				for (const f of [...this.files.keys()]) if (f.startsWith(prefix)) this.files.delete(f);
			} else {
				err += `rm: ${arg}: nicht gefunden\n`;
			}
		}
		return fail(err);
	}

	private cat(args: string[], stdin: string | null): Out {
		if (args.length === 0) return ok(stdin ?? '');
		let out = '';
		let err = '';
		for (const arg of args) {
			const path = this.resolve(arg);
			const content = this.files.get(path);
			if (content !== undefined) {
				if (out.length + content.length > MAX_OUTPUT) {
					err += 'cat: Ausgabe zu lang\n';
					break;
				}
				out += content;
			} else if (this.isDirectory(path)) {
				err += `cat: ${arg}: ist ein Ordner\n`;
			} else {
				err += `cat: ${arg}: nicht gefunden\n`;
			}
		}
		return { out: trimEnd(out), err };
	}

	/** Text aus Dateien oder – ohne Dateien – aus der Pipe: [Name, Inhalt]. */
	private input(name: string, paths: string[], stdin: string | null) {
		const sources: [string, string][] = [];
		let err = '';
		if (paths.length === 0) {
			if (stdin === null) err += `${name}: Datei angeben\n`;
			else sources.push(['', stdin]);
			return { sources, err };
		}
		for (const arg of paths) {
			const path = this.resolve(arg);
			const content = this.files.get(path);
			if (content !== undefined) sources.push([arg, content]);
			else if (this.isDirectory(path)) err += `${name}: ${arg}: ist ein Ordner\n`;
			else err += `${name}: ${arg}: nicht gefunden\n`;
		}
		return { sources, err };
	}

	private headTail(args: string[], stdin: string | null, head: boolean): Out {
		const name = head ? 'head' : 'tail';
		let count = 10;
		const paths: string[] = [];
		for (let i = 0; i < args.length; i++) {
			const a = args[i];
			if (a === '-n' && i + 1 < args.length) {
				const n = parseCount(args[++i]);
				if (n === null) return fail(`${name}: ungültige Zeilenzahl: ${args[i]}`);
				count = n;
			} else if (a.startsWith('-n') && a.length > 2) {
				const n = parseCount(a.slice(2));
				if (n === null) return fail(`${name}: ungültige Zeilenzahl: ${a.slice(2)}`);
				count = n;
			} else if (a.startsWith('-') && a.length > 1) {
				const n = parseCount(a.slice(1));
				if (n === null) return fail(`${name}: unbekannte Option ${a}`);
				count = n;
			} else {
				paths.push(a);
			}
		}
		const input = this.input(name, paths, stdin);
		const all = input.sources.flatMap(([, text]) => lines(text));
		const picked = head ? all.slice(0, count) : all.slice(Math.max(0, all.length - count));
		return { out: picked.join('\n'), err: input.err };
	}

	private grep(args: string[], stdin: string | null): Out {
		let ignoreCase = false;
		let numbers = false;
		let count = false;
		let invert = false;
		let recursive = false;
		let pattern: string | null = null;
		const paths: string[] = [];
		for (const a of args) {
			if (pattern === null && a.startsWith('-') && a.length > 1) {
				for (const c of a.slice(1)) {
					if (c === 'i') ignoreCase = true;
					else if (c === 'n') numbers = true;
					else if (c === 'c') count = true;
					else if (c === 'v') invert = true;
					else if (c === 'r' || c === 'R') recursive = true;
					else return fail(`grep: unbekannte Option -${c}`);
				}
			} else if (pattern === null) {
				pattern = a;
			} else {
				paths.push(a);
			}
		}
		if (pattern === null) return fail('grep: Suchtext fehlt – z. B. grep ERROR server.log');

		const sources: [string, string][] = [];
		let err = '';
		let showNames: boolean;
		if (recursive) {
			for (const arg of paths.length ? paths : ['.']) {
				const path = this.resolve(arg);
				if (this.files.has(path)) {
					sources.push([arg, this.files.get(path)!]);
				} else if (this.isDirectory(path)) {
					const prefix = path === '' ? '' : path + '/';
					const shown = stripSlash(arg);
					for (const f of [...this.files.keys()].sort())
						if (f.startsWith(prefix))
							sources.push([shown + '/' + f.slice(prefix.length), this.files.get(f)!]);
				} else {
					err += `grep: ${arg}: nicht gefunden\n`;
				}
			}
			showNames = true;
		} else {
			const input = this.input('grep', paths, stdin);
			sources.push(...input.sources);
			err += input.err;
			showNames = paths.length > 1;
		}

		const needle = ignoreCase ? pattern.toLowerCase() : pattern;
		const out: string[] = [];
		let size = 0;
		for (const [name, text] of sources) {
			const prefix = showNames ? name + ':' : '';
			const sourceLines = lines(text);
			let hits = 0;
			for (let i = 0; i < sourceLines.length; i++) {
				const l = sourceLines[i];
				const match = (ignoreCase ? l.toLowerCase() : l).includes(needle) !== invert;
				if (!match) continue;
				hits++;
				if (count) continue;
				const hit = prefix + (numbers ? `${i + 1}:` : '') + l;
				size += hit.length + 1;
				if (size > MAX_OUTPUT) return { out: out.join('\n'), err: err + 'grep: Ausgabe zu lang\n' };
				out.push(hit);
			}
			if (count) out.push(prefix + hits);
		}
		return { out: out.join('\n'), err };
	}

	private wc(args: string[], stdin: string | null): Out {
		let l = false;
		let w = false;
		let c = false;
		const paths: string[] = [];
		for (const a of args) {
			if (a.startsWith('-') && a.length > 1) {
				for (const ch of a.slice(1)) {
					if (ch === 'l') l = true;
					else if (ch === 'w') w = true;
					else if (ch === 'c' || ch === 'm') c = true;
					else return fail(`wc: unbekannte Option -${ch}`);
				}
			} else {
				paths.push(a);
			}
		}
		if (!l && !w && !c) l = w = c = true;
		const input = this.input('wc', paths, stdin);
		const out = input.sources.map(([name, text]) => {
			const parts: string[] = [];
			if (l) parts.push(String(lines(text).length));
			if (w) parts.push(String(words(text)));
			if (c) parts.push(String(text.length));
			if (name) parts.push(name);
			return parts.join(' ');
		});
		return { out: out.join('\n'), err: input.err };
	}

	private sort(args: string[], stdin: string | null): Out {
		const input = this.input(
			'sort',
			args.filter((a) => !a.startsWith('-')),
			stdin
		);
		const all = input.sources.flatMap(([, text]) => lines(text)).sort();
		if (args.includes('-r')) all.reverse();
		return { out: all.join('\n'), err: input.err };
	}

	private uniq(args: string[], stdin: string | null): Out {
		const count = args.includes('-c');
		const input = this.input(
			'uniq',
			args.filter((a) => !a.startsWith('-')),
			stdin
		);
		const all = input.sources.flatMap(([, text]) => lines(text));
		const out: string[] = [];
		let i = 0;
		while (i < all.length) {
			let j = i;
			while (j < all.length && all[j] === all[i]) j++;
			out.push(count ? `${j - i} ${all[i]}` : all[i]);
			i = j;
		}
		return { out: out.join('\n'), err: input.err };
	}

	private find(args: string[]): Out {
		let start = '.';
		let name: string | null = null;
		let type: string | null = null;
		for (let i = 0; i < args.length; i++) {
			const a = args[i];
			if (a === '-name') {
				if (i + 1 >= args.length) return fail(`find: Muster nach ${a} fehlt`);
				name = args[++i];
			} else if (a === '-type') {
				if (i + 1 >= args.length || !['f', 'd'].includes(args[i + 1]))
					return fail('find: -type f (Dateien) oder -type d (Ordner)');
				type = args[++i];
			} else if (a.startsWith('-')) {
				return fail(`find: unbekannte Option ${a}`);
			} else if (i === 0) {
				start = a;
			} else {
				return fail('find: Startordner gehört an den Anfang');
			}
		}
		const dir = this.resolve(start);
		if (!this.isDirectory(dir)) return fail(`find: ${start}: Ordner nicht gefunden`);
		const shown = stripSlash(start);
		const prefix = dir === '' ? '' : dir + '/';
		const out: string[] = [];
		if (type !== 'f' && (name === null || glob(name, last(shown)))) out.push(shown);
		if (type !== 'f')
			for (const d of this.directories)
				if (d.startsWith(prefix) && (name === null || glob(name, last(d))))
					out.push(shown + '/' + d.slice(prefix.length));
		if (type !== 'd')
			for (const f of this.files.keys())
				if (f.startsWith(prefix) && (name === null || glob(name, last(f))))
					out.push(shown + '/' + f.slice(prefix.length));
		return ok(out.sort().join('\n'));
	}

	private man(args: string[]): Out {
		if (args.length === 0) return fail('man: Welcher Befehl? Zum Beispiel: man ls');
		return Object.hasOwn(MAN, args[0]) ? ok(MAN[args[0]]) : fail(`man: Keine Hilfe zu ${args[0]}`);
	}

	// --- Dateisystem-Zugriff (auch für GitSimulator) ---

	writeFile(target: string, text: string, append: boolean): string {
		const path = this.resolve(target);
		if (path === '' || this.isDirectory(path)) return `${target}: ist ein Ordner`;
		if (!this.isDirectory(parent(path))) return `${target}: Ordner fehlt`;
		const content = append && this.files.has(path) ? this.files.get(path)! + text : text;
		if (content.length > MAX_FILE_SIZE) return `${target}: Datei zu groß`;
		if (!this.files.has(path) && this.files.size >= MAX_FILES) return `${target}: zu viele Dateien`;
		this.files.set(path, content);
		return '';
	}

	/** Schreibt eine Datei ohne Prüfungen, legt fehlende Ordner an (für Git). */
	putFile(path: string, content: string) {
		this.mkdirs(parent(path));
		this.files.set(path, content);
	}

	/** Entfernt eine Datei und danach leer gewordene Ordner bis hinauf zu stopAt (für Git). */
	deleteFile(path: string, stopAt: string) {
		this.files.delete(path);
		let dir = parent(path);
		while (dir !== '' && dir !== stopAt && this.isEmptyDirectory(dir)) {
			this.directories.delete(dir);
			dir = parent(dir);
		}
	}

	private isEmptyDirectory(dir: string) {
		const prefix = dir + '/';
		return (
			![...this.directories].some((d) => d.startsWith(prefix)) &&
			![...this.files.keys()].some((f) => f.startsWith(prefix))
		);
	}

	cwdPath(): string {
		return this.cwd.join('/');
	}

	private move(args: string[], removeSource: boolean): Out {
		const name = removeSource ? 'mv' : 'cp';
		const paths = args.filter((a) => !a.startsWith('-'));
		if (paths.length !== 2) return fail(`${name}: Quelle und Ziel angeben`);
		const source = this.resolve(paths[0]);
		let target = this.resolve(paths[1]);
		if (this.isDirectory(target)) target = (target === '' ? '' : target + '/') + last(source);
		if (this.files.has(source)) {
			if (!this.isDirectory(parent(target))) return fail(`${name}: Zielordner fehlt`);
			if (this.isDirectory(target)) return fail(`${name}: ${paths[1]}: ist ein Ordner`);
			if (!this.files.has(target) && !removeSource && this.files.size >= MAX_FILES)
				return fail(`${name}: zu viele Dateien`);
			this.files.set(target, this.files.get(source)!);
			if (removeSource && target !== source) this.files.delete(source);
			return ok('');
		}
		if (this.isDirectory(source) && source !== '') {
			if (!removeSource && !args.includes('-r'))
				return fail(`cp: ${paths[0]}: ist ein Ordner (cp -r verwenden)`);
			if (target === source || target.startsWith(source + '/'))
				return fail(`${name}: Ziel liegt in der Quelle`);
			if (this.files.has(target)) return fail(`${name}: ${paths[1]}: ist eine Datei`);
			const from = source + '/';
			const copied = [...this.files.keys()].filter((f) => f.startsWith(from)).length;
			if (!removeSource && this.files.size + copied > MAX_FILES)
				return fail('cp: zu viele Dateien');
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
			return ok('');
		}
		return fail(`${name}: ${paths[0]}: nicht gefunden`);
	}

	mkdirs(path: string) {
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

	isDirectory(path: string) {
		return path === '' || this.directories.has(path);
	}

	resolve(target: string): string {
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

	/** Alle Befehle, Ordner und Dateien, die zum angefangenen Wort passen (für die Tab-Taste). */
	complete(input: string): string | null {
		const match = /^(.*?)([^\s|>]*)$/.exec(input)!;
		const before = match[1];
		const word = match[2];
		let candidates: string[];
		if (before.trim() === '' || /[|]\s*$/.test(before)) {
			candidates = [...COMMANDS].filter((c) => c.startsWith(word));
		} else {
			const slash = word.lastIndexOf('/');
			const dirPart = slash >= 0 ? word.slice(0, slash + 1) : '';
			const namePart = word.slice(slash + 1);
			const dir = this.resolve(dirPart === '' ? '.' : dirPart);
			if (!this.isDirectory(dir)) return null;
			const prefix = dir === '' ? '' : dir + '/';
			const names = [
				...[...this.directories].filter((d) => isChild(prefix, d)).map((d) => last(d) + '/'),
				...[...this.files.keys()].filter((f) => isChild(prefix, f)).map((f) => last(f))
			];
			candidates = names
				.filter((n) => n.startsWith(namePart) && (namePart.startsWith('.') || !n.startsWith('.')))
				.map((n) => dirPart + n);
		}
		if (candidates.length === 0) return null;
		let common = candidates[0];
		for (const c of candidates) while (!c.startsWith(common)) common = common.slice(0, -1);
		if (common.length <= word.length) return null;
		const done = candidates.length === 1 && !common.endsWith('/');
		return before + common + (done ? ' ' : '');
	}
}

const COMMANDS = [
	'cat',
	'cd',
	'clear',
	'cp',
	'echo',
	'find',
	'git',
	'grep',
	'head',
	'help',
	'history',
	'ls',
	'man',
	'mkdir',
	'mv',
	'pwd',
	'rm',
	'sort',
	'tail',
	'touch',
	'uniq',
	'wc'
];

export function normalize(path: string) {
	return path
		.split('/')
		.filter((p) => p !== '' && p !== '.')
		.join('/');
}

export function parent(path: string) {
	const i = path.lastIndexOf('/');
	return i < 0 ? '' : path.slice(0, i);
}

function last(path: string) {
	const i = path.lastIndexOf('/');
	return i < 0 ? path : path.slice(i + 1);
}

function stripSlash(path: string) {
	let p = path;
	while (p.length > 1 && p.endsWith('/')) p = p.slice(0, -1);
	return p;
}

function isChild(prefix: string, path: string) {
	return path.startsWith(prefix) && path !== prefix && path.indexOf('/', prefix.length) < 0;
}

function parseCount(s: string): number | null {
	return /^[0-9]{1,6}$/.test(s) ? Number(s) : null;
}

/** Zeilen eines Texts; ein abschließender Zeilenumbruch erzeugt keine leere Zeile. */
export function lines(text: string): string[] {
	if (text === '') return [];
	return (text.endsWith('\n') ? text.slice(0, -1) : text).split('\n');
}

function words(text: string) {
	let count = 0;
	let inWord = false;
	for (let i = 0; i < text.length; i++) {
		const space = isSpace(text[i]);
		if (!space && !inWord) count++;
		inWord = !space;
	}
	return count;
}

export function isSpace(c: string) {
	return c === ' ' || c === '\t' || c === '\n' || c === '\r';
}

export function trimEnd(s: string) {
	let end = s.length;
	while (end > 0 && isSpace(s[end - 1])) end--;
	return s.slice(0, end);
}

function pad(s: string, width: number) {
	return s.length >= width ? s : ' '.repeat(width - s.length) + s;
}

/** Platzhalter * (beliebig viele Zeichen außer /) und ? (genau ein Zeichen außer /). */
export function glob(pattern: string, text: string): boolean {
	const ps = pattern.split('/');
	const ts = text.split('/');
	return ps.length === ts.length && ps.every((p, i) => globSegment(p, ts[i]));
}

/** Ohne Rekursion: Bei einem Fehlschlag springt nur der letzte * weiter – auch "*a*a*a*b" bleibt schnell. */
function globSegment(p: string, t: string) {
	let pi = 0;
	let ti = 0;
	let star = -1;
	let mark = 0;
	while (ti < t.length) {
		if (pi < p.length && (p[pi] === '?' || p[pi] === t[ti])) {
			pi++;
			ti++;
		} else if (pi < p.length && p[pi] === '*') {
			star = pi++;
			mark = ti;
		} else if (star >= 0) {
			pi = star + 1;
			ti = ++mark;
		} else {
			return false;
		}
	}
	while (pi < p.length && p[pi] === '*') pi++;
	return pi === p.length;
}

/** Zerlegt eine Zeile in Wörter; Anführungszeichen halten Leerzeichen zusammen. */
export function tokenize(line: string): string[] {
	return lex(line).map((t) => t.text);
}

/** Wie tokenize, erkennt aber |, > und >> außerhalb von Anführungszeichen als Operatoren. */
function lex(line: string): Token[] {
	const tokens: Token[] = [];
	let current = '';
	let quote: string | null = null;
	let inToken = false;
	for (let i = 0; i < line.length; i++) {
		const c = line[i];
		if (quote) {
			if (c === quote) quote = null;
			else current += c;
		} else if (c === '"' || c === "'") {
			quote = c;
			inToken = true;
		} else if (isSpace(c) || c === '|' || c === '>') {
			if (inToken) {
				tokens.push({ text: current, op: false });
				current = '';
				inToken = false;
			}
			if (c === '|') {
				tokens.push({ text: '|', op: true });
			} else if (c === '>') {
				const twice = line[i + 1] === '>';
				tokens.push({ text: twice ? '>>' : '>', op: true });
				if (twice) i++;
			}
		} else {
			current += c;
			inToken = true;
		}
	}
	if (inToken) tokens.push({ text: current, op: false });
	return tokens;
}
