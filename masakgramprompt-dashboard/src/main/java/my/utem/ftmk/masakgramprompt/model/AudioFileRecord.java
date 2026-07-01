package my.utem.ftmk.masakgramprompt.model;

import java.time.LocalDateTime;

/**
 * Represents one audio_file row linked to a reel.
 */
public class AudioFileRecord {

    private int audioId;
    private int reelId;
    private String fileName;
    private String filePath;
    private LocalDateTime fileCreatedAt;
    private Long fileSizeBytes;
    private String fileFormat;
    private Boolean reelAudioConsistent;
    private String verifiedByMatric;
    private String verifiedByName;
    private LocalDateTime verifiedAt;

    public int getAudioId() {
        return audioId;
    }

    public void setAudioId(int audioId) {
        this.audioId = audioId;
    }

    public int getReelId() {
        return reelId;
    }

    public void setReelId(int reelId) {
        this.reelId = reelId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public LocalDateTime getFileCreatedAt() {
        return fileCreatedAt;
    }

    public void setFileCreatedAt(LocalDateTime fileCreatedAt) {
        this.fileCreatedAt = fileCreatedAt;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getFileFormat() {
        return fileFormat;
    }

    public void setFileFormat(String fileFormat) {
        this.fileFormat = fileFormat;
    }

    public Boolean getReelAudioConsistent() {
        return reelAudioConsistent;
    }

    public void setReelAudioConsistent(Boolean reelAudioConsistent) {
        this.reelAudioConsistent = reelAudioConsistent;
    }

    public String getVerifiedByMatric() {
        return verifiedByMatric;
    }

    public void setVerifiedByMatric(String verifiedByMatric) {
        this.verifiedByMatric = verifiedByMatric;
    }

    public String getVerifiedByName() {
        return verifiedByName;
    }

    public void setVerifiedByName(String verifiedByName) {
        this.verifiedByName = verifiedByName;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
