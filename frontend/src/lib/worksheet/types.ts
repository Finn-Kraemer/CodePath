/**
 * Aufgabenblätter: Inhalt `{ settings, blocks }` wie im Backend (`worksheet.WorksheetContent`,
 * gezeichnet von `WorksheetRenderer`). Felder, die fehlen, ersetzt der Renderer durch Standardwerte.
 */

export type AnswerKind = 'lines' | 'box' | 'grid' | 'none';
export type Show = 'both' | 'student' | 'solution';

export interface WorksheetSettings {
	kind?: string;
	subtitle?: string;
	school?: string;
	subject?: string;
	className?: string;
	date?: string;
	instructions?: string;
	nameField?: boolean;
	classField?: boolean;
	dateField?: boolean;
	pointsField?: boolean;
	gradeField?: boolean;
	showPoints?: boolean;
	numbering?: boolean;
	numberLabel?: string;
	pageSize?: 'A4' | 'A5' | 'LETTER';
	orientation?: 'portrait' | 'landscape';
	margins?: 'narrow' | 'normal' | 'wide';
	font?: 'sans' | 'serif';
	fontSize?: number;
	lineSpacing?: number;
	accent?: string;
	footer?: string;
	pageNumbers?: boolean;
	logo?: string;
}

interface Base {
	id: string;
	show?: Show;
}

interface TaskBase extends Base {
	prompt: string;
	points: number;
	hint?: string;
}

export interface HeadingBlock extends Base {
	type: 'heading';
	text: string;
	level: 1 | 2;
}
export interface TextBlock extends Base {
	type: 'text';
	text: string;
}
export interface BoxBlock extends Base {
	type: 'box';
	title: string;
	text: string;
	style: 'info' | 'tip' | 'important' | 'neutral' | 'accent';
}
export interface TaskPart {
	text: string;
	solution: string;
	size?: number;
}
export interface FreeTaskBlock extends TaskBase {
	type: 'task';
	answer: AnswerKind;
	size: number;
	solution: string;
	parts: TaskPart[];
}
export interface ChoiceBlock extends TaskBase {
	type: 'choice';
	columns: number;
	options: { text: string; correct: boolean }[];
}
export interface GapBlock extends TaskBase {
	type: 'gap';
	text: string;
	wordBank: boolean;
	distractors: string;
}
export interface TrueFalseBlock extends TaskBase {
	type: 'truefalse';
	statements: { text: string; correct: boolean }[];
	trueLabel?: string;
	falseLabel?: string;
}
export interface MatchingBlock extends TaskBase {
	type: 'matching';
	pairs: { left: string; right: string }[];
}
export interface OrderBlock extends TaskBase {
	type: 'order';
	items: string[];
}
export interface TableBlock extends Base {
	type: 'table';
	prompt: string;
	points: number;
	hint?: string;
	header: boolean;
	rows: string[][];
	solution: string;
}
export interface CodeBlock extends Base {
	type: 'code';
	caption: string;
	code: string;
	lineNumbers: boolean;
	gaps: boolean;
}
export interface ImageBlock extends Base {
	type: 'image';
	key: string;
	width: number;
	align: 'left' | 'center' | 'right';
	caption: string;
}
export interface SpaceBlock extends Base {
	type: 'space';
	size: number;
}
export interface DividerBlock extends Base {
	type: 'divider';
}
export interface PageBreakBlock extends Base {
	type: 'pagebreak';
}

export type WorksheetBlock =
	| HeadingBlock
	| TextBlock
	| BoxBlock
	| FreeTaskBlock
	| ChoiceBlock
	| GapBlock
	| TrueFalseBlock
	| MatchingBlock
	| OrderBlock
	| TableBlock
	| CodeBlock
	| ImageBlock
	| SpaceBlock
	| DividerBlock
	| PageBreakBlock;

export type BlockType = WorksheetBlock['type'];

export interface WorksheetContent {
	settings: WorksheetSettings;
	blocks: WorksheetBlock[];
}

export function blockId(): string {
	if (typeof crypto !== 'undefined' && 'randomUUID' in crypto)
		return crypto.randomUUID().slice(0, 8);
	return Math.random().toString(36).slice(2, 10);
}

type Definition = {
	label: string;
	description: string;
	group: 'Aufgaben' | 'Inhalt' | 'Layout';
	create: () => WorksheetBlock;
};

