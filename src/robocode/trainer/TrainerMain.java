package robocode.trainer;

import java.util.Arrays;

public class TrainerMain {

    public static void main(String[] args) {
        Population population = new Population();
        RobocodeFitnessEvaluator evaluator = new RobocodeFitnessEvaluator();

        try {
            for (int gen = 1; gen <= Config.GENERATIONS; gen++) {
                System.out.println("===== Geração " + gen + " =====");


                for (Genome g : population.getGenomes()) {
                    double fitness = evaluator.evaluate(g);
                    g.fitness = fitness;
                    System.out.println("  Fitness: " + fitness);
                }


                Arrays.sort(population.getGenomes());
                Genome best = population.getGenomes()[0];

                System.out.println(">> Melhor da geração " + gen +
                        " | Fitness = " + best.fitness);


                population.evolve();
            }


            Arrays.sort(population.getGenomes());
            Genome bestOverall = population.getGenomes()[0];
            System.out.println("=== Treino finalizado. Melhor fitness: " + bestOverall.fitness);

            RobocodeFitnessEvaluator finalEval = new RobocodeFitnessEvaluator();
            finalEval.evaluate(bestOverall);
            finalEval.close();

            System.out.println("Pesos do melhor genoma gravados em: " + Config.WEIGHTS_FILE);

        } finally {
            evaluator.close();
        }
    }
}