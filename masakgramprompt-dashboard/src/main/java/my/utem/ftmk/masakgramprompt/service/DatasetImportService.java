package my.utem.ftmk.masakgramprompt.service;

import my.utem.ftmk.masakgramprompt.model.DataSyncSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Timestamp;
import java.util.List;

/**
 * Synchronizes local audio and transcript files into the database metadata tables.
 */
@Service
public class DatasetImportService {

    private final JdbcTemplate jdbcTemplate;
    private final String dataDirectory;

    public DatasetImportService(
            JdbcTemplate jdbcTemplate,
            @Value("${masakgram.data-directory:data}") String dataDirectory
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataDirectory = dataDirectory;
    }

    @EventListener(ApplicationReadyEvent.class)
    /**
     * Runs a best-effort dataset sync after Spring Boot finishes starting.
     */
    public void syncOnStartup() {
        try {
            DataSyncSummary summary = syncDataset();
            System.out.printf(
                    "Dataset sync complete: %d audio files, %d transcripts.%n",
                    summary.audioFilesSynced(),
                    summary.transcriptsSynced()
            );
        } catch (Exception ex) {
            System.err.println("Dataset sync was skipped: " + ex.getMessage());
        }
    }

    /**
     * Scans the configured data directory and inserts or updates matching audio/transcript records.
     */
    public DataSyncSummary syncDataset() {
        Path root = Path.of(dataDirectory).toAbsolutePath().normalize();
        Path audioDirectory = root.resolve("audio");
        Path transcriptDirectory = root.resolve("transcripts");

        int audioSynced = 0;
        int transcriptSynced = 0;
        int missingAudio = 0;
        int missingTranscript = 0;

        for (ReelInput reel : loadReels()) {
            Path audioPath = audioDirectory.resolve(reel.instagramId() + ".mp3");
            if (Files.isRegularFile(audioPath)) {
                syncAudio(reel.reelId(), audioPath);
                audioSynced++;
            } else {
                missingAudio++;
            }

            Path transcriptPath = transcriptDirectory.resolve("transcription_" + reel.reelId() + ".txt");
            if (Files.isRegularFile(transcriptPath)) {
                Integer audioId = findAudioId(reel.reelId());
                if (audioId != null) {
                    syncTranscript(reel.reelId(), audioId, transcriptPath);
                    transcriptSynced++;
                }
            } else {
                missingTranscript++;
            }
        }

        return new DataSyncSummary(audioSynced, transcriptSynced, missingAudio, missingTranscript);
    }

    /**
     * Loads reels so local files can be matched by Instagram ID or reel number.
     */
    private List<ReelInput> loadReels() {
        return jdbcTemplate.query(
                "SELECT reel_id, reel_id_instagram FROM reel ORDER BY reel_id",
                (rs, rowNum) -> new ReelInput(rs.getInt("reel_id"), rs.getString("reel_id_instagram"))
        );
    }

    /**
     * Inserts a new audio row or refreshes the existing row for the reel.
     */
    private void syncAudio(int reelId, Path file) {
        FileMetadata metadata = readMetadata(file);
        Integer existingId = findAudioId(reelId);

        if (existingId == null) {
            jdbcTemplate.update("""
                    INSERT INTO audio_file (
                        reel_id, file_name, file_path, file_created_at, file_size_bytes, file_format
                    ) VALUES (?, ?, ?, ?, ?, 'mp3')
                    """,
                    reelId,
                    metadata.fileName(),
                    metadata.absolutePath(),
                    metadata.createdAt(),
                    metadata.sizeBytes()
            );
            return;
        }

        jdbcTemplate.update("""
                UPDATE audio_file
                SET file_name = ?, file_path = ?, file_created_at = ?, file_size_bytes = ?, file_format = 'mp3'
                WHERE audio_id = ?
                """,
                metadata.fileName(),
                metadata.absolutePath(),
                metadata.createdAt(),
                metadata.sizeBytes(),
                existingId
        );
    }

    /**
     * Inserts a new transcript row or refreshes the existing row linked to the reel audio.
     */
    private void syncTranscript(int reelId, int audioId, Path file) {
        FileMetadata metadata = readMetadata(file);
        Integer existingId = jdbcTemplate.query(
                "SELECT transcript_id FROM transcript WHERE reel_id = ? LIMIT 1",
                (rs, rowNum) -> rs.getInt("transcript_id"),
                reelId
        ).stream().findFirst().orElse(null);

        if (existingId == null) {
            jdbcTemplate.update("""
                    INSERT INTO transcript (
                        reel_id, audio_id, file_name, file_path, file_created_at, file_size_bytes, file_format
                    ) VALUES (?, ?, ?, ?, ?, ?, 'txt')
                    """,
                    reelId,
                    audioId,
                    metadata.fileName(),
                    metadata.absolutePath(),
                    metadata.createdAt(),
                    metadata.sizeBytes()
            );
            return;
        }

        jdbcTemplate.update("""
                UPDATE transcript
                SET audio_id = ?, file_name = ?, file_path = ?, file_created_at = ?, file_size_bytes = ?, file_format = 'txt'
                WHERE transcript_id = ?
                """,
                audioId,
                metadata.fileName(),
                metadata.absolutePath(),
                metadata.createdAt(),
                metadata.sizeBytes(),
                existingId
        );
    }

    /**
     * Returns the database audio id for a reel when audio metadata already exists.
     */
    private Integer findAudioId(int reelId) {
        return jdbcTemplate.query(
                "SELECT audio_id FROM audio_file WHERE reel_id = ? LIMIT 1",
                (rs, rowNum) -> rs.getInt("audio_id"),
                reelId
        ).stream().findFirst().orElse(null);
    }

    /**
     * Reads file metadata that is stored in audio_file and transcript rows.
     */
    private FileMetadata readMetadata(Path file) {
        try {
            FileTime modifiedTime = Files.getLastModifiedTime(file);
            return new FileMetadata(
                    file.getFileName().toString(),
                    file.toAbsolutePath().normalize().toString(),
                    Timestamp.from(modifiedTime.toInstant()),
                    Files.size(file)
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read file metadata for " + file, ex);
        }
    }

    private record ReelInput(int reelId, String instagramId) {
    }

    private record FileMetadata(String fileName, String absolutePath, Timestamp createdAt, long sizeBytes) {
    }
}