/** Alle Blocktypen mit Vorlage für einen neuen Block. */
export const BLOCK_TYPES: Record<BlockType, Definition> = {
	task: {
		label: 'Freie Aufgabe',
		description:
			'Arbeitsauftrag mit Linien, Kasten oder Karos – optional mit Teilaufgaben a), b) …',
		group: 'Aufgaben',
		create: () => ({
			id: blockId(),
			type: 'task',
			prompt: '',
			points: 2,
			answer: 'lines',
			size: 4,
			solution: '',
			parts: []
		})
	},
	choice: {
		label: 'Ankreuzen',
		description: 'Multiple Choice mit Kästchen, auf Wunsch in mehreren Spalten',
		group: 'Aufgaben',
		create: () => ({
			id: blockId(),
			type: 'choice',
			prompt: 'Kreuze die richtige Antwort an.',
			points: 1,
			columns: 1,
			options: [
				{ text: '', correct: true },
				{ text: '', correct: false },
				{ text: '', correct: false }
			]
		})
	},
	gap: {
		label: 'Lückentext',
		description: 'Lücken in [[doppelten Klammern]], optional mit Wortspeicher',
		group: 'Aufgaben',
		create: () => ({
			id: blockId(),
			type: 'gap',
			prompt: 'Fülle die Lücken aus.',
			points: 2,
			text: 'Ein [[Algorithmus]] ist eine eindeutige [[Handlungsvorschrift]].',
			wordBank: false,
			distractors: ''
		})
	},
	truefalse: {
		label: 'Richtig / Falsch',
		description: 'Aussagen als Tabelle zum Ankreuzen',
		group: 'Aufgaben',
		create: () => ({
			id: blockId(),
			type: 'truefalse',
			prompt: 'Kreuze an: richtig oder falsch?',
			points: 2,
			statements: [
				{ text: '', correct: true },
				{ text: '', correct: false }
			]
		})
	},
	matching: {
		label: 'Zuordnen',
		description: 'Begriffe links, gemischte Erklärungen rechts – Buchstaben eintragen',
		group: 'Aufgaben',
		create: () => ({
			id: blockId(),
			type: 'matching',
			prompt: 'Ordne richtig zu.',
			points: 3,
			pairs: [
				{ left: '', right: '' },
				{ left: '', right: '' },
				{ left: '', right: '' }
			]
		})
	},
	order: {
		label: 'Reihenfolge',
		description: 'Schritte gemischt, die Klasse nummeriert sie',
		group: 'Aufgaben',
		create: () => ({
			id: blockId(),
			type: 'order',
			prompt: 'Bringe die Schritte in die richtige Reihenfolge.',
			points: 2,
			items: ['', '', '']
		})
	},
	table: {
		label: 'Tabelle',
		description: 'Tabelle; Zellen wie [[Antwort]] bleiben zum Ausfüllen leer',
		group: 'Aufgaben',
		create: () => ({
			id: blockId(),
			type: 'table',
			prompt: '',
			points: 0,
			header: true,
			rows: [
				['Spalte 1', 'Spalte 2'],
				['', ''],
				['', '']
			],
			solution: ''
		})
	},
	heading: {
		label: 'Überschrift',
		description: 'Gliedert das Blatt, z. B. „Teil A“',
		group: 'Inhalt',
		create: () => ({ id: blockId(), type: 'heading', text: '', level: 1 })
	},
	text: {
		label: 'Text',
		description: 'Absätze und Listen, **fett**, *kursiv*, `Code`',
		group: 'Inhalt',
		create: () => ({ id: blockId(), type: 'text', text: '' })
	},
	box: {
		label: 'Infokasten',
		description: 'Merke-, Tipp- oder Achtung-Kasten',
		group: 'Inhalt',
		create: () => ({ id: blockId(), type: 'box', title: 'Merke', text: '', style: 'info' })
	},
	code: {
		label: 'Code',
		description: 'Quelltext in Festbreitenschrift, optional mit Lücken',
		group: 'Inhalt',
		create: () => ({
			id: blockId(),
			type: 'code',
			caption: '',
			code: '',
			lineNumbers: true,
			gaps: false
		})
	},
	image: {
		label: 'Bild',
		description: 'Hochgeladenes Bild mit Breite und Bildunterschrift',
		group: 'Inhalt',
		create: () => ({
			id: blockId(),
			type: 'image',
			key: '',
			width: 60,
			align: 'center',
			caption: ''
		})
	},
	space: {
		label: 'Freiraum',
		description: 'Leerer Platz, z. B. für eine Skizze',
		group: 'Layout',
		create: () => ({ id: blockId(), type: 'space', size: 4 })
	},
	divider: {
		label: 'Trennlinie',
		description: 'Dünne Linie zwischen Abschnitten',
		group: 'Layout',
		create: () => ({ id: blockId(), type: 'divider' })
	},
	pagebreak: {
		label: 'Seitenumbruch',
		description: 'Nächster Block beginnt auf einer neuen Seite',
		group: 'Layout',
		create: () => ({ id: blockId(), type: 'pagebreak' })
	}
};

