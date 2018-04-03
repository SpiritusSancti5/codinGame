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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Referee extends MultiReferee {
	private static final int BACK_IN_TIME_COUNT = 1;
	private static final int BACK_IN_TIME_MAX = 25;
	private static final int MAX_INACTIVE_ROUND_COUNT = 25;
	
	public static void main(String... args) throws IOException {
		new Referee(System.in, System.out, System.err).start();
	}
	
	private static final Dimension GRID_SIZE = new Dimension(35, 20);
	
	class Player {
		private int id;
		private Stack<Cell> positions;
		private Stack<Set<Cell>> ownedCells;
		private String message;
		private int backInTimeLeft;
		private boolean lost;
		private String lostReason;
		private int backInTimeCount;
		private Stack<Integer> ownedCellCounts;
		
		private int ownedCellCount;
		public Player(int id, Cell p, int backInTimeLeft) {
			this.positions = new Stack<Cell>();
			this.ownedCells = new Stack<Set<Cell>>();
			this.positions.push(p);
			this.id = id;
			this.backInTimeLeft = backInTimeLeft;
			this.lost = false;
			ownedCellCount = 0;
			backInTimeCount = 0;
			ownedCellCounts = new Stack<>();
		}
		
		public int getBackInTimeCount() {
			return backInTimeCount;
		}

		public void setBackInTimeCount(int backInTimeCount) {
			this.backInTimeCount = backInTimeCount;
		}


		public String getLostReason() {
			return lostReason;
		}
		
		public boolean lost() {
			return lost;
		}

		public void setLost(String reason) {
			this.lost = true;
			this.lostReason = reason; 
			this.message = null;
		}

		public boolean canBackInTime() {
			if(backInTimeLeft>0) {
				backInTimeLeft--;
				return true;
			}
			return false;
		}
		
		public void own(Set<Cell> cell) {
			this.ownedCells.push(cell);
			ownedCellCount+=cell.size();
			ownedCellCounts.push(ownedCellCount);
		}
		
		public boolean moveTo(Cell c) {
			Cell currentCell = positions.peek();
			boolean moved = false;
			if(currentCell.position.x!=c.position.x) {
				positions.push(Referee.this.grid[currentCell.position.y][currentCell.position.x+(int)Math.signum(c.position.x-currentCell.position.x)]);
				moved = true;
			} else if(currentCell.position.y!=c.position.y) {
				positions.push(Referee.this.grid[currentCell.position.y+(int)Math.signum(c.position.y-currentCell.position.y)][currentCell.position.x]);
				moved = true;
			} else {
				positions.push(currentCell);
			}
			
			return moved;
		}
		
		public void dontMove() {
			positions.push(getLastPosition());
		}
		
		public void setMessage(String message) {
			if(message != null && message.length()>100) {
				this.message = message.substring(0, 100);
			} else {
				this.message = message;
			}
			
		}
		
		public String getMessage() {
			return this.message;
		}
		
		public Cell getLastPosition() {
			return positions.peek();
		}
		public Cell getPositionAtRound(int round) {
			return positions.get(round);
		}
		
		public Set<Cell> getOwnedCellsAtRound(int inGameRound) {
			return this.ownedCells.get(inGameRound);
		}
		
		public void backInTime(int round) {
			if(this.positions.size()>round) {
	 			List<Cell> subList = this.positions.subList(round+1, positions.size());
				for(Cell cell:subList) {
					if(cell.ownershipRound>round && cell.owner == this) {
						cell.owner = null;
						ownedCellCount--;
					}
				}
				subList.clear();
			}
			if(this.ownedCells.size()>round) {
				List<Set<Cell>> ownedSubList = this.ownedCells.subList(round+1, ownedCells.size());
				for(Set<Cell> cs:ownedSubList) {
					for(Cell cell:cs) {
						if(cell.ownershipRound>round && cell.owner == this) {
							cell.owner = null;
							ownedCellCount--;
						}
					}
				}
				ownedSubList.clear();
			}
			if(this.ownedCellCounts.size()>round) {
				this.ownedCellCounts.subList(round+1, this.ownedCellCounts.size()).clear();
			}
		}

		public boolean moved() {
			return positions.size()>=2 && positions.get(positions.size()-2)!=positions.peek();
		}

		public void own(Cell cell, int inGameRound) {
			cell.setOwner(this, inGameRound);
			++ownedCellCount;
		}
		
		public int getOwnedCellCountAtRound(int round) {
			return ownedCellCounts.get(round);
		}

		public int getLastOwnedCellCount() {
			return ownedCellCounts.peek();
		}
 	}
	
	class Cell {
		public Player owner;
		public int ownershipRound;
		public Point position;
		public Cell(Point position) {
			this.position = position;
		}
		
		public String toString() {
			return this.position.x+" "+this.position.y+"("+(owner==null?"free":owner.id)+")";
		}
		
		public int manhattanDistance(Cell b) {
			return Math.abs(position.x-b.position.x)+Math.abs(position.y-b.position.y);
		}
		
		public void setOwner(Player owner, int round) {
			this.owner = owner;
			this.ownershipRound = round;
		}
		
		public Cell[] getNeighBoringCells() {
			int minX = Math.max(0, position.x-1);
			int maxX = Math.min(position.x+1, Referee.GRID_SIZE.width-1);
			int minY = Math.max(0, position.y-1);
			int maxY = Math.min(position.y+1, Referee.GRID_SIZE.height-1);
			List<Cell> cells = new ArrayList<Referee.Cell>();
			for(int x = minX ; x <= maxX; ++x) {
				for(int y = minY ; y <= maxY; ++y) {
					if(x != position.x || y != position.y) {
						cells.add(Referee.this.grid[y][x]);
					}
				}
			}
			return cells.toArray(new Cell[cells.size()]);
		}
	}
	
	
	static class Vector {
		private double x,y;

		public Vector(double x, double y) {
			this.x = x;
			this.y = y;
		}
		
		public Vector(Vector a, Vector b) {
			this.x = b.x-a.x;
			this.y = b.y-a.y;
		}

		public double getX() {
			return x;
		}

		public double getY() {
			return y;
		}
		
		public double distance(Vector v) {
			return Math.sqrt((v.x-x)*(v.x-x)+(v.y-y)*(v.y-y));
		}
		
		public Vector add(Vector v) {
			return new Vector(x+v.x, y+v.y);
		}
		
		public Vector mult(double factor) {
			return new Vector(x*factor, y*factor);
		}

		public Vector sub(Vector v) {
			return new Vector(this.x-v.x, this.y-v.y);
		}

		public double length() {
			return Math.sqrt(x*x+y*y);
		}
		
		public double lengthSquared() {
			return x*x+y*y;
		}

		public Vector normalize() {
			double length=length();
			if(length==0) return new Vector(0,0);
			return new Vector(x/length, y/length);
		}
        public double dot(Vector v) {
            return x * v.x + y * v.y;
        }

		public double angle() {
			return Math.atan2(y, x);
		}
		
		public String toString() {
			return "["+x+", "+y+"]";
		}

		public Vector project(Vector force) {
			Vector normalize=this.normalize();
			return normalize.mult(normalize.dot(force));
		}
		
		public final Vector cross(double s) {
			return new Vector(-s * y, s * x);
		}
		
		public Vector hsymmetric(double center) {
			return new Vector(2*center-this.x, this.y);
		}
		public Vector vsymmetric(double center) {
			return new Vector(this.x, 2*center-this.y);
		}
		
		public Vector vsymmetric() {
			return new Vector(this.x, -this.y);
		}
		public Vector hsymmetric() {
			return new Vector(-this.x, this.y);
		}
		
		public Vector symmetric(Vector center) {
			return new Vector(center.x*2-this.x, center.y*2-this.y);
		}
		
		private Vector rotate(double angle) {
			double cos = Math.round(Math.cos(angle) * 1000000000.0) / 1000000000.0;;
			double sin = Math.round(Math.sin(angle) * 1000000000.0) / 1000000000.0;
			return new Vector(this.x * cos - this.y * sin, this.x * sin + this.y * cos);
		}
	}
	
	
	private Player[] players;
	private Cell[][] grid;
	private int inGameRound;
	private int backInTimeAmount;
	private int reality;
	private int additionnalRoundCount;
	private boolean endOfGameDetected;
	private long seed;
	private boolean symmetric;
	private boolean sameCell;
	private List<Point> startPositions;
	private int lastActionRound;
	public Referee(InputStream is, PrintStream out, PrintStream err) throws IOException {
		super(is, out, err);
	}

	@Override
	protected void initReferee(int playerCount, Properties prop) throws InvalidFormatException {
		this.additionnalRoundCount = 0;
		this.lastActionRound = 0;
		this.inGameRound = 0;
		this.endOfGameDetected = false;
		this.reality = 0;
		this.players = new Player[playerCount];
		grid = new Cell[GRID_SIZE.height][GRID_SIZE.width];
		for(int y=0;y<GRID_SIZE.height;y++) {
			for(int x=0;x<GRID_SIZE.width;x++) {
				grid[y][x] = new Cell(new Point(x, y));
			}
		}
		
		parseProperties(prop);
		printError(prop);
		printError(playerCount);
		
		this.generatePlayer(playerCount);
		
		this.checkOwnership();
	}
	
	private void parseProperties(Properties prop) throws InvalidFormatException {
		
		try {
			this.seed = Long.valueOf(prop.getProperty("seed", String.valueOf(new Random(System.currentTimeMillis()).nextLong())));
		} catch(java.lang.NumberFormatException e) {
			throw new InvalidFormatException("seed is not an integer");
		}
		this.symmetric = Boolean.valueOf(prop.getProperty("symmetric", Boolean.toString(false)));
		this.sameCell = Boolean.valueOf(prop.getProperty("same_cell", Boolean.toString(false)));
		String positionsString = String.valueOf(prop.get("positions"));
		this.startPositions = new ArrayList<Point>();
		if(positionsString != null) {
			Matcher matcher = Pattern.compile("(?<x>-?[0-9]+),(?<y>-?[0-9]+)").matcher(positionsString);
			while(matcher.find()) {
				int x,y;
				try {
					x = Integer.parseInt(matcher.group("x"));
				} catch(java.lang.NumberFormatException e) {
					throw new InvalidFormatException("expected : "+matcher.group("x")+" is not an integer");
				}
				try {
					y = Integer.parseInt(matcher.group("y"));
				} catch(java.lang.NumberFormatException e) {
					throw new InvalidFormatException("expected : "+matcher.group("y")+" is not an integer");
				}
				if(x<0 || x>=GRID_SIZE.width) {
					throw new InvalidFormatException("expected : 0 <= x < "+GRID_SIZE.width+" but found x="+x);
				}
				if(y<0 || y>=GRID_SIZE.height) {
					throw new InvalidFormatException("expected : 0 <= y < "+GRID_SIZE.height+" but found y="+y);
				}
				this.startPositions.add(new Point(x, y));
			}
		}
	}

	private int getMaxInGameRoundCount(int playerCount) {
		switch(playerCount) {
		case 2:
			return 350;
		case 3:
			return 300;
		default:
			return 250;
		}
	}
	
	@Override
	protected int getMaxRoundCount(int playerCount) {
		return this.getMaxInGameRoundCount(playerCount) + this.additionnalRoundCount;
	}
	
	
	private static int customRound(double center, double value) {
		if(value>center) {
			return (int) Math.floor(value);
		} else {
			return (int) Math.ceil(value);
		}
	}
	
	private void generatePlayer(int playerCount) {
		Random random = new Random(this.seed);
		if(this.startPositions.isEmpty()) {
			this.startPositions.add(new Point(random.nextInt(GRID_SIZE.width), random.nextInt(GRID_SIZE.height)));
		}
		Point firstPoint = this.startPositions.get(0);
		if(this.sameCell) {
			for(int i=0;i<playerCount;++i) {
				players[i] = new Player(i, this.grid[firstPoint.y][firstPoint.x], BACK_IN_TIME_COUNT);
			}
		} else if(this.symmetric) {
			double interAngle = Math.PI*2/(double)playerCount;
			Vector center = new Vector((GRID_SIZE.width-1)/2d,(GRID_SIZE.height-1)/2d);
			Vector dir = new Vector(center, new Vector(firstPoint.x, firstPoint.y));
			double startAngle = dir.angle();
			double maxLength = Math.sqrt(GRID_SIZE.width*GRID_SIZE.width/4+GRID_SIZE.height*GRID_SIZE.height/4);
			for(int i=0;i<playerCount;++i) {
				double angle = startAngle+interAngle*i;
				maxLength = Math.min(maxLength, Math.abs(GRID_SIZE.width/2/Math.cos(angle)));
				maxLength = Math.min(maxLength, Math.abs(GRID_SIZE.height/2/Math.sin(angle)));
			}
			
			dir = dir.mult(Math.min(1, maxLength/dir.length()));
			
			for(int i=0;i<playerCount;++i) {
				Vector pos = center.add(dir.rotate(interAngle*i));
				players[i] = new Player(i, this.grid[Math.min(GRID_SIZE.height-1, Math.max(0, customRound(center.getY(), pos.y)))][Math.min(GRID_SIZE.width-1, Math.max(0, customRound(center.getX(), pos.x)))], BACK_IN_TIME_COUNT);
			}
		} else {
			while(this.startPositions.size()<playerCount) {
				Point p = new Point(random.nextInt(GRID_SIZE.width), random.nextInt(GRID_SIZE.height));
				if(!this.startPositions.contains(p)) {
					this.startPositions.add(p);
				}
			}
			for(int i=0;i<playerCount;++i) {
				Point p = this.startPositions.get(i);
				players[i] = new Player(i, this.grid[p.y][p.x], BACK_IN_TIME_COUNT);
			}
		}
	}
	
	@Override
	protected Properties getConfiguration() {
		Properties prop = new Properties();
		prop.put("seed", String.valueOf(this.seed));
		prop.put("symmetric", String.valueOf(this.symmetric));
		prop.put("same_cell", String.valueOf(this.sameCell));
		
		StringJoiner str = new StringJoiner(" ");
		for(Player p:players) {
			Cell first = p.getPositionAtRound(0);
			str.add(first.position.x+","+first.position.y);
		}
		prop.put("positions", str.toString());
		return prop;
	}

	@Override
	protected void populateMessages(Properties p) {
		p.put("noBackInTimeLeft", "Failure: no remaining back in time");
		p.put("playerMove", "$%d moved to cell %d,%d");
		p.put("playerBackInTime", "$%d wanted to go back in time %d rounds");
		p.put("playerDoNothing", "$%d stayed on cell %d,%d");
		p.put("gameGoBackInTime", "Game round: %d. Game went back in time %d rounds. Next game round: %d");
		p.put("playerScore", "$%d score: %d");
		p.put("gameGoForward", "Game round: %d");
		p.put("EndOfGameDetected","Game was ended prematurely as ranking can no longer change");
		p.put("inactivityDetected","No action detected for "+MAX_INACTIVE_ROUND_COUNT+" rounds: game ended prematurely");
	}

	@Override
	protected String[] getInitDataForView() {
		return new String[] {GRID_SIZE.width+" "+GRID_SIZE.height};
	}

	@Override
	protected String[] getFrameDataForView(int round, int frame, boolean keyFrame) {
		List<String> data = new ArrayList<String>();
		data.add(String.format("%d %d", keyFrame?this.inGameRound:(this.inGameRound-1), this.reality));
		if(players!=null) {
			for(Player p:players) {
				if(p==null) {
					data.add("0 0 0");
					data.add("\"\"");
				} else {
					StringJoiner joiner = new StringJoiner(" ");
					if(keyFrame) {
						joiner.add(String.valueOf(p.backInTimeLeft));
						joiner.add(String.valueOf(p.getBackInTimeCount()));
						joiner.add(String.valueOf(p.getOwnedCellCountAtRound(inGameRound)));
						if(!p.lost()) {
							Cell cell=p.getPositionAtRound(inGameRound);
							joiner.add(String.valueOf(cell.position.x));
							joiner.add(String.valueOf(cell.position.y));
							
							if(cell.owner == p && cell.ownershipRound >= inGameRound) {
								joiner.add(String.valueOf(cell.position.x));
								joiner.add(String.valueOf(cell.position.y));
							}
							for(Cell c:p.getOwnedCellsAtRound(inGameRound)) {
								joiner.add(String.valueOf(c.position.x));
								joiner.add(String.valueOf(c.position.y));
							}
						}
					}
					data.add(joiner.toString());
					data.add(String.format("\"%s\"", (p.getMessage()!=null&&keyFrame)?p.getMessage():""));
				}
			}
		}
		return data.toArray(new String[data.size()]);
	}

	@Override
	protected int getExpectedOutputLineCountForPlayer(int playerIdx) {
		return 1;
	}

	@Override
	protected String getGameName() {
		return "BackToTheCode";
	}

	private static final Pattern POSITION_PATTERN = Pattern.compile("(?<x>-?[0-9]{1,9}) +(?<y>-?[0-9]{1,9})(?: (?<message>\\p{Print}+))?", Pattern.CASE_INSENSITIVE);
	private static final Pattern BACKINTIME_PATTERN = Pattern.compile("BACK +(?<rounds>-?[0-9]{1,9})(?: (?<message>\\p{Print}+))?", Pattern.CASE_INSENSITIVE);
	
	@Override
	protected void handlePlayerOutput(int frame, int round, int playerIdx, String[] outputs) throws WinException, LostException, InvalidInputException {
		String output = outputs[0];
		String message=null;
		Player player = players[playerIdx];
		Matcher m=POSITION_PATTERN.matcher(output);
		if(m.matches()) {
			int x=Integer.parseInt(m.group("x"));
			if(x<0 || x>=GRID_SIZE.width) {
				player.setLost(translate("InvalidInput", "0 <= x < "+GRID_SIZE.width, "x = "+x));
				throw new InvalidInputException("0 <= x < "+GRID_SIZE.width, "x = "+x);
			}
			int y=Integer.parseInt(m.group("y"));
			if(y<0 || y>=GRID_SIZE.height) {
				player.setLost(translate("InvalidInput", "0 <= y < "+GRID_SIZE.height, "y = "+y));
				throw new InvalidInputException("0 <= y < "+GRID_SIZE.height, "y = "+y);
			}
			message = m.group("message");
			player.moveTo(this.grid[y][x]);
			if(player.moved()) {
				this.lastActionRound = round;
			}
		} else {
			player.dontMove();
			
			m=BACKINTIME_PATTERN.matcher(output);
			if(m.matches()) {
				int backRounds = Integer.parseInt(m.group("rounds"));
				
				if(backRounds<=0 || backRounds>BACK_IN_TIME_MAX) {
					player.setLost(translate("InvalidInput", "0 < backRounds <= "+BACK_IN_TIME_MAX, "backRounds = "+backRounds));
					throw new InvalidInputException("0 < backRounds <= "+BACK_IN_TIME_MAX, "backRounds = "+backRounds);
				}
				
				if(!player.canBackInTime()) {
					player.setLost(translate("noBackInTimeLeft"));
					throw new LostException("noBackInTimeLeft");
				}
				
				this.lastActionRound = round;
				
				this.addToolTip(playerIdx, "Back in time ("+backRounds+" rounds)");
				player.setBackInTimeCount(backRounds);
				backInTimeAmount = Math.min(backInTimeAmount+backRounds, inGameRound-1);
				message = m.group("message");
			} else {
				player.setLost(translate("InvalidInput", "\"<x> <y>\" or \"BACK <amount>\"", output));
				throw new InvalidInputException("\"<x> <y>\" or \"BACK <amount>\"", output);
			}
		}
		players[playerIdx].setMessage(message);
	}

	
	private Set<Cell> checkZoneFill(Player player) {
		Set<Cell> playerCells = new HashSet<Cell>();
		Cell lastCell=player.getLastPosition();
		boolean zoneClosed = false;
		for(int i=this.inGameRound-2;i>=0 && !zoneClosed;--i) {
			Cell roundPos = player.getPositionAtRound(i);
			if(roundPos.manhattanDistance(lastCell)==1) {
				zoneClosed = true;
			}
		}
		if(zoneClosed) {

			for(Cell cell: lastCell.getNeighBoringCells()) {
				if(cell.owner==null) {
					
					Set<Cell> cells = tryToFillZone(cell, player);
					if(cells != null) {
						cells.forEach(a->a.setOwner(player, this.inGameRound));
						playerCells.addAll(cells);
					}
				}
			}
		}
		
		return playerCells;
	}
	
	
	
	private Set<Cell> tryToFillZone(Cell fromCell, Player player) {
		Stack<Cell> stack = new Stack<Cell>();
		Set<Cell> zone = new HashSet<Cell>();
		stack.push(fromCell);
		while(!stack.isEmpty()) {
			Cell top = stack.pop();
			zone.add(top);
			
			if(top.position.x==0 || top.position.y==0 || top.position.x==GRID_SIZE.width-1 || top.position.y==GRID_SIZE.height-1) {
				return null;
			}
			for(Cell cell:top.getNeighBoringCells()) {
				if(cell.owner == null) {
					if(!zone.contains(cell)) {
						stack.push(cell);
					}
				} else if(cell.owner != player) {
					return null;
				}
			}
		}
		return zone;
	}

	@Override
	protected String[] getInitInputForPlayer(int playerIdx) {
		return new String[]{String.valueOf(this.players.length-1)};
	}

	@Override
	protected String[] getInputForPlayer(int round, int playerIdx) {
		List<String> data = new ArrayList<String>();
		data.add(String.valueOf(this.inGameRound));
		int[] mapper = new int[this.players.length];
		for(int i=0;i<this.players.length;++i) {
			mapper[i] = i;
		}
		mapper[playerIdx]=0;
		mapper[0]=playerIdx;
		
		for(int i=0;i<this.players.length;++i) {
			Player p = this.players[mapper[i]];
			if(p.lost()) {
				data.add("-1 -1 0");
			} else {
				Point pos = p.getPositionAtRound(this.inGameRound - 1).position;
				data.add(pos.x+" "+pos.y+" "+p.backInTimeLeft);
			}
		}
		
		for(int y=0;y<GRID_SIZE.height;++y) {
			StringBuilder str = new StringBuilder();
			for(int x=0;x<this.grid[y].length;++x) {
				Cell c = this.grid[y][x];
				str.append(c.owner==null?".":String.valueOf(mapper[c.owner.id]));
			}
			data.add(str.toString());
		}
		return data.toArray(new String[data.size()]);
	}

	@Override
	protected String getHeadlineAtGameStartForConsole() {
		return null;
	}

	@Override
	protected int getMinimumPlayerCount() {
		return 1;
	}

	@Override
	protected boolean showTooltips() {
		return true;
	}

	private void checkOwnership() {
		Map<Cell, List<Player>> ownership = new HashMap<Referee.Cell, List<Referee.Player>>();
		for(Player p:players) {
			if(!p.lost()) {
				Cell current = p.getLastPosition();
				if(current.owner == null) {
					List<Player> players = ownership.get(current);
					if(players == null) ownership.put(current, players = new ArrayList<Player>());
					players.add(p);
				}
			}
		}
		for(Entry<Cell, List<Player>> l:ownership.entrySet()) {
			if(l.getValue().size()==1) {
				l.getValue().get(0).own(l.getKey(), this.inGameRound);
			}
		}
		for(Player p:players) {
			if(!p.lost()) {
				p.own(checkZoneFill(p));
			} else {
				p.own(new HashSet<Cell>());
			}
		}
	}
	
	@Override
	protected void prepare(int round) {
		this.inGameRound++;
		
		if(backInTimeAmount > 0) {
			backInTimeAmount = 0;
		}
		for(Player p:this.players) {
			p.setBackInTimeCount(0);
		}
	}
	
	@Override
	protected void updateGame(int round) throws GameOverException {
		checkOwnership();
		if(backInTimeAmount > 0) {
			this.additionnalRoundCount+=backInTimeAmount+1;
			this.inGameRound-=backInTimeAmount + 1;
			for(Player p:players) {
				p.backInTime(inGameRound);
			}
			this.reality++;
		}
		
		if(this.endOfGameDetected) {
			throw new GameOverException("EndOfGameDetected");
		}
		if(this.lastActionRound < round - MAX_INACTIVE_ROUND_COUNT) {
			throw new GameOverException("inactivityDetected");
		}
		if(checkAnticipatedGameOver()) {
			this.endOfGameDetected = true;
		}
	}
	
	@Override
	protected String[] getPlayerActions(int playerIdx, int round) {
		Player player = this.players[playerIdx];
		if(!player.lost() && round>=0) {
			Cell cell=player.getPositionAtRound(this.inGameRound);
			if(player.getBackInTimeCount()>0) {
				return new String[]{translate("playerBackInTime", playerIdx, player.getBackInTimeCount())};
			} else {
				if(player.moved()) {
					return new String[]{translate("playerMove", playerIdx, cell.position.x, cell.position.y)};
				} else {
					return new String[]{translate("playerDoNothing", playerIdx, cell.position.x, cell.position.y)};
				}
			}
			
		}
		return null;
	}
	
	private boolean checkAnticipatedGameOver() throws GameOverException {
		int maxBackInTime = 0;
		for(Player p:this.players) {
			if(!p.lost()) {
				maxBackInTime+=p.backInTimeLeft*BACK_IN_TIME_MAX;
			}
		}
		if(maxBackInTime <= this.inGameRound) {
			int minReachableRound = this.inGameRound - maxBackInTime;
			int availableCellCountAtMinReachableRound = GRID_SIZE.width * GRID_SIZE.height;
			for(Player p:this.players) {
				availableCellCountAtMinReachableRound-=p.getOwnedCellCountAtRound(minReachableRound);
			}
			
			class Potential {
				int min;
				int max;
				public Potential(int min, int max) {
					this.min = min;
					this.max = max;
				}
				
				public int size() {
					return this.max - this.min;
				}
				public boolean isEmpty() {
					return this.size() <= 0;
				}
			}
			Potential[] playerPotentials = new Potential[this.players.length];
			for(int i=0;i<this.players.length;++i) {
				int ownedCellCount = this.players[i].getOwnedCellCountAtRound(minReachableRound);
				int min = ownedCellCount;
				int max;
				if(!this.players[i].lost()) {
					max = ownedCellCount + availableCellCountAtMinReachableRound;
				} else {
					max = this.players[i].getOwnedCellCountAtRound(this.inGameRound);
				}
				playerPotentials[i] = new Potential(min, max);
			}
			
			Arrays.sort(playerPotentials, new Comparator<Potential>() {
				@Override
				public int compare(Potential o1, Potential o2) {
					int diff = o2.max - o1.max;
					if(diff == 0)  {
						diff = o2.min - o1.min;
					}
					return diff;
				}
			});
			boolean finished = true;
			for(int i=1;i<playerPotentials.length && finished;++i) {
				if((!playerPotentials[i].isEmpty() || !playerPotentials[i-1].isEmpty()) && playerPotentials[i].max>=playerPotentials[i-1].min) {
					finished = false;
				}
			}
			
			return finished;
		}
		return false;
	}



	@Override
	protected boolean isPlayerDead(int playerIdx) {
		return this.players[playerIdx].lost();
	}

	@Override
	protected String getDeathReason(int playerIdx) {
		return this.players[playerIdx].getLostReason();
	}

	@Override
	protected int getScore(int playerIdx) {
		return this.players[playerIdx].getLastOwnedCellCount();
	}

	@Override
	protected String[] getGameSummary(int round) {
		List<String> summary = new ArrayList<String>();
		int playerId = 0;

		if(this.backInTimeAmount>0) {
			summary.add(translate("gameGoBackInTime", this.inGameRound+this.backInTimeAmount+1, this.backInTimeAmount, this.inGameRound+1));
		} else if(this.inGameRound>0){
			summary.add(translate("gameGoForward", this.inGameRound));
		}
		for(Player p: players) {
			summary.add(translate("playerScore", playerId, p.getOwnedCellCountAtRound(this.inGameRound)));
			playerId++;
		}
		return summary.toArray(new String[summary.size()]);
	}



	@Override
	protected void setPlayerTimeout(int frame, int round, int playerIdx) {
		this.players[playerIdx].setLost("playerTimeoutMulti");
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
			if(reasonCode != null) {
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
						reason = "Init error: "+e.getMessage();
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
			//e.printStackTrace();
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
						data.addAll(getPlayerActions(this.currentPlayer, newRound?this.round-1:this.round));
					}
				}
			}
		}
		out.println(data);
		if (newRound && round >= -1 && playerCount>1) {
			OutputData summary = new OutputData(OutputCommand.SUMMARY);
			if (frame == 0) {
				String head = getHeadlineAtGameStartForConsole();
				if (head != null) {
					summary.add(head);
				}
			}
			if(round >= 0) {
				summary.addAll(getGameSummary(round));
			}
			if(!isTurnBasedGame() && reason != null) {
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
		try{
			return String.format((String) messages.get(code), values);
		} catch(NullPointerException e) {
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