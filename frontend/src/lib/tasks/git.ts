import {
	glob,
	isSpace,
	lines,
	MAX_OUTPUT,
	normalize,
	trimEnd,
	type Out,
	type TerminalSimulator
} from './terminal';

/**
 * Vereinfachtes Git für Terminal-Aufgaben – gleiche Regeln wie GitSimulator im Backend.
 * Commit-Kennungen sind ein Hash über Inhalt und Reihenfolge und damit auf Server und im Browser gleich.
 */
export interface Commit {
	id: string;
	message: string;
	author: string;
	parents: string[];
	tree: Map<string, string>;
	seq: number;
}

export interface GitCommitSetup {
	message?: string;
	author?: string;
	branch?: string;
	files?: Record<string, string | null>;
}

export interface GitSetup {
	root?: string;
	history?: GitCommitSetup[];
	head?: string;
	remote?: boolean | GitCommitSetup[];
}

interface Merge {
	other: string;
	label: string;
	all: Set<string>;
	unresolved: Set<string>;
}

const ok = (out: string): Out => ({ out, err: '' });
const fail = (err: string): Out => ({ out: '', err });

export const GIT_HELP = [
	'Git-Befehle (vereinfacht):',
	'  git init                  Repository anlegen',
	'  git status                Was ist neu, geändert, vorgemerkt?',
	'  git add datei | .         Änderungen für den nächsten Commit vormerken',
	'  git commit -m "text"      Vorgemerktes als Commit speichern',
	'  git log [--oneline]       Verlauf ansehen',
	'  git show [commit]         Was hat ein Commit geändert?',
	'  git diff [--staged]       Unterschiede anzeigen',
	'  git restore datei         Datei auf den letzten Stand zurücksetzen',
	'  git restore --staged datei  Vormerkung zurücknehmen',
	'  git branch [name]         Branches zeigen oder anlegen',
	'  git switch [-c] name      Branch wechseln (-c: neu anlegen)',
	'  git merge name            Branch in den aktuellen holen',
	'  git checkout --ours/--theirs datei   Konflikt: eine Seite nehmen',
	'  git pull / git push       mit dem Server (origin) abgleichen',
	'  git tag name              Version markieren',
	'  git rm [--cached] datei   Datei aus Git entfernen'
].join('\n');

const MAX_IGNORE_LINES = 200;
/** Rechenbudget für den Zeilenvergleich je Diff (Zeilen alt × Zeilen neu, über alle Dateien). */
const DIFF_BUDGET = 250_000;
const NO_REMOTE = 'fatal: Es ist kein Server (origin) eingerichtet.';
const MERGING =
	'Fehler: Du steckst mitten in einer Zusammenführung. Erst Konflikte lösen und committen (oder abbrechen: git merge --abort).';
const DIRTY =
	'Fehler: Du hast Änderungen, die noch nicht committet sind.\nCommitte sie zuerst (git add, git commit) – oder verwirf sie mit: git restore <Datei>';

export class GitSimulator {
	/** Ordner des Repositorys relativ zum Home-Verzeichnis ("" = Home), null = kein Repository. */
	root: string | null = null;
	commits = new Map<string, Commit>();
	branches = new Map<string, string>();
	head = 'main';
	index = new Map<string, string>();
	remote: Map<string, string> | null = null;
	tags = new Map<string, string>();
	private merge: Merge | null = null;
	private seq = 0;
	private author = 'Du';
	private fs: TerminalSimulator;

	constructor(fs: TerminalSimulator) {
		this.fs = fs;
	}

	get merging() {
		return this.merge !== null;
	}

	// --- Vorbereitetes Repository (Config "git") ---

	setup(config: GitSetup) {
		if (typeof config.root !== 'string') return;
		this.root = normalize(config.root);
		this.fs.mkdirs(this.root);
		let current = 'main';
		for (const entry of objects(config.history))
			current = this.commitOnto(this.branches, entry, current);
		this.head =
			typeof config.head === 'string'
				? config.head
				: this.branches.has('main') || this.branches.size === 0
					? 'main'
					: current;
		const tree = this.headTree();
		for (const p of sortedKeys(tree))
			if (!this.fs.files.has(this.fsPath(p))) this.fs.putFile(this.fsPath(p), tree.get(p)!);
		this.index = new Map(tree);
		if (config.remote === true || Array.isArray(config.remote)) {
			this.remote = new Map(this.branches);
			let remoteCurrent = 'main';
			for (const entry of objects(config.remote))
				remoteCurrent = this.commitOnto(this.remote, entry, remoteCurrent);
		}
	}

	private commitOnto(refs: Map<string, string>, entry: GitCommitSetup, current: string) {
		const target = typeof entry.branch === 'string' ? entry.branch : current;
		if (!refs.has(target) && refs.get(current) !== undefined) refs.set(target, refs.get(current)!);
		const tip = refs.get(target);
		const tree =
			tip === undefined ? new Map<string, string>() : new Map(this.commits.get(tip)!.tree);
		const files = entry.files && typeof entry.files === 'object' ? entry.files : {};
		for (const [path, content] of Object.entries(files)) {
			const p = normalize(path);
			if (content === null) tree.delete(p);
			else tree.set(p, String(content));
		}
		const c = this.newCommit(
			typeof entry.message === 'string' ? entry.message : 'Änderung',
			typeof entry.author === 'string' ? entry.author : 'Team',
			tip === undefined ? [] : [tip],
			tree
		);
		refs.set(target, c.id);
		return target;
	}

	// --- Befehle ---

