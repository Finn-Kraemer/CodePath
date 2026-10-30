import tailwindcss from '@tailwindcss/vite';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vitest/config';

export default defineConfig({
	plugins: [tailwindcss(), sveltekit()],
	server: {
		proxy: {
			'/api': {
				target: 'http://localhost:8080',
				changeOrigin: true
			}
		}
	},
	ssr: { noExternal: ['clsx'] },
	// Der Texteditor für Praxisabgaben (TipTap/ProseMirror) ist ein eigener, nur bei Bedarf geladener Chunk
	build: { chunkSizeWarningLimit: 700 },
	test: {
		include: ['src/**/*.test.ts']
	}
});
