import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Referee extends MultiReferee {
    public static int GAME_VERSION = 4;

    public static final int SYMMETRY_MAP_ONLY = 0;
    public static final int SYMMETRY_MAP_AND_ITEMS = 1;
    public static final int SYMMETRY_NONE = 2;
    public static final int DEFAULT_TIME_LIMIT = 200;

    public static final int MOVE_MODE_STUPID = 0;
    public static final int MOVE_MODE_BFS = 1;

    public static final int NO_PROGRESS_TIMEOUT = 20;
    public static final int EXTRA_ROUNDS_AFTER_NO_BOXES = 20;

    public static boolean ITEMS = false;
    public static boolean VISIBLE_ITEMS = true;
    public static boolean FRIENDLY_FIRE = false;
    public static boolean END_ON_NO_BOXES = true;
    public static boolean COUNTDOWN_ON_NO_BOXES = false;
    public static boolean PRACTICE_MODE = true;
    public static int WIDTH = 13;
    public static int HEIGHT = 11;
    public static int DEFAULT_BOMB_POWER = 3;
    public static int DEFAULT_BOMBS_PER_PLAYER = 1;
    public static int BOMB_FUSE = 8;
    public static int SYMMETRY = SYMMETRY_MAP_AND_ITEMS;
    public static int SUDDEN_DEATH = -1;
    public static int SUDDEN_DEATH_SLOWNESS = 10;
    public static int MOVE_MODE = MOVE_MODE_BFS;

    long seed;
    int boxCount;
    Random random;
    Map<Coord, Cell> grid;
    List<PlayerInfo> players;
    List<Bomb> activeBombs;
    List<Item> activeItems;
    Set<Item> removedItems;
    List<Cell> boxes;
    Set<Cell> explodedBoxes;
    List<Man> activeMen;
    List<List<Explosion>> explosionGroups;
    int countDown, noProgressCountDown, extraCountDown;

    @Override
    protected void initReferee(int playerCount, Properties prop) throws InvalidFormatException {
        seed = Long
                .valueOf(prop.getProperty("seed", String.valueOf(new Random(System.currentTimeMillis()).nextLong())));
        random = new Random(this.seed);

        GAME_VERSION = Integer.valueOf(prop.getProperty("GAME_VERSION", String.valueOf(GAME_VERSION)));

        if (GAME_VERSION >= 2) {
            ITEMS = true;
        }
        if (GAME_VERSION >= 3) {
            PRACTICE_MODE = false;
            END_ON_NO_BOXES = false;
        }
        if (GAME_VERSION >= 4) {
            FRIENDLY_FIRE = true;
            COUNTDOWN_ON_NO_BOXES = true;
            //SUDDEN_DEATH = 200;
        }

        grid = new HashMap<>();
        players = new ArrayList<>(playerCount);
        explodedBoxes = new HashSet<Cell>();
        boxes = new ArrayList<Cell>();
        activeMen = new ArrayList<>(playerCount);
        activeBombs = new ArrayList<>(playerCount * DEFAULT_BOMBS_PER_PLAYER);
        activeItems = new ArrayList<>();
        removedItems = new HashSet<>();
        explosionGroups = new ArrayList<>();

        Coord[] startingPositions = { new Coord(0, 0), new Coord(WIDTH - 1, HEIGHT - 1), new Coord(WIDTH - 1, 0),
                new Coord(0, HEIGHT - 1) };
        Coord[] allDirections = { new Coord(0, -1), new Coord(-1, 0), new Coord(0, 1), new Coord(1, 0),
                new Coord(-1, -1), new Coord(-1, 1), new Coord(1, -1), new Coord(1, 1) };

        WIDTH = Integer.valueOf(prop.getProperty("WIDTH", String.valueOf(WIDTH)));
        HEIGHT = Integer.valueOf(prop.getProperty("HEIGHT", String.valueOf(HEIGHT)));
        boxCount = Integer.valueOf(prop.getProperty("boxes", String.valueOf(30 + random.nextInt(36))));
        BOMB_FUSE = Integer.valueOf(prop.getProperty("BOMB_FUSE", String.valueOf(BOMB_FUSE)));
        DEFAULT_BOMB_POWER = Integer
                .valueOf(prop.getProperty("DEFAULT_BOMB_POWER", String.valueOf(DEFAULT_BOMB_POWER)));
        DEFAULT_BOMBS_PER_PLAYER = Integer.valueOf(prop.getProperty("DEFAULT_BOMBS_PER_PLAYER",
                String.valueOf(DEFAULT_BOMBS_PER_PLAYER)));

        countDown = SUDDEN_DEATH;
        noProgressCountDown = NO_PROGRESS_TIMEOUT;
        extraCountDown = EXTRA_ROUNDS_AFTER_NO_BOXES;

        for (int y = 0; y < HEIGHT; ++y) {
            for (int x = 0; x < WIDTH; ++x) {
                int cellType = (x % 2 == 1 && y % 2 == 1) ? Cell.BLOCK : Cell.FLOOR;
                if (PRACTICE_MODE) {
                    cellType = Cell.FLOOR;
                }
                Cell cell = new Cell(x, y, cellType);
                grid.put(new Coord(cell), cell);
            }
        }

        if (PRACTICE_MODE) {
            boxCount = 30;
        }

        Map<Integer, Integer> toDistribute = new LinkedHashMap<>();
        toDistribute.put(Item.BOMB_UP, 0);
        toDistribute.put(Item.FIRE_UP, 0);
        if (ITEMS) {
            toDistribute.put(Item.BOMB_UP, boxCount / 3);
            toDistribute.put(Item.FIRE_UP, boxCount / 3);
        }

        int iterations = 0;
        int boxId = 0;
        while (boxId < boxCount) {
            iterations++;

            if (iterations > 1000) {
                break;
            }

            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT);

            boolean ok = true;
            for (int i = 0; i < startingPositions.length; ++i) {
                if (startingPositions[i].manhattanTo(x, y) < 2) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            Cell cell = grid.get(new Coord(x, y));
            if (cell.type != Cell.FLOOR) {
                continue;
            }
            if (PRACTICE_MODE) {
                boolean nextToBox = false;
                for (Coord d : allDirections) {
                    Cell neighb = grid.get(new Coord(x + d.x, y + d.y));
                    if (neighb != null && neighb.isBox()) {
                        nextToBox = true;
                        break;
                    }
                }
                if (nextToBox) {
                    continue;
                }
            }

            cell.type = Cell.BOX;

            Integer itemType = null;
            for (Entry<Integer, Integer> distribution : toDistribute.entrySet()) {
                if (distribution.getValue() > 0) {
                    itemType = distribution.getKey();
                }
            }
            if (itemType != null) {
                toDistribute.put(itemType, toDistribute.get(itemType) - 1);
                cell.placeItem(Item.instantiate(itemType));
            }

            cell.boxId = boxId++;
            boxes.add(cell);
            if (SYMMETRY != SYMMETRY_NONE) {
                Cell[] otherCells = { grid.get(new Coord(WIDTH - x - 1, y)),
                        grid.get(new Coord(WIDTH - x - 1, HEIGHT - y - 1)), grid.get(new Coord(x, HEIGHT - y - 1)) };
                for (Cell c : otherCells) {
                    if (c.type != Cell.BOX) {
                        c.type = Cell.BOX;
                        if (SYMMETRY == SYMMETRY_MAP_AND_ITEMS && cell.hasItem()) {
                            c.placeItem(cell.item.copy());
                            toDistribute.put(itemType, toDistribute.get(itemType) - 1);
                        }
                        c.boxId = boxId++;
                        boxes.add(c);
                    }
                }
            }
        }
        boxCount = boxId;

        for (int i = 0; i < playerCount; ++i) {
            PlayerInfo player = new PlayerInfo(i, startingPositions[i], DEFAULT_BOMBS_PER_PLAYER, DEFAULT_BOMB_POWER);
            players.add(player);
            activeMen.add(player.man);
        }

    }

    public static void main(String... args) throws IOException {
        new Referee(System.in, System.out, System.err);
    }

    public Referee(InputStream is, PrintStream out, PrintStream err) throws IOException {
        super(is, out, err);
    }

    @Override
    protected Properties getConfiguration() {
        Properties prop = new Properties();
        prop.setProperty("seed", String.valueOf(seed));
        prop.setProperty("boxes", String.valueOf(boxCount));
        return prop;
    }

    @Override
    protected void prepare(int round) {
        for (PlayerInfo player : players) {
            player.man.reset();
        }
    }

    @Override
    protected String[] getInitInputForPlayer(int playerIdx) {
        List<String> lines = new ArrayList<>();
        lines.add(WIDTH + " " + HEIGHT + " " + playerIdx);
        return lines.toArray(new String[lines.size()]);
    }

    @Override
    protected String[] getInputForPlayer(int round, int playerIdx) {
        List<String> lines = new ArrayList<>();
        for (int y = 0; y < HEIGHT; ++y) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < WIDTH; ++x) {
                Cell c = grid.get(new Coord(x, y));
                row.append(c.toPlayerString(VISIBLE_ITEMS));
            }
            lines.add(row.toString());
        }

        List<String> entities = new LinkedList<>();
        for (PlayerInfo player : players) {
            if (!player.dead) {
                entities.add(player.man.toPlayerString());
            }
        }
        for (Bomb b : activeBombs) {
            entities.add(b.toPlayerString());
        }
        for (Item i : activeItems) {
            entities.add(i.toPlayerString());
        }
        lines.add(String.valueOf(entities.size()));
        lines.addAll(entities);

        return lines.toArray(new String[lines.size()]);
    }

    @Override
    protected int getExpectedOutputLineCountForPlayer(int playerIdx) {
        return 1;
    }

    static final boolean MOVE_WITH_BOMB = true;
    static final Pattern PLAYER_MOVE_PATTERN = Pattern.compile(
            "MOVE\\s+(?<x>-?\\d+)\\s+(?<y>-?\\d+)(?:\\s+)?(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
    static final Pattern PLAYER_BOMB_PATTERN = Pattern.compile("BOMB"
            + (MOVE_WITH_BOMB ? "\\s+(?<x>-?\\d+)\\s+(?<y>-?\\d+)" : "") + "(?:\\s+)?(?:\\s+(?<message>.+))?",
            Pattern.CASE_INSENSITIVE);
    static final String EXPECTED = "MOVE x y | BOMB" + (MOVE_WITH_BOMB ? " x y" : "");

    @Override
    protected void handlePlayerOutput(int frame, int round, int playerIdx, String[] outputs) throws WinException,
            LostException, InvalidInputException {
        PlayerInfo player = players.get(playerIdx);
        Man man = player.man;
        String line = outputs[0];

        Matcher matchMove = PLAYER_MOVE_PATTERN.matcher(line);
        Matcher matchBomb = PLAYER_BOMB_PATTERN.matcher(line);

        try {
            if (matchMove.matches()) {
                // Movement
                int x = Integer.valueOf(matchMove.group("x"));
                int y = Integer.valueOf(matchMove.group("y"));
                if (man.getX() != x || man.getY() != y) {
                    man.attempt = Man.Action.MOVE;
                    man.attemptCoord = new Coord(x, y);
                }
                // Message
                matchMessage(man, matchMove);
            } else if (matchBomb.matches()) {
                // Bomb
                man.attempt = Man.Action.BOMB;
                if (MOVE_WITH_BOMB) {
                    int x = Integer.valueOf(matchBomb.group("x"));
                    int y = Integer.valueOf(matchBomb.group("y"));
                    if (man.getX() != x || man.getY() != y) {
                        man.attemptCoord = new Coord(x, y);
                    }
                }
                // Message
                matchMessage(man, matchBomb);
            } else {
                throw new InvalidInputException(EXPECTED, line);
            }
        } catch (Exception e) {
            player.dead = true;
            player.deadAt = round;
            player.invalidAction = true;
            throw new InvalidInputException(EXPECTED, line);
        }

    }

    private void matchMessage(Man man, Matcher match) {
        man.message = match.group("message");
        if (man.message != null && man.message.length() > 19) {
            man.message = man.message.substring(0, 17) + "...";
        }
    }

    private boolean moveStupid(Man man) {
        if (man.lastDestination == null || !man.lastDestination.equals(man.attemptCoord)) {
            man.lastOccupiedPosition = null;
            man.lastDestination = man.attemptCoord;
        }
        Direction best = null;
        int by = 0;
        Direction lastResort = null;
        for (Direction d : Direction.values()) {
            Coord neighb = man.position.add(d.getX(), d.getY());
            Cell c = grid.get(neighb);
            if (c != null && c.isFree()) {
                if (neighb.equals(man.attemptCoord)) {
                    best = d;
                    break;
                } else if (neighb.equals(man.lastOccupiedPosition)) {
                    lastResort = d;
                } else {
                    int dist = c.manhattanTo(man.attemptCoord);
                    if (best == null || dist < by) {
                        by = dist;
                        best = d;
                    }
                }
            }
        }
        if (best == null) {
            best = lastResort;
        }
        if (best != null) {
            man.nextMove = best;
            return true;
        } else {
            man.lastOccupiedPosition = null;
        }
        return false;
    }

    private boolean moveBFS(Man man) {
        Queue<Coord> fifo = new LinkedList<>();
        Map<Coord, Coord> route = new HashMap<>();
        route.put(man.position, null);
        fifo.add(man.position);
        Coord closest = man.position;
        int by = man.position.manhattanTo(man.attemptCoord);
        boolean foundTarget = false;
        while (!foundTarget && !fifo.isEmpty()) {
            Coord e = fifo.poll();
            for (Direction d : Direction.values()) {
                Coord n = e.add(d.getX(), d.getY());
                Cell c = grid.get(n);
                if (c != null && c.isFree() && !route.containsKey(c)) {
                    if (man.attemptCoord.equals(n)) {
                        foundTarget = true;
                    }
                    route.put(c, e);
                    fifo.add(c);
                    int distance = c.manhattanTo(man.attemptCoord);
                    if (closest == null || distance < by) {
                        by = distance;
                        closest = c;
                    }
                }
            }
        }
        if (closest != null) {
            Coord cur = closest;
            Coord prev = closest;
            while (!cur.equals(man.position)) {
                prev = cur;
                cur = route.get(cur);
            }

            if (man.getX() < prev.x) {
                man.nextMove = Direction.RIGHT;
            } else if (man.getX() > prev.x) {
                man.nextMove = Direction.LEFT;
            } else if (man.getY() < prev.y) {
                man.nextMove = Direction.DOWN;
            } else if (man.getY() > prev.y) {
                man.nextMove = Direction.UP;
            }
            return true;

        }
        return false;
    }

    @Override
    protected void updateGame(int round) throws GameOverException {
        boolean progress = false;

        explosionGroups.clear();

        // Compute explosions
        Set<Bomb> nextBombs = new HashSet<>(activeBombs.size());
        for (Bomb bomb : activeBombs) {
            progress = true;
            bomb.fuse--;
            if (bomb.fuse <= 0) {
                nextBombs.add(bomb);
            }
        }
        Set<Coord> blownUp = new HashSet<>();
        while (!nextBombs.isEmpty()) {
            List<Explosion> currentExplosions = new ArrayList<>();
            for (Bomb bomb : nextBombs) {
                Explosion e = new Explosion(bomb);
                for (Direction d : Direction.values()) {
                    int distance = bomb.power;
                    Coord cur = bomb.position;
                    boolean blocked = false;
                    while (!blocked && distance > 0) {
                        Cell c = grid.get(cur);
                        if (c != null && (c.type != Cell.BLOCK && !blownUp.contains(c) || c.hasBomb())) {
                            if (!cur.equals(bomb.position) && (c.isBox() || c.hasBomb() || c.hasItem())) {
                                if (!blownUp.contains(c)) {
                                    if (c.isBox() || c.hasItem()) {
                                        e.flammables.add(c);
                                    } else if (c.hasBomb()) {
                                        for (Bomb b : c.bombs) {
                                            if (b.fuse > 0) {
                                                e.bombs.add(b);
                                            }
                                        }
                                    }
                                }
                                if (!bomb.owner.piercingBombs) {
                                    blocked = true;
                                }

                            }
                            if (!PRACTICE_MODE) {
                                List<Man> men = menOn(c);
                                for (Man man : men) {
                                    if (FRIENDLY_FIRE || bomb.owner != man) {
                                        man.dead = true;
                                        if (man.killedBy == null) {
                                            man.killedBy = e;
                                        }
                                        e.kills.add(man);
                                    }
                                }
                            }
                            cur = cur.add(d.getX(), d.getY());
                            distance--;
                        } else {
                            blocked = true;
                        }
                    }
                    e.span.put(d, bomb.power - distance);
                }
                currentExplosions.add(e);
            }
            nextBombs.clear();

            if (!currentExplosions.isEmpty()) {
                for (Explosion e : currentExplosions) {
                    for (Bomb b : e.bombs) {
                        blownUp.add(b.position);
                    }
                    // blownUp.addAll(e.flammables);
                    blownUp.add(e.source.position);
                    nextBombs.addAll(e.bombs);
                }
                explosionGroups.add(currentExplosions);
            }

        }

        // Sudden death
        if (SUDDEN_DEATH > 0) {
            countDown--;
            if (countDown < 0) {
                int depth = getDepth();

                List<Cell> deathZones = new LinkedList<>();
                for (Cell c : grid.values()) {
                    if (c.type != Cell.BLOCK
                            && (c.x == depth - 1 || c.x == WIDTH - depth || c.y == depth - 1 || c.y == HEIGHT - depth)) {
                        deathZones.add(c);
                    }
                }
                for (Cell c : deathZones) {
                    if (c.hasItem()) {
                        activeItems.remove(c.item);
                        c.item = null;
                    }
                    for (Bomb b : c.bombs) {
                        b.fuse = 1;
                    }
                    List<Man> deadMen = menOn(c);
                    for (Man man : deadMen) {
                        man.dead = true;
                    }
                    c.type = Cell.BLOCK;
                }
            }
        }

        // Compute intentions
        List<BombPlacement> bombPlacements = new ArrayList<>(players.size());
        for (PlayerInfo player : players) {
            if (player.dead) {
                continue;
            }
            Man man = player.man;
            if (man.dead) {
                player.dead = true;
                player.deadAt = round;
                continue;
            }
            if (man.attempt != null) {
                switch (man.attempt) {
                case BOMB: {
                    Cell c = grid.get(man.position);
                    if (!man.ammo.isEmpty() && c != null && c.isFree()) {
                        bombPlacements.add(new BombPlacement(man, c));
                        man.success = true;
                        progress = true;
                    } else {
                        man.confused = true;
                    }
                    if (MOVE_WITH_BOMB && man.attemptCoord != null) {
                        move(man);
                    }
                    break;
                }
                case MOVE: {
                    man.success = move(man);
                    break;

                }
                default:
                    break;
                }
            }

        }

        // Place bombs
        for (BombPlacement bp : bombPlacements) {
            Bomb b = bp.owner.ammo.pop();
            b.arm(bp.owner.position, bp.owner.power, BOMB_FUSE);
            bp.cell.bombs.add(b);
            activeBombs.add(b);
        }

        // Apply explosions (and retrieve ammo)
        Map<PlayerInfo, Set<Cell>> boxHitMap = new HashMap<PlayerInfo, Set<Cell>>();
        for (PlayerInfo player : players) {
            boxHitMap.put(player, new HashSet<Cell>());
        }

        explodedBoxes.clear();
        for (List<Explosion> explosionGroup : explosionGroups) {
            for (Explosion explosion : explosionGroup) {
                unspawnBomb(explosion.source);
                for (Bomb bomb : explosion.bombs) {
                    unspawnBomb(bomb);
                }
                for (Cell cell : explosion.flammables) {
                    if (cell.isBox()) {
                        explodedBoxes.add(cell);
                        boxes.remove(cell);
                        Man responsible = explosion.source.owner;
                        boxHitMap.get(responsible.player).add(cell);

                    } else if (cell.hasItem()) {
                        if (activeItems.remove(cell.item)) {
                            removedItems.add(cell.item);
                        }
                        cell.item = null;
                    }
                }
            }
        }

        for (PlayerInfo player : players) {
            player.boxesHit += boxHitMap.get(player).size();
        }

        // Perform actions
        Set<Item> collectedItems = new HashSet<Item>(players.size());
        for (Man man : activeMen) {
            if (man.nextMove != null) {
                progress = true;
                man.lastOccupiedPosition = man.position;
                man.position = man.position.add(man.nextMove.getX(), man.nextMove.getY());
                man.direction = man.nextMove;
                Cell location = grid.get(man.position);
                if (location.item != null) {
                    collectedItems.add(location.item);
                    location.item.removedBy.add(man);
                    location.item.powerUp(man);
                }
            }
        }
        for (Item item : collectedItems) {
            Cell location = grid.get(item.position);
            if (activeItems.remove(location.item)) {
                removedItems.add(location.item);
            }
            location.item = null;
        }

        // Pop items
        for (Cell box : explodedBoxes) {
            Item item = box.item;
            if (item != null) {
                activeItems.add(item);
                item.position = box;
            }
            box.type = Cell.FLOOR;
        }
        
        if (progress) {
            noProgressCountDown = NO_PROGRESS_TIMEOUT;
        } else {
            --noProgressCountDown;
        }
        
        if (boxes.isEmpty()) {
            --extraCountDown;
        }
        
    }

    private boolean move(Man man) {
        if (MOVE_MODE == MOVE_MODE_STUPID) {
            return moveStupid(man);
        } else if (MOVE_MODE == MOVE_MODE_BFS) {
            return moveBFS(man);
        }
        return false;
    }

    private int getDepth() {
        if (countDown < 0) {
            return -countDown / SUDDEN_DEATH_SLOWNESS;
        }
        return 0;
    }

    private void unspawnBomb(Bomb bomb) {
        boolean defused = activeBombs.remove(bomb);
        if (defused) {
            bomb.owner.ammo.add(bomb);
            grid.get(bomb.position).bombs.remove(bomb);
        }

    }

    private List<Man> menOn(Cell c) {
        ArrayList<Man> res = new ArrayList<>(activeMen.size());
        for (Man man : activeMen) {
            if (!man.dead && man.position.equals(c)) {
                res.add(man);
            }
        }
        return res;
    }

    @Override
    protected void populateMessages(Properties p) {
        p.put("move", "$%d moves to %d,%d");
        p.put("bomb", "$%d places a bomb");
        p.put("bombError", "$%d wants to place a bomb but cannot");
    }

    @Override
    protected String[] getInitDataForView() {
        List<String> lines = new ArrayList<>();
        lines.add(WIDTH + " " + HEIGHT + " " + players.size() + " "
                + (SUDDEN_DEATH < 0 ? DEFAULT_TIME_LIMIT : SUDDEN_DEATH) + " " + (PRACTICE_MODE ? 1 : 0));
        lines.add(String.valueOf(boxes.size()));
        for (Cell box : boxes) {
            lines.add(box.boxId + " " + box.x + " " + box.y + " " + box.toPlayerString(true));
        }
        lines.add(0, String.valueOf(lines.size() + 1));

        return lines.toArray(new String[lines.size()]);
    }

    @Override
    protected String[] getFrameDataForView(int round, int frame, boolean keyFrame) {
        List<String> lines = new ArrayList<>();
        lines.add(String.valueOf(round));
        for (PlayerInfo player : players) {
            lines.add(player.man.getX() + " " + player.man.getY() + " " + player.man.direction.toString() + " "
                    + player.boxesHit + " " + (player.dead ? 0 : 1) + " " + player.man.ammo.size() + " "
                    + player.man.maxAmmo + " " + getScore(player.index) + " "
                    + (player.man.message == null ? "" : player.man.message));
        }
        lines.add(String.valueOf(activeBombs.size()));
        for (Bomb b : activeBombs) {
            lines.add(b.position.toString() + " " + b.power + " " + b.fuse + " " + b.id + " " + b.getOwner());
        }
        lines.add(String.valueOf(explosionGroups.size()));
        for (List<Explosion> group : explosionGroups) {
            lines.add(String.valueOf(group.size()));
            for (Explosion explosion : group) {
                lines.add(explosion.toFrameString());
            }
        }

        lines.add(String.valueOf(explodedBoxes.size()));
        for (Cell box : explodedBoxes) {
            lines.add(String.valueOf(box.boxId));
        }

        //TODO: don't send item positions each turn, but at init
        lines.add(String.valueOf(activeItems.size()));
        for (Item item : activeItems) {
            lines.add(item.position.toString() + " " + item.itemType + " " + item.id);
        }
        lines.add(String.valueOf(removedItems.size()));
        for (Item item : removedItems) {
            lines.add(String.valueOf(item.id) + " " + (item.removedBy == null ? 0 : 1));
        }

        lines.add(String.valueOf(getDepth()));

        return lines.toArray(new String[lines.size()]);
    }

    @Override
    protected String getGameName() {
        return "Hypersonic";
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
    protected String[] getPlayerActions(int playerIdx, int round) {
        return null;
    }

    @Override
    protected boolean isPlayerDead(int playerIdx) {
        return players.get(playerIdx).dead;
    }

    @Override
    protected String getDeathReason(int playerIdx) {
        Explosion e = players.get(playerIdx).man.killedBy;
        if (e != null) {
            return "$" + playerIdx + ": Disintegrated!";
        }
        return "$" + playerIdx + ": Eliminated!";

    }

    @Override
    protected int getScore(int playerIdx) {
        PlayerInfo player = players.get(playerIdx);
        return player.boxesHit + (player.dead ? (player.deadAt*100) : 1000000);
    }

    @Override
    protected String[] getGameSummary(int round) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < players.size(); ++i) {
            lines.addAll(getPlayerSummary(i, round));
        }
        return lines.toArray(new String[lines.size()]);
    }

    protected List<String> getPlayerSummary(int playerIdx, int round) {
        List<String> lines = new ArrayList<>();
        PlayerInfo player = players.get(playerIdx);
        Man man = player.man;
        if (man.attempt == Man.Action.MOVE && man.success) {
            lines.add(translate("move", player.index, man.position.x, man.position.y));
        } else if (man.attempt == Man.Action.BOMB) {
            if (man.success) {
                lines.add(translate("bomb", player.index));
            } else {
                lines.add(translate("bombError", player.index));
            }
            if (man.attemptCoord != null && man.nextMove != null) {
                lines.add(translate("move", player.index, man.position.x, man.position.y));
            }
        }
        if (man.dead) {
            if (man.player.deadAt == round) {
                lines.add(getDeathReason(playerIdx));
            }
        }
        return lines;
    }

    @Override
    protected void setPlayerTimeout(int frame, int round, int playerIdx) {
        PlayerInfo player = players.get(playerIdx);
        player.dead = true;
        player.deadAt = round;
        player.man.dead = true;
    }

    @Override
    protected int getMaxRoundCount(int playerCount) {
        return SUDDEN_DEATH < 0 ? DEFAULT_TIME_LIMIT : (SUDDEN_DEATH + SUDDEN_DEATH_SLOWNESS
                * (Math.max(WIDTH, HEIGHT) / 2 + 1));
    }

    @Override
    protected boolean gameOver() {
        return noProgressCountDown <= 0 || COUNTDOWN_ON_NO_BOXES && extraCountDown <= 0 || END_ON_NO_BOXES && boxes.isEmpty() || super.gameOver();
    }

}

