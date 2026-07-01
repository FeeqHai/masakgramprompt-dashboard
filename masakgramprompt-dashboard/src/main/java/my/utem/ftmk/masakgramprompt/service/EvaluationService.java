package my.utem.ftmk.masakgramprompt.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calculates accuracy metrics and aggregate rankings for completed experiments.
 */
@Service
public class EvaluationService {

    private final ReviewDashboardService reviewDashboardService;

    public EvaluationService(ReviewDashboardService reviewDashboardService) {
        this.reviewDashboardService = reviewDashboardService;
    }

    /**
     * Calculates per-experiment metrics from completed experiment rows.
     */
    public List<ExperimentEvaluation> completedExperimentEvaluations() {
        List<ExperimentEvaluation> evaluations = new ArrayList<>();
        for (ReviewDashboardService.CompletedExperimentKey key : reviewDashboardService.findCompletedExperimentKeys()) {
            ReviewDashboardService.ExperimentDetail experiment = reviewDashboardService
                    .findExperimentById(key.experimentId())
                    .orElse(ReviewDashboardService.ExperimentDetail.empty(
                            key.transcriptId(),
                            key.modelId(),
                            key.techniqueId()
                    ));
            List<ReviewDashboardService.IngredientDetail> groundTruth =
                    reviewDashboardService.loadGroundTruthIngredients(key.transcriptId());
            List<ReviewDashboardService.IngredientDetail> ai =
                    reviewDashboardService.loadAiIngredients(key.experimentId());
            List<ReviewDashboardService.IngredientComparisonRow> ingredientRows =
                    reviewDashboardService.compareIngredients(groundTruth, ai);
            ReviewDashboardService.NutritionValues groundTruthTotals =
                    reviewDashboardService.sumNutrition(groundTruth);
            ReviewDashboardService.NutritionValues aiTotals = experiment.totalNutrition().isEmpty()
                    ? reviewDashboardService.sumNutrition(ai)
                    : experiment.totalNutrition();
            List<ReviewDashboardService.NutritionComparisonRow> nutritionRows =
                    reviewDashboardService.compareNutrition(groundTruthTotals, aiTotals);
            ReviewDashboardService.EvaluationMetrics metrics =
                    reviewDashboardService.calculateMetrics(ingredientRows, ai, experiment);

            evaluations.add(new ExperimentEvaluation(
                    key.experimentId(),
                    key.transcriptId(),
                    key.modelId(),
                    key.modelName(),
                    key.techniqueId(),
                    key.techniqueName(),
                    experiment,
                    ingredientRows,
                    nutritionRows,
                    metrics
            ));
        }
        return evaluations;
    }

    /**
     * Groups completed experiment evaluations by model and prompt technique.
     */
    public List<EvaluationAggregate> aggregateByModelTechnique() {
        Map<String, AggregateAccumulator> accumulators = new LinkedHashMap<>();
        for (ExperimentEvaluation evaluation : completedExperimentEvaluations()) {
            String key = evaluation.modelId() + ":" + evaluation.techniqueId();
            AggregateAccumulator accumulator = accumulators.computeIfAbsent(
                    key,
                    value -> new AggregateAccumulator(
                            evaluation.modelId(),
                            evaluation.modelName(),
                            evaluation.techniqueId(),
                            evaluation.techniqueName()
                    )
            );
            accumulator.add(evaluation);
        }
        return accumulators.values().stream()
                .map(AggregateAccumulator::toAggregate)
                .toList();
    }

    /**
     * Builds the page model for the accuracy dashboard from existing aggregate metrics.
     */
    public EvaluationDashboardPage loadEvaluationDashboard() {
        return loadEvaluationDashboard(null, "ingredient");
    }

    /**
     * Builds the evaluation dashboard for the selected model and metric type.
     */
    public EvaluationDashboardPage loadEvaluationDashboard(Integer modelId, String type) {
        List<EvaluationAggregate> aggregates = aggregateByModelTechnique();
        String selectedType = normalizeEvaluationType(type);
        List<EvaluationAggregate> filteredRows = aggregates.stream()
                .filter(row -> modelId == null || row.modelId() == modelId)
                .sorted(rowComparator(selectedType))
                .toList();

        return new EvaluationDashboardPage(
                buildSummary(aggregates),
                modelOptions(),
                modelId,
                selectedType,
                filteredRows
        );
    }

    /**
     * Loads model options used by the evaluation filter dropdown.
     */
    private List<ModelOption> modelOptions() {
        return reviewDashboardService.findModelCards().stream()
                .map(model -> new ModelOption(model.modelId(), model.modelName()))
                .toList();
    }

    /**
     * Keeps unsupported tab names from breaking the evaluation page.
     */
    private String normalizeEvaluationType(String type) {
        if (type == null || type.isBlank()) {
            return "ingredient";
        }
        return switch (type) {
            case "nutrition", "json", "ranking" -> type;
            default -> "ingredient";
        };
    }

