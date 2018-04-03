import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Scanner;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Referee extends MultiReferee {

	private static final int MAX_ROUNDS = 250;
	private static int START_PLATINUM = 0;
	private static int START_UNITS = 10;
	private static int UNIT_COST = 20;

	private static final int NEUTRAL = -1;
	private static final int NOBODY = -2;

	public static final int COMBAT_STRIKES = 3;
	public static final double MAX_PLATINUM = 6;
	private static final int MAX_MESSAGE_LENGTH = 30;
	private static final int MIN_HEIGHT = 5;
	private static final int MAX_HEIGHT = 18;
	private static final float MAP_RATIO = 32f / 18f;
	private static final int HEIGHT_LOWEST = 1;
	private static final int HEIGHT_HIGHEST = 40;
	private static final int WIDTH_LOWEST = 2;
	private static final int WIDTH_HIGHEST = 70;

	static class Battle {
		Coord coord;
		List<Integer> contenders;
		int capturer = -1;
		int oldOwner = -1;
		int hexId, strikes;
		public int[] deaths;

		public Battle() {
		}

		public int getX() {
			return coord.x;
		}
		public int getY() {
			return coord.y;
		}
		public String getPlayers() {
			StringBuilder sb = new StringBuilder();
			int size = contenders.size();
			for (int i = 0; i < size; i++) {
				sb.append("$").append(i);
				if (i < size - 2)
					sb.append(", ");
				else if (i < size - 1)
					sb.append(" and ");
			}
			return sb.toString();
		}
		public void setHex(Coord c, int i) {
			this.coord = c;
			this.hexId = i;
		}
		public void setPlayers(List<Integer> contenders) {
			this.contenders = contenders;
		}
		public void setCapture(int ownerId, int newOwner) {
			this.oldOwner = ownerId;
			this.capturer = newOwner;
		}

		public boolean hasActivity() {
			return hasFight() || hasCapturer();
		}

		public boolean hasFight() {
			return contenders != null;
		}

		public boolean hasCapturer() {
			return capturer != -1;
		}

		public int getCapturer() {
			return capturer;
		}

		public Coord getCoord() {
			return coord;
		}
		@Override
		public String toString() {
			String deathInfo = "";
			if (hasFight()) {
				for (int d : deaths) {
					deathInfo += d + " ";
				}
			}
			return String.format("%d %d %d %d %s", coord.x, coord.y, capturer, hasFight() ? 1 : 0, deathInfo.trim());
		}

		public int getHexId() {
			return hexId;
		}
	}

	static class Coord {
		int x, y;

		public Coord(int x, int y) {
			this.x = x;
			this.y = y;
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
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Coord other = (Coord) obj;
			if (x != other.x)
				return false;
			if (y != other.y)
				return false;
			return true;
		}

		public boolean isEven() {
			return x % 2 == 0;
		}

	}

	static class Tile {
		private int platinum, ownerId, island, id, x, y;
		private int[] units;
		private boolean contested;
		public Set<Integer> neighbours;

		public Tile(int playerCount, int id) {
			this(playerCount, -1, 0, id);
		}

		public Tile(int playerCount, int ownerId, int platinum, int id) {
			this.ownerId = ownerId;
			this.platinum = platinum;
			units = new int[playerCount];
			contested = false;
			island = -1;
			this.id = id;
			neighbours = new HashSet<>();
		}

		public Tile(int playerCount, int id, Coord c) {
			this(playerCount, id);
			this.x = c.x;
			this.y = c.y;
		}

		public String toStringInit() {
			return String.format("%d", platinum);
		}
		@Override
		public String toString() {
			return String.format("%d %s", ownerId, unitsAsString());
		}

		private String unitsAsString() {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < units.length; i++) {
				sb.append(units[i]).append(" ");
			}
			return sb.toString().trim();
		}
		public int getPodCountFor(int playerId, PlayerMoveSet moves) {
			int now = units[playerId];
			int moveAways = 0;

			for (Move m : moves) {
				if (m.fromId == id)
					moveAways += m.amount;
			}

			return now - moveAways;
		}
		public void remove(int amount, int id) {
			units[id] -= amount;
		}
		public void add(int amount, int id) {
			units[id] += amount;
		}
		public Battle update() {
			Battle battle = new Battle();
			int newOwner = ownerId;
			List<Integer> contenders = new ArrayList<>(8);

			// Contenders
			for (int i = 0; i < units.length; i++) {
				if (units[i] > 0) {
					contenders.add(i);
				}
			}

			// Fight
			int contendersLeft = contenders.size();
			if (contendersLeft > 1) {
				battle.setPlayers(contenders);

				int strikes = 0;
				int[] deaths = { 0, 0, 0, 0 };
				boolean resolved = false;
				while (!resolved) {
					for (Integer ind : contenders) {
						if (units[ind] > 0) {
							deaths[ind]++;
							units[ind] = Math.max(0, units[ind] - 1);
							if (units[ind] == 0) {
								contendersLeft--;
							}
						}
					}
					strikes++;
					if (contendersLeft <= 1 || strikes == COMBAT_STRIKES) {
						resolved = true;
					}
				}
				battle.strikes = strikes;
				battle.deaths = deaths;
			}
			// Change ownership if needed
			if (contendersLeft == 1) {
				for (Integer ind : contenders) {
					if (units[ind] != 0) {
						newOwner = ind;
						break;
					}
				}
			}

			// Capture
			if (contendersLeft == 1) {
				if (ownerId != newOwner) {
					battle.setCapture(ownerId, newOwner);
				}
				ownerId = newOwner;
				contested = false;
			} else if (contendersLeft > 1) {
				contested = true;
			} else {
				contested = false;
			}
			return battle;
		}
		public boolean isContested() {
			return contested;
		}

		public String getNeighboursAsString() {
			StringBuilder sb = new StringBuilder();
			for (Integer id : neighbours) {
				sb.append(id);
			}
			return sb.toString().trim();
		}

		public int getEnemyCountFor(int playerId) {
			int sum = 0;
			for (int i = 0; i < units.length; i++) {
				if (i != playerId) {
					sum += units[i];
				}
			}
			return sum;
		}

		public String allUnits() {
			String s = "";
			for (int i = 0; i < units.length; ++i) {
				if (units.length > i) {
					s += units[i] + " ";
				} else {
					s += "0 ";
				}
			}
			return s.trim();
		}

	}

	static enum Distribution {
		LOWISH, HIGHISH, MOSTLY_LOW, MOSTLY_HIGH;
		public double value(double p) {
			switch (this) {
			case MOSTLY_HIGH:
				return Math.pow(p, .40);
			case MOSTLY_LOW:
				return Math.pow(p, 10);
			case HIGHISH:
				return Math.pow(p, 1);
			case LOWISH:
				return Math.pow(p, 5);
			}
			return p;
		}
	}

	static class HexGrid {
		int width, height;
		Map<Coord, Tile> map;
		int[] zones = { 0, 0, 0, 0, 0 };
		int[] units = { 0, 0, 0, 0 };
		int[] income = { 0, 0, 0, 0 };
		List<Set<Tile>> occupiedTiles = new ArrayList<>(2);
		List<Set<Integer>> visibleTiles = new ArrayList<>(2);
		List<List<Coord>> islands;

		private List<Coord> aroundEven;
		private List<Coord> aroundOdd;
		private List<Tile> graph;

		static class Edge {
			int a, b;

			public Edge(int a, int b) {
				this.a = Math.min(a, b);
				this.b = Math.max(a, b);
			}

			@Override
			public String toString() {
				return a + " " + b;
			}

			@Override
			public int hashCode() {
				final int prime = 31;
				int result = 1;
				result = prime * result + a;
				result = prime * result + b;
				return result;
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj)
					return true;
				if (obj == null)
					return false;
				if (getClass() != obj.getClass())
					return false;
				Edge other = (Edge) obj;
				if (a != other.a)
					return false;
				if (b != other.b)
					return false;
				return true;
			}
		}

		/*
		 * Try to keep width and height below 30
		 */
		public HexGrid() {
			width = 0;
			height = 0;
			graph = new ArrayList<>();
			islands = new ArrayList<>();
			map = new HashMap<Coord, Tile>();
			occupiedTiles.add(new HashSet<Tile>());
			occupiedTiles.add(new HashSet<Tile>());
			visibleTiles.add(new HashSet<Integer>());
			visibleTiles.add(new HashSet<Integer>());
			aroundEven = new LinkedList<Coord>();
			aroundEven.add(new Coord(0, -1));
			aroundEven.add(new Coord(1, 0));
			aroundEven.add(new Coord(0, 1));
			aroundEven.add(new Coord(-1, 0));
			aroundEven.add(new Coord(1, -1));
			aroundEven.add(new Coord(-1, -1));

			aroundOdd = new LinkedList<Coord>();
			aroundOdd.add(new Coord(0, -1));
			aroundOdd.add(new Coord(1, 0));
			aroundOdd.add(new Coord(0, 1));
			aroundOdd.add(new Coord(-1, 0));
			aroundOdd.add(new Coord(-1, 1));
			aroundOdd.add(new Coord(1, 1));

		}
		public Tile get(int x, int y) {
			Coord c = new Coord(x, y);
			return map.get(c);
		}

		public void generate(int playerCount, String mapData, int totalPlatinum, Distribution distribution, boolean noEmptyIslands, boolean symetric, long seed) {
			List<Tile> allTiles = new LinkedList<Tile>();

			String[] tileData = mapData.split(" ");
			int tileCount = tileData.length / 2;
			for (int i = 0; i < tileCount; i++) {
				int x = Integer.valueOf(tileData[i * 2]);
				int y = Integer.valueOf(tileData[i * 2 + 1]);

				if (x + 1 > width)
					width = x + 1;
				if (y + 1 > height)
					height = y + 1;

				Coord c = new Coord(x, y);
				Tile t = new Tile(playerCount, i, c);
				map.put(c, t);
				graph.add(t);
				allTiles.add(t);
				zones[0]++;
			}

			initIslands(false);
			distributePlatinum(totalPlatinum, allTiles, distribution, symetric, seed);
		}

		private void distributePlatinum(int total, List<Tile> tiles, Distribution method, boolean symetric, long seed) {
			Random random = new Random(seed);
			List<Tile> discardedTiles = new LinkedList<Tile>();
			int platinum = total;
			while (platinum > 0 && tiles.size() >= 3 + (symetric ? 1 : 0) && (platinum > 1 || !symetric)) {
				int rTile = random.nextInt(tiles.size());
				if (symetric && rTile == tiles.size() - 1 - rTile)
					continue;
				Tile t = tiles.get(rTile);

				double percent = random.nextDouble();

				int count = 1 + (int) (method.value(percent) * (MAX_PLATINUM - 1) + 0.5d);
				count = Math.min((symetric ? platinum / 2 : platinum), count);
				count = Math.min((int) MAX_PLATINUM, count);
				count = Math.max(1, count);

				platinum -= count;
				t.platinum = count;
				if (symetric) {
					Tile t2 = tiles.get(tiles.size() - 1 - rTile);
					if (t2.id != t.id) {
						platinum -= count;
						t2.platinum = count;
						tiles.remove(t2);
					}
					discardedTiles.add(t2);
				}
				tiles.remove(t);
				discardedTiles.add(t);
			}

			while (platinum > 0 && !discardedTiles.isEmpty() && ((platinum > 1 && discardedTiles.size() > 1) || !symetric)) {
				int rTile = random.nextInt(discardedTiles.size());
				Tile t = discardedTiles.get(rTile);
				if (t.platinum < 6) {
					t.platinum++;
					platinum--;
					if (symetric) {
						Tile t2 = discardedTiles.get(rTile + (rTile % 2 == 0 ? 1 : -1));
						t2.platinum++;
						platinum--;
					}
				} else {
					if (symetric) {
						Tile t2 = discardedTiles.get(rTile + (rTile % 2 == 0 ? 1 : -1));
						discardedTiles.remove(t2);
					}
					discardedTiles.remove(t);
				}
			}
		}

		private void initIslands(boolean asGraph) {
			int currentIsland = 0;
			List<Coord> islandData = new LinkedList<Coord>();

			for (Coord c : map.keySet()) {
				Tile t = map.get(c);
				if (t.island == -1) {
					Stack<Coord> stack = new Stack<>();
					stack.push(c);
					map.get(c).island = currentIsland;
					while (!stack.isEmpty()) {
						Coord hex = stack.pop();
						Tile tile = map.get(hex);

						islandData.add(hex);
						List<Coord> around;

						if (asGraph) {
							for (Integer i : tile.neighbours) {
								Tile nextTile = graph.get(i);
								Coord next = new Coord(nextTile.x, nextTile.y);
								if (nextTile != null) {
									if (nextTile.island == -1) {
										nextTile.island = currentIsland;
										stack.push(next);
									}
								}
							}
						} else {
							if (hex.isEven())
								around = aroundEven;
							else
								around = aroundOdd;
							for (Coord neigh : around) {
								Coord next = new Coord(hex.x + neigh.x, hex.y + neigh.y);
								Tile nextTile = map.get(next);

								if (nextTile != null) {
									tile.neighbours.add(nextTile.id);
									nextTile.neighbours.add(tile.id);

									if (nextTile.island == -1) {
										nextTile.island = currentIsland;
										stack.push(next);
									}
								}
							}
						}

					}
					islands.add(islandData);
					islandData = new LinkedList<>();
					currentIsland++;
				}
			}
			Collections.sort(islands, new Comparator<List<Coord>>() {
				@Override
				public int compare(List<Coord> a, List<Coord> b) {
					return b.size() - a.size();
				}
			});
		}
		public List<String> toStringInitAsMap(List<Player> players) {
			ArrayList<String> data = new ArrayList<String>();
			Iterator<Tile> it = graph.iterator();
			String baseInfo;
			if (players.size() == 2) {
				baseInfo = String.format("%d %d", players.get(0).base.id, players.get(1).base.id);
			} else {
				baseInfo = "0 0";
			}
			data.add(width + " " + height + " " + map.size() + " " + baseInfo + " fog");
			while (it.hasNext()) {
				Tile t = it.next();
				data.add(t.x + " " + t.y + " " + t.toStringInit() + " " + t.id);
			}

			return data;
		}

		public List<String> toStringInitAsGraph(List<Player> players) {
			if (edges == null) {
				getLinkCount();
			}

			ArrayList<String> data = new ArrayList<String>();
			Iterator<Tile> it = graph.iterator();
			while (it.hasNext()) {
				Tile t = it.next();
				if (players.get(0).base.id == t.id || players.get(1).base.id == t.id)
					data.add(t.id + " " + t.platinum);
				else
					data.add(t.id + " " + 0);
			}
			Iterator<Edge> itEdge = edges.iterator();
			while (itEdge.hasNext()) {
				Edge e = itEdge.next();
				data.add(e.toString());
			}
			return data;
		}

		public boolean adjacent(int x1, int y1, int x2, int y2) {
			if (y1 == y2 && x1 == x2)
				return false;
			if (Math.abs(y1 - y2) > 1 || Math.abs(x1 - x2) > 1)
				return false;

			List<Coord> around;
			if (x1 % 2 == 0)
				around = aroundEven;
			else
				around = aroundOdd;

			for (Coord c : around) {
				if (c.x + x1 == x2 && c.y + y1 == y2)
					return true;
			}

			return false;
		}

		public List<Battle> update(List<Player> players) {
			List<Battle> battleInfos = new LinkedList<>();
			for (int i = 0; i < graph.size(); i++) {
				Tile t = graph.get(i);
				Battle battleInfo = t.update();
				if (battleInfo.hasActivity()) {
					if (battleInfo.hasCapturer()) {
						incZones(battleInfo.capturer);
						decZones(battleInfo.oldOwner);
						income[battleInfo.capturer] += t.platinum;
						if (battleInfo.oldOwner >= 0) {
							income[battleInfo.oldOwner] -= t.platinum;
						}

					}
					if (battleInfo.hasFight()) {
						for (Integer ind : battleInfo.contenders) {
							units[ind] -= battleInfo.deaths[ind];
						}
					}

					battleInfo.setHex(new Coord(t.x, t.y), i);
					battleInfos.add(battleInfo);
				}
				if (t.units[0] == 0) {
					occupiedTiles.get(0).remove(t);
				} else {
					occupiedTiles.get(0).add(t);
				}
				if (t.units[1] == 0) {
					occupiedTiles.get(1).remove(t);
				} else {
					occupiedTiles.get(1).add(t);
				}
			}

			for (int i = 0; i < 2; i++) {
				Set<Integer> sight = visibleTiles.get(i);
				sight.clear();
				Set<Tile> tiles = occupiedTiles.get(i);
				for (Tile tile : tiles) {
					sight.add(tile.id);
					for (int neighbourId : tile.neighbours) {
						sight.add(neighbourId);
					}
				}
				sight.add(players.get(0).base.id);
				sight.add(players.get(1).base.id);
			}

			return battleInfos;
		}
		public void place(int id, int bought, int x, int y) {
			Tile t = get(x, y);
			t.add(bought, id);
			units[id] += bought;
		}

		private void decZones(int id) {
			zones[id + 1]--;
		}
		private void incZones(int id) {
			zones[id + 1]++;
		}

		int getZones(int id) {
			return zones[id + 1];
		}

		public int getTileCountFor(int player) {

			int count = 0;
			for (Coord c : map.keySet()) {
				Tile t = map.get(c);
				if (t.ownerId == player)
					count++;
			}
			return count;
		}

		public List<String> toStringForPlayer(List<Player> players, int playerId) {
			List<String> data = new ArrayList<String>();

			for (Tile t : graph) {
				boolean visible = visibleTiles.get(playerId).contains(t.id);
				int platinum = visible ? t.platinum : 0;
				int pods0 = 0;
				int pods1 = 0;
				if (playerId == 0 || playerId == 1 && visible) {
					pods0 = t.units[0];
				}
				if (playerId == 1 || playerId == 0 && visible) {
					pods1 = t.units[1];
				}
				data.add(t.id + " " + (visible ? t.ownerId : -1) + " " + pods0 + " " + pods1 + " " + (visible ? 1 : 0) + " " + platinum);

			}
			return data;
		}

		public int getIncomeFor(int playerId) {
			return income[playerId];
		}

		public boolean hasPlayerLost(Player player) {
			if (player.base.ownerId != player.id)
				return true;
			return false;
		}
		public boolean isPlayerDead(Player player) {
			int id = player.id;
			// Player has a unit
			if (units[id] > 0)
				return false;
			// Player can buy units
			if (player.platinum >= UNIT_COST && (getZones(NEUTRAL) > 0 || getZones(id) > 0))
				return false;
			// Player owns a hex with platinum on it
			if (income[id] > 0)
				return false;

			return true;
		}
		public Tile get(Coord coord) {
			return map.get(coord);
		}
		public Tile get(int id) {
			if (id >= 0 && id < graph.size())
				return graph.get(id);
			return null;
		}
		public boolean adjacent(int sid, int did) {
			Tile a = get(sid);
			Tile b = get(did);
			if (a == null || b == null)
				return false;
			if (a.neighbours.contains(Integer.valueOf(did)))
				return true;
			return false;
		}

		Set<Edge> edges = null;
		int[] leftToPlace;
		PlayerPlacementSet prePlacements = new PlayerPlacementSet();
		List<Battle> preBattles;
		int hq0, hq1;

		public int getLinkCount() {
			if (edges != null) {
				return edges.size();
			}

			edges = new HashSet<Edge>();
			Iterator<Tile> it = graph.iterator();
			while (it.hasNext()) {
				Tile t = it.next();
				for (Integer i : t.neighbours) {
					edges.add(new Edge(t.id, i));
				}
			}

			return edges.size();
		}
		public int getZonesIn(int playerId, List<Coord> island) {
			int count = 0;
			for (Coord hex : island) {
				if (map.get(hex).ownerId == playerId) {
					count++;
				}
			}
			return count;
		}
		public void spawnRandom(int playerCount, int startUnits, List<Player> players, boolean symmetric, Integer hq0, Integer hq1) {
			List<Tile> noPlatinumtiles = new ArrayList<>();
			for (Tile t : graph) {
				if (t.platinum == 0) {
					noPlatinumtiles.add(t);
				}
			}
			if (symmetric && playerCount == 2 && noPlatinumtiles.size() >= 2) // symetrique
			{
				int idRandom = Referee.random.nextInt(noPlatinumtiles.size() / 2);
				if (hq0 != null) {
					idRandom = hq0;
				}
				this.hq0 = idRandom;

				Tile t = noPlatinumtiles.get(idRandom);
				Tile t2 = noPlatinumtiles.get(noPlatinumtiles.size() - 1 - idRandom);
				place(0, startUnits, t.x, t.y);
				prePlacements.add(new Placement(this, 0, startUnits, t));
				players.get(0).base = t;
				place(1, startUnits, t2.x, t2.y);
				prePlacements.add(new Placement(this, 1, startUnits, t2));
				players.get(1).base = t2;
			} else {
				for (int idPlayer = 0; idPlayer < playerCount; idPlayer++) {
					Tile t = null;
					if (idPlayer == 0) {
						if (hq0 != null) {
							this.hq0 = hq0;
						} else {
							this.hq0 = Referee.random.nextInt(noPlatinumtiles.size());
							;
						}
						t = noPlatinumtiles.get(this.hq0);
					} else {
						if (hq1 != null) {
							this.hq1 = hq1;
						} else {
							this.hq1 = Referee.random.nextInt(noPlatinumtiles.size());
							;
						}
						t = noPlatinumtiles.get(this.hq1);
					}
					place(idPlayer, startUnits, t.x, t.y);
					noPlatinumtiles.remove(t);
					prePlacements.add(new Placement(this, idPlayer, startUnits, t));
					players.get(idPlayer).base = t;
				}
			}
			preBattles = update(players);
		}

		private Coord getRoot(Coord[][] parent, Coord act) {
			if (!act.equals(parent[act.x][act.y])) {
				Coord copie = getRoot(parent, parent[act.x][act.y]);
				parent[act.x][act.y] = new Coord(copie.x, copie.y);
			}
			return parent[act.x][act.y];
		}

		public class compareCoord implements Comparator<Coord> {
			@Override
			public int compare(Coord coord1, Coord coord2) {
				if (coord1.x != coord2.x)
					return (new Integer(coord1.x)).compareTo(new Integer(coord2.x));
				return (new Integer(coord1.y)).compareTo(new Integer(coord2.y));
			}
		}

		public String generateRandomMap(int width, int height, double proportion, boolean symetric) {
			StringBuilder dataMap = new StringBuilder();
			List<Coord> candidates = new ArrayList<Coord>();
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					candidates.add(new Coord(x, y));
				}
			}

			int nbRoads = 0;

			// To verify if a graph is connected, we will use union find
			// algorithm
			// To optimize the verification, we can stock each roots created.
			// The graph will be connected when only one root is present
			// Memory complexity : O(width*height/2)
			// Time complexity: O(width*height*max(1/2,proportion))

			int[] dx = { 0, 0, 1, 1, 1, -1, -1, -1 };
			int[] dy = { 1, -1, 0, 1, -1, 0, 1, -1 };

			List<Coord> res = new ArrayList<Coord>();

			Coord[][] parent = new Coord[width][height];
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					parent[x][y] = new Coord(-1, -1);
				}
			}
			List<Coord> roots = new ArrayList<Coord>();

			// we need at least two areas (for the two players), the graph
			// should be connected (roots.size() == 1) and respect the
			// proportion
			while (proportion > (double) nbRoads / (double) (width * height) || roots.size() != 1 || nbRoads < 2) {
				Coord toCopy = candidates.get(Referee.random.nextInt(candidates.size()));
				Coord toAdd = new Coord(toCopy.x, toCopy.y);
				for (int sym = 0; sym < (symetric ? 2 : 1); sym++) {
					// Can occur on a symetric map with odd width and odd height
					// (map center that we try to roadify two times)
					candidates.remove(toAdd);
					parent[toAdd.x][toAdd.y] = new Coord(toAdd.x, toAdd.y);
					for (int d = 0; d < 8; d++) {
						Coord neighbour = new Coord(toAdd.x + dx[d], toAdd.y + dy[d]);
						if (neighbour.x >= width || neighbour.y >= height || neighbour.x < 0 || neighbour.y < 0 || !adjacent(neighbour.x, neighbour.y, toAdd.x, toAdd.y)) {
							continue;
						}
						if (parent[neighbour.x][neighbour.y].x == -1) {
							continue;
						}

						Coord r = getRoot(parent, neighbour);
						if (!r.equals(toAdd)) {
							parent[r.x][r.y] = new Coord(toAdd.x, toAdd.y);
							roots.remove(r);
						}
					}
					roots.add(new Coord(toAdd.x, toAdd.y));
					res.add(new Coord(toAdd.x, toAdd.y));
					nbRoads++;
					if (toAdd.x == width - toAdd.x - 1 && toAdd.y == height - toAdd.y - 1)
						break;
					toAdd = new Coord(width - toAdd.x - 1, height - toAdd.y - 1);
				}
			}

			Collections.sort(res, new compareCoord());
			for (Coord coord : res) {
				dataMap.append(coord.x).append(" ").append(coord.y).append(" ");
			}
			return dataMap.toString();

		}

	}

	@SuppressWarnings("serial")
	static class InvalidMoveOrPlacementException extends Exception {
		private static final Object[] NO_REPLACEMENT = new Object[0];
		public String action;
		Object[] replacements = NO_REPLACEMENT;

		public InvalidMoveOrPlacementException(String type) {
			super(type);
		}

		public InvalidMoveOrPlacementException(String type, Object... replacements) {
			super(type);
			this.replacements = replacements;
		}

		@Override
		public String toString() {
			return String.format("\"%s\" is an invalid action: %s", action, String.format(getMessage(), replacements));
		}
	}

	class Move implements Runnable {
		Player player;
		int fromX, fromY, toX, toY, amount, fromId, toId;
		HexGrid map;

		public Move(HexGrid map, Player player, Tile from, Tile to, int amount) {
			this.player = player;
			this.fromX = from.x;
			this.fromY = from.y;
			this.toX = to.x;
			this.toY = to.y;
			this.map = map;
			this.amount = amount;
			this.fromId = from.id;
			this.toId = to.id;
		}
		@Override
		public void run() {
			Tile from = map.get(fromX, fromY);
			Tile to = map.get(toX, toY);
			from.remove(amount, player.id);
			to.add(amount, player.id);
		}
		@Override
		public String toString() {
			return String.format("%d %d %d %d %d %d", player.id, amount, fromX, fromY, toX, toY);
		}
	}

	static class Placement implements Runnable {
		private HexGrid map;
		int amount, x, y, id, playerId;

		public Placement(HexGrid map, int playerId, int amount, Tile tile) {
			this.map = map;
			this.playerId = playerId;
			this.amount = amount;
			this.x = tile.x;
			this.y = tile.y;
			this.id = tile.id;
		}

		@Override
		public String toString() {
			return String.format("%d %d %d %d", playerId, amount, x, y);
		}

		@Override
		public void run() {
			map.place(playerId, amount, x, y);
		}
	}

	static class Mistake {
		String message;
		int count = 0;

		public void oneMore() {
			count++;
		}
	}

	static class Player {
		int platinum, id, deadAt, speechHex, speechHexNext, expenses, advantage;
		boolean dead, timeout, eliminated, extinct;
		String speech;
		int lastMoves = 0;
		int lastPlaces = 0;
		Tile base;

		List<String> lastActions;
		LinkedHashMap<String, Mistake> mistakes;

		public Player(int id) {
			this.id = id;
			timeout = false;
			eliminated = false;
			extinct = false;
			dead = false;
			advantage = 0;
			platinum = START_PLATINUM;
			lastActions = new LinkedList<String>();
			expenses = 0;
			mistakes = new LinkedHashMap<>();
			speechHexNext = -1;
		}
		public void setDead(int round) {
			deadAt = round;
			dead = true;
		}
		public boolean hasSpeech() {
			return speech != null;
		}

		public void setSpeech(String m, Tile t) {
			speech = m;
			speechHex = t.id;
			speechHexNext = -1;
		}
		public void setSpeech(String message, Tile from, Tile to) {
			speech = message;
			speechHex = from.id;
			speechHexNext = to.id;
		}
		public void reset() {
			speech = null;
			lastActions.clear();
			lastMoves = 0;
			lastPlaces = 0;
			mistakes.clear();
			speechHexNext = -1;
			expenses = 0;
		}
		public void setTimeout(int turn) {
			setDead(turn);
			timeout = true;
		}

		public void addMistake(String type, String message) {
			Mistake mistake = mistakes.get(type);
			if (mistake != null) {
				mistake.oneMore();
			} else {
				mistake = new Mistake();
				mistake.message = message;
				mistakes.put(type, mistake);
			}
		}
		public void eliminate(int turn) {
			eliminated = true;
			setDead(turn);
		}
	}

	class PlayerMoveSet implements Iterable<Move> {
		private LinkedList<Move> moves = new LinkedList<>();

		public void add(Move moveAction) {

			for (Move move : moves) {
				if (move.fromX == moveAction.fromX && move.fromY == moveAction.fromY && move.toX == moveAction.toX && move.toY == moveAction.toY && move.player.id == moveAction.player.id) {
					move.amount += moveAction.amount;
					return;
				}
			}
			moves.add(moveAction);

		}

		public void clear() {
			moves.clear();
		}

		@Override
		public Iterator<Move> iterator() {
			return moves.iterator();
		}

	}

	static class PlayerPlacementSet implements Iterable<Placement> {
		private LinkedList<Placement> placements = new LinkedList<>();

		public void add(Placement placeAction) {
			for (Placement placement : placements) {
				if (placement.x == placeAction.x && placement.y == placeAction.y && placement.playerId == placeAction.playerId) {
					placement.amount += placeAction.amount;
					return;
				}
			}
			placements.add(placeAction);

		}

		public void clear() {
			placements.clear();
		}

		@Override
		public Iterator<Placement> iterator() {
			return placements.iterator();
		}

		public int size() {
			return placements.size();
		}

	}

	public static void main(String... args) {

		try {
			new Referee(System.in, System.out, System.err);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private HexGrid map;
	private long seed;
	private List<Player> players;
	private PlayerMoveSet playerMoves;
	private PlayerPlacementSet playerPlacements;
	private List<Battle> battles;
	private List<Placement> playerPlacementsForView;
	private List<Move> playerMovesForView;
	private int totalPlatinum;
	private String mapData;
	private Distribution distribution;
	private boolean noEmptyIslands;
	private boolean symmetric;
	private int width;
	private int height;
	private double proportion;
	private boolean procedural;

	protected static Random random;

	public Referee(InputStream arg0, PrintStream arg1, PrintStream arg2) throws IOException {
		super(arg0, arg1, arg2);
	}

	@Override
	protected String getGameName() {
		return "PlatinumRift";
	}
	@Override
	protected void initReferee(int playerCount, Properties prop) throws InvalidFormatException {
		seed = Long.valueOf(prop.getProperty("seed", String.valueOf(new Random(System.currentTimeMillis()).nextLong())));
		random = new Random(seed);
		long distributionSeed = random.nextLong(); 

		// noEmptyIslands = Boolean.valueOf(prop.getProperty("noEmptyIslands",
		// String.valueOf(random.nextBoolean())));
		noEmptyIslands = false;
		symmetric = Boolean.valueOf(prop.getProperty("symmetric", String.valueOf(random.nextBoolean())));
		proportion = Double.valueOf(prop.getProperty("proportion", String.valueOf(random.nextDouble() / 2)));
		String algoName = prop.getProperty("distribution", Distribution.values()[random.nextInt(Distribution.values().length)].name());
		String hq = prop.getProperty("hq");
		Integer hq0 = null;
		Integer hq1 = null;
		if (hq != null) {
			String[] hqs = hq.split(" ");
			try {
				hq0 = Integer.valueOf(hqs[0]);
			} catch (Exception e) {

			}
			try {
				hq1 = Integer.valueOf(hqs[1]);
			} catch (Exception e) {

			}
		}

		try {
			distribution = Distribution.valueOf(algoName);
		} catch (IllegalArgumentException e) {
			distribution = Distribution.values()[random.nextInt(Distribution.values().length)];
		}

		START_PLATINUM = Integer.valueOf(prop.getProperty("startPlatinum", String.valueOf(START_PLATINUM)));
		UNIT_COST = Integer.valueOf(prop.getProperty("unitCost", String.valueOf(UNIT_COST)));
		START_UNITS = Integer.valueOf(prop.getProperty("startUnits", String.valueOf(START_UNITS)));

		String[] maps = { "1 6 1 7 1 8 1 9 1 10 1 11 1 12 1 13 1 14 1 17 1 18 1 19 1 20 1 21 2 6 2 7 2 8 2 9 2 10 2 11 2 12 2 13 2 14 2 15 2 17 2 22 3 5 3 6 3 14 3 15 3 16 3 21 4 5 4 6 4 11 4 12 4 13 4 14 4 15 4 16 4 21 4 22 5 4 5 5 5 10 5 11 5 12 5 13 5 14 5 20 5 22 6 4 6 5 6 6 6 10 6 11 6 12 6 13 6 14 6 17 6 18 6 19 6 20 6 23 7 3 7 4 7 5 7 6 7 7 7 8 7 9 7 10 7 11 7 12 7 13 7 16 7 19 7 22 8 3 8 4 8 12 8 13 8 16 8 19 8 22 9 2 9 3 9 8 9 9 9 10 9 11 9 12 9 15 9 16 9 17 9 18 9 21 10 2 10 3 10 9 10 10 10 11 10 12 10 18 10 21 11 1 11 2 11 3 11 7 11 9 11 10 11 11 11 14 11 15 11 16 11 17 11 20 12 1 12 2 12 3 12 4 12 5 12 6 12 7 12 8 12 10 12 11 12 14 12 17 12 20 13 0 13 1 13 3 13 4 13 8 13 9 13 10 13 13 13 16 13 19 14 0 14 1 14 3 14 4 14 6 14 7 14 9 14 10 14 11 14 12 14 13 14 14 14 15 14 16 14 19 15 0 15 2 15 3 15 5 15 6 15 8 15 9 15 15 15 18 16 0 16 1 16 5 16 6 16 8 16 9 16 10 16 12 16 13 16 14 16 15 16 18 17 0 17 1 17 2 17 3 17 4 17 5 17 6 17 7 17 8 17 14 17 17 18 1 18 2 18 3 18 4 18 5 18 6 18 7 18 8 18 11 18 12 18 13 18 14 18 17 19 7 19 11 19 16 20 7 20 12 20 16 21 6 21 9 21 10 21 11 21 12 21 15 21 16 21 17 21 18 21 19 21 20 21 21 21 22 22 6 22 9 22 15 22 16 22 17 22 18 22 19 22 20 22 21 22 22 22 23 23 5 23 8 23 9 23 10 23 11 23 13 23 14 23 15 23 17 23 18 23 22 23 23 24 5 24 8 24 14 24 15 24 17 24 18 24 20 24 21 24 23 25 4 25 7 25 8 25 9 25 10 25 11 25 12 25 13 25 14 25 16 25 17 25 19 25 20 25 22 25 23 26 4 26 7 26 10 26 13 26 14 26 15 26 19 26 20 26 22 26 23 27 3 27 6 27 9 27 12 27 13 27 15 27 16 27 17 27 18 27 19 27 20 27 21 27 22 28 3 28 6 28 7 28 8 28 9 28 12 28 13 28 14 28 16 28 20 28 21 28 22 29 2 29 5 29 11 29 12 29 13 29 14 29 20 29 21 30 2 30 5 30 6 30 7 30 8 30 11 30 12 30 13 30 14 30 15 30 20 30 21 31 1 31 4 31 7 31 10 31 11 31 19 31 20 32 1 32 4 32 7 32 10 32 11 32 12 32 13 32 14 32 15 32 16 32 17 32 18 32 19 32 20 33 0 33 3 33 4 33 5 33 6 33 9 33 10 33 11 33 12 33 13 33 17 33 18 33 19 34 1 34 3 34 9 34 10 34 11 34 12 34 13 34 18 34 19 35 1 35 2 35 7 35 8 35 9 35 10 35 11 35 12 35 17 35 18 36 2 36 7 36 8 36 9 36 17 36 18 37 1 37 6 37 8 37 9 37 10 37 11 37 12 37 13 37 14 37 15 37 16 37 17 38 2 38 3 38 4 38 5 38 6 38 9 38 10 38 11 38 12 38 13 38 14 38 15 38 16 38 17", "0 8 0 21 1 8 1 9 1 10 1 11 1 12 1 21 1 22 2 3 2 4 2 5 2 11 2 13 2 14 2 22 3 2 3 4 3 5 3 6 3 7 3 14 3 15 3 22 4 2 4 7 4 8 4 9 4 10 4 11 4 16 4 17 4 23 5 2 5 9 5 10 5 11 5 12 5 17 5 19 5 22 6 2 6 5 6 6 6 12 6 13 6 18 6 19 6 22 6 23 7 2 7 6 7 9 7 13 7 14 7 18 7 19 7 22 8 2 8 7 8 8 8 10 8 15 8 19 8 23 9 1 9 6 9 7 9 8 9 10 9 14 9 15 9 19 9 22 9 23 10 5 10 6 10 7 10 9 10 10 10 12 10 16 10 19 10 20 10 23 11 4 11 5 11 9 11 10 11 12 11 16 11 19 11 22 12 3 12 4 12 7 12 8 12 10 12 11 12 13 12 16 12 17 12 20 12 22 12 23 13 3 13 8 13 10 13 11 13 12 13 16 13 19 13 22 14 3 14 8 14 11 14 12 14 16 14 20 14 22 14 23 15 2 15 5 15 6 15 7 15 8 15 9 15 11 15 12 15 14 15 15 15 16 15 19 15 22 16 2 16 5 16 6 16 7 16 9 16 10 16 11 16 12 16 13 16 14 16 15 16 19 16 22 17 1 17 4 17 9 17 10 17 11 17 12 17 13 17 14 17 18 17 21 17 22 18 1 18 3 18 4 18 6 18 7 18 10 18 11 18 13 18 14 18 15 18 17 18 18 18 21 18 22 19 3 19 7 19 8 19 9 19 10 19 13 19 14 19 15 19 16 19 17 19 20 19 21 20 2 20 3 20 6 20 7 20 8 20 9 20 10 20 13 20 14 20 15 20 16 20 20 21 1 21 2 21 5 21 6 21 8 21 9 21 10 21 12 21 13 21 16 21 17 21 19 21 20 21 22 22 1 22 2 22 5 22 9 22 10 22 11 22 12 22 13 22 14 22 19 22 22 23 1 23 4 23 8 23 9 23 10 23 11 23 12 23 13 23 14 23 16 23 17 23 18 23 21 24 1 24 4 24 7 24 8 24 9 24 11 24 12 24 14 24 15 24 16 24 17 24 18 24 21 25 0 25 1 25 3 25 7 25 11 25 12 25 15 25 20 26 1 26 4 26 7 26 11 26 12 26 13 26 15 26 20 27 0 27 1 27 3 27 6 27 7 27 10 27 12 27 13 27 15 27 16 27 19 27 20 28 1 28 4 28 7 28 11 28 13 28 14 28 18 28 19 29 0 29 3 29 4 29 7 29 11 29 13 29 14 29 16 29 17 29 18 30 0 30 1 30 4 30 8 30 9 30 13 30 15 30 16 30 17 30 22 31 0 31 4 31 8 31 13 31 15 31 16 31 21 32 1 32 4 32 5 32 9 32 10 32 14 32 17 32 21 33 0 33 1 33 4 33 5 33 10 33 11 33 17 33 18 33 21 34 1 34 4 34 6 34 11 34 12 34 13 34 14 34 21 35 0 35 6 35 7 35 12 35 13 35 14 35 15 35 16 35 21 36 1 36 8 36 9 36 16 36 17 36 18 36 19 36 21 37 1 37 9 37 10 37 12 37 18 37 19 37 20 38 1 38 2 38 11 38 12 38 13 38 14 38 15 39 2 39 15", "0 1 0 2 0 3 0 4 0 9 0 10 0 11 1 0 1 1 1 2 1 3 1 4 1 9 1 10 1 11 2 0 2 1 2 2 2 3 2 5 2 9 2 10 2 11 3 0 3 1 3 2 3 5 3 7 3 8 3 10 3 11 4 0 4 1 4 2 4 6 4 7 4 9 4 11 5 0 5 1 5 2 5 4 5 6 5 9 5 11 6 0 6 1 6 3 6 4 6 5 6 7 6 8 6 10 7 0 7 3 7 4 7 5 7 7 7 9 8 0 8 1 8 3 8 4 8 5 8 6 8 8 8 9 9 1 9 3 9 4 9 8 10 2 10 4 10 5 10 9 11 2 11 6 11 7 11 9 12 3 12 7 12 8 12 10 13 2 13 3 13 5 13 6 13 7 13 8 13 10 13 11 14 2 14 4 14 6 14 7 14 8 14 11 15 1 15 3 15 4 15 6 15 7 15 8 15 10 15 11 16 0 16 2 16 5 16 7 16 9 16 10 16 11 17 0 17 2 17 4 17 5 17 9 17 10 17 11 18 0 18 1 18 3 18 4 18 6 18 9 18 10 18 11 19 0 19 1 19 2 19 6 19 8 19 9 19 10 19 11 20 0 20 1 20 2 20 7 20 8 20 9 20 10 20 11 21 0 21 1 21 2 21 7 21 8 21 9 21 10", "1 10 1 11 2 2 2 3 2 4 2 5 2 6 2 7 2 8 2 9 2 10 2 12 3 1 3 2 3 3 3 4 3 5 3 6 3 7 3 9 3 10 3 11 4 1 4 2 4 3 4 5 4 8 4 9 4 11 4 12 5 0 5 1 5 3 5 5 5 6 5 8 5 9 5 10 5 11 6 1 6 2 6 4 6 6 6 7 6 8 6 10 6 12 7 0 7 1 7 3 7 4 7 7 7 8 7 9 7 10 7 11 8 1 8 2 8 4 8 5 8 7 8 8 8 11 8 12 9 0 9 1 9 5 9 6 9 7 9 10 9 11 10 1 10 2 10 3 10 6 10 7 10 8 10 9 10 11 10 12 11 0 11 1 11 3 11 4 11 5 11 6 11 9 11 10 11 11 12 1 12 2 12 5 12 6 12 7 12 11 12 12 13 0 13 1 13 4 13 5 13 7 13 8 13 10 13 11 14 1 14 2 14 3 14 4 14 5 14 8 14 9 14 11 14 12 15 0 15 2 15 4 15 5 15 6 15 8 15 10 15 11 16 1 16 2 16 3 16 4 16 6 16 7 16 9 16 11 16 12 17 0 17 1 17 3 17 4 17 7 17 9 17 10 17 11 18 1 18 2 18 3 18 5 18 6 18 7 18 8 18 9 18 10 18 11 19 0 19 2 19 3 19 4 19 5 19 6 19 7 19 8 19 9 19 10 20 1 20 2", "0 0 0 1 0 2 0 3 0 4 0 5 0 6 0 7 0 8 0 9 1 0 1 1 1 2 1 3 1 4 1 5 1 6 1 7 1 8 1 9 2 0 2 1 2 2 2 3 2 4 2 5 2 6 2 7 2 8 2 9 3 0 3 1 3 2 3 3 3 4 3 5 3 6 3 7 3 8 3 9 4 0 4 1 4 2 4 3 4 4 4 5 4 6 4 7 4 8 4 9 5 0 5 1 5 2 5 3 5 4 5 5 5 6 5 7 5 8 5 9 6 0 6 1 6 2 6 3 6 4 6 5 6 6 6 7 6 8 6 9 7 0 7 1 7 2 7 3 7 4 7 5 7 6 7 7 7 8 7 9 8 0 8 1 8 2 8 3 8 4 8 5 8 6 8 7 8 8 8 9 9 0 9 1 9 2 9 3 9 4 9 5 9 6 9 7 9 8 9 9 10 0 10 1 10 2 10 3 10 4 10 5 10 6 10 7 10 8 10 9 11 0 11 1 11 2 11 3 11 4 11 5 11 6 11 7 11 8 11 9 12 0 12 1 12 2 12 3 12 4 12 5 12 6 12 7 12 8 12 9 13 0 13 1 13 2 13 3 13 4 13 5 13 6 13 7 13 8 13 9 14 0 14 1 14 2 14 3 14 4 14 5 14 6 14 7 14 8 14 9 15 0 15 1 15 2 15 3 15 4 15 5 15 6 15 7 15 8 15 9 16 0 16 1 16 2 16 3 16 4 16 5 16 6 16 7 16 8 16 9 17 0 17 1 17 2 17 3 17 4 17 5 17 6 17 7 17 8 17 9 18 0 18 1 18 2 18 3 18 4 18 5 18 6 18 7 18 8 18 9 19 0 19 1 19 2 19 3 19 4 19 5 19 6 19 7 19 8 19 9" };

		mapData = prop.getProperty("map", null);
		map = new HexGrid();
		int pushTotalPlatinum = 120;
		if (mapData == null) {
			String H = prop.getProperty("height");
			String W = prop.getProperty("width");
			boolean pushProcedural = random.nextBoolean();
			if (W != null || H != null) {
				pushProcedural = true;
			}
			procedural = Boolean.valueOf(prop.getProperty("procedural", String.valueOf(pushProcedural)));
			if (procedural) {
				height = Integer.valueOf(prop.getProperty("height", "-1"));
				width = Integer.valueOf(prop.getProperty("width", "-1"));

				if (width == -1 && height == -1) {
					height = random.nextInt(MAX_HEIGHT - MIN_HEIGHT) + MIN_HEIGHT;
				} else if (width != -1) {
					height = (int) (width / MAP_RATIO);
				}
				if (width == -1) {
					width = (int) (height * MAP_RATIO);
				}
				height = Math.min(HEIGHT_HIGHEST, Math.max(HEIGHT_LOWEST, height));
				width = Math.min(WIDTH_HIGHEST, Math.max(WIDTH_LOWEST, width));
				if (symmetric) {
					width += width % 2; // Only maps with an even width can be
										// symmetric
				}

				mapData = map.generateRandomMap(width, height, proportion, symmetric);
				int hexCount = mapData.split(" ").length / 2;
				pushTotalPlatinum = hexCount;
			} else {
				mapData = maps[random.nextInt(maps.length)];
			}
		} else {
			procedural = false;
		}

		totalPlatinum = Integer.valueOf(prop.getProperty("platinum", String.valueOf(pushTotalPlatinum)));
		map.generate(playerCount, mapData, totalPlatinum, distribution, noEmptyIslands, symmetric, distributionSeed);
		playerMoves = new PlayerMoveSet();
		playerPlacements = new PlayerPlacementSet();
		battles = new LinkedList<>();
		playerMovesForView = new LinkedList<>();
		playerPlacementsForView = new LinkedList<>();
		players = new ArrayList<Player>(playerCount);
		for (int i = 0; i < playerCount; i++) {
			Player p = new Player(i);
			players.add(p);

		}
		map.spawnRandom(playerCount, START_UNITS, players, symmetric, hq0, hq1);
	}
	@Override
	protected Properties getConfiguration() {
		Properties prop = new Properties();
		prop.put("seed", seed);
		prop.put("distribution", distribution);
		prop.put("platinum", totalPlatinum);
		prop.put("startPlatinum", START_PLATINUM);
		prop.put("unitCost", UNIT_COST);
		prop.put("startUnits", START_UNITS);
		prop.put("map", mapData);
		prop.put("symmetric", symmetric);
		prop.put("procedural", procedural);
		prop.put("hq", map.hq0 + " " + map.hq1);
		if (procedural) {
			prop.put("width", width);
			prop.put("height", height);
		}

		return prop;
	}
	@Override
	protected void populateMessages(Properties p) {
		p.put("wait", "$%d bides their time.");
		p.put("dead", "$%d no longer plays in this game.");
		p.put("move", "$%d moved %d pod%s from %d to %d.");
		p.put("groupMove", "$%d moved %d pod%s.");
		p.put("groupPlace", "$%d spawned %d pod%s.");
		p.put("place", "$%d spawned %d pod%s at %d.");
		p.put("status", "$%d score: %d.");
		p.put("battle", "The battle at %d has cost %s up to three pods each.");
		p.put("capture", "$%d has captured %s!");
	}
	@Override
	protected String[] getAdditionalFrameDataAtGameStartForView() {
		List<String> data = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		sb.append(players.size()).append(" ");
		for (Player p : players) {
			sb.append(p.platinum).append(" ");
		}
		data.add(sb.toString().trim());

		data.addAll(map.toStringInitAsMap(players));
		return data.toArray(new String[data.size()]);
	}
	@Override
	protected String[] getFrameDataForView(int round, int frame, int player, boolean keyFrame) {
		List<String> data = new ArrayList<>();

		for (Player p : players) {
			Tile t = map.get(p.speechHex);
			int nx = -1, ny = -1;
			if (p.speechHexNext >= 0) {
				Tile next = map.get(p.speechHexNext);
				nx = next.x;
				ny = next.y;
			}
			data.add(String.format("%d %d %d %d %d %d %d %d", p.platinum, getZones(p.id), getScore(p.id), (p.hasSpeech() ? 1 : 0), t.x, t.y, nx, ny));
			if (p.hasSpeech()) {
				data.add(p.speech.trim());
			}
		}

		if (!keyFrame) {
			data.add("0");
			data.add("0");
		} else {
			if (round == 0) {
				data.add(Integer.toString(map.prePlacements.size()));
				for (Placement placement : map.prePlacements) {
					data.add(placement.toString());
				}
				data.add("0");
				data.add(Integer.toString(map.preBattles.size()));
				for (Battle battle : map.preBattles) {
					data.add(battle.toString());
				}
			} else {
				data.add(Integer.toString(playerPlacementsForView.size()));
				for (Placement placement : playerPlacementsForView) {
					data.add(placement.toString());
				}
				data.add(Integer.toString(playerMovesForView.size()));
				for (Move move : playerMovesForView) {
					data.add(move.toString());
				}
				data.add(Integer.toString(battles.size()));
				for (Battle battle : battles) {
					data.add(battle.toString());
				}
			}
			playerPlacementsForView.clear();
			playerMovesForView.clear();
		}

		StringBuilder visibles = new StringBuilder();
		for (int p = 0; p < 2; p++) {
			for (int i : map.visibleTiles.get(p)) {
				visibles.append(i).append(" ");
			}
			if (p == 0)
				visibles.append("!");
		}
		data.add(visibles.toString());
		
		return data.toArray(new String[data.size()]);
	}
	@Override
	protected int getExpectedOutputLineCountForPlayer(int player) {
		return 2;
	}

	private String parsePlayerMessage(String[] output, int line) {
		String userMessage = null;
		char charQuote = '#';
		int quoteIdx = output[line].indexOf(charQuote);
		if (quoteIdx == -1) {
			charQuote = '\'';
			quoteIdx = output[line].indexOf(charQuote);
		}
		if (quoteIdx != -1) {
			int lastQuoteIndex = output[line].length();
			lastQuoteIndex = Math.min(quoteIdx + 1 + MAX_MESSAGE_LENGTH, lastQuoteIndex);
			if (quoteIdx + 1 < lastQuoteIndex) {
				userMessage = output[line].substring(quoteIdx + 1, lastQuoteIndex).trim();
				if (userMessage.length() == 0) {
					userMessage = null;
				}
			}
			output[line] = output[line].substring(0, quoteIdx).trim();
		}
		return userMessage;
	}

	@Override
	protected void handlePlayerOutput(int turn, int round, int playerId, String[] output) throws WinException, LostException, InvalidInputException {
		Player player = players.get(playerId);

		if (map.isPlayerDead(player) || map.hasPlayerLost(player)) {
			player.eliminate(turn);
			// Do not return
		}

		output[0] = output[0].trim();
		output[1] = output[1].trim();
		String moveMessage = parsePlayerMessage(output, 0);
		// String placeMessage = null;
		// if (moveMessage == null)
		// placeMessage = parsePlayerMessage(output, 1);
		String[] moveData = null;
		if (!output[0].equalsIgnoreCase("wait") && !output[0].isEmpty()) {
			moveData = output[0].split("\\s+");
		}

		PlayerMoveSet frameMoves = new PlayerMoveSet();
		PlayerPlacementSet framePlacements = new PlayerPlacementSet();

		if (moveData != null) {

			int movel = moveData.length / 3 * 3;

			if (moveData.length < 3) {
				player.setDead(turn);
				throw new InvalidInputException("x y z | \"WAIT\"", output[0]);
			}
			if (moveData.length % 3 != 0) {
				StringBuilder extra = new StringBuilder();
				for (int i = movel; i < moveData.length; i++) {
					extra.append(moveData[i]).append(' ');
				}
				extra.setLength(extra.length() - 1);
				player.setDead(turn);
				throw new InvalidInputException("multiple of 3 integers | \"WAIT\"", "trailing " + extra);
			}

			Tile msgFrom = null;
			Tile msgTo = null;

			for (int move = 0; move < movel;) {
				String n1 = moveData[move++];
				String n2 = moveData[move++];
				String n3 = moveData[move++];

				try {
					int n = Integer.valueOf(n1);
					int sid = Integer.valueOf(n2);
					int did = Integer.valueOf(n3);

					try {
						// Is n > 0 ?
						if (n <= 0)
							throw new InvalidMoveOrPlacementException("You can't move zero pods");

						// Does player have at least n pods at sid?
						Tile from = map.get(sid);
						if (from == null)
							throw new InvalidMoveOrPlacementException("Source hex %d is not part of the map", sid);
						int podCount = from.getPodCountFor(playerId, frameMoves);
						if (podCount < n)
							throw new InvalidMoveOrPlacementException("There aren't enough pods (%d instead of %d) on hex %d", podCount, n, sid);

						// Is this accessible from sid?
						Tile to = map.get(did);
						if (to == null)
							throw new InvalidMoveOrPlacementException("Destination hex %d is not part of the map", did);
						if (!map.adjacent(sid, did))
							throw new InvalidMoveOrPlacementException("Destination hex %d is not adjacent to source hex %d", did, sid);

						// Is player allowed across the border?
						if (from.contested && to.ownerId != player.id && to.ownerId != -1) {
							throw new InvalidMoveOrPlacementException("Cannot retreat from  %d to %d as destination is controlled by enemy", sid, did);
						}

						// All good
						msgFrom = from;
						msgTo = to;
						playerMoves.add(new Move(map, player, from, to, n));
						frameMoves.add(new Move(map, player, from, to, n));

					} catch (InvalidMoveOrPlacementException e) {
						e.action = n1 + " " + n2 + " " + n3;
						// printError(e);
						player.addMistake(e.getMessage(), e.toString());
						continue;
					}

				} catch (NumberFormatException nfe) {
					player.setDead(turn);
					throw new InvalidInputException("x y z | \"WAIT\"", n1 + " " + n2 + " " + n3);
				}
			}
			if (moveMessage != null && msgFrom != null && msgTo != null) {
				player.setSpeech(moveMessage, msgFrom, msgTo);
			}
		}

		for (Move move : frameMoves) {
			playerMovesForView.add(move);
			player.lastMoves += move.amount;
			player.lastActions.add(translate("move", playerId, move.amount, move.amount > 1 ? "s" : "", move.fromId, move.toId));
		}
		frameMoves.clear();

		if (player.platinum >= UNIT_COST && UNIT_COST != 0) {
			int podsToBuy = player.platinum / UNIT_COST;
			Tile t = player.base;
			playerPlacements.add(new Placement(map, player.id, podsToBuy, t));
			framePlacements.add(new Placement(map, player.id, podsToBuy, t));
			player.platinum %= UNIT_COST;
		}

		for (Placement place : framePlacements) {
			playerPlacementsForView.add(place);
			player.lastPlaces += place.amount;
			player.lastActions.add(translate("place", playerId, place.amount, place.amount > 1 ? "s" : "", place.id));
		}
		framePlacements.clear();
	}

	@Override
	protected String[] getInitInputForPlayer(int player) {
		List<String> data = new ArrayList<>();
		data.add(players.size() + " " + player + " " + map.graph.size() + " " + map.getLinkCount());
		data.addAll(map.toStringInitAsGraph(players));
		return data.toArray(new String[data.size()]);
	}
	@Override
	protected String[] getInputForPlayer(int round, int playerId) {
		List<String> data = new ArrayList<>();
		data.add(Integer.toString(players.get(playerId).platinum));
		data.addAll(map.toStringForPlayer(players, playerId));
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
	protected void updateGame(int round) throws GameOverException {
		for (Move move : playerMoves) {
			move.run();
		}
		playerMoves.clear();

		for (Placement move : playerPlacements) {
			move.run();
		}
		playerPlacements.clear();

		List<Battle> battleInfos = map.update(players);

		battles.clear();
		for (Battle infos : battleInfos) {
			battles.add(infos);
		}

		for (Player p : players) {
			p.platinum -= p.expenses;
			p.platinum += map.getIncomeFor(p.id);
		}

		detectExtinction(round);
		detectEndOfGame();
	}

	private void detectExtinction(int round) {
		boolean needUpdate = false;
		for (int i = 0; i < players.size(); ++i) {
			int zones = map.getZones(i);
			Player p = players.get(i);
			if (zones == 0 && p.dead && !p.extinct) {
				p.extinct = true;
				needUpdate = true;
			}
		}
		if (needUpdate) {
			for (Player player : players) {
				if (!player.extinct && !player.timeout)
					player.advantage++;
			}
		}
	}

	private void detectEndOfGame() throws GameOverException {
		int[] owned = new int[players.size()];
		int[] potential = new int[players.size()];
		boolean[] canChange = new boolean[map.islands.size()];

		int allMyBasesAreBelongToMe = 0;
		for (int playerId = 0; playerId < players.size(); playerId++) {
			if (players.get(playerId).base.ownerId == playerId) {
				allMyBasesAreBelongToMe++;
			}
		}
		if (allMyBasesAreBelongToMe <= 1) {
			throw new GameOverException();
		}
		for (int i = 0, il = map.islands.size(); i < il; i++) {
			List<Coord> island = map.islands.get(i);
			int islandSize = island.size();
			int playerId = map.get(island.get(0)).ownerId;
			boolean isOwner = playerId != NEUTRAL;
			boolean[] possibles = new boolean[players.size()];
			canChange[i] = true;
			for (Coord c : island) {
				Tile t = map.get(c);

				if (isOwner && (t.ownerId != playerId || t.getEnemyCountFor(playerId) > 0)) {
					isOwner = false;
				}
				if (t.ownerId != NEUTRAL) {
					possibles[t.ownerId] = true;
				} else {
					for (int p = 0, pl = players.size(); p < pl; p++) {
						possibles[p] = true;
					}
					break;
				}
				if (t.isContested()) {
					for (int p = 0, pl = t.units.length; p < pl; p++) {
						if (t.units[p] > 0) {
							possibles[p] = true;
						}
					}
				}
			}
			int aliveContesters = 0;
			int deadContesters = 0;
			for (int j = 0, jl = players.size(); j < jl; j++) {
				if (possibles[j]) {
					if (!players.get(j).dead)
						aliveContesters++;
					else
						deadContesters++;
				}
			}

			if (aliveContesters == 0 || aliveContesters + deadContesters <= 1)
				canChange[i] = false;

			int soloPlayer = NOBODY;
			for (Player p : players) {
				if (possibles[p.id] && !p.dead) {
					if (soloPlayer == NOBODY) {
						soloPlayer = p.id;
					} else {
						soloPlayer = NOBODY;
						break;
					}
				}
			}

			for (int p = 0, pl = players.size(); p < pl; p++) {
				if (possibles[p]) {
					if (players.get(p).dead) {
						potential[p] += getZonesIn(p, island);
					} else {
						potential[p] += islandSize;
					}
				}
			}

			if (isOwner) {
				owned[playerId] += islandSize;
			} else if (soloPlayer != NOBODY) {
				owned[soloPlayer] += getZonesIn(soloPlayer, island);
			}
		}

		boolean gameFrozen = true;
		for (boolean b : canChange) {
			if (b) {
				gameFrozen = false;
				break;
			}
		}
		if (gameFrozen) {
			throw new GameOverException();
		}

		int sizeToRank = players.size() - 1;
		for (int p = 0, pl = players.size(); p < pl; p++) {
			if (potential[p] == 0) {
				sizeToRank--;
			}
		}
		if (sizeToRank == 0) {
			throw new GameOverException();
		}

		Set<Integer> ranked = new HashSet<>();
		boolean redo = false;

		do {
			redo = false;
			for (int p = 0, pl = players.size(); p < pl; p++) {
				if (ranked.contains(p)) {
					continue;
				}
				boolean master = true;
				for (int enemy = 0, el = players.size(); enemy < el; enemy++) {
					if (p == enemy || ranked.contains(enemy)) {
						continue;
					}
					if (owned[p] <= potential[enemy]) {
						master = false;
					}
				}
				if (master) {
					redo = true;
					ranked.add(p);
					break;
				}
			}
			if (ranked.size() == sizeToRank) {
				break;
			}
		} while (redo == true);

		if (ranked.size() == sizeToRank) {
			throw new GameOverException();
		}
	}

	private int getZonesIn(int playerId, List<Coord> island) {
		return map.getZonesIn(playerId, island);
	}

	@Override
	protected void prepare(int round) {
		for (Player p : players) {
			p.reset();
		}
	}
	@Override
	protected boolean isPlayerDead(int player) {
		Player p = players.get(player);
		return p.dead;
	}
	@Override
	protected int getScore(int player) {
		if (players.get(player).base.ownerId != players.get(player).id)
			return 0;
		return getZones(player) + players.get(player).advantage;
	}
	protected int getZones(int player) {
		return map.getZones(player);
	}
	@Override
	protected String[] getGameSummary(int round) {
		List<String> data = new ArrayList<>();
		data.add(String.format("=== Summary for round %d ===", round));

		HashMap<Integer, List<Integer>> captures = new HashMap<>();

		for (Battle battle : battles) {
			// if (battle.hasFight())
			// data.add(translate("battle", battle.getHexId(),
			// battle.getPlayers()));
			if (battle.hasCapturer()) {
				int capturer = battle.getCapturer();
				if (!captures.containsKey(capturer))
					captures.put(capturer, new LinkedList<Integer>());
				captures.get(capturer).add(battle.getHexId());
			}
		}

		for (Integer capturer : captures.keySet()) {
			List<Integer> captured = captures.get(capturer);

			StringBuilder sb = new StringBuilder();
			int size = captured.size();
			for (int i = 0; i < size; i++) {
				Integer hexId = captured.get(i);
				sb.append(hexId);
				if (size > 1) {
					if (i < size - 2)
						sb.append(", ");
					else if (i < size - 1)
						sb.append(" and ");
				}
			}
			data.add(translate("capture", capturer, sb.toString()));
		}

		for (Player player : players) {
			if (player.dead)
				data.add(translate("dead", player.id));
			else
				data.add(translate("status", player.id, getScore(player.id)));
		}

		return data.toArray(new String[data.size()]);
	}
	@Override
	protected String[] getPlayerActions(int playerId) {
		List<String> data = new ArrayList<>();
		Player player = players.get(playerId);
		List<String> playerActions = player.lastActions;

		if (playerActions.isEmpty()) {
			if (!player.dead)
				data.add(translate("wait", playerId));
		} else {
			if (player.lastMoves > 0) {
				data.add(translate("groupMove", playerId, player.lastMoves, player.lastMoves > 1 ? "s" : ""));
			}
			if (player.lastPlaces > 0) {
				data.add(translate("groupPlace", playerId, player.lastPlaces, player.lastPlaces > 1 ? "s" : ""));
			}
			// for (String action : playerActions)
			// data.add(action);
		}

		for (Mistake mistake : player.mistakes.values()) {
			if (mistake.count == 0) {
				data.add(mistake.message);
			} else {
				data.add(String.format("%s (and %d additional errors of the same type)", mistake.message, mistake.count));
			}
		}

		return data.toArray(new String[data.size()]);
	}
	@Override
	protected void setPlayerTimeout(int round, int player) {
		players.get(player).setTimeout(round);
	}
	@Override
	protected int getMaxRoundCount() {
		return MAX_ROUNDS;
	}

	@Override
	protected String getDeathReason(int player) {
		return "$" + player + " eliminated";
	}

	@Override
	protected boolean showDeathTooltips() {
		return true;
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
	protected boolean showDeathTooltips() {
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
	protected void setPlayerTimeout(int round, int player) {
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
		private boolean lost;
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

		public boolean hasLost() {
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
		VIEW, INFOS, NEXT_PLAYER_INPUT, NEXT_PLAYER_INFO, SCORES, UINPUT, TOOLTIP;
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
	private int playerCount;
	protected int alivePlayerCount;
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
		messages.put("InvalidInput", "invalid input. Expected '%s' but found '%s'");
		messages.put("playerTimeoutMulti", "Timeout: the program did not provide %d input lines in due time... $%d will no longer be active in this game.");
		messages.put("playerTimeoutSolo", "Timeout: the program did not provide %d input lines in due time...");
		messages.put("maxRoundsCountReached", "Maximum amount of rounds reached...");
		messages.put("notEnoughPlayers", "Not enough players (expected > %d, found %d)");
		populateMessages(messages);
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
					for (i = 0; i < playerCount; ++i) {
						players[i] = new PlayerStatus(i);
					}

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
						e.printStackTrace();
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
					} catch (InvalidInputException | LostException e) {
						playerStatus.score = getScore(nextPlayer);
						playerStatus.lost = true;
						playerStatus.info = e.getReason();
						playerStatus.reasonCode = e.getReasonCode();
						addDeathToolTip(nextPlayer, "$" + nextPlayer + ": invalid action");
						if (--alivePlayerCount < getMinimumPlayerCount()) {
							lastPlayer = playerStatus;
							throw new GameOverException();
						}
					} catch (WinException e) {
						playerStatus.score = getScore(nextPlayer);
						playerStatus.info = e.getReason();
						playerStatus.reasonCode = e.getReasonCode();
						lastPlayer = playerStatus;
						throw new GameOverException();
					}
					break;
				case SET_PLAYER_TIMEOUT:
					++frame;
					int count = getExpectedOutputLineCountForPlayer(nextPlayer);
					setPlayerTimeout(round, nextPlayer);
					playerStatus.lost = true;
					playerStatus.reasonCode = "timeout";
					if (playerCount <= 1) {
						playerStatus.info = translate("playerTimeoutSolo", count);
					} else {
						playerStatus.info = translate("playerTimeoutMulti", count, nextPlayer);
					}
					addDeathToolTip(nextPlayer, "$" + nextPlayer + ": timeout!");
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
			updateScores();
		} while (this.players[nextPlayer].lost);
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
			data.addAll(getFrameDataForView(this.round, this.frame, this.currentPlayer, this.newRound));
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
				if (head != null)
					data.add(getColoredReason(lastPlayer.lost, head));
			}
		}
		if (round >= 0) {
			if (frame > 0)
				data.addAll(getPlayerActions(this.currentPlayer));
			if (newRound && round >= 0) {
				data.addAll(getGameSummary(round));
			}
		}
		out.println(data);
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
		data.addAll(getInputForPlayer(this.round, nextPlayer));
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
				addDeathToolTip(i, players[i].info);
			}
			players[i].score = getScore(i);
		}
	}

	private void addDeathToolTip(int player, String message) {
		if (showDeathTooltips())
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

	protected abstract String[] getFrameDataForView(int round, int frame, int currentPlayer, boolean newRound);

	protected abstract int getExpectedOutputLineCountForPlayer(int player);

	protected abstract String getGameName();

	protected abstract void appendDataToEnd(PrintStream stream) throws IOException;
	/**
	 * 
	 * @param player
	 *            player id
	 * @param nextPlayer2
	 * @param output
	 * @return score of the player
	 */
	protected abstract void handlePlayerOutput(int frame, int round, int player, String[] output) throws WinException, LostException, InvalidInputException;
	protected abstract String[] getInitInputForPlayer(int player);
	protected abstract String[] getInputForPlayer(int round, int player);
	protected abstract String getHeadlineAtGameStartForConsole();
	protected abstract int getMinimumPlayerCount();
	protected abstract boolean showDeathTooltips();
	/**
	 * 
	 * @param round
	 * @return scores of all players
	 */
	protected abstract void updateGame(int round) throws GameOverException;
	protected abstract void prepare(int round);

	protected abstract boolean isPlayerDead(int player);
	protected abstract String getDeathReason(int player);

	protected abstract int getScore(int player);

	protected abstract String[] getGameSummary(int round);
	protected abstract String[] getPlayerActions(int player);
	protected abstract void setPlayerTimeout(int round, int player);
}
