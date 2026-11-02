import { expect, test } from '@playwright/test';
import { courseWithModules, registerStudent } from './helpers';

/** Quiz-Runde: Lehrkraft startet, Schüler antwortet am Gerät, Beamer zeigt Verteilung und Auflösung. */
test('Quiz-Runde mit Beamer', async ({ browser }) => {
	const suffix = Date.now().toString(36);
	const courseName = `Quiz ${suffix}`;
	const { teacher, joinCode } = await courseWithModules(browser, courseName, []);
	const student = await registerStudent(browser, joinCode, `quiz-${suffix}`);

	await teacher.goto('/admin/quiz');
	// Falls ein früherer Lauf eine Runde offen ließ
	if (await teacher.getByRole('button', { name: 'Quiz beenden' }).isVisible()) {
		await teacher.getByRole('button', { name: 'Quiz beenden' }).click();
	}
	await teacher.getByLabel('Titel').fill(`Warm-up ${suffix}`);
	await teacher.getByLabel('Für').selectOption({ label: courseName });
	await teacher
		.getByRole('checkbox', { name: /Variable/ })
		.first()
		.check();
	await teacher.getByRole('button', { name: 'Quiz starten' }).click();
	await expect(teacher.getByTestId('quiz-stage')).toContainText("Gleich geht's los");

	// Schüler sieht den Hinweis live und macht mit
	await student.goto('/modules');
	await student.getByTestId('quiz-banner').click();
	await expect(student.getByTestId('quiz-player')).toContainText('Du bist dabei');

	await teacher.getByRole('button', { name: 'Erste Frage' }).click();
	const options = student.getByTestId('quiz-player').getByRole('button');
	await expect(options.first()).toBeEnabled({ timeout: 15_000 });
	await options.first().click();
	await expect(student.getByTestId('quiz-feedback')).toContainText('Antwort gespeichert');
	await expect(teacher.getByTestId('quiz-answered')).toContainText('1 Antwort', {
		timeout: 15_000
	});

	// Beamer im zweiten Tab der Lehrkraft
	const beamer = await teacher.context().newPage();
	await beamer.goto('/projector');
	await expect(beamer.getByTestId('projector-quiz')).toBeVisible();
	await beamer.getByRole('button', { name: 'Auflösen' }).click();
	await expect(beamer.locator('[data-correct="true"]')).toHaveCount(1);
	await expect(student.getByTestId('quiz-feedback')).toContainText(/Richtig|Leider nicht/, {
		timeout: 15_000
	});

	await beamer.getByRole('button', { name: /Ergebnis zeigen|Nächste Frage/ }).click();
	await teacher.goto('/admin/quiz');
	if (await teacher.getByRole('button', { name: 'Quiz beenden' }).isVisible()) {
		await teacher.getByRole('button', { name: 'Quiz beenden' }).click();
	}
	await expect(student.getByTestId('quiz-player')).toContainText('Gerade läuft kein Quiz', {
		timeout: 15_000
	});
});