    /**
     * Chooses the table ordering for each evaluation tab.
     */
    private Comparator<EvaluationAggregate> rowComparator(String type) {
        if ("ranking".equals(type)) {
            return Comparator
                    .comparing(EvaluationAggregate::averageF1).reversed()
                    .thenComparing(EvaluationAggregate::hallucinationRate)
                    .thenComparing(EvaluationAggregate::averageCalorieAbsoluteError);
        }
        return Comparator
                .comparing(EvaluationAggregate::modelId)
                .thenComparing(EvaluationAggregate::techniqueId);
    }

    /**
     * Returns all aggregate rows sorted by best overall condition.
     */
    public List<EvaluationAggregate> rankedAggregates() {
        return aggregateByModelTechnique().stream()
                .sorted(Comparator
                        .comparing(EvaluationAggregate::averageF1).reversed()
                        .thenComparing(EvaluationAggregate::hallucinationRate)
                        .thenComparing(EvaluationAggregate::averageCalorieAbsoluteError))
                .toList();
    }

    /**
     * Builds the top-level summary cards from all aggregate rows.
     */
    private EvaluationSummary buildSummary(List<EvaluationAggregate> aggregates) {
        int totalCompleted = aggregates.stream()
                .mapToInt(EvaluationAggregate::completedExperiments)
                .sum();
        Optional<EvaluationAggregate> bestF1 = aggregates.stream()
                .max(Comparator.comparing(EvaluationAggregate::averageF1));
        Optional<EvaluationAggregate> bestRecall = aggregates.stream()
                .max(Comparator.comparing(EvaluationAggregate::averageRecall));
        Optional<EvaluationAggregate> lowestCalorieError = aggregates.stream()
                .min(Comparator.comparing(EvaluationAggregate::averageCalorieAbsoluteError));
        Optional<EvaluationAggregate> bestJsonValidity = aggregates.stream()
                .max(Comparator.comparing(EvaluationAggregate::jsonValidityRate));
        Optional<EvaluationAggregate> highestHallucination = aggregates.stream()
                .max(Comparator.comparing(EvaluationAggregate::hallucinationRate));

        return new EvaluationSummary(
                totalCompleted,
                label(bestF1),
                bestF1.map(EvaluationAggregate::averageF1).orElse(0.0),
                label(bestRecall),
                bestRecall.map(EvaluationAggregate::averageRecall).orElse(0.0),
                label(lowestCalorieError),
                lowestCalorieError.map(EvaluationAggregate::averageCalorieAbsoluteError).orElse(0.0),
                label(bestJsonValidity),
                bestJsonValidity.map(EvaluationAggregate::jsonValidityRate).orElse(0.0),
                label(highestHallucination),
                highestHallucination.map(EvaluationAggregate::hallucinationRate).orElse(0.0)
        );
    }

    /**
     * Formats the model and technique label used in summary cards.
     */
    private String label(Optional<EvaluationAggregate> aggregate) {
        return aggregate
                .map(item -> item.modelName() + " - " + item.techniqueName())
                .orElse("Not available");
    }

    private static final class AggregateAccumulator {

        private final int modelId;
        private final String modelName;
        private final int techniqueId;
        private final String techniqueName;
        private int completedExperiments;
        private double precisionTotal;
        private double recallTotal;
        private double f1Total;
        private double calorieErrorTotal;
        private double proteinErrorTotal;
        private double fatErrorTotal;
        private double carbErrorTotal;
        private int calorieErrorCount;
        private int proteinErrorCount;
        private int fatErrorCount;
        private int carbErrorCount;
        private int jsonValidCount;
        private int hallucinationCount;
        private int aiIngredientCount;

        private AggregateAccumulator(
                int modelId,
                String modelName,
                int techniqueId,
                String techniqueName
        ) {
            this.modelId = modelId;
            this.modelName = modelName;
            this.techniqueId = techniqueId;
            this.techniqueName = techniqueName;
        }

        /**
         * Adds one experiment's metrics into this aggregate accumulator.
         */
        private void add(ExperimentEvaluation evaluation) {
            ReviewDashboardService.EvaluationMetrics metrics = evaluation.metrics();
            completedExperiments++;
            precisionTotal += metrics.precision();
            recallTotal += metrics.recall();
            f1Total += metrics.f1();
            if (metrics.jsonValid()) {
                jsonValidCount++;
            }
            hallucinationCount += metrics.hallucinationCount();
            aiIngredientCount += metrics.aiCount();
            addNutritionError(evaluation, "calories");
            addNutritionError(evaluation, "protein_g");
            addNutritionError(evaluation, "total_fat_g");
            addNutritionError(evaluation, "total_carbohydrate_g");
        }

