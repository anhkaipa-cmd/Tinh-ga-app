import com.anhthu.tinhga.XlsxExporter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

public class ExcelSmokeTest {
    public static void main(String[] args) throws Exception {
        File file=File.createTempFile("tinh-ga-can-trung-", ".xlsx");
        XlsxExporter.write(file,"24/11/2026","15/12/2026", Arrays.asList(
            new XlsxExporter.Record("20/11/2026","V2",64,61.5,62.3,63.1),
            new XlsxExporter.Record("21/11/2026","V3",65,60.3,59.4,61.1),
            new XlsxExporter.Record("22/11/2026","D1",45,55.0,56.5,54.5)
        ));
        try(ZipFile zip=new ZipFile(file)) {
            if(zip.getEntry("[Content_Types].xml")==null||zip.getEntry("xl/styles.xml")==null
              ||zip.getEntry("xl/worksheets/sheet1.xml")==null) throw new Exception("Workbook entries missing");
            ZipEntry sheet=zip.getEntry("xl/worksheets/sheet1.xml");
            String xml=new String(zip.getInputStream(sheet).readAllBytes(), StandardCharsets.UTF_8);
            for(String expected:new String[]{"20/11/2026","21/11/2026","22/11/2026",
                    "15/12/2026","V2","V3","D1","<f>(F5+G5+H5)/3</f>"}) {
                if(!xml.contains(expected)) throw new Exception("Missing Excel data: "+expected);
            }
            if(xml.contains("DOC dự kiến")) throw new Exception("DOC page accidentally included");
        }
        System.out.println("Excel XLSX ZIP, all 3 dates, weekly ages, three positions and average formula OK");
        file.delete();
    }
}