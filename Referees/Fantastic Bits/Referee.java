import java.awt.Dimension;
import java.awt.Point;
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
import java.util.Iterator;
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

    private static boolean WITH_BLUDGERS = true;
    private static boolean WITH_SPELLS = true;

    /**
     *
     * Spells:
     * - Obliviate: Bludgers forget your pod
     * - Accio: an entity is attracted to you
     * - Flipendo: apply a remote force on the enemy
     * - Petrificus: froze an opponent or object
     *
     */

    private static final double EPSILON = 0.00001;
    private static final double MIN_IMPULSE = 100;
    private static int MAX_POWER = 150;
    private static int MAX_POWER_SNAFFLE = 500;
    private static double FRICTION = 0.25;
    private static double FRICTION_BLUDGER = 0.1;
    private static double FRICTION_SNAFFLE = 0.25;
    private static double BLUDGER_MASS = 8;
    private static double SNAFFLE_MASS = 0.5;
    private static int POD_RADIUS = 400;
    private static int SNAFFLE_RADIUS = 150;
    private static int BLUDGER_RADIUS = 200;
    private static int GOAL_RADIUS = 300;
    private static int GOAL_SIZE = 4000;
    private static final int PODS_PER_PLAYER = 2;
    private static int SPACE_BETWEEN_POD = 3000;
    private static int MIN_SPACE_BETWEEN_SNAFFLES = 1250;
    private static int WIDTH = 16000;
    private static int HEIGHT = 7500;
    private static int POINTS_GOAL = 1;
    private static int TIME_BEFORE_CAPTURE_AGAIN = 3;
    private static int SCORE_TO_WIN;
    private static final Dimension SIZE = new Dimension(WIDTH, HEIGHT);

    //private static int CLOAK_DURATION = 5;
    private static int OBLIVIATE_DURATION = 3;
    private static int ACCIO_DURATION = 6;
    private static int FLIPENDO_DURATION = 3;
    private static int PETRIFICUS_DURATION = 1;

    private static int MAX_MAGIC = 100;
    private static int ACCIO_MAGIC = 20;
    private static int PETRIFICUS_MAGIC = 10;
    private static int OBLIVIATE_MAGIC = 5;
    private static int FLIPENDO_MAGIC = 20;

    private static final Pattern PLAYER_INPUT_MOVE_PATTERN = Pattern
            .compile("MOVE (?<x>-?[0-9]{1,8})\\s+(?<y>-?[0-9]{1,8})\\s+(?<power>([0-9]{1,8}))(?:\\s+(?<message>.+))?");

    private static final Pattern PLAYER_INPUT_THROW_PATTERN = Pattern
            .compile("THROW (?<x>-?[0-9]{1,8})\\s+(?<y>-?[0-9]{1,8})\\s+(?<power>([0-9]{1,8}))(?:\\s+(?<message>.+))?");

    private static final Pattern PLAYER_INPUT_FLIPENDO_PATTERN = Pattern
            .compile("FLIPENDO (?<id>-?[0-9]{1,3})(?:\\s+(?<message>.+))?");

    private static final Pattern PLAYER_INPUT_PETRIFICUS_PATTERN = Pattern
            .compile("PETRIFICUS (?<id>-?[0-9]{1,3})(?:\\s+(?<message>.+))?");

    private static final Pattern PLAYER_INPUT_ACCIO_PATTERN = Pattern
            .compile("ACCIO (?<id>-?[0-9]{1,3})(?:\\s+(?<message>.+))?");

    private static final Pattern PLAYER_INPUT_OBLIVIATE_PATTERN = Pattern
            .compile("OBLIVIATE (?<id>-?[0-9]{1,3})(?:\\s+(?<message>.+))?");

    //private static final Pattern PLAYER_INPUT_CLOAK_PATTERN = Pattern
    //        .compile("CLOAK(?:\\s+(?<message>.+))?");

    public static void main(String... args) throws IOException {
        new Referee(System.in, System.out, System.err);
    }

    public Referee(InputStream is, PrintStream out, PrintStream err) throws IOException {
        super(is, out, err);
    }

    public static int symmetricRound(double x) {
        int s = x < 0 ? -1 : 1;
        return s * (int)Math.round(s * x);
    }

    static class Vector {
        private double x, y;

        public Vector(Point a) {
            this.x = a.getX();
            this.y = a.getY();
        }

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

        private Vector symmetricRound() {
            return new Vector(Referee.symmetricRound(this.x), Referee.symmetricRound(this.y));
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

        public double distance2(Vector v) {
            return (v.x - x) * (v.x - x) + (v.y - y) * (v.y - y);
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
            if (length == 0) return new Vector(0, 0);
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

    enum Side {
        LEFT,
        RIGHT,
        BOTTOM,
        TOP
    }

    static class CollisionSide {
        Side side;
        double timeDist;

        public CollisionSide(Side side, double timeDist) {
            this.side = side;
            this.timeDist = timeDist;
        }
    }

    abstract static class Entity {
        private static int UNIQUE_ENTITY_ID = 0;
        protected int id;
        protected Vector position;
        protected int radius;
        protected String type;

        public Entity(String type, Vector position, int radius) {
            this.id = UNIQUE_ENTITY_ID++;
            this.position = position;
            this.radius = radius;
            this.type = type;
        }

        public boolean collidesWith(Entity other) {
            return this.position.distance2(other.position) <= (this.radius + other.radius) * (this.radius + other.radius);
        }

        public String toPlayerString() {
            return this.id + " " + type + " " + Math.round(position.x) + " " + Math.round(position.y) + " " + 0 + " " + 0 + " " + 0;
        }
    }

    abstract static class DynamicEntity extends Entity {
        public class EntityWayPoint {
            double time;
            Vector position;

            public EntityWayPoint(double time, Vector position) {
                this.time = time;
                this.position = position;
            }
        }

        protected List<EntityWayPoint> wayPoints;
        protected Vector speed;
        protected Vector acceleration;
        protected boolean alive = true; // if not alive, object is not passed to player and shouldn't interact anymore (but it can still move and be passed to view for graphical reasons)

        public DynamicEntity(String type, Vector position, int radius) {
            super(type, position, radius);
            this.speed = new Vector(0, 0);
            this.acceleration = new Vector(0, 0);
            this.wayPoints = new ArrayList<>();
        }

        abstract public void prepareRound();
        abstract public String toViewString();

        @Override
        public String toPlayerString() {
            return this.id + " " + this.type + " " + Math.round(position.x) + " " + Math.round(position.y) + " " + Referee.symmetricRound(speed.x) + " " + Referee.symmetricRound(speed.y) + " " + 0;
        }

        public void step(double step) {
            if (step > 0) {
                this.position = this.position.add(this.speed.mult(step));
            }
        }

        public double getMass() {
            return 1;
        }

        public void reset() {
        }

        public void applyForce(double time, Vector force) {
            applyForce(force);
            addWayPoint(time);
        }

        void applyForce(Vector force) {
            this.speed = this.speed.add(force.mult(1d / getMass()));
        }

        public void addWayPoint(double time) {
            if (!wayPoints.isEmpty()) {
                EntityWayPoint last = wayPoints.get(wayPoints.size() - 1);
                if (last.time == time) {
                    wayPoints.remove(wayPoints.size() - 1);
                }
            }
            wayPoints.add(new EntityWayPoint(time, this.position));
        }

        protected double contactPosition(Vector dv, Vector d, double dmin) {
            double a = dv.lengthSquared();
            double b = d.x * dv.x + d.y * dv.y;
            double c = d.lengthSquared() - dmin * dmin;
            double delta = b * b - a * c;
            if (delta <= 0 || a == 0) {
                return Double.POSITIVE_INFINITY;
            }
            double rd = Math.sqrt(delta);
            return Math.min((-b + rd) / a, (-b - rd) / a);
        }

        public double contactPosition(Entity o) {
            return contactPosition(this.speed, new Vector(o.position, this.position), radius + o.radius);
        }

        public double contactPosition(DynamicEntity o) {
            return contactPosition(this.speed.sub(o.speed), new Vector(o.position, this.position), radius + o.radius);
        }

        public void fixPosition(Vector position) {
            setPosition(position);
            if (!wayPoints.isEmpty()) {
                EntityWayPoint last = wayPoints.get(wayPoints.size() - 1);
                last.position = position;
            }
        }

        private void setPosition(Vector position) {
            this.position = position;
        }


        public void checkEntityCollision(DynamicEntity other, List<Collision> collisions, double beginTime, double min) {
            if (this.collidesWith(other)) {
                collisions.add(new DynamicEntityCollision(beginTime, this, other));
            } else {
                double colision = this.contactPosition(other);
                if (colision <= min && colision > 0) {
                    collisions.add(new DynamicEntityCollision(beginTime + colision, this, other));
                }
            }
        }

        public void checkEntityCollision(Entity other, List<Collision> collisions, double beginTime, double min) {
            if (this.collidesWith(other)) {
                collisions.add(new EntityCollision(beginTime, this, other));
            } else {
                double colision = this.contactPosition(other);
                if (colision <= min && colision > 0) {
                    collisions.add(new EntityCollision(beginTime + colision, this, other));
                }
            }
        }

        public void checkWallCollisions(List<Collision> collisions, double beginTime, double min) {
            if (this.position.x < this.radius) {
                collisions.add(new WallCollision(beginTime, this, Side.LEFT));
            } else if (this.position.x > WIDTH - this.radius) {
                collisions.add(new WallCollision(beginTime, this, Side.RIGHT));
            } else if (this.position.y < this.radius) {
                collisions.add(new WallCollision(beginTime, this, Side.TOP));
            } else if (this.position.y > HEIGHT - this.radius) {
                collisions.add(new WallCollision(beginTime, this, Side.BOTTOM));
            } else {
                CollisionSide collision = this.contactPositionWall();
                if (collision != null && collision.timeDist <= min) {
                    collisions.add(new WallCollision(beginTime + collision.timeDist, this, collision.side));
                }
            }
        }

        public CollisionSide contactPositionWall() {
            CollisionSide res = null;

            if (this.speed.x > 0) {
                double right = (WIDTH - radius - this.position.x) / this.speed.x;
                if (right >= 0) {
                    res = new CollisionSide(Side.RIGHT, right);
                }
            }
            if (this.speed.x < 0) {
                double left = (radius - this.position.x) / this.speed.x;
                if (left >= 0 && (res == null || res.timeDist > left)) {
                    res = new CollisionSide(Side.LEFT, left);
                }
            }
            if (this.speed.y > 0) {
                double bottom = (HEIGHT - radius - this.position.y) / this.speed.y;
                if (bottom >= 0 && (res == null || res.timeDist > bottom)) {
                    res = new CollisionSide(Side.BOTTOM, bottom);
                }
            }
            if (this.speed.y < 0) {
                double top = (radius - this.position.y) / this.speed.y;
                if (top >= 0 && (res == null || res.timeDist > top)) {
                    res = new CollisionSide(Side.TOP, top);
                }
            }

            return res;
        }

        public void hasCollidedWith(DynamicEntity c2) {
        }

        public void endRound() {
            this.position = this.position.symmetricRound();
            this.speed = this.speed.symmetricRound();
        }

    }

    class GoalPost extends Entity {
        public GoalPost(int x, int y) {
            super("GOALPOST", new Vector(x, y), GOAL_RADIUS);
        }
    }

    class Bludger extends DynamicEntity {
        private Pod lastVictim;

        public Bludger(int x, int y) {
            super("BLUDGER", new Vector(x, y), BLUDGER_RADIUS);
        }

        public double getMass() {
            return BLUDGER_MASS;
        }

        public void hasCollidedWith(DynamicEntity c2) {
            if (c2 instanceof Pod) {
                lastVictim = (Pod)c2;
            }
        }

        public void prepareRound() {
            this.acceleration = new Vector(0, 0);
        }

        public void endRound() {
            this.speed = this.speed.mult(1 - FRICTION_BLUDGER);
            super.endRound();
        }

        private boolean canTarget(Pod pod, List<Pod> pods) {
            // Obliviate spell: if one pod of the same team as `pod` sent an obliviate on this bludger, ignore player
            for (Pod p: pods) {
                if (p.obliviateCountDown.getValue() > 0 && p.obliviateTarget == this && p.player.id == pod.player.id) {
                    return false;
                }
            }
            return pod != lastVictim && pod.cloakCountDown.getValue() == 0;
        }

        public void updateAcceleration(List<Pod> pods) {
            Pod closestPod = null;
            for (Pod p: pods) {
                if (canTarget(p, pods) && (closestPod == null || position.distance(p.position) < position.distance(closestPod.position))) {
                    closestPod = p;
                }
            }
            if (closestPod != null) {
                this.acceleration = closestPod.position.sub(this.position).normalize().mult(1000);
            }
        }

        public String toViewString() {
            return id + " " + Math.round(position.x) + " " + Math.round(position.y);
        }

    }

    class Snaffle extends DynamicEntity {
        private boolean captured;
        private List<Snaffle> ignoreCollisionList;

        public Snaffle(int x, int y, int vx, int vy) {
            super("SNAFFLE", new Vector(x, y), SNAFFLE_RADIUS);

            this.speed = new Vector(vx, vy);
            this.captured = false;
            this.ignoreCollisionList = new ArrayList<>();
        }

        @Override
        public double getMass() {
            return SNAFFLE_MASS;
        }

        public void prepareRound() {
            this.acceleration = new Vector(0, 0);
        }

        public void endRound() {
            this.speed = this.speed.mult(1 - FRICTION_SNAFFLE);
            super.endRound();
        }

        public void setCapturedByPod(Pod pod) {
            this.captured = true;

            this.position = pod.position;
            this.speed = pod.speed;
        }

        public void scored() {
            this.alive = false;
        }

        public boolean canBeCapturedByPod(Pod p) {
            return this.alive && p.timeBeforeCanCaptureAgain <= 0 && this.captured == false;
        }

        @Override
        public CollisionSide contactPositionWall() {
            CollisionSide res = null;

            if (this.speed.x > 0) {
                double right = (WIDTH - radius - position.x) / speed.x;
                double collisionY = position.y + right * speed.y;

                if (right >= 0 && (collisionY < (HEIGHT - GOAL_SIZE) / 2 || collisionY > (HEIGHT + GOAL_SIZE) / 2)) {
                    res = new CollisionSide(Side.RIGHT, right);
                }
            } else if (this.speed.x < 0) {
                double left = (radius - this.position.x) / this.speed.x;
                double collisionY = position.y + left * speed.y;
                if (left >= 0 && (collisionY < (HEIGHT - GOAL_SIZE) / 2 || collisionY > (HEIGHT + GOAL_SIZE) / 2) && (res == null || res.timeDist > left)) {
                    res = new CollisionSide(Side.LEFT, left);
                }
            }

            if (this.speed.y > 0) {
                double bottom = (HEIGHT - radius - this.position.y) / this.speed.y;
                if (bottom >= 0 && (res == null || res.timeDist > bottom)) {
                    res = new CollisionSide(Side.BOTTOM, bottom);
                }
            } else if (this.speed.y < 0) {
                double top = (radius - this.position.y) / this.speed.y;
                if (top >= 0 && (res == null || res.timeDist > top)) {
                    res = new CollisionSide(Side.TOP, top);
                }
            }

            return res;
        }

        private boolean inGoal() {
            return (this.position.x <= this.radius || this.position.x >= WIDTH - this.radius)
                    && this.position.y >= (HEIGHT - GOAL_SIZE) / 2 && this.position.y <= (HEIGHT + GOAL_SIZE) / 2;
        }

        @Override
        public void checkWallCollisions(List<Collision> collisions, double beginTime, double min) {
            if (!inGoal()) {
                super.checkWallCollisions(collisions, beginTime, min);
            }
        }

        public String toViewString() {
            return id + " " + Math.round(position.x) + " " + Math.round(position.y) + " " + (alive ? 1 : 0);
        }

        public void checkGoalCollisions(List<Collision> collisions, double beginTime, double min) {
            if (this.position.x >= WIDTH) {
                collisions.add(new SnaffleScoreCollision(this, beginTime, players[0]));
            } else if (this.position.x <= 0) {
                collisions.add(new SnaffleScoreCollision(this, beginTime, players[1]));
            } else {
                if (Math.abs(this.speed.x) > 0) {
                    double collisionRight = (WIDTH - this.position.x) / this.speed.x;
                    double collisionLeft = -this.position.x / this.speed.x;

                    if (collisionRight <= min && collisionRight > 0) {
                        collisions.add(new SnaffleScoreCollision(this, beginTime + collisionRight, players[0]));
                    } else if (collisionLeft <= min && collisionLeft > 0) {
                        collisions.add(new SnaffleScoreCollision(this, beginTime + collisionLeft, players[1]));
                    }
                }
            }
        }

        public void ignoreCollisionWith(Snaffle snaffle) {
            this.ignoreCollisionList.add(snaffle);
        }
    }

    public enum Action {
        MOVE, ACCIO, OBLIVIATE, PETRIFICUS, FLIPENDO, IDLE, THROW
    }

    static class CountDown {
        private int value;

        public CountDown() {
            this.value = 0;
        }

        public void dec() {
            if (value > 0) {
                value--;
            }
        }

        public void setValue(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    class Pod extends DynamicEntity {
        private Action lastAction;
        private int timeBeforeCanCaptureAgain;
        private Snaffle snaffle;
        private Point targetPosition;

        private Player player;
        private String message;
        public int power;
        public int throwPower;

        // Cloak spell
        public CountDown cloakCountDown = new CountDown();
        // Obliviate spell
        public CountDown obliviateCountDown = new CountDown();
        public Bludger obliviateTarget;
        // Accio spell
        public CountDown accioCountDown = new CountDown();
        public DynamicEntity accioTarget;
        // Flipendo spell
        public CountDown flipendoCountDown = new CountDown();
        public DynamicEntity flipendoTarget;
        // Petrificus spell
        public CountDown petrificusCountDown = new CountDown();
        public DynamicEntity petrificusTarget;

        public boolean doAccio = false;
        public boolean doObliviate = false;
        public boolean doFlipendo = false;
        public boolean doPetrificus = false;

        public DynamicEntity spellTarget;

        private Boolean spellSuccess;

        public Pod(Vector position, Player player, Point startPoint) {
            super("POD", position, POD_RADIUS);
            this.targetPosition = new Point(startPoint);
            this.player = player;
            this.timeBeforeCanCaptureAgain = 0;
        }

        public String toPlayerString(boolean myPlayer) {
            long positionX = (myPlayer || cloakCountDown.getValue() == 0) ? Math.round(position.x) : 0;
            long positionY = (myPlayer || cloakCountDown.getValue() == 0) ? Math.round(position.y) : 0;
            String type = myPlayer ? "WIZARD" : "OPPONENT_WIZARD";
            return this.id + " " + type + " " + positionX + " " + positionY + " " + Referee.symmetricRound(speed.x) + " " + Referee.symmetricRound(speed.y) + " " + ((this.snaffle != null) ? 1 : 0);
        }

        public String toViewString() {
            StringBuilder pods = new StringBuilder();
            pods.append(Math.round(position.x) + " " + Math.round(position.y) + " " + Referee.symmetricRound(speed.x) + " " + Referee.symmetricRound(speed.y) + " " + power);
            if (targetPosition != null) {
                pods.append(" " + Math.round(targetPosition.x) + " " + Math.round(targetPosition.y));
            } else if (spellTarget != null) {
                pods.append(" " + Math.round(spellTarget.position.x) + " " + Math.round(spellTarget.position.y));
            } else {
                pods.append(" null null");
            }
            if (message != null) {
                pods.append(" " + message);
            }
            return pods.toString();
        }

        public void reset() {
            wayPoints.clear();
            this.message = null;
            this.targetPosition = null;
            this.power = 0;
            this.throwPower = 0;
            this.spellSuccess = null;
        }

        public void prepareRound() {
            this.acceleration = new Vector(0, 0);

            if (WITH_SPELLS) {
                cloakCountDown.dec();
                obliviateCountDown.dec();
                accioCountDown.dec();
                flipendoCountDown.dec();
                petrificusCountDown.dec();

                if (doAccio) {
                    accioCountDown.setValue(ACCIO_DURATION);
                    accioTarget = spellTarget;
                    addToolTip(this.player.id, "$" + this.player.id + " Accio!");
                    doAccio = false;
                }
                if (doFlipendo) {
                    flipendoCountDown.setValue(FLIPENDO_DURATION);
                    flipendoTarget = spellTarget;
                    addToolTip(this.player.id, "$" + this.player.id + " Flipendo!");
                    doFlipendo = false;
                }
                if (doPetrificus) {
                    petrificusCountDown.setValue(PETRIFICUS_DURATION);
                    petrificusTarget = spellTarget;
                    addToolTip(this.player.id, "$" + this.player.id + " Petrificus!");
                    doPetrificus = false;
                }
                if (doObliviate) {
                    obliviateCountDown.setValue(OBLIVIATE_DURATION);
                    obliviateTarget = (Bludger) spellTarget;
                    addToolTip(this.player.id, "$" + this.player.id + " Obliviate!");
                    doObliviate = false;
                }

                if (accioCountDown.getValue() == 0) {
                    accioTarget = null;
                }
                if (flipendoCountDown.getValue() == 0) {
                    flipendoTarget = null;
                }
                if (petrificusCountDown.getValue() == 0) {
                    petrificusTarget = null;
                }
                if (obliviateCountDown.getValue() == 0) {
                    obliviateTarget = null;
                }
            }
        }

        @Override
        public void endRound() {
            this.speed = this.speed.mult(1 - FRICTION);
            super.endRound();
        }

        public void releaseSnaffle() {
            this.snaffle.captured = false;
            this.snaffle = null;
        }

        public double contactPosition(Snaffle o) {
            return contactPosition(this.speed.sub(o.speed), new Vector(o.position, this.position), radius - 1);
        }

        public void checkCaptureSnaffleCollision(List<Collision> collisions, double beginTime, double min, List<Pod> pods) {
            if (snaffle == null) {
                // Find closest snaffle
                Snaffle closestSnaffle = null;
                double distMin = 0;
                for (Snaffle snaffle: snaffles) {
                    if (snaffle.canBeCapturedByPod(this)) {
                        double dist2 = this.position.distance2(snaffle.position);
                        if (dist2 <= (this.radius - 1) * (this.radius - 1) && (closestSnaffle == null || dist2 < distMin)) {
                           closestSnaffle = snaffle;
                           distMin = dist2;
                        }
                    }
                }

                // Check collision with closest snaffle first
                if (closestSnaffle != null) {
                    collisions.add(new SnaffleCollision(closestSnaffle, beginTime, this, pods));
                }

                // Snaffle collision
                for (Snaffle snaffle : snaffles) {
                    if (snaffle != closestSnaffle && snaffle.canBeCapturedByPod(this)) {
                        double collision = this.contactPosition(snaffle);
                        if (collision <= min && collision > 0) {
                            collisions.add(new SnaffleCollision(snaffle, beginTime + collision, this, pods));
                        }
                    }
                }
            }
        }

        public void applyAccio() {
            if (accioTarget != null) {
                Vector d = position.sub(accioTarget.position);
                double power = Math.min(3000.0 * (1000 * 1000 / d.lengthSquared()), 1000); // power = 3000 / (dist/1000)**2
                Vector force = d.normalize().mult(power);

                accioTarget.applyForce(force);
            }
        }

        public void applyFlipendo() {
            if (flipendoTarget != null && (flipendoTarget.position.x != position.x || flipendoTarget.position.y != position.y)) {
                Vector d = flipendoTarget.position.sub(position);
                double power = Math.min(6000.0 * (1000 * 1000 / d.lengthSquared()), 1000); // power = 6000 / (dist/1000)**2
                Vector force = d.normalize().mult(power);

                flipendoTarget.applyForce(force);
            }
        }

        public void applyPetrificus() {
            if (petrificusTarget != null) {
                petrificusTarget.speed = new Vector(0, 0);
            }
        }

        public void setMessage(String msg) {
            this.message = msg;
            if (this.message != null && this.message.length() > 28) {
                this.message = this.message.substring(0, 26) + "...";
            }
        }
    }

    private class Player {
        private int id;
        private List<Pod> pods;
        private int score;
        private int magic;
        private boolean dead;

        public Player(int id) {
            this.id = id;
            this.score = 0;
            this.magic = 0;
            pods = new ArrayList<>();
        }

        public void addPod(Pod pod) {
            this.pods.add(pod);
        }

        public boolean dead() {
            return this.dead;
        }

        public void setDead(boolean b, int round) {
            this.score = -1;
            this.dead = b;
        }

        public void addScore(int points) {
            if (points != 0 && !this.dead) {
                this.score += points;
                addToolTip(this.id, "$" + this.id + " scored");
            }
        }

        public int getMagic() {
            return magic;
        }

        public void increaseMagic() {
            if (magic < MAX_MAGIC && WITH_SPELLS) {
                magic++;
            }
        }

        public void decreaseMagic(int value) {
            magic -= value;
        }
    }

    private static abstract class Collision implements Comparable<Collision> {
        static int COLLISION_UNIQUE_ID = 0;
        protected int uniqueId;

        protected double time;

        public Collision(double time) {
            this.time = time;
            this.uniqueId = COLLISION_UNIQUE_ID++;
        }

        public abstract boolean react();

        @Override
        public int compareTo(Collision col) {
            return Double.compare(time, col.time);
        }

        public String toString() {
            return this.getClass().getName() + " " + String.valueOf(time);
        }

        public abstract String toViewString();
    }

    private static class SnaffleScoreCollision extends Collision {
        private Snaffle snaffle;
        private Player player;

        public SnaffleScoreCollision(Snaffle snaffle, double time, Player player) {
            super(time);
            this.snaffle = snaffle;
            this.player = player;
        }

        @Override
        public boolean react() {
            this.snaffle.scored();
            this.player.addScore(POINTS_GOAL);

            return true;
        }

        @Override
        public String toViewString() {
            return "SNAFFLESCORE " + uniqueId + " " + this.time + " " + snaffle.id + " " + player.id + " " + Math.round(snaffle.position.x) + " " + Math.round(snaffle.position.y);
        }
    }

    private static class SnaffleCollision extends Collision {
        private Pod c1;
        private Vector c1Position;
        private Vector snafflePosition;
        private Snaffle snaffle;
        private List<Pod> pods;

        public SnaffleCollision(Snaffle snaffle, double time, Pod c1, List<Pod> pods) {
            super(time);
            this.snaffle = snaffle;
            this.c1 = c1;
            this.pods = pods;
        }

        @Override
        public boolean react() {
            if (c1.snaffle != null) {
                return false;
            }

            double distSnaffle2 = snaffle.position.distance2(c1.position) + EPSILON;
            //System.err.println(distSnaffle2);

            for (Pod p: pods) {
                if (p != c1 && p.snaffle == null && distSnaffle2 >= snaffle.position.distance2(p.position)) {
                    return false;
                } else {
                    //System.err.println(snaffle.position.distance2(p.position));
                }
            }

            this.c1Position = c1.position;
            this.snafflePosition = snaffle.position;

            c1.snaffle = snaffle;
            snaffle.setCapturedByPod(c1);

            return true;
        }

        @Override
        public String toViewString() {
            return "SNAFFLE " + uniqueId + " " + this.time + " " + snaffle.id + " " + c1.id + " " + Math.round(c1Position.x) + " " + Math.round(c1Position.y) + " " + Math.round(snafflePosition.x) + " " + Math.round(snafflePosition.y);
        }
    }

    private static class WallCollision extends Collision {
        private DynamicEntity c1;
        private Side side;
        private Vector c1Position;
        private double forceTotal;
        private Vector speed;

        public WallCollision(double time, DynamicEntity a, Side side) {
            super(time);
            this.c1 = a;
            this.side = side;
        }

        @Override
        public boolean react() {
            if (c1 instanceof Snaffle) {
                Snaffle s = (Snaffle) c1;
                if (s.inGoal()) {
                    return true;
                }
            }

            this.c1Position = c1.position;
            this.speed = c1.speed;

            Vector normal;
            switch (side) {
            case BOTTOM:
                normal = new Vector(0, -1);
                forceTotal = 2 * c1.speed.y * c1.getMass();
                break;
            case LEFT:
                normal = new Vector(-1, 0);
                forceTotal = 2 * c1.speed.x * c1.getMass();
                break;
            case RIGHT:
                normal = new Vector(-1, 0);
                forceTotal = 2 * c1.speed.x * c1.getMass();
                break;
            case TOP:
                normal = new Vector(0, -1);
                forceTotal = 2 * c1.speed.y * c1.getMass();
                break;
            default:
                return false;
            }

            Vector impulse = normal.mult(forceTotal);
            c1.applyForce(time, impulse);


            Double error = 0.0;
            if (c1.position.x > WIDTH - c1.radius) {
                error = c1.position.x - (WIDTH - c1.radius);
                c1.fixPosition(c1.position.add(new Vector(-2 * error, 0)));
            }
            if (c1.position.x < c1.radius) {
                error = c1.position.x - c1.radius;
                c1.fixPosition(c1.position.add(new Vector(-2 * error, 0)));
            }
            if (c1.position.y > HEIGHT - c1.radius) {
                error = c1.position.y - (HEIGHT - c1.radius);
                c1.fixPosition(c1.position.add(new Vector(0, -2 * error)));
            }
            if (c1.position.y < c1.radius) {
                error = c1.position.y - c1.radius;
                c1.fixPosition(c1.position.add(new Vector(0, -2 * error)));
            }

            if (c1 instanceof Pod && ((Pod)c1).snaffle != null) {
                Snaffle s = ((Pod)c1).snaffle;
                s.position.x = c1.position.x;
                s.position.y = c1.position.y;
                s.speed.x = c1.speed.x;
                s.speed.y = c1.speed.y;
            }

            return true;
        }

        @Override
        public String toViewString() {
            return "WALL " + uniqueId + " " + this.time + " " + c1.id + " " + Math.round(c1Position.x) + " " + Math.round(c1Position.y) + " " + "0"
                    + " " + Math.abs(forceTotal) + " " + Math.round(this.speed.x) + " " + Math.round(this.speed.y) + " " + side;
        }
    }

    private static class EntityCollision extends Collision {
        protected DynamicEntity c1;
        protected Entity c2;
        protected double forceTotal;
        protected Vector c1Position, c2Position;
        protected Vector speed;

        public EntityCollision(double time, DynamicEntity c1, Entity c2) {
            super(time);
            this.c1 = c1;
            this.c2 = c2;
        }

        public boolean react() {
            this.c1Position = c1.position;
            this.c2Position = c2.position;

            this.speed = c1.speed;

            Vector normal = new Vector(c1.position, c2.position).normalize();
            if (normal.x == 0 && normal.y == 0) {
                normal = new Vector(1, 0);
            }
            Vector relativeVelocity = c1.speed;

            double force = normal.dot(relativeVelocity) * c1.getMass();
            double repulseForce = Math.max(MIN_IMPULSE, force);
            this.forceTotal = force + repulseForce;

            Vector impulse = normal.mult(-1 * this.forceTotal);
            c1.applyForce(time, impulse);

            double distance = c1.position.distance(c2.position);
            double diff = distance - c1.radius - c2.radius;
            if (diff <= 0) {
                c1.fixPosition(c1.position.add(normal.mult(-(-diff / 2 + EPSILON))));
            }

            if (c1 instanceof Pod && ((Pod)c1).snaffle != null) {
                Snaffle s = ((Pod)c1).snaffle;
                s.position.x = c1.position.x;
                s.position.y = c1.position.y;
                s.speed.x = c1.speed.x;
                s.speed.y = c1.speed.y;
            }

            return true;
        }

        public String toViewString() {
            return "ENTITY " + uniqueId + " " + this.time + " " + c1.id + " " + Math.round(c1Position.x) + " " + Math.round(c1Position.y) + " "
                    + c2.id + " " + Math.round(c2Position.x) + " " + Math.round(c2Position.y) + " " + forceTotal + " "
                    + Math.round(this.speed.x) + " " + Math.round(this.speed.y);
        }
    }

    private static class DynamicEntityCollision extends EntityCollision {
        public DynamicEntityCollision(double time, DynamicEntity c1, DynamicEntity c2) {
            super(time, c1, c2);
        }

        @Override
        public boolean react() {
            DynamicEntity c2 = (DynamicEntity) this.c2;

            this.c1Position = c1.position;
            this.c2Position = c2.position;

            this.speed = c1.speed.add(c2.speed).mult(0.5);

            Vector normal = new Vector(c1.position, c2.position).normalize();
            if (normal.x == 0 && normal.y == 0) {
                normal = new Vector(1, 0);
            }
            Vector relativeVelocity = c1.speed.sub(c2.speed);

            double force = normal.dot(relativeVelocity) / (1 / c1.getMass() + 1 / c2.getMass());
            double repulseForce = Math.max(MIN_IMPULSE, force);
            this.forceTotal = force + repulseForce;

            Vector impulse = normal.mult(-1 * this.forceTotal);
            c1.applyForce(time, impulse);
            c2.applyForce(time, impulse.mult(-1));

            double distance = c1.position.distance(c2.position);
            double diff = distance - c1.radius - c2.radius;
            if (diff <= 0) {
                c1.fixPosition(c1.position.add(normal.mult(-(-diff / 2 + EPSILON))));
                c2.fixPosition(c2.position.add(normal.mult(-diff / 2 + EPSILON)));
            }

            c1.hasCollidedWith(c2);
            c2.hasCollidedWith(c1);

            if (c1 instanceof Pod && ((Pod)c1).snaffle != null) {
                Snaffle s = ((Pod)c1).snaffle;
                s.position.x = c1.position.x;
                s.position.y = c1.position.y;
                s.speed.x = c1.speed.x;
                s.speed.y = c1.speed.y;
            }

            if (c2 instanceof Pod && ((Pod)c2).snaffle != null) {
                Snaffle s = ((Pod)c2).snaffle;
                s.position.x = c2.position.x;
                s.position.y = c2.position.y;
                s.speed.x = c2.speed.x;
                s.speed.y = c2.speed.y;
            }


            return true;
        }

    }

    private List<GoalPost> goalPosts;
    private List<Bludger> bludgers;
    private List<Snaffle> snaffles;
    private Pod[] pods;
    private Player[] players;
    private long seed;
    private List<Collision> collisions;
    private Random random;

    @Override
    protected void initReferee(int playerCount, Properties prop) throws InvalidFormatException {
        this.seed = Long.valueOf(prop.getProperty("seed", String.valueOf(new Random(System.currentTimeMillis()).nextLong())));

        FRICTION = Double.valueOf(prop.getProperty("friction", String.valueOf(FRICTION)));
        FRICTION_BLUDGER = Double.valueOf(prop.getProperty("friction_bludger", String.valueOf(FRICTION_BLUDGER)));
        FRICTION_SNAFFLE = Double.valueOf(prop.getProperty("friction_snaffle", String.valueOf(FRICTION_SNAFFLE)));
        MAX_POWER = Integer.valueOf(prop.getProperty("max_power", String.valueOf(MAX_POWER)));
        MAX_POWER_SNAFFLE = Integer.valueOf(prop.getProperty("max_power_snaffle", String.valueOf(MAX_POWER_SNAFFLE)));

        SNAFFLE_MASS = Double.valueOf(prop.getProperty("snaffle_mass", String.valueOf(SNAFFLE_MASS)));
        BLUDGER_MASS = Double.valueOf(prop.getProperty("bludger_mass", String.valueOf(BLUDGER_MASS)));

        POD_RADIUS = Integer.valueOf(prop.getProperty("pod_radius", String.valueOf(POD_RADIUS)));
        SNAFFLE_RADIUS = Integer.valueOf(prop.getProperty("snaffle_radius", String.valueOf(SNAFFLE_RADIUS)));
        BLUDGER_RADIUS = Integer.valueOf(prop.getProperty("bludger_radius", String.valueOf(BLUDGER_RADIUS)));
        SPACE_BETWEEN_POD = Integer.valueOf(prop.getProperty("space_between_pod", String.valueOf(SPACE_BETWEEN_POD)));

        GOAL_RADIUS = Integer.valueOf(prop.getProperty("goal_radius", String.valueOf(GOAL_RADIUS)));
        GOAL_SIZE = Integer.valueOf(prop.getProperty("goal_size", String.valueOf(GOAL_SIZE)));

        OBLIVIATE_DURATION = Integer.valueOf(prop.getProperty("obliviate_duration", String.valueOf(OBLIVIATE_DURATION)));
        ACCIO_DURATION = Integer.valueOf(prop.getProperty("accio_duration", String.valueOf(ACCIO_DURATION)));
        FLIPENDO_DURATION = Integer.valueOf(prop.getProperty("flipendo_duration", String.valueOf(FLIPENDO_DURATION)));
        PETRIFICUS_DURATION = Integer.valueOf(prop.getProperty("petrificus_duration", String.valueOf(PETRIFICUS_DURATION)));

        WITH_BLUDGERS = Boolean.valueOf(prop.getProperty("with_bludgers", String.valueOf(WITH_BLUDGERS)));
        WITH_SPELLS = Boolean.valueOf(prop.getProperty("with_spells", String.valueOf(WITH_SPELLS)));

        collisions = new ArrayList<>();

        this.random = new Random(seed);
        generatePlayers(playerCount);
        generateSnaffles();
        generateBludgers();
        generateGoals();
    }

    private void generateGoals() {
        this.goalPosts = new ArrayList<>();
        goalPosts.add(new GoalPost(0, (HEIGHT - GOAL_SIZE) / 2));
        goalPosts.add(new GoalPost(0, (HEIGHT + GOAL_SIZE) / 2));
        goalPosts.add(new GoalPost(WIDTH, (HEIGHT - GOAL_SIZE) / 2));
        goalPosts.add(new GoalPost(WIDTH, (HEIGHT + GOAL_SIZE) / 2));
    }

    private void generatePlayers(int playerCount) {
        Point startPoint = new Point(WIDTH / 2, HEIGHT / 2);
        this.players = new Player[playerCount];
        for (int i = 0; i < playerCount; ++i) {
            this.players[i] = new Player(i);
        }

        List<Pod> pods = new ArrayList<>();
        for (int j = 0; j < playerCount; j++) {
            Player player = this.players[j];
            for (int i = 0; i < PODS_PER_PLAYER; ++i) {
                Vector pos = new Vector(j * (WIDTH - 2000) + 1000, HEIGHT / 2 + ((j % 2 == 0) ? -1 : 1) * (SPACE_BETWEEN_POD * i - (SPACE_BETWEEN_POD * (PODS_PER_PLAYER - 1)) / 2));
                Pod pod = new Pod(pos, player, startPoint);
                player.addPod(pod);
                pods.add(pod);
            }
        }
        this.pods = pods.toArray(new Pod[pods.size()]);
    }

    private void generateSnaffles() {
        this.snaffles = new ArrayList<>();

        int nbSnaffles = 2 + random.nextInt(2);
        SCORE_TO_WIN = nbSnaffles + 1;

        int i = 0;
        while (i < nbSnaffles) {
            int x = random.nextInt(WIDTH / 2 - 3000) + 2000;
            int y = random.nextInt(HEIGHT - 1000) + 500;

            Vector position = new Vector(x, y);

            boolean valid = true;
            for (Snaffle f : snaffles) {
                if (position.distance(f.position) < MIN_SPACE_BETWEEN_SNAFFLES) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                Snaffle snaffle = new Snaffle(x, y, 0, 0);
                this.snaffles.add(snaffle);
                this.snaffles.add(new Snaffle(WIDTH - x, HEIGHT - y, 0, 0));
                i++;
            }
        }

        this.snaffles.add(new Snaffle(WIDTH / 2, HEIGHT / 2, 0, 0));
    }

    private void generateBludgers() {
        this.bludgers = new ArrayList<>();
        if (WITH_BLUDGERS) {
            this.bludgers.add(new Bludger(WIDTH / 2 - SNAFFLE_RADIUS - 2 * BLUDGER_RADIUS, HEIGHT / 2));
            this.bludgers.add(new Bludger(WIDTH / 2 + SNAFFLE_RADIUS + 2 * BLUDGER_RADIUS, HEIGHT / 2));
        }
    }

    @Override
    protected int getMaxRoundCount(int playerCount) {
        return 200;
    }

    @Override
    protected Properties getConfiguration() {
        Properties prop = new Properties();
        prop.setProperty("seed", String.valueOf(this.seed));
        return prop;
    }

    @Override
    protected void populateMessages(Properties p) {
        p.put("endReached", "End reached");
        p.put("podActionMove", "$%d: Wizard %d moves towards coordinate (%d, %d) with thrust %d");
        p.put("podActionThrow", "$%d: Wizard %d throws the snaffle to coordinate (%d, %d) with power %d");
        p.put("podActionAccio", "$%d: Wizard %d casts Accio on entity %d");
        p.put("podActionFlipendo", "$%d: Wizard %d casts Flipendo on entity %d");
        p.put("podActionPetrificus", "$%d: Wizard %d casts Petrificus on entity %d");
        p.put("podActionObliviate", "$%d: Wizard %d casts Obliviate on bludger %d");
    }

    @Override
    protected String[] getInitDataForView() {
        List<String> list = new ArrayList<>();
        list.add(SIZE.width + " " + SIZE.height + " " + POD_RADIUS + " " + (this.players.length * PODS_PER_PLAYER) + " " + SNAFFLE_RADIUS + " " + GOAL_SIZE);

        StringBuilder pods = new StringBuilder();
        for (Pod p : this.pods) {
            if (p != this.pods[0]) {
                pods.append(" ");
            }
            pods.append(p.player.id + " " + Math.round(p.position.x) + " " + Math.round(p.position.y));
        }
        list.add(pods.toString());

        StringBuilder snafflesB = new StringBuilder();
        for (Snaffle f : this.snaffles) {
            if (f != this.snaffles.get(0)) {
                snafflesB.append(" ");
            }
            snafflesB.append(f.id + " " + Math.round(f.position.x) + " " + Math.round(f.position.y));
        }
        list.add(snafflesB.toString());

        StringBuilder bludgerB = new StringBuilder();
        for (Bludger f : this.bludgers) {
            if (f != this.bludgers.get(0)) {
                bludgerB.append(" ");
            }
            bludgerB.append(f.id + " " + Math.round(f.position.x) + " " + Math.round(f.position.y));
        }
        list.add(bludgerB.toString());

        return list.toArray(new String[list.size()]);
    }

    @Override
    protected String[] getFrameDataForView(int round, int frame, boolean keyFrame) {
        if (keyFrame) {
            List<String> list = new ArrayList<>();
            for (Pod p : this.pods) {
                list.addAll(Arrays.asList(p.toViewString()));
            }

            List<String> spells = new ArrayList<>();
            for (Pod p : this.pods) {
                if (p.accioCountDown.getValue() > 0) {
                    spells.add("ACCIO " + p.id + " " + p.accioTarget.id + " " + p.accioCountDown.getValue() + " " + (p.accioCountDown.getValue() == ACCIO_DURATION));
                }
                if (p.flipendoCountDown.getValue() > 0) {
                    spells.add("FLIPENDO " + p.id + " " + p.flipendoTarget.id + " " + p.flipendoCountDown.getValue() + " " + (p.flipendoCountDown.getValue() == FLIPENDO_DURATION));
                }
                if (p.obliviateCountDown.getValue() > 0) {
                    spells.add("OBLIVIATE " + p.id + " " + p.obliviateTarget.id + " " + p.obliviateCountDown.getValue() + " " + (p.obliviateCountDown.getValue() == OBLIVIATE_DURATION));
                }
                if (p.petrificusCountDown.getValue() > 0) {
                    spells.add("PETRIFICUS " + p.id + " " + p.petrificusTarget.id + " " + p.petrificusCountDown.getValue() + " " + (p.petrificusCountDown.getValue() == PETRIFICUS_DURATION));
                }
            }
            list.add("" + spells.size());
            list.addAll(spells);

            list.add("" + this.snaffles.size());
            for (Snaffle g : this.snaffles) {
                list.add(g.toViewString());
            }
            list.add("" + this.bludgers.size());
            for (Bludger g : this.bludgers) {
                list.add(g.toViewString());
            }

            StringBuilder scores = new StringBuilder();
            for (Player p : players) {
                if (p != players[0]) {
                    scores.append(" ");
                }
                scores.append(p.score);
            }
            list.add(scores.toString());

            StringBuilder magic = new StringBuilder();
            for (Player p: players) {
                if (p != players[0]) {
                    magic.append(" ");
                }
                magic.append(p.getMagic());
            }
            list.add(magic.toString());

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
        return PODS_PER_PLAYER;
    }

    @Override
    protected String getGameName() {
        return "Fantastic Bits";
    }

    private Snaffle findSnaffleById(int id) {
        for (Snaffle snaffle: snaffles) {
            if (snaffle.alive && snaffle.id == id) {
                return snaffle;
            }
        }
        return null;
    }

    private Bludger findBludgerById(int id) {
        for (Bludger bludger: bludgers) {
            if (bludger.id == id) {
                return bludger;
            }
        }
        return null;
    }

    private Pod findPodById(int id) {
        for (Pod pod: pods) {
            if (pod.id == id) {
                return pod;
            }
        }
        return null;
    }

    private DynamicEntity findEntityById(int id) {
        Snaffle snaffle = findSnaffleById(id);
        if (snaffle != null) {
            return snaffle;
        }
        Bludger bludger = findBludgerById(id);
        if (bludger != null) {
            return bludger;
        }
        Pod pod = findPodById(id);
        if (pod != null) {
            return pod;
        }
        return null;
    }

    private void handlePlayerThrow(Matcher match, Pod pod) throws InvalidInputException {
        if (pod.snaffle != null) {
            int x = Integer.parseInt(match.group("x"));
            int y = Integer.parseInt(match.group("y"));

            String throttle = match.group("power");
            int power = Integer.parseInt(throttle);
            if (power < 0 || power > MAX_POWER_SNAFFLE) {
                throw new InvalidInputException("0 <= throw power <= " + MAX_POWER_SNAFFLE, "power = " + power);
            }
            pod.throwPower = power;

            Snaffle snaffle = pod.snaffle;
            snaffle.position = pod.position;
            pod.targetPosition = new Point(x, y);
            if (snaffle.position.x == x && snaffle.position.y == y) {
                snaffle.acceleration = new Vector(0, 0);
            } else {
                int snafflePower = power;
                Vector direction = new Vector(snaffle.position, new Vector(x, y)).normalize();
                snaffle.acceleration = direction.normalize().mult(snafflePower);
            }
            pod.lastAction = Action.THROW;
            pod.setMessage(match.group("message"));
        } else {
            throw new InvalidInputException("Impossible to throw a snaffle without holding one.", "id = " + pod.id);
        }
    }

    private void handlePlayerMove(Matcher match, Pod pod) throws InvalidInputException {
        int x = Integer.parseInt(match.group("x"));
        int y = Integer.parseInt(match.group("y"));

        String throttle = match.group("power");
        int power = Integer.parseInt(throttle);
        if (power < 0 || power > MAX_POWER) {
            throw new InvalidInputException("0 <= move power <= " + MAX_POWER, "power = " + power);
        }

        pod.targetPosition = new Point(x, y);
        pod.power = power;
        if (pod.position.x == x && pod.position.y == y) {
            pod.acceleration = new Vector(0, 0);
        } else {
            Vector direction = new Vector(pod.position, new Vector(x, y)).normalize();
            pod.acceleration = direction.normalize().mult(power);
        }

        pod.lastAction = Action.MOVE;
        pod.setMessage(match.group("message"));
    }

    private void handlePlayerObliviate(Matcher match, Pod pod) throws InvalidInputException {
        int id = Integer.valueOf(match.group("id"));
        DynamicEntity entity = findEntityById(id);
        if (entity == null) {
            throw new InvalidInputException("Target not found", "id = " + id);
        }
        if (!(entity instanceof Bludger)) {
            throw new InvalidInputException("Target is not a bludger", "id = " + id);
        }
        if (pod.player.getMagic() >= OBLIVIATE_MAGIC && WITH_SPELLS) {
            pod.player.decreaseMagic(OBLIVIATE_MAGIC);
            pod.doObliviate = true;
            pod.spellSuccess = true;
        } else {
            pod.spellSuccess = false;
        }
        pod.spellTarget = entity;
        pod.lastAction = Action.OBLIVIATE;
        pod.setMessage(match.group("message"));
    }

    private void handlePlayerAccio(Matcher match, Pod pod) throws InvalidInputException {
        int id = Integer.valueOf(match.group("id"));
        DynamicEntity entity = findEntityById(id);
        if (entity == null || entity.alive == false) {
            throw new InvalidInputException("Target not found", "id = " + id);
        }
        if (entity instanceof Pod) {
            throw new InvalidInputException("Target can't be a player", "id = " + id);
        }

        if (pod.player.getMagic() >= ACCIO_MAGIC && WITH_SPELLS) {
            pod.player.decreaseMagic(ACCIO_MAGIC);
            pod.doAccio = true;
            pod.spellSuccess = true;
        } else {
            pod.spellSuccess = false;
        }

        pod.spellTarget = entity;
        pod.lastAction = Action.ACCIO;
        pod.setMessage(match.group("message"));
    }

    private void handlePlayerFlipendo(Matcher match, Pod pod) throws InvalidInputException {
        int id = Integer.valueOf(match.group("id"));
        DynamicEntity entity = findEntityById(id);
        if (entity == null || entity.alive == false) {
            throw new InvalidInputException("Target not found", "id = " + id);
        }
        if (entity instanceof Pod && ((Pod)entity).player.id == pod.player.id) {
            throw new InvalidInputException("Target can't be yourself", "id = " + id);
        }

        if (pod.player.getMagic() >= FLIPENDO_MAGIC && WITH_SPELLS) {
            pod.player.decreaseMagic(FLIPENDO_MAGIC);
            pod.doFlipendo = true;
            pod.spellSuccess = true;
        } else {
            pod.spellSuccess = false;
        }

        pod.spellTarget = entity;
        pod.lastAction = Action.FLIPENDO;
        pod.setMessage(match.group("message"));
    }

    private void handlePlayerPetrificus(Matcher match, Pod pod) throws InvalidInputException {
        int id = Integer.valueOf(match.group("id"));
        DynamicEntity entity = findEntityById(id);
        if (entity == null || entity.alive == false) {
            throw new InvalidInputException("Target not found", "id = " + id);
        }
        if (entity instanceof Pod && ((Pod)entity).player.id == pod.player.id) {
            throw new InvalidInputException("Target can't be yourself", "id = " + id);
        }

        if (pod.player.getMagic() >= PETRIFICUS_MAGIC && WITH_SPELLS) {
            pod.player.decreaseMagic(PETRIFICUS_MAGIC);
            pod.doPetrificus = true;
            pod.spellSuccess = true;
        } else {
            pod.spellSuccess = false;
        }

        pod.spellTarget = entity;
        pod.lastAction = Action.PETRIFICUS;
        pod.setMessage(match.group("message"));
    }

    @Override
    protected void handlePlayerOutput(int frame, int round, int playerIdx, String[] outputs)
            throws WinException, LostException, InvalidInputException {
        int i = 0;
        Player player = this.players[playerIdx];
        for (String line : outputs) {
            Pod pod = player.pods.get(i);
            Matcher matchMove = PLAYER_INPUT_MOVE_PATTERN.matcher(line);
            Matcher matchThrow = PLAYER_INPUT_THROW_PATTERN.matcher(line);
            //Matcher matchCloak = PLAYER_INPUT_CLOAK_PATTERN.matcher(line);
            Matcher matchAccio = PLAYER_INPUT_ACCIO_PATTERN.matcher(line);
            Matcher matchFlipendo = PLAYER_INPUT_FLIPENDO_PATTERN.matcher(line);
            Matcher matchObliviate = PLAYER_INPUT_OBLIVIATE_PATTERN.matcher(line);
            Matcher matchPetrificus = PLAYER_INPUT_PETRIFICUS_PATTERN.matcher(line);
            try {
                pod.lastAction = Action.IDLE;
                pod.targetPosition = null;

                if (matchMove.matches()) {
                    handlePlayerMove(matchMove, pod);
                //} else if (matchCloak.matches()) {
                //    handlePlayerCloak(matchCloak, frame, round, player, pod, outputs);
                } else if (matchThrow.matches()) {
                    handlePlayerThrow(matchThrow, pod);
                } else if (matchAccio.matches()) {
                    handlePlayerAccio(matchAccio, pod);
                } else if (matchObliviate.matches()) {
                    handlePlayerObliviate(matchObliviate, pod);
                } else if (matchFlipendo.matches()) {
                    handlePlayerFlipendo(matchFlipendo, pod);
                } else if (matchPetrificus.matches()) {
                    handlePlayerPetrificus(matchPetrificus, pod);
                } else {
                    throw new InvalidInputException("MOVE x y thrust | THROW x y power | FLIPENDO id | ACCIO id | OBLIVIATE id | PETRIFICUS id", line);
                }

                if (pod.snaffle != null) {
                    Snaffle snaffle = pod.snaffle;
                    snaffle.speed = pod.speed;
                    snaffle.position = pod.position;
                    pod.timeBeforeCanCaptureAgain = TIME_BEFORE_CAPTURE_AGAIN;
                }
            } catch (InvalidInputException e) {
                player.setDead(true, round);
                throw e;
            }
            ++i;
        }
    }


    @Override
    protected String[] getInitInputForPlayer(int playerIdx) {
        List<String> data = new ArrayList<>();
        data.add(String.valueOf(playerIdx));
        return data.toArray(new String[data.size()]);
    }

    @Override
    protected String[] getInputForPlayer(int round, int playerIdx) {
        List<String> data = new ArrayList<>();
        Player player = this.players[playerIdx];

        for (Pod p : this.pods) {
            if (p.player == player) {
                data.add(p.toPlayerString(true));
            }
        }

        for (Pod p : this.pods) {
            if (p.player != player) {
                data.add(p.toPlayerString(false));
            }
        }

        for (Snaffle snaffle : this.snaffles) {
            if (snaffle.alive) {
                data.add(snaffle.toPlayerString());
            }
        }
        for (Bludger bludger : this.bludgers) {
            data.add(bludger.toPlayerString());
        }

        data.add(0, data.size() + "");

        return data.toArray(new String[data.size()]);
    }

    @Override
    protected String getHeadlineAtGameStartForConsole() {
        return null;
    }

    @Override
    protected int getMinimumPlayerCount() {
        return 2;
    }

    @Override
    protected boolean showTooltips() {
        return true;
    }

    @Override
    protected void prepare(int round) {
        for (Player p : players) {
            for (Pod pod : p.pods) {
                pod.reset();
                pod.prepareRound();
            }
        }
        for (Snaffle f : snaffles) {
            f.reset();
            f.prepareRound();
        }
        for (Bludger b : bludgers) {
            b.reset();
            b.prepareRound();
        }
        this.collisions.clear();
    }

    @Override
    protected void updateGame(int round) throws GameOverException {
        List<Pod> pods = new ArrayList<>();

        for (Player p : players) {
            pods.addAll(p.pods);
        }
        for (Snaffle snaffle: snaffles) {
            for (Iterator<Snaffle> iterator = snaffle.ignoreCollisionList.iterator(); iterator.hasNext();) {
                Snaffle snaffle2 = iterator.next();
                if (!snaffle2.collidesWith(snaffle)) {
                    iterator.remove();
                }
            }
        }
        for (Pod p : pods) {
            p.applyForce(p.acceleration);
            if (p.snaffle != null) {
                if (p.accioTarget == p.snaffle) {
                    p.accioCountDown.setValue(0);
                    p.accioTarget = null;
                }
                for (Snaffle snaffle: snaffles) {
                    if (snaffle != p.snaffle) {
                        if (p.snaffle.collidesWith(snaffle)) {
                            snaffle.ignoreCollisionWith(p.snaffle);
                            p.snaffle.ignoreCollisionWith(snaffle);
                        }
                    }
                }
                p.releaseSnaffle();
            }
            if (p.accioTarget != null && !p.accioTarget.alive) {
                p.accioCountDown.setValue(0);
                p.accioTarget = null;
            }
            if (p.timeBeforeCanCaptureAgain > 0) {
                p.timeBeforeCanCaptureAgain--;
            }
        }
        int aliveSnaffles = 0;
        for (Snaffle f : snaffles) {
            if (f.alive) {
                aliveSnaffles++;
            }
            f.applyForce(f.acceleration);
        }
        for (Bludger b : bludgers) {
            b.updateAcceleration(pods);
            b.applyForce(b.acceleration);
        }

        // Apply spells (order is important):
        if (WITH_SPELLS) {
            for (Pod pod: pods) {
                pod.applyPetrificus();
            }
            for (Pod pod: pods) {
                pod.applyAccio();
            }
            for (Pod pod: pods) {
                pod.applyFlipendo();
            }
        }

        for (Player player: players) {
            player.increaseMagic();
        }

        double time = 0;
        while (time < 1) {
            double step = step(pods, time);
            time += step;
        }

        for (Pod p : pods) {
            p.endRound();
        }
        for (Snaffle f : snaffles) {
            f.endRound();
        }
        for (Bludger b : bludgers) {
            b.endRound();
        }

        boolean ended = false;

        if (aliveSnaffles == 0 || (players[0].score >= SCORE_TO_WIN || players[1].score >= SCORE_TO_WIN) || players[0].dead() || players[1].dead()) {
            ended = true;
        }

        if (ended) {
            throw new GameOverException("endReached");
        }
    }

    private void stepAllEntities(double step) {
        for (Pod ch : pods) {
            ch.step(step);
        }
        for (Snaffle f: snaffles) {
            f.step(step);
        }
        for (Bludger b: bludgers) {
            b.step(step);
        }
    }

    private double step(List<Pod> pods, double beginTime) {
        List<Collision> collisions = new ArrayList<>();
        double min = 1 - beginTime;

        // physics loop with walls and other pods
        for (int i = pods.size() - 1; i >= 0; --i) {
            Pod pod = pods.get(i);

            pod.checkWallCollisions(collisions, beginTime, min);

            for (GoalPost goalPost: goalPosts) {
                pod.checkEntityCollision(goalPost, collisions, beginTime, min);
            }

            // Collision with other pods
            for (int j = i - 1; j >= 0; --j) {
                Pod pod2 = pods.get(j);
                pod.checkEntityCollision(pod2, collisions, beginTime, min);
            }

            pod.checkCaptureSnaffleCollision(collisions, beginTime, min, pods);
        }

        for (int i = snaffles.size() - 1; i >= 0; --i) {
            Snaffle snaffle = snaffles.get(i);

            if (snaffle.alive && !snaffle.captured) {
                for (GoalPost goalPost: goalPosts) {
                    snaffle.checkEntityCollision(goalPost, collisions, beginTime, min);
                }

                snaffle.checkWallCollisions(collisions, beginTime, min);

                // Collision with other snaffles
                for (int j = i - 1; j >= 0; --j) {
                    Snaffle snaffle2 = snaffles.get(j);
                    if (!snaffle2.alive || snaffle2.captured || snaffle.ignoreCollisionList.contains(snaffle2)) continue;

                    snaffle.checkEntityCollision(snaffle2, collisions, beginTime, min);
                }

                snaffle.checkGoalCollisions(collisions, beginTime, min);
            }
        }

        for (int i = bludgers.size() - 1; i >= 0; --i) {
            Bludger bludger = bludgers.get(i);
            bludger.checkWallCollisions(collisions, beginTime, min);

            for (GoalPost goalPost: goalPosts) {
                bludger.checkEntityCollision(goalPost, collisions, beginTime, min);
            }

            for (int j = i - 1; j >= 0; --j) {
                Bludger bludger2 = bludgers.get(j);
                bludger.checkEntityCollision(bludger2, collisions, beginTime, min);
            }
            for (Pod pod : pods) {
                bludger.checkEntityCollision(pod, collisions, beginTime, min);
            }
            for (Snaffle snaffle: snaffles) {
                bludger.checkEntityCollision(snaffle, collisions, beginTime, min);
            }
        }

        if (!collisions.isEmpty()) {
            Collections.sort(collisions);
            double time = -1;
            for (Collision c : collisions) {

                if (time <= -1) {
                    time = c.time;
                    double step = time - beginTime;
                    stepAllEntities(step);
                }
                if (c.time <= time + EPSILON) {
                    if (c.react()) {
                        this.collisions.add(c);
                    }
                } else {
                    break;
                }
            }

            return time - beginTime;
        }

        double step = 1 - beginTime;
        stepAllEntities(step);

        return step;
    }

    @Override
    protected String[] getPlayerActions(int playerIdx, int round) {
        List<Pod> pods = this.players[playerIdx].pods;
        List<String> action = new ArrayList<>(pods.size());

        for (Pod p : pods) {
            switch (p.lastAction) {
            case MOVE:
                action.add(translate("podActionMove", p.player.id, p.id, p.targetPosition.x, p.targetPosition.y, p.power));
                break;
            case THROW:
                action.add(translate("podActionThrow", p.player.id, p.id, p.targetPosition.x, p.targetPosition.y, p.throwPower));
                break;
            case ACCIO:
                action.add(translate("podActionAccio", p.player.id, p.id, p.spellTarget.id));
                break;
            case FLIPENDO:
                action.add(translate("podActionFlipendo", p.player.id, p.id, p.spellTarget.id));
                break;
            case OBLIVIATE:
                action.add(translate("podActionObliviate", p.player.id, p.id, p.spellTarget.id));
                break;
            case PETRIFICUS:
                action.add(translate("podActionPetrificus", p.player.id, p.id, p.spellTarget.id));
                break;
            }

        }
        return action.toArray(new String[action.size()]);
    }

    @Override
    protected boolean isPlayerDead(int playerIdx) {
        return this.players[playerIdx].dead();
    }

    @Override
    protected String getDeathReason(int playerIdx) {
        return "Eliminated!";
    }

    @Override
    protected int getScore(int playerIdx) {
        return players[playerIdx].score;
    }

    @Override
    protected String[] getGameSummary(int round) {
        List<String> summary = new ArrayList<>();
        for (Player p : players) {
            String sum = "$" + p.id + " Score: " + p.score;
            if (WITH_SPELLS) {
                sum += " | Magic: " + p.magic;
            }
            summary.add(sum);
            for (Pod pod : p.pods) {
                if (pod.snaffle != null) {
                    summary.add("$" + p.id + " " + pod.id + " caught snaffle " + pod.snaffle.id);
                }
                if (pod.spellSuccess != null) {
                    if (pod.spellSuccess == false) {
                        summary.add("$" + p.id + ": Wizard " + pod.id + " failed his spell!");
                    }
                }
            }
        }
        return summary.toArray(new String[summary.size()]);
    }

    @Override
    protected boolean gameOver() {
        return false;
    }

    @Override
    protected void setPlayerTimeout(int frame, int round, int playerIdx) {
        this.players[playerIdx].setDead(true, round);
    }

}

// ------------------------------------------------------------------------------------------------------------
//------------------------------------------------------------------------------------------------------------

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
        if (playerIdx != 0) throw new RuntimeException("SoloReferee could only handle one-player games");
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
        if (playerIdx != 0) throw new RuntimeException("SoloReferee could only handle one-player games");
        return getInitInputForPlayer();

    }

    @Override
    protected boolean showTooltips() {
        return false;
    }

    @Override
    protected final String[] getInputForPlayer(int round, int playerIdx) {
        if (playerIdx != 0) throw new RuntimeException("SoloReferee could only handle one-player games");
        return getInputForPlayer(round);
    }

    protected abstract int getExpectedOutputLineCountForPlayer();

    @Override
    protected int getExpectedOutputLineCountForPlayer(int playerIdx) {
        if (playerIdx != 0) throw new RuntimeException("SoloReferee could only handle one-player games");
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
        private String[] nextInput;

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

        public String[] getNextInput() {
            return nextInput;
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
            if (s != null) return super.add(s);
            return false;
        }

        public void addAll(String[] data) {
            if (data != null) super.addAll(Arrays.asList(data));
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
        this.messages.put("LostTooltip", "$%d lost: %s");

        populateMessages(this.messages);
        Scanner s = new Scanner(is);
        int i;
        try {
            while (true) {
                String line = s.nextLine();
                Matcher m = HEADER_PATTERN.matcher(line);
                if (!m.matches()) throw new RuntimeException("Error in data sent to referee");
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
                        // ? @author of question mark = julien
                        if (!this.isTurnBasedGame()) {
                            for (PlayerStatus player : this.players) {
                                if (!player.lost) {
                                    player.nextInput = getInputForPlayer(round, player.id);
                                } else {
                                    player.nextInput = null;
                                }
                            }
                        }
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
                        if (e instanceof InvalidInputException) {
                            addToolTip(nextPlayer, translate("InvalidActionTooltip", nextPlayer));
                        } else {
                            addToolTip(nextPlayer, translate("LostTooltip", nextPlayer, translate(e.getReasonCode(), e.values)));
                        }
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
        if (reasonCode == null && playerStatus != null) reasonCode = playerStatus.reasonCode;

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
        if (newRound || isTurnBasedGame()) {
            data.addAll(getFrameDataForView(round, frame, newRound));
        }

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

        if (!tooltips.isEmpty() && (newRound || isTurnBasedGame())) {
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
        if (this.isTurnBasedGame()) {
            this.players[nextPlayer].nextInput = getInputForPlayer(round, nextPlayer);
        }
        data.addAll(this.players[nextPlayer].nextInput);
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
        return 100;
    }

    protected int getMaxRoundCount(int playerCount) {
        return 200;
    }

    private void nextRound() throws GameOverException {
        newRound = true;
        if (++round > 0) {
            updateGame(round);
            updateScores();
        }
        //if (gameOver()) {
        //  throw new GameOverException(null);
        //}
        if (alivePlayerCount < getMinimumPlayerCount()) {
            throw new GameOverException(null);
        }
    }

    abstract protected boolean gameOver();

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
        if (showTooltips()) tooltips.add(new Tooltip(player, message));
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
