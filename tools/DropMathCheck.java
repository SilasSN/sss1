import com.nom1s.percentboosts.logic.DropMath;
import java.util.Random;
public final class DropMathCheck {
    public static void main(String[] args) {
        if (DropMath.strength(20, 4, 2, true) != 53) throw new AssertionError("strength formula");
        if (DropMath.strength(20, 4, 2, false) != 41) throw new AssertionError("friendly formula");
        Random random = new Random(0xCAFE);
        int drops = 0;
        for (int i = 0; i < 100_000; i++) {
            if (DropMath.shouldDrop(random)) drops++;
            int percent = DropMath.rollPercent(i % 150, random);
            if (percent < 1 || percent > 100) throw new AssertionError("percent out of range " + percent);
        }
        if (drops < 23000 || drops > 27000) throw new AssertionError("drop probability: " + drops);
        System.out.println("PASS: strength formula, percent bounds and ~25% drop distribution (" + drops + "/100000)");
    }
}
