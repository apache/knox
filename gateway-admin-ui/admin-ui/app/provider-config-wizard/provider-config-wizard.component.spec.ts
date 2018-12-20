import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ProviderConfigWizardComponent } from './provider-config-wizard.component';

describe('ProviderConfigWizardComponent', () => {
  let component: ProviderConfigWizardComponent;
  let fixture: ComponentFixture<ProviderConfigWizardComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ProviderConfigWizardComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ProviderConfigWizardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
