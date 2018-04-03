import java.awt.Dimension;
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
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



class Referee extends MultiReferee {
	public Referee(InputStream is, PrintStream out, PrintStream err)
			throws IOException {
		super(is, out, err);
	}

	public Referee() throws IOException {
		super(System.in, System.out, System.err);
	}

	private static final double EPSILON=0.00001;
	public static final Dimension SIZE = new Dimension(800, 515);

	public static final double CHIP_MIN_RADIUS=5;
	public static final int MAX_MESSAGE_LENGTH=20;
	public static final double CHIP_MIN_START_AREA=10f*10f*Math.PI;
	public static final double CHIP_MAX_START_AREA=25f*25f*Math.PI;
	public static final double CHIP_MIN_START_RADIUS=Math.sqrt(CHIP_MIN_START_AREA / Math.PI);
	public static final double CHIP_MAX_START_RADIUS=Math.sqrt(CHIP_MAX_START_AREA / Math.PI);
	
	public static final double CHIP_EJECTION_SPEED=200f;
	public static final double CHIP_EJECTION_RATIO=1f/15f;
	
	public static final int MAX_ROUND_COUNT = 300;
	public static final int MINIMUM_PLAYER_COUNT = 2;
	
	private static final int MAX_DEFAULT_CHIP_COUNT = 50;

	private static final boolean NEW_PHYSICS_ENGINE=true;
	private static final int PHYSICS_STEPS = 500;
	private int step;

	private List<Chip> chips;
	private List<Chip> removedChips;
	private Player[] players;
	
	private int maxNeutralChipCount, chipCountPerPlayer;
	private long seed;
	private double playerInitialRadius;
	private int playerCount;
	private boolean symmetric;
	private Set<Runnable> playersActions;
	
	@Override
	protected void populateMessages(Properties p) {
		p.put("playerAction", "$%s pushes chip %d toward %.2f %.2f (x y)");
		p.put("playerDoNothing", "$%d doesn't move chip %d");
		p.put("playerDoNothing", "$%d doesn't move chip %d");
		p.put("playerDeadStatus", "$%d no longer plays in this game. (current score: %d)");
		p.put("playerAliveStatus", "$%d score: %d");
		p.put("playerActionFail", "$%s tried to move chip %d but gave a position too close to the chip");
		p.put("playerActionImpossible", "$%s couldn't push chip %d because it's too small");
	}

	@Override
	protected Properties getConfiguration() {
		Properties prop = new Properties();
		prop.put("max_neutral_count", String.valueOf(maxNeutralChipCount));
		prop.put("chips_count_per_player", String.valueOf(chipCountPerPlayer));
		prop.put("seed", String.valueOf(seed));
		prop.put("player_initial_radius", String.valueOf(playerInitialRadius));
		prop.put("symmetric", Boolean.valueOf(symmetric));
		return prop;
	}
	

