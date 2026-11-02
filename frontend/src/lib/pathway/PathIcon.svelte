<script lang="ts" module>
	/**
	 * Schlichte Linien-Icons für CodePaths statt Emojis. Module speichern ihr Icon weiterhin als Emoji
	 * (Content-Pakete); `iconFor` übersetzt die bekannten in ein Linien-Icon, alles andere wird zum
	 * Anfangsbuchstaben des Titels.
	 */
	export type IconName =
		| 'module'
		| 'module-part'
		| 'task'
		| 'milestone'
		| 'trophy'
		| 'block'
		| 'lock'
		| 'check'
		| 'star'
		| 'warning'
		| 'bolt'
		| 'cloud'
		| 'database'
		| 'chip'
		| 'branch'
		| 'shield'
		| 'network'
		| 'code'
		| 'terminal'
		| 'globe'
		| 'grip'
		| 'close'
		| 'plus'
		| 'minus'
		| 'fit'
		| 'link'
		| 'settings'
		| 'eye'
		| 'search'
		| 'chevron'
		| 'arrange'
		| 'crown'
		| 'door'
		| 'split'
		| 'puzzle'
		| 'lifebuoy'
		| 'map'
		| 'list'
		| 'target'
		| 'gate';

	const EMOJI: Record<string, IconName> = {
		'🏁': 'milestone',
		'🏆': 'trophy',
		'⚡': 'bolt',
		'☁': 'cloud',
		'🗄': 'database',
		'💾': 'database',
		'🔧': 'chip',
		'🔌': 'chip',
		'🌿': 'branch',
		'🔒': 'shield',
		'🛡': 'shield',
		'🤖': 'network',
		'🧠': 'network',
		'🐍': 'code',
		'💻': 'code',
		'🖥': 'terminal',
		'⌨': 'terminal',
		'🌐': 'globe',
		'⭐': 'star',
		'👑': 'crown',
		'🚪': 'door',
		'🔀': 'split',
		'🧩': 'puzzle',
		'🛟': 'lifebuoy'
	};

	/** Linien-Icon zu einem Modul-Emoji, sonst null */
	export function iconFor(emoji: string | null | undefined): IconName | null {
		if (!emoji) return null;
		return EMOJI[emoji.replace(/️/g, '').trim()] ?? null;
	}
</script>

<script lang="ts">
	let {
		name = null,
		emoji = null,
		title = '',
		size = 18,
		class: className = ''
	}: {
		name?: IconName | null;
		/** Modul-Icon (Emoji) – wird übersetzt */
		emoji?: string | null;
		/** für den Anfangsbuchstaben, wenn es kein passendes Icon gibt */
		title?: string;
		size?: number;
		class?: string;
	} = $props();

	const resolved = $derived(name ?? iconFor(emoji));
	const initial = $derived((title.trim()[0] ?? '?').toUpperCase());
</script>

