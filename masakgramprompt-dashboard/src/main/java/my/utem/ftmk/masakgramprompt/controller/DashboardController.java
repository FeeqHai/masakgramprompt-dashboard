package my.utem.ftmk.masakgramprompt.controller;

import my.utem.ftmk.masakgramprompt.model.BatchRunStatus;
import my.utem.ftmk.masakgramprompt.service.DashboardService;
import my.utem.ftmk.masakgramprompt.service.BatchExperimentService;
import my.utem.ftmk.masakgramprompt.service.CsvExportService;
import my.utem.ftmk.masakgramprompt.service.DatasetImportService;
import my.utem.ftmk.masakgramprompt.service.EvaluationService;
import my.utem.ftmk.masakgramprompt.service.ExcelExportService;
import my.utem.ftmk.masakgramprompt.service.LlmExperimentRunnerService;
import my.utem.ftmk.masakgramprompt.service.ReviewDashboardService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final LlmExperimentRunnerService llmExperimentRunnerService;
    private final DatasetImportService datasetImportService;
    private final BatchExperimentService batchExperimentService;
    private final ExcelExportService excelExportService;
    private final ReviewDashboardService reviewDashboardService;
    private final EvaluationService evaluationService;
    private final CsvExportService csvExportService;

    public DashboardController(
            DashboardService dashboardService,
            LlmExperimentRunnerService llmExperimentRunnerService,
            DatasetImportService datasetImportService,
            BatchExperimentService batchExperimentService,
            ExcelExportService excelExportService,
            ReviewDashboardService reviewDashboardService,
            EvaluationService evaluationService,
            CsvExportService csvExportService
    ) {
        this.dashboardService = dashboardService;
        this.llmExperimentRunnerService = llmExperimentRunnerService;
        this.datasetImportService = datasetImportService;
        this.batchExperimentService = batchExperimentService;
        this.excelExportService = excelExportService;
        this.reviewDashboardService = reviewDashboardService;
        this.evaluationService = evaluationService;
        this.csvExportService = csvExportService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/models";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("summary", dashboardService.getSummary());
        model.addAttribute("reels", dashboardService.findAllReels());
        model.addAttribute("models", dashboardService.findModelOptions());
        model.addAttribute("techniques", dashboardService.findPromptTechniqueOptions());
        model.addAttribute("batchStatus", batchExperimentService.getStatus());
        model.addAttribute("modelProcessingTimes", dashboardService.findModelProcessingSummaries());
        return "dashboard";
    }

    @PostMapping("/data/sync")
    public String syncData(RedirectAttributes redirectAttributes) {
        try {
            var summary = datasetImportService.syncDataset();
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Data sync complete: " + summary.audioFilesSynced() + " audio files and "
                            + summary.transcriptsSynced() + " transcripts."
            );
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Data sync failed: " + ex.getMessage());
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/models")
    public String models(Model model) {
        model.addAttribute("activePage", "models");
        model.addAttribute("models", reviewDashboardService.findModelCards());
        return "models";
    }

    @GetMapping("/models/{modelId}/techniques")
    public String modelTechniques(@PathVariable int modelId, Model model) {
        var selectedModel = reviewDashboardService.findModelCard(modelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found"));

        model.addAttribute("activePage", "models");
        model.addAttribute("selectedModel", selectedModel);
        model.addAttribute("techniques", reviewDashboardService.findTechniqueCards(modelId));
        return "techniques";
    }

    @GetMapping("/models/{modelId}/techniques/{techniqueId}/reels")
    public String modelTechniqueReels(
            @PathVariable int modelId,
            @PathVariable int techniqueId,
            Model model
    ) {
        var selectedModel = reviewDashboardService.findModelCard(modelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found"));
        var selectedTechnique = reviewDashboardService.findTechniqueCard(modelId, techniqueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prompt technique not found"));

        model.addAttribute("activePage", "models");
        model.addAttribute("selectedModel", selectedModel);
        model.addAttribute("selectedTechnique", selectedTechnique);
        model.addAttribute("reels", reviewDashboardService.findReelRows(modelId, techniqueId));
        return "model-reels";
    }

    @GetMapping("/models/{modelId}/techniques/{techniqueId}/reels/{reelId}/result")
    public String reelResult(
            @PathVariable int modelId,
            @PathVariable int techniqueId,
            @PathVariable int reelId,
            Model model
    ) {
        try {
            model.addAttribute("activePage", "models");
            model.addAttribute("page", reviewDashboardService.loadResultPage(modelId, techniqueId, reelId));
            model.addAttribute("nutritionFields", reviewDashboardService.nutritionFields());
            return "result";
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/batch")
    public String batch(
            @RequestParam(name = "modelId", required = false) Integer modelId,
            Model model
    ) {
        var models = reviewDashboardService.findModelCards();
        var availableReels = dashboardService.findAllReels().stream()
                .filter(reel -> reel.isHasTranscript())
                .toList();
        int selectedModelId = modelId == null && !models.isEmpty() ? models.get(0).modelId() : modelId == null ? 0 : modelId;

        model.addAttribute("activePage", "batch");
        model.addAttribute("models", models);
        model.addAttribute("availableReels", availableReels);
        model.addAttribute("selectedModelId", selectedModelId);
        model.addAttribute("techniqueStatusRows", selectedModelId == 0
                ? List.of()
                : reviewDashboardService.findTechniqueCards(selectedModelId));
        model.addAttribute("batchStatus", batchExperimentService.getStatus());
        return "batch";
    }

    @GetMapping("/single-run")
    public String singleRun(
            @RequestParam(name = "modelId", required = false) Integer modelId,
            @RequestParam(name = "techniqueId", required = false) Integer techniqueId,
            Model model
    ) {
        var models = reviewDashboardService.findModelCards();
        int selectedModelId = modelId == null && !models.isEmpty() ? models.get(0).modelId() : modelId == null ? 0 : modelId;
        List<ReviewDashboardService.TechniqueCard> techniques = selectedModelId == 0
                ? List.of()
                : reviewDashboardService.findTechniqueCards(selectedModelId);
        int selectedTechniqueId = techniqueId == null
                ? defaultTechniqueId(techniques)
                : techniqueId;
        List<ReviewDashboardService.ReelReviewRow> reels = selectedModelId == 0 || selectedTechniqueId == 0
                ? List.of()
                : reviewDashboardService.findReelRows(selectedModelId, selectedTechniqueId);
        long untestedCount = reels.stream()
                .filter(reel -> reel.transcriptId() != null && reel.resultId() == null)
                .count();
        long runnableCount = reels.stream()
                .filter(ReviewDashboardService.ReelReviewRow::canRunExperiment)
                .count();

        model.addAttribute("activePage", "single-run");
        model.addAttribute("models", models);
        model.addAttribute("techniques", techniques);
        model.addAttribute("selectedModelId", selectedModelId);
        model.addAttribute("selectedTechniqueId", selectedTechniqueId);
        model.addAttribute("selectedModelName", selectedModelName(models, selectedModelId));
        model.addAttribute("selectedTechniqueName", selectedTechniqueName(techniques, selectedTechniqueId));
        model.addAttribute("reels", reels);
        model.addAttribute("untestedCount", untestedCount);
        model.addAttribute("runnableCount", runnableCount);
        model.addAttribute("batchStatus", batchExperimentService.getStatus());
        return "single-run";
    }

    @PostMapping("/batch/run")
    public String runBatchFromBatchPage(
            @RequestParam int modelId,
            @RequestParam(name = "techniqueIds", required = false) List<Integer> techniqueIds,
            RedirectAttributes redirectAttributes
    ) {
        try {
            boolean started = batchExperimentService.start(modelId, techniqueIds);
            if (started) {
                redirectAttributes.addFlashAttribute(
                        "successMessage",
                        "Batch started for " + techniqueIds.size() + " selected prompt technique(s)."
                );
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "A batch is already running.");
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Batch could not start: " + ex.getMessage());
        }
        return "redirect:/batch?modelId=" + modelId;
    }

    @PostMapping({"/batch/run-single", "/single-run/run"})
    public String runSingleFromBatchPage(
            @RequestParam int reelId,
            @RequestParam int modelId,
            @RequestParam int techniqueId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            int experimentId = llmExperimentRunnerService.run(reelId, modelId, techniqueId);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Single experiment " + experimentId + " completed."
            );
            return "redirect:/models/" + modelId + "/techniques/" + techniqueId + "/reels/" + reelId + "/result";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Single experiment failed: " + ex.getMessage());
            return "redirect:/single-run?modelId=" + modelId + "&techniqueId=" + techniqueId;
        }
    }

    @PostMapping("/single-run/run-json")
    public ResponseEntity<SingleRunResponse> runSingleJson(
            @RequestParam int reelId,
            @RequestParam int modelId,
            @RequestParam int techniqueId
    ) {
        try {
            int experimentId = llmExperimentRunnerService.run(reelId, modelId, techniqueId);
            return ResponseEntity.ok(new SingleRunResponse(
                    true,
                    "/models/" + modelId + "/techniques/" + techniqueId + "/reels/" + reelId + "/result",
                    "Single experiment " + experimentId + " completed."
            ));
        } catch (Exception ex) {
            return ResponseEntity
                    .badRequest()
                    .body(new SingleRunResponse(false, null, "Single experiment failed: " + ex.getMessage()));
        }
    }

    public record SingleRunResponse(boolean success, String redirectUrl, String message) {
    }

    @GetMapping("/batch/status")
    public ResponseEntity<BatchRunStatus> batchStatus() {
        return ResponseEntity.ok(batchExperimentService.getStatus());
    }

    @GetMapping("/performance")
    public String performance(Model model) {
        model.addAttribute("activePage", "performance");
        model.addAttribute("page", reviewDashboardService.loadPerformancePage());
        return "performance";
    }

    @GetMapping("/evaluation")
    public String evaluation(
            @RequestParam(name = "modelId", required = false) String modelId,
            @RequestParam(name = "type", required = false, defaultValue = "ingredient") String type,
            Model model
    ) {
        model.addAttribute("activePage", "evaluation");
        model.addAttribute("page", evaluationService.loadEvaluationDashboard(parseModelId(modelId), type));
        return "evaluation";
    }

    @GetMapping("/exports")
    public String exports(Model model) {
        model.addAttribute("activePage", "exports");
        model.addAttribute("exports", csvExportService.exportDefinitions());
        return "exports";
    }

    @PostMapping("/experiments/batch")
    public String runBatch(
            @RequestParam int modelId,
            @RequestParam int techniqueId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            boolean started = batchExperimentService.start(modelId, techniqueId);
            if (started) {
                redirectAttributes.addFlashAttribute(
                        "successMessage",
                        "Batch started. The selected model and prompt will run one transcript at a time."
                );
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "A batch is already running.");
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Batch could not start: " + ex.getMessage());
        }
        return "redirect:/batch?modelId=" + modelId;
    }

    @GetMapping("/exports/llm-results.xlsx")
    public ResponseEntity<byte[]> downloadLlmResults() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            excelExportService.writeLlmResults(output);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"));
            String filename = "masakgramprompt-results-" + timestamp + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    ))
                    .body(output.toByteArray());
        } catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Excel export failed. Update the Maven project in Eclipse, restart the dashboard, and try again.",
                    ex
            );
        }
    }

    @GetMapping(
            value = "/exports/{exportName:.+\\.csv}",
            produces = "text/csv; charset=UTF-8"
    )
    public ResponseEntity<String> downloadCsvExport(@PathVariable String exportName) {
        try {
            CsvExportService.CsvFile file = csvExportService.generateExport(exportName);
            return csvResponse(file);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping(
            value = "/models/{modelId}/techniques/{techniqueId}/reels/{reelId}/result/fact-sheet.csv",
            produces = "text/csv; charset=UTF-8"
    )
    public ResponseEntity<String> downloadFactSheet(
            @PathVariable int modelId,
            @PathVariable int techniqueId,
            @PathVariable int reelId
    ) {
        try {
            return csvResponse(csvExportService.generateFactSheet(modelId, techniqueId, reelId));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/reels/{reelId}")
    public String reelDetail(@PathVariable int reelId, Model model) {
        var reel = dashboardService.findReelById(reelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reel not found"));

        model.addAttribute("reel", reel);
        model.addAttribute("audio", dashboardService.findAudioByReelId(reelId).orElse(null));
        model.addAttribute("transcript", dashboardService.findTranscriptByReelId(reelId).orElse(null));
        model.addAttribute("experiments", dashboardService.findExperimentsByReelId(reelId));
        model.addAttribute("nutritionResults", dashboardService.findNutritionResultsByReelId(reelId));
        model.addAttribute("ingredientResults", dashboardService.findIngredientResultsByReelId(reelId));
        model.addAttribute("models", dashboardService.findModelOptions());
        model.addAttribute("techniques", dashboardService.findPromptTechniqueOptions());

        return "reel-detail";
    }

    @PostMapping("/reels/{reelId}/experiments/run")
    public String runExperiment(
            @PathVariable int reelId,
            @RequestParam int modelId,
            @RequestParam int techniqueId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            int experimentId = llmExperimentRunnerService.run(reelId, modelId, techniqueId);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Experiment " + experimentId + " completed. Previous result for the same model and prompt was replaced."
            );
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/reels/" + reelId + "#experiments";
    }

    private ResponseEntity<String> csvResponse(CsvExportService.CsvFile file) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(file.content());
    }

    private Integer parseModelId(String modelId) {
        if (modelId == null || modelId.isBlank() || "all".equalsIgnoreCase(modelId)) {
            return null;
        }
        try {
            return Integer.valueOf(modelId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int defaultTechniqueId(List<ReviewDashboardService.TechniqueCard> techniques) {
        if (techniques.isEmpty()) {
            return 0;
        }
        return techniques.stream()
                .filter(technique -> "structured-output".equals(technique.techniqueName()))
                .findFirst()
                .orElseGet(() -> techniques.get(0))
                .techniqueId();
    }

    private String selectedModelName(List<ReviewDashboardService.ModelCard> models, int selectedModelId) {
        return models.stream()
                .filter(model -> model.modelId() == selectedModelId)
                .map(ReviewDashboardService.ModelCard::modelName)
                .findFirst()
                .orElse("-");
    }

    private String selectedTechniqueName(
            List<ReviewDashboardService.TechniqueCard> techniques,
            int selectedTechniqueId
    ) {
        return techniques.stream()
                .filter(technique -> technique.techniqueId() == selectedTechniqueId)
                .map(ReviewDashboardService.TechniqueCard::techniqueName)
                .findFirst()
                .orElse("-");
    }
}
