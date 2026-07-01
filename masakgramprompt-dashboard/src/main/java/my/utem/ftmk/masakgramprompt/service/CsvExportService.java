package my.utem.ftmk.masakgramprompt.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Builds CSV files for the export page and the per-result fact sheet download.
 */
@Service
public class CsvExportService {

    private final ReviewDashboardService reviewDashboardService;
    private final EvaluationService evaluationService;

    private final Map<String, ExportDefinition> definitions = Map.ofEntries(
            entry("layer1a_exact_match.csv", "Exact ingredient matches",
                    "Ground truth and AI ingredient names with matched/missed/extra status."),
            entry("layer1b_text_similarity.csv", "Text similarity helper",
                    "Normalized names for later fuzzy, BLEU, or ROUGE analysis."),
            entry("layer2a_numeric_quantity.csv", "Quantity and weight errors",
                    "Compares quantity values and estimated ingredient weights."),
            entry("layer2b_numeric_nutrition.csv", "Ingredient nutrition errors",
                    "Per-ingredient calorie, protein, fat, and carbohydrate differences."),
            entry("layer2c_nutrition_totals.csv", "Recipe nutrition totals",
                    "Ground truth recipe totals, AI totals, and differences."),
            entry("layer3a_json_validity.csv", "JSON validity",
                    "One row per completed experiment showing valid or invalid JSON."),
            entry("layer3b_hallucination.csv", "Hallucination check",
                    "AI ingredients inferred as possible hallucinations."),
            entry("layer3c_ingredient_detection.csv", "Ingredient detection metrics",
                    "Precision, recall, and F1 per completed experiment."),
            entry("layer4_human_evaluation.csv", "Human evaluation template",
                    "Reviewer-ready rows with blank human score columns."),
            entry("layer5_condition_scores.csv", "Condition scores",
                    "Average scores grouped by model and prompt technique.")
    );

    public CsvExportService(
            ReviewDashboardService reviewDashboardService,
            EvaluationService evaluationService
    ) {
        this.reviewDashboardService = reviewDashboardService;
        this.evaluationService = evaluationService;
    }

    /**
     * Lists the CSV files the user can download from the Exports page.
     */
    public List<ExportDefinition> exportDefinitions() {
        return definitions.values().stream()
                .sorted((left, right) -> left.fileName().compareTo(right.fileName()))
                .toList();
    }

    /**
     * Routes a requested CSV file name to the matching export builder.
     */
    public CsvFile generateExport(String exportName) {
        return switch (exportName) {
            case "layer1a_exact_match.csv" -> csv(exportName, this::layer1aRows);
            case "layer1b_text_similarity.csv" -> csv(exportName, this::layer1bRows);
            case "layer2a_numeric_quantity.csv" -> csv(exportName, this::layer2aRows);
            case "layer2b_numeric_nutrition.csv" -> csv(exportName, this::layer2bRows);
            case "layer2c_nutrition_totals.csv" -> csv(exportName, this::layer2cRows);
            case "layer3a_json_validity.csv" -> csv(exportName, this::layer3aRows);
            case "layer3b_hallucination.csv" -> csv(exportName, this::layer3bRows);
            case "layer3c_ingredient_detection.csv" -> csv(exportName, this::layer3cRows);
            case "layer4_human_evaluation.csv" -> csv(exportName, this::layer4Rows);
            case "layer5_condition_scores.csv" -> csv(exportName, this::layer5Rows);
            default -> throw new IllegalArgumentException("Unknown export: " + exportName);
        };
    }

    /**
     * Creates a compact CSV fact sheet for one model, prompt technique, and reel.
     */
    public CsvFile generateFactSheet(int modelId, int techniqueId, int reelId) {
        ReviewDashboardService.ResultPage page = reviewDashboardService.loadResultPage(modelId, techniqueId, reelId);
        List<List<Object>> rows = new ArrayList<>();
        rows.add(row("section", "field", "ground_truth", "ai", "difference", "status"));
        for (ReviewDashboardService.NutritionComparisonRow item : page.nutritionRows()) {
            rows.add(row(
                    "nutrition_total",
                    item.label() + " (" + item.unit() + ")",
                    item.groundTruthValue(),
                    item.aiValue(),
                    item.difference(),
                    ""
            ));
        }
        for (ReviewDashboardService.IngredientComparisonRow item : page.ingredientRows()) {
            rows.add(row(
                    "ingredient",
                    "name",
                    item.groundTruth() == null ? "" : item.groundTruth().nameOriginal(),
                    item.ai() == null ? "" : item.ai().nameOriginal(),
                    "",
                    item.status()
            ));
        }
        return new CsvFile("fact_sheet_reel_" + reelId + ".csv", toCsv(rows));
    }

