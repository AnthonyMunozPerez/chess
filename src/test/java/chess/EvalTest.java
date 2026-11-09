package chess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalTest {
    @TempDir Path output;

    @Test
    void roundTripsAllEvaluationWeights() throws Exception {
        Eval expected = Eval.standard();
        Path model = output.resolve("weights.properties");

        expected.save(model);

        assertEquals(expected, Eval.load(model));
    }

    @Test
    void mutationChangesOneNamedWeightWithinBounds() {
        Eval original = Eval.standard();
        Eval.Mutation mutation = original.mutate(new Random(7));

        assertNotEquals(original, mutation.weights());
        assertTrue(Eval.NAMES.contains(mutation.field()));
        assertNotEquals(mutation.oldValue(), mutation.newValue());
    }
}