	run(args: string[]): Out {
		if (args.length === 0 || args[0] === 'help' || args[0] === '--help') return ok(GIT_HELP);
		const [sub, ...rest] = args;
		if (sub === 'init') return this.init();
		if (sub === '--version') return ok('git version 2.47.0 (CodePath-Simulation)');
		if (sub === 'config') return this.config(rest);
		if (this.root === null)
			return fail('fatal: Hier ist kein Git-Repository. Lege eines an mit: git init');
		if (!this.inRepo())
			return fail(
				`fatal: Hier ist kein Git-Repository. Das Repository liegt in ${display(this.root)} – wechsle mit cd dorthin.`
			);
		switch (sub) {
			case 'status':
				return this.status();
			case 'add':
				return this.add(rest);
			case 'commit':
				return this.commit(rest);
			case 'log':
				return this.log(rest);
			case 'show':
				return this.show(rest);
			case 'diff':
				return this.diff(rest);
			case 'restore':
				return this.restore(rest);
			case 'rm':
				return this.rm(rest);
			case 'branch':
				return this.branch(rest);
			case 'switch':
				return this.switchCommand(rest);
			case 'checkout':
				return this.checkout(rest);
			case 'merge':
				return this.mergeCommand(rest);
			case 'push':
				return this.push(rest);
			case 'pull':
				return this.pull(rest);
			case 'fetch':
				return this.remote === null ? fail(NO_REMOTE) : ok('Von origin');
			case 'remote':
				return this.remote === null
					? ok('')
					: ok(rest.includes('-v') ? 'origin\tgit@schulserver:team/projekt.git' : 'origin');
			case 'tag':
				return this.tag(rest);
			default:
				return fail(`git: '${sub}' ist kein Git-Befehl. Siehe: git help`);
		}
	}

	private init(): Out {
		const here = this.fs.cwdPath();
		if (this.root !== null) {
			if (this.root === here) return ok(`Das Repository in ${display(this.root)} gibt es schon.`);
			return fail(`Fehler: Es gibt schon ein Repository in ${display(this.root)} – eins reicht.`);
		}
		this.root = here;
		this.head = 'main';
		return ok(`Leeres Git-Repository in ${display(this.root)}/.git/ angelegt`);
	}

	private config(args: string[]): Out {
		const values = args.filter((a) => !a.startsWith('--'));
		if (values.length === 0) return fail('Beispiel: git config user.name "Dein Name"');
		if (values[0] !== 'user.name') return ok('');
		if (values.length === 1) return ok(this.author);
		if (values[1].trim() === '') return fail('Fehler: Der Name ist leer.');
		this.author = values[1];
		return ok('');
	}

	private status(): Out {
		const blocks: string[] = [];
		let first = 'Auf Branch ' + this.head;
		const headId = this.headId();
		const upstream = this.remote?.get(this.head);
		if (this.remote !== null && headId !== undefined && upstream !== undefined)
			first += '\n' + this.tracking(headId, upstream);
		blocks.push(first);
		if (headId === undefined) blocks.push('Noch keine Commits');

		const work = this.working();
		const headTree = this.headTree();
		const conflicts = this.merge === null ? new Set<string>() : this.merge.unresolved;
		if (conflicts.size > 0) {
			const ls = ['Nicht zusammengeführte Pfade:', '  (Konflikt lösen, dann: git add <Datei>)'];
			for (const p of [...conflicts].sort()) ls.push(entry('beide geändert', p));
			blocks.push(ls.join('\n'));
		} else if (this.merge !== null) {
			blocks.push('Alle Konflikte gelöst – schließe die Zusammenführung ab mit: git commit');
		}

		const staged: string[] = [];
		for (const p of sortedKeys(this.index, headTree)) {
			if (conflicts.has(p)) continue;
			const i = this.index.get(p);
			const h = headTree.get(p);
			if (i === h) continue;
			staged.push(
				entry(h === undefined ? 'neue Datei' : i === undefined ? 'gelöscht' : 'geändert', p)
			);
		}
		const unstaged: string[] = [];
		for (const p of sortedKeys(this.index)) {
			if (conflicts.has(p)) continue;
			const w = work.get(p);
			if (w === undefined) unstaged.push(entry('gelöscht', p));
			else if (w !== this.index.get(p)) unstaged.push(entry('geändert', p));
		}
		const untracked: string[] = [];
		for (const p of sortedKeys(work))
			if (!this.index.has(p) && !conflicts.has(p) && !this.ignored(p)) untracked.push('\t' + p);

		if (staged.length)
			blocks.push(
				'Zum Commit vorgemerkte Änderungen:\n  (zurücknehmen mit: git restore --staged <Datei>)\n' +
					staged.join('\n')
			);
		if (unstaged.length)
			blocks.push(
				'Änderungen, die nicht zum Commit vorgemerkt sind:\n  (vormerken mit: git add <Datei>)\n' +
					unstaged.join('\n')
			);
		if (untracked.length)
			blocks.push(
				'Unversionierte Dateien:\n  (vormerken mit: git add <Datei>)\n' + untracked.join('\n')
			);
		if (this.merge === null) {
			if (staged.length === 0 && (unstaged.length > 0 || untracked.length > 0)) {
				blocks.push('keine Änderungen zum Commit vorgemerkt (benutze: git add)');
			} else if (staged.length === 0) {
				blocks.push(
					headId === undefined
						? 'nichts zu committen (lege Dateien an und merke sie vor mit: git add)'
						: 'nichts zu committen, Arbeitsverzeichnis sauber'
				);
			}
		}
		return ok(blocks.join('\n\n'));
	}

