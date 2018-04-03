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

	private static final int MAX_ROUNDS = 200;
	private static int START_PLATINUM = 200;
	private static int UNIT_COST = 20;
	private static final int NEUTRAL = -1;
	private static final int NOBODY = -2;

	public static final int COMBAT_STRIKES = 3;
	public static final double MAX_PLATINUM = 6;
	public static final double RICH_AMERICA_PERCENTAGE = .55;
	private static final int MAX_MESSAGE_LENGTH = 30;

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
			for (int i = 0; i < 4; ++i) {
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

		List<List<Coord>> islands;

		private List<Coord> aroundEven;
		private List<Coord> aroundOdd;
		private List<Tile> graph;
		private List<Coord> america;

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

		public void generate(int playerCount, String mapData, int totalPlatinum, Distribution distribution, boolean noEmptyIslands, boolean richAmericas) {
			int platinum = totalPlatinum;
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
			if (noEmptyIslands) {
				// List<Tile> islandTiles = new LinkedList<Tile>();
				for (List<Coord> island : islands) {
					Tile t = map.get(island.get(random.nextInt(island.size())));
					double percent = random.nextDouble();

					int count = 1 + (int) (distribution.value(percent) * (MAX_PLATINUM - 1) + 0.5d);

					count = Math.min(platinum, count);
					count = Math.min((int) MAX_PLATINUM, count);
					count = Math.max(1, count);
					platinum -= count;
					t.platinum = count;

				}
			}

			if (richAmericas && america != null) {
				List<Tile> americanTiles = new LinkedList<>();
				int americanPlatinum = (int) (platinum * RICH_AMERICA_PERCENTAGE);
				platinum -= americanPlatinum;
				for (Coord c : america) {
					Tile t = map.get(c);
					allTiles.remove(t);
					americanTiles.add(t);
				}
				distributePlatinum(americanPlatinum, americanTiles, distribution);
			}
			distributePlatinum(platinum, allTiles, distribution);
			if (finale) {
				shiftPlates();
				islands.clear();
				for (Tile t : graph) {
					t.island = -1;
				}
				initIslands(true);
			}
		}

		private void shiftPlates() {
			int[] suezAfrica = {71,83,95};
			int[] suezAsia = {82,94,103};
			for (int i : suezAfrica) {
				for (int j : suezAsia) {
					Tile t1 = graph.get(i);
					Tile t2 = graph.get(j);
					if (t1.neighbours.contains(t2.id)) {
						t1.neighbours.remove(t2.id);
						t2.neighbours.remove(t1.id);
					}
				}	
			}
			
			int[] africa = {50,51,54,55,56,60,61,62,63,64,65,66,71,72,73,74,75,76,77,83,84,85,86,87,88,95,96};
			HashMap<Coord, Tile> newMap = new HashMap<>();
			for (int id : africa) {
				Tile t = graph.get(id);
				map.remove(new Coord(t.x, t.y));
				t.y += 1;
				newMap.put(new Coord(t.x,t.y), t);
			}
			for (Coord c : newMap.keySet()) {
				map.put(c, newMap.get(c));
			}
			
			Tile panama = graph.get(18);
			map.remove(new Coord(panama.x, panama.y));
			panama.x = 9;
			panama.y = 8;
			map.put(new Coord(panama.x, panama.y), panama);
			for (Integer i : panama.neighbours) {
				graph.get(i).neighbours.remove(panama.id);
			}
			panama.neighbours.clear();
			panama.neighbours.add(44);
			panama.neighbours.add(51);
			graph.get(44).neighbours.add(18);
			graph.get(51).neighbours.add(18);			
			
		}
		
		private void distributePlatinum(int total, List<Tile> tiles, Distribution method) {
			List<Tile> discardedTiles = new LinkedList<Tile>();
			int platinum = total;
			while (platinum > 0 && !tiles.isEmpty()) {
				Tile t = tiles.get(random.nextInt(tiles.size()));
				double percent = random.nextDouble();

				int count = 1 + (int) (method.value(percent) * (MAX_PLATINUM - 1) + 0.5d);
				count = Math.min(platinum, count);
				count = Math.min((int) MAX_PLATINUM, count);
				count = Math.max(1, count);

				platinum -= count;
				t.platinum = count;
				tiles.remove(t);
				discardedTiles.add(t);
			}

			while (platinum > 0 && !discardedTiles.isEmpty()) {
				Tile t = discardedTiles.get(random.nextInt(discardedTiles.size()));
				if (t.platinum < 6) {
					t.platinum++;
					platinum--;
				} else {
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
			if (islands.size() > 1)
				america = islands.get(1);
			else if (islands.size() > 0)
				america = islands.get(0);

		}
		public List<String> toStringInitAsMap() {
			ArrayList<String> data = new ArrayList<String>();
			Iterator<Coord> it = map.keySet().iterator();
			data.add(width + " " + height + " " + map.size());
			while (it.hasNext()) {
				Coord c = it.next();
				Tile t = map.get(c);
				data.add(c + " " + t.toStringInit() + " " + t.id);
			}
			return data;
		}

		public List<String> toStringInitAsGraph() {
			if (edges == null) {
				getLinkCount();
			}

			ArrayList<String> data = new ArrayList<String>();
			Iterator<Tile> it = graph.iterator();
			while (it.hasNext()) {
				Tile t = it.next();
				data.add(t.id + " " + t.platinum);
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

		public List<Battle> update() {
			List<Battle> battleInfos = new LinkedList<>();
			for (int i = 0; i < graph.size(); i++) {
				Tile t = graph.get(i);
				Battle battleInfo = t.update();
				if (battleInfo.hasActivity()) {
					if (battleInfo.hasCapturer()) {
						incZones(battleInfo.capturer);
						decZones(battleInfo.oldOwner);
						income[battleInfo.capturer] += t.platinum;
						if (battleInfo.oldOwner >= 0)
							income[battleInfo.oldOwner] -= t.platinum;

					}
					if (battleInfo.hasFight()) {
						for (Integer ind : battleInfo.contenders) {
							units[ind] -= battleInfo.deaths[ind];
						}
					}

					battleInfo.setHex(new Coord(t.x, t.y), i);
					battleInfos.add(battleInfo);
				}
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

		public List<String> toStringForPlayer(int playerCount, int platinum) {
			List<String> data = new ArrayList<String>();

			for (Tile t : graph) {
				data.add(t.id + " " + t.ownerId + " " + t.allUnits());
			}

			return data;
		}

		public int getIncomeFor(int playerId) {
			return income[playerId];
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

	class Placement implements Runnable {
		private HexGrid map;
		int amount, x, y, id;
		private Player player;

		public Placement(HexGrid map, Player player, int amount, Tile tile) {
			this.map = map;
			this.player = player;
			this.amount = amount;
			this.x = tile.x;
			this.y = tile.y;
			this.id = tile.id;
		}

		@Override
		public String toString() {
			return String.format("%d %d %d %d", player.id, amount, x, y);
		}

		@Override
		public void run() {
			map.place(player.id, amount, x, y);
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

	class PlayerPlacementSet implements Iterable<Placement> {
		private LinkedList<Placement> placements = new LinkedList<>();

		public void add(Placement placeAction) {
			for (Placement placement : placements) {
				if (placement.x == placeAction.x && placement.y == placeAction.y && placement.player.id == placeAction.player.id) {
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
	private Boolean noEmptyIslands, richAmericas;
	private static Boolean finale;

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

		noEmptyIslands = Boolean.valueOf(prop.getProperty("noEmptyIslands", String.valueOf(random.nextBoolean())));
		richAmericas = Boolean.valueOf(prop.getProperty("richAmericas", String.valueOf(random.nextBoolean())));

		String algoName = prop.getProperty("distribution", Distribution.values()[random.nextInt(Distribution.values().length)].name());

		try {
			distribution = Distribution.valueOf(algoName);
		} catch (IllegalArgumentException e) {
			distribution = Distribution.values()[random.nextInt(Distribution.values().length)];
		}
		finale = Boolean.valueOf(prop.getProperty("finale", "true"));
		totalPlatinum = Integer.valueOf(prop.getProperty("platinum", "120"));
		START_PLATINUM = Integer.valueOf(prop.getProperty("startPlatinum", String.valueOf(START_PLATINUM)));
		UNIT_COST = Integer.valueOf(prop.getProperty("unitCost", String.valueOf(UNIT_COST)));

		mapData = prop.getProperty("map", "0 2 1 1 2 1 2 2 2 3 2 4 2 5 3 1 3 2 3 3 3 4 3 5 3 6 4 1 4 2 4 3 4 4 4 5 4 7 5 0 5 1 5 2 5 3 5 4 5 7 5 8 5 9 6 1 6 2 6 3 6 8 6 9 6 10 6 11 6 12 6 13 6 14 7 0 7 2 7 8 7 9 7 10 7 11 8 0 8 9 8 10 9 0 9 1 10 0 10 1 10 6 10 7 11 2 11 3 11 5 11 6 11 7 11 14 12 1 12 3 12 5 12 6 12 7 12 8 12 9 12 10 12 11 12 14 13 1 13 2 13 3 13 5 13 6 13 7 13 8 13 9 13 10 13 11 13 14 14 2 14 3 14 4 14 5 14 6 14 7 14 8 14 9 14 10 14 11 14 14 15 1 15 2 15 3 15 4 15 5 15 6 15 7 15 14 16 1 16 2 16 3 16 4 16 5 16 6 16 14 17 0 17 1 17 2 17 3 17 4 17 5 17 6 17 7 17 14 18 1 18 2 18 3 18 4 18 5 18 6 19 1 19 2 19 3 19 4 19 5 19 6 19 7 19 8 20 1 20 2 20 3 20 4 20 5 20 6 20 9 20 11 20 12 21 1 21 9 21 10 21 11 22 1 22 2 22 5 22 9 22 11 22 12 22 13 23 1 23 3 23 4 23 9 23 13 24 13");

		map = new HexGrid();
		map.generate(playerCount, mapData, totalPlatinum, distribution, noEmptyIslands, richAmericas);

		playerMoves = new PlayerMoveSet();
		playerPlacements = new PlayerPlacementSet();
		battles = new LinkedList<>();
		playerMovesForView = new LinkedList<>();
		playerPlacementsForView = new LinkedList<>();

		players = new ArrayList<Player>(playerCount);
		for (int i = 0; i < playerCount; i++) {
			players.add(new Player(i));
		}
	}
	@Override
	protected Properties getConfiguration() {
		Properties prop = new Properties();
		prop.put("seed", String.valueOf(seed));
		prop.put("distribution", distribution);
		prop.put("richAmericas", richAmericas);
		prop.put("noEmptyIslands", noEmptyIslands);
		prop.put("finale", finale);
		// TODO: remove below
		prop.put("platinum", String.valueOf(totalPlatinum));
		prop.put("startPlatinum", START_PLATINUM);
		prop.put("unitCost", UNIT_COST);
		prop.put("map", mapData);

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

		data.addAll(map.toStringInitAsMap());
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
			data.add(Integer.toString(0));
			data.add(Integer.toString(0));
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
			playerPlacementsForView.clear();
			playerMovesForView.clear();
		}
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
		if (map.isPlayerDead(player)) {
			player.eliminate(turn);
			// Do not return
		}

		output[0] = output[0].trim();
		output[1] = output[1].trim();
		String moveMessage = parsePlayerMessage(output, 0);
		String placeMessage = null;
		// String
		if (moveMessage == null)
			placeMessage = parsePlayerMessage(output, 1);
		String[] moveData = null;
		if (!output[0].equalsIgnoreCase("wait") && !output[0].isEmpty()) {
			moveData = output[0].split("\\s+");
		}

		String[] placeData = null;
		if (!output[1].equalsIgnoreCase("wait") && !output[1].isEmpty()) {
			placeData = output[1].split("\\s+");
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

		if (placeData != null) {
			Tile msgFrom = null;
			int placel = placeData.length;

			if (placeData.length < 2) {
				player.setDead(turn);
				throw new InvalidInputException("x y | \"WAIT\"", output[0]);
			}
			if (placeData.length % 2 != 0) {
				player.setDead(turn);
				throw new InvalidInputException("multiple of 2 integers | \"WAIT\"", "trailing " + placeData[placel - 1]);
			}

			for (int place = 0; place < placel;) {
				String n1 = placeData[place++];
				String n2 = placeData[place++];

				try {
					int n = Integer.valueOf(n1);
					int id = Integer.valueOf(n2);

					try {
						// n > 0 ?
						if (n <= 0)
							throw new InvalidMoveOrPlacementException("Expected n > 0 but got n = %d", n);
						// id in map ?
						Tile t = map.get(id);
						if (t == null)
							throw new InvalidMoveOrPlacementException("Hex %d is not part of the map", id);
						// id is player's ?
						if (t.ownerId != playerId && t.ownerId != -1)
							throw new InvalidMoveOrPlacementException("Hex %d is owned by another player", id);

						// no battle on x y ?
						if (t.ownerId == -1 && t.isContested())
							throw new InvalidMoveOrPlacementException("Hex %d is a battlefield", id);

						// How many can player afford ?
						boolean buyNextPod = true;
						int expenses = 0;
						int bought = 0;
						while (buyNextPod) {
							if (player.platinum - player.expenses - expenses >= UNIT_COST) {
								bought++;
								expenses += UNIT_COST;
								if (bought >= n)
									buyNextPod = false;
							} else {
								buyNextPod = false;
							}
						}

						player.expenses += expenses;

						// All good
						if (bought > 0) {
							playerPlacements.add(new Placement(map, player, bought, t));
							framePlacements.add(new Placement(map, player, bought, t));
							msgFrom = t;
						} else {
							throw new InvalidMoveOrPlacementException("Buyer can't afford reinforcements");
						}

					} catch (InvalidMoveOrPlacementException e) {
						e.action = n1 + " " + n2;
						// printError(e);
						player.addMistake(e.getMessage(), e.toString());
						continue;
					}
				} catch (NumberFormatException nfe) {
					player.setDead(turn);
					throw new InvalidInputException("x y | \"WAIT\"", n1 + " " + n2);
				}

			}
			if (placeMessage != null && msgFrom != null) {
				player.setSpeech(placeMessage, msgFrom);
			}
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
		data.addAll(map.toStringInitAsGraph());
		return data.toArray(new String[data.size()]);
	}
	@Override
	protected String[] getInputForPlayer(int round, int player) {
		List<String> data = new ArrayList<>();
		data.add(Integer.toString(players.get(player).platinum));
		data.addAll(map.toStringForPlayer(players.size(), players.get(player).platinum));
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

		List<Battle> battleInfos = map.update();

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
		return getZones(player) + players.get(player).advantage;
	}
	protected int getZones(int player) {
		return map.getZones(player);
	}
	@Override
	protected String[] getGameSummary(int round) {
		List<String> data = new ArrayList<>();

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