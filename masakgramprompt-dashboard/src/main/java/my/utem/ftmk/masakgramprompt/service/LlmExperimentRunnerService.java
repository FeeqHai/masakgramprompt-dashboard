package my.utem.ftmk.masakgramprompt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.core.io.ClassPathResource;
@Service
public class LlmExperimentRunnerService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String ollamaBaseUrl;

    public LlmExperimentRunnerService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${masakgram.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }
    private Path resolveProjectFile(String storedPath) {
        Path path = Path.of(storedPath);

        if (path.isAbsolute()) {
            return path;
        }

        return Path.of("").toAbsolutePath().resolve(path).normalize();
    }
    
    public int run(int reelId, int modelId, int techniqueId) {
        return run(reelId, modelId, techniqueId, stage -> {
        });
    }

    public int run(int reelId, int modelId, int techniqueId, Consumer<String> stageListener) {
        long startedAtNanos = System.nanoTime();
        stageListener.accept("Preparing request");
        TranscriptInput transcript = loadTranscript(reelId);
        ModelInput model = loadModel(modelId);
        PromptInput prompt = loadPrompt(techniqueId);

        int experimentId = findOrCreateExperiment(transcript.transcriptId(), modelId, techniqueId);
        clearPreviousResults(experimentId);
        markExperimentRunning(experimentId);

        try {
            stageListener.accept("Reading transcript file");
        	Path transcriptPath = resolveProjectFile(transcript.filePath());
        	String transcriptText = cleanTranscript(Files.readString(transcriptPath, StandardCharsets.UTF_8));
            stageListener.accept("Building prompt");
            String userPrompt = prompt.userPrompt().replace("{{TRANSCRIPT}}", transcriptText);
            stageListener.accept("Waiting for Ollama response");
            String rawOutput = callOllama(model.modelTag(), prompt.systemPrompt(), userPrompt);
            stageListener.accept("Parsing JSON output");
            boolean jsonValid = isValidJson(rawOutput);

            stageListener.accept("Saving result to database");
            int resultId = insertNutritionResult(experimentId, rawOutput, jsonValid);
            if (jsonValid) {
                insertIngredientResults(resultId, rawOutput);
            }

            markExperimentFinished(experimentId, "completed", elapsedMilliseconds(startedAtNanos));
            return experimentId;
        } catch (Exception ex) {
            markExperimentFinished(experimentId, "failed", elapsedMilliseconds(startedAtNanos));
            throw new IllegalStateException("Experiment failed: " + ex.getMessage(), ex);
        }
    }

    private TranscriptInput loadTranscript(int reelId) {
        return jdbcTemplate.query("""
                SELECT transcript_id, file_path
                FROM transcript
                WHERE reel_id = ?
                LIMIT 1
                """, (rs, rowNum) -> new TranscriptInput(
                rs.getInt("transcript_id"),
                rs.getString("file_path")
        ), reelId).stream().findFirst().orElseThrow(
                () -> new IllegalStateException("No transcript exists for Reel " + reelId)
        );
    }

    private ModelInput loadModel(int modelId) {
        return jdbcTemplate.query("""
                SELECT model_tag
                FROM llm_model
                WHERE model_id = ?
                """, (rs, rowNum) -> new ModelInput(rs.getString("model_tag")), modelId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Model not found: " + modelId));
    }

    private PromptInput loadPrompt(int techniqueId) {
        return jdbcTemplate.query("""
                SELECT system_prompt_file, user_prompt_file
                FROM prompt_technique
                WHERE technique_id = ?
                """, (rs, rowNum) -> {
				String systemPrompt = readPromptFile(rs.getString("system_prompt_file"));
				String userPrompt = readPromptFile(rs.getString("user_prompt_file"));
	
	return new PromptInput(systemPrompt, userPrompt);
	        }, techniqueId).stream().findFirst().orElseThrow(
                () -> new IllegalStateException("Prompt technique not found: " + techniqueId)
        );
    }

    private String readPromptFile(String storedPath) {
        try {
            String fileName = Path.of(storedPath).getFileName().toString();
            ClassPathResource resource = new ClassPathResource("prompts/" + fileName);

            if (!resource.exists()) {
                throw new IllegalStateException("Prompt file not found in src/main/resources/prompts: " + fileName);
            }

            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read prompt file: " + storedPath, ex);
        }
    }

    private int findOrCreateExperiment(int transcriptId, int modelId, int techniqueId) {
        return jdbcTemplate.query("""
                SELECT experiment_id
                FROM experiment
                WHERE transcript_id = ?
                  AND model_id = ?
                  AND technique_id = ?
                  AND rag_enabled = FALSE
                ORDER BY experiment_id
                LIMIT 1
                """, (rs, rowNum) -> rs.getInt("experiment_id"), transcriptId, modelId, techniqueId)
                .stream()
                .findFirst()
                .orElseGet(() -> createExperiment(transcriptId, modelId, techniqueId));
    }

    private int createExperiment(int transcriptId, int modelId, int techniqueId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO experiment (transcript_id, model_id, technique_id, rag_enabled, status)
                    VALUES (?, ?, ?, FALSE, 'pending')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, transcriptId);
            ps.setInt(2, modelId);
            ps.setInt(3, techniqueId);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Could not create experiment row");
        }
        return key.intValue();
    }

    private void clearPreviousResults(int experimentId) {
        jdbcTemplate.update("""
                DELETE ir
                FROM ingredient_result ir
                JOIN nutrition_result nr ON nr.result_id = ir.result_id
                WHERE nr.experiment_id = ?
                """, experimentId);

        jdbcTemplate.update("""
                DELETE FROM nutrition_result
                WHERE experiment_id = ?
                """, experimentId);
    }

    private void markExperimentRunning(int experimentId) {
        jdbcTemplate.update("""
                UPDATE experiment
                SET status = 'running', executed_at = NULL, started_at = NOW(), finished_at = NULL, processing_time_ms = NULL
                WHERE experiment_id = ?
                """, experimentId);
    }

    private void markExperimentFinished(int experimentId, String status, long processingTimeMs) {
        jdbcTemplate.update("""
                UPDATE experiment
                SET status = ?, executed_at = NOW(), finished_at = NOW(), processing_time_ms = ?
                WHERE experiment_id = ?
                """, status, processingTimeMs, experimentId);
    }

    private long elapsedMilliseconds(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
    }

    private String cleanTranscript(String transcript) {
        int divider = transcript.indexOf("=====================================");
        if (divider < 0) {
            return transcript.trim();
        }
        return transcript.substring(divider + "=====================================".length()).trim();
    }

    private String callOllama(String modelTag, String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "model", modelTag,
                "stream", false,
                "format", "json",
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaBaseUrl + "/api/chat"))
                .timeout(Duration.ofMinutes(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Ollama returned HTTP " + response.statusCode());
        }

        JsonNode responseJson = objectMapper.readTree(response.body());
        String content = responseJson.path("message").path("content").asText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Ollama returned an empty message");
        }
        return extractJsonObject(content);
    }

    private String extractJsonObject(String value) {
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }

    private boolean isValidJson(String rawOutput) {
        try {
            objectMapper.readTree(rawOutput);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private int insertNutritionResult(int experimentId, String rawOutput, boolean jsonValid) throws IOException {
        JsonNode root = jsonValid ? objectMapper.readTree(rawOutput) : objectMapper.createObjectNode();
        JsonNode serving = root.path("amount_per_serving");
        JsonNode total = root.path("nutrition_total");

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO nutrition_result (
                        experiment_id,
                        recipe_name,
                        servings_estimated,
                        serving_calories,
                        serving_total_fat_g,
                        serving_saturated_fat_g,
                        serving_cholesterol_mg,
                        serving_sodium_mg,
                        serving_carbohydrate_g,
                        serving_fiber_g,
                        serving_sugars_g,
                        serving_protein_g,
                        serving_vitamin_d_mcg,
                        serving_calcium_mg,
                        serving_iron_mg,
                        serving_potassium_mg,
                        total_calories,
                        total_fat_g,
                        total_saturated_fat_g,
                        total_cholesterol_mg,
                        total_sodium_mg,
                        total_carbohydrate_g,
                        total_fiber_g,
                        total_sugars_g,
                        total_protein_g,
                        total_vitamin_d_mcg,
                        total_calcium_mg,
                        total_iron_mg,
                        total_potassium_mg,
                        raw_json_output,
                        json_valid
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, experimentId);
            ps.setString(2, root.path("recipe_name").asText(null));
            setInteger(ps, 3, root.path("servings_estimated"));
            setFloat(ps, 4, serving.path("calories"));
            setFloat(ps, 5, serving.path("total_fat_g"));
            setFloat(ps, 6, serving.path("saturated_fat_g"));
            setFloat(ps, 7, serving.path("cholesterol_mg"));
            setFloat(ps, 8, serving.path("sodium_mg"));
            setFloat(ps, 9, serving.path("total_carbohydrate_g"));
            setFloat(ps, 10, serving.path("dietary_fiber_g"));
            setFloat(ps, 11, serving.path("total_sugars_g"));
            setFloat(ps, 12, serving.path("protein_g"));
            setFloat(ps, 13, serving.path("vitamin_d_mcg"));
            setFloat(ps, 14, serving.path("calcium_mg"));
            setFloat(ps, 15, serving.path("iron_mg"));
            setFloat(ps, 16, serving.path("potassium_mg"));
            setFloat(ps, 17, total.path("calories"));
            setFloat(ps, 18, total.path("total_fat_g"));
            setFloat(ps, 19, total.path("saturated_fat_g"));
            setFloat(ps, 20, total.path("cholesterol_mg"));
            setFloat(ps, 21, total.path("sodium_mg"));
            setFloat(ps, 22, total.path("total_carbohydrate_g"));
            setFloat(ps, 23, total.path("dietary_fiber_g"));
            setFloat(ps, 24, total.path("total_sugars_g"));
            setFloat(ps, 25, total.path("protein_g"));
            setFloat(ps, 26, total.path("vitamin_d_mcg"));
            setFloat(ps, 27, total.path("calcium_mg"));
            setFloat(ps, 28, total.path("iron_mg"));
            setFloat(ps, 29, total.path("potassium_mg"));
            ps.setString(30, rawOutput);
            ps.setBoolean(31, jsonValid);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Could not create nutrition_result row");
        }
        return key.intValue();
    }

    private void insertIngredientResults(int resultId, String rawOutput) throws IOException {
        JsonNode ingredients = objectMapper.readTree(rawOutput).path("ingredients");
        if (!ingredients.isArray()) {
            return;
        }

        for (JsonNode ingredient : ingredients) {
            jdbcTemplate.update("""
                    INSERT INTO ingredient_result (
                        result_id,
                        name_original,
                        name_en,
                        quantity_value,
                        unit_original,
                        unit_en,
                        estimated_weight_g,
                        calories,
                        total_fat_g,
                        saturated_fat_g,
                        cholesterol_mg,
                        sodium_mg,
                        total_carbohydrate_g,
                        dietary_fiber_g,
                        total_sugars_g,
                        protein_g,
                        vitamin_d_mcg,
                        calcium_mg,
                        iron_mg,
                        potassium_mg
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    resultId,
                    ingredient.path("ingredient_name_original").asText(null),
                    ingredient.path("ingredient_name_en").asText(null),
                    nullableFloat(ingredient.path("quantity_value")),
                    ingredient.path("quantity_unit_original").asText(null),
                    ingredient.path("quantity_unit_en").asText(null),
                    nullableFloat(ingredient.path("estimated_weight_g")),
                    nullableFloat(ingredient.path("calories")),
                    nullableFloat(ingredient.path("total_fat_g")),
                    nullableFloat(ingredient.path("saturated_fat_g")),
                    nullableFloat(ingredient.path("cholesterol_mg")),
                    nullableFloat(ingredient.path("sodium_mg")),
                    nullableFloat(ingredient.path("total_carbohydrate_g")),
                    nullableFloat(ingredient.path("dietary_fiber_g")),
                    nullableFloat(ingredient.path("total_sugars_g")),
                    nullableFloat(ingredient.path("protein_g")),
                    nullableFloat(ingredient.path("vitamin_d_mcg")),
                    nullableFloat(ingredient.path("calcium_mg")),
                    nullableFloat(ingredient.path("iron_mg")),
                    nullableFloat(ingredient.path("potassium_mg"))
            );
        }
    }

    private void setInteger(PreparedStatement ps, int index, JsonNode node) throws java.sql.SQLException {
        if (node.isMissingNode() || node.isNull()) {
            ps.setObject(index, null);
        } else {
            ps.setInt(index, node.asInt());
        }
    }

    private void setFloat(PreparedStatement ps, int index, JsonNode node) throws java.sql.SQLException {
        Float value = nullableFloat(node);
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setFloat(index, value);
        }
    }

    private Float nullableFloat(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        return (float) node.asDouble();
    }

    private record TranscriptInput(int transcriptId, String filePath) {
    }

    private record ModelInput(String modelTag) {
    }

    private record PromptInput(String systemPrompt, String userPrompt) {
    }
}