	private tracking(local: string, upstream: string) {
		const ahead = count(this.reachable([local]), this.reachable([upstream]));
		const behind = count(this.reachable([upstream]), this.reachable([local]));
		const ref = `'origin/${this.head}'`;
		if (ahead === 0 && behind === 0) return `Dein Branch ist auf demselben Stand wie ${ref}.`;
		if (behind === 0)
			return `Dein Branch ist ${plural(ahead, 'Commit', 'Commits')} vor ${ref}. (hochladen mit: git push)`;
		if (ahead === 0)
			return `Dein Branch ist ${plural(behind, 'Commit', 'Commits')} hinter ${ref}. (holen mit: git pull)`;
		return `Dein Branch und ${ref} sind auseinandergelaufen. (zusammenführen mit: git pull)`;
	}

	private add(args: string[]): Out {
		if (args.length === 0)
			return fail(
				'Was soll vorgemerkt werden? Beispiel: git add index.html (oder git add . für alles)'
			);
		const work = this.working();
		let err = '';
		for (const arg of args) {
			const all = arg === '-A' || arg === '--all';
			if (arg.startsWith('-') && !all) {
				err += `git add: unbekannte Option ${arg}\n`;
				continue;
			}
			const spec = all ? '' : this.repoPath(arg);
			if (spec === null) {
				err += `fatal: ${arg} liegt außerhalb des Repositorys\n`;
				continue;
			}
			const candidates = matching(spec, work.keys(), this.index.keys());
			if (candidates.length === 0) {
				err += `fatal: Pfad '${arg}' passt zu keiner Datei\n`;
				continue;
			}
			for (const p of candidates) {
				const content = work.get(p);
				if (content === undefined) {
					this.index.delete(p);
				} else if (!this.index.has(p) && this.ignored(p)) {
					if (p === spec)
						err += `Die Datei ${arg} steht in .gitignore und wird nicht vorgemerkt.\n`;
					continue;
				} else {
					this.index.set(p, content);
				}
				this.merge?.unresolved.delete(p);
			}
		}
		return fail(err);
	}

	private commit(args: string[]): Out {
		let all = false;
		let message: string | null = null;
		let wantsMessage = false;
		for (let i = 0; i < args.length; i++) {
			const a = args[i];
			if (a === '-m' || a === '--message' || a === '-am') {
				if (a === '-am') all = true;
				wantsMessage = true;
				if (i + 1 < args.length) message = args[++i];
			} else if (a === '-a' || a === '--all') {
				all = true;
			} else if (a.startsWith('-m') && a.length > 2) {
				wantsMessage = true;
				message = a.slice(2);
			} else {
				return fail(
					`git commit: unbekannte Angabe '${a}' – Beispiel: git commit -m "Startseite angelegt"`
				);
			}
		}
		if (wantsMessage && message === null)
			return fail(
				'Fehler: Nach -m fehlt die Nachricht, z. B.: git commit -m "Startseite angelegt"'
			);
		if (all) {
			const work = this.working();
			for (const p of sortedKeys(this.index)) {
				const w = work.get(p);
				if (w === undefined) this.index.delete(p);
				else this.index.set(p, w);
				this.merge?.unresolved.delete(p);
			}
		}
		if (this.merge !== null && this.merge.unresolved.size > 0)
			return fail(
				`Fehler: Commit nicht möglich – es gibt noch Konflikte in: ${[...this.merge.unresolved].sort().join(', ')}` +
					'\n(Datei reparieren oder git checkout --ours/--theirs <Datei>, dann git add <Datei>)'
			);
		if (message === null) {
			if (this.merge === null)
				return fail(
					'Fehler: Bitte gib eine Nachricht an, z. B.: git commit -m "Startseite angelegt"'
				);
			message = `Merge branch '${this.merge.label}'`;
		}
		if (message.trim() === '') return fail('Fehler: Die Nachricht ist leer.');
		const before = this.headTree();
		if (this.merge === null && sameMap(this.index, before)) {
			const pending = !sameMap(this.working(), this.index);
			return ok(
				`Auf Branch ${this.head}\nnichts zu committen` +
					(pending
						? ' – merke Änderungen zuerst vor mit: git add <Datei>'
						: ', Arbeitsverzeichnis sauber')
			);
		}
		const parentId = this.headId();
		const parents: string[] = [];
		if (parentId !== undefined) parents.push(parentId);
		if (this.merge !== null) parents.push(this.merge.other);
		const c = this.newCommit(message, this.author, parents, new Map(this.index));
		this.branches.set(this.head, c.id);
		this.merge = null;
		const changed = changedCount(before, this.index);
		return ok(
			`[${this.head}${parentId === undefined ? ' (Root-Commit)' : ''} ${c.id}] ${message}\n ${plural(changed, 'Datei', 'Dateien')} geändert`
		);
	}

