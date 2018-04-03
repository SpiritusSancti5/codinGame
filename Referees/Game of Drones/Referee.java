import java.awt.Point;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Scanner;

class Referee {

	private int width = 4000;
	private int height = 1800;
	private int speed = 100;
	// private int zoneEnergy = 10;
	private int zoneRadius = 100;

	private int zoneUpMargin = 250;
	private int zoneRightMargin = 350;

	Random rand = null;

	private boolean end = false;
	private String reason = null;
	private String reasonCode = null;

	private int maxRounds = 200;
	private int currentPlayerId = -1;

	int nbPlayers;
	int nbZones;
	int nbDronesPerPlayer;

	private String saveUserInput = "";

	private static class PlayerData {
		int score;
		boolean lost = false;
		String info = null;
		int[] dx;
		int[] dy;
		float[] angle;
		double[] rx;
		double[] ry;
	}

	private static class ZoneData {
		// Rectangle zone;
		int playerId = -1;
		// int energy = 0;
		int attackerId = -1;
		int[] count;
		Point center;
	}

	PlayerData[] playersData;
	ZoneData[] zones;

	// For debugging
	private static final File LOG_FILE = new File("/tmp/referee.log");

	public static void main(String[] args) {
		try {
			new Referee().start(System.in);
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

	public void start(InputStream is) throws IOException {
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

	private static final String LOST_PARSING_REASON = "Failure: invalid input";
	private static final String OUT_OF_BOUNDS = "Failure: an input value is out of bounds";
	private static final String LOST_PARSING_REASON_CODE = "INPUT";

	Command_INIT ci;

	private void initGame(Command_INIT c) throws IOException {

		nbPlayers = c.nbPlayers;
		nbCurrentPlayers = nbPlayers;
		playersData = new PlayerData[nbPlayers];
		if (c.initLines.length == 0) {
			long seed = System.currentTimeMillis();
			rand = new Random(seed);
			nbZones = nbPlayers * 2;
			nbDronesPerPlayer = 3 + (2 * rand.nextInt(5));
			saveUserInput += "nbZones=" + nbZones + "\n";
			saveUserInput += "nbDrones=" + nbDronesPerPlayer + "\n";
			saveUserInput += "gameSeed=" + seed + "\n";
			generateInput();
			return;
		}
		// } else {
		// nbZones = nbPlayers * 2;
		// nbDronesPerPlayer = 3 + (2*2);
		// generateInput();
		// return;
		// }

		try {
			int line = 0;
			long seed = 0;
			String inToStr;
			String[] parseInputArray = new String[3];
			String[] parsedInput = new String[3];
			Scanner input = new Scanner(c.initLines[line++]);
			input.useLocale(Locale.US);
			inToStr = input.nextLine();
			parseInputArray[0] = inToStr;
			input = new Scanner(c.initLines[line++]);
			inToStr = input.nextLine();
			parseInputArray[1] = inToStr;
			input = new Scanner(c.initLines[line++]);
			inToStr = input.nextLine();
			parseInputArray[2] = inToStr;
			for (int i = 0; i != 3 && i < parseInputArray.length; i++) {
				parsedInput[i] = parseInputArray[i].split("=")[1];
			}
			nbZones = Integer.parseInt(parsedInput[0]);
			nbDronesPerPlayer = Integer.parseInt(parsedInput[1]);
			seed = Long.parseLong(parsedInput[2]);
			input.close();
			saveUserInput += "nbZones=" + nbZones + "\n";
			saveUserInput += "nbDrones=" + nbDronesPerPlayer + "\n";
			saveUserInput += "gameSeed=" + seed + "\n";
			if (nbDronesPerPlayer > 20 || nbDronesPerPlayer < 1 || nbZones > 8
					|| nbZones < 1) {
				reason = OUT_OF_BOUNDS;
				throw new Exception();
			}
			rand = new Random(seed);
			rand.nextInt(5);
			generateInput();
			return;
			// zones = new ZoneData[nbZones];
			// // Next NB_ZONES lines: ZX ZY
			// for (int z = 0; z < nbZones; z++) {
			// zones[z] = new ZoneData();
			// input = new Scanner(c.initLines[line++]);
			// input.useLocale(Locale.US);
			// zones[z].center = new Point(input.nextInt(), input.nextInt());
			// input.close();
			// zones[z].count = new int[nbPlayers];
			// }
			// for (int p = 0; p < nbPlayers; p++) {
			// PlayerData pd = new PlayerData();
			// playersData[p] = pd;
			// pd.dx = new int[nbDronesPerPlayer];
			// pd.dy = new int[nbDronesPerPlayer];
			// pd.angle = new float[nbDronesPerPlayer];
			// for (int d = 0; d < nbDronesPerPlayer; d++) {
			// input = new Scanner(c.initLines[line++]);
			// input.useLocale(Locale.US);
			// pd.dx[d] = input.nextInt();
			// pd.dy[d] = input.nextInt();
			// pd.rx[d] = pd.dx[d];
			// pd.ry[d] = pd.dy[d];
			// input.close();
			// }
			// playersData[p] = pd;
			// }
		} catch (Exception ex) {
			// Parsing failed: assume input is bad
			end = true;
			if (reason != OUT_OF_BOUNDS) {
				reason = LOST_PARSING_REASON;
			}
			reasonCode = LOST_PARSING_REASON_CODE;
			ci = c;
		}
	}

	private int nbRounds = 0;
	private int nbIntermediate = 0;
	private int nbGlobalRounds = 0;
	private int nbCurrentPlayers;
//	private List<String> turnMessages = new ArrayList<>();

	private void getGameInfo(Command_GET_GAME_INFO c) {
		if (end && reasonCode == LOST_PARSING_REASON_CODE) {
			String coloredReason = ("¤RED¤" + reason + "§RED§");
			if (reasonCode == LOST_PARSING_REASON_CODE) {
				dumpResponse("VIEW", "KEY_FRAME -1", reasonCode);
				StringBuilder inputError = new StringBuilder();
				for (String initLine : ci.initLines) {
					inputError.append(initLine);
					inputError.append('\n');
				}
				dumpResponse("INFOS", coloredReason, inputError.toString());
			}
			dumpScores();
			return;
		}
		if (nbIntermediate == nbCurrentPlayers) {
			nbIntermediate = 0;
			updateScores();
			nbCurrentPlayers = 0;
			for (int p = 0; p < nbPlayers; p++) {
				if (playersData[p].lost != true) {
					nbCurrentPlayers++;
				}
			}
			nbGlobalRounds++;
		}

		boolean key = (nbIntermediate == 0);

		String frame = (key ? "KEY_FRAME" : "INTERMEDIATE_FRAME") + " "
				+ nbRounds;

		boolean scores = (nbCurrentPlayers == 0)
				|| (nbGlobalRounds == maxRounds);

		int lastPlayerId = currentPlayerId;

		if (nbCurrentPlayers != 0) {
			do {
				currentPlayerId = (currentPlayerId + 1) % nbPlayers;
			} while (playersData[currentPlayerId].lost);
			currentPlayer = playersData[currentPlayerId];
		}

		String params = buildPlayerInput();
		String viewParams = buildViewParams();
		List<String> infos = buildPlayerInfo(lastPlayerId, key);
		List<String> summary=null;
		if(key) {
			summary=new ArrayList<String>();
			for (int p = 0; p < nbPlayers; p++) {
				PlayerData player = playersData[p];
				if (player.lost) {
					summary.add("$"+ p+ " no longer plays in this game. (current Score = "+ player.score + ")");
				} else {
					summary.add("$" + p + " score: " + player.score);
				}
			}
			for (int z = 0; z < nbZones; z++) {
				if (zones[z].playerId == -1) {
					summary.add("Zone " + z + " is neutral");
				} else {
					summary.add("Zone " + z + " belongs to " + "$"+ zones[z].playerId);
				}
			}
			
		}
		if (end) {
			String coloredReason = ("¤RED¤" + reason + "§RED§");
			if (reasonCode == LOST_PARSING_REASON_CODE) {
				dumpResponse("VIEW", "KEY_FRAME -1", reasonCode);
				StringBuilder inputError = new StringBuilder();
				for (String initLine : ci.initLines) {
					inputError.append(initLine);
					inputError.append('\n');
				}
				dumpResponse("INFOS", coloredReason, inputError.toString());
				if(summary!=null) dumpResponse("SUMMARY", summary.toArray(new String[summary.size()]));
			} else {
				dumpResponse("VIEW", frame, viewParams, reasonCode);
				dumpResponse("INFOS", coloredReason);
				if(summary!=null) dumpResponse("SUMMARY", summary.toArray(new String[summary.size()]));
			}
			dumpScores();
		} else if (nbGlobalRounds == 0) {
			if (nbRounds == 0) {
				dumpResponse("VIEW", frame, "GOD", buildInitView(),
						buildInitInput(-1), viewParams);
			} else {
				dumpResponse("VIEW", frame, viewParams);
			}
			dumpResponse("INFOS", infos.toArray(new String[infos.size()]));
			if(summary!=null) dumpResponse("SUMMARY", summary.toArray(new String[summary.size()]));
			dumpResponse("NEXT_PLAYER_INPUT", buildInitInput(currentPlayerId),
					params);
			dumpResponse("NEXT_PLAYER_INFO", "" + currentPlayerId, ""
					+ nbDronesPerPlayer, "1000");
		} else if (scores) {
			dumpResponse("VIEW", frame, viewParams);
			// TODO compute winner
			dumpResponse("INFOS", infos.toArray(new String[infos.size()]));
			if(summary!=null) dumpResponse("SUMMARY", summary.toArray(new String[summary.size()]));
			dumpScores();
		} else if (key) {
			dumpResponse("VIEW", frame, viewParams);
			dumpResponse("INFOS", infos.toArray(new String[infos.size()]));
			if(summary!=null) dumpResponse("SUMMARY", summary.toArray(new String[summary.size()]));
			dumpResponse("NEXT_PLAYER_INFO", "" + currentPlayerId, ""
					+ nbDronesPerPlayer, "150");
			dumpResponse("NEXT_PLAYER_INPUT", params);
		} else {
			dumpResponse("VIEW", frame, viewParams);
			dumpResponse("INFOS", infos.toArray(new String[infos.size()]));
			if(summary!=null) dumpResponse("SUMMARY", summary.toArray(new String[summary.size()]));
			dumpResponse("NEXT_PLAYER_INFO", "" + currentPlayerId, ""
					+ nbDronesPerPlayer, "150");
			dumpResponse("NEXT_PLAYER_INPUT", params);
		}
		if (key) {
			nbRounds++;
		}
	}

	private String buildInitView() {
		return "" + width + " " + height + " " + zoneRadius;
	}

	private String buildInitInput(int playerId) {
		StringBuilder sb = new StringBuilder();
		sb.append(nbPlayers + " " + playerId + " " + nbDronesPerPlayer + " "
				+ nbZones + "\n");
		for (ZoneData zoneData : zones) {
			sb.append(zoneData.center.x + " " + zoneData.center.y + "\n");
		}
		return sb.toString();
	}

	private String buildPlayerInput() {
		StringBuilder sb = new StringBuilder();
		for (ZoneData zoneData : zones) {
			sb.append(zoneData.playerId).append('\n');
		}
		for (PlayerData player : playersData) {
			for (int d = 0; d < nbDronesPerPlayer; d++) {
				sb.append(player.dx[d]).append(' ').append(player.dy[d])
						.append('\n');
			}
		}
		return sb.toString();
	}

	private List<String> buildPlayerInfo(int playerId, boolean key) {
		List<String> infos = new ArrayList<>();
		if (nbRounds == 0) {
			infos.add("Game starting...");
		} else {
			String info = playersData[playerId].info;
			if (info != null) {
				infos.add(info);
				playersData[playerId].info = null;
			} else {
				infos.add("$" + playerId + " moved their drones");
			}
		}

		return infos;
	}

	private String buildViewParams() {
		StringBuilder sb = new StringBuilder();
		for (int p = 0; p < nbPlayers; p++) {
			sb.append(playersData[p].score).append('\n');
		}
		for (int z = 0; z < nbZones; z++) {
			// sb.append(zones[z].playerId).append(' ').append(zones[z].energy).append('\n');
			sb.append(zones[z].playerId).append('\n');
			for (int p = 0; p < nbPlayers; p++) {
				sb.append(zones[z].count[p]).append('\n');
			}
		}
		for (int p = 0; p < nbPlayers; p++) {
			PlayerData player = playersData[p];
			for (int d = 0; d < nbDronesPerPlayer; d++) {
				sb.append(player.dx[d]).append(' ').append(player.dy[d])
						.append(' ').append("" + player.angle[d]).append('\n');
			}
		}
		return sb.toString();
	}

	private void dumpScores() {
		String[] playerScores = new String[nbPlayers];
		for (int i = 0; i < nbPlayers; i++) {
			if (reasonCode == LOST_PARSING_REASON_CODE) {
				playerScores[i] = i + " " + 0;
			} else {
				playerScores[i] = i + " " + playersData[i].score;
			}
		}

		if (saveUserInput != null && reasonCode != LOST_PARSING_REASON_CODE) {
			dumpResponse("UINPUT", saveUserInput);
		}

		dumpResponse("SCORES", playerScores);
	}

	PlayerData currentPlayer;

	private void setPlayerOuput(Command_SET_PLAYER_OUTPUT c) {

		nbIntermediate++;

		// Read drones requested positions and move drones according to fixed
		// speed
		for (int i = 0; i < nbDronesPerPlayer; i++) {
			String[] coords = c.output[i].split(" ");

			int x = 0;
			int y = 0;
			boolean invalid = false;

			try {
				x = Integer.parseInt(coords[0]);
				y = Integer.parseInt(coords[1]);
				if ((x < 0) || (x >= width) || (y < 0) || (y >= height)) {
					invalid = true;
				}
			} catch (Exception nfe) {
				invalid = true;
			}
			if (invalid) {
				currentPlayer.lost = true;
				currentPlayer.info = "¤RED¤Failure: invalid input§RED§. At line "
						+ i
						+ ": expected two integers with values between [0 and "
						+ (width - 1) + "] and [0 and " + (height - 1) + "]";
				return;
			}

			// compute next position using doubles (rounded to int afterwards)
			if ((x != currentPlayer.dx[i]) || (y != currentPlayer.dy[i])) {
				double curX = currentPlayer.rx[i];
				double curY = currentPlayer.ry[i];
				double diffX = (x - curX);
				double diffY = (y - curY);
				double square = (diffX * diffX) + (diffY * diffY);
				if (square <= (speed * speed)) {
					currentPlayer.rx[i] = x;
					currentPlayer.ry[i] = y;
				} else {
					double norm = Math.sqrt(square);
					currentPlayer.rx[i] = curX + ((speed * diffX) / norm);
					currentPlayer.ry[i] = curY + ((speed * diffY) / norm);
				}
				double w = currentPlayer.rx[i] - curX;
				double h = currentPlayer.ry[i] - curY;
				if ((w != 0) || (h != 0)) {
					currentPlayer.angle[i] = (float) Math.atan2(h, w);
				}
			}
		}

	}

	private void updateScores() {
		// Compute zones color
		// Step 1: count players'drone in each zones
		for (int p = 0; p < nbPlayers; p++) {
			for (int z = 0; z < nbZones; z++) {
				zones[z].count[p] = 0;
			}
			PlayerData playerData = playersData[p];
			for (int d = 0; d < nbDronesPerPlayer; d++) {
				playerData.dx[d] = (int) (playerData.rx[d] + 0.5);
				playerData.dy[d] = (int) (playerData.ry[d] + 0.5);
				for (int z = 0; z < nbZones; z++) {
					if (zones[z].center.distance(playerData.dx[d],
							playerData.dy[d]) <= zoneRadius) {
						zones[z].count[p]++;
					}
				}
			}
		}
		// For each zone let's see if someone is first (with no equality)
		for (int z = 0; z < nbZones; z++) {
			int max = 0;
			int playerId = -1;
			int[] count = zones[z].count;
			for (int i = 0; i < nbPlayers; i++) {
				if (count[i] > max) {
					max = count[i];
					playerId = i;
				} else if (max == count[i]) {
					playerId = -1;
				}
			}
			// if (playerId != -1) {
			// // Someone is first, let's if it changes the zone color
			// if (zones[z].energy == 0) {
			// zones[z].energy = zoneEnergy;
			// zones[z].playerId = playerId;
			// playersData[playerId].score++;
			// // TODO gain of zone
			// } else if (zones[z].playerId == playerId) {
			// zones[z].energy = zoneEnergy;
			// playersData[playerId].score++;
			// } else {
			// zones[z].energy--;
			// if (zones[z].energy == 0) {
			// zones[z].energy = zoneEnergy;
			// zones[z].playerId = playerId;
			// playersData[playerId].score++;
			// // TODO loss and gain of zone
			// }
			// }
			// }

			if (playerId != -1) {
				// Someone is first, let's if it changes the zone color
				zones[z].playerId = playerId;
			}

			// Update scores according to zone owner
			if (zones[z].playerId != -1) {
				playersData[zones[z].playerId].score++;
			}
		}
	}

	private void setPlayerTimeout(Command_SET_PLAYER_TIMEOUT c) {
		currentPlayer.lost = true;
		currentPlayer.info = "¤RED¤Timeout: the program did not provide "
				+ nbDronesPerPlayer + " input lines in due time...§RED§ $"
				+ currentPlayerId + " will no longer be active in this game.";
		nbIntermediate++;
	}

	private void generateInput() {
		playersData = new PlayerData[nbPlayers];
		for (int p = 0; p < nbPlayers; p++) {
			PlayerData player = playersData[p] = new PlayerData();
			player.dx = new int[nbDronesPerPlayer];
			player.dy = new int[nbDronesPerPlayer];
			player.angle = new float[nbDronesPerPlayer];
			player.rx = new double[nbDronesPerPlayer];
			player.ry = new double[nbDronesPerPlayer];
		}
		zones = new ZoneData[nbZones];
		for (int z = 0; z < nbZones; z++) {
			zones[z] = new ZoneData();
			zones[z].count = new int[nbPlayers];
		}

		for (int i = 0; i < nbZones; i++) {
			boolean intersect = false;
			do {
				intersect = false;
				int x = rand.nextInt((width - zoneRightMargin)
						- (2 * zoneRadius))
						+ zoneRadius;
				int y = rand
						.nextInt((height - zoneUpMargin) - (2 * zoneRadius))
						+ zoneRadius + zoneUpMargin;
				for (int j = 0; j < i; j++) {
					if (zones[j].center.distance(x, y) < (zoneRadius * 7)) {
						intersect = true;
						break;
					}
				}
				if (!intersect) {
					zones[i].center = new Point(x, y);
				}
			} while (intersect);
		}

		for (int i = 0; i < nbDronesPerPlayer; i++) {
			boolean intersect = false;
			do {
				intersect = false;
				int x = rand.nextInt(width);
				int y = rand.nextInt(height);
				for (int j = 0; j < nbZones; j++) {
					if (zones[j].center.distance(x, y) <= zoneRadius) {
						intersect = true;
						break;
					}
				}
				if (!intersect) {
					// new starting spot for each players
					for (int k = 0; k < nbPlayers; k++) {
						playersData[k].dx[i] = x;
						playersData[k].dy[i] = y;
						playersData[k].rx[i] = x;
						playersData[k].ry[i] = y;
					}
				}
			} while (intersect);
		}
	}

	private void dumpResponse(String id, String... output) {
		int nbLines = 0;
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

		System.out.println("[[" + id + "] " + nbLines + "]");
		for (String s : output) {
			if (s != null) {
				System.out.print(s);
				if (s.charAt(s.length() - 1) != '\n') {
					System.out.print('\n');
				}
			}
		}
	}

	private Command parseCommand(Scanner s) {
		String line = s.nextLine();
		int firstClose = line.indexOf(']');
		String cmd = line.substring(2, firstClose);
		int nbLines = Integer.parseInt(line.substring(firstClose + 2,
				line.length() - 1));
		switch (cmd) {
		case Command_INIT.STRID: {
			System.err.print(nbLines);
			return new Command_INIT(s, nbLines);
		}
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
			System.err.print(nbLines);
			int i = 0;
			if (nbLines > 1) {
				initLines = new String[nbLines - 1];
				for (i = 0; i < (nbLines - 1); i++) {
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