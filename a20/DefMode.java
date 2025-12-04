package a20;

import robocode.*;
import robocode.util.Utils;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DefMode extends AdvancedRobot {

    // ======= CÉREBRO (MLP evoluída pelo seu GA) =======
    private NeatBrain brain;

    // Direção de movimento: 1 = frente, -1 = trás
    private int moveDirection = 1;

    // Margem de parede
    private static final double WALL_MARGIN = 40.0;

    // Informações de cada inimigo conhecido
    static class EnemyInfo {
        double x, y;
        double distance;
        double energy;
        double velocity;
        double headingRadians;
        long   lastSeenTime;
    }

    private final Map<String, EnemyInfo> enemies = new HashMap<>();

    // Contadores simples de acerto
    private long totalShots = 0;
    private long totalHits  = 0;

    @Override
    public void run() {
        // ======= CARREGA PESOS DO ARQUIVO (OU RANDOM) =======
        brain = new NeatBrain();
        try {
            File weightsFile = getDataFile("defmode_weights.txt");
            if (weightsFile.exists()) {
                brain.loadFromFile(weightsFile);
                out.println("DefMode: pesos carregados de " + weightsFile.getAbsolutePath());
            } else {
                out.println("DefMode: arquivo de pesos não encontrado, usando pesos aleatórios.");
                brain.initRandom();
            }
        } catch (IOException e) {
            out.println("DefMode: erro ao carregar pesos, usando aleatório: " + e.getMessage());
            brain.initRandom();
        }

        // ======= ESTÉTICA =======
        setBodyColor(Color.black);
        setRadarColor(Color.green);
        setGunColor(Color.black);
        setBulletColor(Color.orange);

        // Arma e radar independentes do corpo
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        // Velocidade máxima
        setMaxVelocity(8);

        // Loop principal: foca em manter o radar girando
        while (true) {
            // Varrer constantemente para manter info dos inimigos atualizada
            setTurnRadarRight(45);
            execute();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        double distance = e.getDistance();
        double enemyEnergy = e.getEnergy();

        // Ângulo absoluto até o inimigo
        double absBearingRad = getHeadingRadians() + e.getBearingRadians();

        // Estima posição (x,y) do inimigo
        double enemyX = getX() + Math.sin(absBearingRad) * distance;
        double enemyY = getY() + Math.cos(absBearingRad) * distance;

        // Atualiza / cria registro do inimigo
        EnemyInfo info = enemies.get(e.getName());
        if (info == null) {
            info = new EnemyInfo();
            enemies.put(e.getName(), info);
        }
        info.x = enemyX;
        info.y = enemyY;
        info.distance = distance;
        info.energy = enemyEnergy;
        info.velocity = e.getVelocity();
        info.headingRadians = e.getHeadingRadians();
        info.lastSeenTime = getTime();

        // ======= PREPARA INPUTS PRO CÉREBRO (6 entradas) =======
        double bfWidth  = getBattleFieldWidth();
        double bfHeight = getBattleFieldHeight();

        // Distância média e mínima dos inimigos conhecidos
        double sumDist = 0.0;
        double minDist = Double.POSITIVE_INFINITY;
        int count = 0;
        for (EnemyInfo ei : enemies.values()) {
            sumDist += ei.distance;
            if (ei.distance < minDist) {
                minDist = ei.distance;
            }
            count++;
        }
        if (count == 0) {
            sumDist = distance;
            minDist = distance;
            count = 1;
        }
        double avgDist = sumDist / count;

        // Normalizações 0..1
        double nearestDistNorm = Math.min(1.0, minDist / 800.0);
        double avgDistNorm     = Math.min(1.0, avgDist / 800.0);
        double myEnergyNorm    = Math.min(1.0, getEnergy() / 100.0);
        double enemyEnergyNorm = Math.min(1.0, enemyEnergy / 100.0);
        double numEnemiesNorm  = Math.min(1.0, getOthers() / 30.0);

        // proximidade de paredes (0 = longe, 1 = colado)
        double minToWall = Math.min(
                Math.min(getX(), bfWidth - getX()),
                Math.min(getY(), bfHeight - getY())
        );
        double wallProximity = 1.0 - Math.min(1.0, minToWall / 200.0);

        double[] inputs = new double[] {
                nearestDistNorm,
                avgDistNorm,
                myEnergyNorm,
                enemyEnergyNorm,
                numEnemiesNorm,
                wallProximity
        };

        double[] outNN = brain.forward(inputs);

        // Saídas da rede (-1..1)
        double outTurn  = outNN[0];  // ajuste fino de rotação
        double outMove  = outNN[1];  // escala de movimento
        double outAggro = outNN[2];  // agressividade do tiro

        // ======= MOVIMENTO: ANTI-GRAVITY + ajuste do cérebro =======
        double moveScale = (outMove + 1.0) / 2.0; // -1..1 -> 0..1
        moveScale = Math.max(0.0, Math.min(1.0, moveScale));
        antiGravityMove(moveScale);

        // Pequeno ajuste extra de rotação baseado na rede
        setTurnRight(getTurnRemaining() + outTurn * 20.0);

        // ======= TIRO: potência + mira linear =======
        double bulletPower;
        if (getEnergy() < 18) {
            bulletPower = 1.0;
        } else {
            if (distance > 500) {
                bulletPower = 1.2;
            } else if (distance > 250) {
                bulletPower = 1.8;
            } else {
                bulletPower = 2.4;
            }
        }

        // agressividade ajustando a potência (0.8x até 1.4x)
        double aggression = (outAggro + 1.0) / 2.0; // 0..1
        bulletPower = bulletPower * (0.8 + 0.6 * aggression);
        bulletPower = Math.max(0.1, Math.min(3.0, bulletPower));

        // Mira linear (predição simples da posição futura)
        double bulletSpeed = 20.0 - 3.0 * bulletPower;

        EnemyInfo ei = enemies.get(e.getName());
        double ex = ei.x;
        double ey = ei.y;
        double eh = ei.headingRadians;
        double ev = ei.velocity;

        double dx = ex - getX();
        double dy = ey - getY();
        double distanceToEnemy = Math.sqrt(dx * dx + dy * dy);
        double time = distanceToEnemy / bulletSpeed;

        double futureX = ex + Math.sin(eh) * ev * time;
        double futureY = ey + Math.cos(eh) * ev * time;

        // Garante que o ponto futuro fica dentro da arena
        futureX = Math.max(18, Math.min(bfWidth - 18, futureX));
        futureY = Math.max(18, Math.min(bfHeight - 18, futureY));

        double theta = Math.atan2(futureX - getX(), futureY - getY());
        double gunTurn = Utils.normalRelativeAngle(theta - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        if (getGunHeat() == 0 && bulletPower > 0.1) {
            setFire(bulletPower);
            totalShots++;
        }

        // Radar: travar no inimigo atual
        double radarTurn = Utils.normalRelativeAngle(
                absBearingRad - getRadarHeadingRadians()
        );
        setTurnRadarRightRadians(radarTurn * 2);
    }

    @Override
    public void onRobotDeath(RobotDeathEvent e) {
        // remove inimigo morto do mapa
        enemies.remove(e.getName());
    }

    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        // tomou tiro → troca direção e faz "swerve"
        moveDirection = -moveDirection;
        setAhead(150 * moveDirection);
        setTurnRight(45 * moveDirection);
    }

    @Override
    public void onHitWall(HitWallEvent e) {
        // bateu na parede → recua e vira pro centro
        moveDirection = -moveDirection;
        setBack(80 * moveDirection);
        setTurnRight(90);
    }

    @Override
    public void onBulletHit(BulletHitEvent event) {
        totalHits++;
    }

    // ======= MOVIMENTO ANTI-GRAVITY (fugir de todos) =======
    private void antiGravityMove(double moveScale) {
        double x = getX();
        double y = getY();
        double bfWidth  = getBattleFieldWidth();
        double bfHeight = getBattleFieldHeight();

        double forceX = 0.0;
        double forceY = 0.0;

        // Constante de repulsão de inimigos
        double K = 50000.0;

        // Repulsão de cada inimigo conhecido
        for (EnemyInfo e : enemies.values()) {
            double dx = x - e.x;
            double dy = y - e.y;

            double dist2 = dx * dx + dy * dy;
            if (dist2 < 1) dist2 = 1;

            double dist = Math.sqrt(dist2);
            double power = K / dist2; // quanto mais perto, mais forte a repulsão

            forceX += (dx / dist) * power;
            forceY += (dy / dist) * power;
        }

        // Repulsão das paredes (4 “fontes” simples)
        double wallK = 80000.0;

        // esquerda
        double dx = x - 0;
        double dist2 = dx * dx;
        if (dist2 < 1) dist2 = 1;
        forceX += (dx / Math.sqrt(dist2)) * (wallK / dist2);

        // direita
        dx = x - bfWidth;
        dist2 = dx * dx;
        if (dist2 < 1) dist2 = 1;
        forceX += (dx / Math.sqrt(dist2)) * (wallK / dist2);

        // baixo
        double dy = y - 0;
        dist2 = dy * dy;
        if (dist2 < 1) dist2 = 1;
        forceY += (dy / Math.sqrt(dist2)) * (wallK / dist2);

        // topo
        dy = y - bfHeight;
        dist2 = dy * dy;
        if (dist2 < 1) dist2 = 1;
        forceY += (dy / Math.sqrt(dist2)) * (wallK / dist2);

        // Ponto "seguro" = posição atual + vetor de força
        double targetX = x + forceX;
        double targetY = y + forceY;

        // Converte em ângulo pra onde ir
        double angleToTarget = Math.toDegrees(Math.atan2(targetX - x, targetY - y));
        double turn = Utils.normalRelativeAngleDegrees(angleToTarget - getHeading());
        setTurnRight(turn);

        // Movimento baseado na escala da rede
        moveScale = Math.max(0.0, Math.min(1.0, moveScale));
        double baseMove = 120.0;
        double extra    = 80.0 * moveScale;
        double move     = baseMove + extra;

        setAhead(move * moveDirection);

        // random flip pra não ficar totalmente previsível
        if (Math.random() < 0.02) {
            moveDirection = -moveDirection;
            setAhead(move * moveDirection);
        }
    }
}