	private log(args: string[]): Out {
		let oneline = false;
		let allRefs = false;
		let limit: number | null = null;
		let ref: string | null = null;
		for (let i = 0; i < args.length; i++) {
			const a = args[i];
			if (a === '--oneline') oneline = true;
			else if (a === '--all') allRefs = true;
			else if (a === '--graph') continue;
			else if (a === '-n' && i + 1 < args.length) limit = parseInt(args[++i]);
			else if (a.startsWith('-') && parseInt(a.slice(1)) !== null) limit = parseInt(a.slice(1));
			else if (a.startsWith('-')) return fail(`git log: unbekannte Option ${a}`);
			else ref = a;
		}
		const starts: string[] = [];
		if (allRefs) {
			starts.push(...sortedValues(this.branches));
			if (this.remote !== null) starts.push(...sortedValues(this.remote));
		} else if (ref !== null) {
			const id = this.resolve(ref);
			if (id === null) return fail(`fatal: '${ref}' gibt es nicht`);
			starts.push(id);
		} else if (this.headId() !== undefined) {
			starts.push(this.headId()!);
		}
		if (starts.length === 0) return fail(`fatal: Branch '${this.head}' hat noch keine Commits`);
		let list = this.sorted(this.reachable(starts));
		if (limit !== null && limit < list.length) list = list.slice(0, limit);
		return ok(
			list.map((c) => (oneline ? this.oneline(c) : this.full(c))).join(oneline ? '\n' : '\n\n')
		);
	}

	private oneline(c: Commit) {
		const deco = this.decorations(c.id);
		return `${c.id}${deco ? ` (${deco})` : ''} ${c.message.split('\n')[0]}`;
	}

	private full(c: Commit) {
		const deco = this.decorations(c.id);
		let s = 'commit ' + c.id;
		if (deco) s += ` (${deco})`;
		if (c.parents.length > 1) s += '\nMerge: ' + c.parents.join(' ');
		s += `\nAutor: ${c.author}\n\n    ${c.message.replaceAll('\n', '\n    ')}`;
		return s;
	}

	/** Verweise auf einen Commit, z. B. "HEAD -> main, origin/main, tag: v1.0". */
	decorations(id: string) {
		const refs: string[] = [];
		if (id === this.headId()) refs.push('HEAD -> ' + this.head);
		for (const name of sortedKeys(this.branches))
			if (this.branches.get(name) === id && name !== this.head) refs.push(name);
		if (this.remote !== null)
			for (const name of sortedKeys(this.remote))
				if (this.remote.get(name) === id) refs.push('origin/' + name);
		for (const name of sortedKeys(this.tags))
			if (this.tags.get(name) === id) refs.push('tag: ' + name);
		return refs.join(', ');
	}

	private show(args: string[]): Out {
		const ref = args.find((a) => !a.startsWith('-')) ?? 'HEAD';
		const id = this.resolve(ref);
		if (id === null)
			return fail(ref === 'HEAD' ? 'fatal: Noch keine Commits' : `fatal: '${ref}' gibt es nicht`);
		const c = this.commits.get(id)!;
		const before =
			c.parents.length === 0 ? new Map<string, string>() : this.commits.get(c.parents[0])!.tree;
		const diff = diffTrees(before, c.tree, null);
		return ok(this.full(c) + (diff ? '\n\n' + diff : ''));
	}

	private diff(args: string[]): Out {
		const staged = args.includes('--staged') || args.includes('--cached');
		let filter: Set<string> | null = null;
		for (const a of args) {
			if (a.startsWith('-')) continue;
			const spec = this.repoPath(a);
			if (spec === null) return fail(`fatal: ${a} liegt außerhalb des Repositorys`);
			filter ??= new Set();
			filter.add(spec);
		}
		if (staged) return ok(diffTrees(this.headTree(), this.index, filter));
		const work = this.working();
		const tracked = new Map<string, string>();
		for (const p of this.index.keys()) if (work.has(p)) tracked.set(p, work.get(p)!);
		return ok(diffTrees(this.index, tracked, filter));
	}

	private restore(args: string[]): Out {
		const staged = args.includes('--staged') || args.includes('-S');
		const paths = args.filter((a) => !a.startsWith('-'));
		if (paths.length === 0) return fail('Welche Datei? Beispiel: git restore index.html');
		const headTree = this.headTree();
		let err = '';
		for (const arg of paths) {
			const spec = this.repoPath(arg);
			if (spec === null) {
				err += `fatal: ${arg} liegt außerhalb des Repositorys\n`;
				continue;
			}
			const candidates = staged
				? matching(spec, this.index.keys(), headTree.keys())
				: matching(spec, this.index.keys());
			if (candidates.length === 0) {
				err += `Fehler: '${arg}' kennt Git nicht – nur Dateien, die schon vorgemerkt oder committet sind, lassen sich zurückholen.\n`;
				continue;
			}
			for (const p of candidates) {
				if (staged) {
					if (headTree.has(p)) this.index.set(p, headTree.get(p)!);
					else this.index.delete(p);
				} else {
					this.fs.putFile(this.fsPath(p), this.index.get(p)!);
				}
			}
		}
		return fail(err);
	}

	private rm(args: string[]): Out {
		const cached = args.includes('--cached');
		const paths = args.filter((a) => !a.startsWith('-'));
		if (paths.length === 0) return fail('Welche Datei? Beispiel: git rm --cached passwort.txt');
		const out: string[] = [];
		let err = '';
		for (const arg of paths) {
			const spec = this.repoPath(arg);
			const candidates = spec === null ? [] : matching(spec, this.index.keys());
			if (candidates.length === 0) {
				err += `fatal: '${arg}' ist nicht in Git\n`;
				continue;
			}
			for (const p of candidates) {
				this.index.delete(p);
				if (!cached) this.fs.deleteFile(this.fsPath(p), this.root!);
				out.push(`rm '${p}'`);
			}
		}
		return { out: out.join('\n'), err };
	}

