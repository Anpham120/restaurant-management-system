package vn.khoibep.rms.common.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A workbook of one sheet in the .xlsx format that Excel and import tools such as MISA's read: text goes to the shared
 * strings, so a tax code keeps its leading zero, and numbers stay numbers. No formulas, formats or other sheets.
 */
public final class Xlsx {

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
    private static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String OFFICE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private static final String CONTENT_TYPES = XML
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument"
            + ".spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats"
            + "-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats"
            + "-officedocument.spreadsheetml.sharedStrings+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument"
            + ".spreadsheetml.styles+xml\"/>"
            + "</Types>";

    private static final String PACKAGE_RELS = XML + "<Relationships xmlns=\"" + RELATIONSHIPS + "\">"
            + "<Relationship Id=\"rId1\" Type=\"" + OFFICE + "/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    private static final String WORKBOOK_RELS = XML + "<Relationships xmlns=\"" + RELATIONSHIPS + "\">"
            + "<Relationship Id=\"rId1\" Type=\"" + OFFICE + "/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"" + OFFICE + "/sharedStrings\" Target=\"sharedStrings.xml\"/>"
            + "<Relationship Id=\"rId3\" Type=\"" + OFFICE + "/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>";

    /** The default style only; some readers expect the part to be there. */
    private static final String STYLES = XML + "<styleSheet xmlns=\"" + MAIN + "\">"
            + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
            + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill></fills>"
            + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";

    private Xlsx() {
    }

    /** Rows of text, or of whole numbers ({@link Number}); a null cell stays empty. The first row is the header. */
    public static byte[] workbook(String sheetName, List<? extends List<?>> rows) {
        Map<String, Integer> strings = new LinkedHashMap<>();
        int stringCells = 0;
        StringBuilder sheet = new StringBuilder(XML).append("<worksheet xmlns=\"").append(MAIN).append("\"><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            sheet.append("<row r=\"").append(r + 1).append("\">");
            List<?> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                Object value = row.get(c);
                if (value == null) {
                    continue;
                }
                String ref = column(c) + (r + 1);
                if (value instanceof Number number) {
                    sheet.append("<c r=\"").append(ref).append("\"><v>").append(number).append("</v></c>");
                } else {
                    int index = strings.computeIfAbsent(value.toString(), text -> strings.size());
                    stringCells++;
                    sheet.append("<c r=\"").append(ref).append("\" t=\"s\"><v>").append(index).append("</v></c>");
                }
            }
            sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");

        StringBuilder shared = new StringBuilder(XML).append("<sst xmlns=\"").append(MAIN).append("\" count=\"")
                .append(stringCells).append("\" uniqueCount=\"").append(strings.size()).append("\">");
        strings.keySet().forEach(text -> shared.append("<si><t xml:space=\"preserve\">").append(escape(text))
                .append("</t></si>"));
        shared.append("</sst>");

        String workbook = XML + "<workbook xmlns=\"" + MAIN + "\" xmlns:r=\"" + OFFICE + "\"><sheets><sheet name=\""
                + escape(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", CONTENT_TYPES);
            put(zip, "_rels/.rels", PACKAGE_RELS);
            put(zip, "xl/workbook.xml", workbook);
            put(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
            put(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            put(zip, "xl/sharedStrings.xml", shared.toString());
            put(zip, "xl/styles.xml", STYLES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** 0 is A, 25 is Z, 26 is AA. */
    static String column(int index) {
        StringBuilder letters = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) {
            letters.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return letters.toString();
    }

    /** XML text: the five special characters escaped, control characters XML does not allow left out. */
    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            switch (ch) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