class Coord {
    final int x, y;

    public Coord add(int x, int y) {
        return new Coord(this.x + x, this.y + y);
    }

    public Coord add(Coord c) {
        return new Coord(c.x + x, c.y + y);
    }

    public Coord(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Coord(Coord c) {
        this.x = c.x;
        this.y = c.y;
    }

    @Override
    public String toString() {
        return x + " " + y;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + x;
        result = prime * result + y;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof Coord)) return false;
        Coord other = (Coord) obj;
        if (x != other.x) return false;
        if (y != other.y) return false;
        return true;
    }

    public boolean equals(Coord obj) {
        if (obj == null) return false;
        return equals(obj.x, obj.y);
    }

    public boolean equals(int x, int y) {
        return x == this.x && y == this.y;
    }

    public int manhattanTo(Coord target) {
        return manhattanTo(target.x, target.y);
    }

    public int manhattanTo(int x, int y) {
        return Math.abs(x - this.x) + Math.abs(y - this.y);
    }
}

abstract class Item extends GameEntity {
    public static int ITEM_COUNT = 0;
    public static final int FIRE_UP = 1;
    public static final int BOMB_UP = 2;
    public static final int PIERCE_BOMB = 3;

    int itemType;
    List<Man> removedBy;

    public Item(int type) {
        super(++ITEM_COUNT, null, ITEM);
        itemType = type;
        removedBy = new LinkedList<>();
    }

