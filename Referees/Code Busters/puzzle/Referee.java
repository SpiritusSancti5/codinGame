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
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//TODO: to each entity its id

class Referee extends MultiReferee {

	public static void main(String... args) throws IOException {
		new Referee(System.in, System.out, System.err).start();
	}

	public Referee(InputStream is, PrintStream out, PrintStream err) throws IOException {
		super(is, out, err);
	}

	public static int GAME_VERSION = 5;

	public static final int WIDTH = 16001;
	public static final int HEIGHT = 9001;
	public static final int[] DEFAULT_GHOST_STAMINA = { 3, 15, 40 };
	public static int[] GHOST_STAMINA = {};
	public static int MIN_BUSTERS = 2;
	public static int MAX_BUSTERS = 5;
	public static int SPAWN_RADIUS = 1600;
	public static int HQ_RADIUS = 1600;
	public static final int SPACE_BETWEEN_BUSTERS = 600;
	public static final int MAX_EXTRA_GHOSTS = 8;
	public static int MAX_GHOSTS = 30;
	public static int BUSTER_SPEED = 800;
	public static int BUSTER_SPEED_WHILE_CARRYING = 800;
	public static int VIEW_RANGE = 2200;
	public static int RADAR_RANGE = 4400;
	public static int EJECT_RANGE = 1760;
	public static int GHOST_VIEW_RANGE = 2200;
	public static int GRAB_RANGE = 1760;
	public static int GRAB_SAFETY_DISTANCE = 900;
	public static int STUN_RANGE = 1760;
	public static final int GHOST_MIN_SEPERATION = 1200;
	public static final int GHOST_MARGIN = 200;
	public static int STUN_DURATION = 10;
	public static int STUN_COOLDOWN = 20;
	public static boolean CAN_GRAB_GHOSTS_IN_ENEMY_SPAWN = false;
	public static boolean CAN_STUN = false;
	public static boolean CAN_USE_RADAR = false;
	public static boolean CAN_EJECT = false;
	public static int OBSTACLE_MODE = 0;
	public static int GHOST_SPEED = 400;
	public static boolean CENTRAL_GHOST = true;
	public static final int RADARS_PER_BUSTER = 1;

	private static final int COLLISION_STEPS = 10;

	List<PlayerInfo> players;
	List<Buster> allBusters;
	int bustersPerPlayer, ghostCount;
	long seed;
	Random random;
	private List<Ghost> ghosts;
	private boolean gameOver;
	private Obstacle[] map;

	private static Vector[] corners = { new Vector(0, 0), new Vector(WIDTH - 1, HEIGHT - 1), new Vector(WIDTH - 1, 0), new Vector(0, HEIGHT - 1) };