	private branch(args: string[]): Out {
		const all = args.includes('-a');
		const rest = args.filter((a) => a !== '-a' && a !== '--list' && a !== '-v');
		if (rest.length === 0) {
			const out = sortedKeys(this.branches).map((n) => (n === this.head ? '* ' : '  ') + n);
			if (all && this.remote !== null)
				for (const n of sortedKeys(this.remote)) out.push('  remotes/origin/' + n);
			return ok(out.join('\n'));
		}
		const first = rest[0];
		if (first === '-d' || first === '-D') {
			if (rest.length < 2) return fail('Welcher Branch? Beispiel: git branch -d kontakt');
			const name = rest[1];
			if (!this.branches.has(name)) return fail(`Fehler: Branch '${name}' gibt es nicht.`);
			if (name === this.head)
				return fail(
					`Fehler: Du bist gerade auf '${name}' – wechsle zuerst auf einen anderen Branch.`
				);
			const id = this.branches.get(name)!;
			const headId = this.headId();
			if (first === '-d' && (headId === undefined || !this.isAncestor(id, headId)))
				return fail(
					`Fehler: Branch '${name}' ist noch nicht zusammengeführt. (Trotzdem löschen: git branch -D ${name})`
				);
			this.branches.delete(name);
			return ok(`Branch ${name} entfernt (war ${id}).`);
		}
		if (first.startsWith('-')) return fail(`git branch: unbekannte Option ${first}`);
		const problem = checkName(first);
		if (problem) return fail(problem);
		if (this.branches.has(first)) return fail(`fatal: Branch '${first}' gibt es schon`);
		const start = rest.length > 1 ? this.resolve(rest[1]) : (this.headId() ?? null);
		if (start === null)
			return fail(
				rest.length > 1
					? `fatal: '${rest[1]}' gibt es nicht`
					: 'Fehler: Lege zuerst einen Commit an – vorher gibt es nichts, wovon ein Branch abzweigen kann.'
			);
		this.branches.set(first, start);
		return ok('');
	}

	private switchCommand(args: string[]): Out {
		if (args.length === 0)
			return fail('Wohin? Beispiel: git switch main (neuer Branch: git switch -c name)');
		if (args[0] === '-c' || args[0] === '--create') {
			if (args.length < 2) return fail('Name fehlt. Beispiel: git switch -c kontakt');
			return this.switchTo(args[1], true);
		}
		return this.switchTo(args[0], false);
	}

	private switchTo(name: string, create: boolean): Out {
		if (this.merge !== null) return fail(MERGING);
		if (create) {
			const problem = checkName(name);
			if (problem) return fail(problem);
			if (this.branches.has(name))
				return fail(`fatal: Branch '${name}' gibt es schon (wechseln mit: git switch ${name})`);
			const headId = this.headId();
			if (headId !== undefined) this.branches.set(name, headId);
			this.head = name;
			return ok(`Zu neuem Branch '${name}' gewechselt`);
		}
		if (name === this.head) return ok(`Bereits auf '${name}'`);
		const fromRemote = !this.branches.has(name) && this.remote !== null && this.remote.has(name);
		if (!this.branches.has(name) && !fromRemote)
			return fail(
				`fatal: Branch '${name}' gibt es nicht. (Neu anlegen mit: git switch -c ${name})`
			);
		if (this.dirty()) return fail(DIRTY);
		if (fromRemote) this.branches.set(name, this.remote!.get(name)!);
		this.checkoutTree(this.headTree(), this.commits.get(this.branches.get(name)!)!.tree);
		this.head = name;
		return ok(
			(fromRemote ? `Branch '${name}' folgt jetzt 'origin/${name}'.\n` : '') +
				`Zu Branch '${name}' gewechselt`
		);
	}

	private checkout(args: string[]): Out {
		if (args.length === 0) return fail('Wohin? Beispiel: git checkout main');
		const [first, ...rest] = args;
		if (first === '-b') {
			if (rest.length === 0) return fail('Name fehlt. Beispiel: git checkout -b kontakt');
			return this.switchTo(rest[0], true);
		}
		if (first === '--ours' || first === '--theirs') return this.takeSide(rest, first === '--ours');
		if (first === '--') return this.restore(rest);
		if (this.branches.has(first) || (this.remote !== null && this.remote.has(first)))
			return this.switchTo(first, false);
		const spec = this.repoPath(first);
		if (spec !== null && matching(spec, this.index.keys()).length > 0) return this.restore(args);
		return fail(`Fehler: '${first}' ist weder ein Branch noch eine Datei, die Git kennt.`);
	}

	private takeSide(paths: string[], ours: boolean): Out {
		if (this.merge === null)
			return fail('Fehler: --ours und --theirs gibt es nur während eines Merge-Konflikts.');
		if (paths.length === 0) return fail('Welche Datei? Beispiel: git checkout --theirs index.html');
		let err = '';
		const tree = ours ? this.headTree() : this.commits.get(this.merge.other)!.tree;
		for (const arg of paths) {
			const p = this.repoPath(arg);
			if (p === null || !this.merge.all.has(p)) {
				err += `Fehler: '${arg}' hat keinen Konflikt.\n`;
				continue;
			}
			const content = tree.get(p);
			if (content === undefined) this.fs.deleteFile(this.fsPath(p), this.root!);
			else this.fs.putFile(this.fsPath(p), content);
		}
		return fail(err);
	}

