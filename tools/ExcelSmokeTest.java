import com.anhthu.tinhga.XlsxExporter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

public class ExcelSmokeTest {
    private static void verify(File file,String... tokens) throws Exception {
        try(ZipFile z=new ZipFile(file)) {
            if(z.getEntry("[Content_Types].xml")==null
                ||z.getEntry("xl/styles.xml")==null
                ||z.getEntry("xl/worksheets/sheet1.xml")==null) throw new AssertionError("Missing workbook parts");
            String xml=new String(z.getInputStream(z.getEntry("xl/worksheets/sheet1.xml")).readAllBytes(),
                StandardCharsets.UTF_8);
            for(String token:tokens) if(!xml.contains(token)) throw new AssertionError("Missing "+token);
            if(xml.contains("DOC dự kiến")) throw new AssertionError("DOC data leaked into weighing export");
        }
    }
    public static void main(String[] args) throws Exception {
        File file=File.createTempFile("tinh-ga-egg-",".xlsx");
        try {
            List<XlsxExporter.Record> two=Arrays.asList(
                new XlsxExporter.Record("26/11/2026","17/12/2026","24/11/2026","M08","V2",64,61.5,62.3,63.1),
                new XlsxExporter.Record("26/11/2026","17/12/2026","24/11/2026","M11","V2",64,60.1,61.2,62.3),
                new XlsxExporter.Record("27/11/2026","18/12/2026","25/11/2026","M08","V3",65,61.1,62.0,63.2),
                new XlsxExporter.Record("27/11/2026","18/12/2026","25/11/2026","M11","V3",65,60.0,61.5,63.0)
            );
            XlsxExporter.write(file,2,2,two);
            verify(file,"26/11/2026","27/11/2026","17/12/2026","18/12/2026",
                "24/11/2026","25/11/2026","M08","M11","V2","V3",
                "<f>(G5+H5+I5)/3</f>","Máy cân");
            List<XlsxExporter.Record> three=Arrays.asList(
                new XlsxExporter.Record("26/11/2026","17/12/2026","24/11/2026","02","B1",40,51,52,53),
                new XlsxExporter.Record("27/11/2026","18/12/2026","25/11/2026","09","B1",40,50,52,54),
                new XlsxExporter.Record("28/11/2026","19/12/2026","26/11/2026","12","B5",41,54,53,52)
            );
            XlsxExporter.write(file,3,1,three);
            verify(file,"26/11/2026","27/11/2026","28/11/2026",
                "17/12/2026","18/12/2026","19/12/2026","02","09","12",
                "B1","B5","<f>(G7+H7+I7)/3</f>");
            System.out.println("PASS: independent Thursday Friday Saturday incubations, separate +21 hatch dates, 2/3-day export, manual machines and shared houses");
        } finally { file.delete(); }
    }
}