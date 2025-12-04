package robocode.trainer;

public class Config {

    public static final String ROBOCODE_HOME = "C:/robocode";

    public static final String ROBOT_CLASS = "a20.DefMode*";

    // caminho do arquivo de pesos que o DefMode/NeatBrain vai ler
    public static final String WEIGHTS_FILE =
            ROBOCODE_HOME + "/robots/a20/DefMode.data/defmode_weights.txt";

    // devem bater com o NeatBrain no robô
    public static final int INPUT_SIZE = 6;
    public static final int HIDDEN_SIZE = 8;
    public static final int OUTPUT_SIZE = 3;


    public static final int HIDDEN_PARAMS = HIDDEN_SIZE * (INPUT_SIZE + 1);
    public static final int OUTPUT_PARAMS = OUTPUT_SIZE * (HIDDEN_SIZE + 1);
    public static final int GENE_COUNT = HIDDEN_PARAMS + OUTPUT_PARAMS;


    public static final int POPULATION_SIZE = 20;
    public static final int GENERATIONS = 50;
    public static final double MUTATION_RATE = 0.1;
    public static final double MUTATION_STRENGTH = 0.3;
    public static final double CROSSOVER_RATE = 0.7;
    public static final int ELITE_COUNT = 2;


    public static final int ROUNDS_PER_BATTLE = 5;
    public static final String ENEMY_ROBOTS = "sample.SittingDuck,sample.RamFire";

}

