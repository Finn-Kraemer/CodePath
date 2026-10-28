<script lang="ts">
	import { auth } from '$lib/auth.svelte';
	import Markdown from '$lib/Markdown.svelte';
	import { mediaUrl } from '$lib/media';
	import type { Block, HomepageContent, Theme } from './types';

	let {
		content,
		selected = null,
		onselect
	}: {
		content: HomepageContent;
		/** Nur im Editor: markierter Block und Klick zum Auswählen */
		selected?: string | null;
		onselect?: (id: string) => void;
	} = $props();

	const SECTION: Record<Theme, string> = {
		light: 'bg-white text-institutional-navy border-b border-slate-100',
		muted: 'bg-slate-50 text-institutional-navy border-b border-slate-200',
		dark: 'bg-institutional-navy text-white'
	};
	const BODY: Record<Theme, string> = {
		light: 'text-slate-500',
		muted: 'text-slate-500',
		dark: 'text-slate-300'
	};
	const BADGE: Record<Theme, string> = {
		light: 'text-slate-400',
		muted: 'text-institutional-gold',
		dark: 'text-institutional-gold'
	};

	const visible = $derived(content.blocks.filter((b) => !b.hidden || onselect));
</script>

{#snippet badge(text: string, theme: Theme)}
	{#if text}
		<span
			class="mb-4 inline-block font-mono text-[10px] font-bold tracking-[4px] uppercase {BADGE[
				theme
			]}">{text}</span
		>
	{/if}
{/snippet}

