import { expect, test, type Page } from '@playwright/test';

const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin-pw-123';

/** Code-Visualizer: Schleife, Rekursion und Aliasing Schritt für Schritt; Endlosschleife bricht ab. */
test('Programm Schritt für Schritt ansehen', async ({ page }) => {
	await login(page, 'admin', ADMIN_PASSWORD);
	await page.goto('/modules/programmierung/p13');
	await expect(page.getByLabel('Python-Editor').locator('.monaco-editor')).toBeVisible();

	await setCode(
		page,
		[
			'def fak(n):',
			'    if n <= 1:',
			'        return 1',
			'    return n * fak(n - 1)',
			'',
			'a = [3, 1]',
			'b = a',
			'for i in range(2):',
			'    b.append(fak(3))',
			'print(a)'
		].join('\n')
	);
	const start = page.getByRole('button', { name: 'Schritt für Schritt', exact: true });
	await expect(start).toBeEnabled({ timeout: 60_000 });
	await start.click();

	const visualizer = page.getByTestId('code-visualizer');
	await expect(visualizer.getByTestId('visualizer-position')).toContainText('Schritt 1 von', {
		timeout: 30_000
	});
	if (await visualizer.getByRole('button', { name: 'Verstanden' }).isVisible()) {
		await visualizer.getByRole('button', { name: 'Verstanden' }).click();
	}
	// Erster Schritt: Zeile 1 kommt als Nächstes
	await expect(visualizer.locator('[aria-current="step"]')).toHaveAttribute('data-line', '1');

	// Tastatur: vor bis b = a ausgeführt ist – dieselbe Liste für a und b
	await visualizer.focus();
	await page.keyboard.press('ArrowRight');
	await page.keyboard.press('ArrowRight');
	await page.keyboard.press('ArrowRight');
	await expect(visualizer.locator('[aria-current="step"]')).toHaveAttribute('data-line', '8');
	await expect(visualizer.getByTestId('visualizer-alias')).toContainText('a, b');
	await page.keyboard.press('ArrowLeft');
	await expect(visualizer.locator('[aria-current="step"]')).toHaveAttribute('data-line', '7');

	// Vor bis zur tiefsten Rekursion: drei fak-Rahmen übereinander
	const next = visualizer.getByRole('button', { name: 'Schritt vor' });
	for (let i = 0; i < 40; i++) {
		if ((await visualizer.locator('[data-frame="fak"]').count()) === 3) break;
		await next.click();
	}
	await expect(visualizer.locator('[data-frame="fak"]')).toHaveCount(3);
	await expect(visualizer.getByTestId('visualizer-changes')).toContainText('Aufruf von fak(n = 1)');

	// Ende: vollständige Ausgabe, Änderungen als Text
	await visualizer.getByRole('button', { name: 'Zum Ende' }).click();
	await expect(visualizer.getByTestId('visualizer-output')).toHaveText('[3, 1, 6, 6]');

	// Schieberegler zurück an den Anfang
	await visualizer.getByLabel('Schritt wählen').fill('0');
	await expect(visualizer.getByTestId('visualizer-position')).toContainText('Schritt 1 von');

	// Endlosschleife: Abbruch nach dem Schrittlimit, die Seite bleibt bedienbar
	await visualizer.getByRole('button', { name: 'Schließen' }).click();
	await setCode(page, 'i = 0\nwhile True:\n    i += 1');
	await start.click();
	await expect(page.getByTestId('code-visualizer').getByRole('alert')).toContainText(
		'Nach 1000 Schritten angehalten',
		{ timeout: 30_000 }
	);
	await expect(page.getByRole('button', { name: 'Ausprobieren' })).toBeEnabled();
});

async function setCode(page: Page, code: string) {
	await page.evaluate((value) => {
		// eslint-disable-next-line @typescript-eslint/no-explicit-any
		(window as any).monaco.editor.getModels()[0].setValue(value);
	}, code);
}

async function login(page: Page, username: string, password: string) {
	await page.goto('/login');
	await page.fill('#username', username);
	await page.fill('#password', password);
	await page.click('button[type=submit]');
	await expect(page).toHaveURL(/\/modules/);
}