    public static Item instantiate(int itemType) {
        switch (itemType) {
        case BOMB_UP:
            return new BombUpItem();
        case FIRE_UP:
            return new FireUpItem();
        }
        return null;
    }

    public int getValue() {
        return itemType;
    }

    public int getState() {
        return itemType;
    }

    public abstract Item copy();

    public abstract void powerUp(Man man);

}

class PierceBombItem extends Item {

    public PierceBombItem() {
        super(PIERCE_BOMB);
    }

    @Override
    public Item copy() {
        return new PierceBombItem();
    }

    @Override
    public void powerUp(Man man) {
        man.piercingBombs = true;
    }

}

class BombUpItem extends Item {

    public BombUpItem() {
        super(BOMB_UP);
    }

    @Override
    public void powerUp(Man man) {
        man.ammo.add(new Bomb(man));
        man.maxAmmo += 1;
    }

    @Override
    public Item copy() {
        return new BombUpItem();
    }

}

class FireUpItem extends Item {

    public FireUpItem() {
        super(FIRE_UP);
    }

    @Override
    public void powerUp(Man man) {
        man.power++;
    }

    @Override
    public Item copy() {
        return new FireUpItem();
    }

}

abstract class GameEntity {
    public static final int PLAYER = 0;
    public static final int BOMB = 1;
    public static final int ITEM = 2;

