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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class Referee extends MultiReferee {

    private static final Dimension GRID_SIZE = new Dimension(6, 12);
    private static final int AHEAD_BLOCK = 8;
    private static final int BLOCK_TYPE_COUNT = 5;
    private static final int MINIMUM_ZONE_SIZE = 4;
    private static final int DEFAULT_ROTATION = 3;
    private static final boolean ROTATION_ALLOWED = true;
    private static final boolean BLOCK_PAIR_IDENTICAL = false;
    
    private static final int NUISANCE_FACTOR = 70;

    private static final int MAX_MESSAGE_LENGTH = 40;
    

    
    public static void main(String... args) throws IOException {
        new Referee(System.in, System.out, System.err).start();
    }
    
    public static class Rational implements Comparable<Rational> {
        private static Rational zero = new Rational(0, 1);

        private int num;   // the numerator
        private int den;   // the denominator

        // create and initialize a new Rational object
        public Rational(int numerator, int denominator) {

            // deal with x/0
            //if (denominator == 0) {
            //   throw new RuntimeException("Denominator is zero");
            //}

            // reduce fraction
            int g = gcd(numerator, denominator);
            num = numerator   / g;
            den = denominator / g;

            // only needed for negative numbers
            if (den < 0) { den = -den; num = -num; }
        }

        // return the numerator and denominator of (this)
        public int numerator()   { return num; }
        public int denominator() { return den; }

        // return double precision representation of (this)
        public double toDouble() {
            return (double) num / den;
        }
        
        public int floor() {
            return num / den;
        }

        // return string representation of (this)
        public String toString() { 
            if (den == 1) return num + "";
            else          return num + "/" + den;
        }

        // return { -1, 0, +1 } if a < b, a = b, or a > b
        public int compareTo(Rational b) {
            Rational a = this;
            int lhs = a.num * b.den;
            int rhs = a.den * b.num;
            if (lhs < rhs) return -1;
            if (lhs > rhs) return +1;
            return 0;
        }

        // is this Rational object equal to y?
        public boolean equals(Object y) {
            if (y == null) return false;
            if (y.getClass() != this.getClass()) return false;
            Rational b = (Rational) y;
            return compareTo(b) == 0;
        }

        // hashCode consistent with equals() and compareTo()
        public int hashCode() {
            return this.toString().hashCode();
        }


        // create and return a new rational (r.num + s.num) / (r.den + s.den)
        public static Rational mediant(Rational r, Rational s) {
            return new Rational(r.num + s.num, r.den + s.den);
        }

        // return gcd(|m|, |n|)
        private static int gcd(int m, int n) {
            if (m < 0) m = -m;
            if (n < 0) n = -n;
            if (0 == n) return m;
            else return gcd(n, m % n);
        }

        // return lcm(|m|, |n|)
        private static int lcm(int m, int n) {
            if (m < 0) m = -m;
            if (n < 0) n = -n;
            return m * (n / gcd(m, n));    // parentheses important to avoid overflow
        }

        // return a * b, staving off overflow as much as possible by cross-cancellation
        public Rational times(Rational b) {
            Rational a = this;

            // reduce p1/q2 and p2/q1, then multiply, where a = p1/q1 and b = p2/q2
            Rational c = new Rational(a.num, b.den);
            Rational d = new Rational(b.num, a.den);
            return new Rational(c.num * d.num, c.den * d.den);
        }

        // return a + b, staving off overflow
        public Rational plus(Rational b) {
            return plus(b.num, b.den);
        }
        
        public Rational plus(int v) {
            return this.plus(v, 1);
        }
        
        public Rational plus(int num, int den) {
            Rational a = this;

            // special cases
            if (a.num == 0) return new Rational(num, den);
            if (num == 0) return a;

            // Find gcd of numerators and denominators
            int f = gcd(a.num, num);
            int g = gcd(a.den, den);

            // add cross-product terms for numerator
            Rational s = new Rational((a.num / f) * (den / g) + (num / f) * (a.den / g), lcm(a.den, den));

            // multiply back in
            s.num *= f;
            return s;
        }

        // return -a
        public Rational negate() {
            return new Rational(-num, den);
        }

        // return a - b
        public Rational minus(Rational b) {
            return this.plus(b.negate());
        }
        
        public Rational minus(int i) {
            return this.plus(-i);
        }

        public Rational reciprocal() { 
            return new Rational(den, num);
        }
        public Rational divides(Rational b) {
            Rational a = this;
            return a.times(b.reciprocal());
        }


    }

    public Referee(InputStream is, PrintStream out, PrintStream err) throws IOException {
        super(is, out, err);
    }

    private class PlayerAction {
        int position;
        int rotation;
        
        public PlayerAction(int position, int rotation) {
            this.position = position;
            this.rotation = rotation;
        }
    }
    
    private class PlacedBlockResult {
        private Point position;
        private int type;
        public PlacedBlockResult(Point position, int type) {
            this.position = position;
            this.type = type;
        }
        
        @Override
        public String toString() {
            return String.format("%d %d %d", position.x, position.y, type);
        }
    }
    
    private class Grid {
        private Integer[][] grid;
        public Grid() {
            this.grid = new Integer[GRID_SIZE.width][GRID_SIZE.height];
        }
        
        public PlacedBlockResult addBlock(int position, int type) {
            Integer[] column = grid[position];
            int i;
            for(i=0 ; i < column.length && column[i] != null ; i++);
            
            if(i>=column.length) {
                return null;
            } else {
                column[i] = type;
                return new PlacedBlockResult(new Point(position, i), type);
            }
        }
        
        public List<Point> breakNuisanceAround(Point p) {
            List<Point> points = new ArrayList<>();
            if(p.x>0 && this.grid[p.x-1][p.y]!=null && this.grid[p.x-1][p.y]==0) {
                this.grid[p.x-1][p.y] = null;
                points.add(new Point(p.x-1, p.y));
            }
            if(p.y>0 && this.grid[p.x][p.y-1]!=null && this.grid[p.x][p.y-1]==0) {
                this.grid[p.x][p.y-1] = null;
                points.add(new Point(p.x, p.y-1));
            }
            if(p.x<GRID_SIZE.width-1 && this.grid[p.x+1][p.y]!=null && this.grid[p.x+1][p.y]==0) {
                this.grid[p.x+1][p.y] = null;
                points.add(new Point(p.x+1, p.y));
            }
            if(p.y<GRID_SIZE.height-1 && this.grid[p.x][p.y+1]!=null && this.grid[p.x][p.y+1]==0) {
                this.grid[p.x][p.y+1] = null;
                points.add(new Point(p.x, p.y+1));
            }
            return points;
        }
        
        
        private void gravity() {
            for(Integer[] column:this.grid) {
                for(int i = 0 ; i < column.length ; ++i) {
                    int newPos = i-1;
                    while(newPos >= 0 && column[newPos] == null) {
                        newPos--;
                    }
                    newPos++;
                    if(newPos != i) {
                        column[newPos] = column[i];
                        column[i] = null;
                    }
                }
            }
        }
        
        private void checkCombinations(Set<Point> alreadyChecked, Combination combination, Point cell, Integer cellType) {
            Integer currentCellType = this.grid[cell.x][cell.y];
            if(cellType == currentCellType) {
                combination.points.add(cell);
                if(alreadyChecked.add(cell)) {
                    if(cell.x>0) {
                        checkCombinations(alreadyChecked, combination, new Point(cell.x-1, cell.y), cellType);
                    }
                    if(cell.x<GRID_SIZE.width-1) {
                        checkCombinations(alreadyChecked, combination, new Point(cell.x+1, cell.y), cellType);
                    }
                    if(cell.y>0) {
                        checkCombinations(alreadyChecked, combination, new Point(cell.x, cell.y-1), cellType);
                    }
                    if(cell.y<GRID_SIZE.height-1) {
                        checkCombinations(alreadyChecked, combination, new Point(cell.x, cell.y+1), cellType);
                    }
                }
            }
        }
        

        
        public Combo checkCombinations() {
            Combo combo = new Combo();
            
            Set<Point> alreadyChecked = new HashSet<>();
            for(int i = 0 ; i < GRID_SIZE.height ; ++i) {
                for(int j = 0 ; j < GRID_SIZE.width ; ++j) {
                    Integer cellType = this.grid[j][i];
                    if(cellType != null && cellType > 0) {
                        Point cell = new Point(j, i);
                        if(!alreadyChecked.contains(cell)) {
                            Combination combi = new Combination(cellType);
                            
                            
                            checkCombinations(alreadyChecked, combi, cell, cellType);
                            if(combi.isValid()) {
                                combi.apply(this);
                                combo.combinations.add(combi);
                            }
                        }
                    }
                }
            }
            this.gravity();
            
            return combo;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            for(int i = 0 ; i < GRID_SIZE.height ; ++i) {
                for(int j = 0 ; j < GRID_SIZE.width ; ++j) {
                    Integer cell = grid[j][GRID_SIZE.height - i - 1];
                    builder.append(cell == null?".":cell);
                }
                builder.append("\n");
            }
            return builder.toString();
        }
    }
    
    private static class Combination {
        private int color;
        private Set<Point> destroyedNuisance;
        private Set<Point> points;
        public Combination(int color) {
            this.color = color;
            this.points = new HashSet<>();
            this.destroyedNuisance = new HashSet<>();
        }
        public void apply(Grid grid) {
            for(Point p:points) {
                grid.grid[p.x][p.y] = null;
                destroyedNuisance.addAll(grid.breakNuisanceAround(p));
            }
        }
        public boolean isValid() {
            return points.size() >= MINIMUM_ZONE_SIZE;
        }
        
        public String toString() {
            StringJoiner combinationJoiner = new StringJoiner(" ");
            for(Point point : points) {
                combinationJoiner.add(point.x+" "+point.y);
            }
            
            if(!destroyedNuisance.isEmpty()) {
                StringJoiner destroyedNuisanceJoiner = new StringJoiner(" ");
                for(Point point : destroyedNuisance) {
                    destroyedNuisanceJoiner.add(point.x+" "+point.y);
                }
                return combinationJoiner.toString()+'|'+destroyedNuisanceJoiner.toString();
            }
            return combinationJoiner.toString();
        }
    }
    
    private class Combo {
        private List<Combination> combinations;
        private int points;
        private Rational nuisance;
        public Combo() {
            this.combinations = new ArrayList<>();
        }
        
        public String toString() {
            StringJoiner combinationsJoiner = new StringJoiner(",");
            for(Combination combination:combinations) {
                combinationsJoiner.add(combination.toString());
            }
            return combinationsJoiner.toString()+'/'+points+'/'+nuisance.toDouble();
        }
    }
    
    
    static enum DeathReason {
        TIMEOUT("timeout"), INVALID_INPUT("InvalidInput"), NO_MORE_SPACE("noMoreSpace");
        private String deathReasonLabel;
        private DeathReason(String deathReasonLabel) {
            this.deathReasonLabel = deathReasonLabel;
        }
    }
    private class Player {
        
        private PlayerAction nextAction;
        private Grid grid;
        private Stack<Combo> zonesCombo;
        private List<PlacedBlockResult> newBlocks;
        private List<Point> newNuisanceBlocks;
        
        private int points;
        private Rational nuisanceR;
        private Rational nuisanceAfterDrop;
        private String message;
        
        private DeathReason deathReason;
        
        public Player() {
            this.grid = new Grid();
            this.points = 0;
            this.nuisanceAfterDrop = this.nuisanceR = new Rational(0, 1);
            this.newNuisanceBlocks = new ArrayList<>();
            this.newBlocks = new ArrayList<>();
            this.zonesCombo = new Stack<>();
            this.deathReason = null;
        }
        
        public boolean isDead() {
            return this.deathReason != null;
        }
        
        public void reset() {
            this.nextAction = null;
            this.zonesCombo.clear();
            this.newBlocks.clear();
            this.newNuisanceBlocks.clear();
            this.nuisanceAfterDrop = new Rational(0, 1);
            this.message = null;
        }
        
        public void harm() {
            int nuisanceLines = this.nuisanceR.divides(new Rational(GRID_SIZE.width, 1)).floor();
            for(int line = 0 ; line < nuisanceLines ; ++line) {
                for(int column = 0 ; column < GRID_SIZE.width ; ++column) {
                    PlacedBlockResult result = grid.addBlock(column, 0);
                    if(result != null) {
                        newNuisanceBlocks.add(result.position);
                    }
                }
            }
            this.nuisanceR = this.nuisanceR.minus(nuisanceLines * GRID_SIZE.width);
            this.nuisanceAfterDrop = this.nuisanceR;
        }
        
        public PlacedBlockResult addBlock(int position, int type) throws LostException {
            PlacedBlockResult result = grid.addBlock(position, type);
            if(result == null) {
                this.deathReason = DeathReason.NO_MORE_SPACE;
                throw new LostException("noMoreSpace");
            }
            return result;
        }
        
        public int doAction(BlockPair pair) throws InvalidInputException, LostException {
            if(nextAction != null) {
                int secondBlockXShift = (int)Math.round(Math.cos(-nextAction.rotation * Math.PI/2));
                int secondBlockYShift = (int)Math.round(Math.sin(-nextAction.rotation * Math.PI/2));
                int secondBlockX = secondBlockXShift + nextAction.position;
                
                if(nextAction.position < 0 || nextAction.position >= GRID_SIZE.width || secondBlockX < 0 || secondBlockX >= GRID_SIZE.width) {
                    this.deathReason = DeathReason.INVALID_INPUT;
                    throw new InvalidInputException(Math.max(0, -secondBlockXShift)+" <= position < "+(GRID_SIZE.width+Math.min(0, -secondBlockXShift)), "position = "+nextAction.position);
                }
                
                if(secondBlockYShift > 0) {
                    newBlocks.add(addBlock(secondBlockX, pair.block2));
                    newBlocks.add(addBlock(nextAction.position, pair.block1));
                } else {
                    newBlocks.add(addBlock(nextAction.position, pair.block1));
                    newBlocks.add(addBlock(secondBlockX, pair.block2));
                }
            }
            
            Combo combo;
            do {
                combo = this.grid.checkCombinations();
            } while(!combo.combinations.isEmpty() && zonesCombo.add(combo));
            return zonesCombo.size();
        }
        
        public int getScore() {
            if(this.isDead()) {
                return 0;
            } else {
                return this.points;
            }
        }
    }
    private class BlockPair {
        private int block1, block2;
        public BlockPair(int block1, int block2) {
            this.block1 = block1;
            this.block2 = block2;
        }
    }
    private List<BlockPair> blocks;
    private long seed;
    private Random random;
    private Player[] players;
    
    
    @Override
    protected void initReferee(int playerCount, Properties prop) throws InvalidFormatException {
        this.blocks = new LinkedList<>();
        
        this.seed = Long.parseLong(prop.getProperty("seed", String.valueOf(new Random().nextLong())));
        this.random = new Random(seed);
        
        players = new Player[playerCount];
        for(int i=0;i<playerCount;++i) {
            players[i] = new Player();
        }
        generateNextBlocks();
    }
    @Override
    protected Properties getConfiguration() {
        Properties prop = new Properties();
        prop.setProperty("seed", String.valueOf(this.seed));
        return prop;
    }
    
    
    @Override
    protected String[] getInitInputForPlayer(int playerIdx) {
        return new String[0];
    }

    private void generateNextBlocks() {
        while(blocks.size() < AHEAD_BLOCK) {
            if(BLOCK_PAIR_IDENTICAL) {
                int color = random.nextInt(BLOCK_TYPE_COUNT) + 1;
                blocks.add(new BlockPair(color, color));
            } else {
                blocks.add(new BlockPair(random.nextInt(BLOCK_TYPE_COUNT) + 1, random.nextInt(BLOCK_TYPE_COUNT) + 1));
            }
        }
    }
    
    @Override
    protected void prepare(int round) {
        if(round > 0 && !blocks.isEmpty()) {
            blocks.remove(0);
        }
        generateNextBlocks();
        
        for(Player p:players) {
            p.reset();
            p.harm();
        }
    }
    
    @Override
    protected String[] getInputForPlayer(int round, int playerIdx) {
        List<String> out = new ArrayList<>(AHEAD_BLOCK);
        for(int i= AHEAD_BLOCK  ; i> 0 ; --i) {
            BlockPair pair = blocks.get(blocks.size() - i);
            out.add(pair.block1+" "+pair.block2);
        }
        Player p = this.players[playerIdx];
        out.add(String.valueOf(p.getScore()));
        for(int i = 0 ; i < GRID_SIZE.height ; ++i) {
            StringBuilder builder = new StringBuilder();
            for(int j = 0 ; j < GRID_SIZE.width ; ++j) {
                Integer cell = p.grid.grid[j][GRID_SIZE.height - i - 1];
                builder.append(cell == null?".":cell);
            }
            out.add(builder.toString());
        }
        Player p2 = this.players[1-playerIdx];
        out.add(String.valueOf(p2.getScore()));
        for(int i = 0 ; i < GRID_SIZE.height ; ++i) {
            StringBuilder builder = new StringBuilder();
            for(int j = 0 ; j < GRID_SIZE.width ; ++j) {
                Integer cell = p2.grid.grid[j][GRID_SIZE.height - i - 1];
                builder.append(cell == null?".":cell);
            }
            out.add(builder.toString());
        }
        return out.toArray(new String[out.size()]);
    }

    @Override
    protected int getExpectedOutputLineCountForPlayer(int playerIdx) {
        return 1;
    }
    
    private static final Pattern PLAYER_OUTPUT_PATTERN1 = Pattern.compile("(?<position>-?\\d{1,8})(?:\\s+(?<message>.+))?");
    private static final Pattern PLAYER_OUTPUT_PATTERN2 = Pattern.compile("(?<position>-?\\d{1,8})(?:\\s+(?<rotation>[0123]))?\\s*(?:\\s+(?<message>.+))?");
    
    @Override
    protected void handlePlayerOutput(int frame, int round, int playerIdx, String[] outputs) throws WinException, LostException, InvalidInputException {
        Player player = this.players[playerIdx];
        Pattern p = ROTATION_ALLOWED ? PLAYER_OUTPUT_PATTERN2 : PLAYER_OUTPUT_PATTERN1;
        
        Matcher m = p.matcher(outputs[0]);
        if(m.matches()) {
            int position = Integer.parseInt(m.group("position"));
            
            int rotation = DEFAULT_ROTATION;
            
            if(ROTATION_ALLOWED && m.group("rotation") != null) {
                rotation = Integer.parseInt(m.group("rotation"));
            }
            
            player.nextAction = new PlayerAction(position, rotation);
            int comboCount = player.doAction(blocks.get(0));
            if(comboCount > 1 ) {
                addToolTip(playerIdx, translate("playerCombo", comboCount));
            }
            player.message = m.group("message");
            if(player.message != null && player.message.length() > MAX_MESSAGE_LENGTH) {
                player.message = player.message.substring(0, MAX_MESSAGE_LENGTH);
            }
        } else {
            player.deathReason = DeathReason.INVALID_INPUT;
            player.nextAction = null;
            if (ROTATION_ALLOWED) {
                throw new InvalidInputException("position rotation", outputs[0]);   
            } else {
                throw new InvalidInputException("position", outputs[0]);
            }
        }
    }
    
    private int computePoints(Combo combo, int counter) {
        // https://puyonexus.net/wiki/Scoring#Color_Bonus
        // score = (10 *PC) * (CP + CB + GB)
        // PC = Number of puyo cleared in the chain.
        // CP = Chain Power
        // CB = Color Bonus
        // GB = Group Bonus 
        
        // PC
        int pc = 0;
        for(Combination combi: combo.combinations) {
            pc += combi.points.size();
        }
        
        // CP
        int cp = Math.min(counter <= 0 ? 0 : (4<<counter), 999);
        
        // CB
        Set<Integer> colors = new HashSet<>();
        for(Combination combi:  combo.combinations) {
            colors.add(combi.color);
        }
        int colorCount = colors.size();
        int cb = colorCount<=1 ? 0 : (1<<(colorCount-1));
        
        // GB
        int gb = 0;
        for(Combination combi: combo.combinations) {
            int lgb = combi.points.size() - 4;
            if(combi.points.size() >=11) {
                lgb = 8;
            }
            gb += lgb;
        }
        
        int points = (10 * pc) * Math.max(1, Math.min(999, cp + cb + gb));
        
        return points;
    }
    
    private int computePoints(Player p) {
        Player other = null;
        for(Player p2:players) {
            if(p != p2) {
                other = p2;
            }
        }
        Rational otherNuisance = other.nuisanceR;

        int pointsTotal = 0;
        for(int i = 0 ; i < p.zonesCombo.size() ; ++i) {
            
            
            Combo combo = p.zonesCombo.get(i);
            int points = computePoints(combo, i);
            
            pointsTotal += points;
            p.points += points;
            otherNuisance = otherNuisance.plus(new Rational(points, NUISANCE_FACTOR));
            
            
            combo.points = p.points;
            combo.nuisance = otherNuisance;
        }
        
        
        Rational nuisance = new Rational(pointsTotal, NUISANCE_FACTOR);
        other.nuisanceR = other.nuisanceR.plus(nuisance);
        
        
        return pointsTotal;
    }

    @Override
    protected void updateGame(int round) throws GameOverException {
        for(Player p:players) {
            computePoints(p);
        }
    }


    @Override
    protected void populateMessages(Properties p) {
        p.put("noMoreSpace", "No more space!");
        p.put("playerCombo", "Chain of %d!");
    }


    @Override
    protected String[] getInitDataForView() {
        List<String> output = new ArrayList<String>();
        output.add(String.format("%d %d %d", GRID_SIZE.width, GRID_SIZE.height, AHEAD_BLOCK));
        return output.toArray(new String[output.size()]);
    }

    @Override
    protected String[] getFrameDataForView(int round, int frame, boolean keyFrame) {
        List<String> output = new ArrayList<String>();
        
        for(Player p:players) {
            output.add(String.format("%d %f %f %s", p.getScore(), p.nuisanceR.toDouble(), p.nuisanceAfterDrop.toDouble(), p.isDead()?p.deathReason.toString():"-"));
            if(p.newNuisanceBlocks == null || p.newNuisanceBlocks.isEmpty()) {
                output.add("-");
            } else {
                output.add(p.newNuisanceBlocks.stream().map(a->(a.x+" "+a.y)).collect(Collectors.joining(" ")));
            }
            if(p.newBlocks == null || p.newBlocks.isEmpty()) {
                output.add("-");
            } else {
                output.add(p.newBlocks.stream().map(Object::toString).collect(Collectors.joining(" ")));
            }
            StringJoiner comboJoiner = new StringJoiner(";");
            if(p.zonesCombo != null && !p.zonesCombo.isEmpty()) {
                for(Combo combo: p.zonesCombo) {
                    comboJoiner.add(combo.toString());
                }
                output.add(comboJoiner.toString());
            } else {
                output.add("-");
            }
            if(p.message != null) {
                output.add(String.format("\"%s\"", p.message));
            } else {
                output.add("\"\"");
            }
        }
        
        StringJoiner nextBlocks = new StringJoiner(",");
        for(BlockPair p:this.blocks) {
            nextBlocks.add(p.block1+" "+p.block2);
        }
        output.add(nextBlocks.toString());
        return output.toArray(new String[output.size()]);

    }



    @Override
    protected String getGameName() {
        return "SmashTheCode";
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
        return new String[0];
    }

    @Override
    protected boolean isPlayerDead(int playerIdx) {
        return this.players[playerIdx].deathReason != null;
    }

    @Override
    protected String getDeathReason(int playerIdx) {
        return this.players[playerIdx].deathReason.deathReasonLabel;
    }

    @Override
    protected int getScore(int playerIdx) {
        Player player = this.players[playerIdx];
        if(player.isDead()) {
            return 0;
        } else {
            return Math.max(1, this.players[playerIdx].getScore());
        }
    }

    @Override
    protected String[] getGameSummary(int round) {
        return new String[0];
    }
    @Override
    protected void setPlayerTimeout(int frame, int round, int playerIdx) {
        this.players[playerIdx].deathReason = DeathReason.TIMEOUT;
    }
    

    @Override
    protected int getMaxRoundCount(int playerCount) {
        return 200;
    }
    
    protected int getMillisTimeForRound() {
        return 100;
    }
    
    protected int getMillisTimeForFirstRound() {
        return 1000;
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
    protected final void handlePlayerOutput(int frame, int round, int playerIdx, String[] playerOutput) throws WinException, LostException, InvalidInputException {
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
        this.messages.put("playerTimeoutMulti", "Timeout: the program did not provide %d input lines in due time... $%d will no longer be active in this game.");
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
                        if(!this.isTurnBasedGame()) {
                            for(PlayerStatus player : this.players) {
                                if(!player.lost) {
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
                        if(e instanceof InvalidInputException) {
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
        if(newRound || isTurnBasedGame()) {
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
        if(this.isTurnBasedGame()) {
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
    protected abstract void handlePlayerOutput(int frame, int round, int playerIdx, String[] output) throws WinException, LostException, InvalidInputException;
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