    private static Map.Entry<String, ExportDefinition> entry(
            String fileName,
            String title,
            String description
    ) {
        return Map.entry(fileName, new ExportDefinition(fileName, title, description));
    }

    /**
     * Wraps row generation and CSV serialization for one export.
     */
    private CsvFile csv(String exportName, Supplier<List<List<Object>>> supplier) {
        return new CsvFile(exportName, toCsv(supplier.get()));
    }

    /**
     * Exports ingredient-level exact match labels for ground truth versus AI output.
     */
    private List<List<Object>> layer1aRows() {
        List<List<Object>> rows = withHeader(
                "experiment_id", "transcript_id", "model_name", "technique_name",
                "gt_name_original", "gt_name_en", "ai_name_original", "ai_name_en",
                "match_status", "exact_match"
        );
        for (EvaluationService.ExperimentEvaluation evaluation : evaluationService.completedExperimentEvaluations()) {
            for (ReviewDashboardService.IngredientComparisonRow item : evaluation.ingredientRows()) {
                rows.add(row(
                        evaluation.experimentId(),
                        evaluation.transcriptId(),
                        evaluation.modelName(),
                        evaluation.techniqueName(),
                        value(item.groundTruth(), ReviewDashboardService.IngredientDetail::nameOriginal),
                        value(item.groundTruth(), ReviewDashboardService.IngredientDetail::nameEn),
                        value(item.ai(), ReviewDashboardService.IngredientDetail::nameOriginal),
                        value(item.ai(), ReviewDashboardService.IngredientDetail::nameEn),
                        item.status(),
                        "Matched".equals(item.status())
                ));
            }
        }
        return rows;
    }

    /**
     * Exports normalized ingredient names for later text-similarity analysis.
     */
    private List<List<Object>> layer1bRows() {
        List<List<Object>> rows = withHeader(
                "experiment_id", "transcript_id", "model_name", "technique_name",
                "gt_name_normalized", "ai_name_normalized", "contains_or_partial_match", "match_status"
        );
        for (EvaluationService.ExperimentEvaluation evaluation : evaluationService.completedExperimentEvaluations()) {
            for (ReviewDashboardService.IngredientComparisonRow item : evaluation.ingredientRows()) {
                rows.add(row(
                        evaluation.experimentId(),
                        evaluation.transcriptId(),
                        evaluation.modelName(),
                        evaluation.techniqueName(),
                        normalize(value(item.groundTruth(), ReviewDashboardService.IngredientDetail::nameEn)),
                        normalize(value(item.ai(), ReviewDashboardService.IngredientDetail::nameEn)),
                        item.matched(),
                        item.status()
                ));
            }
        }
        return rows;
    }

    /**
     * Exports numeric quantity and estimated-weight differences per ingredient.
     */
    private List<List<Object>> layer2aRows() {
        List<List<Object>> rows = withHeader(
                "experiment_id", "transcript_id", "model_name", "technique_name",
                "match_status", "gt_quantity_value", "ai_quantity_value",
                "gt_weight_g", "ai_weight_g", "weight_abs_error_g"
        );
        for (EvaluationService.ExperimentEvaluation evaluation : evaluationService.completedExperimentEvaluations()) {
            for (ReviewDashboardService.IngredientComparisonRow item : evaluation.ingredientRows()) {
                Double gtWeight = value(item.groundTruth(), ReviewDashboardService.IngredientDetail::estimatedWeightG);
                Double aiWeight = value(item.ai(), ReviewDashboardService.IngredientDetail::estimatedWeightG);
                rows.add(row(
                        evaluation.experimentId(),
                        evaluation.transcriptId(),
                        evaluation.modelName(),
                        evaluation.techniqueName(),
                        item.status(),
                        value(item.groundTruth(), ReviewDashboardService.IngredientDetail::quantityValue),
                        value(item.ai(), ReviewDashboardService.IngredientDetail::quantityValue),
                        gtWeight,
                        aiWeight,
                        absoluteDifference(gtWeight, aiWeight)
                ));
            }
        }
        return rows;
    }

