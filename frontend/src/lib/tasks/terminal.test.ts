import { describe, expect, it } from 'vitest';
import { TerminalSimulator, tokenize } from './terminal';

// Gleiche Fälle wie HandlersTest$Terminal im Backend: beide Modelle müssen übereinstimmen
describe('TerminalSimulator', () => {
	it('unterstützt die üblichen Befehle', () => {
		const sim = new TerminalSimulator();
		sim.execute('mkdir -p a/b');
		sim.execute('echo "Hallo Welt" > a/b/gruss.txt');
		sim.execute('cp a/b/gruss.txt a/kopie.txt');
		sim.execute('mv a/kopie.txt a/b');
		expect(sim.execute('cat a/b/kopie.txt')).toBe('Hallo Welt');
		sim.execute('cd a/b');
		expect(sim.execute('pwd')).toBe('~/a/b');
		expect(sim.execute('ls')).toBe('gruss.txt  kopie.txt');
		sim.execute('cd ../..');
		sim.execute('rm -r a');
		expect(sim.directories.size).toBe(0);
		expect(sim.files.size).toBe(0);
		expect(sim.execute('rm nichtda').startsWith('rm:')).toBe(true);
	});

	it('baut die Projektstruktur der Git-Aufgabe', () => {
		const sim = new TerminalSimulator();
		for (const cmd of ['mkdir website', 'cd website', 'touch index.html styles.css'])
			sim.execute(cmd);
		expect([...sim.directories]).toEqual(['website']);
		expect([...sim.files.keys()].sort()).toEqual(['website/index.html', 'website/styles.css']);
	});

	it('zerlegt Anführungszeichen wie die Shell', () => {
		expect(tokenize(`echo "a b" 'c d' e`)).toEqual(['echo', 'a b', 'c d', 'e']);
	});
});
