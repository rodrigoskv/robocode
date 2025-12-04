package robocode.trainer;

import java.util.Arrays;
import java.util.Random;

public class Population {

    private Genome[] genomes;
    private static final Random RAND = new Random();

    public Population() {
        this.genomes = new Genome[Config.POPULATION_SIZE];
        for (int i = 0; i < genomes.length; i++) {
            genomes[i] = Genome.randomGenome();
        }
    }

    public Genome[] getGenomes() {
        return genomes;
    }

    public void evolve() {
        Arrays.sort(genomes);

        Genome[] newGen = new Genome[genomes.length];

        // elitismo
        for (int i = 0; i < Config.ELITE_COUNT; i++) {
            newGen[i] = genomes[i].copy();
        }

        // resto: gera por crossover + mutação
        for (int i = Config.ELITE_COUNT; i < genomes.length; i++) {
            Genome parent1 = selectParent();
            Genome parent2 = selectParent();
            Genome child = crossover(parent1, parent2);
            mutate(child);
            newGen[i] = child;
        }

        genomes = newGen;
    }

    private Genome selectParent() {
        Genome best = null;
        for (int i = 0; i < 3; i++) {
            Genome g = genomes[RAND.nextInt(genomes.length)];
            if (best == null || g.fitness > best.fitness) {
                best = g;
            }
        }
        return best;
    }

    private Genome crossover(Genome a, Genome b) {
        Genome child = new Genome();
        for (int i = 0; i < child.genes.length; i++) {
            if (RAND.nextDouble() < Config.CROSSOVER_RATE) {
                child.genes[i] = a.genes[i];
            } else {
                child.genes[i] = b.genes[i];
            }
        }
        return child;
    }

    private void mutate(Genome g) {
        for (int i = 0; i < g.genes.length; i++) {
            if (RAND.nextDouble() < Config.MUTATION_RATE) {
                double delta = (RAND.nextDouble() * 2.0 - 1.0) * Config.MUTATION_STRENGTH;
                g.genes[i] += delta;
            }
        }
    }
}
