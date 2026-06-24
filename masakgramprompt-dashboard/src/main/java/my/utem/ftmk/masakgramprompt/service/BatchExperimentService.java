package my.utem.ftmk.masakgramprompt.service;

import my.utem.ftmk.masakgramprompt.model.BatchRunStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class BatchExperimentService {

    private final JdbcTemplate jdbcTemplate;
    private final LlmExperimentRunnerService experimentRunnerService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final BatchRunStatus status = new BatchRunStatus();

    public BatchExperimentService(
            JdbcTemplate jdbcTemplate,
            LlmExperimentRunnerService experimentRunnerService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.experimentRunnerService = experimentRunnerService;
    }

    public synchronized boolean start(int modelId, int techniqueId) {
        if (status.isRunning()) {
            return false;
        }

        List<ReelInput> reels = loadReelsWithTranscript();
        if (reels.isEmpty()) {
            throw new IllegalStateException("No transcript records are available. Sync the data folder first.");
        }

        status.setRunning(true);
        status.setTotalRuns(reels.size());
        status.setCompletedRuns(0);
        status.setFailedRuns(0);
        status.setTotalProcessingTimeMs(0);
        status.setLastProcessingTimeMs(null);
        status.setCurrentReelId(null);
        status.setCurrentReelInstagramId(null);
        status.setModelName(loadModelName(modelId));
        status.setTechniqueName(loadTechniqueName(techniqueId));
        status.setStartedAt(LocalDateTime.now());
        status.setFinishedAt(null);

        executor.submit(() -> runBatch(reels, modelId, techniqueId));
        return true;
    }

    public synchronized BatchRunStatus getStatus() {
        BatchRunStatus copy = new BatchRunStatus();
        copy.setRunning(status.isRunning());
        copy.setTotalRuns(status.getTotalRuns());
        copy.setCompletedRuns(status.getCompletedRuns());
        copy.setFailedRuns(status.getFailedRuns());
        copy.setTotalProcessingTimeMs(status.getTotalProcessingTimeMs());
        copy.setLastProcessingTimeMs(status.getLastProcessingTimeMs());
        copy.setCurrentReelId(status.getCurrentReelId());
        copy.setCurrentReelInstagramId(status.getCurrentReelInstagramId());
        copy.setModelName(status.getModelName());
        copy.setTechniqueName(status.getTechniqueName());
        copy.setStartedAt(status.getStartedAt());
        copy.setFinishedAt(status.getFinishedAt());
        return copy;
    }

    private void runBatch(List<ReelInput> reels, int modelId, int techniqueId) {
        try {
            for (ReelInput reel : reels) {
                synchronized (this) {
                    status.setCurrentReelId(reel.reelId());
                    status.setCurrentReelInstagramId(reel.instagramId());
                }

                long runStartedAtNanos = System.nanoTime();
                try {
                    experimentRunnerService.run(reel.reelId(), modelId, techniqueId);
                    synchronized (this) {
                        status.setCompletedRuns(status.getCompletedRuns() + 1);
                    }
                } catch (Exception ex) {
                    synchronized (this) {
                        status.setFailedRuns(status.getFailedRuns() + 1);
                    }
                    System.err.printf("Batch run failed for Reel %d: %s%n", reel.reelId(), ex.getMessage());
                } finally {
                    long processingTimeMs = java.time.Duration.ofNanos(System.nanoTime() - runStartedAtNanos).toMillis();
                    synchronized (this) {
                        status.setLastProcessingTimeMs(processingTimeMs);
                        status.setTotalProcessingTimeMs(status.getTotalProcessingTimeMs() + processingTimeMs);
                    }
                }
            }
        } finally {
            synchronized (this) {
                status.setRunning(false);
                status.setCurrentReelId(null);
                status.setCurrentReelInstagramId(null);
                status.setFinishedAt(LocalDateTime.now());
            }
        }
    }

    private List<ReelInput> loadReelsWithTranscript() {
        return jdbcTemplate.query("""
                SELECT r.reel_id, r.reel_id_instagram
                FROM reel r
                JOIN transcript t ON t.reel_id = r.reel_id
                ORDER BY r.reel_id
                """, (rs, rowNum) -> new ReelInput(
                rs.getInt("reel_id"),
                rs.getString("reel_id_instagram")
        ));
    }

    private String loadModelName(int modelId) {
        return jdbcTemplate.query("SELECT model_name FROM llm_model WHERE model_id = ?", 
                (rs, rowNum) -> rs.getString("model_name"), modelId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Model not found: " + modelId));
    }

    private String loadTechniqueName(int techniqueId) {
        return jdbcTemplate.query("SELECT technique_name FROM prompt_technique WHERE technique_id = ?", 
                (rs, rowNum) -> rs.getString("technique_name"), techniqueId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Prompt technique not found: " + techniqueId));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    private record ReelInput(int reelId, String instagramId) {
    }
}