	private mergeCommand(args: string[]): Out {
		if (args.includes('--abort')) {
			if (this.merge === null) return fail('Es läuft gerade keine Zusammenführung.');
			const current = new Map(this.index);
			for (const p of this.merge.all) if (!current.has(p)) current.set(p, '');
			this.checkoutTree(current, this.headTree());
			this.merge = null;
			return ok('Zusammenführung abgebrochen.');
		}
		if (this.merge !== null) return fail(MERGING);
		const ref = args.find((a) => !a.startsWith('-'));
		if (ref === undefined) return fail('Welchen Branch? Beispiel: git merge kontakt');
		const other = this.resolve(ref);
		if (other === null) return fail(`fatal: '${ref}' gibt es nicht`);
		return this.mergeWith(other, ref);
	}

	private mergeWith(other: string, label: string): Out {
		const headId = this.headId();
		if (headId === undefined) {
			this.checkoutTree(this.headTree(), this.commits.get(other)!.tree);
			this.branches.set(this.head, other);
			return ok('Fast-forward');
		}
		if (this.dirty()) return fail(DIRTY);
		if (this.isAncestor(other, headId)) return ok('Bereits aktuell.');
		const ours = this.headTree();
		if (this.isAncestor(headId, other)) {
			const theirs = this.commits.get(other)!.tree;
			this.checkoutTree(ours, theirs);
			this.branches.set(this.head, other);
			return ok(
				`Aktualisiere ${headId}..${other}\nFast-forward\n ${plural(changedCount(ours, theirs), 'Datei', 'Dateien')} geändert`
			);
		}
		const base = this.mergeBase(headId, other);
		const baseTree = base === null ? new Map<string, string>() : this.commits.get(base)!.tree;
		const theirs = this.commits.get(other)!.tree;
		const merged = new Map<string, string>();
		const conflicts: string[] = [];
		for (const p of sortedKeys(ours, theirs, baseTree)) {
			const o = ours.get(p);
			const t = theirs.get(p);
			const b = baseTree.get(p);
			let r: string | undefined;
			if (o === t) r = o;
			else if (o === b) r = t;
			else if (t === b) r = o;
			else {
				conflicts.push(p);
				continue;
			}
			if (r !== undefined) merged.set(p, r);
		}
		this.checkoutTree(ours, merged);
		if (conflicts.length === 0) {
			const c = this.newCommit(
				`Merge branch '${label}'`,
				this.author,
				[headId, other],
				new Map(merged)
			);
			this.branches.set(this.head, c.id);
			return ok(
				`Zusammenführung mit der 'ort'-Strategie.\n ${plural(changedCount(ours, merged), 'Datei', 'Dateien')} geändert`
			);
		}
		const out: string[] = [];
		for (const p of conflicts) {
			const o = ours.get(p);
			const t = theirs.get(p);
			if (o !== undefined) this.index.set(p, o);
			this.fs.putFile(
				this.fsPath(p),
				`<<<<<<< HEAD\n${withNewline(o)}=======\n${withNewline(t)}>>>>>>> ${label}\n`
			);
			out.push(`KONFLIKT: Beide Seiten haben ${p} geändert.`);
		}
		this.merge = { other, label, all: new Set(conflicts), unresolved: new Set(conflicts) };
		out.push(
			'Automatisches Zusammenführen fehlgeschlagen. Löse die Konflikte und committe dann das Ergebnis.'
		);
		out.push(`(Tipp: cat ${conflicts[0]} zeigt die Konfliktstelle)`);
		return ok(out.join('\n'));
	}

	private push(args: string[]): Out {
		if (this.remote === null) return fail(NO_REMOTE);
		const [name, problem] = this.remoteTarget(args);
		if (problem) return fail(problem);
		const local = this.branches.get(name);
		if (local === undefined) return fail(`Fehler: Branch '${name}' hat noch keine Commits`);
		const upstream = this.remote.get(name);
		if (local === upstream) return ok('Alles aktuell.');
		if (upstream === undefined) {
			this.remote.set(name, local);
			return ok(`Nach origin\n * [neuer Branch]      ${name} -> ${name}`);
		}
		if (!this.isAncestor(upstream, local))
			return fail(
				`Nach origin\n ! [abgelehnt]        ${name} -> ${name} (zuerst holen)` +
					'\nFehler: Auf dem Server gibt es neue Commits, die du noch nicht hast.\nHole sie zuerst mit: git pull'
			);
		this.remote.set(name, local);
		return ok(`Nach origin\n   ${upstream}..${local}  ${name} -> ${name}`);
	}

	private pull(args: string[]): Out {
		if (this.remote === null) return fail(NO_REMOTE);
		const [name, problem] = this.remoteTarget(args);
		if (problem) return fail(problem);
		const upstream = this.remote.get(name);
		if (upstream === undefined)
			return fail(`fatal: Auf dem Server gibt es keinen Branch '${name}'`);
		const result = this.mergeWith(upstream, 'origin/' + name);
		return { out: 'Von origin' + (result.out ? '\n' + result.out : ''), err: result.err };
	}

	/** [Branch, Fehler] aus "git push|pull [-u] [origin] [branch]". */
	private remoteTarget(args: string[]): [string, string | null] {
		const positional = args.filter((a) => !a.startsWith('-'));
		if (positional.length > 0 && positional[0] !== 'origin')
			return ['', `fatal: '${positional[0]}' ist kein bekannter Server – hier heißt er origin`];
		return [positional.length > 1 ? positional[1] : this.head, null];
	}

