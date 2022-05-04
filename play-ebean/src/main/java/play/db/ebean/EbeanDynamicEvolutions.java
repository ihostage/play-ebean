/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */
package play.db.ebean;

import io.ebean.Database;
import io.ebean.DatabaseFactory;
import io.ebean.EbeanServer;
import io.ebeaninternal.dbmigration.model.CurrentModel;
import io.ebeaninternal.api.SpiEbeanServer;
import play.Environment;
import play.api.db.evolutions.DynamicEvolutions;
import play.inject.ApplicationLifecycle;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A Play module that automatically manages Ebean configuration.
 */
@Singleton
public class EbeanDynamicEvolutions extends DynamicEvolutions {

    private final EbeanConfig config;
    private final Environment environment;

    private final Map<String, Database> databases = new HashMap<>();

    @Inject
    public EbeanDynamicEvolutions(EbeanConfig config, Environment environment, ApplicationLifecycle lifecycle) {
        this.config = config;
        this.environment = environment;
        start();
        lifecycle.addStopHook(() -> {
            databases.forEach((key, database) -> database.shutdown(false, false));
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Initialise the Ebean servers/databases.
     */
    public void start() {
        config.serverConfigs().forEach((key, serverConfig) -> databases.put(key, DatabaseFactory.create(serverConfig)));
    }

    /**
     * Generate evolutions.
     */
    @Override
    public void create() {
        if (environment.isProd()) {
            return;
        }
        config.serverConfigs().forEach((key, serverConfig) -> {
            String evolutionScript = generateEvolutionScript(databases.get(key));
            if (evolutionScript == null) {
                return;
            }
            File evolutions = environment.getFile("conf/evolutions/" + key + "/1.sql");
            try {
                String content = "";
                if (evolutions.exists()) {
                    content = new String(Files.readAllBytes(evolutions.toPath()), "utf-8");
                }

                if (content.isEmpty() || content.startsWith("# --- Created by Ebean DDL")) {
                    environment.getFile("conf/evolutions/" + key).mkdirs();
                    if (!content.equals(evolutionScript)) {
                        Files.write(evolutions.toPath(), evolutionScript.getBytes("utf-8"));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Helper method that generates the required evolution to properly run Ebean/DB.
     *
     * @param database the Database.
     * @return the complete migration generated by Ebean/DB.
     */
    public static String generateEvolutionScript(Database database) {
        return generateScript((SpiEbeanServer) database);
    }

    /**
     * Helper method that generates the required evolution to properly run Ebean/DB.
     *
     * @deprecated
     * Use {@link EbeanDynamicEvolutions#generateEvolutionScript(Database)} instead.
     *
     * @param server the EbeanServer.
     * @return the complete migration generated by Ebean/DB.
     *
     */
    @Deprecated
    public static String generateEvolutionScript(EbeanServer server) {
        return generateScript((SpiEbeanServer) server);
    }

    private static String generateScript(SpiEbeanServer spiServer) {
        CurrentModel ddl = new CurrentModel(spiServer);

        String ups = ddl.getCreateDdl();
        String downs = ddl.getDropAllDdl();

        if (ups == null || ups.trim().isEmpty()) {
                return null;
            }

        return
                "# --- Created by Ebean DDL\r\n" +
                "# To stop Ebean DDL generation, remove this comment and start using Evolutions\r\n" +
                "\r\n" +
                "# --- !Ups\r\n" +
                "\r\n" +
                ups +
                "\r\n" +
                "# --- !Downs\r\n" +
                "\r\n" +
                downs;
    }
}
