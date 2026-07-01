# MasakGramPrompt Dashboard

Spring Boot, MySQL, and Thymeleaf dashboard for the BITP 3123 project:
`Nutritional Analytics from Code-switched Gastronomy Influencer Reels Using Prompt Engineering and Large Language Models`.

## Working Directory

```text
C:\Users\rafee\git\repository\masakgramprompt-dashboard
```

## Main Navigation

The dashboard now uses the lecturer-requested review flow:

```text
/models
  -> /models/{modelId}/techniques
  -> /models/{modelId}/techniques/{techniqueId}/reels
  -> /models/{modelId}/techniques/{techniqueId}/reels/{reelId}/result
```

Main pages:

- `/models`: choose one of the five LLM models.
- `/batch`: run one model with one or more prompt techniques.
- `/performance`: compare runtime and speed using `experiment.processing_time_ms`.
- `/evaluation`: compare correctness against human ground truth.
- `/exports`: download the 10 required CSV evaluation files.

The older `/dashboard` and `/reels/{id}` pages are still available as legacy helpers, but the primary review path starts at `/models`.

## Ground Truth Import

A verified import script already exists outside this repo:

```text
import_ground_truth_into_masakgram_prompt_combined.sql
```

Import it manually in MySQL Workbench or from a terminal. Do not run destructive SQL from Java startup.
The import should only affect:

- `ground_truth_reel`
- `ground_truth_ingredient`

It should not delete or corrupt:

- `reel`
- `audio_file`
- `transcript`
- `experiment`
- `nutrition_result`
- `ingredient_result`

Expected counts after import:

- `ground_truth_reel`: 50 rows
- `ground_truth_ingredient`: 775 rows

Verification SQL was added here:

```text
data/verify_ground_truth_import.sql
```

Run it after import to check total counts and ingredient counts for transcripts 1 to 50.

## Run In Eclipse

1. Open Eclipse.
2. Select `File > Import`.
3. Select `Maven > Existing Maven Projects`.
4. Browse to the working directory above.
5. Select `Maven > Update Project`.
6. Add your MySQL password to the run configuration:

```text
Name: MASAKGRAM_DB_PASSWORD
Value: your MySQL password
```

7. Run:

```text
src/main/java/my/utem/ftmk/masakgramprompt/MasakGramPromptDashboardApplication.java
```

Then open:

```text
http://localhost:8080/models
```

## Batch Experiment

Open `/batch`.

Use this page for run mode:

1. Select one LLM model.
2. Select one or more prompt techniques.
3. Keep transcript scope as all imported transcripts.
4. Click `Run Batch Experiment`.
5. Watch the live progress section while the batch runs.

The batch runner reuses the existing experiment runner. It only clears and replaces results for the same transcript, model, and prompt-technique combination being rerun.

## Result Review

Use `/models` for review mode:

1. Choose a model.
2. Choose a prompt technique.
3. Choose a reel with a completed result.
4. Open the reel result page.

The result page shows:

- reel and transcript details
- selected model and prompt technique
- processing time
- ground truth availability and annotator
- transcript preview with simple Malay cooking-term highlighting
- ground truth vs AI ingredient comparison
- nutrition total comparison
- precision, recall, F1, JSON validity, and hallucination count
- collapsed raw JSON
- fact-sheet CSV download

## Performance vs Evaluation

`/performance` is for speed and runtime:

- uses `experiment.processing_time_ms`
- groups results by model and prompt technique
- calculates average, minimum, and maximum time from completed experiments only

`/evaluation` is for correctness:

- compares `ground_truth_ingredient` against `ingredient_result`
- calculates ingredient detection precision, recall, and F1
- calculates nutrition absolute errors
- tracks JSON validity and possible hallucination rate

The Evaluation Dashboard has four sections:

- `Ingredient Detection`: checks whether the AI found the correct ground truth ingredients.
- `Nutrition Accuracy`: compares nutrition totals using Mean Absolute Error (MAE).
- `JSON Quality`: checks whether the LLM output followed the required JSON format.
- `Condition Ranking`: ranks each model and prompt technique by F1 first, then hallucination rate, then calorie error.

Formula reminders:

- Precision = matched ingredients / AI extracted ingredients.
- Recall = matched ingredients / ground truth ingredients.
- F1 = balanced score between precision and recall.
- Nutrition MAE = average absolute difference between AI nutrition totals and ground truth totals.
- JSON validity rate = completed outputs with valid JSON / completed outputs.
- Hallucination rate = extra AI ingredients / AI extracted ingredients.

Each Evaluation row includes a `View Reels` link so you can move from:

```text
Evaluation -> Model + Technique Reels -> Individual Result / Fact Sheet
```

Keep these pages separate when explaining the system.

## CSV Exports

Open `/exports` to download:

1. `layer1a_exact_match.csv`
2. `layer1b_text_similarity.csv`
3. `layer2a_numeric_quantity.csv`
4. `layer2b_numeric_nutrition.csv`
5. `layer2c_nutrition_totals.csv`
6. `layer3a_json_validity.csv`
7. `layer3b_hallucination.csv`
8. `layer3c_ingredient_detection.csv`
9. `layer4_human_evaluation.csv`
10. `layer5_condition_scores.csv`

The existing Excel export remains available as an extra helper from `/exports/llm-results.xlsx`.

## Current Limitations

- Video duration and language tag are displayed as `Not available` because those fields are not in the current schema.
- Matching is intentionally simple: normalized exact match first, then basic contains matching.
- Hallucination count uses unmatched AI ingredients when no dedicated hallucination column exists.
- Custom transcript ranges are future work; the batch page currently runs all imported transcripts.