	private tag(args: string[]): Out {
		const names = args.filter((a) => !a.startsWith('-'));
		if (names.length === 0) return ok(sortedKeys(this.tags).join('\n'));
		const name = names[0];
		const problem = checkName(name);
		if (problem) return fail(problem);
		if (this.tags.has(name)) return fail(`fatal: Tag '${name}' gibt es schon`);
		const id = names.length > 1 ? this.resolve(names[1]) : (this.headId() ?? null);
		if (id === null) return fail('Fehler: Lege zuerst einen Commit an.');
		this.tags.set(name, id);
		return ok('');
	}

	// --- Hilfen ---

	headId(): string | undefined {
		return this.branches.get(this.head);
	}

	private headTree(): Map<string, string> {
		const id = this.headId();
		return id === undefined ? new Map() : new Map(this.commits.get(id)!.tree);
	}

	private dirty() {
		if (!sameMap(this.index, this.headTree())) return true;
		const work = this.working();
		for (const [p, content] of this.index) if (work.get(p) !== content) return true;
		return false;
	}

	/** Dateien im Repository-Ordner, Pfade relativ zum Repository. */
	private working(): Map<string, string> {
		const result = new Map<string, string>();
		const prefix = this.root === '' ? '' : this.root + '/';
		for (const [path, content] of this.fs.files) {
			if (!path.startsWith(prefix)) continue;
			const p = path.slice(prefix.length);
			if (!p.startsWith('.git/')) result.set(p, content);
		}
		return result;
	}

	private checkoutTree(from: Map<string, string>, to: Map<string, string>) {
		for (const p of sortedKeys(from))
			if (!to.has(p)) this.fs.deleteFile(this.fsPath(p), this.root!);
		for (const p of sortedKeys(to)) this.fs.putFile(this.fsPath(p), to.get(p)!);
		this.index = new Map(to);
	}

	private inRepo() {
		const here = this.fs.cwdPath();
		return this.root === '' || here === this.root || here.startsWith(this.root + '/');
	}

	/** Pfad aus einem Argument relativ zum Repository, null wenn außerhalb. */
	private repoPath(arg: string): string | null {
		const path = this.fs.resolve(arg);
		const root = this.root!;
		if (root === '') return path;
		if (path === root) return '';
		return path.startsWith(root + '/') ? path.slice(root.length + 1) : null;
	}

	private fsPath(repoPath: string) {
		return this.root === '' ? repoPath : this.root + '/' + repoPath;
	}

	/** Wird der Pfad von der .gitignore im Repository-Ordner ausgeschlossen? Muster: name, *.log, ordner/, /pfad. */
	ignored(path: string): boolean {
		if (this.root === null) return false;
		const content = this.fs.files.get(this.fsPath('.gitignore'));
		if (content === undefined) return false;
		const segments = path.split('/');
		for (const raw of lines(content).slice(0, MAX_IGNORE_LINES)) {
			let p = trim(raw);
			if (p === '' || p.startsWith('#')) continue;
			const dirOnly = p.endsWith('/');
			while (p.endsWith('/')) p = p.slice(0, -1);
			const anchored = p.startsWith('/') || p.includes('/');
			while (p.startsWith('/')) p = p.slice(1);
			if (p === '') continue;
			let acc = '';
			for (let i = 0; i < segments.length; i++) {
				acc = i === 0 ? segments[0] : acc + '/' + segments[i];
				if (dirOnly && i === segments.length - 1) continue;
				if (glob(p, anchored ? acc : segments[i])) return true;
			}
		}
		return false;
	}

	resolve(ref: string): string | null {
		if (ref === 'HEAD') return this.headId() ?? null;
		if (ref.startsWith('HEAD~') || ref.startsWith('HEAD^')) {
			const steps = ref.length === 5 ? 1 : parseInt(ref.slice(5));
			let id = this.headId() ?? null;
			for (let i = 0; steps !== null && i < steps && id !== null; i++) {
				const parents = this.commits.get(id)!.parents;
				id = parents.length === 0 ? null : parents[0];
			}
			return steps === null ? null : id;
		}
		if (this.branches.has(ref)) return this.branches.get(ref)!;
		if (ref.startsWith('origin/') && this.remote !== null && this.remote.has(ref.slice(7)))
			return this.remote.get(ref.slice(7))!;
		if (this.tags.has(ref)) return this.tags.get(ref)!;
		if (ref.length >= 4) {
			for (const c of this.sorted(new Set(this.commits.keys())))
				if (c.id.startsWith(ref)) return c.id;
		}
		return null;
	}

	reachable(starts: Iterable<string>): Set<string> {
		const seen = new Set<string>();
		const todo = [...starts];
		while (todo.length > 0) {
			const id = todo.pop()!;
			if (seen.has(id)) continue;
			seen.add(id);
			todo.push(...this.commits.get(id)!.parents);
		}
		return seen;
	}

	private isAncestor(ancestor: string, of: string) {
		return this.reachable([of]).has(ancestor);
	}

	private mergeBase(a: string, b: string): string | null {
		const fromB = this.reachable([b]);
		let best: Commit | null = null;
		for (const id of this.reachable([a])) {
			const c = this.commits.get(id)!;
			if (fromB.has(id) && (best === null || c.seq > best.seq)) best = c;
		}
		return best === null ? null : best.id;
	}

	/** Neueste zuerst. */
	sorted(ids: Iterable<string>): Commit[] {
		return [...ids].map((id) => this.commits.get(id)!).sort((x, y) => y.seq - x.seq);
	}