        /**
         * Adds one nutrition absolute-error value when the metric exists.
         */
        private void addNutritionError(ExperimentEvaluation evaluation, String nutritionKey) {
            Double error = evaluation.absoluteNutritionError(nutritionKey);
            if (error == null) {
                return;
            }
            switch (nutritionKey) {
                case "calories" -> {
                    calorieErrorTotal += error;
                    calorieErrorCount++;
                }
                case "protein_g" -> {
                    proteinErrorTotal += error;
                    proteinErrorCount++;
                }
                case "total_fat_g" -> {
                    fatErrorTotal += error;
                    fatErrorCount++;
                }
                case "total_carbohydrate_g" -> {
                    carbErrorTotal += error;
                    carbErrorCount++;
                }
                default -> {
                }
            }
        }

        /**
         * Converts totals and counters into a dashboard-ready aggregate row.
         */
        private EvaluationAggregate toAggregate() {
            return new EvaluationAggregate(
                    modelId,
                    modelName,
                    techniqueId,
                    techniqueName,
                    completedExperiments,
                    average(precisionTotal, completedExperiments),
                    average(recallTotal, completedExperiments),
                    average(f1Total, completedExperiments),
                    average(calorieErrorTotal, calorieErrorCount),
                    average(proteinErrorTotal, proteinErrorCount),
                    average(fatErrorTotal, fatErrorCount),
                    average(carbErrorTotal, carbErrorCount),
                    average(jsonValidCount, completedExperiments),
                    hallucinationCount,
                    aiIngredientCount == 0 ? 0.0 : round((double) hallucinationCount / aiIngredientCount)
            );
        }

        /**
         * Calculates an average while avoiding divide-by-zero.
         */
        private double average(double total, int count) {
            return count == 0 ? 0.0 : round(total / count);
        }

        /**
         * Rounds metric values to three decimal places for display.
         */
        private double round(double value) {
            return Math.round(value * 1000.0) / 1000.0;
        }
    }

    public record ExperimentEvaluation(
            int experimentId,
            int transcriptId,
            int modelId,
            String modelName,
            int techniqueId,
            String techniqueName,
            ReviewDashboardService.ExperimentDetail experiment,
            List<ReviewDashboardService.IngredientComparisonRow> ingredientRows,
            List<ReviewDashboardService.NutritionComparisonRow> nutritionRows,
            ReviewDashboardService.EvaluationMetrics metrics
    ) {
        /**
         * Finds the nutrition absolute error for a named nutrition field.
         */
        public Double absoluteNutritionError(String nutritionKey) {
            return nutritionRows.stream()
                    .filter(row -> row.key().equals(nutritionKey))
                    .findFirst()
                    .map(ReviewDashboardService.NutritionComparisonRow::difference)
                    .map(Math::abs)
                    .orElse(null);
        }
    }

    public record EvaluationAggregate(
            int modelId,
            String modelName,
            int techniqueId,
            String techniqueName,
            int completedExperiments,
            double averagePrecision,
            double averageRecall,
            double averageF1,
            double averageCalorieAbsoluteError,
            double averageProteinAbsoluteError,
            double averageFatAbsoluteError,
            double averageCarbAbsoluteError,
            double jsonValidityRate,
            int hallucinationCount,
            double hallucinationRate
    ) {
        /**
         * Returns a readable condition label for model plus prompt technique.
         */
        public String conditionLabel() {
            return modelName + " - " + techniqueName;
        }

        /**
         * Chooses the badge style for JSON validity percentage.
         */
        public String jsonValidityCssClass() {
            if (jsonValidityRate >= 0.9) {
                return "badge ok";
            }
            if (jsonValidityRate >= 0.7) {
                return "badge pending";
            }
            return "badge missing";
        }
    }

    public record EvaluationDashboardPage(
            EvaluationSummary summary,
            List<ModelOption> modelOptions,
            Integer selectedModelId,
            String selectedType,
            List<EvaluationAggregate> rows
    ) {
        /**
         * Lets Thymeleaf check whether the filtered dashboard has any rows.
         */
        public boolean hasRows() {
            return !rows.isEmpty();
        }
    }

    public record ModelOption(int modelId, String modelName) {
    }

    public record EvaluationSummary(
            int totalCompletedExperiments,
            String bestF1Label,
            double bestF1,
            String bestRecallLabel,
            double bestRecall,
            String lowestCalorieErrorLabel,
            double lowestCalorieError,
            String bestJsonValidityLabel,
            double bestJsonValidity,
            String highestHallucinationLabel,
            double highestHallucinationRate
    ) {
    }

    public record ModelEvaluationGroup(
            int modelId,
            String modelName,
            List<EvaluationAggregate> techniques
    ) {
        /**
         * Finds the best F1 row inside one model group.
         */
        public EvaluationAggregate bestF1Technique() {
            return techniques.stream()
                    .max(Comparator.comparing(EvaluationAggregate::averageF1))
                    .orElse(null);
        }

        /**
         * Returns the prompt technique name for the model's best F1 row.
         */
        public String bestTechniqueName() {
            EvaluationAggregate best = bestF1Technique();
            return best == null ? "Not available" : best.techniqueName();
        }

        /**
         * Returns the best F1 score for the model summary line.
         */
        public double bestF1() {
            EvaluationAggregate best = bestF1Technique();
            return best == null ? 0.0 : best.averageF1();
        }
    }
}