    protected int id;
    protected Coord position;
    protected int type;

    public GameEntity(int id, Coord position, int type) {
        this.id = id;
        this.position = position;
        this.type = type;
    }

    abstract public int getValue();

    abstract public int getState();

    public int getOwner() {
        return 0;
    }

    public String toPlayerString() {
        return type + " " + getOwner() + " " + position.toString() + " " + getState() + " " + getValue();
    }
}

class PlayerInfo {
    boolean invalidAction;
    boolean dead;
    int index, deadAt;
    Man man;
    int boxesHit;

    public PlayerInfo(int index, Coord position, int bombs, int power) {
        this.index = index;
        man = new Man(position, bombs, power, this);
        boxesHit = 0;
    }
}

enum Direction {
    LEFT(-1, 0), RIGHT(1, 0), UP(0, -1), DOWN(0, 1);
    private int x, y;

    private Direction(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}

class Man extends GameEntity {
    boolean success, confused, dead, piercingBombs;
    Explosion killedBy;
    Direction nextMove;
    Action attempt;
    Coord attemptCoord;
    Coord lastOccupiedPosition;
    Coord lastDestination;
    String message;
    Direction direction;
    int power, maxAmmo;
    PlayerInfo player;
    Stack<Bomb> ammo;

    enum Action {
        MOVE, BOMB
    };

