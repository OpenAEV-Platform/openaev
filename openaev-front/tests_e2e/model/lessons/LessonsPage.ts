import { expect, type Locator, type Page } from '@playwright/test';

import MuiListHelpers from '../../utils/MuiListHelpers';

class LessonsPage {
  readonly page: Page;
  readonly objectivesSection: Locator;
  readonly categoriesSection: Locator;

  constructor(page: Page) {
    this.page = page;
    // The test id sits on the header row only: its parent also holds the section content.
    this.objectivesSection = page.getByTestId('lib-header-row').filter({ hasText: 'Objectives' }).locator('..');
    this.categoriesSection = page.getByTestId('lib-header-row').filter({ hasText: 'Categories and questions' }).locator('..');
  }

  // -- Get Locator methods

  getObjective(title: string) {
    return MuiListHelpers.filterItemsInList(this.objectivesSection, title);
  }

  getCategory(name: string) {
    return this.categoriesSection
      .locator('section')
      .filter({
        has: this.page.getByRole('heading', {
          name,
          exact: true,
        }),
      });
  }

  getQuestion(categoryName: string, content: string) {
    return MuiListHelpers.filterItemsInList(this.getCategory(categoryName), content);
  }

  // -- Action methods

  async fillField(container: Locator | Page, label: string, value: string) {
    const field = container.getByLabel(label, { exact: true });
    await field.clear();
    await field.fill(value);
  }

  async confirmDelete() {
    const dialog = this.page.getByRole('dialog');
    await dialog.getByRole('button', {
      name: 'Delete',
      exact: true,
    }).click();
    await expect(dialog).toBeHidden();
  }

  async createObjective(title: string) {
    await this.objectivesSection.getByTestId('button-create').click();
    const dialog = this.page.getByRole('dialog', {
      name: 'Create a new objective',
      exact: true,
    });
    await this.fillField(dialog, 'Title', title);
    await dialog.getByRole('button', {
      name: 'Create',
      exact: true,
    }).click();
    await expect(dialog).toBeHidden();
  }

  async updateObjective(title: string, newTitle: string) {
    await MuiListHelpers.clickSecondaryActionOnListItem(this.page, this.objectivesSection, title, 'Update');
    await this.fillField(this.page, 'Title', newTitle);
    await this.page.getByRole('button', {
      name: 'Update',
      exact: true,
    }).click();
  }

  async deleteObjective(title: string) {
    await MuiListHelpers.clickSecondaryActionOnListItem(this.page, this.objectivesSection, title, 'Delete');
    await this.confirmDelete();
  }

  async openEvaluations(title: string) {
    // The row button's accessible name also holds the description and the score ("<title> 0%"):
    // match on the title only, the row is already scoped by getObjective.
    await this.getObjective(title).getByRole('button', { name: title }).click();
    const dialog = this.page.getByRole('dialog', {
      name: 'Objective achievement evaluation',
      exact: true,
    });
    await expect(dialog).toBeVisible();
    return dialog;
  }

  async createCategory(name: string, order: string) {
    await this.categoriesSection.getByTestId('button-create').first().click();
    const dialog = this.page.getByRole('dialog', {
      name: 'Create a new lessons learned category',
      exact: true,
    });
    await this.fillField(dialog, 'Name', name);
    await this.fillField(dialog, 'Order', order);
    await dialog.getByRole('button', {
      name: 'Create',
      exact: true,
    }).click();
    await expect(dialog).toBeHidden();
  }

  async deleteCategory(name: string) {
    await this.getCategory(name).locator('header').getByRole('button', {
      name: 'More actions',
      exact: true,
    }).click();
    await this.page.getByRole('menuitem', {
      name: 'Delete',
      exact: true,
    }).click();
    await this.confirmDelete();
  }

  async createQuestion(categoryName: string, content: string, order: string) {
    await this.getCategory(categoryName).getByRole('button', {
      name: 'Create a new lessons learned question',
      exact: true,
    }).click();
    const dialog = this.page.getByRole('dialog', {
      name: 'Create a new lessons learned question',
      exact: true,
    });
    await this.fillField(dialog, 'Content', content);
    await this.fillField(dialog, 'Order', order);
    await dialog.getByRole('button', {
      name: 'Create',
      exact: true,
    }).click();
    await expect(dialog).toBeHidden();
  }

  async updateQuestion(categoryName: string, content: string, newContent: string) {
    await MuiListHelpers.clickSecondaryActionOnListItem(this.page, this.getCategory(categoryName), content, 'Update');
    await this.fillField(this.page, 'Content', newContent);
    await this.page.getByRole('button', {
      name: 'Update',
      exact: true,
    }).click();
  }

  async deleteQuestion(categoryName: string, content: string) {
    await MuiListHelpers.clickSecondaryActionOnListItem(this.page, this.getCategory(categoryName), content, 'Delete');
    await this.confirmDelete();
  }
}

export default LessonsPage;