	private newCommit(message: string, author: string, parents: string[], tree: Map<string, string>) {
		this.seq++;
		let s = `${this.seq}\n${parents.join(',')}\n${author}\n${message}\n`;
		for (const p of sortedKeys(tree)) s += `${p}\u0000${tree.get(p)}\n`;
		let id = hash(s);
		while (this.commits.has(id)) {
			s += '+';
			id = hash(s);
		}
		const c: Commit = {
			id,
			message,
			author,
			parents: [...parents],
			tree: new Map(tree),
			seq: this.seq
		};
		this.commits.set(id, c);
		return c;
	}
}

/** FNV-1a (32 Bit), die ersten 7 Hex-Zeichen – genauso in GitSimulator.java. */
export function hash(s: string) {
	let h = 0x811c9dc5;
	for (let i = 0; i < s.length; i++) {
		h ^= s.charCodeAt(i);
		h = Math.imul(h, 0x01000193);
	}
	return (h >>> 0).toString(16).padStart(8, '0').slice(0, 7);
}

function objects(value: unknown): GitCommitSetup[] {
	return Array.isArray(value)
		? value.filter(
				(v): v is GitCommitSetup => v !== null && typeof v === 'object' && !Array.isArray(v)
			)
		: [];
}

function sortedKeys(...maps: Map<string, unknown>[]): string[] {
	const keys = new Set<string>();
	for (const m of maps) for (const k of m.keys()) keys.add(k);
	return [...keys].sort();
}

function sortedValues(map: Map<string, string>): string[] {
	return sortedKeys(map).map((k) => map.get(k)!);
}

function sameMap(a: Map<string, string>, b: Map<string, string>) {
	if (a.size !== b.size) return false;
	for (const [k, v] of a) if (b.get(k) !== v) return false;
	return true;
}

function matching(spec: string, ...sources: Iterable<string>[]): string[] {
	const result = new Set<string>();
	for (const source of sources)
		for (const p of source)
			if (spec === '' || p === spec || p.startsWith(spec + '/')) result.add(p);
	return [...result].sort();
}

function count(a: Set<string>, without: Set<string>) {
	let n = 0;
	for (const id of a) if (!without.has(id)) n++;
	return n;
}

function entry(label: string, path: string) {
	const l = label + ':';
	return '\t' + l + ' '.repeat(Math.max(1, 16 - l.length)) + path;
}

function checkName(name: string): string | null {
	const valid =
		name !== '' &&
		!name.startsWith('-') &&
		!name.startsWith('/') &&
		!name.endsWith('/') &&
		!name.includes('..') &&
		!name.endsWith('.') &&
		/^[A-Za-z0-9\-_/.]+$/.test(name);
	return valid
		? null
		: `fatal: '${name}' ist kein gültiger Name (erlaubt: Buchstaben ohne Umlaute, Ziffern, - _ / .)`;
}

function withNewline(s: string | undefined) {
	if (s === undefined || s === '') return '';
	return s.endsWith('\n') ? s : s + '\n';
}

function display(path: string) {
	return '~' + (path === '' ? '' : '/' + path);
}

function trim(s: string) {
	let start = 0;
	while (start < s.length && isSpace(s[start])) start++;
	return trimEnd(s.slice(start));
}

function changedCount(a: Map<string, string>, b: Map<string, string>) {
	let n = 0;
	for (const k of sortedKeys(a, b)) if (a.get(k) !== b.get(k)) n++;
	return n;
}

function plural(n: number, one: string, many: string) {
	return `${n} ${n === 1 ? one : many}`;
}

function parseInt(s: string): number | null {
	return /^[0-9]{1,6}$/.test(s) ? Number(s) : null;
}

/** Unterschiede als vollständige Dateien mit -/+ je Zeile. */
export function diffTrees(
	before: Map<string, string>,
	after: Map<string, string>,
	filter: Set<string> | null
) {
	const out: string[] = [];
	let budget = DIFF_BUDGET;
	let size = 0;
	for (const p of sortedKeys(before, after)) {
		if (filter !== null && ![...filter].some((f) => f === '' || p === f || p.startsWith(f + '/')))
			continue;
		const a = before.get(p);
		const b = after.get(p);
		if (a === b) continue;
		const la = lines(a ?? '');
		const lb = lines(b ?? '');
		const file = [
			'--- ' + (a === undefined ? '/dev/null' : 'a/' + p),
			'+++ ' + (b === undefined ? '/dev/null' : 'b/' + p),
			...lineDiff(la, lb, budget)
		];
		if (la.length * lb.length <= budget) budget -= la.length * lb.length;
		for (const line of file) size += line.length + 1;
		if (size > MAX_OUTPUT) {
			out.push('… (Ausgabe gekürzt)');
			break;
		}
		out.push(...file);
	}
	return out.join('\n');
}

/** Vergleich über die längste gemeinsame Teilfolge; übersteigt er das Budget, alles alt (-) und alles neu (+). */
export function lineDiff(a: string[], b: string[], budget: number): string[] {
	const out: string[] = [];
	const n = a.length;
	const m = b.length;
	if (n * m > budget) {
		for (const l of a) out.push('-' + l);
		for (const l of b) out.push('+' + l);
		return out;
	}
	const lcs: number[][] = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0));
	for (let i = n - 1; i >= 0; i--)
		for (let j = m - 1; j >= 0; j--)
			lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
	let i = 0;
	let j = 0;
	while (i < n && j < m) {
		if (a[i] === b[j]) {
			out.push(' ' + a[i]);
			i++;
			j++;
		} else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
			out.push('-' + a[i++]);
		} else {
			out.push('+' + b[j++]);
		}
	}
	while (i < n) out.push('-' + a[i++]);
	while (j < m) out.push('+' + b[j++]);
	return out;
}
