package lk.hms.modules.auth.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Console mode: {@code java -jar hms-backend.jar --hms.recover-admin=<username> --server.port=0}
 * (deploy/windows/recover-admin.ps1 runs it with the service's settings). Prints the temporary password to the
 * console only (never the log file) and exits. Port 0 = a random loopback port, so it can run beside the service
 * (a web context is needed: session revocation uses Spring Session); exit code 0 on success, 1 otherwise.
 */
@Component
@ConditionalOnProperty(name = "hms.recover-admin")
public class AdminRecoveryRunner implements ApplicationRunner {

    private final AdminRecovery recovery;
    private final ConfigurableApplicationContext context;

    public AdminRecoveryRunner(AdminRecovery recovery, ConfigurableApplicationContext context) {
        this.recovery = recovery;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = context.getEnvironment().getProperty("hms.recover-admin", "");
        int code;
        try {
            String temporary = recovery.recover(username);
            System.out.println();
            System.out.println("==============================================================");
            System.out.println(" Temporary password for '" + username.trim().toLowerCase() + "':");
            System.out.println();
            System.out.println("     " + temporary);
            System.out.println();
            System.out.println(" Sign in with it now; you will be asked to choose a new one.");
            System.out.println(" This was recorded in the audit log (ADMIN_RECOVERY).");
            System.out.println("==============================================================");
            code = 0;
        } catch (RuntimeException e) {
            System.out.println("Recovery failed: " + e.getMessage());
            code = 1;
        }
        int exit = code;
        System.exit(SpringApplication.exit(context, () -> exit));
    }
}