{#snippet heading(title: string, highlight: string, extra = '')}
	{#if title || highlight}
		<h2
			class="font-sans text-4xl leading-none font-black tracking-tighter uppercase sm:text-5xl {extra}"
		>
			{title}{#if highlight}<br /><span class="text-institutional-gold">{highlight}</span>{/if}
		</h2>
	{/if}
{/snippet}

{#snippet buttons(large: boolean, theme: Theme)}
	<div class="flex flex-wrap gap-6 {large ? '' : 'justify-center'}">
		{#if !auth.isAuthenticated}
			<a
				href="/login"
				class="rounded-none bg-institutional-gold px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:bg-amber-600 hover:shadow-xl"
				>Login</a
			>
			<a
				href="/register"
				class="rounded-none border-2 px-10 py-5 text-[11px] font-bold tracking-[3px] uppercase transition-all {theme ===
				'dark'
					? 'border-white/20 text-white hover:bg-white/10'
					: 'border-institutional-navy/20 text-institutional-navy hover:bg-institutional-navy/5'}"
				>Account erstellen</a
			>
		{:else}
			<a
				href="/modules"
				class="rounded-none bg-institutional-gold px-10 py-5 text-[11px] font-bold tracking-[3px] text-white uppercase shadow-sm transition-all hover:bg-amber-600 hover:shadow-xl"
				>Zu den Lernmodulen</a
			>
		{/if}
	</div>
{/snippet}

{#snippet render(block: Block)}
	{#if block.type === 'hero'}
		<section class="relative overflow-hidden py-32 lg:py-48 {SECTION[block.theme]}">
			<div class="absolute inset-0 opacity-10">
				<svg class="h-full w-full" fill="none" aria-hidden="true">
					<defs>
						<pattern id="grid-{block.id}" width="40" height="40" patternUnits="userSpaceOnUse">
							<path d="M 40 0 L 0 0 0 40" fill="none" stroke="currentColor" stroke-width="1" />
						</pattern>
					</defs>
					<rect width="100%" height="100%" fill="url(#grid-{block.id})" />
				</svg>
			</div>
			<div class="container-institutional relative z-10">
				<div class="animate-in max-w-4xl">
					{@render badge(block.badge, block.theme === 'light' ? 'muted' : block.theme)}
					<h1
						class="mb-8 font-sans text-4xl leading-tight font-extrabold tracking-tight uppercase sm:text-7xl"
					>
						{block.title}
						{#if block.highlight}<span class="text-institutional-gold">{block.highlight}</span>{/if}
					</h1>
					{#if block.text}
						<p
							class="mb-12 max-w-2xl text-xl leading-relaxed whitespace-pre-line {BODY[
								block.theme
							]}"
						>
							{block.text}
						</p>
					{/if}
					{#if block.buttons}{@render buttons(true, block.theme)}{/if}
				</div>
			</div>
		</section>
	{:else if block.type === 'text'}
		<section class="py-32 {SECTION[block.theme]}">
			<div class="container-institutional {block.align === 'center' ? 'text-center' : ''}">
				<div class="max-w-3xl {block.align === 'center' ? 'mx-auto' : ''}">
					{@render badge(block.badge, block.theme)}
					{@render heading(block.title, '', 'mb-12')}
					<Markdown source={block.body} class="text-lg leading-relaxed {BODY[block.theme]}" />
				</div>
			</div>
		</section>
	{:else if block.type === 'list'}
		<section class="py-32 {SECTION[block.theme]}">
			<div class="container-institutional {block.align === 'center' ? 'text-center' : ''}">
				<div class="max-w-3xl {block.align === 'center' ? 'mx-auto' : ''}">
					{@render badge(block.badge, block.theme)}
					{@render heading(block.title, '', 'mb-12')}
					<ul
						class="inline-block w-full list-none space-y-6 p-0 text-left md:w-auto {BODY[
							block.theme
						]}"
					>
						{#each block.items as item, i (i)}
							<li class="flex items-start gap-4 text-lg">
								<span class="shrink-0 text-xl font-bold text-institutional-gold">//</span>
								<span>{item}</span>
							</li>
						{/each}
					</ul>
				</div>
			</div>
		</section>
	{:else if block.type === 'cards'}
		<section class="relative py-32 {SECTION[block.theme]}">
			<div class="container-institutional">
				<div class="mb-20 max-w-3xl">
					{@render badge(block.badge, block.theme)}
					{@render heading(block.title, '', 'mb-6')}
					{#if block.intro}
						<p class="text-lg leading-relaxed whitespace-pre-line {BODY[block.theme]}">
							{block.intro}
						</p>
					{/if}
				</div>
				<div class="grid grid-cols-1 gap-8 md:grid-cols-2 lg:grid-cols-3">
					{#each block.items as card, i (i)}
						<div
							class="group border border-slate-200 bg-white p-10 shadow-sm transition-all duration-300 hover:border-institutional-navy hover:shadow-xl"
						>
							{#if card.icon}
								<div
									class="mb-8 flex h-16 w-16 items-center justify-center bg-slate-50 text-3xl transition-transform group-hover:scale-110"
								>
									{card.icon}
								</div>
							{/if}
							<h3
								class="mb-4 font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase"
							>
								{card.title}
							</h3>
							<p class="leading-relaxed whitespace-pre-line text-slate-500">{card.text}</p>
						</div>
					{/each}
				</div>
			</div>
		</section>
	{:else if block.type === 'split'}
		<section class="py-32 {SECTION[block.theme]}">
			<div class="container-institutional">
				<div class="grid grid-cols-1 items-center gap-16 lg:grid-cols-2">
					<div>
						{@render badge(block.badge, block.theme)}
						{@render heading(block.title, block.highlight, 'mb-8')}
						{#if block.lead}
							<p class="mb-8 text-xl leading-relaxed font-bold {BODY[block.theme]}">{block.lead}</p>
						{/if}
						<Markdown
							source={block.body}
							class="mb-10 leading-relaxed {block.theme === 'dark'
								? 'text-slate-400'
								: 'text-slate-500'}"
						/>
						{#if block.tags.length}
							<div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
								{#each block.tags as tag, i (i)}
									<div
										class="border p-4 font-mono text-[11px] tracking-wider uppercase {block.theme ===
										'dark'
											? 'border-white/10 bg-white/5'
											: 'border-slate-200 bg-white'}"
									>
										<span class="mr-2 text-institutional-gold">/</span>
										{tag}
									</div>
								{/each}
							</div>
						{/if}
					</div>
					{#if block.boxTitle || block.boxItems.length}
						<div
							class="border p-10 {block.theme === 'dark'
								? 'border-white/10 bg-white/5 backdrop-blur-sm'
								: 'border-slate-200 bg-white shadow-sm'}"
						>
							{#if block.boxTitle}
								<h3
									class="mb-8 border-b pb-4 font-sans text-sm font-black tracking-[3px] text-institutional-gold uppercase {block.theme ===
									'dark'
										? 'border-white/10'
										: 'border-slate-100'}"
								>
									{block.boxTitle}
								</h3>
							{/if}
							<ul class="space-y-4 font-sans text-xs {BODY[block.theme]}">
								{#each block.boxItems as item, i (i)}
									<li class="flex items-center gap-3">
										<div class="h-1 w-1 shrink-0 bg-institutional-gold"></div>
										{item}
									</li>
								{/each}
							</ul>
						</div>
					{/if}
				</div>
			</div>
		</section>
	{:else if block.type === 'timeline'}
		<section class="overflow-hidden py-32 {SECTION[block.theme]}">
			<div class="container-institutional">
				<div class="mb-24 text-center">
					{@render badge(block.badge, block.theme)}
					{@render heading(block.title, '')}
				</div>
				<div class="relative mx-auto max-w-5xl">
					<div
						class="absolute top-0 bottom-0 left-4 w-[2px] -translate-x-1/2 md:left-1/2 {block.theme ===
						'dark'
							? 'bg-white/10'
							: 'bg-slate-100'}"
					></div>
					<div class="relative space-y-24">
						{#each block.steps as step, i (i)}
							{@const left = i % 2 === 0}
							{@const last = i === block.steps.length - 1 && block.steps.length > 1}
							<div class="relative flex flex-col items-center gap-8 md:flex-row md:gap-12">
								{#if !left}<div class="order-1 hidden md:block md:w-1/2"></div>{/if}
								<div
									class="absolute left-4 z-10 flex h-10 min-w-10 -translate-x-1/2 items-center justify-center border-4 border-white px-1 font-mono text-sm font-bold text-white shadow-lg md:left-1/2 md:h-12 md:min-w-12 {last
										? 'bg-institutional-gold'
										: 'bg-institutional-navy'}"
								>
									{step.marker}
								</div>
								<div
									class="ml-12 text-left md:ml-0 md:w-1/2 {left
										? 'order-2 md:order-1 md:text-right'
										: 'order-2 md:order-3'}"
								>
									<h3 class="font-sans text-2xl leading-tight font-black uppercase">
										{step.title}
									</h3>
									<p class="mt-4 text-sm leading-relaxed whitespace-pre-line {BODY[block.theme]}">
										{step.text}
									</p>
								</div>
								{#if left}<div class="order-3 md:w-1/2"></div>{/if}
							</div>
						{/each}
					</div>
				</div>
			</div>
		</section>
	{:else if block.type === 'grades'}
		<section class="py-32 {SECTION[block.theme]}">
			<div class="container-institutional">
				<div class="grid grid-cols-1 items-center gap-20 lg:grid-cols-2">
					<div>
						{@render badge(block.badge, block.theme)}
						{@render heading(block.title, '', 'mb-8')}
						<Markdown source={block.body} class="text-lg leading-relaxed {BODY[block.theme]}" />
					</div>
					<div class="relative overflow-hidden border border-slate-200 bg-white p-10 shadow-2xl">
						<div
							class="absolute top-0 right-0 h-32 w-32 translate-x-16 -translate-y-16 -rotate-45 bg-institutional-gold/5"
						></div>
						{#if block.boxTitle}
							<h3
								class="mb-8 border-b border-slate-100 pb-4 font-sans text-xs font-black tracking-[3px] text-slate-400 uppercase"
							>
								{block.boxTitle}
							</h3>
						{/if}
						<div class="space-y-4 font-mono">
							{#each block.rows as row, i (i)}
								<div
									class="grid grid-cols-3 items-center border-b border-slate-50 pb-2 text-[11px]"
								>
									<span class="text-slate-400">{row.range}</span>
									<span class="text-center font-bold text-institutional-navy uppercase"
										>{row.label}</span
									>
									<span class="text-right font-black text-institutional-gold">{row.grade}</span>
								</div>
							{/each}
						</div>
					</div>
				</div>
			</div>
		</section>
	{:else if block.type === 'image'}
		<section class="py-24 {SECTION[block.theme]}">
			<div class="container-institutional text-center">
				{@render badge(block.badge, block.theme)}
				{@render heading(block.title, '', 'mb-12')}
				{#if block.media}
					<figure class="mx-auto max-w-5xl">
						<img
							src={mediaUrl(block.media)}
							alt={block.alt}
							class="mx-auto max-h-[70vh] w-auto border border-slate-200 shadow-sm"
							loading="lazy"
						/>
						{#if block.caption}
							<figcaption class="mt-4 font-mono text-[11px] tracking-wider {BODY[block.theme]}">
								{block.caption}
							</figcaption>
						{/if}
					</figure>
				{:else if onselect}
					<p class="font-mono text-xs text-slate-400">(Noch kein Bild gewählt)</p>
				{/if}
			</div>
		</section>
	{:else if block.type === 'cta'}
		<section class="relative overflow-hidden py-32 text-center {SECTION[block.theme]}">
			<div class="container-institutional relative z-10">
				{@render badge(block.badge, block.theme)}
				<h2
					class="mb-12 font-sans text-4xl leading-none font-black tracking-tighter uppercase sm:text-5xl"
				>
					{block.title}
					{#if block.highlight}<span class="text-institutional-gold">{block.highlight}</span>{/if}
				</h2>
				{#if block.buttons}{@render buttons(false, block.theme)}{/if}
			</div>
		</section>
	{/if}
{/snippet}

<div class="flex flex-col overflow-x-hidden">
	{#each visible as block (block.id)}
		{#if onselect}
			<!-- Im Editor: Klick auf einen Abschnitt wählt ihn zum Bearbeiten aus -->
			<div
				role="button"
				tabindex="0"
				class="relative cursor-pointer outline-offset-[-4px] {selected === block.id
					? 'outline-4 outline-institutional-gold'
					: 'hover:outline-2 hover:outline-institutional-gold/50'} {block.hidden
					? 'opacity-40'
					: ''}"
				onclick={(e) => {
					e.preventDefault();
					onselect(block.id);
				}}
				onkeydown={(e) => {
					if (e.key === 'Enter') onselect(block.id);
				}}
			>
				{@render render(block)}
			</div>
		{:else}
			{@render render(block)}
		{/if}
	{/each}
</div>

<style>
	@keyframes fade-in {
		from {
			transform: translateX(-20px);
			opacity: 0;
		}
		to {
			transform: translateX(0);
			opacity: 1;
		}
	}

	.animate-in {
		animation: fade-in 0.7s ease-out both;
	}
</style>
