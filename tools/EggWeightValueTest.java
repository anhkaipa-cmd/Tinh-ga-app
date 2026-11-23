import com.anhthu.tinhga.EggWeightValue;

public class EggWeightValueTest {
    private static void same(double actual, double expected) {
        if (!Double.isFinite(actual) || Math.abs(actual-expected)>0.0000001) {
            throw new AssertionError("Expected "+expected+", got "+actual);
        }
    }
    private static void reject(String input) {
        if(!Double.isNaN(EggWeightValue.parse(input))) {
            throw new AssertionError("Expected invalid for "+input);
        }
    }
    public static void main(String[] args) {
        same(EggWeightValue.parse("5000"),5000);
        same(EggWeightValue.parse("18000"),18000);
        same(EggWeightValue.parse("11200"),11200);
        same(EggWeightValue.parse("11300"),11300);
        same(EggWeightValue.parse("11400"),11400);
        same(EggWeightValue.parse("11.200"),11200);
        same(EggWeightValue.parse("11,200"),11200);
        same(EggWeightValue.parse("11.300,5"),11300.5);
        same(EggWeightValue.parse("11,300.5"),11300.5);
        same(EggWeightValue.parse("11200,5"),11200.5);
        same((EggWeightValue.parse("11200")+EggWeightValue.parse("11300")+
            EggWeightValue.parse("11400"))/3,11300);
        for(String invalid: new String[]{"4999","18001","0","-3","abc","","61,5","50000"}) {
            reject(invalid);
        }
        String display=EggWeightValue.display(11300);
        if(!display.contains("11") || !display.contains("300"))
            throw new AssertionError("Formatting incorrect: "+display);
        System.out.println("PASS: weights 5000–18000 inclusive, grouped weights, and 11300 average");
    }
}
