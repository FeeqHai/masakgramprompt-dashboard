package my.utem.ftmk.masakgramprompt.service;

import my.utem.ftmk.masakgramprompt.model.AudioFileRecord;
import my.utem.ftmk.masakgramprompt.model.DashboardSummary;
import my.utem.ftmk.masakgramprompt.model.ExperimentRecord;
import my.utem.ftmk.masakgramprompt.model.LlmModelOption;
import my.utem.ftmk.masakgramprompt.model.NutritionResultRecord;
import my.utem.ftmk.masakgramprompt.model.PromptTechniqueOption;
import my.utem.ftmk.masakgramprompt.model.Reel;
import my.utem.ftmk.masakgramprompt.model.TranscriptRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Service
public class DashboardService {

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
                    e.processing_time_ms
                FROM experiment e
                JOIN transcript t ON t.transcript_id = e.transcript_id
                JOIN llm_model lm ON lm.model_id = e.model_id
                JOIN prompt_technique pt ON pt.technique_id = e.technique_id
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
            return experiment;
        }, reelId);
    }

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

    private int count(String tableName) {
        Integer value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return value == null ? 0 : value;
    }

    private int countExperimentsByStatus(String status) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM experiment WHERE status = ?",
                Integer.class,
                status
        );
        return value == null ? 0 : value;
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

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

    private Float getFloatOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return Float.parseFloat(value.toString());
    }
}
