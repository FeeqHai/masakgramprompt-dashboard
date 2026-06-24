package my.utem.ftmk.masakgramprompt.model;

import java.time.LocalDateTime;

public class TranscriptRecord {

    private int transcriptId;
    private int reelId;
    private int audioId;
    private String fileName;
    private String filePath;
    private LocalDateTime fileCreatedAt;
    private Long fileSizeBytes;
    private String fileFormat;
    private Boolean audioTranscriptConsistent;
    private String verifiedByMatric;
    private String verifiedByName;
    private LocalDateTime verifiedAt;

    public int getTranscriptId() {
        return transcriptId;
    }

    public void setTranscriptId(int transcriptId) {
        this.transcriptId = transcriptId;
    }

    public int getReelId() {
        return reelId;
    }

    public void setReelId(int reelId) {
        this.reelId = reelId;
    }

    public int getAudioId() {
        return audioId;
    }

    public void setAudioId(int audioId) {
        this.audioId = audioId;
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

    public Boolean getAudioTranscriptConsistent() {
        return audioTranscriptConsistent;
    }

    public void setAudioTranscriptConsistent(Boolean audioTranscriptConsistent) {
        this.audioTranscriptConsistent = audioTranscriptConsistent;
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
