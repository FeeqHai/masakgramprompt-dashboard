package my.utem.ftmk.masakgramprompt.model;

import java.time.LocalDate;

/**
 * Holds reel metadata and readiness counters for dashboard and detail pages.
 */
public class Reel {

    private int reelId;
    private String reelIdInstagram;
    private String reelUrl;
    private String identifiedByMatric;
    private String identifiedByName;
    private LocalDate identifiedDate;
    private String influencerName;
    private String influencerAccount;
    private boolean hasAudio;
    private boolean hasTranscript;
    private boolean hasGroundTruth;
    private int experimentCount;
    private int completedExperimentCount;

    public int getReelId() {
        return reelId;
    }

    public void setReelId(int reelId) {
        this.reelId = reelId;
    }

    public String getReelIdInstagram() {
        return reelIdInstagram;
    }

    public void setReelIdInstagram(String reelIdInstagram) {
        this.reelIdInstagram = reelIdInstagram;
    }

    public String getReelUrl() {
        return reelUrl;
    }

    public void setReelUrl(String reelUrl) {
        this.reelUrl = reelUrl;
    }

    public String getIdentifiedByMatric() {
        return identifiedByMatric;
    }

    public void setIdentifiedByMatric(String identifiedByMatric) {
        this.identifiedByMatric = identifiedByMatric;
    }

    public String getIdentifiedByName() {
        return identifiedByName;
    }

    public void setIdentifiedByName(String identifiedByName) {
        this.identifiedByName = identifiedByName;
    }

    public LocalDate getIdentifiedDate() {
        return identifiedDate;
    }

    public void setIdentifiedDate(LocalDate identifiedDate) {
        this.identifiedDate = identifiedDate;
    }

    public String getInfluencerName() {
        return influencerName;
    }

    public void setInfluencerName(String influencerName) {
        this.influencerName = influencerName;
    }

    public String getInfluencerAccount() {
        return influencerAccount;
    }

    public void setInfluencerAccount(String influencerAccount) {
        this.influencerAccount = influencerAccount;
    }

    public boolean isHasAudio() {
        return hasAudio;
    }

    public void setHasAudio(boolean hasAudio) {
        this.hasAudio = hasAudio;
    }

    public boolean isHasTranscript() {
        return hasTranscript;
    }

    public void setHasTranscript(boolean hasTranscript) {
        this.hasTranscript = hasTranscript;
    }

    public boolean isHasGroundTruth() {
        return hasGroundTruth;
    }

    public void setHasGroundTruth(boolean hasGroundTruth) {
        this.hasGroundTruth = hasGroundTruth;
    }

    public int getExperimentCount() {
        return experimentCount;
    }

    public void setExperimentCount(int experimentCount) {
        this.experimentCount = experimentCount;
    }

    public int getCompletedExperimentCount() {
        return completedExperimentCount;
    }

    public void setCompletedExperimentCount(int completedExperimentCount) {
        this.completedExperimentCount = completedExperimentCount;
    }
}
