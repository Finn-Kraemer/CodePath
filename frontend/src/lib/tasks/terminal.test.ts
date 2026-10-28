import { describe, expect, it } from 'vitest';
import { TerminalSimulator, tokenize } from './terminal';
import type { GitSetup } from './git';
import { graphLayout } from './git-graph';
import cases from './terminal-cases.json';

interface Case {
	name: string;
	initialDirectories?: string[];
	initialFiles?: Record<string, string>;
	git?: GitSetup;
	steps: { cmd: string; out: string }[];
	directories: string[];
	files: Record<string, string>;
}

// Dieselben Fälle prüft TerminalCasesTest im Backend: beide Modelle müssen übereinstimmen
describe('TerminalSimulator – gemeinsame Fälle mit dem Backend', () => {
	for (const c of cases as unknown as Case[]) {
		it(c.name, () => {
			const sim = new TerminalSimulator(c.initialDirectories, c.initialFiles, c.git);
			for (const step of c.steps) expect(sim.execute(step.cmd), step.cmd).toBe(step.out);
			expect([...sim.directories].sort()).toEqual(c.directories);
			expect(Object.fromEntries([...sim.files].sort(([a], [b]) => (a < b ? -1 : 1)))).toEqual(
				c.files
			);
		});
	}
});

describe('TerminalSimulator', () => {
	it('zerlegt Anführungszeichen wie die Shell', () => {
		expect(tokenize(`echo "a b" 'c d' e`)).toEqual(['echo', 'a b', 'c d', 'e']);
		expect(tokenize(`grep x a.txt|wc -l>n.txt`)).toEqual([
			'grep',
			'x',
			'a.txt',
			'|',
			'wc',
			'-l',
			'>',
			'n.txt'
		]);
	});

	it('ergänzt Befehle und Pfade mit Tab', () => {
		const sim = new TerminalSimulator(['website/bilder'], {
			'website/index.html': '',
			'notiz.txt': ''
		});
		expect(sim.complete('mk')).toBe('mkdir ');
		expect(sim.complete('cd web')).toBe('cd website/');
		expect(sim.complete('cat website/i')).toBe('cat website/index.html ');
		expect(sim.complete('ls website/')).toBeNull();
		expect(sim.complete('cat x')).toBeNull();
		expect(sim.complete('grep a notiz.txt | w')).toBe('grep a notiz.txt | wc ');
	});
});

describe('Commit-Graph', () => {
	it('legt Branches in eigene Spuren und führt sie beim Merge zusammen', () => {
		const sim = new TerminalSimulator();
		for (const cmd of [
			'git init',
			'echo a > a.txt',
			'git add .',
			'git commit -m A',
			'git switch -c kontakt',
			'echo k > k.txt',
			'git add .',
			'git commit -m K',
			'git switch main',
			'echo b > b.txt',
			'git add .',
			'git commit -m B',
			'git merge kontakt'
		])
			sim.execute(cmd);
		const graph = graphLayout(sim.git);
		expect(graph.rows.map((r) => r.message)).toEqual(["Merge branch 'kontakt'", 'B', 'K', 'A']);
		expect(graph.rows.map((r) => r.lane)).toEqual([0, 0, 1, 0]);
		expect(graph.lanes).toBe(2);
		expect(graph.rows[0].refs).toEqual([{ label: 'main', kind: 'head' }]);
		expect(graph.rows[2].refs).toEqual([{ label: 'kontakt', kind: 'branch' }]);
		expect(graph.edges).toHaveLength(4);
	});
});

describe('Grenzen gegen absichtlich teure Eingaben', () => {
	it('prüft Muster ohne exponentielles Backtracking', () => {
		const sim = new TerminalSimulator([], { [`${'a'.repeat(200)}.txt`]: '' });
		const start = Date.now();
		expect(sim.execute(`find . -name "${'*a'.repeat(40)}*b"`)).toBe('');
		expect(sim.execute('find . -name "*a*.txt"')).toBe(`./${'a'.repeat(200)}.txt`);
		expect(Date.now() - start).toBeLessThan(1000);
	});

	it('kürzt riesige Diffs', () => {
		const sim = new TerminalSimulator();
		sim.execute('git init');
		for (let i = 0; i < 6; i++) sim.execute(`echo ${'x'.repeat(400)} >> a.txt`);
		for (let f = 0; f < 30; f++) sim.execute(`cp a.txt f${f}.txt`);
		sim.execute('git add .');
		sim.execute('git commit -m alles');
		const out = sim.execute('git show');
		expect(out.endsWith('… (Ausgabe gekürzt)')).toBe(true);
		expect(out.length).toBeLessThan(60_000);
	});
});