export const TASK_TYPES: BlockType[] = ['task', 'choice', 'gap', 'truefalse', 'matching', 'order'];

export function isTask(block: WorksheetBlock): boolean {
	return TASK_TYPES.includes(block.type) || (block.type === 'table' && !!block.prompt.trim());
}

export function totalPoints(blocks: WorksheetBlock[]): number {
	return blocks.reduce(
		(sum, b) => sum + (isTask(b) && 'points' in b ? Math.max(0, Number(b.points) || 0) : 0),
		0
	);
}

/** Kurzer Text für die Blockliste. */
export function blockLabel(block: WorksheetBlock): string {
	const first = (text: string) =>
		text
			.split('\n')[0]
			.replace(/[*`[\]]/g, '')
			.trim();
	switch (block.type) {
		case 'heading':
			return first(block.text);
		case 'text':
			return first(block.text);
		case 'box':
			return block.title || first(block.text);
		case 'code':
			return block.caption || first(block.code);
		case 'image':
			return block.caption || (block.key ? 'Bild' : 'Kein Bild gewählt');
		case 'space':
			return `${block.size} Zeilen`;
		case 'divider':
		case 'pagebreak':
			return '';
		default:
			return first(block.prompt);
	}
}

/** Standardwerte – müssen zu den Standardwerten im WorksheetRenderer passen. */
export const DEFAULT_SETTINGS: Required<Omit<WorksheetSettings, 'logo'>> = {
	kind: 'Arbeitsblatt',
	subtitle: '',
	school: '',
	subject: '',
	className: '',
	date: '',
	instructions: '',
	nameField: true,
	classField: true,
	dateField: true,
	pointsField: true,
	gradeField: false,
	showPoints: true,
	numbering: true,
	numberLabel: 'Aufgabe',
	pageSize: 'A4',
	orientation: 'portrait',
	margins: 'normal',
	font: 'sans',
	fontSize: 11,
	lineSpacing: 9,
	accent: '#b45309',
	footer: '',
	pageNumbers: true
};

/** Ergänzt fehlende Felder, damit die Formulare immer etwas zum Binden haben. */
export function normalize(raw: unknown): WorksheetContent {
	const value = (raw ?? {}) as Partial<WorksheetContent>;
	const settings = { ...DEFAULT_SETTINGS, ...(value.settings ?? {}) };
	const blocks = (Array.isArray(value.blocks) ? value.blocks : [])
		.filter((b) => b && typeof b === 'object' && b.type in BLOCK_TYPES)
		.map(
			(b) => ({ ...BLOCK_TYPES[b.type].create(), ...b, id: b.id || blockId() }) as WorksheetBlock
		);
	blocks.forEach((b) => (b.show ??= 'both'));
	return { settings, blocks };
}

/** Fertige Kopf-Einstellungen für häufige Blattarten. */
export const PRESETS: { label: string; settings: Partial<WorksheetSettings> }[] = [
	{
		label: 'Arbeitsblatt',
		settings: { kind: 'Arbeitsblatt', pointsField: false, gradeField: false, showPoints: false }
	},
	{
		label: 'Hausaufgabe',
		settings: { kind: 'Hausaufgabe', pointsField: false, gradeField: false, showPoints: false }
	},
	{
		label: 'Übungsblatt mit Punkten',
		settings: { kind: 'Übung', pointsField: true, gradeField: false, showPoints: true }
	},
	{
		label: 'Test / Lernkontrolle',
		settings: { kind: 'Lernkontrolle', pointsField: true, gradeField: true, showPoints: true }
	}
];
