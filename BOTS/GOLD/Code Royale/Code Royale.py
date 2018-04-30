# 64 - silver


import sys
import math
from random import *

obstacles = []

pos_moves = []

moveScore = []

# classes
class obstacle:
    def __init__(self, idy, x, y, radius):
      self.idy = idy
      self.x = x
      self.y = y
      self.radius = radius
      self.gold = -1
      self.mineSize = -1
      self.structureType = -1
      self.owner = -1
      self.atr = -1
      self.spec = -1
      
      
class unit:
    def __init__(self, x, y, owner, unitType, health):
        self.x = x
        self.y = y
        self.owner = owner
        self.unitType = unitType
        self.health = health
        if unitType == -1:
            self.speed = 60
            self.damage = 0
            self.attackRange = 0
            self.radius = 30
        if unitType == 0:
            self.speed = 100
            self.damage = 1
            self.attackRange = 0
            self.radius = 20
        if unitType == 1:
            self.speed = 75
            self.damage = 2
            self.attackRange = 200
            self.radius = 25
        if unitType == 2:
            self.speed = 50
            self.damage = 80
            self.attackRange = 0
            self.radius = 40
            

# initial input
num_obstacles = int(input())
for i in range(num_obstacles):
    idy, x, y, radius = [int(j) for j in input().split()]
    ob = obstacle(idy, x, y, radius)
    obstacles.append(ob)

# variables
lolzId = 0

units = []

eUnits = []


myQueen = unit(-1,-1,1,-1,200)

enemyQueen = unit(-1,-1,2,-1,200)


aux_unit = unit(-1,-1,2,-1,200)


dangerDist = 300 #max(1000 - myQueen.health*20, 0) + 500

# enemy buildings

et = 0

# closest obstacles
current = -1

second = -1


turn = 0


# functions
def distance(x1, y1, x2, y2):
    return math.sqrt( (x1 - x2 ) * ( x1 - x2 ) + ( y1 - y2 ) * ( y1 - y2 ) )
    

def isSafeObs(idy):
    for t in obstacles:
        if t.structureType == 1 and t.owner == 1 and t.spec > distance(t.x, t.y, obstacles[idy].x, obstacles[idy].y) + 5:
            return False
    return True
    
    
def isSafe(x,y):
    for t in obstacles:
        if t.structureType == 1 and t.owner == 1 and t.spec > distance(t.x, t.y, x, y):
            return
    
    score = 1
    pos_moves.append("MOVE " + str(x) + " " + str(y) )
    moveScore.append(score)
    
    return True
    
    
def isSafeSpot(x,y):
    for t in obstacles:
        if t.structureType == 1 and t.owner == 1 and t.spec > distance(t.x, t.y, x, y):
            return
    
    return True
    
    
def closestEmpty(x,y):
    dist = 9999999
    idy = -1
    
    for obs in obstacles:
        dist2 = distance(x, y, obs.x, obs.y)
        if dist > dist2 and obs.owner != 0 and isSafe(obs.idy) and distance(obs.x, obs.y, myQueen.x, myQueen.y) < 700:
            dist = dist2
            idy = obs.idy
            
    return idy
    

def emptyMine(x,y):
    dist = 9999999
    idy = -1
    
    for obs in obstacles:
        dist2 = distance(x, y, obs.x, obs.y)
        if dist > dist2 and obs.owner != 0 and obs.gold > 0 and isSafe(obs.idy):
            dist = dist2
            idy = obs.idy
            
    return idy # closest empty end
    
    
def closestTower(x,y):
    dist = 9999999
    idy = -1
    
    for obs in obstacles:
        dist2 = distance(x, y, obs.x, obs.y)
        if dist > dist2 and obs.structureType == 1 and obs.owner == 0:
            dist = dist2
            idy = obs.idy
            
    return idy
    
    
def closestKnight(x,y):
    dist = dangerDist
    hp = -1
    
    for u in eUnits:
        dist2 = distance(x, y, u.x, u.y)
        if dist > dist2 and u.unitType == 0 and u.owner == 1:
            dist = dist2
            hp = u.health
            aux_unit.x = u.x
            aux_unit.y = u.y
            
    return hp # closest empty end
    
    
def growTower(x,y,r):
    dist = r
    idy = -1
    
    for obs in obstacles:
        dist2 = distance(x, y, obs.x, obs.y)
        if obs.owner == 0 and obs.structureType == 1 and dist > dist2 and obs.atr < 400 and isSafe(obs.idy):
            dist = dist2
            idy = obs.idy
            
    return idy
    
    
def lowestTower(x,y,r):
    atr = 900
    idy = -1
    
    for obs in obstacles:
        dist2 = distance(x, y, obs.x, obs.y)
        if obs.owner == 0 and obs.structureType == 1 and r > dist2 and obs.atr < atr and isSafe(obs.idy):
            atr = obs.atr
            idy = obs.idy
            
    return idy
    

