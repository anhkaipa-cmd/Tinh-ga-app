import com.anhthu.tinhga.XlsxExporter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

public class ExcelSmokeTest {
    private static void check(File file, String... expected) throws Exception {
        try(ZipFile zip=new ZipFile(file)) {
            if(zip.getEntry("[Content_Types].xml")==null
                ||zip.getEntry("xl/styles.xml")==null
                ||zip.getEntry("xl/worksheets/sheet1.xml")==null)
                throw new Exception("Workbook entries missing");
            ZipEntry sheet=zip.getEntry("xl/worksheets/sheet1.xml");
            String xml=new String(zip.getInputStream(sheet).readAllBytes(), StandardCharsets.UTF_8);
            for(String value:expected) {
                if(!xml.contains(value)) throw new Exception("Missing Excel data: "+value);
            }
            if(xml.contains("DOC dự kiến")) throw new Exception("DOC page accidentally included");
        }
    }
    public static void main(String[] args) throws Exception {
        File file=File.createTempFile("tinh-ga-can-trung-", ".xlsx");
        try {
            // Two production dates, two weighing machines, same house V2 in both machines.
            List<XlsxExporter.Record> twoDays=Arrays.asList(
                new XlsxExporter.Record("20/11/2026",1,"V2",64,61.5,62.3,63.1),
                new XlsxExporter.Record("20/11/2026",2,"V2",64,62.5,61.3,62.1),
                new XlsxExporter.Record("21/11/2026",1,"V3",65,60.3,59.4,61.1),
                new XlsxExporter.Record("21/11/2026",2,"V3",65,61.1,62.2,63.3)
            );
            XlsxExporter.write(file,"24/11/2026","15/12/2026",2,2,twoDays);
            check(file,"20/11/2026","21/11/2026","15/12/2026","V2","V3",
                "<f>(G5+H5+I5)/3</f>","<v>2.00</v>", "Máy cân");
            if(twoDays.get(0).average()!=62.300000000000004
                && Math.abs(twoDays.get(0).average()-62.3)>0.00001)
                throw new Exception("Average incorrect");

            // Third date can be included without requiring extra distinct house names.
            List<XlsxExporter.Record> threeDays=Arrays.asList(
                new XlsxExporter.Record("20/11/2026",1,"B1",40,51,52,53),
                new XlsxExporter.Record("21/11/2026",1,"B1",40,50,52,54),
                new XlsxExporter.Record("22/11/2026",1,"B1",40,54,53,52)
            );
            XlsxExporter.write(file,"24/11/2026","15/12/2026",3,1,threeDays);
            check(file,"20/11/2026","21/11/2026","22/11/2026","B1",
                "<f>(G7+H7+I7)/3</f>", "Ngày sản xuất");
            System.out.println("OK: XLSX two/three dates, same house across machines, age weeks, 3 weights and mean formula");
        } finally {
            file.delete();
        }
    }
}