    /**
     * Exports per-ingredient nutrition differences between matched rows.
     */
    private List<List<Object>> layer2bRows() {
        List<List<Object>> rows = withHeader(
                "experiment_id", "transcript_id", "model_name", "technique_name",
                "match_status", "gt_name_en", "ai_name_en", "gt_calories", "ai_calories",
                "calorie_abs_error", "gt_protein_g", "ai_protein_g",
                "gt_fat_g", "ai_fat_g", "gt_carbohydrate_g", "ai_carbohydrate_g"
        );
        for (EvaluationService.ExperimentEvaluation evaluation : evaluationService.completedExperimentEvaluations()) {
            for (ReviewDashboardService.IngredientComparisonRow item : evaluation.ingredientRows()) {
                ReviewDashboardService.NutritionValues gt = nutrition(item.groundTruth());
                ReviewDashboardService.NutritionValues ai = nutrition(item.ai());
                rows.add(row(
                        evaluation.experimentId(),
                        evaluation.transcriptId(),
                        evaluation.modelName(),
                        evaluation.techniqueName(),
                        item.status(),
                        value(item.groundTruth(), ReviewDashboardService.IngredientDetail::nameEn),
                        value(item.ai(), ReviewDashboardService.IngredientDetail::nameEn),
                        gt.calories(),
                        ai.calories(),
                        absoluteDifference(gt.calories(), ai.calories()),
                        gt.proteinG(),
                        ai.proteinG(),
                        gt.totalFatG(),
                        ai.totalFatG(),
                        gt.totalCarbohydrateG(),
                        ai.totalCarbohydrateG()
                ));
            }
        }
        return rows;
    }

    /**
     * Exports recipe-level nutrition total differences.
     */
    private List<List<Object>> layer2cRows() {
        List<List<Object>> rows = withHeader(
                "experiment_id", "transcript_id", "model_name", "technique_name",
                "nutrition_field", "unit", "gt_total", "ai_total", "difference", "percentage_error"
        );
        for (EvaluationService.ExperimentEvaluation evaluation : evaluationService.completedExperimentEvaluations()) {
            for (ReviewDashboardService.NutritionComparisonRow item : evaluation.nutritionRows()) {
                rows.add(row(
                        evaluation.experimentId(),
                        evaluation.transcriptId(),
                        evaluation.modelName(),
                        evaluation.techniqueName(),
                        item.label(),
                        item.unit(),
                        item.groundTruthValue(),
                        item.aiValue(),
                        item.difference(),
                        item.percentageError()
                ));
            }
        }
        return rows;
    }

    /**
     * Exports whether each completed experiment produced valid JSON.
     */
    private List<List<Object>> layer3aRows() {
        List<List<Object>> rows = withHeader(
                "experiment_id", "transcript_id", "model_name", "technique_name", "json_valid"
        );
        for (EvaluationService.ExperimentEvaluation evaluation : evaluationService.completedExperimentEvaluations()) {
            rows.add(row(
                    evaluation.experimentId(),
                    evaluation.transcriptId(),
                    evaluation.modelName(),
                    evaluation.techniqueName(),
                    evaluation.metrics().jsonValid()
            ));
        }
        return rows;
    }

    /**
     * Exports AI ingredients marked as extra compared with human ground truth.
     */
    private List<List<Object>> layer3bRows() {
        List<List<Object>> rows = withHeader(
                "experiment_id", "transcript_id", "model_name", "technique_name",
                "ai_name_original", "ai_name_en", "match_status", "possible_hallucination"
        );
        for (EvaluationService.ExperimentEvaluation evaluation : evaluationService.completedExperimentEvaluations()) {
            for (ReviewDashboardService.IngredientComparisonRow item : evaluation.ingredientRows()) {
                if (item.ai() == null) {
                    continue;
                }
                rows.add(row(
                        evaluation.experimentId(),
                        evaluation.transcriptId(),
                        evaluation.modelName(),
                        evaluation.techniqueName(),
                        item.ai().nameOriginal(),
                        item.ai().nameEn(),
                        item.status(),
                        item.status().startsWith("Extra AI")
                ));
            }
        }
        return rows;
    }

