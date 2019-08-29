package com.instaclustr.cassandra.backup.cli;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidationException;
import javax.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.instaclustr.cassandra.backup.guice.BackupRestoreModule;
import com.instaclustr.guice.GuiceInjectorHolder;
import com.instaclustr.picocli.CLIApplication;
import com.instaclustr.picocli.CassandraJMXSpec;
import com.instaclustr.operations.OperationRequest;
import com.instaclustr.operations.OperationsModule;
import com.instaclustr.threading.ExecutorsModule;
import com.instaclustr.validation.GuiceInjectingConstraintValidatorFactory;
import jmx.org.apache.cassandra.JMXConnectionInfo;
import jmx.org.apache.cassandra.guice.CassandraModule;
import jmx.org.apache.cassandra.service.StorageServiceMBean;
import org.slf4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(subcommands = {BackupApplication.class, RestoreApplication.class, CommitLogBackupApplication.class, CommitLogRestoreApplication.class},
         synopsisSubcommandLabel = "COMMAND",
         versionProvider = BackupRestoreCLI.class
)
public class BackupRestoreCLI extends CLIApplication implements Runnable {

    @Option(names = {"-V", "--version"},
            versionHelp = true,
            description = "print version information and exit")
    private boolean version;

    @Spec
    private CommandSpec spec;

    public static void main(String[] args) {
        System.exit(execute(new CommandLine(new BackupRestoreCLI()), args));
    }

    @Override
    public void run() {
        throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand.");
    }

    static void init(final Runnable command,
                     final CassandraJMXSpec jmxSpec,
                     final OperationRequest operationRequest,
                     final Logger logger) {

        final List<Module> modules = new ArrayList<>();

        if (jmxSpec != null) {
            modules.add(new CassandraModule(new JMXConnectionInfo(jmxSpec.jmxPassword,
                                                                  jmxSpec.jmxUser,
                                                                  jmxSpec.jmxServiceURL,
                                                                  jmxSpec.trustStore,
                                                                  jmxSpec.trustStorePassword)));
        } else {
            modules.add(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(StorageServiceMBean.class).toProvider(() -> null);
                }
            });
        }

        modules.add(new OperationsModule());
        modules.add(new BackupRestoreModule());
        modules.add(new ExecutorsModule());

        final Injector injector = Guice.createInjector(
                Stage.PRODUCTION, // production binds singletons as eager by default
                modules
        );

        GuiceInjectorHolder.INSTANCE.setInjector(injector);

        injector.injectMembers(command);

        final Validator validator = Validation.byDefaultProvider()
                                              .configure()
                                              .constraintValidatorFactory(new GuiceInjectingConstraintValidatorFactory()).buildValidatorFactory()
                                              .getValidator();

        final Set<ConstraintViolation<OperationRequest>> violations = validator.validate(operationRequest);

        if (!violations.isEmpty()) {
            violations.forEach(violation -> logger.error(violation.getMessage()));
            throw new ValidationException();
        }
    }

    @Override
    public String getImplementationTitle() {
        return "backup-restore";
    }
}