	@Override
	protected void initReferee(int playerCount, Properties prop) throws InvalidFormatException {
		try {
			seed = Long.parseLong(prop.getProperty("seed", String.valueOf(new Random(System.currentTimeMillis()).nextLong())));
			Random random=new Random(seed);
			this.playerCount=playerCount;
			maxNeutralChipCount = Integer.parseInt(prop.getProperty("max_neutral_count",String.valueOf(playerCount+random.nextInt(MAX_DEFAULT_CHIP_COUNT-playerCount))));
			chipCountPerPlayer = Integer.parseInt(prop.getProperty("chips_count_per_player",String.valueOf(random.nextInt(5)+1)));
			double averageChipSize=(CHIP_MAX_START_RADIUS+CHIP_MIN_START_RADIUS)/2;
			playerInitialRadius = Double.parseDouble(prop.getProperty("player_initial_radius", String.valueOf(Math.round(random.nextDouble() * (CHIP_MAX_START_RADIUS-averageChipSize) + averageChipSize))));
			symmetric=Boolean.parseBoolean(prop.getProperty("symmetric", Boolean.toString(playerCount>=4)));
			playersActions=new HashSet<>();

			int counter=20;
			boolean failed;
			while((failed=!generateMap(random)) && counter-->0);
			if(failed) {
				throw new RuntimeException("Impossible to generate so much chips");
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private boolean generateMap(Random random) {
		if(symmetric) return generateSymmetricMap(random);
		else return generateRandomMap(random);
	}

	private boolean generateSymmetricMap(Random random) {
		chips = new ArrayList<>();
		removedChips = new ArrayList<>();
		players = new Player[playerCount];
		for (int i = 0; i < playerCount; ++i) {
			players[i] = new Player(i);
		}
		
		Vector mapCenter=new Vector(0,0);
		
		if(playerCount%2==1) {
			// nombre de joueurs impaire
			if(chipCountPerPlayer%2==1) {
				Chip chip3 = new Chip(players[playerCount-1], mapCenter, new Vector(0, 0), playerInitialRadius);
				chips.add(chip3);
				players[playerCount-1].addChip(chip3);
			}
			for (int j = 0; j < chipCountPerPlayer/2; j++) {
				Vector pos;
				int counter = 50;
				do {
					if (--counter < 0) {
						return false;
					}
					double x = -SIZE.getWidth()/2+playerInitialRadius + random.nextDouble() * ((double) (SIZE.getWidth()) - playerInitialRadius * 2);
					double y = -SIZE.getHeight()/2+playerInitialRadius + random.nextDouble() * ((double) (SIZE.getHeight()) - playerInitialRadius * 2);
					pos = new Vector(x, y);
				} while (
						(Math.abs(pos.getX()-mapCenter.getX()) <= playerInitialRadius*2) 
						|| (Math.abs(pos.getY()-mapCenter.getY()) <= playerInitialRadius*2) 
						|| collide(pos, playerInitialRadius));
				Chip chip1 = new Chip(players[playerCount-1], pos, new Vector(0, 0), playerInitialRadius);
				chips.add(chip1);
				players[playerCount-1].addChip(chip1);
				
				Chip chip2 = new Chip(players[playerCount-1], pos.symmetric(mapCenter), new Vector(0, 0), playerInitialRadius);
				chips.add(chip2);
				players[playerCount-1].addChip(chip2);
			}
		}
		
		for (int j = 0; j < chipCountPerPlayer; ++j) {
			Vector pos;
			int counter = 50;
			do {
				if (--counter < 0) {
					return false;
				}
				double x = -SIZE.getWidth()/2+playerInitialRadius + random.nextDouble() * ((double) (SIZE.getWidth()) - playerInitialRadius * 2);
				double y = -SIZE.getHeight()/2+playerInitialRadius + random.nextDouble() * ((double) (SIZE.getHeight()) - playerInitialRadius * 2);
				pos = new Vector(x, y);
			} while (
					(Math.abs(pos.getX()-mapCenter.getX()) <= playerInitialRadius*2) 
					|| (Math.abs(pos.getY()-mapCenter.getY()) <= playerInitialRadius*2) 
					|| collide(pos, playerInitialRadius));
			
			Chip chip1 = new Chip(players[0], pos, new Vector(0, 0), playerInitialRadius);
			chips.add(chip1);
			players[0].addChip(chip1);
			
			Chip chip2 = new Chip(players[1], pos.symmetric(mapCenter), new Vector(0, 0), playerInitialRadius);
			chips.add(chip2);
			players[1].addChip(chip2);
			
			if(playerCount==4) {
				Chip chip3 = new Chip(players[2], pos.vsymmetric(mapCenter.getY()), new Vector(0, 0), playerInitialRadius);
				chips.add(chip3);
				players[2].addChip(chip3);
				
				Chip chip4 = new Chip(players[3], pos.hsymmetric(mapCenter.getX()), new Vector(0, 0), playerInitialRadius);
				chips.add(chip4);
				players[3].addChip(chip4);
			}
			
		}
		
		int step=playerCount-playerCount%2;
		chipGeneration:for (int i = maxNeutralChipCount - 1; i >= 0; i-=step) {
			double radius = random.nextDouble() * (CHIP_MAX_START_RADIUS-CHIP_MIN_START_RADIUS) + CHIP_MIN_START_RADIUS;
			Vector pos;
			int counter = 50;
			do {
				if (--counter < 0) {
					break chipGeneration;
				}
				double x = -SIZE.getWidth()/2+radius + random.nextDouble()* ((double) (SIZE.getWidth()) - radius * 2);
				double y = -SIZE.getHeight()/2+radius + random.nextDouble()* ((double) (SIZE.getHeight()) - radius * 2);
				pos = new Vector(x, y);
			} while ((Math.abs(pos.getX()-mapCenter.getX()) <= radius*2) 
					|| (Math.abs(pos.getY()-mapCenter.getY()) <= radius*2) 
					|| collide(pos, radius));

			double speed = random.nextDouble() * 10;
			if (radius >= playerInitialRadius) speed = 0;
			double angle = random.nextDouble() * Math.PI;
			Vector speedVector=new Vector(Math.cos(angle) * speed, Math.sin(angle)* speed);
			Chip chip1 = new Chip(null, pos, speedVector, radius);
			chips.add(chip1);
			Chip chip2 = new Chip(null, pos.symmetric(mapCenter), speedVector.mult(-1), radius);
			chips.add(chip2);
			if(playerCount==4) {
				Chip chip3 = new Chip(null, pos.vsymmetric(mapCenter.getY()), speedVector.vsymmetric(), radius);
				chips.add(chip3);
				Chip chip4 = new Chip(null, pos.hsymmetric(mapCenter.getX()), speedVector.hsymmetric(), radius);
				chips.add(chip4);
			}
		}
		return true;
	}
	
	private boolean generateRandomMap(Random random) {
		chips = new ArrayList<>();
		removedChips = new ArrayList<>();
		players = new Player[playerCount];
		for (int i = 0; i < playerCount; ++i) {
			players[i] = new Player(i);
		}
		for (int i = 0; i < playerCount; ++i) {
			Player player=players[i];
			for (int j = 0; j < chipCountPerPlayer; ++j) {
				Vector pos;
				int counter = 50;
				do {
					if (--counter < 0) {
						return false;
					}
					double x = -SIZE.getWidth()/2+playerInitialRadius + random.nextDouble() * ((double) (SIZE.getWidth()) - playerInitialRadius * 2);
					double y = -SIZE.getHeight()/2+playerInitialRadius + random.nextDouble() * ((double) (SIZE.getHeight()) - playerInitialRadius * 2);
					pos = new Vector(x, y);
				} while (collide(pos, playerInitialRadius));
				Chip chip = new Chip(player, pos, new Vector(0, 0), playerInitialRadius);
				chips.add(chip);
				players[i].addChip(chip);
			}
		}
		
		chipGeneration:for (int i = maxNeutralChipCount - 1; i >= 0; --i) {
			double radius = random.nextDouble() * (CHIP_MAX_START_RADIUS-CHIP_MIN_START_RADIUS) + CHIP_MIN_START_RADIUS;
			Vector pos;
			int counter = 50;
			do {
				if (--counter < 0) {
					break chipGeneration;
				}
				double x = -SIZE.getWidth()/2+radius + random.nextDouble()* ((double) (SIZE.getWidth()) - radius * 2);
				double y = -SIZE.getHeight()/2+radius + random.nextDouble()* ((double) (SIZE.getHeight()) - radius * 2);
				pos = new Vector(x, y);
			} while (collide(pos, radius));

			double speed = random.nextDouble() * 10;
			if (radius >= playerInitialRadius) speed = 0;
			double angle = random.nextDouble() * Math.PI;
			
			Vector speedVector=new Vector(Math.cos(angle) * speed, Math.sin(angle)* speed);
			Chip chip = new Chip(null, pos, speedVector, radius);
			chips.add(chip);
		}
		return true;
	}

	private boolean collide(Vector pos, double radius) {
		if(pos.getX()-radius<-SIZE.getWidth()/2) return true;
		if(pos.getX()+radius>SIZE.getWidth()/2) return true;
		if(pos.getY()-radius<-SIZE.getHeight()/2) return true;
		if(pos.getY()+radius>SIZE.getHeight()/2) return true;
		for (Chip c : chips) {
			double factor=1.4;
			if(c.player!=null) factor=2;
			double minDistance=(radius + c.getRadius()) * factor;
			if (c.getPosition().distance(pos) <= minDistance)
				return true;
		}
		return false;
	}
	
	@Override
	protected void prepare(int round) {
		for (Player player : players) {
			player.update(round-1);
		}
		for (Chip chip : chips) {
			chip.reset();
		}
	}
	
	private abstract class Colision implements Comparable<Colision>{
		protected double time;
		
		public Colision(double time) {
			this.time = time;
		}

		public abstract void react();
		
		@Override
		public int compareTo(Colision col) {
			return Double.compare(time, col.time);
		}
		
		public String toString() {
			return this.getClass().getName()+" "+String.valueOf(time);
		}
	}
	
	private class ChipColision extends Colision {
		private Chip c1,c2;
		
		public ChipColision(double time, Chip a, Chip b) {
			super(time);
			this.c1 = a;
			this.c2 = b;
		}

		@Override
		public void react() {
			if(c1.getRadius()==c2.getRadius()) {
				// même taille = rebond
				Vector normal=c2.getPosition().sub(c1.getPosition()).normalize();
				Vector relativeVelocity=c1.getLinearVelocity().sub(c2.getLinearVelocity());
				Vector impulse = normal.mult(-2 * normal.dot(relativeVelocity)/(1 / c1.getMass() + 1 / c2.getMass()));
				if(impulse.dot(normal)<=0) {
					c1.applyForce(time, impulse);
					c2.applyForce(time, impulse.mult(-1));
				}
				
				double distance=c1.getPosition().distance(c2.getPosition());
				double diff=distance-c1.getRadius()+c2.getRadius();
				if(diff<0) {
					c1.setPosition(time, c1.getPosition().add(normal.mult(-(diff/2+EPSILON))));
					c2.setPosition(time, c2.getPosition().add(normal.mult((diff/2+EPSILON))));
				}
			} else {
				if(c1.getRadius()>c2.getRadius()) {
					c1.eat(time, c2);
					chips.remove(c2);
					removedChips.add(c2);
				} else {
					c2.eat(time, c1);
					chips.remove(c1);
					removedChips.add(c1);
				}
			}
		}
		public String toString() {
			return super.toString()+" "+c1.getId()+" "+c2.getId();
		}
	}
	public static enum Border {
		TOP, LEFT, RIGHT, BOTTOM;
	}
	private class BorderColision extends Colision {
		private Chip chip;
		private Border border;
		public BorderColision(double time, Chip chip, Border border) {
			super(time);
			this.chip=chip;
			this.border=border;
		}
		
		public void react() {
			Vector velocity=chip.getLinearVelocity();
			switch(border) {
			case TOP:
				chip.setLinearVelocity(time, new Vector(velocity.getX(), Math.abs(velocity.getY())));
				break;
			case BOTTOM:
				chip.setLinearVelocity(time, new Vector(velocity.getX(), -Math.abs(velocity.getY())));
				break;
			case LEFT:
				chip.setLinearVelocity(time, new Vector(Math.abs(velocity.getX()), velocity.getY()));
				break;
			case RIGHT:
				chip.setLinearVelocity(time, new Vector(-Math.abs(velocity.getX()), velocity.getY()));
				break;
			}
		}
		public String toString() {
			return super.toString()+" "+chip.getId()+" "+border;
		}
	}
	
	private double step2(boolean force, double beginTime) {
		double min=1-beginTime;

		List<Colision> colisions=new ArrayList<>();
		//physics loop with edges
		for(Chip c:chips) {
			c.checkBounds(-SIZE.getWidth()/2, -SIZE.getHeight()/2, SIZE.getWidth()/2, SIZE.getHeight()/2);
			double verticalColision1=((SIZE.getHeight()/2-c.getRadius())-c.getPosition().getY())/c.getLinearVelocity().getY();
			double verticalColision2=((-SIZE.getHeight()/2+c.getRadius())-c.getPosition().getY())/c.getLinearVelocity().getY();
			if(verticalColision1<=min && verticalColision1>0) {
				colisions.add(new BorderColision(beginTime+verticalColision1, c, Border.BOTTOM));
			}
			if(verticalColision2<=min && verticalColision2>0) {
				colisions.add(new BorderColision(beginTime+verticalColision2, c, Border.TOP));
			}
			
			double horizontalColision1=(SIZE.getWidth()/2-c.getRadius()-c.getPosition().getX())/c.getLinearVelocity().getX();
			double horizontalColision2=(-SIZE.getWidth()/2+c.getRadius()-c.getPosition().getX())/c.getLinearVelocity().getX();
			if(horizontalColision1<=min && horizontalColision1>0) {
				colisions.add(new BorderColision(beginTime+horizontalColision1, c, Border.RIGHT));
			}
			if(horizontalColision2<=min && horizontalColision2>0) {
				colisions.add(new BorderColision(beginTime+horizontalColision2, c, Border.LEFT));
			}
		}
		// physics loop between chips
		for(int i=chips.size()-1;i>=0;--i) {
			Chip chip1=chips.get(i);
			if(chip1.getRadius()>=0) {
				for(int j=i-1;j>=0;--j) {
					Chip chip2=chips.get(j);
					if(chip2.getRadius()>=0) {
						if(chip2.collide(chip1)) {
							if(!chip1.bounce.contains(chip2)) {
								colisions.add(new ChipColision(beginTime, chip1, chip2));
								chip1.bounce.add(chip2);
								chip2.bounce.add(chip1);
							}
						} else {
							double colision=chip1.contactPosition(chip2);
							if(colision<=min && colision>0) {
								colisions.add(new ChipColision(beginTime+colision, chip1, chip2));
							}
						}
					}
				}
			}
		}
		
		if(!colisions.isEmpty()) {
			Collections.sort(colisions);
			double time=-1;
			for(Colision c:colisions) {
				if(time<=-1) {
					time=c.time;
					double step=time-beginTime;
					for(Chip ch:chips) {
						ch.step(step);
					}
				}
				if(c.time<=time+EPSILON) {
					c.react();
				} else {
					break;
				}
			}
			return time-beginTime;
		}
		double step=1-beginTime;
		for(Chip ch:chips) {
			ch.step(step);
		}

		return step;
	}
	
	@Override
	protected void updateGame(int round) {
		for(Runnable r:playersActions) {
			r.run();
		}
		playersActions.clear();
		
		if(NEW_PHYSICS_ENGINE) {
			boolean force=false;
			double time=0;
			while(time<1) {
				double step=step2(force, time);
				force=step<=0;
				time+=step;
			}
		} else {
			double stepSize = 1f / (double) PHYSICS_STEPS;
			for (step = 0; step < PHYSICS_STEPS; ++step) {
				step(stepSize);
			}
		}
		
		for(Player player:players) {
			player.checkDeath(round);
		}
	}
	
	private void step(double stepSize) {
		double time=stepSize/2+this.step/(double)PHYSICS_STEPS;
		for(Chip c:chips) {
			c.step(stepSize);
		}
		for(Chip c:chips) {
			Vector position=c.getPosition();
			double radius=c.getRadius();
			Vector speed=c.getLinearVelocity();
			if(position.getX()-radius<-SIZE.width/2) {
				if(speed.getX()<0) speed=new Vector(Math.abs(speed.getX()),speed.getY());
				position=new Vector(Math.max(radius-SIZE.width/2, position.getX()), position.getY());
			}
			if(position.getY()-radius<-SIZE.height/2) {
				if(speed.getY()<0) speed=new Vector(speed.getX(),Math.abs(speed.getY()));
				position=new Vector(position.getX(), Math.max(radius-SIZE.height/2, position.getY()));
			}
			if(position.getX()+radius>SIZE.width/2) {
				if(speed.getX()>0) speed=new Vector(-Math.abs(speed.getX()),speed.getY());
				position=new Vector(Math.min(SIZE.width/2-radius, position.getX()), position.getY());
			}
			if(position.getY()+radius>SIZE.height/2) {
				if(speed.getY()>0) speed=new Vector(speed.getX(),-Math.abs(speed.getY()));
				position=new Vector(position.getX(), Math.min(SIZE.height/2-radius, position.getY()));
			}
			if(c.getLinearVelocity()!=speed) c.setLinearVelocity(time, speed);
			if(c.getPosition()!=position) c.setPosition(time, position);

		}
		
		physicsLoop:for(int i=chips.size()-1;i>=0;--i) {
			for(int j=i-1;j>=0;--j) {
				Chip c1=chips.get(i);
				Chip c2=chips.get(j);
				if(c1.collide(c2)) {
					if(c1.getRadius()==c2.getRadius()) {
						// même taille = rebond
						Vector normal=c2.getPosition().sub(c1.getPosition()).normalize();
						Vector relativeVelocity=c1.getLinearVelocity().sub(c2.getLinearVelocity());
						Vector impulse = normal.mult(-2 * normal.dot(relativeVelocity)/(1 / c1.getMass() + 1 / c2.getMass()));
//						if(normal.dot(c1.getLinearVelocity())+normal.dot(c2.getLinearVelocity())<=0) {
							c1.applyForce(time, impulse);
							c2.applyForce(time, impulse.mult(-1));
//						}
					} else {
						if(c1.getRadius()>c2.getRadius()) {
							c1.eat(time, c2);
							removedChips.add(chips.remove(j));
							--i;
						} else {
							c2.eat(time, c1);
							removedChips.add(chips.remove(i));
							continue physicsLoop;
						}
					}
				}
			}
		}
	}
	
	private void establishRanking() {
		List<Player> ranking=new ArrayList<>(Arrays.asList(players));
		Collections.sort(ranking);
		int score=-1;
		double lastArea=-1;
		double lastLostTime=-1;
		for(Player player:ranking) {
			if(player.isDead()) {
				if(player.lostTime>lastLostTime) {
					lastLostTime=player.lostTime;
					++score;
				}
				player.setScore(score);
			} else {
				double totalArea=player.getTotalArea();
				if(totalArea>lastArea) {
					++score;
					lastArea=totalArea;
				}
				player.setScore((int)(totalArea+0.5)+score);
			}
		}
	}
	
//	private static final Pattern PLAYER_INPUT_PATTERN=Pattern.compile("(?<action>(?:WAIT|(?<x>[-+]?[0-9]+(\\.[0-9]*)?) (?<y>[-+]?[0-9]+(\\.[0-9]*)?)|(?<angle>[-+]?[0-9]+(\\.[0-9]*)?)))( +(?<message>.+))?", Pattern.CASE_INSENSITIVE);
	private static final Pattern PLAYER_INPUT_PATTERN=Pattern.compile("(?<action>(?:WAIT|(?<x>\\S+) (?<y>\\S+)))( +(?<message>.+))?", Pattern.CASE_INSENSITIVE);
	
	@Override
	protected void handlePlayerOutput(int round, int playerId, String[] output) throws WinException, LostException, InvalidInputException {
		Player player = players[playerId];
		if (player.chips.size() != output.length) {
			player.setDead(round);
			throw new InvalidInputException(player.chips.size() + " lines", output.length + " lines");
		}
		int chipId = 0;
		for (String line : output) {
			final Chip chip = player.chips.get(chipId++);
			try {
				Matcher matcher=PLAYER_INPUT_PATTERN.matcher(line);
				if(!matcher.matches()) {
					player.setDead(round);
					throw new InvalidInputException("wait|x y", line);
				}

				chip.setMessage(matcher.group("message"));
				
				if(matcher.group("action").equalsIgnoreCase("wait")) {
					chip.setLastAction(translate("playerDoNothing", player.id, chip.id));
					continue;
				} else {
					if(chip.getRadius()>CHIP_MIN_RADIUS) {
						double x = Double.parseDouble(matcher.group("x"));
						double y = Double.parseDouble(matcher.group("y"));
						if(Double.isNaN(x) || Double.isInfinite(x)) {
							player.setDead(round);
							throw new InvalidInputException("x = An integer", "x = NaN");
						}
						if(Double.isNaN(y) || Double.isInfinite(y)) {
							player.setDead(round);
							throw new InvalidInputException("y = An integer", "y = NaN");
						}
						x-=SIZE.getWidth()/2;
						y-=SIZE.getHeight()/2;
						double distance=Math.sqrt((y-chip.getPosition().y)*(y-chip.getPosition().y)+ (x- chip.getPosition().x)*(x- chip.getPosition().x));
						if(distance<0.5) {
							chip.setLastAction(translate("playerActionFail", player.id, chip.id));
						} else {
							chip.setLastAction(translate("playerAction", player.id, chip.id, x+SIZE.getWidth()/2, y+SIZE.getHeight()/2));
							final double angle=Math.atan2(y - chip.getPosition().y, x- chip.getPosition().x);
							playersActions.add(new Runnable() {
								@Override
								public void run() {
									chips.add(chip.propulse(angle));
								}
							});
						}
					} else {
						chip.setLastAction(translate("playerActionImpossible", player.id, chip.id));
					}
				}

			} catch (NumberFormatException e) {
				player.setDead(round);
				throw new InvalidInputException("wait|x y", line);
			}
		}
	}

	@Override
	protected String[] getAdditionalFrameDataAtGameStartForView() {
		return new String[] {playerCount+" "+SIZE.width + " " + SIZE.height + " "+ MAX_ROUND_COUNT };
	}

	@Override
	protected String[] getFrameDataForView() {
		establishRanking();
		
		List<String> data = new ArrayList<>();
		data.add(String.valueOf(chips.size()+removedChips.size()));
		double totalArea=0;
		double[] playersAreas=new double[this.playerCount];
		for (Chip chip : chips) {
			double area=chip.getArea();
			if(chip.player!=null) playersAreas[chip.player.id]+=area;
			totalArea+=area;
			data.add(chip.toViewString());
		}
		for (Chip chip : removedChips) {
			data.add(chip.toViewString());
		}
		removedChips.clear();
		
		StringBuffer str=new StringBuffer();
		for(int i=0;i<playerCount;++i) {
			str.append(String.format(Locale.US, "%f ", playersAreas[i]));
		}
		str.append(String.format(Locale.US,"%f", totalArea));
		data.add(str.toString());
		
		str=new StringBuffer();
		for(int i=0;i<playerCount;++i) {
			str.append(String.format(Locale.US, "%d ", this.players[i].getScore()));
		}
		data.add(str.toString().trim());
		return data.toArray(new String[data.size()]);
	}

	@Override
	protected int getExpectedOutputLineCountForPlayer(int playerId) {
		return players[playerId].chips.size();
	}

	@Override
	protected String getGameName() {
		return "PokerChipRace";
	}

	@Override
	protected String[] getInitInputForPlayer(int player) {
		return new String[] { String.valueOf(player)/* +" "+SIZE.width + " " + SIZE.height + " "+ MAX_ROUND_COUNT*/};
	}

	@Override
	protected String[] getInputForPlayer(int playerId) {
		Player player = players[playerId];
		List<String> str = new ArrayList<>();
		str.add(String.valueOf(player.chips.size()));
		str.add(String.valueOf(chips.size()));
		for (Chip chip : player.chips) {
			str.add(chip.toPlayerString());
		}
		for (Chip chip : chips) {
			if(!player.chips.contains(chip))
				str.add(chip.toPlayerString());
		}
		return str.toArray(new String[str.size()]);
	}
	@Override
	protected int getMaxRoundCount() {
		return MAX_ROUND_COUNT;
	}

	@Override
	protected String getHeadlineAtGameStartForConsole() {
		return null;
	}

	@Override
	protected int getMinimumPlayerCount() {
		return MINIMUM_PLAYER_COUNT;
	}

	@Override
	protected boolean isPlayerDead(int player) {
		return players[player].isDead();
	}

	@Override
	protected String getDeathReason(int player) {
		return "$"+player+" got eaten";
	}

	@Override
	protected int getScore(int player) {
		return players[player].getScore();
	}
	
	@Override
	protected final void setPlayerTimeout(int round, int player) {
		players[player].setDead(round);
	}

	@Override
	protected String[] getGameSummary(int round) {
		List<String> data=new ArrayList<>();
		for (int i = 0; i < playerCount; ++i) {
			data.add(translate(players[i].isDead()?"playerDeadStatus":"playerAliveStatus", i, players[i].getScore()));
		}
		return data.toArray(new String[data.size()]);
	}

	@Override
	protected String[] getPlayerActions(int player) {
		List<String> actions=new ArrayList<String>(players[player].chips.size());
		for(Chip chip:players[player].chips) {
			String action=chip.getLastAction();
			if(action!=null) {
				actions.add(action);
			}
		}
		return actions.toArray(new String[actions.size()]);
	}

	
	public static void main(String... args) {
		try {
			new Referee(System.in, System.out, System.err);
		} catch (IOException e) {
			e.printStackTrace();
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

		public Vector checkBounds(double minx, double miny, double maxx, double maxy) {
			if(x<minx || x>maxx || y<miny || y>maxy) {
				return new Vector(Math.min(maxx, Math.max(minx, x)),Math.min(maxy, Math.max(miny, y)));
			}
			return this;
		}
	}

	static class Chip {
		static class ChipWayPoint {
			private double time;
			private Vector position;
			private double radius;
			private Chip eaten;
			private double rotation;

			public String toString() {
				return String.format(Locale.US, "%.2f %.2f %.2f %.2f %.2f %d", time, position.x+SIZE.getWidth()/2, position.y+SIZE.getHeight()/2, radius, rotation, eaten!=null?eaten.id:-1);
			}
			public ChipWayPoint(double time, double radius, Vector position, double rotation) {
				this(time, radius, position, rotation, null);
			}
			public ChipWayPoint(double time, double radius, Vector position, double rotation, Chip eaten) {
				this.time = time;
				this.position = position;
				this.radius = radius;
				this.eaten=eaten;
				this.rotation=rotation;
			}
		}
		
		private static int CHIP_GLOBAL_ID=0;
		private int id;
		public List<ChipWayPoint> wayPoints;
		private Player player;
		
		private Chip parent;
		private String message;
		
		private Vector position;
		private Vector velocity;
		
		private double radius;
		private double rotation;
		private Set<Chip> bounce;
		
		private String lastAction;
		
		public Chip(Player player, Vector position, Vector velocity, double radius) {
			this.id = CHIP_GLOBAL_ID++;
			this.bounce=new HashSet<>();
			this.velocity=velocity;
			this.position=position;
			this.radius=radius;
			this.player=player;
			this.wayPoints = new ArrayList<>();
			rotation=0;
		}

		public void checkBounds(double minx, double miny, double maxx, double maxy) {
			this.position=position.checkBounds(minx+radius+EPSILON, miny+radius+EPSILON, maxx-radius-EPSILON, maxy-radius-EPSILON);
		}

		public double getArea() {
			return getRadius() * getRadius() * Math.PI;
		}

		public void setArea(double time, double area) {
			setRadius(time, Math.sqrt(area / Math.PI));
		}
		
		private void setArea(double area) {
			setRadius(Math.sqrt(area / Math.PI));
		}

		public String toViewString() {
			StringBuffer buffer = new StringBuffer(String.valueOf(wayPoints.size()));
			for (ChipWayPoint point : wayPoints) {
				buffer.append(" ");
				buffer.append(point);
			}
			return String.format(Locale.US, "%d %d %.2f %.2f %.2f %.2f %s %s", id, player!=null?player.id:-1,
					getRadius(), getPosition().x+SIZE.getWidth()/2, getPosition().y+SIZE.getHeight()/2, getRotation(),
				buffer.toString(), message!=null?message:"").trim();
		}
		
		public String toPlayerString() {
			return String.format(Locale.US, "%d %d %f %f %f %f %f", id, player!=null?player.id:-1,
					getRadius(), getPosition().x+SIZE.getWidth()/2, getPosition().y+SIZE.getHeight()/2,
					getLinearVelocity().getX(), getLinearVelocity().getY());
		}

		public Vector getPosition() {
			return this.position;
		}
		

		public Vector getLinearVelocity() {
			return this.velocity;
		}

		public void setLinearVelocity(double time, Vector v) {
			this.velocity=v;
			addWayPoint(time);
		}

		public double getRadius() {
			return this.radius;
		}

		public synchronized void setRadius(double time, double radius) {
			setRadius(radius);
			addWayPoint(time);
		}
		
		private synchronized void setRadius(double radius) {
			this.radius=radius;
		}

		public void addWayPoint(double time) {
			parent = null;
			boolean force=false;
			if(!wayPoints.isEmpty()) {
				ChipWayPoint last=wayPoints.get(wayPoints.size()-1);
				if(last.time==time) {
					wayPoints.remove(wayPoints.size()-1);
				} else {
					force=last.radius!=getRadius();
					if(last.radius==getRadius() && last.position.distance(getPosition())<2f) {
						return;
					}
				}
			}
			if (wayPoints.size() < 8 || force) {
				wayPoints.add(new ChipWayPoint(time, getRadius(), getPosition(), getRotation()));
			}
		}

		public void reset() {
			wayPoints.clear();
			parent = null;
			this.lastAction=null;
			this.message=null;
		}
		
		public Chip getParent() {
			return parent;
		}

		public void setParent(Chip parent) {
			this.parent = parent;
		}

		public int getId() {
			return this.id;
		}

		public double getMass() {
			return getArea();
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			if(message!=null && message.length()>MAX_MESSAGE_LENGTH) message = message.substring(0, MAX_MESSAGE_LENGTH);
			this.message = message;
		}

		public void step(double step) {
			if(step>0) {
				bounce.clear();
				this.position=this.position.add(this.velocity.mult(step));
			}
		}
		
		public boolean collide(Chip c2) {
			return this.position.distance(c2.position)<this.radius+c2.radius-EPSILON
					&& c2.parent!=this && parent!=c2;
		}
		

		public void applyForce(double time, Vector force) {
			applyForce(force);
			addWayPoint(time);
		}
		
		private void applyForce(Vector force) {
			this.velocity=this.velocity.add(force.mult(1d/getMass()));

		}

		public void setPosition(double time, Vector position) {
			setPosition(position);
			addWayPoint(time);
		}
		
		private void setPosition(Vector position) {
			this.position=position;
		}

		public void eat(double time, Chip c2) {
			addWayPoint(time);
			setRotation(new Vector(position, c2.position).angle());
			this.position=this.position.mult(getMass()).add(c2.getPosition().mult(c2.getMass())).mult(1d/(c2.getMass()+getMass()));
			this.velocity=this.velocity.mult(getMass()).add(c2.velocity.mult(c2.getMass())).mult(1d/(c2.getMass()+getMass()));
			setArea(getArea()+c2.getArea());
			wayPoints.add(new ChipWayPoint(time, getRadius(), getPosition(), getRotation(), c2));
			c2.setRadius(time, -1);
		}

		public void removePlayer() {
			this.player=null;
		}

		public double getRotation() {
			return rotation;
		}

		public void setRotation(double rotation) {
			this.rotation = rotation;
		}

		public Chip propulse(double angle) {
			setRotation(angle);
			
			double area = getArea() * CHIP_EJECTION_RATIO;
			setArea(getArea() - area);
			
			Chip trash = new Chip(null, new Vector(0, 0), getLinearVelocity(), 0);
			trash.setArea(area);
			double radiusSum = (getRadius() - trash.getRadius());
			trash.setPosition(new Vector(getPosition().x- Math.cos(angle) * radiusSum, getPosition().y - Math.sin(angle) * radiusSum));
			
			Vector vector = new Vector(Math.cos(angle), Math.sin(angle)).mult(CHIP_EJECTION_SPEED*trash.getMass());
			applyForce(vector);
			Vector inverse = vector.mult(-1);
			trash.applyForce(inverse);
			trash.wayPoints.add(new ChipWayPoint(0, trash.getRadius(), trash.getPosition(), trash.getRotation(), this));
			trash.setParent(this);
			return trash;
		}

		public double getDeathTime() {
			for(ChipWayPoint point:wayPoints) {
				if(point.radius<0) {
					return point.time;
				}
			}
			if(getRadius()<0) return 0;
			throw new RuntimeException("Chip not dead");
		}

		public String getLastAction() {
			return lastAction;
		}

		public void setLastAction(String lastAction) {
			this.lastAction = lastAction;
		}
		
		public double contactPosition(Chip chip) {
			if(chip.parent==this || parent==chip) return Double.POSITIVE_INFINITY;
		    Vector dv=this.velocity.sub(chip.velocity);
		    Vector d=this.position.sub(chip.position);
		    double a=dv.lengthSquared();
		    double b=d.x*dv.x+d.y*dv.y;
		    double dmin=this.radius+chip.radius;
		    double c=d.lengthSquared()-dmin*dmin;
		    double delta=b*b-a*c;
		    if(delta <=0 || a==0) {
		        return Double.POSITIVE_INFINITY;
		    }
		    double rd=Math.sqrt(delta);
		    return Math.min((-b+rd)/a, (-b-rd)/a);
		}
	}

	static class Player implements Comparable<Player>{
		private int id;
		private int score;
		private double lostTime;
		private List<Chip> chips;

		public Player(int id) {
			this.id = id;
			this.chips = new ArrayList<>();
			this.lostTime=-1;
		}



		public Chip[] getChips() {
			return chips.toArray(new Chip[chips.size()]);
		}
		
		public void addChip(Chip chip) {
			this.chips.add(chip);
		}
		
		public boolean isDead() {
			for(Chip c:chips) {
				if(c.getRadius()>=0) return false;
			}
			return true;
		}

		@Override
		public int compareTo(Player p2) {
			double diff=getTotalArea()-p2.getTotalArea();
			if(diff==0 && chips.isEmpty() && p2.chips.isEmpty()) {
				diff=lostTime-p2.lostTime;
			}
			return diff>0?1:(diff<0?-1:0);
		}
		
		public double getTotalArea() {
			double area=0;
			for(Chip chip:chips) {
				area+=chip.getArea();
			}
			return area;
		}
		
		public int getScore() {
			return score;
		}

		public void setScore(int score) {
			this.score = score;
		}
		
		public void update(int round) {
			if(!chips.isEmpty()) {
				Iterator<Chip> chipsIt = this.chips.iterator();
				while (chipsIt.hasNext()) {
					Chip chip=chipsIt.next();
					if (chip.getRadius() < 0) {
						chipsIt.remove();
					}
				}
			}
		}
		
		public void checkDeath(int round) {
			if(isDead() && this.lostTime<0) {
				double maxTime=0;
				for(Chip chip:this.chips) {
					if (chip.getRadius() < 0) {
						maxTime=Math.max(maxTime, chip.getDeathTime());
					}
				}
				this.lostTime=round+maxTime;
			}
		}

		public void setDead(int round) {
			for(Chip chip:chips) {
				chip.removePlayer();
			}
			chips.clear();
			this.lostTime=round;
		}
	}

}

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

	public SoloReferee(InputStream is, PrintStream out, PrintStream err)
			throws IOException {
		super(is, out, err);
	}

	protected abstract void handlePlayerOutput(int round, String[] playerOutput)
			throws WinException, LostException, InvalidInputException;

	@Override
	protected final void handlePlayerOutput(int round, int player, String[] playerOutput)
			throws WinException, LostException, InvalidInputException {
		if (player != 0)
			throw new RuntimeException(
					"SoloReferee could only handle one-player games");
		try {
			handlePlayerOutput(round, playerOutput);
		} catch (LostException | InvalidInputException e) {
			score = 0;
		} catch (WinException e) {
			score = 1;
		}
	}

	protected abstract String[] getInitInputForPlayer();
	protected abstract String[] getInputForPlayer();
	protected abstract String[] getTextForConsole();
	
	
	@Override
	protected final String[] getInitInputForPlayer(int player) {
		if (player != 0)
			throw new RuntimeException(
					"SoloReferee could only handle one-player games");
		return getInitInputForPlayer();

	}


	@Override
	protected final String[] getInputForPlayer(int player) {
		if (player != 0)
			throw new RuntimeException(
					"SoloReferee could only handle one-player games");
		return getInputForPlayer();
	}

	protected abstract int getExpectedOutputLineCountForPlayer();

	@Override
	protected int getExpectedOutputLineCountForPlayer(int player) {
		if (player != 0)
			throw new RuntimeException(
					"SoloReferee could only handle one-player games");
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
		return getTextForConsole();
	}
	
	protected final String[] getPlayerActions(int player) {
		return new String[0];
	}
	
	protected void prepare(int round) {
		// don't care
	}
	protected final void setPlayerTimeout(int player) {
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
	final class InvalidFormatException extends Exception{
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
	private int playerCount, alivePlayerCount;
	private int currentPlayer, nextPlayer;
	private PlayerStatus lastPlayer,playerStatus;
	private int frame, round;
	private PlayerStatus[] players;
	private String[] initLines;
	private boolean newRound;
	private String reasonCode, reason;
	
	private InputStream is;
	private PrintStream out;
	private PrintStream err;

	public AbstractReferee(InputStream is, PrintStream out, PrintStream err) throws IOException {
		tooltips=new HashSet<>();
		this.is = is;
		this.out = out;
		this.err = err;
		start();
	}

	@SuppressWarnings("resource")
	public void start() throws IOException {
		this.messages.put("InvalidInput","invalid input. Expected '%s' but found '%s'");
		this.messages.put("playerTimeout","Timeout: the program did not provide %d input lines in due time... $%d will no longer be active in this game.");
		this.messages.put("maxRoundsCountReached", "Max rounds count reached");
		this.messages.put("notEnoughPlayers", "Not enough players (expected > %d, found %d)");
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
					round =-1;
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
					} catch (RuntimeException|InvalidFormatException e) {
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

					if(playerCount<getMinimumPlayerCount()) {
						reasonCode = "notEnoughPlayers";
						reason = translate(reasonCode, getMinimumPlayerCount(), playerCount);
						throw new GameOverException();
					}
					break;
				case GET_GAME_INFO:
					lastPlayer=playerStatus;
					playerStatus = nextPlayer();
					if (this.round >= getMaxRoundCount()) {
						reasonCode = "maxRoundsCountReached";
						reason = translate(reasonCode);
						throw new GameOverException();
					}
					dumpView();
					dumpInfos();
					if(newRound) {
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
						handlePlayerOutput(round, nextPlayer, output);
					} catch (LostException|InvalidInputException e) {
						playerStatus.score = getScore(nextPlayer);
						playerStatus.lost = true;
						playerStatus.info = e.getReason();
						playerStatus.reasonCode = e.getReasonCode();
						if (--alivePlayerCount < getMinimumPlayerCount()) {
							lastPlayer=playerStatus;
							throw new GameOverException();
						}
					} catch (WinException e) {
						playerStatus.score = getScore(nextPlayer);
						playerStatus.info = e.getReason();
						playerStatus.reasonCode = e.getReasonCode();
						lastPlayer=playerStatus;
						throw new GameOverException();
					}
					break;
				case SET_PLAYER_TIMEOUT:
					++frame;
					int count=getExpectedOutputLineCountForPlayer(nextPlayer);
					setPlayerTimeout(round, nextPlayer);
					playerStatus.lost=true;
					playerStatus.info = translate("playerTimeout", count, nextPlayer);
					addDeathToolTip(nextPlayer, playerStatus.info);
					if (--alivePlayerCount < getMinimumPlayerCount()) {
						lastPlayer=playerStatus;
						throw new GameOverException();
					}
					break;
				}
			}
		} catch (GameOverException e) {
			newRound = true;
			dumpView();
			dumpInfos();
			prepare(round);
			updateScores();
		} catch(GameErrorException e) {
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
		if (reasonCode == null && playerStatus!=null)
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
				data.add(String.format("INTERMEDIATE_FRAME %d %s", this.frame,
						reasonCode));
			} else {
				data.add(String.format("INTERMEDIATE_FRAME %d", frame));
			}
		}
		try{
		data.addAll(getFrameDataForView());
		} catch(Throwable t) {
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
		data.addAll(getInputForPlayer(nextPlayer));
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
				addDeathToolTip(i, players[i].info);
			}
			players[i].score = getScore(i);
		}
	}
	
	
	private void addDeathToolTip(int player, String message) {
		tooltips.add(new Tooltip(player, message));
	}

	/**
	 * 
	 * Add message (key = reasonCode, value = reason)
	 * @param p
	 */
	protected abstract void populateMessages(Properties p);

	protected abstract void handleInitInputForReferee(int playerCount, String[] init) throws InvalidFormatException;

	protected abstract String[] getAdditionalFrameDataAtGameStartForView();

	protected abstract String[] getFrameDataForView();

	protected abstract int getExpectedOutputLineCountForPlayer(int player);

	protected abstract String getGameName();

	protected abstract void appendDataToEnd(PrintStream stream)
			throws IOException;
	/**
	 * 
	 * @param player
	 *            player id
	 * @param output
	 * @return score of the player
	 */
	protected abstract void handlePlayerOutput(int round, int player, String[] output) throws WinException, LostException, InvalidInputException;
	protected abstract String[] getInitInputForPlayer(int player);
	protected abstract String[] getInputForPlayer(int player);
	protected abstract String getHeadlineAtGameStartForConsole();
	protected abstract int getMinimumPlayerCount();

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
	protected abstract void setPlayerTimeout(int round, int player);
}