def growMine(x,y,r):
    dist = r
    idy = -1
    
    for obs in obstacles:
        dist2 = distance(x, y, obs.x, obs.y)
        if obs.owner == 0 and obs.structureType == 0 and dist > dist2 and obs.mineSize > obs.atr and isSafe(obs.idy):
            dist = dist2
            idy = obs.idy
            
    return idy


def dangerZone(x,y):    
    for u in eUnits:
        if distance(x, y, u.x, u.y) < dangerDist+200:
            return True
    
    return False
    

# game loop
while True:
    
    turn += 1
    
    pos_moves = []
    moveScore = []
    
    # own structures - just to avoid extra sorts
    rax = 0
    raxID = -1
    
    mrax = 0
    mraxID = -1
    
    mineCount = 0
    income = 0
    
    ot = 0
    
    # enemy structure counts
    et = 0
    emines = 0
    eincome = 0
    egrax = 0
    errax = 0
    emrax = 0
    
    # own units
    melees = 0
    ranged = 0
    giants = 0
    
    # enemy units
    em = 0
    er = 0
    eg = 0
    
    gold, touched = [int(i) for i in input().split()]
    for i in range(num_obstacles):
        # gold_remaining: -1 if unknown
        # max_mine_size: -1 if unknown
        # structure_type: -1 = No structure, 0 = Resource Mine, 1 = Tower, 2 = Barracks
        # owner: -1 = No structure, 0 = Friendly, 1 = Enemy
        idy, gold_, mineSize, structureType, owner, atr, spec = [int(j) for j in input().split()]
        obstacles[idy].gold = gold_
        obstacles[idy].mineSize = mineSize
        obstacles[idy].structureType = structureType
        obstacles[idy].owner = owner
        obstacles[idy].atr = atr
        obstacles[idy].spec = spec
        
        if owner == 1:
            if structureType == 2:
                lolzId = idy
            elif structureType == 1:
                et += 1  
            elif structureType == 0:
                emines += 1
                eincome += atr
        elif owner == 0:
            if structureType == 0:
                mineCount += 1
                income += atr
            elif structureType == 1:
                ot += 1
            elif structureType == 2:
                if spec == 2:
                    rax += 1
                    raxID = idy
                elif spec == 0:
                    mrax += 1
                    mraxID = idy
                    
    units = []
    eUnits = []
            
    num_units = int(input())
    for i in range(num_units):
        x, y, owner, unitType, health = [int(j) for j in input().split()]
        un = unit(x,y,owner,unitType,health)
        
        if unitType == -1:
            if owner == 0:
                myQueen = un
            else:
                enemyQueen = un
        elif unitType == 0:
            if owner == 0:
                units.append(un)
            else:
                eUnits.append(un)
                em += 1
    
    
    # print("Debug messages...", file=sys.stderr)
    
    isSafe(myQueen.x -60, myQueen.y -60)
    isSafe(myQueen.x, myQueen.y -60)
    isSafe(myQueen.x +60, myQueen.y -60)
        
    isSafe(myQueen.x -60, myQueen.y)
    isSafe(myQueen.x, myQueen.y)
    isSafe(myQueen.x +60, myQueen.y)
        
    isSafe(myQueen.x -60, myQueen.y +60)
    isSafe(myQueen.x, myQueen.y +60)
    isSafe(myQueen.x +60, myQueen.y +60)
    
    # building selecton
    def building(x):
        if income < 5 and obstacles[x].mineSize > obstacles[x].atr:
            return " MINE"
        elif mrax == 0:
            return " BARRACKS-KNIGHT"
        return " TOWER"
    # build score moves
       
    for o in obstacles:
        d = distance(myQueen.x, myQueen.y, o.x, o.y)
        if d < 700:
            d -= o.radius
            x = myQueen.x + ((o.x-myQueen.y) / d * 65)
            y = myQueen.y + ((o.y-myQueen.y) / d * 65)
            
            if d < 60 + 30 + 10:
                pos_moves.append( "BUILD " + str(o.idy) + building(o.idy) )
                score = 2 #float(2 + 1000/int(d))
                moveScore.append(score)
            else:
                isSafe(int(x), int(y))
                
            print(len(pos_moves), file=sys.stderr)
            print(pos_moves[-1], file=sys.stderr)
            print(moveScore[-1], file=sys.stderr)
            
    
    print( pos_moves[moveScore.index( max(moveScore) )] ) 
        
        
    # training commands        
    if rax > 0 and raxID >= 0 and gold > 139 and et > 0:
        print("TRAIN", raxID)
    elif mrax > 0 and mraxID >= 0 and gold > 79:
        print("TRAIN", mraxID)
    else:
        print("TRAIN")