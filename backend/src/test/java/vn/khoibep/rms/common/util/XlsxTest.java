package vn.khoibep.rms.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;

/** The .xlsx files exported for MISA (FR-20.4). */
class XlsxTest {

    @Test
    void columnsAreNamedLikeExcel() {
        assertThat(Xlsx.column(0)).isEqualTo("A");
        assertThat(Xlsx.column(25)).isEqualTo("Z");
        assertThat(Xlsx.column(26)).isEqualTo("AA");
        assertThat(Xlsx.column(701)).isEqualTo("ZZ");
        assertThat(Xlsx.column(702)).isEqualTo("AAA");
    }

    @Test
    void textIsEscapedAndControlCharactersLeftOut() {
        assertThat(Xlsx.escape("A&B <c> \"d\" 'e'\u0001")).isEqualTo("A&amp;B &lt;c&gt; &quot;d&quot; &apos;e&apos;");
    }

    @Test
    void aWorkbookHasEveryPartAndKeepsTextAsText() throws Exception {
        byte[] file = Xlsx.workbook("HoaDon", List.of(List.of("Mã số thuế", "Số lượng"),
                Arrays.asList("0101234567", 2), Arrays.asList(null, 3L)));
        Map<String, String> parts = unzip(file);

        assertThat(parts).containsOnlyKeys("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml", "xl/sharedStrings.xml", "xl/styles.xml");
        for (String xml : parts.values()) {
            DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        }
        // The tax code keeps its leading zero as a shared string; numbers stay numbers; an empty cell is left out.
        assertThat(parts.get("xl/sharedStrings.xml")).contains("<t xml:space=\"preserve\">0101234567</t>")
                .contains("uniqueCount=\"3\"");
        assertThat(parts.get("xl/worksheets/sheet1.xml")).contains("<c r=\"A2\" t=\"s\"><v>2</v></c>")
                .contains("<c r=\"B2\"><v>2</v></c>").contains("<row r=\"3\"><c r=\"B3\"><v>3</v></c></row>");
    }

    private static Map<String, String> unzip(byte[] file) throws Exception {
        Map<String, String> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                parts.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return parts;
    }
}
