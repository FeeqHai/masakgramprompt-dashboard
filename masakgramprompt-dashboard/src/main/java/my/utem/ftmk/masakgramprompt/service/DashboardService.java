package my.utem.ftmk.masakgramprompt.service;

import my.utem.ftmk.masakgramprompt.model.AudioFileRecord;
import my.utem.ftmk.masakgramprompt.model.DashboardSummary;
import my.utem.ftmk.masakgramprompt.model.ExperimentRecord;
import my.utem.ftmk.masakgramprompt.model.IngredientResultRecord;
import my.utem.ftmk.masakgramprompt.model.LlmModelOption;
import my.utem.ftmk.masakgramprompt.model.ModelProcessingSummary;
import my.utem.ftmk.masakgramprompt.model.NutritionResultRecord;
import my.utem.ftmk.masakgramprompt.model.ProcessingTimeSummary;
import my.utem.ftmk.masakgramprompt.model.PromptTechniqueOption;
import my.utem.ftmk.masakgramprompt.model.Reel;
import my.utem.ftmk.masakgramprompt.model.TranscriptRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provides database-backed data for the original dashboard and reel detail pages.
 */
@Service
public class DashboardService {

    private static final List<String> PROMPT_TECHNIQUE_ORDER = List.of(
            "zero-shot",
            "few-shot",
            "chain-of-thought",
            "structured-output"
    );

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Reel> reelRowMapper = (rs, rowNum) -> {
        Reel reel = new Reel();
        reel.setReelId(rs.getInt("reel_id"));
        reel.setReelIdInstagram(rs.getString("reel_id_instagram"));
        reel.setReelUrl(rs.getString("reel_url"));
        reel.setIdentifiedByMatric(rs.getString("identified_by_matric"));
        reel.setIdentifiedByName(rs.getString("identified_by_name"));
        Date identifiedDate = rs.getDate("identified_date");
        reel.setIdentifiedDate(identifiedDate == null ? null : identifiedDate.toLocalDate());
        reel.setInfluencerName(rs.getString("influencer_name"));
        reel.setInfluencerAccount(rs.getString("influencer_account"));
        reel.setHasAudio(rs.getInt("has_audio") == 1);
        reel.setHasTranscript(rs.getInt("has_transcript") == 1);
        reel.setHasGroundTruth(rs.getInt("has_ground_truth") == 1);
        reel.setExperimentCount(rs.getInt("experiment_count"));
        reel.setCompletedExperimentCount(rs.getInt("completed_experiment_count"));
        return reel;
    };

    public DashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Builds the top dashboard counters from dataset and experiment tables.
     */
    public DashboardSummary getSummary() {
        DashboardSummary summary = new DashboardSummary();
        summary.setReelCount(count("reel"));
        summary.setAudioCount(count("audio_file"));
        summary.setTranscriptCount(count("transcript"));
        summary.setGroundTruthCount(count("ground_truth_reel"));
        summary.setExperimentCount(count("experiment"));
        summary.setPendingExperimentCount(countExperimentsByStatus("pending"));
        summary.setRunningExperimentCount(countExperimentsByStatus("running"));
        summary.setCompletedExperimentCount(countExperimentsByStatus("completed"));
        summary.setFailedExperimentCount(countExperimentsByStatus("failed"));
        return summary;
    }

