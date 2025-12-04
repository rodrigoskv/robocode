package robocode.trainer;

import java.util.Random;

public class Genome implements Comparable<Genome> {

    public double[] genes;
    public double fitness;

    private static final Random RAND = new Random();

    public Genome() {
        this.genes = new double[Config.GENE_COUNT];
    }

    public static Genome randomGenome() {
        Genome g = new Genome();
        for (int i = 0; i < g.genes.length; i++) {
            g.genes[i] = (RAND.nextDouble() * 2.0 - 1.0) * 0.5; // -0.5..0.5
        }
        return g;
    }

    @Override
    public int compareTo(Genome o) {
        // ordenação descendente por fitness
        return Double.compare(o.fitness, this.fitness);
    }

    public Genome copy() {
        Genome g = new Genome();
        System.arraycopy(this.genes, 0, g.genes, 0, this.genes.length);
        g.fitness = this.fitness;
        return g;
    }
}
