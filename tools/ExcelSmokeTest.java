import com.anhthu.tinhga.XlsxExporter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

public class ExcelSmokeTest {
    private static String readXml(File file) throws Exception {
        try(ZipFile z=new ZipFile(file)) {
            if(z.getEntry("[Content_Types].xml")==null
                ||z.getEntry("xl/styles.xml")==null
                ||z.getEntry("xl/worksheets/sheet1.xml")==null)
                throw new AssertionError("Missing XLSX workbook parts");
            String xml=new String(z.getInputStream(z.getEntry("xl/worksheets/sheet1.xml")).readAllBytes(),
                StandardCharsets.UTF_8);
            if(xml.contains("DOC dự kiến")) throw new AssertionError("DOC data leaked into weighing report");
            return xml;
        }
    }
    private static void expect(String xml, String... tokens) {
        for(String token:tokens) if(!xml.contains(token)) throw new AssertionError("Missing "+token);
    }
    private static int count(String haystack,String needle) {
        int count=0, offset=0;
        while((offset=haystack.indexOf(needle,offset))!=-1) {count++;offset+=needle.length();}
        return count;
    }
    public static void main(String[] args) throws Exception {
        File file=File.createTempFile("tinh-ga-egg-",".xlsx");
        try {
            // Two days: machine 3 production 11/11, machine 4 production 15/11, shared house.
            List<XlsxExporter.Record> two=Arrays.asList(
                new XlsxExporter.Record("26/11/2026","17/12/2026","11/11/2026","3","V2",64,61.5,62.3,63.1),
                new XlsxExporter.Record("26/11/2026","17/12/2026","15/11/2026","4","V2",64,60.1,61.2,62.3),
                new XlsxExporter.Record("27/11/2026","18/12/2026","17/11/2026","7","V3",65,61.1,62.0,63.2)
            );
            XlsxExporter.write(file,2,"26/11: 2 | 27/11: 1",two);
            String xml=readXml(file);
            expect(xml,"11/11/2026","15/11/2026","17/11/2026",
                "26/11/2026","27/11/2026","17/12/2026","18/12/2026",
                "26/11: 2 | 27/11: 1","V2","V3","<f>(G5+H5+I5)/3</f>");
            if(count(xml,"<row r=")!=7) throw new AssertionError("Wrong row count: "+count(xml,"<row r="));

            // Partial-day export: two completed machines from eight planned machines.
            // Unfinished machines must not become empty Excel rows.
            List<XlsxExporter.Record> partial=Arrays.asList(
                new XlsxExporter.Record("26/11/2026","17/12/2026",
                    "11/11/2026","3","V2",33,11200,11300,11400),
                new XlsxExporter.Record("26/11/2026","17/12/2026",
                    "15/11/2026","4","V2",33,12000,12100,12200)
            );
            XlsxExporter.write(file,1,"THỨ 5: 2/8",partial);
            xml=readXml(file);
            expect(xml,"THỨ 5: 2/8","11/11/2026","15/11/2026",
                "<v>11300.00</v>","<f>(G6+H6+I6)/3</f>");
            if(count(xml,"<row r=")!=6)
                throw new AssertionError("Partial export should have 4 heading + 2 machine rows");
            
            // Three incubation dates Thursday 8, Friday 8, Saturday 5 weighings.
            List<XlsxExporter.Record> three=new ArrayList<>();
            int[] machineCounts={8,8,5};
            String[] incub={"26/11/2026","27/11/2026","28/11/2026"};
            String[] hatch={"17/12/2026","18/12/2026","19/12/2026"};
            for(int day=0;day<3;day++) {
                for(int machine=0;machine<machineCounts[day];machine++) {
                    String prod=machine%2==0?"11/11/2026":"15/11/2026";
                    three.add(new XlsxExporter.Record(incub[day],hatch[day],prod,
                        "Máy "+(machine+1),machine%2==0?"B1":"B4",40+day,
                        55+day,56+day,57+day));
                }
            }
            XlsxExporter.write(file,3,"26/11: 8 | 27/11: 8 | 28/11: 5",three);
            xml=readXml(file);
            expect(xml,"26/11: 8 | 27/11: 8 | 28/11: 5","28/11/2026",
                "19/12/2026","11/11/2026","15/11/2026","Máy 5","B1","B4",
                "<f>(G25+H25+I25)/3</f>");
            if(count(xml,"<row r=")!=25) throw new AssertionError("Expected 4 header + 21 rows, got "+count(xml,"<row r="));
            System.out.println("PASS: 2/3-day XLSX, per-day machine counts 8/8/5, individual machine production dates, common houses and formulas");
        } finally {file.delete();}
    }
}