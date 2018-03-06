import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { NewDescWizardComponent } from './new-desc-wizard.component';

describe('NewDescWizardComponent', () => {
  let component: NewDescWizardComponent;
  let fixture: ComponentFixture<NewDescWizardComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ NewDescWizardComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(NewDescWizardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