	@Override
	protected void initReferee(int playerCount, Properties prop) throws InvalidFormatException {
		seed = Long.valueOf(prop.getProperty("seed", String.valueOf(new Random(System.currentTimeMillis()).nextLong())));
		random = new Random(this.seed);

		if (GAME_VERSION >= 2) {
			CAN_STUN = true;
		}
		if (GAME_VERSION >= 3) {
			GHOST_STAMINA = DEFAULT_GHOST_STAMINA;
		}
		if (GAME_VERSION >= 4) {
			CAN_USE_RADAR = true;
		}
		if (GAME_VERSION >= 5) {
			CAN_EJECT = true;
		}

		bustersPerPlayer = Math.max(MIN_BUSTERS, Math.min(MAX_BUSTERS, Integer.valueOf(prop.getProperty("busters", String.valueOf(random.nextInt(MAX_BUSTERS - MIN_BUSTERS) + MIN_BUSTERS)))));
		ghostCount = Math.max(1, Integer.valueOf(prop.getProperty("ghosts", String.valueOf(Math.min(MAX_GHOSTS, bustersPerPlayer * 4 + random.nextInt(MAX_EXTRA_GHOSTS + 1))))));
		BUSTER_SPEED = Integer.valueOf(prop.getProperty("BUSTER_SPEED", String.valueOf(BUSTER_SPEED)));
		BUSTER_SPEED_WHILE_CARRYING = Integer.valueOf(prop.getProperty("BUSTER_SPEED_WHILE_CARRYING", String.valueOf(BUSTER_SPEED_WHILE_CARRYING)));
		VIEW_RANGE = Integer.valueOf(prop.getProperty("VIEW_RANGE", String.valueOf(VIEW_RANGE)));
		GHOST_VIEW_RANGE = Integer.valueOf(prop.getProperty("GHOST_VIEW_RANGE", String.valueOf(GHOST_VIEW_RANGE)));
		GHOST_SPEED = Integer.valueOf(prop.getProperty("GHOST_SPEED", String.valueOf(GHOST_SPEED)));
		CENTRAL_GHOST = Boolean.valueOf(prop.getProperty("CENTRAL_GHOST", String.valueOf(CENTRAL_GHOST)));
		EJECT_RANGE = Integer.valueOf(prop.getProperty("EJECT_RANGE", String.valueOf(EJECT_RANGE)));

		String mapCoords = prop.getProperty("map");

		map = null;
		if (mapCoords == null) {
			map = chooseMap();
		} else {
			String[] tab = mapCoords.split(" ");
			map = new Obstacle[tab.length / 3];
			for (int i = 0; i < tab.length; i++) {
				map[i] = new Obstacle(Integer.parseInt(tab[i * 2]), Integer.parseInt(tab[i * 3 + 1]), Integer.parseInt(tab[i * 3 + 2]));
			}
		}

		players = new ArrayList<>(playerCount);
		allBusters = new ArrayList<>(playerCount * bustersPerPlayer);

		// Generate busters
		int busterId = 0;

		Vector[] directions = { new Vector(1, 1), new Vector(-1, -1), new Vector(-1, 1), new Vector(1, -1) };
		int spawnOffset = 2 * SPAWN_RADIUS / 2;

		for (int i = 0; i < playerCount; ++i) {
			PlayerInfo playerInfo = new PlayerInfo(i);
			players.add(playerInfo);

			Vector vector = (i < 2 ? new Vector(1, -1) : new Vector(1, 1)).normalize();
			if (i % 2 == 1) {
				vector = vector.mult(-1);
			}

			Vector startPoint = corners[i];
			for (int j = 0; j < bustersPerPlayer; ++j) {
				double offset = ((j % 2 == 0 ? -1 : 1) * (j / 2 * 2 + 1) + bustersPerPlayer % 2);
				Vector position = vector.mult(offset * (SPACE_BETWEEN_BUSTERS)).translate(startPoint).translate(directions[i].mult(spawnOffset)).round();
				snapToGameZone(position);
				Buster b = new Buster(busterId++, j, position, playerInfo, directions[i].angle());
				playerInfo.addBuster(b);
				allBusters.add(b);
			}
		}

		// Generate ghosts
		int iterations = 0;
		ghosts = new LinkedList<Ghost>();
		int ghostId = 0;

		if (CENTRAL_GHOST) {
			int stamina = 0;
			if (GHOST_STAMINA.length > 0) {
				stamina = GHOST_STAMINA[random.nextInt(GHOST_STAMINA.length)];
			}
			Ghost g = new Ghost(ghostId++, new Vector((WIDTH - 1) / 2, (HEIGHT - 1) / 2), GameEntity.GHOST_NORMAL, stamina);
			ghosts.add(g);
		}

		while (ghostId < ghostCount) {
			iterations++;

			if (iterations > 1000) {
				break;
			}

			int x = (int) (GHOST_MARGIN + random.nextInt(WIDTH - GHOST_MARGIN * 2));
			int y = (int) (GHOST_MARGIN + random.nextInt(HEIGHT - GHOST_MARGIN * 2));
			Vector pos = new Vector(x, y);

			boolean ok = true;
			for (Ghost ghost : ghosts) {
				if (ghost.position.distance(pos) <= GHOST_MIN_SEPERATION) {
					ok = false;
					break;
				}
			}
			if (!ok)
				continue;
			for (int k = 0; k < playerCount; ++k) {
				// if (corners[k].distance(pos) <= SPAWN_RADIUS + VIEW_RANGE) {
				if (corners[k].distance(pos) <= SPAWN_RADIUS + 2200) {
					ok = false;
					break;
				}
			}
			if (!ok)
				continue;
			for (Obstacle c : map) {
				if (c.position.distance(pos) <= c.getRadius()) {
					ok = false;
					break;
				}
			}

			if (ok) {
				int stamina = 0;
				if (GHOST_STAMINA.length > 0) {
					stamina = GHOST_STAMINA[random.nextInt(GHOST_STAMINA.length)];
				}
				Ghost g = new Ghost(ghostId++, pos, GameEntity.GHOST_NORMAL, stamina);
				ghosts.add(g);

				g = new Ghost(ghostId++, new Vector(WIDTH - 1 - pos.getX(), HEIGHT - 1 - pos.getY()), GameEntity.GHOST_NORMAL, stamina);
				ghosts.add(g);
			}
		}
		ghostCount = ghostId;
	}

	private Obstacle[] chooseMap() {

		List<Obstacle[]> maps = new ArrayList<>();
		maps.add(new Obstacle[] { new Obstacle(12460, 1350, 1000), new Obstacle(10540, 5980, 1000), new Obstacle(3580, 5180, 1000), new Obstacle(13580, 7600, 1000) });
		maps.add(new Obstacle[] { new Obstacle(3600, 5280, 1000), new Obstacle(13840, 5080, 1000), new Obstacle(10680, 2280, 1000), new Obstacle(8700, 7460, 1000), new Obstacle(7200, 2160, 1000) });
		maps.add(new Obstacle[] { new Obstacle(4560, 2180, 1000), new Obstacle(7350, 4940, 1000), new Obstacle(3320, 7230, 1000), new Obstacle(14580, 7700, 1000), new Obstacle(10560, 5060, 1000), new Obstacle(13100, 2320, 1000) });
		maps.add(new Obstacle[] { new Obstacle(5010, 5260, 1000), new Obstacle(11480, 6080, 1000), new Obstacle(9100, 1840, 1000) });
		maps.add(new Obstacle[] { new Obstacle(14660, 1410, 1000), new Obstacle(3450, 7220, 1000), new Obstacle(9420, 7240, 1000), new Obstacle(5970, 4240, 1000) });
		maps.add(new Obstacle[] { new Obstacle(3640, 4420, 1000), new Obstacle(8000, 7900, 1000), new Obstacle(13300, 5540, 1000), new Obstacle(9560, 1400, 1000) });
		maps.add(new Obstacle[] { new Obstacle(4100, 7420, 1000), new Obstacle(13500, 2340, 1000), new Obstacle(12940, 7220, 1000), new Obstacle(5640, 2580, 1000) });
		maps.add(new Obstacle[] { new Obstacle(14520, 7780, 1000), new Obstacle(6320, 4290, 1000), new Obstacle(7800, 860, 1000), new Obstacle(7660, 5970, 1000), new Obstacle(3140, 7540, 1000), new Obstacle(9520, 4380, 1000) });
		maps.add(new Obstacle[] { new Obstacle(10040, 5970, 1000), new Obstacle(13920, 1940, 1000), new Obstacle(8020, 3260, 1000), new Obstacle(2670, 7020, 1000) });
		maps.add(new Obstacle[] { new Obstacle(7500, 6940, 1000), new Obstacle(6000, 5360, 1000), new Obstacle(11300, 2820, 1000) });
		maps.add(new Obstacle[] { new Obstacle(4060, 4660, 1000), new Obstacle(13040, 1900, 1000), new Obstacle(6560, 7840, 1000), new Obstacle(7480, 1360, 1000), new Obstacle(12700, 7100, 1000) });
		maps.add(new Obstacle[] { new Obstacle(3020, 5190, 1000), new Obstacle(6280, 7760, 1000), new Obstacle(14100, 7760, 1000), new Obstacle(13880, 1220, 1000), new Obstacle(10240, 4920, 1000), new Obstacle(6100, 2200, 1000) });
		maps.add(new Obstacle[] { new Obstacle(10323, 3366, 1000), new Obstacle(11203, 5425, 1000), new Obstacle(7259, 6656, 1000), new Obstacle(5425, 2838, 1000) });

		if (OBSTACLE_MODE == 0) {
			return new Obstacle[] {};
		} else {
			return maps.get(random.nextInt(maps.size()));

		}
	}

