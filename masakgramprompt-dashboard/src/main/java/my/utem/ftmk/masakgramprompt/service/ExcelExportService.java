package my.utem.ftmk.masakgramprompt.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Generates the Excel workbook used to inspect saved LLM experiment outputs.
 */
@Service
public class ExcelExportService {

    private static final int EXCEL_CELL_TEXT_LIMIT = 32_000;

    private final JdbcTemplate jdbcTemplate;

    public ExcelExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Writes a multi-sheet workbook containing summary, result, and ingredient data.
     */
    public void writeLlmResults(OutputStream outputStream) throws IOException {
        List<ResultRow> results = loadResults();
        List<IngredientRow> ingredients = loadIngredients();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));

            createSummarySheet(workbook, headerStyle, results, ingredients);
            createResultsSheet(workbook, headerStyle, dateStyle, results);
            createIngredientsSheet(workbook, headerStyle, ingredients);

            workbook.write(outputStream);
        }
    }

    /**
     * Creates a short overview sheet with export counts and run status totals.
     */
    private void createSummarySheet(
            XSSFWorkbook workbook,
            CellStyle headerStyle,
            List<ResultRow> results,
            List<IngredientRow> ingredients
    ) {
        Sheet sheet = workbook.createSheet("Summary");
        sheet.createRow(0).createCell(0).setCellValue("MasakGramPrompt LLM Results Export");
        sheet.getRow(0).getCell(0).setCellStyle(headerStyle);

        String[] headers = {"Item", "Value"};
        writeHeader(sheet, 2, headers, headerStyle);

        int completed = (int) results.stream().filter(row -> "completed".equals(row.status())).count();
        int running = (int) results.stream().filter(row -> "running".equals(row.status())).count();
        int pending = (int) results.stream().filter(row -> "pending".equals(row.status())).count();
        int failed = (int) results.stream().filter(row -> "failed".equals(row.status())).count();
        int validJson = (int) results.stream().filter(row -> Boolean.TRUE.equals(row.jsonValid())).count();
        Object[][] values = {
                {"Exported at", LocalDateTime.now().toString()},
                {"Experiment rows", results.size()},
                {"Completed experiments", completed},
                {"Running experiments", running},
                {"Pending experiments", pending},
                {"Failed experiments", failed},
                {"Valid JSON results", validJson},
                {"Extracted ingredient rows", ingredients.size()},
                {"Purpose", "Shows the current saved experiment state, including partial batches."}
        };
        for (int index = 0; index < values.length; index++) {
            Row row = sheet.createRow(index + 3);
            setCell(row, 0, values[index][0], null);
            setCell(row, 1, values[index][1], null);
        }

        sheet.setColumnWidth(0, 28 * 256);
        sheet.setColumnWidth(1, 78 * 256);
        sheet.createFreezePane(0, 3);
    }

    /**
     * Creates one row per experiment with model, prompt, nutrition, and JSON fields.
     */
    private void createResultsSheet(
            XSSFWorkbook workbook,
            CellStyle headerStyle,
            CellStyle dateStyle,
            List<ResultRow> results
    ) {
        Sheet sheet = workbook.createSheet("LLM Results");
        String[] headers = {
                "Experiment ID", "Reel No.", "Instagram Reel ID", "Transcript File", "Model", "Model Tag",
                "Prompt Technique", "Status", "Executed At", "Recipe Name", "Servings", "JSON Valid",
                "Total Calories", "Total Protein (g)", "Total Carbohydrate (g)", "Total Fat (g)",
                "Serving Calories", "Serving Protein (g)", "Serving Carbohydrate (g)", "Serving Fat (g)",
                "Raw LLM JSON"
        };
        writeHeader(sheet, 0, headers, headerStyle);

        int rowIndex = 1;
        for (ResultRow result : results) {
            Row row = sheet.createRow(rowIndex++);
            Object[] values = {
                    result.experimentId(), result.reelId(), result.instagramId(), result.transcriptFile(),
                    result.modelName(), result.modelTag(), result.techniqueName(), result.status(), result.executedAt(),
                    result.recipeName(), result.servings(), result.jsonValid(), result.totalCalories(), result.totalProteinG(),
                    result.totalCarbohydrateG(), result.totalFatG(), result.servingCalories(), result.servingProteinG(),
                    result.servingCarbohydrateG(), result.servingFatG(), shortenForExcel(result.rawJson())
            };
            for (int column = 0; column < values.length; column++) {
                setCell(row, column, values[column], column == 8 ? dateStyle : null);
            }
        }

        int[] widths = {14, 10, 20, 28, 24, 22, 22, 14, 20, 28, 10, 12, 16, 18, 24, 16, 18, 20, 27, 18, 70};
        setColumnWidths(sheet, widths);
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, headers.length - 1));
       }

    /**
     * Creates one row per extracted ingredient result.
     */
    private void createIngredientsSheet(
            XSSFWorkbook workbook,
            CellStyle headerStyle,
            List<IngredientRow> ingredients
    ) {
        Sheet sheet = workbook.createSheet("Extracted Ingredients");
        String[] headers = {
                "Experiment ID", "Reel No.", "Instagram Reel ID", "Model", "Prompt Technique",
                "Ingredient (Original)", "Ingredient (English)", "Quantity", "Unit (Original)", "Unit (English)",
                "Estimated Weight (g)", "Calories", "Protein (g)", "Carbohydrate (g)", "Fat (g)"
        };
        writeHeader(sheet, 0, headers, headerStyle);

        int rowIndex = 1;
        for (IngredientRow ingredient : ingredients) {
            Row row = sheet.createRow(rowIndex++);
            Object[] values = {
                    ingredient.experimentId(), ingredient.reelId(), ingredient.instagramId(), ingredient.modelName(),
                    ingredient.techniqueName(), ingredient.nameOriginal(), ingredient.nameEnglish(), ingredient.quantityValue(),
                    ingredient.unitOriginal(), ingredient.unitEnglish(), ingredient.estimatedWeightG(), ingredient.calories(),
                    ingredient.proteinG(), ingredient.carbohydrateG(), ingredient.fatG()
            };
            for (int column = 0; column < values.length; column++) {
                setCell(row, column, values[column], null);
            }
        }

        int[] widths = {14, 10, 20, 24, 22, 28, 28, 12, 18, 18, 20, 14, 14, 20, 14};
        setColumnWidths(sheet, widths);
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, headers.length - 1));
    }

    /**
     * Creates the visual style used for workbook header rows.
     */
    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    /**
     * Writes a header row and applies the shared header style.
     */
    private void writeHeader(Sheet sheet, int rowIndex, String[] headers, CellStyle headerStyle) {
        Row row = sheet.createRow(rowIndex);
        for (int column = 0; column < headers.length; column++) {
            Cell cell = row.createCell(column);
            cell.setCellValue(headers[column]);
            cell.setCellStyle(headerStyle);
        }
    }

    /**
     * Applies fixed column widths so exported sheets are readable when opened.
     */
    private void setColumnWidths(Sheet sheet, int[] widths) {
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
    }

    /**
     * Writes a supported Java value into an Excel cell.
     */
    private void setCell(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()));
        } else {
            cell.setCellValue(value.toString());
        }

        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    /**
     * Trims long raw JSON values to stay below Excel's cell character limit.
     */
    private String shortenForExcel(String value) {
        if (value == null || value.length() <= EXCEL_CELL_TEXT_LIMIT) {
            return value;
        }
        return value.substring(0, EXCEL_CELL_TEXT_LIMIT) + "\n[Raw JSON shortened for the Excel cell limit]";
    }

    /**
     * Loads experiment and nutrition result rows for the workbook.
     */
    private List<ResultRow> loadResults() {
        return jdbcTemplate.query("""
                SELECT
                    e.experiment_id,
                    r.reel_id,
                    r.reel_id_instagram,
                    t.file_name AS transcript_file,
                    lm.model_name,
                    lm.model_tag,
                    pt.technique_name,
                    e.status,
                    e.executed_at,
                    nr.recipe_name,
                    nr.servings_estimated,
                    nr.json_valid,
                    nr.total_calories,
                    nr.total_protein_g,
                    nr.total_carbohydrate_g,
                    nr.total_fat_g,
                    nr.serving_calories,
                    nr.serving_protein_g,
                    nr.serving_carbohydrate_g,
                    nr.serving_total_fat_g,
                    nr.raw_json_output
                FROM experiment e
                JOIN transcript t ON t.transcript_id = e.transcript_id
                JOIN reel r ON r.reel_id = t.reel_id
                JOIN llm_model lm ON lm.model_id = e.model_id
                JOIN prompt_technique pt ON pt.technique_id = e.technique_id
                LEFT JOIN nutrition_result nr ON nr.experiment_id = e.experiment_id
                ORDER BY r.reel_id, e.experiment_id
                """, (rs, rowNum) -> new ResultRow(
                rs.getInt("experiment_id"),
                rs.getInt("reel_id"),
                rs.getString("reel_id_instagram"),
                rs.getString("transcript_file"),
                rs.getString("model_name"),
                rs.getString("model_tag"),
                rs.getString("technique_name"),
                rs.getString("status"),
                rs.getTimestamp("executed_at") == null ? null : rs.getTimestamp("executed_at").toLocalDateTime(),
                rs.getString("recipe_name"),
                getIntegerOrNull(rs, "servings_estimated"),
                getBooleanOrNull(rs.getObject("json_valid")),
                getDoubleOrNull(rs.getObject("total_calories")),
                getDoubleOrNull(rs.getObject("total_protein_g")),
                getDoubleOrNull(rs.getObject("total_carbohydrate_g")),
                getDoubleOrNull(rs.getObject("total_fat_g")),
                getDoubleOrNull(rs.getObject("serving_calories")),
                getDoubleOrNull(rs.getObject("serving_protein_g")),
                getDoubleOrNull(rs.getObject("serving_carbohydrate_g")),
                getDoubleOrNull(rs.getObject("serving_total_fat_g")),
                rs.getString("raw_json_output")
        ));
    }

    /**
     * Loads extracted ingredient rows for the workbook.
     */
    private List<IngredientRow> loadIngredients() {
        return jdbcTemplate.query("""
                SELECT
                    e.experiment_id,
                    r.reel_id,
                    r.reel_id_instagram,
                    lm.model_name,
                    pt.technique_name,
                    ir.name_original,
                    ir.name_en,
                    ir.quantity_value,
                    ir.unit_original,
                    ir.unit_en,
                    ir.estimated_weight_g,
                    ir.calories,
                    ir.protein_g,
                    ir.total_carbohydrate_g,
                    ir.total_fat_g
                FROM ingredient_result ir
                JOIN nutrition_result nr ON nr.result_id = ir.result_id
                JOIN experiment e ON e.experiment_id = nr.experiment_id
                JOIN transcript t ON t.transcript_id = e.transcript_id
                JOIN reel r ON r.reel_id = t.reel_id
                JOIN llm_model lm ON lm.model_id = e.model_id
                JOIN prompt_technique pt ON pt.technique_id = e.technique_id
                ORDER BY r.reel_id, e.experiment_id, ir.ingredient_id
                """, (rs, rowNum) -> new IngredientRow(
                rs.getInt("experiment_id"),
                rs.getInt("reel_id"),
                rs.getString("reel_id_instagram"),
                rs.getString("model_name"),
                rs.getString("technique_name"),
                rs.getString("name_original"),
                rs.getString("name_en"),
                getDoubleOrNull(rs.getObject("quantity_value")),
                rs.getString("unit_original"),
                rs.getString("unit_en"),
                getDoubleOrNull(rs.getObject("estimated_weight_g")),
                getDoubleOrNull(rs.getObject("calories")),
                getDoubleOrNull(rs.getObject("protein_g")),
                getDoubleOrNull(rs.getObject("total_carbohydrate_g")),
                getDoubleOrNull(rs.getObject("total_fat_g"))
        ));
    }

    /**
     * Reads a nullable integer from JDBC without converting null to zero.
     */
    private Integer getIntegerOrNull(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    /**
     * Converts JDBC numeric values to nullable doubles.
     */
    private Double getDoubleOrNull(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    /**
     * Converts JDBC boolean-like values to nullable booleans.
     */
    private Boolean getBooleanOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return (Boolean) value;
    }

    private record ResultRow(
            int experimentId,
            int reelId,
            String instagramId,
            String transcriptFile,
            String modelName,
            String modelTag,
            String techniqueName,
            String status,
            LocalDateTime executedAt,
            String recipeName,
            Integer servings,
            Boolean jsonValid,
            Double totalCalories,
            Double totalProteinG,
            Double totalCarbohydrateG,
            Double totalFatG,
            Double servingCalories,
            Double servingProteinG,
            Double servingCarbohydrateG,
            Double servingFatG,
            String rawJson
    ) {
    }

    private record IngredientRow(
            int experimentId,
            int reelId,
            String instagramId,
            String modelName,
            String techniqueName,
            String nameOriginal,
            String nameEnglish,
            Double quantityValue,
            String unitOriginal,
            String unitEnglish,
            Double estimatedWeightG,
            Double calories,
            Double proteinG,
            Double carbohydrateG,
            Double fatG
    ) {
    }
}
