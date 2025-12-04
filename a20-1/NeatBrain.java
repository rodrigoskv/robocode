package a20;

import java.io.*;
import java.util.Arrays;

public class NeatBrain {

    private static final int INPUT_SIZE = 6;
    private static final int HIDDEN_SIZE = 8;
    private static final int OUTPUT_SIZE = 3;

    private final double[][] wInputHidden = new double[HIDDEN_SIZE][INPUT_SIZE];
    private final double[]   bHidden      = new double[HIDDEN_SIZE];

    private final double[][] wHiddenOutput = new double[OUTPUT_SIZE][HIDDEN_SIZE];
    private final double[]   bOutput       = new double[OUTPUT_SIZE];


    public void loadFromFile(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;


            for (int h = 0; h < HIDDEN_SIZE; h++) {
                line = br.readLine();
                if (line == null) throw new IOException("Arquivo de pesos incompleto (hidden).");
                String[] parts = line.trim().split("\\s+");
                if (parts.length != INPUT_SIZE + 1) {
                    throw new IOException("Linha hidden com tamanho errado: " + parts.length);
                }
                for (int i = 0; i < INPUT_SIZE; i++) {
                    wInputHidden[h][i] = Double.parseDouble(parts[i]);
                }
                bHidden[h] = Double.parseDouble(parts[INPUT_SIZE]);
            }


            for (int o = 0; o < OUTPUT_SIZE; o++) {
                line = br.readLine();
                if (line == null) throw new IOException("Arquivo de pesos incompleto (output).");
                String[] parts = line.trim().split("\\s+");
                if (parts.length != HIDDEN_SIZE + 1) {
                    throw new IOException("Linha output com tamanho errado: " + parts.length);
                }
                for (int h = 0; h < HIDDEN_SIZE; h++) {
                    wHiddenOutput[o][h] = Double.parseDouble(parts[h]);
                }
                bOutput[o] = Double.parseDouble(parts[HIDDEN_SIZE]);
            }
        }
    }


    public void initRandom() {
        java.util.Random rand = new java.util.Random();
        for (int h = 0; h < HIDDEN_SIZE; h++) {
            for (int i = 0; i < INPUT_SIZE; i++) {
                wInputHidden[h][i] = (rand.nextDouble() * 2 - 1) * 0.5;
            }
            bHidden[h] = (rand.nextDouble() * 2 - 1) * 0.5;
        }
        for (int o = 0; o < OUTPUT_SIZE; o++) {
            for (int h = 0; h < HIDDEN_SIZE; h++) {
                wHiddenOutput[o][h] = (rand.nextDouble() * 2 - 1) * 0.5;
            }
            bOutput[o] = (rand.nextDouble() * 2 - 1) * 0.5;
        }
    }

    // Propagação para frente: recebe 6 inputs, devolve 3 outputs (-1..1)
    public double[] forward(double[] input) {
        if (input.length != INPUT_SIZE) {
            throw new IllegalArgumentException("Esperava " + INPUT_SIZE + " entradas.");
        }

        double[] hidden = new double[HIDDEN_SIZE];
        for (int h = 0; h < HIDDEN_SIZE; h++) {
            double sum = bHidden[h];
            for (int i = 0; i < INPUT_SIZE; i++) {
                sum += wInputHidden[h][i] * input[i];
            }
            hidden[h] = Math.tanh(sum);
        }

        double[] output = new double[OUTPUT_SIZE];
        for (int o = 0; o < OUTPUT_SIZE; o++) {
            double sum = bOutput[o];
            for (int h = 0; h < HIDDEN_SIZE; h++) {
                sum += wHiddenOutput[o][h] * hidden[h];
            }
            output[o] = Math.tanh(sum);
        }

        return output;
    }
}

