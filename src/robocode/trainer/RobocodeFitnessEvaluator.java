package robocode.trainer;

import robocode.BattleResults;
import robocode.control.*;
import robocode.control.events.BattleAdaptor;
import robocode.control.events.BattleCompletedEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


public class RobocodeFitnessEvaluator {

    private final RobocodeEngine engine;

    public RobocodeFitnessEvaluator() {
        engine = new RobocodeEngine(new File(Config.ROBOCODE_HOME));
        engine.setVisible(false); // sem GUI
    }

    public void close() {
        engine.close();
    }

    public double evaluate(Genome genome) {
        try {
            writeGenomeToWeightsFile(genome, Config.WEIGHTS_FILE);
        } catch (IOException e) {
            e.printStackTrace();
            return -1_000_000;
        }

        BattlefieldSpecification battlefield =
                new BattlefieldSpecification(800, 600);

        String robotsSpec = Config.ROBOT_CLASS + "," + Config.ENEMY_ROBOTS;
        RobotSpecification[] robots = engine.getLocalRepository(robotsSpec);

        BattleSpecification battleSpec =
                new BattleSpecification(Config.ROUNDS_PER_BATTLE,
                        battlefield, robots);

        FitnessBattleObserver observer = new FitnessBattleObserver(Config.ROBOT_CLASS);
        engine.addBattleListener(observer);

        engine.runBattle(battleSpec, true); // true = espera terminar

        engine.removeBattleListener(observer);

        return observer.getFitness();
    }


    private void writeGenomeToWeightsFile(Genome genome, String path) throws IOException {
        try (FileWriter fw = new FileWriter(path)) {
            int idx = 0;

            // Hidden layer: HIDDEN_SIZE linhas, cada uma:
            // INPUT_SIZE pesos + 1 bias
            for (int h = 0; h < Config.HIDDEN_SIZE; h++) {
                for (int i = 0; i < Config.INPUT_SIZE; i++) {
                    fw.write(genome.genes[idx++] + " ");
                }
                // bias
                fw.write(genome.genes[idx++] + "\n");
            }


            for (int o = 0; o < Config.OUTPUT_SIZE; o++) {
                for (int h = 0; h < Config.HIDDEN_SIZE; h++) {
                    fw.write(genome.genes[idx++] + " ");
                }
                // bias
                fw.write(genome.genes[idx++] + "\n");
            }
        }
    }


    private static class FitnessBattleObserver extends BattleAdaptor {
        private final String robotClassName;
        private double fitness = -1_000_000;

        public FitnessBattleObserver(String robotClassName) {
            this.robotClassName = robotClassName;
        }

        @Override
        public void onBattleCompleted(BattleCompletedEvent event) {
            BattleResults[] results = event.getIndexedResults();


            int totalRounds = event.getBattleRules().getNumRounds();

            for (BattleResults r : results) {
                if (r.getTeamLeaderName().contains(robotClassName)) {

                    double score     = r.getScore();
                    double survival  = r.getSurvival();
                    double bulletDmg = r.getBulletDamage();
                    double ramDmg    = r.getRamDamage();


                    double roundsSurvivedApprox = survival / 50.0;
                    double deaths = Math.max(0.0, totalRounds - roundsSurvivedApprox);

                    double dmgTakenProxy = deaths * 100.0;


                    fitness = score
                            + 1.5 * survival
                            + 0.5 * bulletDmg
                            + 0.3 * ramDmg
                            - 2.0 * dmgTakenProxy;
                }
            }
        }

        public double getFitness() {
            return fitness;
        }
    }
}
