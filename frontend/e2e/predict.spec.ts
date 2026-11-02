import { expect, test } from '@playwright/test';
import { courseWithModules, registerStudent } from './helpers';

/** Vorhersage-Aufgabe und Visualizer-Baustein im Wissensartikel. */
test('Werte am Haltepunkt vorhersagen und Auflösung ansehen', async ({ browser }) => {
	const suffix = Date.now().toString(36);
	const { joinCode } = await courseWithModules(browser, `Vorhersage ${suffix}`, [
		'programmierung',
		'algorithmen'
	]);
	const student = await registerStudent(browser, joinCode, `vorhersage-${suffix}`);

	await student.goto('/modules/programmierung/p16');
	await expect(student.getByTestId('predict-breakpoint')).toContainText('Zeile 4');
	await student.getByLabel('Wert von z').fill('4');
	await student.getByLabel('Wert von groesste').fill('7');
	await student.getByRole('button', { name: 'Vorhersage prüfen' }).click();
	await expect(student.getByRole('status')).toContainText('1 von 2 Werten richtig');

	await student.getByLabel('Wert von z').fill('1');
	await student.getByRole('button', { name: 'Vorhersage prüfen' }).click();
	await expect(student.getByRole('status')).toContainText('Richtig vorhergesagt');
	// Nach dem Lösen wird die Aufgabe neu gezeichnet (Felder gesperrt, Lösung sichtbar)
	await expect(student.getByLabel('Wert von z')).toBeDisabled();

	// Auflösung springt direkt zum Haltepunkt
	await student.getByRole('button', { name: 'Auflösung Schritt für Schritt' }).click();
	const visualizer = student.getByTestId('code-visualizer');
	await expect(visualizer.locator('[aria-current="step"]')).toHaveAttribute('data-line', '4', {
		timeout: 60_000
	});
	await expect(visualizer.locator('[data-var="z"]')).toContainText('1');
	await expect(visualizer.locator('[data-var="groesste"]')).toContainText('7');

	// Baustein im Wissensartikel
	await student.goto('/knowledge?artikel=algorithms');
	const block = student.getByTestId('visualizer-block');
	await expect(block).toBeVisible();
	await block.getByRole('button', { name: /Schritt für Schritt ansehen/ }).click();
	await expect(block.getByTestId('visualizer-position')).toContainText('Schritt 1 von', {
		timeout: 60_000
	});
	await block.getByRole('button', { name: 'Zum Ende' }).click();
	await expect(block.getByTestId('visualizer-output')).toHaveText('[1, 3, 5, 8]');
});
