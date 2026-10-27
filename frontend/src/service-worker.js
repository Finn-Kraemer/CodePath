/// <reference types="@sveltejs/kit" />
import { build, files, version } from '$service-worker';

const CACHE = `cache-${version}`;

// Monaco besteht aus Hunderten Dateien; vorab geladen wird nur, was jeder Code-Editor braucht.
// Der Rest landet beim ersten Aufruf im Cache.
const PRECACHED_VENDOR = [
	'/vendor/monaco/vs/loader.js',
	'/vendor/monaco/vs/editor/editor.main.js',
	'/vendor/monaco/vs/editor/editor.main.css',
	'/vendor/monaco/vs/base/worker/workerMain.js'
];

const ASSETS = [
	...build,
	...files.filter((f) => !f.startsWith('/vendor/monaco/')),
	...PRECACHED_VENDOR
];

self.addEventListener('install', (event) => {
	async function addFilesToCache() {
		const cache = await caches.open(CACHE);
		await cache.addAll(ASSETS);
	}

	event.waitUntil(addFilesToCache());
});

self.addEventListener('activate', (event) => {
	async function deleteOldCaches() {
		for (const key of await caches.keys()) {
			if (key !== CACHE) await caches.delete(key);
		}
	}

	event.waitUntil(deleteOldCaches());
});

self.addEventListener('fetch', (event) => {
	if (event.request.method !== 'GET') return;

	const url = new URL(event.request.url);
	// Live-Updates sind ein Datenstrom und dürfen nie aus dem Cache kommen
	if (url.pathname === '/api/events' || url.origin !== self.location.origin) return;

	async function respond() {
		const cache = await caches.open(CACHE);

		if (ASSETS.includes(url.pathname)) {
			const response = await cache.match(event.request);
			if (response) return response;
		}

		// Sonst Netzwerk zuerst, bei instabilem Schul-WLAN Rückfall auf den Cache
		try {
			const response = await fetch(event.request);
			if (response.status === 200) {
				cache.put(event.request, response.clone());
			}
			return response;
		} catch (err) {
			const response = await cache.match(event.request);
			if (response) return response;
			throw err;
		}
	}

	event.respondWith(respond());
});