    public Man(Coord c, int maxAmmo, int power, PlayerInfo player) {
        super(player.index, c, PLAYER);
        this.power = power;
        direction = Direction.DOWN;
        this.player = player;
        lastOccupiedPosition = null;
        ammo = new Stack<>();
        for (int i = 0; i < maxAmmo; ++i) {
            Bomb bomb = new Bomb(this);
            ammo.add(bomb);
        }
        piercingBombs = false;
        this.maxAmmo = maxAmmo;
    }

    @Override
    public int getValue() {
        return power;
    }

    @Override
    public int getState() {
        return ammo.size();
    }

    public int getX() {
        return position.x;
    }

    public int getY() {
        return position.y;
    }

    public String toString() {
        return position.toString() + " " + ammo.size() + " " + power;
    }

    public void reset() {
        message = null;
        attempt = null;
        attemptCoord = null;
        nextMove = null;
        confused = false;
        success = false;
    }

    public int getOwner() {
        return player.index;
    }

}

class Explosion {
    Bomb source;
    List<Cell> flammables;
    List<Bomb> bombs;
    Set<Man> kills;
    Map<Direction, Integer> span;

    public Explosion(Bomb source) {
        this.source = source;
        flammables = new ArrayList<Cell>();
        bombs = new ArrayList<Bomb>();
        kills = new HashSet<Man>();
        span = new HashMap<Direction, Integer>();
    }

