import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

class Enemy {
	double distance, speed, angle, prevDistance;
	boolean dead = false;
	int type;
	String name;
	static int count = 0;
	int id = Enemy.count++;
	
	public static Enemy dummy = new Enemy("Nobody", 9999, 0);

	public Enemy(String[] data, int angle) {
		name = data[0];
		type = Integer.parseInt(data[1]);
		distance = Double.parseDouble(data[2]);
		prevDistance = distance;
		this.angle = angle;
		speed = Double.parseDouble(data[3]);
	}

	public Enemy( String name, int distance, int speed) {
		this.distance = distance;
		this.speed = speed;
		this.name = name;
	}

	public String toString() {
		return String.format("%s %d %.2f %.2f %.2f %d", name, type, distance, prevDistance, angle, id);
	}

	void updatePosition() {
		prevDistance = distance;
		distance -= speed;
	}

	public int getThreat() {
		return (int) (distance - speed);
	}
}

class Referee extends SoloReferee {

	@Override
	protected String getGameName() {
		return "CGD";
	}

	private static final int ALPHA_SEP = 45 / 2;
	private static final int COMBOMAX = 6;
	public static final int HITBOX = 5;

	private static final String REASON_SAFE = "Success: You eliminated the threat";
	private static final String REASON_DEAD = "Failure: You were destroyed";
	private static final int RANGE = 80;

	private LinkedList<String> viewInfo;
	private LinkedList<Enemy> enemies;

	int kills;
	private int enemyCount;

	int score;
	int multiplier;
	int combo;
	protected Referee control;
	private Enemy deadEnemy;
	private int direction;
	private String headLine;

	@Override
	protected void handleInitInputForReferee(String[] initLines) {
		try {
			headLine = null;
			viewInfo = new LinkedList<String>();
			enemies = new LinkedList<>();
			deadEnemy = null;
			score = 0;
			combo = 0;
			multiplier = 1;
			control = this;
			kills = 0;
			enemyCount = Integer.parseInt(initLines[0]);
			direction = 180;
			int maxAlpha = 180 + ALPHA_SEP, minAlpha = 180 - ALPHA_SEP;

			double sep = (maxAlpha - minAlpha) / Math.max(1, enemyCount - 1.0);

			if (enemyCount == 0) {
				throw new Exception("There are no enemies");
			}

			for (int i = 0; i < enemyCount; i++) {
				String[] data = initLines[i + 1].split(" ");
				Enemy e = new Enemy(data, (int) (minAlpha + sep * i));
				enemies.add(e);
			}

		} catch (Exception e) {
			lost = true;
			reason = "Invalid test input: " + e;
			e.printStackTrace();
		}
	}

	public void spawn(Enemy e) {
		enemies.add(e);
	}

	public void gainPoints(int n) {
		kills++;
		score += n * multiplier;
		combo++;
		if (combo % COMBOMAX == 0) {
			multiplier++;
		}
	}

	@Override
	protected void handlePlayerOutput(String[] playerOutput) {
		// Should set variable "win", "lost" if needed. If "win" or "lost" are
		// true, "reason" and "reasonCode" must be set.
		try {
			Enemy targ = null;
			deadEnemy = null;
			String name = playerOutput[0];
			headLine = null;

			if (getVisibleEnemies().size() == 0) {
				headLine = "No enemy ship within range.";
			} else if (!validName(name)) {
				headLine = "Your output '" + name + "' is not one of the remaining ship names.";
			} else {
				// List<Enemy> potentialTargs = getTargetEnemies();
				List<Enemy> potentialTargs = enemies;

				for (Enemy e : potentialTargs) {
					if (e.name.equals(name)) {
						targ = e;
						break;
					}
				}
			}

			if (targ != null) {
				enemies.remove(targ);
				deadEnemy = targ;
				direction = (int) (deadEnemy.angle);
				gainPoints(10);
			}

			for (Enemy e : enemies) {
				e.updatePosition();
				if (e.distance <= HITBOX) {
					e.distance = Math.max(0, e.distance);
					lost = true;
					reasonCode = "DEAD";
					reason = REASON_DEAD;
				}
			}
			if (deadEnemy != null)
				deadEnemy.updatePosition();
			if (enemies.size() == 0) {
				win = true;
				reasonCode = "SAFE";
				reason = REASON_SAFE;
			}
		} catch (Exception e) {
			lost = true;
			reason = "Invalid output : " + e.toString();
			e.printStackTrace();
		}
	}

