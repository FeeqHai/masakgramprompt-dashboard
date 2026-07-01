package my.utem.ftmk.masakgramprompt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReviewDashboardService {

    private static final int TRANSCRIPT_PREVIEW_LIMIT = 900;
    private static final Pattern COOKING_TERM_PATTERN = Pattern.compile(
            "(?i)\\b(bawang|serai|halia|lengkuas|santan|kunyit|cili|garam|gula|ayam|ikan|"
                    + "udang|daging|tepung|susu|daun|limau)\\b"
    );

    private static final List<NutritionField> NUTRITION_FIELDS = List.of(
            new NutritionField("calories", "Calories", "kcal"),
            new NutritionField("total_fat_g", "Total fat", "g"),
            new NutritionField("saturated_fat_g", "Saturated fat", "g"),
            new NutritionField("cholesterol_mg", "Cholesterol", "mg"),
            new NutritionField("sodium_mg", "Sodium", "mg"),
            new NutritionField("total_carbohydrate_g", "Total carbohydrate", "g"),
            new NutritionField("dietary_fiber_g", "Dietary fiber", "g"),
            new NutritionField("total_sugars_g", "Total sugars", "g"),
            new NutritionField("protein_g", "Protein", "g"),
            new NutritionField("vitamin_d_mcg", "Vitamin D", "mcg"),
            new NutritionField("calcium_mg", "Calcium", "mg"),
            new NutritionField("iron_mg", "Iron", "mg"),
            new NutritionField("potassium_mg", "Potassium", "mg")
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ReviewDashboardService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Loads the five model cards for the first review page.
     */
    public List<ModelCard> findModelCards() {
        String sql = """
                SELECT
                    lm.model_id,
                    lm.model_name,
                    lm.model_tag,
                    lm.provider,
                    lm.description,
                    COUNT(e.experiment_id) AS total_count,
                    SUM(CASE WHEN e.status = 'pending' THEN 1 ELSE 0 END) AS pending_count,
                    SUM(CASE WHEN e.status = 'running' THEN 1 ELSE 0 END) AS running_count,
                    SUM(CASE WHEN e.status = 'completed' THEN 1 ELSE 0 END) AS completed_count,
                    SUM(CASE WHEN e.status = 'failed' THEN 1 ELSE 0 END) AS failed_count
                FROM llm_model lm
                LEFT JOIN experiment e ON e.model_id = lm.model_id
                GROUP BY
                    lm.model_id,
                    lm.model_name,
                    lm.model_tag,
                    lm.provider,
                    lm.description
                ORDER BY lm.model_id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ModelCard(
                rs.getInt("model_id"),
                rs.getString("model_name"),
                rs.getString("model_tag"),
                rs.getString("provider"),
                rs.getString("description"),
                mapStatusCount(rs)
        ));
    }

    public Optional<ModelCard> findModelCard(int modelId) {
        return findModelCards().stream()
                .filter(model -> model.modelId() == modelId)
                .findFirst();
    }

    /**
     * Loads prompt technique cards and status totals for the selected model.
     */
    public List<TechniqueCard> findTechniqueCards(int modelId) {
        String sql = """
                SELECT
                    pt.technique_id,
                    pt.technique_name,
                    pt.description,
                    COUNT(e.experiment_id) AS total_count,
                    SUM(CASE WHEN e.status = 'pending' THEN 1 ELSE 0 END) AS pending_count,
                    SUM(CASE WHEN e.status = 'running' THEN 1 ELSE 0 END) AS running_count,
                    SUM(CASE WHEN e.status = 'completed' THEN 1 ELSE 0 END) AS completed_count,
                    SUM(CASE WHEN e.status = 'failed' THEN 1 ELSE 0 END) AS failed_count
                FROM prompt_technique pt
                LEFT JOIN experiment e
                    ON e.technique_id = pt.technique_id
                   AND e.model_id = ?
                GROUP BY pt.technique_id, pt.technique_name, pt.description
                ORDER BY pt.technique_id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new TechniqueCard(
                rs.getInt("technique_id"),
                rs.getString("technique_name"),
                rs.getString("description"),
                mapStatusCount(rs)
        ), modelId);
    }

    public Optional<TechniqueCard> findTechniqueCard(int modelId, int techniqueId) {
        return findTechniqueCards(modelId).stream()
                .filter(technique -> technique.techniqueId() == techniqueId)
                .findFirst();
    }

    /**
     * Shows transcript/reel rows for one selected model and prompt technique.
     */
    public List<ReelReviewRow> findReelRows(int modelId, int techniqueId) {
        String sql = """
                SELECT
                    r.reel_id,
                    r.reel_id_instagram,
                    r.reel_url,
                    i.instagram_account,
                    t.transcript_id,
                    t.file_name AS transcript_file_name,
                    t.audio_transcript_consistent,
                    gtr.gt_reel_id,
                    COALESCE(gt_count.ingredient_count, 0) AS gt_ingredient_count,
                    e.experiment_id,
                    e.status,
                    nr.result_id,
                    nr.json_valid
                FROM reel r
                JOIN influencer i ON i.influencer_id = r.influencer_id
                LEFT JOIN transcript t ON t.reel_id = r.reel_id
                LEFT JOIN ground_truth_reel gtr ON gtr.transcript_id = t.transcript_id
                LEFT JOIN (
                    SELECT gt_reel_id, COUNT(*) AS ingredient_count
                    FROM ground_truth_ingredient
                    GROUP BY gt_reel_id
                ) gt_count ON gt_count.gt_reel_id = gtr.gt_reel_id
                LEFT JOIN (
                    SELECT e1.*
                    FROM experiment e1
                    JOIN (
                        SELECT transcript_id, MAX(experiment_id) AS experiment_id
                        FROM experiment
                        WHERE model_id = ?
                          AND technique_id = ?
                          AND rag_enabled = FALSE
                        GROUP BY transcript_id
                    ) latest ON latest.experiment_id = e1.experiment_id
                ) e ON e.transcript_id = t.transcript_id
                LEFT JOIN nutrition_result nr ON nr.experiment_id = e.experiment_id
                ORDER BY r.reel_id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ReelReviewRow(
                rs.getInt("reel_id"),
                rs.getString("reel_id_instagram"),
                rs.getString("reel_url"),
                rs.getString("instagram_account"),
                getInteger(rs, "transcript_id"),
                rs.getString("transcript_file_name"),
                getBoolean(rs, "audio_transcript_consistent"),
                getInteger(rs, "gt_reel_id") != null,
                rs.getInt("gt_ingredient_count"),
                getInteger(rs, "experiment_id"),
                rs.getString("status"),
                getInteger(rs, "result_id"),
                getBoolean(rs, "json_valid")
        ), modelId, techniqueId);
    }

    /**
     * Builds the main fact-sheet page for a selected reel/model/technique.
     */
    public ResultPage loadResultPage(int modelId, int techniqueId, int reelId) {
        ModelCard model = findModelCard(modelId)
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelId));
        TechniqueCard technique = findTechniqueCard(modelId, techniqueId)
                .orElseThrow(() -> new IllegalArgumentException("Technique not found: " + techniqueId));
        ReelDetail reel = loadReelDetail(reelId)
                .orElseThrow(() -> new IllegalArgumentException("Reel not found: " + reelId));
        Integer transcriptId = reel.transcriptId();
        ExperimentDetail experiment = transcriptId == null
                ? ExperimentDetail.empty(null, modelId, techniqueId)
                : findLatestExperiment(transcriptId, modelId, techniqueId)
                        .orElse(ExperimentDetail.empty(transcriptId, modelId, techniqueId));
        GroundTruthInfo groundTruth = loadGroundTruthInfo(reel.transcriptId());
        List<IngredientDetail> groundTruthIngredients = transcriptId == null
                ? List.of()
                : loadGroundTruthIngredients(transcriptId);
        List<IngredientDetail> aiIngredients = experiment.experimentId() == null
                ? List.of()
                : loadAiIngredients(experiment.experimentId());
        List<IngredientComparisonRow> ingredientRows = compareIngredients(
                groundTruthIngredients,
                aiIngredients
        );
        NutritionValues groundTruthTotals = sumNutrition(groundTruthIngredients);
        NutritionValues aiTotals = experiment.totalNutrition().isEmpty()
                ? sumNutrition(aiIngredients)
                : experiment.totalNutrition();
        List<NutritionComparisonRow> nutritionRows = compareNutrition(groundTruthTotals, aiTotals);
        EvaluationMetrics metrics = calculateMetrics(ingredientRows, aiIngredients, experiment);

        return new ResultPage(
                model,
                technique,
                reel,
                experiment,
                groundTruth,
                highlightTranscript(readTranscriptPreview(reel.transcriptFilePath(), reel.transcriptFileName())),
                ingredientRows,
                nutritionRows,
                metrics
        );
    }

    public List<NutritionField> nutritionFields() {
        return NUTRITION_FIELDS;
    }

    public List<CompletedExperimentKey> findCompletedExperimentKeys() {
        String sql = """
                SELECT
                    e.experiment_id,
                    e.transcript_id,
                    e.model_id,
                    e.technique_id,
                    lm.model_name,
                    pt.technique_name
                FROM experiment e
                JOIN llm_model lm ON lm.model_id = e.model_id
                JOIN prompt_technique pt ON pt.technique_id = e.technique_id
                WHERE e.status = 'completed'
                ORDER BY e.experiment_id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CompletedExperimentKey(
                rs.getInt("experiment_id"),
                rs.getInt("transcript_id"),
                rs.getInt("model_id"),
                rs.getInt("technique_id"),
                rs.getString("model_name"),
                rs.getString("technique_name")
        ));
    }

    public Optional<ExperimentDetail> findExperimentById(int experimentId) {
        String sql = experimentSelect("""
                WHERE e.experiment_id = ?
                LIMIT 1
                """);
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapExperiment(rs), experimentId)
                .stream()
                .findFirst();
    }

    public List<IngredientDetail> loadGroundTruthIngredients(int transcriptId) {
        String sql = """
                SELECT
                    gti.gt_ingredient_id,
                    gti.name_original,
                    gti.name_en,
                    gti.quantity_expression,
                    gti.quantity_category,
                    gti.quantity_unit_culinary,
                    gti.quantity_value_culinary,
                    gti.language_mentioned,
                    gti.estimated_weight_g,
                    gti.calories,
                    gti.total_fat_g,
                    gti.saturated_fat_g,
                    gti.cholesterol_mg,
                    gti.sodium_mg,
                    gti.total_carbohydrate_g,
                    gti.dietary_fiber_g,
                    gti.total_sugars_g,
                    gti.protein_g,
                    gti.vitamin_d_mcg,
                    gti.calcium_mg,
                    gti.iron_mg,
                    gti.potassium_mg,
                    gti.annotation_layer
                FROM ground_truth_reel gtr
                JOIN ground_truth_ingredient gti ON gti.gt_reel_id = gtr.gt_reel_id
                WHERE gtr.transcript_id = ?
                ORDER BY gti.gt_ingredient_id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapGroundTruthIngredient(rs), transcriptId);
    }

    public List<IngredientDetail> loadAiIngredients(int experimentId) {
        String sql = """
                SELECT
                    ir.ingredient_id,
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
                FROM nutrition_result nr
                JOIN ingredient_result ir ON ir.result_id = nr.result_id
                WHERE nr.experiment_id = ?
                ORDER BY ir.ingredient_id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapAiIngredient(rs), experimentId);
    }

    public List<IngredientComparisonRow> compareIngredients(
            List<IngredientDetail> groundTruth,
            List<IngredientDetail> aiIngredients
    ) {
        List<IngredientComparisonRow> rows = new ArrayList<>();
        List<IngredientDetail> unmatchedAi = new ArrayList<>(aiIngredients);

        for (IngredientDetail gt : groundTruth) {
            MatchResult match = findBestMatch(gt, unmatchedAi);
            if (match.ingredient() == null) {
                rows.add(new IngredientComparisonRow(rows.size() + 1, "Missed by AI", "pending", gt, null));
                continue;
            }
            unmatchedAi.remove(match.ingredient());
            rows.add(new IngredientComparisonRow(
                    rows.size() + 1,
                    match.status(),
                    match.cssClass(),
                    gt,
                    match.ingredient()
            ));
        }

        for (IngredientDetail ai : unmatchedAi) {
            rows.add(new IngredientComparisonRow(
                    rows.size() + 1,
                    "Extra AI ingredient / possible hallucination",
                    "missing",
                    null,
                    ai
            ));
        }

        return rows;
    }

    public NutritionValues sumNutrition(List<IngredientDetail> ingredients) {
        NutritionValues total = NutritionValues.empty();
        for (IngredientDetail ingredient : ingredients) {
            total = total.plus(ingredient.nutrition());
        }
        return total;
    }

    public List<NutritionComparisonRow> compareNutrition(NutritionValues groundTruth, NutritionValues ai) {
        List<NutritionComparisonRow> rows = new ArrayList<>();
        for (NutritionField field : NUTRITION_FIELDS) {
            Double gtValue = groundTruth.value(field.key());
            Double aiValue = ai.value(field.key());
            Double difference = gtValue == null || aiValue == null ? null : aiValue - gtValue;
            Double percentageError = null;
            if (gtValue != null && gtValue != 0.0 && difference != null) {
                percentageError = Math.abs(difference) / Math.abs(gtValue) * 100.0;
            }
            rows.add(new NutritionComparisonRow(
                    field.key(),
                    field.label(),
                    field.unit(),
                    gtValue,
                    aiValue,
                    difference,
                    percentageError
            ));
        }
        return rows;
    }

    public EvaluationMetrics calculateMetrics(
            List<IngredientComparisonRow> rows,
            List<IngredientDetail> aiIngredients,
            ExperimentDetail experiment
    ) {
        long groundTruthCount = rows.stream().filter(row -> row.groundTruth() != null).count();
        long aiCount = rows.stream().filter(row -> row.ai() != null).count();
        long matchedCount = rows.stream().filter(IngredientComparisonRow::matched).count();
        long missedCount = rows.stream().filter(row -> row.status().equals("Missed by AI")).count();
        long extraCount = rows.stream().filter(row -> row.status().startsWith("Extra AI")).count();
        double precision = aiCount == 0 ? 0.0 : (double) matchedCount / aiCount;
        double recall = groundTruthCount == 0 ? 0.0 : (double) matchedCount / groundTruthCount;
        double f1 = precision + recall == 0 ? 0.0 : (2.0 * precision * recall) / (precision + recall);
        boolean jsonValid = experiment.jsonValid() != null
                ? experiment.jsonValid()
                : isRawJsonValid(experiment.rawJsonOutput());

        return new EvaluationMetrics(
                (int) groundTruthCount,
                aiIngredients.size(),
                (int) matchedCount,
                (int) missedCount,
                (int) extraCount,
                round(precision),
                round(recall),
                round(f1),
                jsonValid,
                (int) extraCount
        );
    }

    public PerformancePage loadPerformancePage() {
        String sql = """
                SELECT
                    lm.model_id,
                    lm.model_name,
                    lm.model_tag,
                    pt.technique_id,
                    pt.technique_name,
                    COUNT(e.experiment_id) AS total_count,
                    SUM(CASE WHEN e.status = 'pending' THEN 1 ELSE 0 END) AS pending_count,
                    SUM(CASE WHEN e.status = 'running' THEN 1 ELSE 0 END) AS running_count,
                    SUM(CASE WHEN e.status = 'completed' THEN 1 ELSE 0 END) AS completed_count,
                    SUM(CASE WHEN e.status = 'failed' THEN 1 ELSE 0 END) AS failed_count,
                    ROUND(AVG(CASE WHEN e.status = 'completed' THEN e.processing_time_ms END) / 1000, 1)
                        AS avg_seconds,
                    ROUND(MIN(CASE WHEN e.status = 'completed' THEN e.processing_time_ms END) / 1000, 1)
                        AS min_seconds,
                    ROUND(MAX(CASE WHEN e.status = 'completed' THEN e.processing_time_ms END) / 1000, 1)
                        AS max_seconds
                FROM llm_model lm
                CROSS JOIN prompt_technique pt
                LEFT JOIN experiment e
                    ON e.model_id = lm.model_id
                   AND e.technique_id = pt.technique_id
                GROUP BY lm.model_id, lm.model_name, lm.model_tag, pt.technique_id, pt.technique_name
                ORDER BY lm.model_id, pt.technique_id
                """;
        List<PerformanceTechniqueRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new PerformanceTechniqueRow(
                rs.getInt("model_id"),
                rs.getString("model_name"),
                rs.getString("model_tag"),
                rs.getInt("technique_id"),
                rs.getString("technique_name"),
                mapStatusCount(rs),
                getDouble(rs, "avg_seconds"),
                getDouble(rs, "min_seconds"),
                getDouble(rs, "max_seconds")
        ));
        Map<Integer, PerformanceModelGroup> groups = new LinkedHashMap<>();
        for (PerformanceTechniqueRow row : rows) {
            groups.computeIfAbsent(
                    row.modelId(),
                    key -> new PerformanceModelGroup(row.modelId(), row.modelName(), row.modelTag(), new ArrayList<>())
            ).techniques().add(row);
        }
        long totalCompleted = rows.stream().mapToLong(row -> row.status().completed()).sum();
        Optional<PerformanceTechniqueRow> fastest = rows.stream()
                .filter(row -> row.averageSeconds() != null)
                .min(Comparator.comparing(PerformanceTechniqueRow::averageSeconds));
        Optional<PerformanceTechniqueRow> slowest = rows.stream()
                .filter(row -> row.averageSeconds() != null)
                .max(Comparator.comparing(PerformanceTechniqueRow::averageSeconds));
        return new PerformancePage(
                totalCompleted,
                fastest.map(PerformanceTechniqueRow::modelName).orElse("Not available"),
                slowest.map(PerformanceTechniqueRow::modelName).orElse("Not available"),
                rows.stream()
                        .filter(row -> row.averageSeconds() != null)
                        .mapToDouble(PerformanceTechniqueRow::averageSeconds)
                        .average()
                        .stream()
                        .mapToObj(value -> String.format(Locale.ROOT, "%.1fs", value))
                        .findFirst()
                        .orElse("Not available"),
                new ArrayList<>(groups.values())
        );
    }

    private Optional<ReelDetail> loadReelDetail(int reelId) {
        String sql = """
                SELECT
                    r.reel_id,
                    r.reel_id_instagram,
                    r.reel_url,
                    i.name AS influencer_name,
                    i.instagram_account,
                    t.transcript_id,
                    t.file_name AS transcript_file_name,
                    t.file_path AS transcript_file_path,
                    t.audio_transcript_consistent,
                    t.verified_at
                FROM reel r
                JOIN influencer i ON i.influencer_id = r.influencer_id
                LEFT JOIN transcript t ON t.reel_id = r.reel_id
                WHERE r.reel_id = ?
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ReelDetail(
                rs.getInt("reel_id"),
                rs.getString("reel_id_instagram"),
                rs.getString("reel_url"),
                rs.getString("influencer_name"),
                rs.getString("instagram_account"),
                getInteger(rs, "transcript_id"),
                rs.getString("transcript_file_name"),
                rs.getString("transcript_file_path"),
                getBoolean(rs, "audio_transcript_consistent"),
                toLocalDateTime(rs.getTimestamp("verified_at"))
        ), reelId).stream().findFirst();
    }

    private Optional<ExperimentDetail> findLatestExperiment(int transcriptId, int modelId, int techniqueId) {
        String sql = experimentSelect("""
                WHERE e.transcript_id = ?
                  AND e.model_id = ?
                  AND e.technique_id = ?
                  AND e.rag_enabled = FALSE
                ORDER BY e.experiment_id DESC
                LIMIT 1
                """);
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapExperiment(rs), transcriptId, modelId, techniqueId)
                .stream()
                .findFirst();
    }

    private String experimentSelect(String whereClause) {
        return """
                SELECT
                    e.experiment_id,
                    e.transcript_id,
                    e.model_id,
                    e.technique_id,
                    e.status,
                    e.executed_at,
                    e.started_at,
                    e.finished_at,
                    e.processing_time_ms,
                    nr.result_id,
                    nr.recipe_name,
                    nr.servings_estimated,
                    nr.total_calories,
                    nr.total_fat_g,
                    nr.total_saturated_fat_g,
                    nr.total_cholesterol_mg,
                    nr.total_sodium_mg,
                    nr.total_carbohydrate_g,
                    nr.total_fiber_g,
                    nr.total_sugars_g,
                    nr.total_protein_g,
                    nr.total_vitamin_d_mcg,
                    nr.total_calcium_mg,
                    nr.total_iron_mg,
                    nr.total_potassium_mg,
                    nr.raw_json_output,
                    nr.json_valid
                FROM experiment e
                LEFT JOIN nutrition_result nr ON nr.experiment_id = e.experiment_id
                %s
                """.formatted(whereClause);
    }

    private GroundTruthInfo loadGroundTruthInfo(Integer transcriptId) {
        if (transcriptId == null) {
            return GroundTruthInfo.missing();
        }
        String sql = """
                SELECT
                    gtr.gt_reel_id,
                    gtr.annotator_matric,
                    gtr.annotator_name,
                    gtr.annotated_at,
                    COUNT(gti.gt_ingredient_id) AS ingredient_count
                FROM ground_truth_reel gtr
                LEFT JOIN ground_truth_ingredient gti ON gti.gt_reel_id = gtr.gt_reel_id
                WHERE gtr.transcript_id = ?
                GROUP BY gtr.gt_reel_id, gtr.annotator_matric, gtr.annotator_name, gtr.annotated_at
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new GroundTruthInfo(
                true,
                getInteger(rs, "gt_reel_id"),
                rs.getString("annotator_matric"),
                rs.getString("annotator_name"),
                toLocalDateTime(rs.getTimestamp("annotated_at")),
                rs.getInt("ingredient_count")
        ), transcriptId).stream().findFirst().orElse(GroundTruthInfo.missing());
    }

    private MatchResult findBestMatch(IngredientDetail gt, List<IngredientDetail> aiIngredients) {
        for (IngredientDetail ai : aiIngredients) {
            if (matchesExactly(gt.nameEn(), ai.nameEn()) || matchesExactly(gt.nameOriginal(), ai.nameOriginal())) {
                return new MatchResult(ai, "Matched", "ok");
            }
        }
        for (IngredientDetail ai : aiIngredients) {
            if (matchesPartially(gt.nameEn(), ai.nameEn()) || matchesPartially(gt.nameOriginal(), ai.nameOriginal())) {
                return new MatchResult(ai, "Partial match", "neutral");
            }
        }
        return new MatchResult(null, "Missed by AI", "pending");
    }

    private boolean matchesExactly(String left, String right) {
        String normalizedLeft = normalizeIngredientName(left);
        String normalizedRight = normalizeIngredientName(right);
        return !normalizedLeft.isBlank() && normalizedLeft.equals(normalizedRight);
    }

    private boolean matchesPartially(String left, String right) {
        String normalizedLeft = normalizeIngredientName(left);
        String normalizedRight = normalizeIngredientName(right);
        if (normalizedLeft.length() < 4 || normalizedRight.length() < 4) {
            return false;
        }
        return normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft);
    }

    private String normalizeIngredientName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String readTranscriptPreview(String filePath, String fileName) {
        if (filePath != null && !filePath.isBlank()) {
            try {
                Path path = Path.of(filePath);
                if (Files.exists(path)) {
                    String text = cleanTranscript(Files.readString(path, StandardCharsets.UTF_8));
                    return text.length() <= TRANSCRIPT_PREVIEW_LIMIT
                            ? text
                            : text.substring(0, TRANSCRIPT_PREVIEW_LIMIT) + "...";
                }
            } catch (IOException | RuntimeException ex) {
                return "Transcript file could not be read: " + ex.getMessage();
            }
        }
        return "Transcript text is not stored in the database. File reference: "
                + (fileName == null ? "Not available" : fileName);
    }

    private String cleanTranscript(String transcript) {
        int divider = transcript.indexOf("=====================================");
        if (divider < 0) {
            return transcript.trim();
        }
        return transcript.substring(divider + "=====================================".length()).trim();
    }

    private List<TranscriptSegment> highlightTranscript(String text) {
        List<TranscriptSegment> segments = new ArrayList<>();
        Matcher matcher = COOKING_TERM_PATTERN.matcher(text);
        int currentIndex = 0;
        while (matcher.find()) {
            if (matcher.start() > currentIndex) {
                segments.add(new TranscriptSegment(text.substring(currentIndex, matcher.start()), false));
            }
            segments.add(new TranscriptSegment(matcher.group(), true));
            currentIndex = matcher.end();
        }
        if (currentIndex < text.length()) {
            segments.add(new TranscriptSegment(text.substring(currentIndex), false));
        }
        return segments.isEmpty() ? List.of(new TranscriptSegment(text, false)) : segments;
    }

    private boolean isRawJsonValid(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return false;
        }
        try {
            objectMapper.readTree(rawJson);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private StatusCount mapStatusCount(ResultSet rs) throws SQLException {
        return new StatusCount(
                rs.getLong("pending_count"),
                rs.getLong("running_count"),
                rs.getLong("completed_count"),
                rs.getLong("failed_count"),
                rs.getLong("total_count")
        );
    }

    private ExperimentDetail mapExperiment(ResultSet rs) throws SQLException {
        return new ExperimentDetail(
                getInteger(rs, "experiment_id"),
                getInteger(rs, "transcript_id"),
                getInteger(rs, "model_id"),
                getInteger(rs, "technique_id"),
                rs.getString("status"),
                toLocalDateTime(rs.getTimestamp("executed_at")),
                toLocalDateTime(rs.getTimestamp("started_at")),
                toLocalDateTime(rs.getTimestamp("finished_at")),
                getLong(rs, "processing_time_ms"),
                getInteger(rs, "result_id"),
                rs.getString("recipe_name"),
                getInteger(rs, "servings_estimated"),
                new NutritionValues(
                        getDouble(rs, "total_calories"),
                        getDouble(rs, "total_fat_g"),
                        getDouble(rs, "total_saturated_fat_g"),
                        getDouble(rs, "total_cholesterol_mg"),
                        getDouble(rs, "total_sodium_mg"),
                        getDouble(rs, "total_carbohydrate_g"),
                        getDouble(rs, "total_fiber_g"),
                        getDouble(rs, "total_sugars_g"),
                        getDouble(rs, "total_protein_g"),
                        getDouble(rs, "total_vitamin_d_mcg"),
                        getDouble(rs, "total_calcium_mg"),
                        getDouble(rs, "total_iron_mg"),
                        getDouble(rs, "total_potassium_mg")
                ),
                rs.getString("raw_json_output"),
                getBoolean(rs, "json_valid")
        );
    }

    private IngredientDetail mapGroundTruthIngredient(ResultSet rs) throws SQLException {
        return new IngredientDetail(
                getInteger(rs, "gt_ingredient_id"),
                rs.getString("name_original"),
                rs.getString("name_en"),
                rs.getString("quantity_expression"),
                rs.getString("quantity_category"),
                rs.getString("quantity_unit_culinary"),
                null,
                getDouble(rs, "quantity_value_culinary"),
                rs.getString("language_mentioned"),
                getDouble(rs, "estimated_weight_g"),
                null,
                rs.getString("annotation_layer"),
                mapNutritionValues(rs)
        );
    }

    private IngredientDetail mapAiIngredient(ResultSet rs) throws SQLException {
        return new IngredientDetail(
                getInteger(rs, "ingredient_id"),
                rs.getString("name_original"),
                rs.getString("name_en"),
                null,
                null,
                rs.getString("unit_original"),
                rs.getString("unit_en"),
                getDouble(rs, "quantity_value"),
                null,
                getDouble(rs, "estimated_weight_g"),
                null,
                null,
                mapNutritionValues(rs)
        );
    }

    private NutritionValues mapNutritionValues(ResultSet rs) throws SQLException {
        return new NutritionValues(
                getDouble(rs, "calories"),
                getDouble(rs, "total_fat_g"),
                getDouble(rs, "saturated_fat_g"),
                getDouble(rs, "cholesterol_mg"),
                getDouble(rs, "sodium_mg"),
                getDouble(rs, "total_carbohydrate_g"),
                getDouble(rs, "dietary_fiber_g"),
                getDouble(rs, "total_sugars_g"),
                getDouble(rs, "protein_g"),
                getDouble(rs, "vitamin_d_mcg"),
                getDouble(rs, "calcium_mg"),
                getDouble(rs, "iron_mg"),
                getDouble(rs, "potassium_mg")
        );
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Integer getInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private Long getLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private Double getDouble(ResultSet rs, String columnName) throws SQLException {
        double value = rs.getDouble(columnName);
        return rs.wasNull() ? null : value;
    }

    private Boolean getBoolean(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
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

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record MatchResult(IngredientDetail ingredient, String status, String cssClass) {
    }

    public record NutritionField(String key, String label, String unit) {
    }

    public record StatusCount(long pending, long running, long completed, long failed, long total) {
    }

    public record ModelCard(
            int modelId,
            String modelName,
            String modelTag,
            String provider,
            String description,
            StatusCount status
    ) {
    }

    public record TechniqueCard(int techniqueId, String techniqueName, String description, StatusCount status) {
    }

    public record ReelReviewRow(
            int reelId,
            String instagramReelId,
            String reelUrl,
            String influencerAccount,
            Integer transcriptId,
            String transcriptFileName,
            Boolean transcriptConsistent,
            boolean groundTruthAvailable,
            int groundTruthIngredientCount,
            Integer experimentId,
            String experimentStatus,
            Integer resultId,
            Boolean jsonValid
    ) {
        public String transcriptStatusLabel() {
            if (transcriptId == null) {
                return "Missing";
            }
            return Boolean.TRUE.equals(transcriptConsistent) ? "Verified" : "Available";
        }

        public String resultStatusLabel() {
            if (resultId != null) {
                return "Result available";
            }
            return experimentStatus == null ? "No result" : experimentStatus;
        }

        public boolean running() {
            return "running".equalsIgnoreCase(experimentStatus);
        }

        public boolean canRunExperiment() {
            return transcriptId != null && !running();
        }

        public String runOptionLabel() {
            if (running()) {
                return "running";
            }
            return resultId == null ? "not tested" : "result available";
        }
    }

    public record ReelDetail(
            int reelId,
            String instagramReelId,
            String reelUrl,
            String influencerName,
            String influencerAccount,
            Integer transcriptId,
            String transcriptFileName,
            String transcriptFilePath,
            Boolean transcriptConsistent,
            LocalDateTime transcriptVerifiedAt
    ) {
        public String transcriptStatusLabel() {
            if (transcriptId == null) {
                return "Missing";
            }
            return Boolean.TRUE.equals(transcriptConsistent) ? "Verified" : "Available";
        }
    }

    public record ExperimentDetail(
            Integer experimentId,
            Integer transcriptId,
            Integer modelId,
            Integer techniqueId,
            String status,
            LocalDateTime executedAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Long processingTimeMs,
            Integer resultId,
            String recipeName,
            Integer servingsEstimated,
            NutritionValues totalNutrition,
            String rawJsonOutput,
            Boolean jsonValid
    ) {
        public static ExperimentDetail empty(Integer transcriptId, int modelId, int techniqueId) {
            return new ExperimentDetail(
                    null,
                    transcriptId,
                    modelId,
                    techniqueId,
                    "No experiment",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    NutritionValues.empty(),
                    null,
                    null
            );
        }

        public String processingTimeLabel() {
            return processingTimeMs == null
                    ? "Not available"
                    : String.format(Locale.ROOT, "%.1fs", processingTimeMs / 1000.0);
        }
    }

    public record GroundTruthInfo(
            boolean available,
            Integer gtReelId,
            String annotatorMatric,
            String annotatorName,
            LocalDateTime annotatedAt,
            int ingredientCount
    ) {
        public static GroundTruthInfo missing() {
            return new GroundTruthInfo(false, null, null, null, null, 0);
        }
    }

    public record NutritionValues(
            Double calories,
            Double totalFatG,
            Double saturatedFatG,
            Double cholesterolMg,
            Double sodiumMg,
            Double totalCarbohydrateG,
            Double dietaryFiberG,
            Double totalSugarsG,
            Double proteinG,
            Double vitaminDMcg,
            Double calciumMg,
            Double ironMg,
            Double potassiumMg
    ) {
        public static NutritionValues empty() {
            return new NutritionValues(null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        public NutritionValues plus(NutritionValues other) {
            return new NutritionValues(
                    add(calories, other.calories),
                    add(totalFatG, other.totalFatG),
                    add(saturatedFatG, other.saturatedFatG),
                    add(cholesterolMg, other.cholesterolMg),
                    add(sodiumMg, other.sodiumMg),
                    add(totalCarbohydrateG, other.totalCarbohydrateG),
                    add(dietaryFiberG, other.dietaryFiberG),
                    add(totalSugarsG, other.totalSugarsG),
                    add(proteinG, other.proteinG),
                    add(vitaminDMcg, other.vitaminDMcg),
                    add(calciumMg, other.calciumMg),
                    add(ironMg, other.ironMg),
                    add(potassiumMg, other.potassiumMg)
            );
        }

        public Double value(String key) {
            return switch (key) {
                case "calories" -> calories;
                case "total_fat_g" -> totalFatG;
                case "saturated_fat_g" -> saturatedFatG;
                case "cholesterol_mg" -> cholesterolMg;
                case "sodium_mg" -> sodiumMg;
                case "total_carbohydrate_g" -> totalCarbohydrateG;
                case "dietary_fiber_g" -> dietaryFiberG;
                case "total_sugars_g" -> totalSugarsG;
                case "protein_g" -> proteinG;
                case "vitamin_d_mcg" -> vitaminDMcg;
                case "calcium_mg" -> calciumMg;
                case "iron_mg" -> ironMg;
                case "potassium_mg" -> potassiumMg;
                default -> null;
            };
        }

        public boolean isEmpty() {
            return calories == null
                    && totalFatG == null
                    && saturatedFatG == null
                    && cholesterolMg == null
                    && sodiumMg == null
                    && totalCarbohydrateG == null
                    && dietaryFiberG == null
                    && totalSugarsG == null
                    && proteinG == null
                    && vitaminDMcg == null
                    && calciumMg == null
                    && ironMg == null
                    && potassiumMg == null;
        }

        private static Double add(Double left, Double right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return left + right;
        }
    }

    public record IngredientDetail(
            Integer id,
            String nameOriginal,
            String nameEn,
            String quantityExpression,
            String quantityCategory,
            String quantityUnit,
            String quantityUnitEn,
            Double quantityValue,
            String languageMentioned,
            Double estimatedWeightG,
            Boolean hallucinated,
            String annotationLayer,
            NutritionValues nutrition
    ) {
        public String displayQuantity() {
            List<String> parts = new ArrayList<>();
            if (quantityExpression != null && !quantityExpression.isBlank()) {
                parts.add(quantityExpression);
            }
            if (quantityValue != null) {
                parts.add(String.format(Locale.ROOT, "%.2f", quantityValue));
            }
            if (quantityUnit != null && !quantityUnit.isBlank()) {
                parts.add(quantityUnit);
            }
            return parts.isEmpty() ? "Not available" : String.join(" ", parts);
        }
    }

    public record IngredientComparisonRow(
            int rowNumber,
            String status,
            String cssClass,
            IngredientDetail groundTruth,
            IngredientDetail ai
    ) {
        public boolean matched() {
            return status.equals("Matched") || status.equals("Partial match");
        }
    }

    public record NutritionComparisonRow(
            String key,
            String label,
            String unit,
            Double groundTruthValue,
            Double aiValue,
            Double difference,
            Double percentageError
    ) {
    }

    public record EvaluationMetrics(
            int groundTruthCount,
            int aiCount,
            int matchedCount,
            int missedCount,
            int extraCount,
            double precision,
            double recall,
            double f1,
            boolean jsonValid,
            int hallucinationCount
    ) {
    }

    public record TranscriptSegment(String text, boolean highlighted) {
    }

    public record ResultPage(
            ModelCard model,
            TechniqueCard technique,
            ReelDetail reel,
            ExperimentDetail experiment,
            GroundTruthInfo groundTruth,
            List<TranscriptSegment> transcriptSegments,
            List<IngredientComparisonRow> ingredientRows,
            List<NutritionComparisonRow> nutritionRows,
            EvaluationMetrics metrics
    ) {
    }

    public record CompletedExperimentKey(
            int experimentId,
            int transcriptId,
            int modelId,
            int techniqueId,
            String modelName,
            String techniqueName
    ) {
    }

    public record PerformanceTechniqueRow(
            int modelId,
            String modelName,
            String modelTag,
            int techniqueId,
            String techniqueName,
            StatusCount status,
            Double averageSeconds,
            Double minSeconds,
            Double maxSeconds
    ) {
    }

    public record PerformanceModelGroup(
            int modelId,
            String modelName,
            String modelTag,
            List<PerformanceTechniqueRow> techniques
    ) {
    }

    public record PerformancePage(
            long totalCompletedRuns,
            String fastestModel,
            String slowestModel,
            String averageProcessingTime,
            List<PerformanceModelGroup> modelGroups
    ) {
    }
}
