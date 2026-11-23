import com.anhthu.tinhga.EggWeightValue;

public class EggWeightValueTest {
    private static void same(double actual, double expected) {
        if (!Double.isFinite(actual) || Math.abs(actual-expected)>0.0000001) {
            throw new AssertionError("Expected "+expected+", got "+actual);
        }
    }
    public static void main(String[] args) {
        same(EggWeightValue.parse("11200"),11200);
        same(EggWeightValue.parse("11300"),11300);
        same(EggWeightValue.parse("11400"),11400);
        same(EggWeightValue.parse("61,5"),61.5);
        same(EggWeightValue.parse("61.5"),61.5);
        same((EggWeightValue.parse("11200")+EggWeightValue.parse("11300")
                +EggWeightValue.parse("11400"))/3,11300);
        if(!Double.isNaN(EggWeightValue.parse("0"))) throw new AssertionError("Zero must be rejected");
        if(!Double.isNaN(EggWeightValue.parse("-3"))) throw new AssertionError("Negative must be rejected");
        if(!Double.isNaN(EggWeightValue.parse("abc"))) throw new AssertionError("Invalid input must be rejected");
        String display=EggWeightValue.display(11300);
        if(!display.contains("11") || !display.contains("300")) throw new AssertionError("Formatting incorrect");
        System.out.println("PASS egg weights 11200, 11300, 11400 and mean 11300, decimal weights");
    }
}
