package deployment.mgmt.configs.updateconfigs;

import deployment.mgmt.configs.componentgroup.ComponentGroupService;
import deployment.mgmt.configs.filestructure.DeployFileStructure;
import deployment.mgmt.configs.service.properties.PropertyService;
import io.microconfig.commands.buildconfig.features.templates.CopyTemplatesService;
import io.microconfig.core.properties.resolver.EnvComponent;
import io.microconfig.core.properties.resolver.PropertyResolver;
import io.microconfig.factory.ConfigType;
import io.microconfig.factory.MicroconfigFactory;
import lombok.RequiredArgsConstructor;

import static io.microconfig.core.environments.Component.byType;
import static io.microconfig.factory.configtypes.StandardConfigTypes.APPLICATION;
import static java.lang.ThreadLocal.withInitial;

@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {
    private final CopyTemplatesService copyTemplatesService;

    private final ComponentGroupService componentGroupService;
    private final DeployFileStructure deployFileStructure;
    private final PropertyService propertyService;

    private final ThreadLocal<PropertyResolver> resolver = withInitial(this::newPropertyResolver);

    @Override
    public void copyTemplates(String service) {
        copyTemplatesService.copyTemplates(
                new EnvComponent(byType(service), componentGroupService.getEnv()),
                deployFileStructure.service().getServiceDir(service),
                propertyService.getServiceProperties(service),
                resolver.get()
        );
    }

    private PropertyResolver newPropertyResolver() {
        MicroconfigFactory factory = MicroconfigFactory.init(
                deployFileStructure.configs().getMicroconfigSourcesRootDir(),
                deployFileStructure.service().getComponentsDir()
        );
        ConfigType configType = APPLICATION.getType();
        return factory.newResolver(factory.newFileBasedProvider(configType), configType);
    }
}