	@Override
	protected Properties getConfiguration() {
		Properties prop = new Properties();
		prop.setProperty("seed", String.valueOf(seed));
		prop.setProperty("busters", String.valueOf(bustersPerPlayer));
		prop.setProperty("ghosts", String.valueOf(ghostCount));
		return prop;
	}

	public String convertToCoords(Obstacle[] map) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < map.length; ++i) {
			Obstacle o = map[i];
			sb.append(o.position.toIntString()).append(o.getRadius());
			if (i < map.length - 1) {
				sb.append(" ");
			}
		}
		return sb.toString();
	}

	@Override
	protected String[] getInitInputForPlayer(int playerIdx) {
		List<String> lines = new ArrayList<>();
		lines.add(String.valueOf(bustersPerPlayer));
		lines.add(String.valueOf(ghostCount));
		lines.add(String.valueOf(playerIdx));
		return lines.toArray(new String[lines.size()]);
	}

	protected void prepare(int round) {
		// System.err.println("prepare " + round);

		for (PlayerInfo player : players) {
			for (Buster buster : player.busters) {
				buster.reset();

			}
		}
	}

	@Override
	protected String[] getInputForPlayer(int round, int playerIdx) {
		List<String> lines = new ArrayList<>();

		PlayerInfo player = players.get(playerIdx);
		List<Buster> visibleEnemies = new LinkedList<>();
		List<Obstacle> visibleObstacles = new LinkedList<>();
		List<Ghost> visibleGhosts = new LinkedList<>();

		for (int j = 0; j < players.size(); ++j) {
			if (j == playerIdx) {
				continue;
			}
			for (Buster enemy : players.get(j).busters) {
				for (Buster b : player.busters) {
					if (enemy.position.distance(b.position) <= (b.radarTimeout > 0 ? RADAR_RANGE : VIEW_RANGE)) {
						visibleEnemies.add(enemy);
						break;
					}
				}
			}
		}

		for (Ghost ghost : ghosts) {
			for (Buster b : player.busters) {
				if (!ghost.inHq && !ghost.isGrabbed() && ghost.position.distance(b.position) <= (b.radarTimeout > 0 ? RADAR_RANGE : VIEW_RANGE)) {
					visibleGhosts.add(ghost);
					break;
				}
			}
		}

		for (Obstacle o : map) {
			for (Buster b : player.busters) {
				if (o.position.distance(b.position) <= (b.radarTimeout > 0 ? RADAR_RANGE : VIEW_RANGE) + o.getRadius()) {
					visibleObstacles.add(o);
					break;
				}
			}
		}
		lines.add(String.valueOf(bustersPerPlayer + visibleEnemies.size() + visibleGhosts.size() + visibleObstacles.size()));
		// Player's N busters
		for (GameEntity b : player.busters) {
			lines.add(b.toPlayerString());
		}
		// Oppnent's visible busters
		for (GameEntity enemy : visibleEnemies) {
			lines.add(enemy.toPlayerString());
		}
		// Visible ghosts
		for (GameEntity ghost : visibleGhosts) {
			lines.add(ghost.toPlayerString());
		}
		// Visible obstacles
		for (GameEntity obstacle : visibleObstacles) {
			lines.add(obstacle.toPlayerString());
		}

		return lines.toArray(new String[lines.size()]);
	}

	@Override
	protected int getExpectedOutputLineCountForPlayer(int playerIdx) {
		return bustersPerPlayer;
	}

	static final Pattern PLAYER_MOVE_PATTERN = Pattern.compile("MOVE\\s+(?<x>-?\\d+)\\s+(?<y>-?\\d+)(?:\\s+)?(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
	static final Pattern PLAYER_GRAB_PATTERN = Pattern.compile("BUST\\s+(?<id>\\d+)(?:\\s+)?(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
	static final Pattern PLAYER_RELEASE_PATTERN = Pattern.compile("RELEASE(?:\\s+)?(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
	static final Pattern PLAYER_STUN_PATTERN = Pattern.compile("STUN\\s+(?<id>\\d+)(?:\\s+)?(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
	static final Pattern PLAYER_RADAR_PATTERN = Pattern.compile("RADAR(?:\\s+)?(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
	static final Pattern PLAYER_EJECT_PATTERN = Pattern.compile("EJECT\\s+(?<x>-?\\d+)\\s+(?<y>-?\\d+)(?:\\s+)?(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);

	@Override
	protected void handlePlayerOutput(int frame, int round, int playerIdx, String[] outputs) throws WinException, LostException, InvalidInputException {
		int i = 0;
		PlayerInfo player = players.get(playerIdx);
		for (String line : outputs) {
			Matcher matchMove = PLAYER_MOVE_PATTERN.matcher(line);
			Matcher matchGrab = PLAYER_GRAB_PATTERN.matcher(line);
			Matcher matchRelease = PLAYER_RELEASE_PATTERN.matcher(line);
			Matcher matchStun = CAN_STUN ? PLAYER_STUN_PATTERN.matcher(line) : null;
			Matcher matchRadar = CAN_USE_RADAR ? PLAYER_RADAR_PATTERN.matcher(line) : null;
			Matcher matchEject = CAN_EJECT ? PLAYER_EJECT_PATTERN.matcher(line) : null;

			Buster buster = player.busters.get(i);
			try {
				if (matchMove.matches()) {
					// Movement
					int x = Integer.valueOf(matchMove.group("x"));
					int y = Integer.valueOf(matchMove.group("y"));
					if (buster.position.getX() != x || buster.position.getY() != y) {
						int speed = buster.state == Buster.CARRYING ? BUSTER_SPEED_WHILE_CARRYING : BUSTER_SPEED;
						Vector v = new Vector(buster.position, new Vector(x, y));
						if (v.lengthSquared() <= speed * speed) {
							buster.attemptedVector = v;
						} else {
							buster.attemptedVector = v.normalize().mult(speed);
						}
						buster.attempt = Buster.Action.MOVE;
					}

					// Message
					matchMessage(buster, matchMove);

				} else if (matchGrab.matches()) {
					// Grabbing
					Integer id = Integer.valueOf(matchGrab.group("id"));
					buster.attemptTargetId = id;
					buster.attempt = Buster.Action.GRAB;
					// Message
					matchMessage(buster, matchGrab);
				} else if (matchRelease.matches()) {
					// Releasing
					buster.attempt = Buster.Action.RELEASE;
					// Message
					matchMessage(buster, matchRelease);
				} else if (CAN_STUN && matchStun.matches()) {
					// Stun
					Integer id = Integer.valueOf(matchStun.group("id"));
					buster.attempt = Buster.Action.STUN;
					buster.attemptTargetId = id;
					// Message
					matchMessage(buster, matchStun);
				} else if (CAN_USE_RADAR && matchRadar.matches()) {
					// Radar
					buster.attempt = Buster.Action.RADAR;
					// Message
					matchMessage(buster, matchRadar);
				} else if (CAN_EJECT && matchEject.matches()) {
					// Eject
					buster.attempt = Buster.Action.EJECT;
					int x = Integer.valueOf(matchEject.group("x"));
					int y = Integer.valueOf(matchEject.group("y"));

					Vector v = new Vector(buster.position, new Vector(x, y));
					if (v.lengthSquared() <= EJECT_RANGE * EJECT_RANGE) {
						buster.attemptedVector = v;
					} else {
						buster.attemptedVector = v.normalize().mult(EJECT_RANGE);
					}

					// Message
					matchMessage(buster, matchEject);
				} else {
					throw new InvalidInputException("MOVE x y | BUST id | RELEASE" + (CAN_STUN ? " | STUN id" : "") + (CAN_USE_RADAR ? " | RADAR" : "") + (CAN_EJECT ? " | EJECT" : ""), line);
				}
			} catch (Exception e) {
				buster.letGo();
				buster.invalidMove = true;
				buster.confused = true;
				buster.state = Buster.IDLE;
				player.dead = true;
				throw new InvalidInputException("MOVE x y | BUST id | RELEASE" + (CAN_STUN ? " | STUN id" : "") + (CAN_USE_RADAR ? " | RADAR" : "") + (CAN_EJECT ? " | EJECT" : ""), line);
			}
			++i;
		}
	}
	private void matchMessage(Buster buster, Matcher match) {
		buster.message = match.group("message");
		if (buster.message != null && buster.message.length() > 19) {
			buster.message = buster.message.substring(0, 17) + "...";
		}
	}

	private Vector computeCollisions(Buster buster, Vector newPos) {
		LinkedList<Obstacle> collidesWith = new LinkedList<Obstacle>();
		for (Obstacle o : map) {
			if (o.position.distance(newPos) <= o.getRadius()) {
				collidesWith.add(o);
			}
		}
		if (!collidesWith.isEmpty()) {
			Vector v = new Vector(buster.position, newPos);
			double smallest = 1;

			for (Obstacle o : collidesWith) {
				double a = 0;
				double b = 1;
				double best = 0;

				for (int i = 0; i < COLLISION_STEPS; ++i) {
					double next = (a + b) / 2;
					if (buster.position.add(v.mult(next)).distance(o.position) <= o.getRadius()) {
						b = next;
					} else {
						a = next;
						best = a;
					}
				}
				if (best < smallest) {
					smallest = best;
				}
			}
			return buster.position.add(v.mult(smallest));
		}
		return newPos;
	}

	@Override
	protected void updateGame(int round) throws GameOverException {

		if (gameOver) {
			throw new GameOverException("end");
		}

		Map<Ghost, List<Buster>> grabDemand = new HashMap<>();
		for (Ghost ghost : ghosts) {
			grabDemand.put(ghost, new LinkedList<Buster>());
		}
		Map<Buster, Buster> stunMap = new HashMap<>();

		for (Buster buster : allBusters) {
			if (buster.state == Buster.STUNNED) {
				continue;
			}
			if (buster.attempt != null) {
				switch (buster.attempt) {
				case GRAB: {
					buster.letGo();
					Ghost target = null;
					if (buster.attemptTargetId >= 0 && buster.attemptTargetId < ghosts.size()) {
						target = ghosts.get(buster.attemptTargetId);
						double dist = target.position.distance(buster.position);
						if (dist < GRAB_SAFETY_DISTANCE || dist > GRAB_RANGE /*|| target.isGrabbed()*/) {
							if (GRAB_SAFETY_DISTANCE > dist) {
								buster.tooClose = true;
							}
							target = null;
						} else if (!CAN_GRAB_GHOSTS_IN_ENEMY_SPAWN) {
							// Check if in hq
							if (target.isCaptured()) {
								target = null;
							}
						}
					}

					if (target == null) {
						buster.confused = true;
					} else {
						grabDemand.get(target).add(buster);
						buster.rotation = new Vector(buster.position, target.position).angle();
						buster.success = true;
						buster.ghost = target;
					}
					break;
				}
				case MOVE:
					Vector move = buster.attemptedVector;
					Vector newPos = snapToGameZone(buster.position.add(move)).round();
					// computeCollisions(buster, newPos);
					buster.nextPosition = newPos;
					break;

				case EJECT: {
					if (buster.state == Buster.CARRYING) {
						Vector target = snapToGameZone(buster.position.add(buster.attemptedVector)).round();
						buster.success = true;
						buster.ghost.position = target;
						buster.ghost.inHq = false;
						buster.letGo();

					} else {
						buster.confused = true;
					}
					break;
				}
				case RADAR:
					if (buster.radars > 0) {
						buster.success = true;
						buster.radars--;
						buster.radarTimeout = 2;
					} else {
						buster.confused = true;
					}
					break;
				case RELEASE:
					if (buster.state == Buster.CARRYING) {
						buster.success = true;
						buster.letGo();
					} else {
						buster.confused = true;
					}
					break;
				case STUN: {
					if (buster.state == Buster.GRABBING) {
						buster.state = Buster.IDLE;
					}
					Buster target = null;
					if (buster.stunRecharge == 0 && buster.attemptTargetId >= 0 && buster.attemptTargetId < allBusters.size()) {
						target = allBusters.get(buster.attemptTargetId);
						if (target.position.distance(buster.position) > STUN_RANGE) {
							target = null;
						}
					}
					if (target == null) {
						buster.confused = true;
					} else {
						buster.success = true;
						buster.stunRecharge = STUN_COOLDOWN;
						buster.letGo();
						stunMap.put(buster, target);
					}
					break;
				}

				default:
					break;
				}
			}
		}
		for (Buster buster : allBusters) {
			if (buster.nextPosition != null) {
				buster.rotation = new Vector(buster.position, buster.nextPosition).angle();
				buster.position = buster.nextPosition;
			}
			if (buster.stunCountDown > 0) {
				buster.stunCountDown--;
				if (buster.stunCountDown == 0) {
					buster.state = Buster.IDLE;
				}
			}
			if (buster.stunRecharge > 0) {
				buster.stunRecharge--;
			}
			if (buster.state == Buster.GRABBING && !Buster.Action.GRAB.equals(buster.attempt)) {
				buster.state = Buster.IDLE;
			}
		}

		for (Ghost ghost : ghosts) {
			ghost.wasSuffering = ghost.suffering;
			ghost.suffering = false;
			
			ghost.attemptedGrabs = 0;

			if (!ghost.isGrabbed() && !ghost.escaping) {
				List<Buster> grabbers = grabDemand.get(ghost);
				if (!grabbers.isEmpty()) {
					ghost.decreaseStamina(grabbers.size());
					ghost.attemptedGrabs = grabbers.size();
					ghost.suffering = true;
					if (ghost.getStamina() == 0) {
						Integer winningTeam = null;
						int max = 0;
						Map<Integer, Integer> teamGrabs = new HashMap<Integer, Integer>();
						for (int i = 0; i < players.size(); ++i) {
							teamGrabs.put(i, 0);
						}
						for (Buster b : grabbers) {
							int x = teamGrabs.get(b.owner.index) + 1;
							teamGrabs.put(b.owner.index, x);
							if (x > max) {
								max = x;
								winningTeam = b.owner.index;
							} else if (x == max) {
								winningTeam = null;
							}
						}

						double min = Double.MAX_VALUE;
						Buster closest = null;
						if (winningTeam != null) {
							for (Buster b : grabbers) {
								if (winningTeam != null && b.owner.index != winningTeam)
									continue;
								double dist = b.position.distance(ghost.position);
								if (dist < min) {
									min = dist;
									closest = b;
								}
							}
							for (Buster b : grabbers) {
								b.state = Buster.IDLE;
								b.finishGrab = true;
							}
							closest.state = Buster.CARRYING;
							ghost.grabbedBy = closest;
						} else {
							for (Buster b : grabbers) {
								b.state = Buster.GRABBING;
							}
						}
					} else {
						for (Buster b : grabbers) {
							b.state = Buster.GRABBING;
						}
					}
				}
			}

			if (ghost.isGrabbed()) {
				ghost.position = ghost.grabbedBy.position;

				boolean inHqRange = false;
				for (int i = 0; i < players.size(); ++i) {
					if (ghost.position.distance(corners[i]) <= HQ_RADIUS) {
						inHqRange = true;
						break;
					}
				}
				ghost.inHq = inHqRange;
				ghost.flagUpdated();
			} else if (ghost.suffering != ghost.wasSuffering) {
				ghost.flagUpdated();
			}
		}

		for (Entry<Buster, Buster> stunEntry : stunMap.entrySet()) {
			Buster stunned = stunEntry.getValue();
			if (stunned.state == Buster.CARRYING) {
				if (stunned.ghost != null && stunned.ghost.grabbedBy == stunned) {
					stunned.ghost.grabbedBy = null;
					stunned.ghost.flagUpdated();
				} else {
					printError("Error: letting go of nothing");
				}
			}
			stunned.stun(STUN_DURATION);
			stunEntry.getKey().rotation = new Vector(stunEntry.getKey().position, stunEntry.getValue().position).angle();
		}

		// Frightened ghosts
		for (Ghost ghost : ghosts) {
			ghost.escaping = false;
			if (!ghost.isGrabbed() && !ghost.isCaptured() && !ghost.suffering) {
				if (ghost.frightenedBy != null) {
					Vector move = new Vector(ghost.frightenedBy, ghost.position).normalize().mult(GHOST_SPEED);
					ghost.position = snapToGameZone(ghost.position.add(move)).round();
					ghost.flagUpdated();
				}

				List<Buster> closest = new LinkedList<>();
				double minDist = 0;
				for (Buster b : allBusters) {
					double dist = b.position.distance(ghost.position);
					if (closest.isEmpty()) {
						closest.add(b);
						minDist = dist;
					} else if (dist == minDist) {
						closest.add(b);
					} else if (dist < minDist) {
						closest.clear();
						closest.add(b);
						minDist = dist;
					}
				}
				if (minDist <= GHOST_VIEW_RANGE) {
					double x = 0;
					double y = 0;

					for (Buster b : closest) {
						x += b.position.getX();
						y += b.position.getY();
					}
					x /= closest.size();
					y /= closest.size();

					ghost.frightenedBy = new Vector(x, y);
					if (ghost.position.round().equals(new Vector(x, y).round())) {
						ghost.frightenedBy = null;
					}
				} else {
					ghost.frightenedBy = null;
				}
			} else {
				ghost.frightenedBy = null;
			}
		}

		if (!CAN_GRAB_GHOSTS_IN_ENEMY_SPAWN && allGhostsCaptured()) {
			gameOver = true;
		}

		int[] scores = { getScore(0), getScore(1) };
		int[] captures = { getGhostCount(0), getGhostCount(1) };

		int deads = 0;
		PlayerInfo alive = null;
		for (PlayerInfo player : players) {
			if (player.dead) {
				deads++;
			} else {
				alive = player;
			}
		}
		if (deads == players.size() - 1) {
			int capturedByDead = 0;
			for (PlayerInfo other : players) {
				if (alive == other)
					continue;
				capturedByDead += scores[other.index];
			}
			if (scores[alive.index] > capturedByDead) {
				gameOver = true;
			}
		} else if (deads == players.size()) {
			gameOver = true;
		}

		// Arret anticipé
		if (captures[0] > ghosts.size() - captures[0] || captures[1] > ghosts.size() - captures[1]) {
			gameOver = true;
		}

	}
	private boolean allGhostsCaptured() {
		for (Ghost g : ghosts) {
			if (!g.isCaptured()) {
				return false;
			}
		}

		return true;
	}

	private Vector snapToGameZone(Vector v) {
		double snapX = v.getX();
		double snapY = v.getY();

		if (snapX < 0)
			snapX = 0;
		if (snapX >= WIDTH)
			snapX = WIDTH - 1;
		if (snapY < 0)
			snapY = 0;
		if (snapY >= HEIGHT)
			snapY = HEIGHT - 1;
		return new Vector(snapX, snapY);
	};

	@Override
	protected void populateMessages(Properties p) {
		p.put("invalid", "Buster %d of player $%d was given an invalid command");
		p.put("stunned", "Buster %d of player $%d is stunned");
		p.put("move", "Buster %d of player $%d moves to (%d,%d)");
		p.put("moveAndBust", "Buster %d of player $%d moves to (%d,%d) while carrying ghost %d");
		p.put("bustError", "Buster %d of player $%d wants to bust ghost %d but can't see it.");
		p.put("bustTooClose", "Buster %d of player $%d wants to bust ghost %d but is too close.");
		p.put("bust", "Buster %d of player $%d trapped ghost %d.");
		p.put("busting", "Buster %d of player $%d is busting ghost %d.");
		p.put("stun", "Buster %d of player $%d stuns buster %d of player $%d.");
		p.put("autostun", "Buster %d of player $%d stuns himself! What a clutz!.");
		p.put("stunError", "Buster %d of player $%d wants to stun buster %d but cannot.");
		p.put("stunErrorRecharge", "Buster %d of player $%d wants to stun buster %d but needs more time.");
		p.put("release", "Buster %d of player $%d released ghost %d.");
		p.put("radar", "Buster %d of player $%d used his radar.");

	}

	@Override
	protected String[] getInitDataForView() {
		List<String> lines = new ArrayList<>();

		lines.add(WIDTH + " " + HEIGHT + " " + HQ_RADIUS + " " + bustersPerPlayer + " " + ghostCount + " " + VIEW_RANGE + " " + GRAB_RANGE + " " + map.length);
		for (Ghost g : ghosts) {
			lines.add(g.toInitFrameString());
		}
		for (Obstacle o : map) {
			lines.add(o.toInitFrameString());
		}
		lines.add(0, String.valueOf(lines.size() + 1));
		return lines.toArray(new String[lines.size()]);
	}

	@Override
	protected String[] getFrameDataForView(int round, int frame, boolean keyFrame) {
		List<String> lines = new ArrayList<>();
		if (keyFrame) {
			for (PlayerInfo player : players) {
				lines.add(String.valueOf(getGhostCount(player.index)));
				lines.add(String.valueOf(getScore(player.index)));
				for (Buster b : player.busters) {
					lines.add(b.toFrameString());
				}
			}
			List<Ghost> updatedGhosts = new LinkedList<>();
			for (Ghost ghost : ghosts) {
				if (ghost.hasMoved(round)) {
					updatedGhosts.add(ghost);
				}
			}
			lines.add(String.valueOf(updatedGhosts.size()));
			for (Ghost g : updatedGhosts) {
				lines.add(g.toFrameString());
			}

		} else {
			// This will never ever happen. Not in a million years.
			lines.add("X");
		}

		return lines.toArray(new String[lines.size()]);
	}

	@Override
	protected String getGameName() {
		return "CodeBusters";
	}

	@Override
	protected String getHeadlineAtGameStartForConsole() {
		return "Who you gonna call... ?";
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

	protected List<String> getPlayerSummary(int playerIdx) {
		// System.err.println("playerActions" + playerIdx+", round " + round);
		List<String> lines = new ArrayList<>(players.size() + bustersPerPlayer);
		PlayerInfo player = players.get(playerIdx);
		for (Buster b : player.busters) {

			if (b.invalidMove) {
				lines.add(translate("invalid", b.id, player.index));
			} else if (b.state == Buster.STUNNED) {
				lines.add(translate("stunned", b.id, player.index));
			} else if (b.attemptedMove() && b.attemptedVector != null) {
				if (b.state == Buster.CARRYING) {
					if (b.ghost != null) {
						lines.add(translate("moveAndBust", b.id, player.index, (int) b.position.getX(), (int) b.position.getY(), b.ghost.id));
					} else {
						printError("Error: carrying no ghost?");
					}
				} else {
					lines.add(translate("move", b.id, player.index, (int) b.position.getX(), (int) b.position.getY()));
				}
			} else if (Buster.Action.STUN.equals(b.attempt)) {
				if (b.success) {
					if (b.attemptTargetId.intValue() == b.id)
						lines.add(translate("autostun", b.id, player.index));
					else
						lines.add(translate("stun", b.id, player.index, b.attemptTargetId, allBusters.get(b.attemptTargetId).owner.index));
				} else if (b.stunRecharge > 0) {
					lines.add(translate("stunErrorRecharge", b.id, player.index, b.attemptTargetId));
				} else {
					lines.add(translate("stunError", b.id, player.index, b.attemptTargetId));
				}
			} else if (b.state == Buster.GRABBING) {
				lines.add(translate("busting", b.id, player.index, b.ghost.id));
			} else if (b.trappedGhost()) {
				lines.add(translate("bust", b.id, player.index, b.ghost.id));
			} else if (Buster.Action.RELEASE.equals(b.attempt) && b.success) {
				lines.add(translate("release", b.id, player.index, b.ghost.id));
			} else if (b.attemptedGrab() && !b.success && b.attemptTargetId != null) {
				if (b.tooClose) {
					lines.add(translate("bustTooClose", b.id, player.index, b.attemptTargetId));
				} else {
					lines.add(translate("bustError", b.id, player.index, b.attemptTargetId));
				}
			} else if (b.radarSucceeded()) {
				lines.add(translate("radar", b.id, player.index));
			}
		}
		return lines;
	}

	@Override
	protected boolean isPlayerDead(int playerIdx) {
		return players.get(playerIdx).dead;
	}

	@Override
	protected String getDeathReason(int playerIdx) {
		return "Timeout!";
	}

	private int getGhostCount(int playerIdx, boolean countTrapped) {
		int count = 0;
		for (int i = 0; i < ghosts.size(); ++i) {
			Ghost g = ghosts.get(i);
			if (g.position.distance(corners[playerIdx]) <= HQ_RADIUS && g.isCaptured() || countTrapped && g.isTrappedBy(players.get(playerIdx))) {
				count++;
			}
		}
		return count;
	}
	private int getGhostCount(int playerIdx) {
		return getGhostCount(playerIdx, false);
	}

	@Override
	protected int getScore(int playerIdx) {
		// if (players.get(playerIdx).lastCaptureFrame == 0) {
		// return getGhostCount(playerIdx);
		// }
		// return (int) ((getGhostCount(playerIdx) + (1. /
		// players.get(playerIdx).lastCaptureFrame)) * 10000);
		return getGhostCount(playerIdx, true);
	}

	@Override
	protected String[] getGameSummary(int round) {
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < players.size(); ++i) {
			lines.addAll(getPlayerSummary(i));
		}
		return lines.toArray(new String[lines.size()]);
	}

	@Override
	protected void setPlayerTimeout(int frame, int round, int playerIdx) {
		players.get(playerIdx).dead = true;
	}

	@Override
	protected int getMillisTimeForRound() {
		return 100;
	}

	@Override
	protected int getMaxRoundCount(int playerCount) {
		return 250;
	}

	@Override
	protected boolean gameOver() {
		return gameOver;
	}

}

class Vector {
	private final double x, y;

	public Vector(double x, double y) {
		this.x = x;
		this.y = y;
	}

	public Vector(Vector a, Vector b) {
		this.x = b.x - a.x;
		this.y = b.y - a.y;
	}

	public boolean equals(Vector v) {
		return v.getX() == x && v.getY() == y;
	}

	public Vector round() {
		return new Vector((int) Math.round(this.x), (int) Math.round(this.y));
	}

	public Vector truncate() {

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

	@Override
	public String toString() {
		return "[" + x + ", " + y + "]";
	}

	public String toIntString() {
		return (int) x + " " + (int) y;
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

	public Vector translate(Vector v) {
		return new Vector(v.x + this.x, v.y + this.y);
	}

}

class GameEntity {
	public static final int GHOST_NORMAL = -1;
	public static final int OBSTACLE = -2;

	protected int id;
	protected Vector position;
	protected int type;
	protected int state;

	public GameEntity(int id, Vector position, int type, int state) {
		this.id = id;
		this.position = position;
		this.type = type;
		this.state = state;
	}

	public int getValue() {
		return 0;
	}

	public String toPlayerString() {
		return id + " " + position.toIntString() + " " + type + " " + state + " " + getValue();
	}
}

class Ghost extends GameEntity {
	Vector frightenedBy;
	Buster grabbedBy;
	private boolean moved;
	boolean inHq, suffering;
	public boolean wasSuffering, escaping;
	int attemptedGrabs;

	public Ghost(int id, Vector position, int type, int stamina) {
		super(id, position, type, stamina);
	}

	public boolean isTrappedBy(PlayerInfo player) {
		return isGrabbed() && grabbedBy.ghost == this && grabbedBy.state == Buster.CARRYING && grabbedBy.owner == player;
	}

	public int getStamina() {
		return state;
	};

	public boolean isGrabbed() {
		return grabbedBy != null;
	}

	public String toInitFrameString() {
		return String.valueOf(getStamina());
	}

	public boolean hasMoved(int round) {
		return (round == 0) || moved;
	}

	public String toFrameString() {
		return id + " " + position.toIntString() + " " + (isGrabbed() ? grabbedBy.id : -1) + " " + (suffering ? 1 : 0) + " " + getStamina() + " " + (isCaptured() ? 1 : 0);
	}

	public void flagUpdated() {
		moved = true;
	}

	public void decreaseStamina(int amount) {
		setStamina(Math.max(0, getStamina() - amount));
		attemptedGrabs = amount;
	}

	private void setStamina(int stamina) {
		this.state = stamina;
	}

	public boolean isCaptured() {
		return !isGrabbed() && inHq;
	}

	@Override
	public int getValue() {
		return suffering ? attemptedGrabs : 0;
	}
}

class Buster extends GameEntity {
	static final int IDLE = 0;
	static final int CARRYING = 1;
	static final int STUNNED = 2;
	static final int GRABBING = 3;

	enum Action {
		MOVE, GRAB, STUN, RADAR, RELEASE, EJECT
	}

	int index, stunCountDown, stunRecharge, radars, radarTimeout;
	PlayerInfo owner;
	String message;
	Vector attemptedVector;
	Vector nextPosition;
	double rotation;
	boolean confused, finishGrab, tooClose;
	public boolean invalidMove;
	private boolean doStun;

	Action attempt;
	boolean success;
	Integer attemptTargetId;

	Ghost ghost;// TODO: rename

	public Buster(int id, int index, Vector position, PlayerInfo owner, double rotation) {
		super(id, position, owner.index, IDLE);
		this.index = index;
		this.owner = owner;
		this.rotation = rotation;
		this.radars = Referee.RADARS_PER_BUSTER;
	}

	public boolean attemptedMove() {
		return Action.MOVE.equals(attempt);
	}

	public boolean radarSucceeded() {
		return Action.RADAR.equals(attempt) && success;
	}

	public boolean attemptedGrab() {
		return Action.GRAB.equals(attempt);
	}

	public void reset() {
		attempt = null;
		success = false;
		nextPosition = null;
		attemptedVector = null;
		invalidMove = false;
		message = null;
		confused = false;
		finishGrab = false;
		tooClose = false;
		if (radarTimeout > 0) {
			radarTimeout--;
		}
		if (doStun) {
			applyStun();
		}
	}
	public String toFrameString() {
		return position.toIntString() + " " + state + " " + (int) (rotation * 180 / Math.PI) + " " + (confused ? 1 : 0) + " " + (stunSucceeded() ? attemptTargetId : -1) + " " + ((state == GRABBING || finishGrab) ? ghost.id : -1) + " " + (radarSucceeded() ? 1 : 0) + " " + (message != null ? message : "");
	}

	private boolean stunSucceeded() {
		return Action.STUN.equals(attempt) && success;
	}

	public void applyStun() {
		state = Buster.STUNNED;
		doStun = false;
	}

	public void stun(int duration) {
		doStun = true;
		stunCountDown = duration;

	}

	public boolean trappedGhost() {
		return attemptedGrab() && state == Buster.CARRYING;
	}

	public void stopGrab() {
		if (state == Buster.GRABBING) {
			state = Buster.IDLE;
		}
	}
	public void letGo() {
		if (state == Buster.CARRYING) {
			state = Buster.IDLE;
			if (ghost != null && ghost.grabbedBy == this) {
				ghost.grabbedBy = null;
				ghost.flagUpdated();
				ghost.escaping = true;
			}
		}
	}
	@Override
	public int getValue() {
		if (state == CARRYING || state == Buster.GRABBING) {
			return ghost.id;
		} else if (state == STUNNED) {
			return stunCountDown;
		} else {
			return -1;
		}
	}
	@Override
	public String toPlayerString() {
		if (Referee.GAME_VERSION >= 3) {
			return super.toPlayerString();
		}
		return id + " " + position.toIntString() + " " + type + " " + (state == Buster.GRABBING ? 0 : state) + " " + getValue();
	}
}

class Obstacle extends GameEntity {
	static int obstacleCount = 0;

	public Obstacle(int x, int y, int radius) {
		super(obstacleCount++, new Vector(x, y), GameEntity.OBSTACLE, radius);
	}

	public int getRadius() {
		return state;
	}

	public String toInitFrameString() {
		return id + " " + position.toIntString() + " " + getRadius();
	}
}

class PlayerInfo {
	List<Buster> busters;
	public boolean dead;
	int index, lastCaptureFrame;

	public PlayerInfo(int index) {
		busters = new LinkedList<>();
		this.index = index;
	}

	public void addBuster(Buster buster) {
		busters.add(buster);
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
		if (gameOver()) {
			throw new GameOverException(null);
		}
		// if (alivePlayerCount < getMinimumPlayerCount()) {
		// throw new GameOverException(null);
		// }
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
