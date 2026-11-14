import { expect } from '@playwright/test';

import { test } from '../../fixtures';
import LessonsPage from '../../model/lessons/LessonsPage';
import { tenantUrl } from '../../utils/url';

test.describe('Scenario - Lessons learned', () => {
  let lessonsPage: LessonsPage;

  test.beforeEach(async ({ page, emptyScenario }) => {
    lessonsPage = new LessonsPage(page);
    await page.addInitScript(() => {
      const style = document.createElement('style');
      style.innerHTML = `
        *, *::before, *::after {
          transition: none !important;
          animation: none !important;
        }
      `;
      document.head.appendChild(style);
    });
    await page.goto(tenantUrl(`/admin/scenarios/${emptyScenario.scenario_id}/lessons`));
    await expect(lessonsPage.objectivesSection).toBeVisible();
  });

  test('should create, evaluate, update and delete an objective', async ({ page }) => {
    const title = `Objective e2e ${Date.now()}`;
    const updatedTitle = `${title} updated`;

    await lessonsPage.createObjective(title);
    await expect(lessonsPage.getObjective(title)).toBeVisible();

    // Evaluating without moving the slider submits the score it shows (10%).
    let evaluations = await lessonsPage.openEvaluations(title);
    await evaluations.getByRole('button', {
      name: 'Evaluate',
      exact: true,
    }).click();
    await expect(evaluations).toBeHidden();
    evaluations = await lessonsPage.openEvaluations(title);
    await expect(evaluations.getByText('10%')).toBeVisible();
    await evaluations.getByRole('button', {
      name: 'Cancel',
      exact: true,
    }).click();
    await expect(evaluations).toBeHidden();

    await lessonsPage.updateObjective(title, updatedTitle);
    await expect(lessonsPage.getObjective(updatedTitle)).toBeVisible();

    await lessonsPage.deleteObjective(updatedTitle);
    await expect(lessonsPage.getObjective(updatedTitle)).toHaveCount(0);
    await expect(page.getByText('No objectives in this scenario.')).toBeVisible();
  });

  test('should create, update and delete a category and its question', async ({ page }) => {
    const categoryName = `Category e2e ${Date.now()}`;
    const question = `Question e2e ${Date.now()}`;
    const updatedQuestion = `${question} updated`;

    await lessonsPage.createCategory(categoryName, '1');
    await expect(lessonsPage.getCategory(categoryName)).toBeVisible();

    await lessonsPage.createQuestion(categoryName, question, '1');
    await expect(lessonsPage.getQuestion(categoryName, question)).toBeVisible();

    await lessonsPage.updateQuestion(categoryName, question, updatedQuestion);
    await expect(lessonsPage.getQuestion(categoryName, updatedQuestion)).toBeVisible();

    await lessonsPage.deleteQuestion(categoryName, updatedQuestion);
    await expect(lessonsPage.getQuestion(categoryName, updatedQuestion)).toHaveCount(0);

    await lessonsPage.deleteCategory(categoryName);
    await expect(lessonsPage.getCategory(categoryName)).toHaveCount(0);
    await expect(page.getByText('No lessons learned categories yet.', { exact: false })).toBeVisible();
  });
});
