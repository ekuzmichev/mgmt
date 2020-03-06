package deployment.mgmt.microconfig.factory;

import deployment.mgmt.microconfig.secrets.SecretServiceImpl;
import deployment.mgmt.microconfig.secrets.UpdateSecretsPostProcessor;
import io.microconfig.BuildConfigMain;
import io.microconfig.commands.Command;
import io.microconfig.commands.buildconfig.BuildConfigCommand;
import io.microconfig.commands.buildconfig.BuildConfigPostProcessor;
import io.microconfig.core.properties.io.ioservice.ConfigIoService;
import io.microconfig.factory.MicroconfigFactory;

import java.io.File;
import java.util.List;

import static io.microconfig.commands.Command.composite;
import static io.microconfig.factory.configtypes.ConfigTypeImpl.byName;
import static io.microconfig.factory.configtypes.StandardConfigTypes.*;
import static io.microconfig.utils.FileUtils.userHome;

public class MgmtMicroConfigAdapter {
    static final String MGMT = ".mgmt";

    public static void execute(String env, List<String> groups, List<String> components, File sourcesRootDir, File destinationComponentDir) {
        Command command = newBuildPropertiesCommand(sourcesRootDir, destinationComponentDir);
        BuildConfigMain.execute(command, env, groups, components);
    }

    private static Command newBuildPropertiesCommand(File sourcesRootDir, File destinationComponentDir) {
        MicroconfigFactory factory = MicroconfigFactory.init(sourcesRootDir, destinationComponentDir);

        BuildConfigCommand serviceCommon = factory.newBuildCommand(APPLICATION.getType());
        factory = factory.withServiceInnerDir(MGMT);
        return composite(
                serviceCommon,
                factory.newBuildCommand(PROCESS.getType(), new WebappPostProcessor()),
                factory.newBuildCommand(DEPLOY.getType()),
                factory.newBuildCommand(ENV.getType()),
                factory.newBuildCommand(SECRET.getType(), updateSecretsPostProcessor(factory.getConfigIoService())),
                factory.newBuildCommand(LOG4J.getType()),
                factory.newBuildCommand(LOG4J2.getType()),
                factory.newBuildCommand(byName("sap")),
                new GenerateComponentListCommand(destinationComponentDir, factory.getEnvironmentProvider()),
                new CopyHelpFilesCommand(factory.getEnvironmentProvider(), factory.getComponentTree(), destinationComponentDir.toPath())
        );
    }

    private static BuildConfigPostProcessor updateSecretsPostProcessor(ConfigIoService configIoService) {
        File secretFile = new File(userHome(), "/secret/" + SECRET.getType().getResultFileName() + ".properties");
        return new UpdateSecretsPostProcessor(new SecretServiceImpl(secretFile, configIoService));
    }
}