	private boolean validName(String name) {
		for (Enemy e : enemies) {
			if (e.name.equals(name) && ((int) (e.distance - e.speed) < RANGE))
				return true;
		}
		return false;
	}

	public static double directionBetween(double x1, double y1, double x2, double y2) {
		double dy = y2 - y1;
		double dx = x2 - x1;
		double res = Math.atan2(-dy, dx);

		if (res < 0)
			res += 2 * Math.PI;
		return res;
	}

	@Override
	protected String[] getInitInputForPlayer() {
		return new String[0];
	}

	@Override
	protected String[] getInputForPlayer() {
		LinkedList<String> res = new LinkedList<String>();
		LinkedList<Enemy> visibleEnemies = getVisibleEnemies();
		Enemy e1 =  null;
		Enemy e2 =  null;
		Collections.sort(visibleEnemies, new Comparator<Enemy>() {
			@Override
			public int compare(Enemy a, Enemy b) {
				return a.getThreat() - b.getThreat();
			}
		});
		
		List<Enemy> enemiesForPlayer = new LinkedList<>();
		
		if (visibleEnemies.size() > 0)
			e1 = visibleEnemies.get(0);
		else
			e1 = Enemy.dummy;
		enemiesForPlayer.add(e1);		
		
		if (visibleEnemies.size() > 1)
			e2 = visibleEnemies.get(1);
		else
			e2 = Enemy.dummy;
		enemiesForPlayer.add(e2);
		
		Collections.sort(enemiesForPlayer, new Comparator<Enemy>() {
			@Override
			public int compare(Enemy a, Enemy b) {
				return a.id - b.id;
			}
		});
		e1 = enemiesForPlayer.get(0);
		e2 = enemiesForPlayer.get(1);
		res.add(e1.name);
		res.add(String.valueOf(e1.getThreat()));
		res.add(e2.name);
		res.add(String.valueOf(e2.getThreat()));
		
		return res.toArray(new String[0]);
	}
	private LinkedList<Enemy> getVisibleEnemies() {
		LinkedList<Enemy> res = new LinkedList<>();

		for (Enemy e : enemies) {
			if (e.getThreat() < RANGE) {
				res.add(e);
			}
		}
		return res;
	}

	public static double distanceBetween(double x, double y, double i, double j) {
		return Math.sqrt(Math.pow((x - i), 2) + Math.pow((y - j), 2));
	}

	@Override
	protected String getHeadlineAtGameStartForConsole() {
		// line in the console for round 0. May be null.
		return enemyCount + " threats approaching fast !";
	}

	@Override
	protected String getHeadlineForConsole() {
		// first line in the console. May be null.
		// Not used when win or lost is set
		if (deadEnemy != null) {
			return deadEnemy.name + " has been targeted";
		} else if (headLine != null) {
			return headLine;
		}
		return null;
	}

	private List<Enemy> getClosestEnemies() {
		LinkedList<Enemy> res = new LinkedList<>();
		double minDist = 0;
		Enemy minEnemy = null;
		for (Enemy e : enemies) {
			if (minEnemy == null || (e.distance - e.speed) < minDist) {
				res.clear();
				minEnemy = e;
				minDist = e.distance - e.speed;
			}
			if (e.distance - e.speed == minDist) {
				res.add(e);
			}
		}
		return res;
	}

