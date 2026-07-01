USE `masakgram_prompt_combined`;

SELECT COUNT(*) AS ground_truth_reel_count
FROM ground_truth_reel;

SELECT COUNT(*) AS ground_truth_ingredient_count
FROM ground_truth_ingredient;

SELECT
    t.transcript_id,
    COUNT(gti.gt_ingredient_id) AS ingredient_count
FROM transcript t
LEFT JOIN ground_truth_reel gtr
    ON t.transcript_id = gtr.transcript_id
LEFT JOIN ground_truth_ingredient gti
    ON gtr.gt_reel_id = gti.gt_reel_id
WHERE t.transcript_id BETWEEN 1 AND 50
GROUP BY t.transcript_id
ORDER BY t.transcript_id;
