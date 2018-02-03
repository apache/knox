import { Component, OnInit, ViewChild } from '@angular/core';
import { ResourceService } from "../resource/resource.service";
import { Resource } from "../resource/resource";
import { BsModalComponent } from "ng2-bs3-modal";
import { Descriptor } from "../resource-detail/descriptor";


@Component({
  selector: 'app-provider-config-selector',
  templateUrl: './provider-config-selector.component.html',
  styleUrls: ['./provider-config-selector.component.css']
})
export class ProviderConfigSelectorComponent implements OnInit {

  @ViewChild('chooseProviderConfigModal')
  private childModal: BsModalComponent;

  private providerConfigs: Resource[];

  // The descriptor whose provider configuration reference should be updated as a result of the selection in this component
  private descriptor: Descriptor;

  selectedName: string;

  constructor(private resourceService: ResourceService) {
  }

  ngOnInit() {  }

  open(desc: Descriptor, size?: string) {
    this.descriptor = desc;
    this.selectedName = desc.providerConfig; // Set the default selection based on the current ref in the descriptor

    // Load the available provider configs every time this modal is open
    this.resourceService.getResources('Provider Configurations').then(result => this.providerConfigs = result);

    this.childModal.open(size);
  }

  onClose() {
    // Assign the descriptor's provider configuration to the selection
    this.descriptor.setProviderConfig(this.selectedName);
  }

  getProviderConfigs(): Resource[] {
    return this.providerConfigs;
  }

  getReferenceName(providerConfigName: string): string {
    let refName = providerConfigName;
    let extIndex = providerConfigName.lastIndexOf('.');
    if (extIndex > 0) {
      refName = providerConfigName.substring(0, extIndex);
    }
    return refName;
  }

}
