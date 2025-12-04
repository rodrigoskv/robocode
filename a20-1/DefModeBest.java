package a20;

import robocode.*;
import robocode.util.Utils;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DefModeBest extends AdvancedRobot {

    private NeatBrain brain;

    private int moveDirection = 1;


    private static final double LOW_ENERGY_THRESHOLD  = 40.0; // abaixo disso ele evita atirar
    private static final double VERY_LOW_ENERGY       = 25.0; // abaixo disso é quase só fuga
    private static final double SAFE_SHOT_DISTANCE    = 220.0; // só atira perto em early/mid

    static class EnemyInfo {
        double x, y;
        double distance;
        double energy;
        double velocity;
        double headingRadians;
        long   lastSeenTime;
    }

    private final Map<String, ScannedRobotEvent> enemies = new HashMap<>();

    private long totalShots = 0;
    private long totalHits  = 0;

    @Override
    public void run() {
        brain = new NeatBrain();
        try {
            File weightsFile = getDataFile("Best.txt");
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

        setBodyColor(Color.black);
        setRadarColor(Color.black);
        setGunColor(Color.black);
        setBulletColor(Color.black);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setMaxVelocity(8);

        while (true) {
            setTurnRadarRight(45);
            execute();
        }
    }

        
    @Override
    public void onScannedRobot(ScannedRobotEvent ei) {
        
            enemies.put(ei.getName(), ei);
        

        for(ScannedRobotEvent e: enemies.values()) {
            if(e.getEnergy() < ei.getEnergy()) {
                ei = e;
            }
        }
        
        double distance    = ei.getDistance();
        double enemyEnergy = ei.getEnergy();
        
        int others = getOthers();
        int gamePhase;
        if (others >= 10) {
            gamePhase = 0; // early-game
        } else if (others >= 4) {
            gamePhase = 1; // mid-game
        } else {
            gamePhase = 2; // late-game
        }

        double absBearingRad = getHeadingRadians() + ei.getBearingRadians();

        double enemyX = getX() + Math.sin(absBearingRad) * distance;
        double enemyY = getY() + Math.cos(absBearingRad) * distance;

        double bfWidth  = getBattleFieldWidth();
        double bfHeight = getBattleFieldHeight();

        double sumDist = 0.0;
        double minDist = Double.POSITIVE_INFINITY;
        int count = 0;
        for (ScannedRobotEvent e2 : enemies.values()) {
            sumDist += e2.getDistance();
            if (e2.getDistance() < minDist) {
                minDist = e2.getDistance();
            }
            count++;
        }
        if (count == 0) {
            sumDist = distance;
            minDist = distance;
            count = 1;
        }
        double avgDist = sumDist / count;

        double nearestDistNorm = Math.min(1.0, minDist / 800.0);
        double avgDistNorm     = Math.min(1.0, avgDist / 800.0);
        double myEnergyNorm    = Math.min(1.0, getEnergy() / 100.0);
        double enemyEnergyNorm = Math.min(1.0, enemyEnergy / 100.0);
        double numEnemiesNorm  = Math.min(1.0, getOthers() / 30.0);

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

        double outTurn  = outNN[0];
        double outMove  = outNN[1];
        double outAggro = outNN[2];

        double moveScale = (outMove + 1.0) / 2.0;
        moveScale = Math.max(0.0, Math.min(1.0, moveScale));
        antiGravityMove(moveScale);

        setTurnRight(getTurnRemaining() + outTurn * 20.0);

        double aggression = (outAggro + 1.0) / 2.0;
        double bulletPower;

        double myEnergy      = getEnergy();
        boolean lowEnergy    = myEnergy < LOW_ENERGY_THRESHOLD;
        boolean veryLowEnergy= myEnergy < VERY_LOW_ENERGY;
        
        if (gamePhase == 0) {
            boolean enemyVeryClose = distance < SAFE_SHOT_DISTANCE;
            boolean imVeryHealthy  = myEnergy > 75;
            boolean energyAdvantage= myEnergy > enemyEnergy + 10;

            if (enemyVeryClose && imVeryHealthy && energyAdvantage) {
                bulletPower = 0.7 + 0.5 * aggression; 
            } else {
                bulletPower = 0.0;
            }
        } else if (gamePhase == 1) {
            if (lowEnergy) {
                boolean close = distance < SAFE_SHOT_DISTANCE;
                boolean slightAdv = myEnergy > enemyEnergy;
                if (close && slightAdv) {
                    bulletPower = 0.6 + 0.6 * aggression; 
                } else {
                    bulletPower = 0.0;
                }
            } else {
              
                if (distance > 500) {
                    bulletPower = 1.0;
                } else if (distance > 250) {
                    bulletPower = 1.5;
                }else if (distance > 50) {
                    bulletPower = 2; }
				 else {
                    bulletPower = 4;
                }
                bulletPower = bulletPower * (0.9 + 0.3 * aggression);
            }
        } else {
            if (veryLowEnergy && enemyEnergy > myEnergy) {
                bulletPower = 0.0;
            } else if (enemyEnergy < 20) {
                bulletPower = Math.min(2.6, Math.max(1.2, myEnergy / 5.0));
            } else if (distance < 300) {
                bulletPower = 2.0;
            } else {
                bulletPower = 1.5;
            }
            bulletPower = bulletPower * (0.95 + 0.25 * aggression);
        }
        

        if (bulletPower < 0.35) {
            bulletPower = 0.0;
        }
        bulletPower = Math.min(3.0, bulletPower);

        double bulletSpeed = (bulletPower > 0.0) ? 20.0 - 3.0 * bulletPower : 0.0;
        
        double ex = getX() + Math.sin(absBearingRad) * ei.getDistance();
        double ey = getY() + Math.cos(absBearingRad) * ei.getDistance();
        double eh = ei.getHeadingRadians();
        double ev = ei.getVelocity();

        double dx = ex - getX();
        double dy = ey - getY();
        double distanceToEnemy = Math.sqrt(dx * dx + dy * dy);

        double time = (bulletPower > 0.0 && bulletSpeed > 0.0)
                ? distanceToEnemy / bulletSpeed : 0.0;

        double futureX = ex + Math.sin(eh) * ev * time;
        double futureY = ey + Math.cos(eh) * ev * time;

        futureX = Math.max(18, Math.min(bfWidth - 18, futureX));
        futureY = Math.max(18, Math.min(bfHeight - 18, futureY));

        double theta = Math.atan2(futureX - getX(), futureY - getY());
        double gunTurn = Utils.normalRelativeAngle(theta - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);


        boolean permittedEnergyUse =
                bulletPower > 0.0 &&
                getGunHeat() == 0 &&
                (
                    gamePhase == 2 ||                  // late game pode atirar se passou filtros acima
                    (!lowEnergy && gamePhase == 1) || // mid game só se não estiver fraco
                    (gamePhase == 0 && myEnergy > 80) // early game só se MUITO saudável
                );

        if (permittedEnergyUse) {
            setFire(bulletPower);
            totalShots++;
        }

        double radarTurn = Utils.normalRelativeAngle(
                absBearingRad - getRadarHeadingRadians()
        );
        setTurnRadarRightRadians(radarTurn * 2);
    }

    @Override
    public void onRobotDeath(RobotDeathEvent e) {
        enemies.remove(e.getName());
    }

    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        moveDirection = -moveDirection;
        setAhead(150 * moveDirection);
        setTurnRight(45 * moveDirection);
    }

    @Override
    public void onHitWall(HitWallEvent e) {
        moveDirection = -moveDirection;
        setBack(80 * moveDirection);
        setTurnRight(90);
    }

    @Override
    public void onBulletHit(BulletHitEvent event) {
        totalHits++;
    }

    private void antiGravityMove(double moveScale) {
        double x = getX();
        double y = getY();
        double bfWidth  = getBattleFieldWidth();
        double bfHeight = getBattleFieldHeight();

        double forceX = 0.0;
        double forceY = 0.0;

        // se estiver com pouca energia, aumenta ainda mais a força de repulsão (foge mais)
        double energyFactor = (getEnergy() < LOW_ENERGY_THRESHOLD) ? 1.8 : 1.0;

        double K = 50000.0 * energyFactor;

        for (ScannedRobotEvent e : enemies.values()) {
            double enemyX = getX() + Math.sin(e.getBearingRadians()) * e.getDistance();
            double enemyY = getY() + Math.cos(e.getBearingRadians()) * e.getDistance();

            double dx = x - enemyX;
            double dy = y - enemyY;

            double dist2 = dx * dx + dy * dy;
            if (dist2 < 1) dist2 = 1;

            double dist = Math.sqrt(dist2);
            double power = K / dist2;

            forceX += (dx / dist) * power;
            forceY += (dy / dist) * power;
        }

        double wallK = 80000.0;

        double dx = x - 0;
        double dist2 = dx * dx;
        if (dist2 < 1) dist2 = 1;
        forceX += (dx / Math.sqrt(dist2)) * (wallK / dist2);

        dx = x - bfWidth;
        dist2 = dx * dx;
        if (dist2 < 1) dist2 = 1;
        forceX += (dx / Math.sqrt(dist2)) * (wallK / dist2);

        double dy = y - 0;
        dist2 = dy * dy;
        if (dist2 < 1) dist2 = 1;
        forceY += (dy / Math.sqrt(dist2)) * (wallK / dist2);

        dy = y - bfHeight;
        dist2 = dy * dy;
        if (dist2 < 1) dist2 = 1;
        forceY += (dy / Math.sqrt(dist2)) * (wallK / dist2);

        double targetX = x + forceX;
        double targetY = y + forceY;

        double angleToTarget = Math.toDegrees(Math.atan2(targetX - x, targetY - y));
        double turn = Utils.normalRelativeAngleDegrees(angleToTarget - getHeading());
        setTurnRight(turn);

        moveScale = Math.max(0.0, Math.min(1.0, moveScale));
        double baseMove = 120.0;
        double extra    = 80.0 * moveScale;
        double move     = baseMove + extra;

        setAhead(move * moveDirection);

        if (Math.random() < 0.02) {
            moveDirection = -moveDirection;
            setAhead(move * moveDirection);
        }
    }
}
