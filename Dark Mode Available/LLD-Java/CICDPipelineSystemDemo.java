/*
 * CI/CD Pipeline System - Low Level Design
 * ----------------------------------------
 * A pipeline is an ordered list of stages (checkout, build, test, deploy),
 * each modeled as a Command/step. Stages run sequentially with fail-fast
 * semantics: once a stage fails the rest are skipped. A status report is
 * printed per stage.
 *
 * Key classes / abstractions:
 *   - Stage (Command interface) + concrete steps
 *   - StageResult, StageStatus (enum)
 *   - Pipeline (sequential runner, fail-fast)
 *   - PipelineReport
 *
 * Patterns used: Command (Stage), Template-ish sequential executor, Builder (Pipeline).
 */

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CICDPipelineSystemDemo {
    public static void main(String[] args) {
        System.out.println("=== CI/CD Pipeline System Demo ===");

        System.out.println("\n-- Run 1: all stages pass --");
        Pipeline good = new Pipeline.Builder("main-build")
                .add(new Stage("checkout", () -> true))
                .add(new Stage("build", () -> true))
                .add(new Stage("unit-tests", () -> true))
                .add(new Stage("deploy", () -> true))
                .build();
        good.run().print();

        System.out.println("\n-- Run 2: tests fail, deploy is skipped (fail-fast) --");
        Pipeline failing = new Pipeline.Builder("feature-x")
                .add(new Stage("checkout", () -> true))
                .add(new Stage("build", () -> true))
                .add(new Stage("unit-tests", () -> false))
                .add(new Stage("deploy", () -> true))
                .build();
        failing.run().print();
    }
}

enum StageStatus { PASSED, FAILED, SKIPPED }

class StageResult {
    final String stageName;
    final StageStatus status;
    StageResult(String stageName, StageStatus status) { this.stageName = stageName; this.status = status; }
}

// Command: a unit of work returning success/failure.
class Stage {
    final String name;
    private final Supplier<Boolean> action;
    Stage(String name, Supplier<Boolean> action) { this.name = name; this.action = action; }
    boolean execute() { return Boolean.TRUE.equals(action.get()); }
}

class Pipeline {
    final String name;
    private final List<Stage> stages;

    private Pipeline(String name, List<Stage> stages) { this.name = name; this.stages = stages; }

    PipelineReport run() {
        PipelineReport report = new PipelineReport(name);
        boolean failed = false;
        for (Stage stage : stages) {
            if (failed) {
                report.add(new StageResult(stage.name, StageStatus.SKIPPED));
                continue;
            }
            boolean ok = stage.execute();
            report.add(new StageResult(stage.name, ok ? StageStatus.PASSED : StageStatus.FAILED));
            if (!ok) failed = true;
        }
        report.succeeded = !failed;
        return report;
    }

    static class Builder {
        private final String name;
        private final List<Stage> stages = new ArrayList<Stage>();
        Builder(String name) { this.name = name; }
        Builder add(Stage stage) { stages.add(stage); return this; }
        Pipeline build() { return new Pipeline(name, stages); }
    }
}

class PipelineReport {
    final String pipelineName;
    final List<StageResult> results = new ArrayList<StageResult>();
    boolean succeeded;

    PipelineReport(String pipelineName) { this.pipelineName = pipelineName; }
    void add(StageResult r) { results.add(r); }

    void print() {
        System.out.println("Pipeline '" + pipelineName + "' => " + (succeeded ? "SUCCESS" : "FAILURE"));
        for (StageResult r : results) {
            System.out.println("  [" + symbol(r.status) + "] " + r.stageName + " - " + r.status);
        }
    }

    private String symbol(StageStatus s) {
        switch (s) {
            case PASSED:  return "\u2713"; // check mark
            case FAILED:  return "\u2717"; // cross mark
            default:      return "-";
        }
    }
}
