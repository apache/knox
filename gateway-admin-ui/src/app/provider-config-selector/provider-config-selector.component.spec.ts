import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ProviderConfigSelectorComponent } from './provider-config-selector.component';

describe('ProviderConfigSelectorComponent', () => {
  let component: ProviderConfigSelectorComponent;
  let fixture: ComponentFixture<ProviderConfigSelectorComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ProviderConfigSelectorComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ProviderConfigSelectorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