    public String toFrameString() {
        return source.position + " " + span.get(Direction.LEFT) + " " + span.get(Direction.RIGHT) + " "
                + span.get(Direction.UP) + " " + span.get(Direction.DOWN) + " " + source.id;
    }

}

class BombPlacement {
    Man owner;
    Cell cell;

    public BombPlacement(Man owner, Cell cell) {
        this.owner = owner;
        this.cell = cell;
    }

}

class Bomb extends GameEntity {
    int power;
    int fuse;
    Man owner;
    public static int BOMB_COUNT = 0;

    public Bomb(Man owner) {
        super(++BOMB_COUNT, null, BOMB);
        this.owner = owner;
    }

    public void arm(Coord c, int power, int fuse) {
        this.power = power;
        this.fuse = fuse;
        this.position = c;
    }

    public String toString(int playerIdx) {
        return position.toString() + " " + power + " " + fuse + " " + owner.player.index;
    }

    @Override
    public int getValue() {
        return power;
    }

    @Override
    public int getState() {
        return fuse;
    }

    @Override
    public int getOwner() {
        return owner.player.index;
    }
}

class Cell extends Coord {
    public static final int BLOCK = 0;
    public static final int FLOOR = 1;
    public static final int BOX = 2;
    List<Bomb> bombs;

