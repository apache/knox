import { NgKnoxUiPage } from './app.po';

describe('ng-knox-ui App', function() {
  let page: NgKnoxUiPage;

  beforeEach(() => {
    page = new NgKnoxUiPage();
  });

  it('should display message saying app works', () => {
    page.navigateTo();
    expect(page.getParagraphText()).toEqual('app works!');
  });
});
