package org.apache.knox.gateway.rm.dispatch;

import org.apache.knox.gateway.config.Configure;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;

public class RMSchedulerUIHaDispatch extends RMHaBaseDispatcher{
    private static final String RESOURCE_ROLE = "YARN-SCHEDULER-UI";
    private HaProvider haProvider;

    public RMSchedulerUIHaDispatch() {
        super();
    }

    @Override
    @Configure
    public void setHaProvider(HaProvider haProvider) {
        this.haProvider = haProvider;
    }

    @Override
    public void init() {
        super.init();
        if (haProvider != null) {
            super.setResourceRole(RESOURCE_ROLE);
            HaServiceConfig serviceConfig = haProvider.getHaDescriptor().getServiceConfig(RESOURCE_ROLE);
            super.setMaxFailoverAttempts(serviceConfig.getMaxFailoverAttempts());
            super.setFailoverSleep(serviceConfig.getFailoverSleep());
            super.setHaProvider(haProvider);
        }
    }
}