    /**
     * Exports precision, recall, F1, and hallucination counts for each experiment.
     */
    private List<List<Object>> layer3cRows() {
        List<List<Object>> rows = withHeader(
                "experiment_id", "transcript_id", "model_name", "technique_name",
                "ground_truth_count", "ai_count", "matched_count", "missed_count",
                "extra_count", "precision", "recall", "f1"
        );
        for (EvaluationService.ExperimentEvaluation evaluation : evaluationService.completedExperimentEvaluations()) {
            ReviewDashboardService.EvaluationMetrics metrics = evaluation.metrics();
            rows.add(row(
                    evaluation.experimentId(),
                    evaluation.transcriptId(),
                    evaluation.modelName(),
                    evaluation.techniqueName(),
                    metrics.groundTruthCount(),
                    metrics.aiCount(),
                    metrics.matchedCount(),
                    metrics.missedCount(),
                    metrics.extraCount(),
                    metrics.precision(),
                    metrics.recall(),
                    metrics.f1()
            ));
        }
        return rows;
    }

    /**
     * Builds a reviewer-friendly CSV with blank columns for human scoring.
     */
    private List<List<Object>> layer4Rows() {
        List<List<Object>> rows = withHeader(
                "experiment_id", "transcript_id", "model_name", "technique_name",
                "human_fluency_score", "human_completeness_score", "human_plausibility_score",
                "reviewer_notes", "auto_f1"
        );
        for (EvaluationService.ExperimentEvaluation evaluation : evaluationService.completedExperimentEvaluations()) {
            rows.add(row(
                    evaluation.experimentId(),
                    evaluation.transcriptId(),
                    evaluation.modelName(),
                    evaluation.techniqueName(),
                    "",
                    "",
                    "",
                    "",
                    evaluation.metrics().f1()
            ));
        }
        return rows;
    }

    /**
     * Exports aggregate condition scores grouped by model and prompt technique.
     */
    private List<List<Object>> layer5Rows() {
        List<List<Object>> rows = withHeader(
                "model_name", "technique_name", "completed_experiments",
                "average_precision", "average_recall", "average_f1",
                "average_calorie_absolute_error", "json_validity_rate", "hallucination_rate"
        );
        for (EvaluationService.EvaluationAggregate aggregate : evaluationService.aggregateByModelTechnique()) {
            rows.add(row(
                    aggregate.modelName(),
                    aggregate.techniqueName(),
                    aggregate.completedExperiments(),
                    aggregate.averagePrecision(),
                    aggregate.averageRecall(),
                    aggregate.averageF1(),
                    aggregate.averageCalorieAbsoluteError(),
                    aggregate.jsonValidityRate(),
                    aggregate.hallucinationRate()
            ));
        }
        return rows;
    }

    /**
     * Creates the first row of a CSV with the provided column names.
     */
    private List<List<Object>> withHeader(String... headers) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(row((Object[]) headers));
        return rows;
    }

    /**
     * Convenience helper for building one CSV row.
     */
    private List<Object> row(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    /**
     * Converts rows into CSV text with Windows-friendly line endings.
     */
    private String toCsv(List<List<Object>> rows) {
        StringBuilder builder = new StringBuilder();
        for (List<Object> row : rows) {
            for (int index = 0; index < row.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(escape(row.get(index)));
            }
            builder.append("\r\n");
        }
        return builder.toString();
    }

    /**
     * Escapes commas, quotes, and line breaks according to CSV rules.
     */
    private String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains("\"") || text.contains(",") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    /**
     * Normalizes ingredient names before comparison-oriented exports.
     */
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private <T> T value(
            ReviewDashboardService.IngredientDetail ingredient,
            java.util.function.Function<ReviewDashboardService.IngredientDetail, T> getter
    ) {
        return ingredient == null ? null : getter.apply(ingredient);
    }

    private ReviewDashboardService.NutritionValues nutrition(
            ReviewDashboardService.IngredientDetail ingredient
    ) {
        return ingredient == null ? ReviewDashboardService.NutritionValues.empty() : ingredient.nutrition();
    }

    /**
     * Returns an absolute numeric difference, preserving null when either value is missing.
     */
    private Double absoluteDifference(Double left, Double right) {
        return left == null || right == null ? null : Math.abs(right - left);
    }

    public record ExportDefinition(String fileName, String title, String description) {
    }

    public record CsvFile(String fileName, String content) {
    }
}
