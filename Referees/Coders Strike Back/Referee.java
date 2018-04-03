import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.Line2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Referee extends MultiReferee {
    private static final double EPSILON = 0.00001;
    private static final double MIN_IMPULSE = 120;
    private static final Dimension SIZE = new Dimension(16000, 9000);
    private static final int POD_RADIUS = 400;
    private static final int CHECKPOINT_RADIUS = 600;
    private static final int SPACE_BETWEEN_POD = 100;
    private static final int DEFAULT_POD_PER_PLAYER = 2;

    private static final int MAX_POWER = 200;

    private static final double FRICTION = 0.15;

    private static final int DEFAULT_POD_TIMEOUT = 100;

    private static final int MIN_CHECKPOINT_COUNT = 15;

    private static final int CHECKPOINT_GENERATION_MAX_GAP = 100;

    private static final double MAX_ROTATION_PER_TURN = Math.PI / 10;

    private static final double INACTIVE_ROUND_AFTER_SHIELD = 3;

    private static final double SHIELD_MASS = 10;

    public static void main(String... args) throws IOException {
        new Referee(System.in, System.out, System.err).start();
    }

    public Referee(InputStream is, PrintStream out, PrintStream err) throws IOException {
        super(is, out, err);
    }

    static class Vector {
        private double x, y;

        public Vector(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public Vector(Vector a, Vector b) {
            this.x = b.x - a.x;
            this.y = b.y - a.y;
        }

        public Vector(Point a, Point b) {
            this.x = b.x - a.x;
            this.y = b.y - a.y;
        }

        private Vector round() {
            return new Vector((int) Math.round(this.x), (int) Math.round(this.y));
        }

        private Vector truncate() {
            return new Vector((int) this.x, (int) this.y);
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double distance(Vector v) {
            return Math.sqrt((v.x - x) * (v.x - x) + (v.y - y) * (v.y - y));
        }

        public Vector add(Vector v) {
            return new Vector(x + v.x, y + v.y);
        }

        public Vector mult(double factor) {
            return new Vector(x * factor, y * factor);
        }

        public Vector sub(Vector v) {
            return new Vector(this.x - v.x, this.y - v.y);
        }

        public double length() {
            return Math.sqrt(x * x + y * y);
        }

        public double lengthSquared() {
            return x * x + y * y;
        }

        public Vector normalize() {
            double length = length();
            if (length == 0)
                return new Vector(0, 0);
            return new Vector(x / length, y / length);
        }

        public double dot(Vector v) {
            return x * v.x + y * v.y;
        }

        public double angle() {
            return Math.atan2(y, x);
        }

        public String toString() {
            return "[" + x + ", " + y + "]";
        }

        public Vector project(Vector force) {
            Vector normalize = this.normalize();
            return normalize.mult(normalize.dot(force));
        }

        public final Vector cross(double s) {
            return new Vector(-s * y, s * x);
        }

        public Vector hsymmetric(double center) {
            return new Vector(2 * center - this.x, this.y);
        }

        public Vector vsymmetric(double center) {
            return new Vector(this.x, 2 * center - this.y);
        }

        public Vector vsymmetric() {
            return new Vector(this.x, -this.y);
        }

        public Vector hsymmetric() {
            return new Vector(-this.x, this.y);
        }

        public Vector symmetric(Vector center) {
            return new Vector(center.x * 2 - this.x, center.y * 2 - this.y);
        }

        public Vector checkBounds(double minx, double miny, double maxx, double maxy) {
            if (x < minx || x > maxx || y < miny || y > maxy) {
                return new Vector(Math.min(maxx, Math.max(minx, x)), Math.min(maxy, Math.max(miny, y)));
            }
            return this;
        }

        public Vector translate(Point startPoint) {
            return new Vector(startPoint.x + this.x, startPoint.y + this.y);
        }
    }

    static class CheckPoint {
        private Point position;

        public CheckPoint(int x, int y) {
            this.position = new Point(x, y);
        }
    }

    class Pod implements Comparable<Pod> {
        public class PodWayPoint {
            private double time;
            private Vector position;

            public PodWayPoint(double time, Vector position) {
                this.time = time;
                this.position = position;
            }

            public String toViewString() {
                return time + " " + Math.round(position.x) + " " + Math.round(position.y);
            }
        }

        private int id;
        private List<Double> checkPointTimes;
        private Point targetPosition;
        private Vector lastPosition;
        private Vector position;
        private Vector speed;
        private Vector acceleration;

        private Player player;
        private List<PodWayPoint> wayPoints;
        private String message;
        public int rank, power, wantedPower;
        public Double lastAngle;
        public boolean shieldMode;
        public Integer lastShieldModeRound;

        public Pod(int id, Vector p) {
            this.id = id;
            this.rank = 1;
            this.wayPoints = new ArrayList<>();
            this.speed = new Vector(0, 0);
            this.lastPosition = this.position = p;
            this.checkPointTimes = new ArrayList<>();
            this.checkPointTimes.add(0.0);
        }

        public double getLastCheckPointTime() {
            return checkPointTimes.get(checkPointTimes.size() - 1);
        }

        public void checkCheckPoints(int round) throws GameOverException {
            List<PodWayPoint> positions = new ArrayList<>();
            positions.add(new PodWayPoint(0, lastPosition));
            for (PodWayPoint pwp : wayPoints) {
                positions.add(pwp);
            }
            positions.add(new PodWayPoint(1, position));

            boolean found = true;
            check: while (found) {
                found = false;
                CheckPoint cp = checkPoints[this.checkPointTimes.size() % checkPoints.length];
                for (int i = 0; i < positions.size() - 1; ++i) {
                    PodWayPoint before = positions.get(i);
                    PodWayPoint after = positions.get(i + 1);
                    if (new Line2D.Double(before.position.x, before.position.y, after.position.x, after.position.y)
                            .ptSegDist(cp.position) <= CHECKPOINT_RADIUS) {
                        this.checkPointTimes.add((double) round);
                        found = true;
                        continue check;
                    }
                }
            }

        }

        public String toPlayerString() {
            long angle = -1;
            if (this.lastAngle != null) {
                angle = Math.round(((this.lastAngle % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2) * 180 / Math.PI);
            }

            return Math.round(position.x) + " " + Math.round(position.y) + " " + Math.round(speed.x) + " " + Math.round(speed.y) + " " + angle + " "
                    + (this.player.isDead() ? -1 : getNextCheckpointId());
        }

        public String[] toViewString(int round) {
            StringBuilder pods = new StringBuilder();
            pods.append(position.x + " " + position.y + " " + Math.round(speed.x) + " " + Math.round(speed.y) + " " + power + " "
                    + (this.player.isDead() ? 1 : 0));
            if (targetPosition != null) {
                pods.append(" " + targetPosition.x + " " + targetPosition.y);
            } else {
                pods.append(" null null");
            }
            pods.append(" " + this.lastAngle);
            pods.append(" " + (this.shieldMode ? 1 : 0));
            pods.append(" " + (this.checkPointTimes.size() % checkPoints.length));
            pods.append(" " + this.rank);
            // pods.append(" " + (POD_TIMEOUT - round +
            // getLastCheckPointTime()));
            return new String[] { pods.toString(), "\"" + (this.message == null ? "" : this.message) + "\"" };
        }

        public void reset() {
            wayPoints.clear();
            this.message = null;
            this.targetPosition = null;
            this.power = 0;
        }

        public double getMass() {
            return shieldMode ? SHIELD_MASS : 1;
        }

        public boolean collide(Pod c2) {
            return this.position.distance(c2.position) <= POD_RADIUS * 2;
        }

        public void addWayPoint(double time, double force) {
            if (!wayPoints.isEmpty()) {
                PodWayPoint last = wayPoints.get(wayPoints.size() - 1);
                if (last.time == time) {
                    wayPoints.remove(wayPoints.size() - 1);
                }
            }
            wayPoints.add(new PodWayPoint(time, this.position));
        }

        public void applyForce(double time, Vector force) {
            applyForce(force);
            addWayPoint(time, force.length());
        }

        private void applyForce(Vector force) {
            this.speed = this.speed.add(force.mult(1d / getMass()));
        }

        public void fixPosition(double time, Vector position) {
            setPosition(position);
            if (!wayPoints.isEmpty()) {
                PodWayPoint last = wayPoints.get(wayPoints.size() - 1);
                last.position = position;
            }

        }

        private void setPosition(Vector position) {
            this.position = position;
        }

        public double contactPosition(Pod pod) {
            Vector dv = this.speed.sub(pod.speed);
            Vector d = new Vector(pod.position, this.position);
            double a = dv.lengthSquared();
            double b = d.x * dv.x + d.y * dv.y;
            double dmin = POD_RADIUS * 2;
            double c = d.lengthSquared() - dmin * dmin;
            double delta = b * b - a * c;
            if (delta <= 0 || a == 0) {
                return Double.POSITIVE_INFINITY;
            }
            double rd = Math.sqrt(delta);
            return Math.min((-b + rd) / a, (-b - rd) / a);
        }

        public void step(double step) {
            if (step > 0) {
                this.position = this.position.add(this.speed.mult(step));
            }
        }

        public void endRound() {
            this.position = this.position.round();
            this.speed = this.speed.truncate();
        }

        public void prepareRound() {
            this.acceleration = new Vector(0, 0);
        }

        public void applyFriction() {
            this.speed = this.speed.mult(1 - FRICTION);
        }

        @Override
        public int compareTo(Pod p2) {
            if (this.player.isDead() && !p2.player.isDead()) {
                return 1;
            }
            if (!this.player.isDead() && p2.player.isDead()) {
                return -1;
            }

            if (checkPointTimes.size() > p2.checkPointTimes.size()) {
                return -1;
            } else if (checkPointTimes.size() < p2.checkPointTimes.size()) {
                return 1;
            } else {
                // double time1 = checkPointTimes.get(checkPointTimes.size() -
                // 1);
                // double time2 =
                // p2.checkPointTimes.get(p2.checkPointTimes.size() - 1);
                // return time1 < time2 ? -1 : (time1 > time2 ? 1 : 0);
                CheckPoint nextCheckpoint = checkPoints[getNextCheckpointId()];
                CheckPoint nextCheckpoint2 = checkPoints[p2.getNextCheckpointId()];
                Vector next = new Vector(nextCheckpoint.position.x, nextCheckpoint.position.y);
                Vector next2 = new Vector(nextCheckpoint2.position.x, nextCheckpoint2.position.y);
                double diff = position.distance(next) - p2.position.distance(next2);
                if (diff < 0) {
                    return -1;
                } else if (diff > 0) {
                    return 1;
                } else {
                    return 0;
                }
            }
        }

        private int getNextCheckpointId() {
            return (checkPointTimes.size() % checkPoints.length);
        }
    }

    static class Player implements Comparable<Player> {
        private int id;
        private List<Pod> pods;
        private int rank;
        private String deathReason;
        private String[] nextInputs;

        public Player(int id) {
            this.id = id;
            this.rank = 1;
            pods = new ArrayList<>();
            nextInputs = new String[0];
        }

        public void addPod(Pod pod) {
            this.pods.add(pod);
        }

        @Override
        public int compareTo(Player p) {
            return rank - p.rank;
        }

        public boolean isDead() {
            return deathReason != null;
        }
        
        public void setNextInput(String[] inputs) {
            nextInputs = inputs;
        }
        
        public String[] getNextInput() {
            return nextInputs;
        }
    }

    private static abstract class Collision implements Comparable<Collision> {
        protected double time;

        public Collision(double time) {
            this.time = time;
        }

        public abstract void react();

        @Override
        public int compareTo(Collision col) {
            return Double.compare(time, col.time);
        }

        public String toString() {
            return this.getClass().getName() + " " + String.valueOf(time);
        }

        public abstract String toViewString();
    }

    private static class PodCollision extends Collision {
        static int COLLISION_UNIQUE_ID = 0;

        private int uniqueId;
        private Pod c1, c2;
        private double forceTotal;
        private Vector c1Position, c2Position;
        private Vector speed;

        public PodCollision(double time, Pod a, Pod b) {
            super(time);
            this.c1 = a;
            this.c2 = b;
            this.uniqueId = COLLISION_UNIQUE_ID++;
        }

        @Override
        public void react() {
            this.c1Position = c1.position;
            this.c2Position = c2.position;

            this.speed = c1.speed.add(c2.speed).mult(0.5);

            Vector normal = new Vector(c1.position, c2.position).normalize();
            Vector relativeVelocity = c1.speed.sub(c2.speed);

            double force = normal.dot(relativeVelocity) / (1 / c1.getMass() + 1 / c2.getMass());
            double repulseForce = Math.max(MIN_IMPULSE, force);
            this.forceTotal = force + repulseForce;

            Vector impulse = normal.mult(-1 * this.forceTotal);
            c1.applyForce(time, impulse);
            c2.applyForce(time, impulse.mult(-1));

            double distance = c1.position.distance(c2.position);
            double diff = distance - POD_RADIUS * 2;
            if (diff <= 0) {
                c1.fixPosition(time, c1.position.add(normal.mult(-(-diff / 2 + EPSILON))));
                c2.fixPosition(time, c2.position.add(normal.mult(-diff / 2 + EPSILON)));
            }
        }

        public String toViewString() {
            return uniqueId + " " + this.time + " " + c1.id + " " + Math.round(c1Position.x) + " " + Math.round(c1Position.y) + " " + c2.id + " "
                    + Math.round(c2Position.x) + " " + Math.round(c2Position.y) + " " + forceTotal + " " + Math.round(this.speed.x) + " "
                    + Math.round(this.speed.y);
        }
    }

    private CheckPoint[] checkPoints;
    private Pod[] pods;
    private Player[] players;
    private long seed;
    private List<Collision> collisions;
    private int podTimeout;
    private int podPerPlayer;
    private Integer simulateSoloForAgent;

    @Override
    protected void initReferee(int playerCount, Properties prop) throws InvalidFormatException {
        this.seed = Long.valueOf(prop.getProperty("seed", String.valueOf(new Random(System.currentTimeMillis()).nextLong())));
        Random r = new Random(this.seed);
        List<Point[]> maps = new ArrayList<>();

        maps.add(new Point[] { new Point(12460, 1350), new Point(10540, 5980), new Point(3580, 5180), new Point(13580, 7600) });
        maps.add(new Point[] { new Point(3600, 5280), new Point(13840, 5080), new Point(10680, 2280), new Point(8700, 7460), new Point(7200, 2160) });
        maps.add(new Point[] { new Point(4560, 2180), new Point(7350, 4940), new Point(3320, 7230), new Point(14580, 7700), new Point(10560, 5060),
                new Point(13100, 2320) });
        maps.add(new Point[] { new Point(5010, 5260), new Point(11480, 6080), new Point(9100, 1840) });
        maps.add(new Point[] { new Point(14660, 1410), new Point(3450, 7220), new Point(9420, 7240), new Point(5970, 4240) });
        maps.add(new Point[] { new Point(3640, 4420), new Point(8000, 7900), new Point(13300, 5540), new Point(9560, 1400) });
        maps.add(new Point[] { new Point(4100, 7420), new Point(13500, 2340), new Point(12940, 7220), new Point(5640, 2580) });
        maps.add(new Point[] { new Point(14520, 7780), new Point(6320, 4290), new Point(7800, 860), new Point(7660, 5970), new Point(3140, 7540),
                new Point(9520, 4380) });
        maps.add(new Point[] { new Point(10040, 5970), new Point(13920, 1940), new Point(8020, 3260), new Point(2670, 7020) });
        maps.add(new Point[] { new Point(7500, 6940), new Point(6000, 5360), new Point(11300, 2820) });
        maps.add(new Point[] { new Point(4060, 4660), new Point(13040, 1900), new Point(6560, 7840), new Point(7480, 1360), new Point(12700, 7100) });
        maps.add(new Point[] { new Point(3020, 5190), new Point(6280, 7760), new Point(14100, 7760), new Point(13880, 1220), new Point(10240, 4920),
                new Point(6100, 2200) });
        maps.add(new Point[] { new Point(10323, 3366), new Point(11203, 5425), new Point(7259, 6656), new Point(5425, 2838) });

        collisions = new ArrayList<>();

        if (prop.getProperty("simulate_solo_for_agent") != null) {
            this.simulateSoloForAgent = Integer.parseInt(prop.getProperty("simulate_solo_for_agent"));
        }

        this.podTimeout = Integer.parseInt(prop.getProperty("pod_timeout", Integer.toString(DEFAULT_POD_TIMEOUT)));
        this.podPerPlayer = Integer.parseInt(prop.getProperty("pod_per_player", Integer.toString(DEFAULT_POD_PER_PLAYER)));

        String mapProperty = prop.getProperty("map");
        if (mapProperty == null) {
            int map = Integer.valueOf(prop.getProperty("map", String.valueOf(r.nextInt(maps.size()))));
            generateMap(r, maps.get(Math.min(maps.size() - 1, Math.max(0, map))));
        } else {
            String[] tab = mapProperty.split(" ");
            if (tab.length % 2 != 0) {
                throw new InvalidFormatException("Map must contain at least 2 checkpoints");
            }
            if (tab.length < 4) {
                throw new InvalidFormatException("Map must contain at least 2 checkpoints");
            }
            List<CheckPoint> checkPoints = new ArrayList<>();

            for (int i = 0; i < tab.length; i += 2) {
                checkPoints.add(new CheckPoint(Integer.parseInt(tab[i]), Integer.parseInt(tab[i + 1])));
            }
            this.checkPoints = checkPoints.toArray(new CheckPoint[checkPoints.size()]);
        }

        generatePlayers(playerCount, checkPoints[0].position, new Vector(checkPoints[0].position, checkPoints[1].position), r);
    }

    private void generateMap(Random r, Point[] map) {
        List<CheckPoint> checkPoints = new ArrayList<>();
        List<Point> points = Arrays.asList(map);
        Collections.rotate(points, r.nextInt(points.size()));
        for (Point p : points) {
            checkPoints.add(new CheckPoint(p.x + r.nextInt(CHECKPOINT_GENERATION_MAX_GAP * 2 + 1) - CHECKPOINT_GENERATION_MAX_GAP,
                    p.y + r.nextInt(CHECKPOINT_GENERATION_MAX_GAP * 2 - 1) - CHECKPOINT_GENERATION_MAX_GAP));
        }

        this.checkPoints = checkPoints.toArray(new CheckPoint[checkPoints.size()]);

    }

    private void generatePlayers(int playerCount, Point startPoint, Vector direction, Random r) {
        this.players = new Player[playerCount];
        for (int i = 0; i < playerCount; ++i) {
            this.players[i] = new Player(i);
        }
        int podCount = this.podPerPlayer * playerCount;

        Vector vector = direction.normalize().cross(1);

        List<Pod> pods = new ArrayList<>();
        for (int i = 0; i < podCount; ++i) {
            double offset = ((i % 2 == 0 ? -1 : 1) * (i / 2 * 2 + 1) + podCount % 2);
            Pod pod = new Pod(i, vector.mult(offset * (POD_RADIUS + SPACE_BETWEEN_POD)).translate(startPoint).round());
            pods.add(pod);
        }
        this.pods = pods.toArray(new Pod[pods.size()]);
        for (int i = 0; i < podCount; ++i) {
            Player player = this.players[i / this.podPerPlayer];
            pods.get(i).player = player;
            player.addPod(pods.get(i));
        }
    }

    @Override
    protected int getMaxRoundCount(int playerCount) {
        return 500;
    }

    @Override
    protected Properties getConfiguration() {
        Properties prop = new Properties();
        prop.setProperty("seed", String.valueOf(this.seed));
        StringBuilder map = new StringBuilder();
        for (CheckPoint p : this.checkPoints) {
            map.append(p.position.x);
            map.append(" ");
            map.append(p.position.y);
            map.append(" ");
        }
        prop.setProperty("map", map.toString().trim());
        prop.setProperty("pod_timeout", String.valueOf(this.podTimeout));
        prop.setProperty("pod_per_player", String.valueOf(this.podPerPlayer));

        if (this.simulateSoloForAgent != null) {
            prop.setProperty("simulate_solo_for_agent", String.valueOf(this.simulateSoloForAgent));
        }

        return prop;
    }

    @Override
    protected void populateMessages(Properties p) {
        p.put("endReached", "End reached");
        p.put("podDead", "Pod %d of player $%d is eliminated");
        p.put("podAction", "Pod %d of player $%d moves towards (%d, %d) at power %d");
        p.put("podReloadShield", "Pod %d of player $%d waits for it's engine to reload");
        p.put("podActionShield", "Pod %d of player $%d enabled it's shield");
        p.put("podBoost", "Pod %d of player $%d boosts towards (%d, %d)");

        p.put("checkPointTimeout", "$%d did not reach the next checkpoint in time");
    }

    @Override
    protected String[] getInitDataForView() {
        List<String> list = new ArrayList<>();
        list.add(SIZE.width + " " + SIZE.height + " " + POD_RADIUS + " " + CHECKPOINT_RADIUS + " " + (this.players.length * this.podPerPlayer) + " "
                + MAX_ROTATION_PER_TURN);

        StringBuilder checkPoints = new StringBuilder();
        for (CheckPoint p : this.checkPoints) {
            if (p != this.checkPoints[0]) {
                checkPoints.append(" ");
            }
            checkPoints.append(p.position.x + " " + p.position.y);
        }
        list.add(checkPoints.toString());

        StringBuilder pods = new StringBuilder();
        for (Pod p : this.pods) {
            if (p != this.pods[0]) {
                pods.append(" ");
            }
            pods.append(p.player.id + " " + p.position.x + " " + p.position.y);
        }
        list.add(pods.toString());

        return list.toArray(new String[list.size()]);
    }

    @Override
    protected String[] getFrameDataForView(int round, int frame, boolean keyFrame) {
        if (keyFrame) {
            List<String> list = new ArrayList<>();
            for (Pod p : this.pods) {
                list.addAll(Arrays.asList(p.toViewString(round)));
            }
            StringBuilder ranks = new StringBuilder();
            for (Player p : players) {
                if (p != players[0]) {
                    ranks.append(" ");
                }
                int lastCheckPointTime = 0;
                for (Pod pod : p.pods) {
                    lastCheckPointTime = Math.max(lastCheckPointTime, (int) pod.getLastCheckPointTime());
                }
                int timeout = this.podTimeout - round + lastCheckPointTime;
                if (timeout > 100) {
                }
                ranks.append(p.rank + ":" + timeout);
            }
            list.add(ranks.toString());

            for (Collision c : this.collisions) {
                list.add(c.toViewString());
            }

            return list.toArray(new String[list.size()]);
        } else {
            return new String[0];
        }
    }

    @Override
    protected int getExpectedOutputLineCountForPlayer(int playerIdx) {
        return this.podPerPlayer;
    }

    @Override
    protected String getGameName() {
        return "CodersStrikeBack";
    }

    private static double shortAngleDist(double a0, double a1) {
        double max = Math.PI * 2;
        double da = (a1 - a0) % max;
        return 2 * da % max - da;
    }

    private static final Pattern PLAYER_INPUT_PATTERN = Pattern
            .compile("(?<x>-?[0-9]{1,9})\\s+(?<y>-?[0-9]{1,9})\\s+(?<power>([0-9]{1,9}|SHIELD))?(?:\\s+(?<message>.+))?");

    @Override
    protected void handlePlayerOutput(int frame, int round, int playerIdx, String[] outputs)
            throws WinException, LostException, InvalidInputException {
        int i = 0;
        Player player = this.players[playerIdx];
        for (String line : outputs) {
            Matcher match = PLAYER_INPUT_PATTERN.matcher(line);
            try {
                if (match.matches()) {
                    Pod pod = player.pods.get(i);
                    if (!pod.player.isDead()) {
                        int x = Integer.parseInt(match.group("x"));
                        int y = Integer.parseInt(match.group("y"));
                        String powerStr = match.group("power");
                        if (pod.shieldMode = "SHIELD".equals(powerStr)) {
                            pod.wantedPower = 0;
                            pod.lastShieldModeRound = round;
                        } else {
                            if (powerStr != null) {
                                pod.wantedPower = Integer.parseInt(powerStr);
                            } else {
                                pod.wantedPower = MAX_POWER;
                            }
                            if (pod.wantedPower < 0 || pod.wantedPower > MAX_POWER) {
                                throw new InvalidInputException("0 <= power <= " + MAX_POWER, "power = " + pod.wantedPower);
                            }
                        }
                        pod.power = pod.wantedPower;
                        if ((pod.lastShieldModeRound != null && round <= pod.lastShieldModeRound + INACTIVE_ROUND_AFTER_SHIELD)) {
                            pod.power = 0;
                        }

                        pod.targetPosition = new Point(x, y);

                        if (pod.position.x != x || pod.position.y != y) {
                            double angle = new Vector(pod.position, new Vector(x, y)).angle();
                            if (pod.lastAngle != null) {
                                double relativeAngle = shortAngleDist(pod.lastAngle, angle);
                                if (Math.abs(relativeAngle) >= MAX_ROTATION_PER_TURN) {
                                    angle = pod.lastAngle + MAX_ROTATION_PER_TURN * Math.signum(relativeAngle);
                                }
                            }
                            pod.lastAngle = angle;

                            Vector direction = new Vector(Math.cos(angle), Math.sin(angle));
                            pod.acceleration = direction.normalize().mult(pod.power);
                        } else {
                            pod.acceleration = new Vector(0, 0);
                        }
                    }

                    pod.message = match.group("message");
                    if (pod.message != null && pod.message.length() > 50) {
                        pod.message = pod.message.substring(0, 50) + "...";
                    }
                } else {
                    throw new InvalidInputException("x y power", line);
                }
            } catch (InvalidInputException e) {
                for (Pod p : player.pods) {
                    p.player.deathReason = "invalidInput";
                }
                throw e;
            }
            ++i;
        }
    }

    @Override
    protected String[] getInitInputForPlayer(int playerIdx) {
        List<String> data = new ArrayList<>();
        if (this.simulateSoloForAgent == null || this.simulateSoloForAgent != playerIdx) {
            int laps = (int) (Math.ceil((double) MIN_CHECKPOINT_COUNT) / ((double) this.checkPoints.length));
            data.add(String.valueOf(laps));
            data.add(String.valueOf(this.checkPoints.length));
            for (CheckPoint cp : this.checkPoints) {
                data.add(cp.position.x + " " + cp.position.y);
            }
        }
        return data.toArray(new String[data.size()]);
    }

    @Override
    protected String[] getInputForPlayer(int round, int playerIdx) {
        return this.players[playerIdx].getNextInput();
    }

    private String[] preGetInputForPlayer(int round, int playerIdx) {
        List<String> data = new ArrayList<>();
        Player player = this.players[playerIdx];

        if (this.simulateSoloForAgent == null || this.simulateSoloForAgent != playerIdx) {
            for (Pod p : this.pods) {
                if (p.player == player) {
                    data.add(p.toPlayerString());
                }
            }
            for (Pod p : this.pods) {
                if (p.player != player) {
                    data.add(p.toPlayerString());
                }
            }
        } else {
            for (Pod p : this.pods) {
                if (p.player == player) {
                    CheckPoint next = checkPoints[p.getNextCheckpointId()];
                    data.add(Math.round(p.position.x) + " " + Math.round(p.position.y) + " " + Math.round(next.position.x) + " "
                            + Math.round(next.position.y));
                }
            }
        }
        return data.toArray(new String[data.size()]);
    }

    @Override
    protected String getHeadlineAtGameStartForConsole() {
        return null;
    }

    @Override
    protected int getMinimumPlayerCount() {
        if (this.players.length == 1) {
            return 1;
        } else {
            return 2;
        }
    }

    @Override
    protected boolean showTooltips() {
        return true;
    }

    @Override
    protected void prepare(int round) {
        for (Player p : players) {
            if (!p.isDead()) {
                p.setNextInput(preGetInputForPlayer(round, p.id));
            } else {
                p.setNextInput(new String[0]);
            }
            for (Pod pod : p.pods) {
                pod.reset();
                pod.prepareRound();
            }
        }
        this.collisions.clear();
    }

    @Override
    protected void updateGame(int round) throws GameOverException {
        List<Pod> pods = new ArrayList<>();

        for (Player p : players) {
            pods.addAll(p.pods);
        }
        for (Pod p : pods) {
            p.lastPosition = p.position;
            p.applyForce(p.acceleration);
        }

        double time = 0;
        while (time < 1) {
            double step = step(pods, time);
            time += step;
        }

        for (Pod p : pods) {
            p.applyFriction();
            p.endRound();
        }

        int lastCheckPoint = (int) (Math.ceil((double) MIN_CHECKPOINT_COUNT) / ((double) this.checkPoints.length)) * this.checkPoints.length;

        boolean ended = false;
        for (Pod p : pods) {
            if (!p.player.isDead()) {
                p.checkCheckPoints(round);
                if (p.checkPointTimes.size() > lastCheckPoint) {
                    ended = true;
                }

            }
        }

        for (Player p : this.players) {
            boolean dead = true;
            for (Pod pod : p.pods) {
                if (round - pod.getLastCheckPointTime() < this.podTimeout) {
                    dead = false;
                }
            }
            if (dead) {
                p.deathReason = translate("checkPointTimeout", p.id);
            }
        }

        List<Pod> podRanking = new ArrayList<>(pods);
        Collections.sort(podRanking);
        for (Player p : this.players) {
            p.rank = pods.size();
        }
        int rank = 0;
        for (int i = 0; i < podRanking.size(); ++i) {
            if (i <= 0 || podRanking.get(i).compareTo(podRanking.get(i - 1)) > 0) {
                rank++;
            }
            podRanking.get(i).rank = rank;
            Player player = podRanking.get(i).player;
            if (rank < player.rank) {
                player.rank = rank;
            }
        }

        for (int i = 1; i <= pods.size(); ++i) {
            int _rank = i;
            boolean found = false;
            while (!found && _rank <= pods.size()) {
                for (Player p : players) {
                    if (p.rank == _rank) {
                        p.rank = i;
                        found = true;
                    }
                }
                _rank++;
            }
        }

        if (ended) {
            throw new GameOverException("endReached");
        }
    }

    private double step(List<Pod> pods, double beginTime) {
        List<Collision> collisions = new ArrayList<>();
        double min = 1 - beginTime;

        // physics loop between chips
        for (int i = pods.size() - 1; i >= 0; --i) {
            Pod pod1 = pods.get(i);
            for (int j = i - 1; j >= 0; --j) {
                Pod pod2 = pods.get(j);
                if (pod2.collide(pod1)) {
                    collisions.add(new PodCollision(beginTime, pod1, pod2));
                } else {
                    double colision = pod1.contactPosition(pod2);
                    if (colision <= min && colision > 0) {
                        collisions.add(new PodCollision(beginTime + colision, pod1, pod2));
                    }
                }
            }
        }

        if (!collisions.isEmpty()) {
            Collections.sort(collisions);
            double time = -1;
            for (Collision c : collisions) {
                if (time <= -1) {
                    time = c.time;
                    double step = time - beginTime;
                    for (Pod ch : pods) {
                        ch.step(step);
                    }
                }
                if (c.time <= time + EPSILON) {
                    this.collisions.add(c);
                    c.react();
                } else {
                    break;
                }
            }

            return time - beginTime;
        }

        double step = 1 - beginTime;
        for (Pod ch : pods) {
            ch.step(step);
        }

        return step;
    }

    @Override
    protected String[] getPlayerActions(int playerIdx, int round) {
        List<Pod> pods = this.players[playerIdx].pods;
        List<String> action = new ArrayList<>(pods.size());
        int podId = 1;
        for (Pod p : pods) {
            if (p.player.isDead()) {
                action.add(translate("podDead", podId, p.player.id));
            } else {
                if (p.shieldMode) {
                    action.add(translate("podActionShield", podId, p.player.id));
                } else {
                    if (p.lastShieldModeRound != null && round <= p.lastShieldModeRound + INACTIVE_ROUND_AFTER_SHIELD) {
                        action.add(translate("podReloadShield", podId, p.player.id));
                    } else {
                        action.add(translate("podAction", podId, p.player.id, p.targetPosition.x, p.targetPosition.y, p.power));
                    }
                }
            }
            podId++;
        }
        return action.toArray(new String[action.size()]);
    }

    @Override
    protected boolean isPlayerDead(int playerIdx) {
        return this.players[playerIdx].isDead();
    }

    @Override
    protected String getDeathReason(int playerIdx) {
        return this.players[playerIdx].deathReason;
    }

    @Override
    protected int getScore(int playerIdx) {
        if (this.players[playerIdx].isDead()) {
            return 0;
        } else {
            return this.players.length - this.players[playerIdx].rank + 1;
        }
    }

    @Override
    protected String[] getGameSummary(int round) {
        List<String> summary = new ArrayList<>();
        for (Player p : players) {
            if (p.isDead()) {
                summary.add("$" + p.id + " eliminated! rank: " + p.rank);
            } else {
                summary.add("$" + p.id + " rank: " + p.rank);
            }
        }
        return summary.toArray(new String[summary.size()]);
    }

    @Override
    protected void setPlayerTimeout(int frame, int round, int playerIdx) {
        this.players[playerIdx].deathReason = "playerTimeout";
    }

}

// ------------------------------------------------------------------------------------------------------------

abstract class MultiReferee extends AbstractReferee {
    private Properties properties;

    public MultiReferee(InputStream is, PrintStream out, PrintStream err) throws IOException {
        super(is, out, err);
    }

    @Override
    protected final void handleInitInputForReferee(int playerCount, String[] init) throws InvalidFormatException {
        properties = new Properties();
        try {
            for (String s : init) {
                properties.load(new StringReader(s));
            }
        } catch (IOException e) {
        }
        initReferee(playerCount, properties);
        properties = getConfiguration();
    }

    abstract protected void initReferee(int playerCount, Properties prop) throws InvalidFormatException;

    abstract protected Properties getConfiguration();

    protected void appendDataToEnd(PrintStream stream) throws IOException {
        stream.println(OutputCommand.UINPUT.format(properties.size()));
        for (Entry<Object, Object> t : properties.entrySet()) {
            stream.println(t.getKey() + "=" + t.getValue());
        }
    }
}

abstract class SoloReferee extends AbstractReferee {
    private int score;

    public SoloReferee(InputStream is, PrintStream out, PrintStream err) throws IOException {
        super(is, out, err);
    }

    protected abstract void handlePlayerOutput(int round, String[] playerOutput) throws WinException, LostException, InvalidInputException;

    @Override
    protected final void handlePlayerOutput(int frame, int round, int playerIdx, String[] playerOutput)
            throws WinException, LostException, InvalidInputException {
        if (playerIdx != 0)
            throw new RuntimeException("SoloReferee could only handle one-player games");
        try {
            handlePlayerOutput(round, playerOutput);
        } catch (LostException | InvalidInputException e) {
            score = 0;
            throw e;
        } catch (WinException e) {
            score = 1;
            throw e;
        }
    }

    protected abstract String[] getInitInputForPlayer();

    protected abstract String[] getInputForPlayer(int round);

    protected abstract String[] getTextForConsole(int round);

    @Override
    protected final String[] getInitInputForPlayer(int playerIdx) {
        if (playerIdx != 0)
            throw new RuntimeException("SoloReferee could only handle one-player games");
        return getInitInputForPlayer();

    }

    @Override
    protected boolean showTooltips() {
        return false;
    }

    @Override
    protected final String[] getInputForPlayer(int round, int playerIdx) {
        if (playerIdx != 0)
            throw new RuntimeException("SoloReferee could only handle one-player games");
        return getInputForPlayer(round);
    }

    protected abstract int getExpectedOutputLineCountForPlayer();

    @Override
    protected int getExpectedOutputLineCountForPlayer(int playerIdx) {
        if (playerIdx != 0)
            throw new RuntimeException("SoloReferee could only handle one-player games");
        return getExpectedOutputLineCountForPlayer();
    }

    @Override
    protected int getMinimumPlayerCount() {
        return 1;
    }

    @Override
    protected void updateGame(int round) {
        // osef
    }

    protected void appendDataToEnd(PrintStream stream) {
    }

    @Override
    protected final boolean isPlayerDead(int playerIdx) {
        return false;
    }

    @Override
    protected final int getScore(int playerIdx) {
        return score;
    }

    protected final String[] getGameSummary(int round) {
        return getTextForConsole(round);
    }

    protected final String[] getPlayerActions(int playerIdx, int round) {
        return new String[0];
    }

    protected final void setPlayerTimeout(int playerIdx) {
        // don't care
    }

    protected abstract void handleInitInputForReferee(String[] init) throws InvalidFormatException;

    protected final void handleInitInputForReferee(int playerCount, String[] init) throws InvalidFormatException {
        if (playerCount > 1) {
            throw new InvalidFormatException("It's a solo game !");
        }
        handleInitInputForReferee(init);
    }

    @Override
    protected void setPlayerTimeout(int frame, int round, int playerIdx) {
        // don't care
    }
}

abstract class AbstractReferee {
    private static final Pattern HEADER_PATTERN = Pattern.compile("\\[\\[(?<cmd>.+)\\] ?(?<lineCount>[0-9]+)\\]");
    private static final String LOST_PARSING_REASON_CODE = "INPUT";
    private static final String LOST_PARSING_REASON = "Failure: invalid input";

    protected static class PlayerStatus {
        private int id;
        private int score;
        private boolean lost, win;
        private String info;
        private String reasonCode;

        public PlayerStatus(int id) {
            this.id = id;
            lost = false;
            info = null;
        }

        public int getScore() {
            return score;
        }

        public boolean isLost() {
            return lost;
        }

        public String getInfo() {
            return info;
        }

        public int getId() {
            return id;
        }

        public String getReasonCode() {
            return reasonCode;
        }
    }

    private Properties messages = new Properties();

    @SuppressWarnings("serial")
    final class InvalidFormatException extends Exception {
        public InvalidFormatException(String message) {
            super(message);
        }
    }

    @SuppressWarnings("serial")
    abstract class GameException extends Exception {
        private String reasonCode;
        private Object[] values;

        public GameException(String reasonCode, Object... values) {
            this.reasonCode = reasonCode;
            this.values = values;
        }

        public String getReason() {
            if (reasonCode != null) {
                return translate(reasonCode, values);
            } else {
                return null;
            }
        }

        public String getReasonCode() {
            return reasonCode;
        }
    }

    @SuppressWarnings("serial")
    class LostException extends GameException {
        public LostException(String reasonCode, Object... values) {
            super(reasonCode, values);
        }
    }

    @SuppressWarnings("serial")
    class WinException extends GameException {
        public WinException(String reasonCode, Object... values) {
            super(reasonCode, values);
        }
    }

    @SuppressWarnings("serial")
    class InvalidInputException extends GameException {
        public InvalidInputException(String expected, String found) {
            super("InvalidInput", expected, found);
        }
    }

    @SuppressWarnings("serial")
    class GameOverException extends GameException {
        public GameOverException(String reasonCode, Object... values) {
            super(reasonCode, values);
        }
    }

    @SuppressWarnings("serial")
    class GameErrorException extends Exception {
        public GameErrorException(Throwable cause) {
            super(cause);
        }
    }

    public static enum InputCommand {
        INIT, GET_GAME_INFO, SET_PLAYER_OUTPUT, SET_PLAYER_TIMEOUT
    }

    public static enum OutputCommand {
        VIEW, INFOS, NEXT_PLAYER_INPUT, NEXT_PLAYER_INFO, SCORES, UINPUT, TOOLTIP, SUMMARY;
        public String format(int lineCount) {
            return String.format("[[%s] %d]", this.name(), lineCount);
        }
    }

    @SuppressWarnings("serial")
    public static class OutputData extends LinkedList<String> {
        private OutputCommand command;

        public OutputData(OutputCommand command) {
            this.command = command;
        }

        public boolean add(String s) {
            if (s != null)
                return super.add(s);
            return false;
        }

        public void addAll(String[] data) {
            if (data != null)
                super.addAll(Arrays.asList(data));
        }

        @Override
        public String toString() {
            StringWriter writer = new StringWriter();
            PrintWriter out = new PrintWriter(writer);
            out.println(this.command.format(this.size()));
            for (String line : this) {
                out.println(line);
            }
            return writer.toString().trim();
        }
    }

    private static class Tooltip {
        int player;
        String message;

        public Tooltip(int player, String message) {
            this.player = player;
            this.message = message;
        }
    }

    private Set<Tooltip> tooltips;
    private int playerCount, alivePlayerCount;
    private int currentPlayer, nextPlayer;
    private PlayerStatus lastPlayer, playerStatus;
    private int frame, round;
    private PlayerStatus[] players;
    private String[] initLines;
    private boolean newRound;
    private String reasonCode, reason;

    private InputStream is;
    private PrintStream out;
    private PrintStream err;

    public AbstractReferee(InputStream is, PrintStream out, PrintStream err) throws IOException {
        tooltips = new HashSet<>();
        this.is = is;
        this.out = out;
        this.err = err;
        start();
    }

    @SuppressWarnings("resource")
    public void start() throws IOException {
        this.messages.put("InvalidInput", "invalid input. Expected '%s' but found '%s'");
        this.messages.put("playerTimeoutMulti",
                "Timeout: the program did not provide %d input lines in due time... $%d will no longer be active in this game.");
        this.messages.put("playerTimeoutSolo", "Timeout: the program did not provide %d input lines in due time...");
        this.messages.put("maxRoundsCountReached", "Max rounds reached");
        this.messages.put("notEnoughPlayers", "Not enough players (expected > %d, found %d)");
        this.messages.put("InvalidActionTooltip", "$%d: invalid action");
        this.messages.put("TimeoutTooltip", "$%d: timeout!");
        this.messages.put("WinTooltip", "$%d: victory!");

        populateMessages(this.messages);
        Scanner s = new Scanner(is);
        int i;
        try {
            while (true) {
                String line = s.nextLine();
                Matcher m = HEADER_PATTERN.matcher(line);
                if (!m.matches())
                    throw new RuntimeException("Error in data sent to referee");
                String cmd = m.group("cmd");
                int lineCount = Integer.parseInt(m.group("lineCount"));

                switch (InputCommand.valueOf(cmd)) {
                case INIT:
                    playerCount = alivePlayerCount = s.nextInt();
                    players = new PlayerStatus[playerCount];
                    for (i = 0; i < playerCount; ++i)
                        players[i] = new PlayerStatus(i);

                    playerStatus = players[0];

                    currentPlayer = nextPlayer = playerCount - 1;
                    frame = 0;
                    round = -1;
                    newRound = true;
                    s.nextLine();
                    i = 0;

                    initLines = null;
                    if (lineCount > 0) {
                        initLines = new String[lineCount - 1];
                        for (i = 0; i < (lineCount - 1); i++) {
                            initLines[i] = s.nextLine();
                        }
                    }
                    try {
                        handleInitInputForReferee(playerCount, initLines);
                    } catch (RuntimeException | InvalidFormatException e) {
                        reason = "Init error: " + e.getMessage();
                        OutputData viewData = new OutputData(OutputCommand.VIEW);
                        viewData.add(String.valueOf(-1));
                        viewData.add(LOST_PARSING_REASON_CODE);
                        out.println(viewData);
                        OutputData infoData = new OutputData(OutputCommand.INFOS);
                        infoData.add(getColoredReason(true, LOST_PARSING_REASON));
                        infoData.add(getColoredReason(true, e.getMessage()));
                        infoData.addAll(initLines);
                        out.println(infoData);
                        dumpView();
                        dumpInfos();
                        throw new GameErrorException(e);
                    }

                    if (playerCount < getMinimumPlayerCount()) {
                        throw new GameOverException("notEnoughPlayers", getMinimumPlayerCount(), playerCount);
                    }
                    break;
                case GET_GAME_INFO:
                    lastPlayer = playerStatus;
                    playerStatus = nextPlayer();
                    if (this.round >= getMaxRoundCount(this.playerCount)) {
                        throw new GameOverException("maxRoundsCountReached");
                    }
                    dumpView();
                    dumpInfos();
                    if (newRound) {
                        prepare(round);
                    }
                    dumpNextPlayerInput();
                    dumpNextPlayerInfos();
                    break;
                case SET_PLAYER_OUTPUT:
                    ++frame;
                    String[] output = new String[lineCount];
                    for (i = 0; i < lineCount; i++) {
                        output[i] = s.nextLine();
                    }
                    try {
                        handlePlayerOutput(frame, round, nextPlayer, output);
                    } catch (LostException | InvalidInputException e) {
                        playerStatus.score = getScore(nextPlayer);
                        playerStatus.lost = true;
                        playerStatus.info = e.getReason();
                        playerStatus.reasonCode = e.getReasonCode();
                        addToolTip(nextPlayer, translate("InvalidActionTooltip", nextPlayer));
                        if (--alivePlayerCount < getMinimumPlayerCount() && isTurnBasedGame()) {
                            lastPlayer = playerStatus;
                            throw new GameOverException(null);
                        }
                    } catch (WinException e) {
                        playerStatus.score = getScore(nextPlayer);
                        playerStatus.win = true;
                        playerStatus.info = e.getReason();
                        playerStatus.reasonCode = e.getReasonCode();
                        addToolTip(nextPlayer, translate("WinTooltip", nextPlayer));
                        if (--alivePlayerCount < getMinimumPlayerCount()) {
                            lastPlayer = playerStatus;
                            throw new GameOverException(null);
                        }
                    }
                    break;
                case SET_PLAYER_TIMEOUT:
                    ++frame;
                    int count = getExpectedOutputLineCountForPlayer(nextPlayer);
                    setPlayerTimeout(frame, round, nextPlayer);
                    playerStatus.lost = true;
                    if (playerCount <= 1) {
                        playerStatus.info = translate("playerTimeoutSolo", count);
                    } else {
                        playerStatus.info = translate("playerTimeoutMulti", count, nextPlayer);
                    }
                    addToolTip(nextPlayer, translate("TimeoutTooltip", nextPlayer));
                    if (--alivePlayerCount < getMinimumPlayerCount() && isTurnBasedGame()) {
                        lastPlayer = playerStatus;
                        throw new GameOverException(null);
                    }
                    break;
                }
            }
        } catch (GameOverException e) {
            newRound = true;
            reasonCode = e.getReasonCode();
            reason = e.getReason();

            dumpView();
            dumpInfos();
            prepare(round);
            updateScores();
        } catch (GameErrorException e) {
            // e.printStackTrace();
        } catch (Throwable t) {
            t.printStackTrace();
            s.close();
            return;
        }

        String[] playerScores = new String[playerCount];
        for (i = 0; i < playerCount; ++i) {
            playerScores[i] = i + " " + players[i].score;
        }
        appendDataToEnd(out);
        OutputData data = new OutputData(OutputCommand.SCORES);
        data.addAll(playerScores);
        out.println(data);
        s.close();
    }

    private PlayerStatus nextPlayer() throws GameOverException {
        currentPlayer = nextPlayer;
        newRound = false;
        do {
            ++nextPlayer;
            if (nextPlayer >= playerCount) {
                nextRound();
                nextPlayer = 0;
            }
        } while (this.players[nextPlayer].lost || this.players[nextPlayer].win);
        return players[nextPlayer];
    }

    protected String getColoredReason(boolean error, String reason) {
        if (error) {
            return String.format("¤RED¤%s§RED§", reason);
        } else {
            return String.format("¤GREEN¤%s§GREEN§", reason);
        }
    }

    private void dumpView() {
        OutputData data = new OutputData(OutputCommand.VIEW);
        String reasonCode = this.reasonCode;
        if (reasonCode == null && playerStatus != null)
            reasonCode = playerStatus.reasonCode;

        if (newRound) {
            if (reasonCode != null) {
                data.add(String.format("KEY_FRAME %d %s", this.frame, reasonCode));
            } else {
                data.add(String.format("KEY_FRAME %d", this.frame));
            }
            if (frame == 0) {
                data.add(getGameName());
                data.addAll(getInitDataForView());
            }
        } else {
            if (reasonCode != null) {
                data.add(String.format("INTERMEDIATE_FRAME %d %s", this.frame, reasonCode));
            } else {
                data.add(String.format("INTERMEDIATE_FRAME %d", frame));
            }
        }
        data.addAll(getFrameDataForView(round, frame, newRound));

        out.println(data);
    }

    private void dumpInfos() {
        OutputData data = new OutputData(OutputCommand.INFOS);
        if (reason != null && isTurnBasedGame()) {
            data.add(getColoredReason(true, reason));
        } else {
            if (lastPlayer != null) {
                String head = lastPlayer.info;
                if (head != null) {
                    data.add(getColoredReason(lastPlayer.lost, head));
                } else {
                    if (frame > 0) {
                        data.addAll(getPlayerActions(this.currentPlayer, newRound ? this.round - 1 : this.round));
                    }
                }
            }
        }
        out.println(data);
        if (newRound && round >= -1 && playerCount > 1) {
            OutputData summary = new OutputData(OutputCommand.SUMMARY);
            if (frame == 0) {
                String head = getHeadlineAtGameStartForConsole();
                if (head != null) {
                    summary.add(head);
                }
            }
            if (round >= 0) {
                summary.addAll(getGameSummary(round));
            }
            if (!isTurnBasedGame() && reason != null) {
                summary.add(getColoredReason(true, reason));
            }
            out.println(summary);
        }

        if (!tooltips.isEmpty()) {
            data = new OutputData(OutputCommand.TOOLTIP);
            for (Tooltip t : tooltips) {
                data.add(t.message);
                data.add(String.valueOf(t.player));
            }
            tooltips.clear();
            out.println(data);
        }
    }

    private void dumpNextPlayerInfos() {
        OutputData data = new OutputData(OutputCommand.NEXT_PLAYER_INFO);
        data.add(String.valueOf(nextPlayer));
        data.add(String.valueOf(getExpectedOutputLineCountForPlayer(nextPlayer)));
        if (this.round == 0) {
            data.add(String.valueOf(getMillisTimeForFirstRound()));
        } else {
            data.add(String.valueOf(getMillisTimeForRound()));
        }
        out.println(data);
    }

    private void dumpNextPlayerInput() {
        OutputData data = new OutputData(OutputCommand.NEXT_PLAYER_INPUT);
        if (this.round == 0) {
            data.addAll(getInitInputForPlayer(nextPlayer));
        }
        data.addAll(getInputForPlayer(round, nextPlayer));
        out.println(data);
    }

    protected final String translate(String code, Object... values) {
        try {
            return String.format((String) messages.get(code), values);
        } catch (NullPointerException e) {
            return String.format((String) messages.get(code), values);
        }
    }

    protected final void printError(Object message) {
        err.println(message);
    }

    protected int getMillisTimeForFirstRound() {
        return 1000;
    }

    protected int getMillisTimeForRound() {
        return 150;
    }

    protected int getMaxRoundCount(int playerCount) {
        return 400;
    }

    private void nextRound() throws GameOverException {
        newRound = true;
        if (++round > 0) {
            updateGame(round);
            updateScores();
        }
        if (alivePlayerCount < getMinimumPlayerCount()) {
            throw new GameOverException(null);
        }
    }

    private void updateScores() {
        for (int i = 0; i < playerCount; ++i) {
            if (!players[i].lost && isPlayerDead(i)) {
                alivePlayerCount--;
                players[i].lost = true;
                players[i].info = getDeathReason(i);
                addToolTip(i, players[i].info);
            }
            players[i].score = getScore(i);
        }
    }

    protected void addToolTip(int player, String message) {
        if (showTooltips())
            tooltips.add(new Tooltip(player, message));
    }

    /**
     * 
     * Add message (key = reasonCode, value = reason)
     * 
     * @param p
     */
    protected abstract void populateMessages(Properties p);

    protected boolean isTurnBasedGame() {
        return false;
    }

    protected abstract void handleInitInputForReferee(int playerCount, String[] init) throws InvalidFormatException;

    protected abstract String[] getInitDataForView();

    protected abstract String[] getFrameDataForView(int round, int frame, boolean keyFrame);

    protected abstract int getExpectedOutputLineCountForPlayer(int playerIdx);

    protected abstract String getGameName();

    protected abstract void appendDataToEnd(PrintStream stream) throws IOException;

    /**
     * 
     * @param player
     *            player id
     * @param output
     * @return score of the player
     */
    protected abstract void handlePlayerOutput(int frame, int round, int playerIdx, String[] output)
            throws WinException, LostException, InvalidInputException;

    protected abstract String[] getInitInputForPlayer(int playerIdx);

    protected abstract String[] getInputForPlayer(int round, int playerIdx);

    protected abstract String getHeadlineAtGameStartForConsole();

    protected abstract int getMinimumPlayerCount();

    protected abstract boolean showTooltips();

    /**
     * 
     * @param round
     * @return scores of all players
     * @throws GameOverException
     */
    protected abstract void updateGame(int round) throws GameOverException;

    protected abstract void prepare(int round);

    protected abstract boolean isPlayerDead(int playerIdx);

    protected abstract String getDeathReason(int playerIdx);

    protected abstract int getScore(int playerIdx);

    protected abstract String[] getGameSummary(int round);

    protected abstract String[] getPlayerActions(int playerIdx, int round);

    protected abstract void setPlayerTimeout(int frame, int round, int playerIdx);
}