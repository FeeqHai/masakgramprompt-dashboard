# MasakGramPrompt Dashboard

This is the Week 06-style Spring Boot dashboard for the project.

## What It Shows Now

- Summary counters for Reels, audio files, transcripts, ground truth, and experiments.
- The 50 Reel records imported into MySQL.
- Status badges for audio, transcript, ground truth, and experiment completion.
- A working `View` button for each Reel.
- A Reel detail page at `/reels/{id}` showing Reel metadata, audio status, transcript status, and experiment status.
- A JSON API at `/api/reels`.

## Import Into Eclipse

1. Open Eclipse.
2. Go to `File > Import`.
3. Select `Maven > Existing Maven Projects`.
4. Browse to:

```text
C:\Users\rafee\Documents\UTeM\DAD\Project Specification-20260618\masakgramprompt-dashboard
```

5. Click `Finish`.
6. Right-click the project.
7. Select `Maven > Update Project`.
8. Tick `Force Update of Snapshots/Releases`.
9. Click `OK`.

## Run In Eclipse

1. Open:

```text
src/main/java/my/utem/ftmk/masakgramprompt/MasakGramPromptDashboardApplication.java
```

2. Right-click the file.
3. Select `Run As > Run Configurations`.
4. Choose the dashboard Java application.
5. Open the `Environment` tab.
6. Click Add.
7. Add this variable:

```text
Name: MASAKGRAM_DB_PASSWORD
Value: your MySQL password
```


7. Click `Apply > Run`.

## Open Dashboard

After Spring Boot starts, open:

```text
http://localhost:8080/dashboard
```

API endpoint:

```text
http://localhost:8080/api/reels
```

## Next Development Step

## Dataset Sync, Batch Runs, and Excel Export

The current project `data` folder contains 50 audio files and 50 transcript files.
When the dashboard starts, it safely synchronizes them into MySQL:

```text
data/audio/<instagram-reel-id>.mp3
data/transcripts/transcription_<reel-number>.txt
```

The sync inserts missing `audio_file` and `transcript` records. Running it again updates the file metadata and does not delete experiment data.

Before running, set your MySQL password in the Eclipse Run Configuration environment:

```text
Name: MASAKGRAM_DB_PASSWORD
Value: your MySQL root password
```

After the dashboard opens:

1. Select `Sync Data` to re-read the current `data` folder.
2. Choose one LLM model and one prompt technique in `Batch Experiment`.
3. Select `Run Batch`.
4. The system processes every available transcript one at a time, which is safer for a local Ollama model.
5. Select `Download Excel` to export the current experiment results.

The Excel export contains:

- `Summary`: total experiment, valid JSON, and ingredient counts.
- `LLM Results`: one row per model and prompt experiment, including nutrition totals and raw LLM JSON.
- `Extracted Ingredients`: one row per ingredient returned by the LLM.

Run a separate batch for each model and prompt-technique combination that your team needs to compare.
