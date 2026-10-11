package com.srmecotech.ecoride.demo;

import com.srmecotech.ecoride.common.config.AppProperties;
import com.srmecotech.ecoride.common.error.ForbiddenOperationException;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.ride.DispatchScheduler;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/** Puts the demo back to its starting state without restarting the server. */
@Service
public class DemoResetService {

    private final DataSource dataSource;
    private final DemoSeeder seeder;
    private final SettingsService settings;
    private final DispatchScheduler dispatcher;
    private final DemoFleetSimulator simulator;
    private final AppProperties properties;

    public DemoResetService(DataSource dataSource, DemoSeeder seeder, SettingsService settings,
                            DispatchScheduler dispatcher, DemoFleetSimulator simulator, AppProperties properties) {
        this.dataSource = dataSource;
        this.seeder = seeder;
        this.settings = settings;
        this.dispatcher = dispatcher;
        this.simulator = simulator;
        this.properties = properties;
    }

    /** @return milliseconds taken */
    public synchronized long reset() {
        if (!properties.demo().resetEnabled()) {
            throw new ForbiddenOperationException("Demo reset is turned off on this server.");
        }
        long started = System.currentTimeMillis();
        dispatcher.pause(true);
        simulator.pause(true);
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
            populator.setSqlScriptEncoding("UTF-8");
            populator.execute(dataSource);
            settings.reload();
            seeder.seed();
            simulator.forget();
        } finally {
            simulator.pause(false);
            dispatcher.pause(false);
        }
        return System.currentTimeMillis() - started;
    }
}
