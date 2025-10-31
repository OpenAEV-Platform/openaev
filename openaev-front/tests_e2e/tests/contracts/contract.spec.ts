// import { expect, test } from '@playwright/test';
//
// import ContractPage from '../../model/contracts/contract.page';
// import ContractApiMock from '../../model/contracts/contract-api';
// import ContractFormPage from '../../model/contracts/contract-form.page';
// import LeftMenuComponent from '../../model/left-menu.page';
// import appUrl from '../../utils/url';
// // FIXME: should be re enabled
// test.describe('Contracts', () => {
//   test('get first page of contract of contracts with searchtext empty and sort by type,label asc', async ({ page }) => {
//     const contractFormPage = new ContractFormPage(page);
//     const contractApiMock = new ContractApiMock(page);
//
//     await contractApiMock.mockContracts();
//
//     await page.goto(appUrl());
//
//     const leftMenuPage = new LeftMenuComponent(page);
//     await leftMenuPage.goToContracts();
//
//     const contractTitles = contractFormPage.getContractTitles();
//     await expect(contractTitles).toHaveCount(5);
//   });
//   test('get second page of contract with searchtext empty and sort by type,label asc', async ({ page }) => {
//     const contractPage = new ContractPage(page);
//     const contractFormPage = new ContractFormPage(page);
//     const contractApiMock = new ContractApiMock(page);
//
//     await contractApiMock.mockContracts();
//
//     await page.goto(appUrl());
//
//     const leftMenuPage = new LeftMenuComponent(page);
//     await leftMenuPage.goToContracts();
//
//     await contractPage.goToNextPage();
//
//     const contractTitles = contractFormPage.getContractTitles();
//     await expect(contractTitles).toHaveCount(5);
//
//     await contractPage.goToPreviousPage();
//   });
// });
