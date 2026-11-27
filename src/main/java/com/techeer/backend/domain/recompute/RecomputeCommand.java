package com.techeer.backend.domain.recompute;

import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

final class RecomputeCommand implements ApplicationRunner {

    private final RecomputeJob job;
    private final ConfigurableApplicationContext context;

    RecomputeCommand(RecomputeJob job, ConfigurableApplicationContext context) {
        this.job = job;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = execute(args);
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    int execute(ApplicationArguments args) {
        LocalDate from = parseDate(args, "recompute.from");
        LocalDate to = parseDate(args, "recompute.to");
        return job.run(from, to) ? 0 : 1;
    }

    private LocalDate parseDate(ApplicationArguments args, String key) {
        List<String> values = args.getOptionValues(key);
        if (values == null || values.size() != 1) {
            throw new IllegalArgumentException("Specify --" + key + "=YYYY-MM-DD exactly once");
        }
        return LocalDate.parse(values.getFirst());
    }
}