	@Override
	protected String[] getTextForConsole() {
		if (reasonCode == "DEAD") {
			Enemy killedBy = getClosestEnemies().get(0);
			String[] out = new String[1];
			out[0] = "You were hit by " + killedBy.name;
			return out;

		} else {
			LinkedList<Enemy> visibleEnemies = getVisibleEnemies();
			LinkedList<Enemy> firstEnemies = new LinkedList<>();
			Collections.sort(visibleEnemies, new Comparator<Enemy>() {
				@Override
				public int compare(Enemy a, Enemy b) {
					return a.getThreat() - b.getThreat();
				}
			});
			
			if (visibleEnemies.size() > 0)
				firstEnemies.add(visibleEnemies.get(0));
			if (visibleEnemies.size() > 1)
				firstEnemies.add(visibleEnemies.get(1));
			
			String[] out = new String[firstEnemies.size() + 1];
			if (firstEnemies.isEmpty()) {
				out[0] = "No ships remaining.";
			} else {
				out[0] = "Threats within range:";
			}
			for (int i = 0; i < firstEnemies.size(); i++) {
				Enemy e = firstEnemies.get(i);
				out[i + 1] = e.name + " " + e.getThreat() + "m";
			}
			return out;
		}
	}

	@Override
	protected String[] getAdditionalFrameDataAtGameStartForView() {

		return new String[0];
	}

	@Override
	protected String[] getFrameDataForView() {
		viewInfo.clear();
		int count = enemies.size();
		for (Enemy e : enemies) {
			viewInfo.add(e.toString());
		}
		if (deadEnemy == null) {
			viewInfo.add("0");
		} else {
			viewInfo.add(deadEnemy.toString());
			count++;
			viewInfo.add("1");
		}
		viewInfo.addFirst(Integer.toString(count));
		viewInfo.add(Integer.toString(direction));

		viewInfo.add(Integer.toString(score));
		viewInfo.add(Integer.toString(kills) + " " + getVisibleEnemies().size());

		return viewInfo.toArray(new String[viewInfo.size()]);
	}

	@Override
	protected int getMillisTimeForFirstRound() {
		// return 600000;
		return super.getMillisTimeForFirstRound();
	}

	@Override
	protected int getMillisTimeForRound() {
		// return 600000;
		return super.getMillisTimeForRound();
	}

	@Override
	protected String getLostTimeoutReason() {
		return super.getLostTimeoutReason();
	}

	@Override
	protected int getPlayerNbExpectedOutputLines() {
		return super.getPlayerNbExpectedOutputLines();
	}

	public static void main(String[] args) {

		try {
			new Referee(System.in, System.out, System.err);
		} catch (Throwable t) {
			try {
				PrintStream ps = new PrintStream(new FileOutputStream(LOG_FILE));
				t.printStackTrace(ps);
				ps.close();
				t.printStackTrace();
			} catch (IOException ioe) {
				System.err.println("I die and the world will not know");
				t.printStackTrace();
			}
		}
	}

	public Referee(InputStream in, PrintStream out, PrintStream err) throws Exception {
		super(in, out, err);
	}
}

abstract class SoloReferee {

	protected static final File LOG_FILE = new File("/tmp/referee.log");

	private PrintStream out;
	// private PrintStream err;

	int nbInitLines = 0;
	String[] initLines;
	String initHeader;
	String initInput;

	boolean firstRound = true;
	int nbRounds = 0;
	String[] lastOutput;

	boolean win = false;
	boolean lost = false;
	String reason = null;
	String reasonCode = null;

	private static String LOST_PARSING_REASON = "Failure: invalid input";
	private static String LOST_TIMEOUT_REASON = "Timeout: your program did not provide an input in due time.";

	private static final String LOST_PARSING_REASON_CODE = "INPUT";
	private static final String LOST_TIMEOUT_REASON_CODE = "TIMEOUT";

	protected abstract String getGameName();

	protected abstract void handlePlayerOutput(final String[] playerOutput);

	protected abstract void handleInitInputForReferee(final String[] initLines);

	protected abstract String[] getInitInputForPlayer();

	protected abstract String[] getInputForPlayer();

	protected abstract String getHeadlineAtGameStartForConsole();