    int type;
    Item item;
    int boxId;

    public Cell(int x, int y, int type) {
        super(x, y);
        this.type = type;
        bombs = new ArrayList<>(4);
    }

    public boolean hasItem() {
        return item != null;
    }

    public void placeItem(Item i) {
        item = i;
    }

    public boolean isBox() {
        return type == BOX;
    }

    public boolean hasBomb() {
        return !bombs.isEmpty();
    }

    public boolean isFree() {
        return type == FLOOR && !hasBomb();
    }

    public String toPlayerString(boolean visibleItems) {
        switch (type) {
        case BLOCK:
            return "X";
        case FLOOR:
            return ".";
        case BOX:
            if (visibleItems && hasItem()) {
                return String.valueOf(item.itemType);
            }
            return "0";
        default:
            return "X";
        }
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
        this.messages
                .put("playerTimeoutMulti",
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
                            addToolTip(nextPlayer,
                                    translate("LostTooltip", nextPlayer, translate(e.getReasonCode(), e.values)));
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
        return 400;
    }

    private void nextRound() throws GameOverException {
        newRound = true;
        if (++round > 0) {
            updateGame(round);
            updateScores();
        }
        // if (alivePlayerCount < getMinimumPlayerCount()) {
        // throw new GameOverException(null);
        // }
        if (gameOver()) {
            throw new GameOverException(null);
        }
    }

    protected boolean gameOver() {
        return alivePlayerCount < getMinimumPlayerCount();
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
