package dev.atikdd.marksman;

import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ThreadLocalRandom;

public class MarksmanController {

    private static class Point {
        double x;
        double y;
        boolean up;
        Line line;
        Point(Line line) {
            this.up = ThreadLocalRandom.current().nextBoolean();
            this.line = line;
            this.x = line.getStartX();
            this.y = ThreadLocalRandom.current().nextDouble(line.getStartY(), line.getEndY()
            );
        }
    }

    @FXML AnchorPane mainPanel;
    @FXML Circle target1;
    @FXML Circle target2;
    @FXML Line line1;
    @FXML Line line2;
    @FXML Line arrow;
    @FXML Label score;
    @FXML Label shots;

    private static final double arrow_x = 39;
    private static final double arrow_y = 177;
    private static final double arrow_length = 30;
    private static final double target1_radius = 27;
    private static final double target2_radius = 16;

    volatile private boolean isRun;
    volatile private boolean isPause;
    volatile private boolean arrowActive = false;
    private final AtomicReference<Double> arrowX = new AtomicReference<>(arrow_x);
    private final AtomicInteger scoreValue = new AtomicInteger(0);
    private final AtomicInteger shotsValue = new AtomicInteger(0);

    private Thread thread;
    private Thread arrowThread;
    private AtomicReference<Point> target1_p;
    private AtomicReference<Point> target2_p;

    @FXML
    public void initialize() {
        target1_p = new AtomicReference<>(new Point(line1));
        target2_p = new AtomicReference<>(new Point(line2));
        AnimationTimer render = new AnimationTimer() {
            @Override
            public void handle(long now) {
                Point p1 = target1_p.get();
                target1.setLayoutX(p1.x);
                target1.setLayoutY(p1.y);

                Point p2 = target2_p.get();
                target2.setLayoutX(p2.x);
                target2.setLayoutY(p2.y);

                if (arrowActive) {
                    arrow.setVisible(true);
                    arrow.setStartX(arrowX.get());
                    arrow.setEndX(arrowX.get() + arrow_length);
                } else {
                    arrow.setVisible(false);
                }

                score.setText(String.valueOf(scoreValue.get()));
                shots.setText(String.valueOf(shotsValue.get()));
            }
        };
        render.start();
    }

    private void move(AtomicReference<Point> target2P) {
        target2P.getAndUpdate(point_t ->
        {
            double ty = point_t.y;
            if (point_t.up) ty += 1;
            else ty-=1;
            if (ty < point_t.line.getStartY() || ty > point_t.line.getEndY()) point_t.up = !point_t.up;
            point_t.y = ty;
            return point_t;
        });
    }


    @FXML
    void tStart() {
        if (thread != null) return;
        scoreValue.set(0);
        shotsValue.set(0);
        thread = new Thread(() -> {
            isRun = true;
            isPause = false;
            while (isRun) {
                move(target1_p);
                move(target2_p);
                synchronized (thread)
                {
                    if (isPause)
                    {
                        try {
                            thread.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                        isPause = false;
                    }
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        thread.start();
    }

    @FXML
    void tStop() {
        if (!isRun) return;
        isRun = false;
        thread.interrupt();
        thread = null;
        if (arrowThread != null) {
            arrowActive = false;
            arrowThread.interrupt();
            arrowThread = null;
        }
    }

    @FXML
    void tPause() {
        isPause = true;
    }

    @FXML
    void tContinue(){
        if (thread == null) return;
        synchronized (thread){
            thread.notifyAll();
        }
    }

    @FXML
    void tShoot() {
        if (!isRun || arrowActive) return;
        arrowActive = true;
        arrowX.set(arrow_x);
        shotsValue.incrementAndGet();
        arrowThread = new Thread(() -> {
            while (arrowActive) {
                arrowX.updateAndGet(x -> x + 3);

                if (arrowX.get() + arrow_length > 560) {
                    arrowActive = false;
                    break;
                }

                double tipX = arrowX.get() + arrow_length;

                Point p1 = target1_p.get();
                double dx1 = tipX - p1.x;
                double dy1 = arrow_y - p1.y;
                if (Math.sqrt(dx1 * dx1 + dy1 * dy1) < target1_radius) {
                    arrowActive = false;
                    scoreValue.incrementAndGet();
                    break;
                }

                Point p2 = target2_p.get();
                double dx2 = tipX - p2.x;
                double dy2 = arrow_y - p2.y;
                if (Math.sqrt(dx2 * dx2 + dy2 * dy2) < target2_radius) {
                    arrowActive = false;
                    scoreValue.addAndGet(2);
                    break;
                }

                synchronized (thread) {
                    if (isPause) {
                        try {
                            thread.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return;
                }
            }
            arrowActive = false;
            arrowThread = null;
        });
        arrowThread.start();
    }
}