    /**
     * Calculates processing-time statistics for completed model/prompt runs.
     */
    public List<ProcessingTimeSummary> findProcessingTimeSummary() {
        String sql = """
                SELECT
                    lm.model_name,
                    lm.model_tag,
                    pt.technique_name,
                    COUNT(*) AS completed_runs,
                    ROUND(AVG(e.processing_time_ms) / 1000, 1) AS avg_seconds,
                    ROUND(MIN(e.processing_time_ms) / 1000, 1) AS min_seconds,
                    ROUND(MAX(e.processing_time_ms) / 1000, 1) AS max_seconds
                FROM experiment e
                JOIN llm_model lm ON lm.model_id = e.model_id
                JOIN prompt_technique pt ON pt.technique_id = e.technique_id
                WHERE e.status = 'completed'
                  AND e.processing_time_ms IS NOT NULL
                GROUP BY lm.model_name, lm.model_tag, pt.technique_name
                ORDER BY lm.model_name, pt.technique_name
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            ProcessingTimeSummary summary = new ProcessingTimeSummary();
            summary.setModelName(rs.getString("model_name"));
            summary.setModelTag(rs.getString("model_tag"));
            summary.setTechniqueName(rs.getString("technique_name"));
            summary.setCompletedRuns(rs.getInt("completed_runs"));
            summary.setAverageSeconds(getDoubleOrNull(rs.getObject("avg_seconds")));
            summary.setMinSeconds(getDoubleOrNull(rs.getObject("min_seconds")));
            summary.setMaxSeconds(getDoubleOrNull(rs.getObject("max_seconds")));
            return summary;
        });
    }

    /**
     * Groups processing-time rows by model and fills missing prompt techniques with placeholders.
     */
    public List<ModelProcessingSummary> findModelProcessingSummaries() {
        Map<String, ModelProcessingSummary> summariesByModel = new LinkedHashMap<>();
        Map<String, Map<String, ProcessingTimeSummary>> timingsByModel = new LinkedHashMap<>();

        for (ProcessingTimeSummary timing : findProcessingTimeSummary()) {
            String modelKey = timing.getModelName() + "|" + timing.getModelTag();

            summariesByModel.computeIfAbsent(modelKey, key -> {
                ModelProcessingSummary modelSummary = new ModelProcessingSummary();
                modelSummary.setModelName(timing.getModelName());
                modelSummary.setModelTag(timing.getModelTag());
                return modelSummary;
            });

            timingsByModel
                    .computeIfAbsent(modelKey, key -> new LinkedHashMap<>())
                    .put(timing.getTechniqueName(), timing);
        }

        for (Map.Entry<String, ModelProcessingSummary> entry : summariesByModel.entrySet()) {
            Map<String, ProcessingTimeSummary> modelTimings = timingsByModel.get(entry.getKey());
            List<ProcessingTimeSummary> techniqueSummaries = new ArrayList<>();
            ProcessingTimeSummary fastest = null;

            for (String techniqueName : PROMPT_TECHNIQUE_ORDER) {
                ProcessingTimeSummary timing = modelTimings.get(techniqueName);
                if (timing == null) {
                    timing = emptyTechniqueSummary(entry.getValue(), techniqueName);
                }

                techniqueSummaries.add(timing);

                if (timing.getAverageSeconds() != null
                        && (fastest == null || timing.getAverageSeconds() < fastest.getAverageSeconds())) {
                    fastest = timing;
                }
            }

            entry.getValue().setTechniqueSummaries(techniqueSummaries);
            entry.getValue().setFastestTechniqueName(fastest == null ? "No data" : fastest.getTechniqueName());
        }

        return new ArrayList<>(summariesByModel.values());
    }

    /**
     * Creates a placeholder timing row when a model has no completed run for a technique.
     */
    private ProcessingTimeSummary emptyTechniqueSummary(ModelProcessingSummary modelSummary, String techniqueName) {
        ProcessingTimeSummary summary = new ProcessingTimeSummary();
        summary.setModelName(modelSummary.getModelName());
        summary.setModelTag(modelSummary.getModelTag());
        summary.setTechniqueName(techniqueName);
        summary.setCompletedRuns(0);
        return summary;
    }

    /**
     * Loads all reels with readiness flags and experiment totals.
     */
    public List<Reel> findAllReels() {
        String sql = """
                SELECT
                    r.reel_id,
                    r.reel_id_instagram,
                    r.reel_url,
                    r.identified_by_matric,
                    r.identified_by_name,
                    r.identified_date,
                    i.name AS influencer_name,
                    i.instagram_account AS influencer_account,
                    CASE WHEN af.audio_id IS NULL THEN 0 ELSE 1 END AS has_audio,
                    CASE WHEN t.transcript_id IS NULL THEN 0 ELSE 1 END AS has_transcript,
                    CASE WHEN gtr.gt_reel_id IS NULL THEN 0 ELSE 1 END AS has_ground_truth,
                    COUNT(e.experiment_id) AS experiment_count,
                    SUM(CASE WHEN e.status = 'completed' THEN 1 ELSE 0 END) AS completed_experiment_count
                FROM reel r
                JOIN influencer i ON i.influencer_id = r.influencer_id
                LEFT JOIN audio_file af ON af.reel_id = r.reel_id
                LEFT JOIN transcript t ON t.reel_id = r.reel_id
                LEFT JOIN ground_truth_reel gtr ON gtr.transcript_id = t.transcript_id
                LEFT JOIN experiment e ON e.transcript_id = t.transcript_id
                GROUP BY
                    r.reel_id,
                    r.reel_id_instagram,
                    r.reel_url,
                    r.identified_by_matric,
                    r.identified_by_name,
                    r.identified_date,
                    i.name,
                    i.instagram_account,
                    af.audio_id,
                    t.transcript_id,
                    gtr.gt_reel_id
                ORDER BY r.reel_id
                """;

        return jdbcTemplate.query(sql, reelRowMapper);
    }

    /**
     * Loads one reel and its readiness counters for the legacy reel detail page.
     */
    public Optional<Reel> findReelById(int reelId) {
        String sql = """
                SELECT
                    r.reel_id,
                    r.reel_id_instagram,
                    r.reel_url,
                    r.identified_by_matric,
                    r.identified_by_name,
                    r.identified_date,
                    i.name AS influencer_name,
                    i.instagram_account AS influencer_account,
                    CASE WHEN af.audio_id IS NULL THEN 0 ELSE 1 END AS has_audio,
                    CASE WHEN t.transcript_id IS NULL THEN 0 ELSE 1 END AS has_transcript,
                    CASE WHEN gtr.gt_reel_id IS NULL THEN 0 ELSE 1 END AS has_ground_truth,
                    COUNT(e.experiment_id) AS experiment_count,
                    SUM(CASE WHEN e.status = 'completed' THEN 1 ELSE 0 END) AS completed_experiment_count
                FROM reel r
                JOIN influencer i ON i.influencer_id = r.influencer_id
                LEFT JOIN audio_file af ON af.reel_id = r.reel_id
                LEFT JOIN transcript t ON t.reel_id = r.reel_id
                LEFT JOIN ground_truth_reel gtr ON gtr.transcript_id = t.transcript_id
                LEFT JOIN experiment e ON e.transcript_id = t.transcript_id
                WHERE r.reel_id = ?
                GROUP BY
                    r.reel_id,
                    r.reel_id_instagram,
                    r.reel_url,
                    r.identified_by_matric,
                    r.identified_by_name,
                    r.identified_date,
                    i.name,
                    i.instagram_account,
                    af.audio_id,
                    t.transcript_id,
                    gtr.gt_reel_id
                """;

        return jdbcTemplate.query(sql, reelRowMapper, reelId).stream().findFirst();
    }

    /**
     * Loads the audio metadata row for one reel when it exists.
     */
    public Optional<AudioFileRecord> findAudioByReelId(int reelId) {
        String sql = """
                SELECT
                    audio_id,
                    reel_id,
                    file_name,
                    file_path,
                    file_created_at,
                    file_size_bytes,
                    file_format,
                    reel_audio_consistent,
                    verified_by_matric,
                    verified_by_name,
                    verified_at
                FROM audio_file
                WHERE reel_id = ?
                LIMIT 1
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            AudioFileRecord audio = new AudioFileRecord();
            audio.setAudioId(rs.getInt("audio_id"));
            audio.setReelId(rs.getInt("reel_id"));
            audio.setFileName(rs.getString("file_name"));
            audio.setFilePath(rs.getString("file_path"));
            audio.setFileCreatedAt(toLocalDateTime(rs.getTimestamp("file_created_at")));
            long fileSizeBytes = rs.getLong("file_size_bytes");
            audio.setFileSizeBytes(rs.wasNull() ? null : fileSizeBytes);
            audio.setFileFormat(rs.getString("file_format"));
            audio.setReelAudioConsistent(getBooleanOrNull(rs.getObject("reel_audio_consistent")));
            audio.setVerifiedByMatric(rs.getString("verified_by_matric"));
            audio.setVerifiedByName(rs.getString("verified_by_name"));
            audio.setVerifiedAt(toLocalDateTime(rs.getTimestamp("verified_at")));
            return audio;
        }, reelId).stream().findFirst();
    }

    /**
     * Loads the transcript metadata row for one reel when it exists.
     */
    public Optional<TranscriptRecord> findTranscriptByReelId(int reelId) {
        String sql = """
                SELECT
                    transcript_id,
                    reel_id,
                    audio_id,
                    file_name,
                    file_path,
                    file_created_at,
                    file_size_bytes,
                    file_format,
                    audio_transcript_consistent,
                    verified_by_matric,
                    verified_by_name,
                    verified_at
                FROM transcript
                WHERE reel_id = ?
                LIMIT 1
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            TranscriptRecord transcript = new TranscriptRecord();
            transcript.setTranscriptId(rs.getInt("transcript_id"));
            transcript.setReelId(rs.getInt("reel_id"));
            transcript.setAudioId(rs.getInt("audio_id"));
            transcript.setFileName(rs.getString("file_name"));
            transcript.setFilePath(rs.getString("file_path"));
            transcript.setFileCreatedAt(toLocalDateTime(rs.getTimestamp("file_created_at")));
            long fileSizeBytes = rs.getLong("file_size_bytes");
            transcript.setFileSizeBytes(rs.wasNull() ? null : fileSizeBytes);
            transcript.setFileFormat(rs.getString("file_format"));
            transcript.setAudioTranscriptConsistent(getBooleanOrNull(rs.getObject("audio_transcript_consistent")));
            transcript.setVerifiedByMatric(rs.getString("verified_by_matric"));
            transcript.setVerifiedByName(rs.getString("verified_by_name"));
            transcript.setVerifiedAt(toLocalDateTime(rs.getTimestamp("verified_at")));
            return transcript;
        }, reelId).stream().findFirst();
    }

    /**
     * Loads all experiment rows connected to a reel transcript.
     */
    public List<ExperimentRecord> findExperimentsByReelId(int reelId) {
        String sql = """
                SELECT
                    e.experiment_id,
                    e.transcript_id,
                    lm.model_name,
                    lm.model_tag,
                    pt.technique_name,
                    e.rag_enabled,
                    e.status,
                    e.executed_at,
                    e.processing_time_ms,
                    nr.result_id AS nutrition_result_id,
                    nr.json_valid AS nutrition_json_valid,
                    nr.recipe_name AS nutrition_recipe_name,
                    nr.servings_estimated AS nutrition_servings_estimated,
                    nr.total_calories AS nutrition_total_calories,
                    nr.serving_calories AS nutrition_serving_calories,
                    nr.serving_protein_g AS nutrition_serving_protein_g,
                    nr.serving_carbohydrate_g AS nutrition_serving_carbohydrate_g,
                    nr.serving_total_fat_g AS nutrition_serving_total_fat_g,
                    nr.raw_json_output AS nutrition_raw_json_output
                FROM experiment e
                JOIN transcript t ON t.transcript_id = e.transcript_id
                JOIN llm_model lm ON lm.model_id = e.model_id
                JOIN prompt_technique pt ON pt.technique_id = e.technique_id
                LEFT JOIN nutrition_result nr ON nr.experiment_id = e.experiment_id
                WHERE t.reel_id = ?
                ORDER BY e.experiment_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            ExperimentRecord experiment = new ExperimentRecord();
            experiment.setExperimentId(rs.getInt("experiment_id"));
            experiment.setTranscriptId(rs.getInt("transcript_id"));
            experiment.setModelName(rs.getString("model_name"));
            experiment.setModelTag(rs.getString("model_tag"));
            experiment.setTechniqueName(rs.getString("technique_name"));
            experiment.setRagEnabled(rs.getBoolean("rag_enabled"));
            experiment.setStatus(rs.getString("status"));
            experiment.setExecutedAt(toLocalDateTime(rs.getTimestamp("executed_at")));
            long processingTimeMs = rs.getLong("processing_time_ms");
            experiment.setProcessingTimeMs(rs.wasNull() ? null : processingTimeMs);
            int nutritionResultId = rs.getInt("nutrition_result_id");
            experiment.setNutritionResultId(rs.wasNull() ? null : nutritionResultId);
            experiment.setNutritionJsonValid(getBooleanOrNull(rs.getObject("nutrition_json_valid")));
            experiment.setNutritionRecipeName(rs.getString("nutrition_recipe_name"));
            int servings = rs.getInt("nutrition_servings_estimated");
            experiment.setNutritionServingsEstimated(rs.wasNull() ? null : servings);
            experiment.setNutritionTotalCalories(getFloatOrNull(rs.getObject("nutrition_total_calories")));
            experiment.setNutritionServingCalories(getFloatOrNull(rs.getObject("nutrition_serving_calories")));
            experiment.setNutritionServingProteinG(getFloatOrNull(rs.getObject("nutrition_serving_protein_g")));
            experiment.setNutritionServingCarbohydrateG(getFloatOrNull(rs.getObject("nutrition_serving_carbohydrate_g")));
            experiment.setNutritionServingTotalFatG(getFloatOrNull(rs.getObject("nutrition_serving_total_fat_g")));
            experiment.setNutritionRawJsonOutput(rs.getString("nutrition_raw_json_output"));
            return experiment;
        }, reelId);
    }

    /**
     * Loads nutrition result summaries connected to a reel transcript.
     */
    public List<NutritionResultRecord> findNutritionResultsByReelId(int reelId) {
        String sql = """
                SELECT
                    nr.result_id,
                    nr.experiment_id,
                    lm.model_name,
                    lm.model_tag,
                    pt.technique_name,
                    nr.recipe_name,
                    nr.servings_estimated,
                    nr.serving_calories,
                    nr.serving_protein_g,
                    nr.serving_carbohydrate_g,
                    nr.serving_total_fat_g,
                    nr.total_calories,
                    nr.total_protein_g,
                    nr.total_carbohydrate_g,
                    nr.total_fat_g,
                    nr.json_valid,
                    nr.raw_json_output
                FROM nutrition_result nr
                JOIN experiment e ON e.experiment_id = nr.experiment_id
                JOIN transcript t ON t.transcript_id = e.transcript_id
                JOIN llm_model lm ON lm.model_id = e.model_id
                JOIN prompt_technique pt ON pt.technique_id = e.technique_id
                WHERE t.reel_id = ?
                ORDER BY nr.result_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            NutritionResultRecord result = new NutritionResultRecord();
            result.setResultId(rs.getInt("result_id"));
            result.setExperimentId(rs.getInt("experiment_id"));
            result.setModelName(rs.getString("model_name"));
            result.setModelTag(rs.getString("model_tag"));
            result.setTechniqueName(rs.getString("technique_name"));
            result.setRecipeName(rs.getString("recipe_name"));
            int servings = rs.getInt("servings_estimated");
            result.setServingsEstimated(rs.wasNull() ? null : servings);
            result.setServingCalories(getFloatOrNull(rs.getObject("serving_calories")));
            result.setServingProteinG(getFloatOrNull(rs.getObject("serving_protein_g")));
            result.setServingCarbohydrateG(getFloatOrNull(rs.getObject("serving_carbohydrate_g")));
            result.setServingTotalFatG(getFloatOrNull(rs.getObject("serving_total_fat_g")));
            result.setTotalCalories(getFloatOrNull(rs.getObject("total_calories")));
            result.setTotalProteinG(getFloatOrNull(rs.getObject("total_protein_g")));
            result.setTotalCarbohydrateG(getFloatOrNull(rs.getObject("total_carbohydrate_g")));
            result.setTotalFatG(getFloatOrNull(rs.getObject("total_fat_g")));
            result.setJsonValid(getBooleanOrNull(rs.getObject("json_valid")));
            result.setRawJsonOutput(rs.getString("raw_json_output"));
            return result;
        }, reelId);
    }

    /**
     * Loads extracted ingredient rows connected to a reel transcript.
     */
    public List<IngredientResultRecord> findIngredientResultsByReelId(int reelId) {
        String sql = """
                SELECT
                    ir.ingredient_id,
                    ir.result_id,
                    ir.name_original,
                    ir.name_en,
                    ir.quantity_value,
                    ir.unit_original,
                    ir.unit_en,
                    ir.estimated_weight_g,
                    ir.calories,
                    ir.total_fat_g,
                    ir.saturated_fat_g,
                    ir.cholesterol_mg,
                    ir.sodium_mg,
                    ir.total_carbohydrate_g,
                    ir.dietary_fiber_g,
                    ir.total_sugars_g,
                    ir.protein_g,
                    ir.vitamin_d_mcg,
                    ir.calcium_mg,
                    ir.iron_mg,
                    ir.potassium_mg
                FROM ingredient_result ir
                JOIN nutrition_result nr ON nr.result_id = ir.result_id
                JOIN experiment e ON e.experiment_id = nr.experiment_id
                JOIN transcript t ON t.transcript_id = e.transcript_id
                WHERE t.reel_id = ?
                ORDER BY nr.result_id, ir.ingredient_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            IngredientResultRecord ingredient = new IngredientResultRecord();
            ingredient.setIngredientResultId(rs.getInt("ingredient_id"));
            ingredient.setResultId(rs.getInt("result_id"));
            ingredient.setNameOriginal(rs.getString("name_original"));
            ingredient.setNameEn(rs.getString("name_en"));
            ingredient.setQuantityValue(getFloatOrNull(rs.getObject("quantity_value")));
            ingredient.setUnitOriginal(rs.getString("unit_original"));
            ingredient.setUnitEn(rs.getString("unit_en"));
            ingredient.setEstimatedWeightG(getFloatOrNull(rs.getObject("estimated_weight_g")));
            ingredient.setCalories(getFloatOrNull(rs.getObject("calories")));
            ingredient.setTotalFatG(getFloatOrNull(rs.getObject("total_fat_g")));
            ingredient.setSaturatedFatG(getFloatOrNull(rs.getObject("saturated_fat_g")));
            ingredient.setCholesterolMg(getFloatOrNull(rs.getObject("cholesterol_mg")));
            ingredient.setSodiumMg(getFloatOrNull(rs.getObject("sodium_mg")));
            ingredient.setTotalCarbohydrateG(getFloatOrNull(rs.getObject("total_carbohydrate_g")));
            ingredient.setDietaryFiberG(getFloatOrNull(rs.getObject("dietary_fiber_g")));
            ingredient.setTotalSugarsG(getFloatOrNull(rs.getObject("total_sugars_g")));
            ingredient.setProteinG(getFloatOrNull(rs.getObject("protein_g")));
            ingredient.setVitaminDMcg(getFloatOrNull(rs.getObject("vitamin_d_mcg")));
            ingredient.setCalciumMg(getFloatOrNull(rs.getObject("calcium_mg")));
            ingredient.setIronMg(getFloatOrNull(rs.getObject("iron_mg")));
            ingredient.setPotassiumMg(getFloatOrNull(rs.getObject("potassium_mg")));
            return ingredient;
        }, reelId);
    }

    /**
     * Loads model options for run forms and dropdowns.
     */
    public List<LlmModelOption> findModelOptions() {
        return jdbcTemplate.query("""
                SELECT model_id, model_name, model_tag
                FROM llm_model
                ORDER BY model_id
                """, (rs, rowNum) -> {
            LlmModelOption option = new LlmModelOption();
            option.setModelId(rs.getInt("model_id"));
            option.setModelName(rs.getString("model_name"));
            option.setModelTag(rs.getString("model_tag"));
            return option;
        });
    }

    /**
     * Loads prompt technique options in the expected display order.
     */
    public List<PromptTechniqueOption> findPromptTechniqueOptions() {
        return jdbcTemplate.query("""
                SELECT technique_id, technique_name
                FROM prompt_technique
                ORDER BY technique_id
                """, (rs, rowNum) -> {
            PromptTechniqueOption option = new PromptTechniqueOption();
            option.setTechniqueId(rs.getInt("technique_id"));
            option.setTechniqueName(rs.getString("technique_name"));
            return option;
        });
    }

    /**
     * Counts all rows in a trusted table name used by dashboard totals.
     */
    private int count(String tableName) {
        Integer value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return value == null ? 0 : value;
    }

    /**
     * Counts experiment rows for one status value.
     */
    private int countExperimentsByStatus(String status) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM experiment WHERE status = ?",
                Integer.class,
                status
        );
        return value == null ? 0 : value;
    }

    /**
     * Converts nullable SQL timestamps to LocalDateTime.
     */
    private java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /**
     * Converts JDBC boolean-like values to nullable booleans.
     */
    private Boolean getBooleanOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        return Boolean.valueOf(value.toString());
    }

    /**
     * Converts JDBC numeric values to nullable floats.
     */
    private Float getFloatOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return Float.parseFloat(value.toString());
    }

    /**
     * Converts JDBC numeric values to nullable doubles.
     */
    private Double getDoubleOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
}
