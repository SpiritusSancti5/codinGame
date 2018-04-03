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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Referee extends MultiReferee {
	public static void main(String ... args) throws IOException {
		new Referee(System.in, System.out, System.err).start();
	}
	
	private static final int DEFAULT_WIDTH=9;
	private static final int DEFAULT_HEIGHT=9;
	@Override
	protected void populateMessages(Properties p) {
		p.put("InvalidMove", "$%d hit a wall");
		p.put("InvalidMoveBorder", "$%d tried to move outside the game area");
		p.put("InvalidWall", "$%d: invalid wall position");
		p.put("BlockForbidden", "$%d's wall (%d,%d,%s) blocking $%d is forbidden");
		p.put("exitReached", "$%d: exit reached");
		p.put("noWallLeft", "$%d: no walls left");
		p.put("playerMove", "$%d moved from (%d,%d) to (%d,%d)");
		p.put("playerPutWall", "$%d placed a wall (%d,%d,%s)");
	}
	
	public Referee(InputStream is, PrintStream out, PrintStream err)
			throws IOException {
		super(is, out, err);
	}
	
	public static interface Goal {
		public boolean isReached(Cell cell);
	}
	public static class VGoal implements Goal{
		private int x;
		public VGoal(int x) {
			this.x = x;
		}
		@Override
		public boolean isReached(Cell cell) {
			return cell.x==this.x;
		}
	}
	public static class HGoal implements Goal {
		private int y;
		public HGoal(int y) {
			this.y = y;
		}
		@Override
		public boolean isReached(Cell cell) {
			return cell.y==this.y;
		}
	}
	public static class Cell {
		private int x,y;
		public Cell(int x, int y) {
			this.x = x;
			this.y = y;
		}
		public int distance(Cell c) {
			return Math.abs(c.x-x)+Math.abs(c.y-y);
		}
		
		@Override
		public int hashCode() {
			return x<<16+y;
		}
		@Override
		public boolean equals(Object o) {
			return o instanceof Cell && ((Cell)o).equals(this);
		}
		
		public boolean equals(Cell c) {
			return c.x==x && c.y==y;
		}
		@Override
		public String toString() {
			return x+" "+y;
		}
	}
	
	public class Player implements Comparable<Player>{
		private int id;
		private int wallCount;
		private LinkedList<Cell> way;
		private List<Cell> minimalway;
		private Goal goal;
		private int score;
		private String lastAction;
		private Integer winTime,deathTime;
		public String message;
		public Player(int position, int player, int wallCount) {
			this.id=player;
			this.wallCount=wallCount;
			way=new LinkedList<>();
			switch(player%4) {
			case 0:
				this.way.add(new Cell(0,position));
				this.goal=new VGoal(width-1);
				break;
			case 1:
				this.way.add(new Cell(width-1,height-1-position));
				this.goal=new VGoal(0);
				break;
			case 2:
				this.way.add(new Cell(width-1-position,0));
				this.goal=new HGoal(height-1);
				break;
			case 3:
				this.way.add(new Cell(position,height-1));
				this.goal=new HGoal(0);
				break;
			}
		}
		
		public List<Cell> computeMinimalWay(Pathfinding finding, CellAccessibilityChecker checker) {
			return finding.findWay(getPosition(), goal, checker, width, height);
		}
		
		public Cell getPosition() {
			return way.getLast();
		}
		
		public boolean win() {
			return goal.isReached(getPosition());
		}

		public void setMinimalWay(List<Cell> value) {
			this.minimalway=value;
		}

		public void addPosition(Cell cell) {
			this.way.add(cell);
		}

		public void setScore(int score) {
			this.score=score;
		}

		public boolean canMoveTo(Cell cell) {
			if(cell.x==getPosition().x) {
				return cell.y>=0 && cell.y<height && Math.abs(getPosition().y-cell.y)==1;
			} else if(cell.y==getPosition().y) {
				return cell.x>=0 && cell.x<width && Math.abs(getPosition().x-cell.x)==1;
			}
			return false;
		}

		@Override
		public int compareTo(Player other) {
			if(other.winTime==null || winTime==null) {
				if(other.winTime==null && winTime==null) {
					if(other.deathTime!=null && deathTime==null) {
						return 1;
					} else if(other.deathTime==null && deathTime!=null) {
						return -1;
					} else if(other.deathTime==null && deathTime==null) {
						return 0;
					} else {
						return deathTime-other.deathTime;
					}
				} else {
					if(other.winTime==null) return 1;
					else return -1;
				}
			} else {
				return other.winTime-winTime;
			}
		}

		public void setDead(int frame) {
			this.deathTime=frame;
			this.lastAction=null;
			this.minimalway=null;
		}
	}
	
	private enum Orientation {
		VERTICAL("V"), HORIZONTAL("H");
		private String label;
		Orientation(String label) {
			this.label=label;
		}
		public String toString() {
			return label;
		}
		
		public static Orientation parse(String s) {
			for(Orientation dir:Orientation.values()) {
				if(dir.toString().equals(s)) return dir;
			}
			return null;
		}
	}
	public class Wall {
		private boolean justPlaced;
		private Cell cell;
		private Orientation dir;
		private int player;
		
		public Wall(int player, Cell cell, Orientation dir) {
			this.player=player;
			this.justPlaced=true;
			this.cell = cell;
			this.dir = dir;
		}

		public boolean block(Cell from, Cell to) {
			if(to.x==from.x) {
				if(dir==Orientation.HORIZONTAL && to.x>=cell.x && to.x<=cell.x+1) {
					return this.cell.y>Math.min(from.y,to.y) && this.cell.y<=Math.max(from.y,to.y);
				} else {
					return false;
				}
			} else if(to.y==from.y) {
				if(dir==Orientation.VERTICAL && to.y>=cell.y && to.y<=cell.y+1) {
					return this.cell.x>Math.min(from.x,to.x) && this.cell.x<=Math.max(from.x,to.x);
				} else {
					return false;
				}
			}
			throw new RuntimeException("Illegal state");
		}

		public boolean conflict(Wall newWall) {
			switch(newWall.dir) {
			case HORIZONTAL:
				switch(dir) {
				case HORIZONTAL:
					return newWall.cell.y==cell.y && Math.abs(newWall.cell.x-cell.x)<=1;
				case VERTICAL:
					return newWall.cell.x==cell.x-1 && newWall.cell.y==cell.y+1;
				}
				break;
			case VERTICAL:
				switch(dir) {
				case HORIZONTAL:
					return cell.x==newWall.cell.x-1 && cell.y==newWall.cell.y+1;
				case VERTICAL:
					return newWall.cell.x==cell.x && Math.abs(newWall.cell.y-cell.y)<=1;
				}
				break;
			}
			return false;
		}
		@Override
		public String toString() {
			return cell+" "+dir.label;
		}
	}
	
	public static interface CellAccessibilityChecker {
		public boolean isBlocked(Cell from, Cell to);
	}
	public static interface Pathfinding{
		public List<Cell> findWay(Cell cell, Goal goal, CellAccessibilityChecker checker, int width, int height);
	}
	public static class CustomPathfinding implements Pathfinding{
		class Node implements Comparable<Node>{
			private List<Cell> cells;
			public Node(Cell cell, Cell ... previous) {
				this.cells=new ArrayList<>(Arrays.asList(previous));
				this.cells.add(cell);
			}
			public int cost() {
				return this.cells.size();
			}
			@Override
			public int compareTo(Node n2) {
				int diff=cost()-n2.cost();
				if(diff==0) diff=getLast().x-n2.getLast().x;
				if(diff==0) diff=getLast().y-n2.getLast().y;
				return diff;
			}
			public Cell getLast() {
				return cells.get(cells.size()-1);
			}
			
			public String toString() {
				return getLast()+" "+cost();
			}
		}
		
		
		@Override
		public List<Cell> findWay(final Cell cell, Goal goal, CellAccessibilityChecker checker, int width, int height) {
			List<Node> openList=new LinkedList<>();
			Set<Cell> closedList=new HashSet<>();
			openList.add(new Node(cell));
			while(!openList.isEmpty()) {
				Node n=openList.remove(0);
				closedList.add(n.getLast());
				Cell last=n.getLast();
				if(goal.isReached(last)) {
					return n.cells;
				}

				for(Cell temp:new Cell[]{new Cell(last.x-1, last.y),new Cell(last.x, last.y-1),new Cell(last.x+1, last.y),new Cell(last.x, last.y+1)}) {
					if(temp.x>=0 && temp.x<width && temp.y>=0 && temp.y<height) {
						if(!closedList.contains(temp) && !checker.isBlocked(last, temp)) {
							openList.add(new Node(temp, n.cells.toArray(new Cell[n.cells.size()])));
						}
					}
				}
			}
			return null;
		}
	}
	
	private static enum Direction {
		UP(0,-1), LEFT(-1,0), RIGHT(1,0), DOWN(0,1);
		private int x,y;
		Direction(int x, int y) {
			this.x=x;
			this.y=y;
		}
		public Cell move(Cell from) {
			return new Cell(from.x+x, from.y+y);
		}
	}

	private int width,height;
	private Player[] players;
	private List<Wall> walls;
	private boolean symmetric;
	private CellAccessibilityChecker checker;
	private Pathfinding pathFinding;
	private int[] positions;
	@Override
	protected void initReferee(int playerCount, Properties prop) throws InvalidFormatException {
		this.players=new Player[playerCount];
		this.walls=new ArrayList<>();
		this.width=Integer.parseInt(prop.getProperty("width", String.valueOf(DEFAULT_WIDTH)));
		this.height=Integer.parseInt(prop.getProperty("height", String.valueOf(DEFAULT_HEIGHT)));
		this.symmetric=Boolean.parseBoolean(prop.getProperty("symmetric", "false"));
		
		width = Math.min(50, Math.max(0, width));
		height = Math.min(50, Math.max(0, height));
		
		Random r=new Random();
		String[] positions=prop.getProperty("positions", r.nextInt(width)+" "+r.nextInt(width)+" "+r.nextInt(height)+" "+r.nextInt(height)).split(" ");
		if(positions.length<playerCount) {
			//throw new InvalidFormatException(playerCount+" positions required");
			positions = (r.nextInt(width)+" "+r.nextInt(width)+" "+r.nextInt(height)+" "+r.nextInt(height)).split(" ");
		}
		this.positions=new int[positions.length];
		for(int i=0;i<positions.length;++i) {
			this.positions[i]=Integer.parseInt(positions[i]);
			if(i<=1 && (this.positions[i]<0 || this.positions[i]>=height)) {
				//throw new InvalidFormatException("0<=position<"+height);
				this.positions[i] = r.nextInt(height);
			}
			if(i>=2 && (this.positions[i]<0 || this.positions[i]>=width)) {
				//throw new InvalidFormatException("0<=position<"+width);
				this.positions[i] = r.nextInt(width);
			}
		}
		
		if(symmetric) {
			this.positions[1]=this.positions[0];
			if(playerCount>=4) {
				this.positions[3]=this.positions[2];
			}
		}
		
		pathFinding=new CustomPathfinding();
		checker=new CellAccessibilityChecker() {
			@Override
			public boolean isBlocked(Cell from, Cell to) {
				for(Wall wall:walls) {
					if(wall.block(from, to)) return true;
				}
				return false;
			}
		};
		int wallperPlayer=20/playerCount;
		for(int i=0;i<playerCount;++i) {
			players[i]=new Player(this.positions[i], i, wallperPlayer);
		}
		
		recomputeMinimalWay();
	}
	
	private void recomputeMinimalWay() {
		for(Player p:players) {
			p.setMinimalWay(p.computeMinimalWay(pathFinding, checker));
		}
	}
	
	@Override
	protected Properties getConfiguration() {
		Properties prop=new Properties();
		prop.put("width", String.valueOf(width));
		prop.put("height", String.valueOf(height));
		StringBuffer positions=new StringBuffer();
		for(int i=0;i<this.positions.length;++i) {
			positions.append(this.positions[i]);
			positions.append(" ");
		}
		prop.put("positions", positions.toString().trim());
		prop.put("symmetric", String.valueOf(symmetric));
		return prop;
	}
	

	@Override
	protected String getGameName() {
		return "TheGreatEscape";
	}

	private static final Pattern PLAYER_OUTPUT_PATTERN=Pattern.compile("((?<direction>UP|RIGHT|DOWN|LEFT)|((?<x>[0-9]+)\\s+(?<y>[0-9]+)\\s*(?<orientation>H|V)))(\\s+(?<message>.*))?");
	@Override
	protected void handlePlayerOutput(int frame, int round, int playerId, String[] outputs) throws WinException, LostException, InvalidInputException {
		Player player=players[playerId];
		try {
			for(Wall wall:walls) {
				wall.justPlaced=false;
			}
			String output=outputs[0];
			Matcher m=PLAYER_OUTPUT_PATTERN.matcher(output);
			player.message = null;
			
			if(m.matches()) {
				String message = m.group("message");
				if (message != null && message.length() > 20) {
					message = message.substring(0, 20);
				}
				player.message = message;
				
				Orientation direction=Orientation.parse(m.group("orientation"));
				if(direction!=null) {
					// WALL
					int x=Integer.parseInt(m.group("x"));
					int y=Integer.parseInt(m.group("y"));
					Cell cell=new Cell(x,y);
					if(player.wallCount<=0) {
						throw new LostException("noWallLeft", playerId);
					}
					if(x>=this.width) {
						throw new InvalidInputException("0<=x<="+(this.width-1), "x="+x);
					}
					if(y>=this.height) {
						throw new InvalidInputException("0<=y<="+(this.height-1), "y="+y);
					}
					switch(direction) {
					case HORIZONTAL:
						if(y<=0) {
							throw new InvalidInputException("0<y<="+(this.height-1), "y="+y);
						}
						if(x>=this.width-1) {
							throw new InvalidInputException("0<=x<"+(this.width-1), "x="+x);
						}
						break;
					case VERTICAL:
						if(x<=0) {
							throw new InvalidInputException("0<x<="+(this.width-1), "x="+x);
						}
						if(y>=this.height-1) {
							throw new InvalidInputException("0<=y<"+(this.height-1), "y="+y);
						}
						break;
					}
					player.wallCount--;
					Wall newWall=new Wall(playerId, cell, direction);
					for(Wall wall:walls) {
						if(wall.conflict(newWall)) {
							throw new LostException("InvalidWall", playerId);
						}
					}
					walls.add(newWall);
					player.lastAction=translate("playerPutWall", playerId, cell.x, cell.y, direction);
					
					
					Map<Player, List<Cell>> newWays=new HashMap<>();
					for(Player p:players) {
						if(p.winTime==null && p.deathTime==null) {
							List<Cell> newWay=p.computeMinimalWay(pathFinding, checker);
							if(newWay==null) {
								walls.remove(newWall);
								throw new LostException("BlockForbidden", playerId, cell.x, cell.y, direction, p.id);
							}
							newWays.put(p, newWay);
						}
					}
					for(Entry<Player, List<Cell>> temp:newWays.entrySet()) {
						temp.getKey().setMinimalWay(temp.getValue());
					}
				} else {
					// MOVE
					Direction dir=Direction.valueOf(m.group("direction"));
					Cell cell=dir.move(player.getPosition());
					
					if(!player.canMoveTo(cell)) {
						throw new LostException("InvalidMoveBorder", playerId);
					}
					if(checker.isBlocked(player.getPosition(), cell)) {
						throw new LostException("InvalidMove", playerId);
					}
					player.lastAction=translate("playerMove", playerId, player.getPosition().x, player.getPosition().y, cell.x, cell.y);
					player.addPosition(cell);
					
					player.setMinimalWay(player.computeMinimalWay(pathFinding, checker));
					if(player.win()) {
						WinException e=new WinException("exitReached", playerId);
						player.lastAction=e.getReason();
						player.winTime=frame;
						throw e;
					}
				}
			} else {
				throw new InvalidInputException("(x y H|V) or (UP | RIGHT | DOWN | LEFT)", output);
			}
		} catch(LostException| InvalidInputException e) {
			player.setDead(frame);
			throw e;
		} finally {
			updateScores();
		}
	}
	
	private void updateScores() {
		Player[] sorted=players.clone();
		Arrays.sort(sorted);
		int score=0;
		Player last = null;
		for(Player p:sorted) {
			if(last!=null && p.compareTo(last)>0) {
				score++;
			}
			p.setScore(score);
			last=p;
		}
	}
	
	@Override
	protected String[] getAdditionalFrameDataAtGameStartForView() {
		return new String[]{this.width+" "+this.height+" "+this.players.length};
	}

	@Override
	protected String[] getFrameDataForView(int round) {
		List<String> list=new ArrayList<>();
		for(Player p:players) {
			StringBuffer str=new StringBuffer();
			str.append(p.getPosition().x);
			str.append(" ");
			str.append(p.getPosition().y);
			str.append(" ");
			str.append(p.wallCount);
			str.append(" ");
			str.append(p.deathTime!=null?1:(p.winTime!=null?2:0));
			if(p.minimalway!=null) {
				for(Cell cell:p.minimalway) {
					str.append(" ");
					str.append(cell.x);
					str.append(" ");
					str.append(cell.y);
				}
			}
			list.add(str.toString());
			list.add(p.message == null ? "" : p.message);
		}
		List<String> wallsList=new ArrayList<>();
		for(Wall wall:walls) {
			if(wall.justPlaced) {
				wallsList.add(wall.player+" "+wall.cell.x+" "+wall.cell.y+" "+wall.dir);
			}
		}
		list.add(String.valueOf(wallsList.size()));
		list.addAll(wallsList);
		return list.toArray(new String[list.size()]);
	}

	@Override
	protected int getExpectedOutputLineCountForPlayer(int player) {
		return 1;
	}
	
	
	@Override
	protected String[] getInitInputForPlayer(int player) {
		return new String[]{width+" "+height+" "+this.players.length+" "+player};
	}

	@Override
	protected String[] getInputForPlayer(int round, int player) {
		List<String> list=new ArrayList<>();
		for(Player p:players) {
			if(p.deathTime==null && p.winTime==null) {
				list.add(p.getPosition().toString()+" "+p.wallCount);
			} else {
				list.add("-1 -1 -1");
			}
		}
		list.add(String.valueOf(walls.size()));
		for(Wall wall:walls) {
			list.add(wall.toString());
		}
		return list.toArray(new String[list.size()]);
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
	protected void updateGame(int round) {
	}

	@Override
	protected void prepare(int round) {
	}

	@Override
	protected boolean isPlayerDead(int player) {
		// useless when no asynchronous death
		return false;
	}

	@Override
	protected String getDeathReason(int player) {
		// useless when no asynchronous death
		throw new RuntimeException();
	}

	@Override
	protected int getScore(int player) {
		return players[player].score;
	}

	@Override
	protected String[] getGameSummary(int round) {
		List<String> scores=new ArrayList<>();
		scores.add("Scores:");
		int player=0;
		for(Player p:players) {
			scores.add("$"+(player++)+": "+p.score);
		}
		return scores.toArray(new String[scores.size()]);
	}

	@Override
	protected String[] getPlayerActions(int player) {
		if(players[player].lastAction==null) {
			return null;
		} else {
			return new String[]{players[player].lastAction};
		}
	}

	@Override
	protected void setPlayerTimeout(int frame, int round, int player) {
		players[player].deathTime=frame;
		updateScores();
	}
	
	protected int getMaxRoundCount() {
		return 100;
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
	protected final void handlePlayerOutput(int frame, int round, int player, String[] playerOutput) throws WinException, LostException, InvalidInputException {
		if (player != 0)
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
	protected final String[] getInitInputForPlayer(int player) {
		if (player != 0)
			throw new RuntimeException("SoloReferee could only handle one-player games");
		return getInitInputForPlayer();

	}

	@Override
	protected boolean showTooltips() {
		return false;
	}

	@Override
	protected final String[] getInputForPlayer(int round, int player) {
		if (player != 0)
			throw new RuntimeException("SoloReferee could only handle one-player games");
		return getInputForPlayer(round);
	}

	protected abstract int getExpectedOutputLineCountForPlayer();

	@Override
	protected int getExpectedOutputLineCountForPlayer(int player) {
		if (player != 0)
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
	protected final boolean isPlayerDead(int player) {
		return false;
	}

	@Override
	protected final int getScore(int player) {
		return score;
	}

	protected final String[] getGameSummary(int round) {
		return getTextForConsole(round);
	}

	protected final String[] getPlayerActions(int player) {
		return new String[0];
	}

	protected final void setPlayerTimeout(int player) {
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
	protected void setPlayerTimeout(int frame, int round, int player) {
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
		private boolean lost,win;
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
			return translate(reasonCode, values);
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
	class GameOverException extends Exception {
	}

	@SuppressWarnings("serial")
	class GameErrorException extends Exception {
	}

	public static enum InputCommand {
		INIT, GET_GAME_INFO, SET_PLAYER_OUTPUT, SET_PLAYER_TIMEOUT
	}

	public static enum OutputCommand {
		VIEW, INFOS, SUMMARY, NEXT_PLAYER_INPUT, NEXT_PLAYER_INFO, SCORES, UINPUT, TOOLTIP;
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
		this.messages.put("WinTooltip",  "$%d: victory!");

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
						throw new GameErrorException();
					}

					if (playerCount < getMinimumPlayerCount()) {
						reasonCode = "notEnoughPlayers";
						reason = translate(reasonCode, getMinimumPlayerCount(), playerCount);
						throw new GameOverException();
					}
					break;
				case GET_GAME_INFO:
					lastPlayer = playerStatus;
					playerStatus = nextPlayer();
					if (this.round >= getMaxRoundCount()) {
						reasonCode = "maxRoundsCountReached";
						reason = translate(reasonCode);
						throw new GameOverException();
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
						addToolTip(nextPlayer,translate("InvalidActionTooltip", nextPlayer));
						if (--alivePlayerCount < getMinimumPlayerCount()) {
							lastPlayer = playerStatus;
							throw new GameOverException();
						}
					} catch (WinException e) {
						playerStatus.score = getScore(nextPlayer);
						playerStatus.win = true;
						playerStatus.info = e.getReason();
						playerStatus.reasonCode = e.getReasonCode();
						addToolTip(nextPlayer, translate("WinTooltip", nextPlayer));
						if (--alivePlayerCount < getMinimumPlayerCount()) {
							lastPlayer = playerStatus;
							throw new GameOverException();
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
					addToolTip(nextPlayer,translate("TimeoutTooltip", nextPlayer));

					addToolTip(nextPlayer, "$" + nextPlayer + ": timeout!");
					if (--alivePlayerCount < getMinimumPlayerCount()) {
						lastPlayer = playerStatus;
						throw new GameOverException();
					}
					break;
				}
			}
		} catch (GameOverException e) {
			newRound = true;
			updateScores();
			dumpView();
			dumpInfos();
			prepare(round);
			updateScores();
		} catch (GameErrorException e) {
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
				data.addAll(getAdditionalFrameDataAtGameStartForView());
			}
		} else {
			if (reasonCode != null) {
				data.add(String.format("INTERMEDIATE_FRAME %d %s", this.frame, reasonCode));
			} else {
				data.add(String.format("INTERMEDIATE_FRAME %d", frame));
			}
		}
		try {
			data.addAll(getFrameDataForView(round));
		} catch (Throwable t) {
			t.printStackTrace();
		}

		out.println(data);
	}

	private void dumpInfos() {
		OutputData data = new OutputData(OutputCommand.INFOS);
		if (reason != null) {
			data.add(getColoredReason(true, reason));
		} else {
			if (frame == 0) {
				String head = getHeadlineAtGameStartForConsole();
				if (head != null)
					data.add(head);
			}
			if (lastPlayer != null) {
				String head = lastPlayer.info;
				if (head != null) {
					data.add(getColoredReason(lastPlayer.lost, head));
				} else {
					if (frame > 0)
						data.addAll(getPlayerActions(this.currentPlayer));
				}
			}
		}
		out.println(data);
		
		if (newRound && round >= 0 && playerCount>1) {
			OutputData summary = new OutputData(OutputCommand.SUMMARY);
			summary.addAll(getGameSummary(round));
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
		return String.format((String) messages.get(code), values);
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

	protected int getMaxRoundCount() {
		return 500;
	}

	private void nextRound() throws GameOverException {
		newRound = true;
		if (++round > 0) {
			updateGame(round);
			updateScores();
		}
		if (alivePlayerCount < getMinimumPlayerCount()) {
			throw new GameOverException();
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

	private void addToolTip(int player, String message) {
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

	protected abstract void handleInitInputForReferee(int playerCount, String[] init) throws InvalidFormatException;

	protected abstract String[] getAdditionalFrameDataAtGameStartForView();

	protected abstract String[] getFrameDataForView(int round);

	protected abstract int getExpectedOutputLineCountForPlayer(int player);

	protected abstract String getGameName();

	protected abstract void appendDataToEnd(PrintStream stream) throws IOException;
	/**
	 * 
	 * @param player
	 *            player id
	 * @param output
	 * @return score of the player
	 */
	protected abstract void handlePlayerOutput(int frame, int round, int player, String[] output) throws WinException, LostException, InvalidInputException;
	protected abstract String[] getInitInputForPlayer(int player);
	protected abstract String[] getInputForPlayer(int round, int player);
	protected abstract String getHeadlineAtGameStartForConsole();
	protected abstract int getMinimumPlayerCount();
	protected abstract boolean showTooltips();

	/**
	 * 
	 * @param round
	 * @return scores of all players
	 */
	protected abstract void updateGame(int round);
	protected abstract void prepare(int round);

	protected abstract boolean isPlayerDead(int player);
	protected abstract String getDeathReason(int player);

	protected abstract int getScore(int player);

	protected abstract String[] getGameSummary(int round);
	protected abstract String[] getPlayerActions(int player);
	protected abstract void setPlayerTimeout(int frame, int round, int player);
}