import {Component, OnInit, ViewChild} from '@angular/core';
import {BsModalComponent} from "ng2-bs3-modal";

import { ProviderConfigSelectorComponent } from "../provider-config-selector/provider-config-selector.component";
import {Descriptor} from "../resource-detail/descriptor";
import {ResourceService} from "../resource/resource.service";
import {Resource} from "../resource/resource";
import {ResourceTypesService} from "../resourcetypes/resourcetypes.service";
import {ValidationUtils} from "../utils/validation-utils";

@Component({
  selector: 'app-new-desc-wizard',
  templateUrl: './new-desc-wizard.component.html',
  styleUrls: ['./new-desc-wizard.component.css']
})
export class NewDescWizardComponent implements OnInit {

  // The maximum length of columns in the service selection display
  private static SERVICE_COLS_MAX_LENGTH = 10;

  // The set of supported services which can be declared in a descriptor
  private static supportedServices: string[] = [ 'AMBARI',
                                                 'AMBARIUI',
                                                 'ATLAS',
                                                 'ATLAS-API',
                                                 'DRUID-BROKER',
                                                 'DRUID-COORDINATOR',
                                                 'DRUID-COORDINATOR-UI',
                                                 'DRUID-OVERLORD',
                                                 'DRUID-OVERLORD-UI',
                                                 'DRUID-ROUTER',
                                                 'FALCON',
                                                 'HBASEUI',
                                                 'HDFSUI',
                                                 'HIVE',
                                                 'JOBTRACKER',
                                                 'JOBHISTORYUI',
                                                 'KNOXSSO',
                                                 'KNOXTOKEN',
                                                 'LIVYSERVER',
                                                 'LOGSEARCH',
                                                 'NAMENODE',
                                                 'NIFI',
                                                 'OOZIE',
                                                 'OOZIEUI',
                                                 'RANGER',
                                                 'RANGERUI',
                                                 'RESOURCEMANAGER',
                                                 'SOLR',
                                                 'SPARKHISTORYUI',
                                                 'STORM',
                                                 'STORM-LOGVIEWER',
                                                 'SUPERSET',
                                                 'WEBHBASE',
                                                 'WEBHCAT',
                                                 'WEBHDFS',
                                                 'YARNUI',
                                                 'YARNUIV2',
                                                 'ZEPPELIN',
                                                 'ZEPPELINUI',
                                                 'ZEPPELINWS'] ;

  @ViewChild('newDescriptorModal')
  childModal: BsModalComponent;

  @ViewChild('choosePC')
  chooseProviderConfigModal: ProviderConfigSelectorComponent;

  resource: Resource;

  descriptorName: string;

  descriptor: Descriptor;

  editModePC: boolean;

  constructor(private resourceTypesService: ResourceTypesService, private resourceService: ResourceService) { }

  ngOnInit() {
    this.descriptor = new Descriptor();
  }

  open(size?: string) {
    this.reset();
    this.childModal.open(size ? size : 'lg');
  }

  reset() {
    this['showDiscovery'] = false;
    this['showServices'] = true;
    this.resource = new Resource();
    this.descriptor = new Descriptor();
    this.descriptorName = '';

    // Reset any previously-selected services
    for (let serviceName of NewDescWizardComponent.supportedServices) {
      if (this.isSelected(serviceName)) {
        // Clear the service selection
        this.toggleServiceSelected(serviceName);
      }
    }
  }

  onClose() {
      // Set the service declarations on the descriptor
      for (let serviceName of NewDescWizardComponent.supportedServices) {
          if (this.isSelected(serviceName)) {
              // Add the selected service to the descriptor
              this.descriptor.addService(serviceName);

              // Clear the service selection
              this.toggleServiceSelected(serviceName);
          }
      }

      // Identify the new resource
      let newResource = new Resource();
      newResource.name = this.descriptorName + '.json';

      // Persist the new descriptor
      this.resourceService.createResource('Descriptors',
                                          newResource,
                                          this.resourceService.serializeDescriptor(this.descriptor, 'json'))
                          .then(() => {
                            // Reload the resource list presentation
                            this.resourceTypesService.selectResourceType('Descriptors');

                            // Set the new descriptor as the selected resource
                            this.resourceService.getDescriptorResources().then(resources => {
                              for (let res of resources) {
                                if (res.name === newResource.name) {
                                  this.resourceService.selectedResource(res);
                                  break;
                                }
                              }
                            });
                          });
  }

  getServiceDisplayColumns() {
    let cols = [];
    let svcCount = NewDescWizardComponent.supportedServices.length;
    let colCount = svcCount / NewDescWizardComponent.SERVICE_COLS_MAX_LENGTH;

    let svcIndex = 0;

    for (let colIndex = 0 ; colIndex < colCount; colIndex++) {
      cols[colIndex] = [];
      for (let j = 0; j < NewDescWizardComponent.SERVICE_COLS_MAX_LENGTH; j++) {
        cols[colIndex][j] = NewDescWizardComponent.supportedServices[svcIndex++];
        if (svcIndex >= svcCount) {
          break;
        }
      }
    }

    return cols;
  }

  toggleServiceSelected(serviceName: string) {
    this[serviceName + '_selected'] = !this.isSelected(serviceName)
  }

  isSelected(serviceName: string) {
    return this[serviceName + '_selected'];
  }

  toggleBoolean(propertyName: string) {
      this[propertyName] = !this[propertyName];
  }

  validate(): boolean {
    let isValid: boolean = true;

    if (this.descriptor) {

      isValid = isValid && this.isValidDescriptorName();

      isValid = isValid && this.isValidProviderConfig();

      // Validate the discovery address
      if (this.descriptor.discoveryAddress) {
        isValid = isValid && this.isValidDiscoveryAddress();
        isValid = isValid && this.isValidDiscoveryCluster();
      }
    } else {
      isValid = false;
    }

    return isValid;
  }

  isMissingDescriptorName(): boolean {
    return !ValidationUtils.isValidString(this.descriptorName);
  }

  isValidDescriptorName(): boolean {
    let isValid: boolean = false;

    if (!this.isMissingDescriptorName()) {
      isValid = ValidationUtils.isValidResourceName(this.descriptorName);
    }

    return isValid;
  }

  isMissingProviderConfig(): boolean {
    return (!this.descriptor || !ValidationUtils.isValidString(this.descriptor.providerConfig));
  }

  isValidProviderConfig(): boolean {
    let isValid: boolean = false;

    if (!this.isMissingProviderConfig()) {
      isValid = ValidationUtils.isValidResourceName(this.descriptor.providerConfig);
    }

    return isValid;
  }

  isValidDiscoveryAddress(): boolean {
    if (this.descriptor.discoveryAddress) {
      return (ValidationUtils.isValidURL(this.descriptor.discoveryAddress));
    } else {
      return true;
    }
  }

  isValidDiscoveryCluster(): boolean {
    if (this.descriptor.discoveryCluster) {
      return (ValidationUtils.isValidResourceName(this.descriptor.discoveryCluster));
    } else {
      return true;
    }
  }

}
