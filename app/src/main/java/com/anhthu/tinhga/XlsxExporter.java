package com.anhthu.tinhga;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class XlsxExporter {
    public static final class Record {
        public final String productionDate;
        public final int machineNumber;
        public final String house;
        public final int ageWeeks;
        public final double top, middle, bottom;
        public Record(String productionDate, int machineNumber, String house, int ageWeeks,
                      double top, double middle, double bottom) {
            this.productionDate = productionDate;
            this.machineNumber = machineNumber;
            this.house = house;
            this.ageWeeks = ageWeeks;
            this.top = top;
            this.middle = middle;
            this.bottom = bottom;
        }
        public Record(String productionDate, String house, int ageWeeks,
                      double top, double middle, double bottom) {
            this(productionDate, 1, house, ageWeeks, top, middle, bottom);
        }
        public double average() { return (top + middle + bottom) / 3.0; }
    }
    private XlsxExporter() {}

    public static void write(File target, String incubationDate, String hatchDate,
                             int productionDays, int machineCount, List<Record> records) throws IOException {
        if (records == null || records.isEmpty()) {
            throw new IOException("Chưa có dữ liệu cân trứng để xuất.");
        }
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(target))) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                "</Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>");
            put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets><sheet name=\"Can trung\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                "</Relationships>");
            put(zip, "xl/styles.xml", styles());
            put(zip, "xl/worksheets/sheet1.xml", worksheet(incubationDate, hatchDate, productionDays, machineCount, records));
        }
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><color rgb=\"FF10366A\"/><sz val=\"12\"/><name val=\"Calibri\"/></font></fonts>" +
            "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF0A376F\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"4\">" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFill=\"1\" applyFont=\"1\"/>" +
            "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
            "<xf numFmtId=\"2\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
            "</cellXfs></styleSheet>";
    }

    private static String worksheet(String incubationDate, String hatchDate,
                                    int productionDays, int machineCount, List<Record> records) {
        StringBuilder xml = new StringBuilder(4096 + records.size() * 360);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        xml.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"4\" topLeftCell=\"A5\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        xml.append("<cols>");
        int[] widths = {19,19,19,13,18,15,16,16,16,18};
        for (int i=0; i<widths.length; i++) {
            xml.append("<col min=\"").append(i+1).append("\" max=\"").append(i+1)
                .append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols><sheetData>");
        xml.append("<row r=\"1\" ht=\"27\" customHeight=\"1\">");
        str(xml, "A1", "BÁO CÁO CÂN TRỨNG", 2);
        xml.append("</row>");
        xml.append("<row r=\"2\">");
        str(xml,"A2","Ngày ấp",2);
        str(xml,"B2",incubationDate,0);
        str(xml,"D2","Ngày ra gà (+21 ngày)",2);
        str(xml,"E2",hatchDate,0);
        xml.append("</row><row r=\"3\">");
        str(xml,"A3","Số ngày sản xuất",2);
        num(xml,"B3",productionDays,0);
        str(xml,"D3","Số máy cân",2);
        num(xml,"E3",machineCount,0);
        xml.append("</row>");
        xml.append("<row r=\"4\" ht=\"32\" customHeight=\"1\">");
        String[] heads = {"Ngày ấp","Ngày ra gà","Ngày sản xuất","Máy cân","Nhà","Tuần tuổi",
                "Trên (g)","Giữa (g)","Dưới (g)","TB 3 vị trí (g)"};
        for (int col=0; col<heads.length; col++) {
            str(xml, column(col)+"4", heads[col],1);
        }
        xml.append("</row>");
        for (int i=0; i<records.size(); i++) {
            Record r=records.get(i);
            int line=i+5;
            xml.append("<row r=\"").append(line).append("\">");
            str(xml,"A"+line,incubationDate,0);
            str(xml,"B"+line,hatchDate,0);
            str(xml,"C"+line,r.productionDate,0);
            num(xml,"D"+line,r.machineNumber,0);
            str(xml,"E"+line,r.house,0);
            num(xml,"F"+line,r.ageWeeks,0);
            num(xml,"G"+line,r.top,3);
            num(xml,"H"+line,r.middle,3);
            num(xml,"I"+line,r.bottom,3);
            formula(xml,"J"+line,"(G"+line+"+H"+line+"+I"+line+")/3",r.average(),3);
            xml.append("</row>");
        }
        xml.append("</sheetData><autoFilter ref=\"A4:J").append(records.size()+4).append("\"/>");
        xml.append("</worksheet>");
        return xml.toString();
    }

    private static String column(int i) { return String.valueOf((char)('A'+i)); }
    private static void str(StringBuilder out, String cell, String value, int style) {
        out.append("<c r=\"").append(cell).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
           .append(escape(value)).append("</t></is></c>");
    }
    private static void num(StringBuilder out, String cell, double value, int style) {
        if (!Double.isFinite(value)) value=0;
        out.append("<c r=\"").append(cell).append("\" s=\"").append(style).append("\"><v>")
           .append(String.format(Locale.US,"%.2f",value)).append("</v></c>");
    }
    private static void formula(StringBuilder out, String cell, String expression, double cached, int style) {
        out.append("<c r=\"").append(cell).append("\" s=\"").append(style)
           .append("\"><f>").append(expression).append("</f><v>")
           .append(String.format(Locale.US,"%.2f",cached)).append("</v></c>");
    }
    private static String escape(String input) {
        if (input==null) return "";
        StringBuilder s=new StringBuilder(input.length()+8);
        for (int i=0;i<input.length();i++) {
            char c=input.charAt(i);
            if (c=='&') s.append("&amp;");
            else if (c=='<') s.append("&lt;");
            else if (c=='>') s.append("&gt;");
            else if (c=='"') s.append("&quot;");
            else if (c=='\'') s.append("&apos;");
            else if (c==9 || c==10 || c==13 || c>=32) s.append(c);
        }
        return s.toString();
    }
    private static void put(ZipOutputStream zip, String name, String data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}