{#if resolved}
	<svg
		class="shrink-0 {className}"
		width={size}
		height={size}
		viewBox="0 0 24 24"
		fill="none"
		stroke="currentColor"
		stroke-width="1.75"
		stroke-linecap="round"
		stroke-linejoin="round"
		aria-hidden="true"
	>
		{#if resolved === 'module'}
			<path d="M12 3 3 7.5l9 4.5 9-4.5L12 3Z" /><path d="m3 12 9 4.5 9-4.5" /><path
				d="m3 16.5 9 4.5 9-4.5"
			/>
		{:else if resolved === 'module-part'}
			<path d="M12 3 3 7.5l9 4.5 9-4.5L12 3Z" /><path
				d="m3 12 9 4.5 9-4.5"
				stroke-dasharray="2 3"
			/>
		{:else if resolved === 'task'}
			<rect x="4" y="4" width="16" height="16" rx="2" /><path d="m8.5 12 2.5 2.5 4.5-5" />
		{:else if resolved === 'milestone'}
			<path d="M5 21V4" /><path d="M5 4h11l-2 4 2 4H5" />
		{:else if resolved === 'trophy'}
			<path d="M8 4h8v5a4 4 0 0 1-8 0V4Z" /><path d="M8 6H5a3 3 0 0 0 3 4" /><path
				d="M16 6h3a3 3 0 0 1-3 4"
			/><path d="M12 13v4" /><path d="M8 20h8" />
		{:else if resolved === 'block'}
			<rect x="4" y="4" width="16" height="16" rx="2" stroke-dasharray="3 3" /><path
				d="M12 9v6M9 12h6"
			/>
		{:else if resolved === 'lock'}
			<rect x="5" y="11" width="14" height="9" rx="1.5" /><path d="M8 11V8a4 4 0 0 1 8 0v3" />
		{:else if resolved === 'check'}
			<path d="m5 12.5 4.5 4.5L19 7.5" />
		{:else if resolved === 'star'}
			<path d="m12 4 2.4 4.9 5.4.8-3.9 3.8.9 5.4L12 16.4 7.2 18.9l.9-5.4-3.9-3.8 5.4-.8L12 4Z" />
		{:else if resolved === 'warning'}
			<path d="M12 4 2.5 20h19L12 4Z" /><path d="M12 10v4" /><path d="M12 17h.01" />
		{:else if resolved === 'bolt'}
			<path d="M13 3 5 13.5h6L10 21l8-10.5h-6L13 3Z" />
		{:else if resolved === 'cloud'}
			<path d="M7 18.5a4 4 0 0 1-.6-7.95A5.5 5.5 0 0 1 17 9a4.75 4.75 0 0 1 .5 9.5H7Z" />
		{:else if resolved === 'database'}
			<ellipse cx="12" cy="6" rx="7" ry="2.75" /><path
				d="M5 6v12c0 1.5 3.1 2.75 7 2.75S19 19.5 19 18V6"
			/><path d="M5 12c0 1.5 3.1 2.75 7 2.75S19 13.5 19 12" />
		{:else if resolved === 'chip'}
			<rect x="7" y="7" width="10" height="10" rx="1" /><path
				d="M10 3v4M14 3v4M10 17v4M14 17v4M3 10h4M3 14h4M17 10h4M17 14h4"
			/>
		{:else if resolved === 'branch'}
			<circle cx="6" cy="5" r="2" /><circle cx="6" cy="19" r="2" /><circle
				cx="18"
				cy="7"
				r="2"
			/><path d="M6 7v10" /><path d="M18 9c0 4-6 3-11.2 8.4" />
		{:else if resolved === 'shield'}
			<path d="M12 3 5 6v5c0 4.5 3 8.3 7 10 4-1.7 7-5.5 7-10V6l-7-3Z" /><path d="m9 12 2 2 4-4" />
		{:else if resolved === 'network'}
			<circle cx="5" cy="7" r="2" /><circle cx="5" cy="17" r="2" /><circle
				cx="12"
				cy="12"
				r="2"
			/><circle cx="19" cy="7" r="2" /><circle cx="19" cy="17" r="2" /><path
				d="m7 8 3 3M7 16l3-3M14 11l3-3M14 13l3 3"
			/>
		{:else if resolved === 'code'}
			<path d="m8 7-5 5 5 5" /><path d="m16 7 5 5-5 5" /><path d="m13.5 5-3 14" />
		{:else if resolved === 'terminal'}
			<rect x="3" y="4" width="18" height="16" rx="2" /><path d="m7 9 3 3-3 3" /><path
				d="M12.5 15H17"
			/>
		{:else if resolved === 'globe'}
			<circle cx="12" cy="12" r="9" /><path d="M3 12h18" /><path
				d="M12 3a14 14 0 0 1 0 18a14 14 0 0 1 0-18Z"
			/>
		{:else if resolved === 'grip'}
			<circle cx="9" cy="6" r="1" /><circle cx="15" cy="6" r="1" /><circle
				cx="9"
				cy="12"
				r="1"
			/><circle cx="15" cy="12" r="1" /><circle cx="9" cy="18" r="1" /><circle
				cx="15"
				cy="18"
				r="1"
			/>
		{:else if resolved === 'close'}
			<path d="M6 6l12 12M18 6 6 18" />
		{:else if resolved === 'plus'}
			<path d="M12 5v14M5 12h14" />
		{:else if resolved === 'minus'}
			<path d="M5 12h14" />
		{:else if resolved === 'fit'}
			<path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" />
		{:else if resolved === 'link'}
			<circle cx="6" cy="6" r="2" /><circle cx="18" cy="18" r="2" /><path
				d="M6 8v3a3 3 0 0 0 3 3h6a3 3 0 0 1 3 3"
			/>
		{:else if resolved === 'settings'}
			<path d="M4 7h10M18 7h2M4 17h4M12 17h8" /><circle cx="16" cy="7" r="2" /><circle
				cx="10"
				cy="17"
				r="2"
			/>
		{:else if resolved === 'eye'}
			<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" /><circle
				cx="12"
				cy="12"
				r="2.75"
			/>
		{:else if resolved === 'search'}
			<circle cx="11" cy="11" r="6" /><path d="m20 20-4.5-4.5" />
		{:else if resolved === 'chevron'}
			<path d="m9 6 6 6-6 6" />
		{:else if resolved === 'arrange'}
			<rect x="9" y="3" width="6" height="4" rx="1" /><rect
				x="3"
				y="17"
				width="6"
				height="4"
				rx="1"
			/><rect x="15" y="17" width="6" height="4" rx="1" /><path d="M12 7v5M6 17v-2.5h12V17" />
		{:else if resolved === 'crown'}
			<path d="M4 8l4 4 4-7 4 7 4-4-1.5 10h-13L4 8Z" /><path d="M5.5 21h13" />
		{:else if resolved === 'door'}
			<path d="M6 21V4h12v17" /><path d="M3 21h18" /><path d="M14.5 12.5h.01" />
		{:else if resolved === 'split'}
			<path d="M12 21v-7" /><path d="M12 14 6 8V4" /><path d="m12 14 6-6V4" /><path
				d="m4 6 2-2 2 2M16 6l2-2 2 2"
			/>
		{:else if resolved === 'puzzle'}
			<path
				d="M5 8h3.5a2 2 0 1 1 4 0H16v3.5a2 2 0 1 1 0 4V19h-3.5a2 2 0 1 0-4 0H5v-3.5a2 2 0 1 0 0-4V8Z"
			/>
		{:else if resolved === 'lifebuoy'}
			<circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="3.5" /><path
				d="m5.6 5.6 3.9 3.9M14.5 14.5l3.9 3.9M18.4 5.6l-3.9 3.9M9.5 14.5l-3.9 3.9"
			/>
		{:else if resolved === 'map'}
			<path d="M9 4 3 6.5v13.5l6-2.5 6 2.5 6-2.5V4l-6 2.5L9 4Z" /><path d="M9 4v13.5M15 6.5V20" />
		{:else if resolved === 'list'}
			<path d="M9 6h11M9 12h11M9 18h11" /><path d="M4.5 6h.01M4.5 12h.01M4.5 18h.01" />
		{:else if resolved === 'target'}
			<circle cx="12" cy="12" r="8" /><circle cx="12" cy="12" r="4" /><path d="M12 12h.01" />
		{:else if resolved === 'gate'}
			<path d="M6 3v5c0 3.5 6 3.5 6 7v6" /><path d="M18 3v5c0 3.5-6 3.5-6 7" /><circle
				cx="12"
				cy="15"
				r="1.5"
			/>
		{/if}
	</svg>
{:else}
	<span
		class="inline-flex shrink-0 items-center justify-center border border-current font-mono font-bold {className}"
		style="width: {size}px; height: {size}px; font-size: {Math.round(size * 0.55)}px"
		aria-hidden="true">{initial}</span
	>
{/if}