	protected abstract String getHeadlineForConsole();

	protected abstract String[] getTextForConsole();

	protected abstract String[] getAdditionalFrameDataAtGameStartForView();

	protected abstract String[] getFrameDataForView();

	protected SoloReferee(InputStream in, PrintStream out, PrintStream err) throws IOException {
		this.out = out;
		// this.err = err;
		start(in);
	}

	protected int getMillisTimeForFirstRound() {
		return 1000;
	}

	protected int getMillisTimeForRound() {
		return 150;
	}

	protected String getLostTimeoutReason() {
		return LOST_TIMEOUT_REASON;
	}

	protected int getPlayerNbExpectedOutputLines() {
		return 1;
	}

	protected final void start(InputStream is) throws IOException {
		Scanner s = new Scanner(is);
		while (true) {
			Command c = parseCommand(s);
			switch (c.id) {
			case Command_INIT.ID:
				initGame((Command_INIT) c);
				break;
			case Command_GET_GAME_INFO.ID:
				getGameInfo((Command_GET_GAME_INFO) c);
				break;
			case Command_SET_PLAYER_OUTPUT.ID:
				setPlayerOuput((Command_SET_PLAYER_OUTPUT) c);
				break;
			case Command_SET_PLAYER_TIMEOUT.ID:
				setPlayerTimeout((Command_SET_PLAYER_TIMEOUT) c);
				break;
			}
		}
	}

	protected final int round(double d) {
		if (d < 0)
			return (int) (d - 0.5);
		else
			return (int) (d + 0.5);
	}

	private void initGame(Command_INIT c) throws IOException {
		try {
			initLines = c.initLines;
			nbInitLines = initLines.length;
			handleInitInputForReferee(initLines);
		} catch (Exception ex) {
			// Parsing failed: assume input is bad
			lost = true;
			reason = LOST_PARSING_REASON;
			reasonCode = LOST_PARSING_REASON_CODE;
		}
	}

	private void setPlayerTimeout(Command_SET_PLAYER_TIMEOUT c) {
		lost = true;
		reason = getLostTimeoutReason();
		reasonCode = LOST_TIMEOUT_REASON_CODE;
	}

	private void setPlayerOuput(Command_SET_PLAYER_OUTPUT c) {
		lastOutput = c.output;
		handlePlayerOutput(lastOutput);
	}

	private void getGameInfo(Command_GET_GAME_INFO c) {
		String frame = "KEY_FRAME " + nbRounds;

		if (win || lost) {
			String coloredReason = lost ? ("¤RED¤" + reason + "§RED§") : ("¤GREEN¤" + reason + "§GREEN§");
			if (reasonCode == LOST_PARSING_REASON_CODE) {
				dumpResponse("VIEW", "KEY_FRAME -1", reasonCode);
				StringBuilder inputError = new StringBuilder();
				for (String initLine : initLines) {
					inputError.append(initLine);
					inputError.append('\n');
				}
				dumpResponse("INFOS", coloredReason, inputError.toString());
			} else {
				dumpResponse("VIEW", frame + " " + reasonCode, getFrameDataForView());
				dumpResponse("INFOS", coloredReason, getTextForConsole());
			}
			dumpResponse("SCORES", "0 " + (win ? "1" : "0"));
		} else if (firstRound) {
			String[] initInput = getInitInputForPlayer();
			dumpResponse("VIEW", frame, getGameName(), getAdditionalFrameDataAtGameStartForView(), getFrameDataForView());
			dumpResponse("INFOS", getHeadlineAtGameStartForConsole(), getTextForConsole());
			dumpResponse("NEXT_PLAYER_INFO", "0", "" + getPlayerNbExpectedOutputLines(), "" + getMillisTimeForFirstRound());
			dumpResponse("NEXT_PLAYER_INPUT", initInput, getInputForPlayer());
			firstRound = false;
		} else {
			dumpResponse("VIEW", frame, getFrameDataForView());
			dumpResponse("INFOS", getHeadlineForConsole(), getTextForConsole());
			dumpResponse("NEXT_PLAYER_INFO", "0", "" + getPlayerNbExpectedOutputLines(), "" + getMillisTimeForRound());
			dumpResponse("NEXT_PLAYER_INPUT", getInputForPlayer());
		}
		nbRounds++;
	}

	private void dumpResponse(String id, String first, String second, String... next) {
		dumpResponse(id, new String[] { first, second }, next);
	}

	private void dumpResponse(String id, String first, String[] second) {
		dumpResponse(id, new String[] { first }, second);
	}

	private void dumpResponse(String id, String first, String second) {
		dumpResponse(id, new String[] { first, second });
	}

	private void dumpResponse(String id, String first, String second, String[] third, String[] fourth) {
		dumpResponse(id, new String[] { first, second }, third, fourth);
	}

	private void dumpResponse(String id, String first) {
		dumpResponse(id, new String[] { first });
	}

	private void dumpResponse(String id, String[]... outputs) {
		int nbLines = 0;
		for (String[] output : outputs) {
			if (output != null) {
				for (String s : output) {
					if (s != null) {
						for (int i = 0, il = s.length(); i < il; i++) {
							char c = s.charAt(i);
							if (c == '\n') {
								nbLines++;
							}
						}
						if (s.charAt(s.length() - 1) != '\n') {
							nbLines++;
						}
					}
				}
			}
		}

		out.println("[[" + id + "] " + nbLines + "]");
		for (String[] output : outputs) {
			if (output != null) {
				for (String s : output) {
					if (s != null) {
						out.print(s);
						if (s.charAt(s.length() - 1) != '\n') {
							out.print('\n');
						}
					}
				}
			}
		}
	}

	private Command parseCommand(Scanner s) {
		String line = s.nextLine();
		int firstClose = line.indexOf(']');
		String cmd = line.substring(2, firstClose);
		int nbLines = Integer.parseInt(line.substring(firstClose + 2, line.length() - 1));
		switch (cmd) {
		case Command_INIT.STRID:
			return new Command_INIT(s, nbLines);
		case Command_GET_GAME_INFO.STRID:
			return new Command_GET_GAME_INFO();
		case Command_SET_PLAYER_OUTPUT.STRID:
			return new Command_SET_PLAYER_OUTPUT(s, nbLines);
		case Command_SET_PLAYER_TIMEOUT.STRID:
			return new Command_SET_PLAYER_TIMEOUT();
		}
		return null;
	}

	public static class Command {
		int id;

		public Command(int id) {
			this.id = id;
		}
	}

	public static class Command_INIT extends Command {

		static final String STRID = "INIT";
		static final int ID = 0;

		int nbPlayers;
		String[] initLines;

		public Command_INIT(Scanner s, int nbLines) {
			super(ID);
			nbPlayers = s.nextInt();
			s.nextLine();
			if (nbLines > 1) {
				initLines = new String[nbLines - 1];
				for (int i = 0; i < (nbLines - 1); i++) {
					initLines[i] = s.nextLine();
				}
			} else {
				initLines = new String[0];
			}
		}
	}

	public static class Command_GET_GAME_INFO extends Command {

		static final String STRID = "GET_GAME_INFO";
		static final int ID = 1;

		public Command_GET_GAME_INFO() {
			super(ID);
		}
	}

	public static class Command_SET_PLAYER_OUTPUT extends Command {

		static final String STRID = "SET_PLAYER_OUTPUT";
		static final int ID = 2;

		String[] output;

		public Command_SET_PLAYER_OUTPUT(Scanner s, int nbLines) {
			super(ID);
			output = new String[nbLines];
			for (int i = 0; i < nbLines; i++) {
				output[i] = s.nextLine();
			}
		}
	}

	public static class Command_SET_PLAYER_TIMEOUT extends Command {

		static final String STRID = "SET_PLAYER_TIMEOUT";
		static final int ID = 3;

		public Command_SET_PLAYER_TIMEOUT() {
			super(ID);
		}
	}
}