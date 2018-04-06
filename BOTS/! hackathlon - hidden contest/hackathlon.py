import sys
import math

# Grab the ball and try to throw it through the opponent's goal!
# Move towards the ball and use your team id to determine where you need to throw it.

myTeam = int(input())  # if 0 you need to score on the right of the map, if 1 you need to score on the left

xG = 0
yG = 0

if myTeam == 0:
    xG = 16000
    yG = 3750
else:
    xG = 0
    yG = 3750

class ent:
    def __init__(self, idy, etype, x, y, vx, vy, state):
        self.idy = idy
        self.etype = etype
        self.x = x
        self.y = y
        self.vx = vx
        self.vy = vy
        self.state = state

# game loop
while True:
    
    ball = ent(0, "ball", 0, 0, 0, 0, 0)
    
    me = []

    ee = []
    
    
    entities = int(input())  # number of entities still in game
    for i in range(entities):
        # entity_id: entity identifier
        # entity_type: "FOOTBALLER", "OPPONENT" or "BALL"
        # x: position
        # y: position
        # vx: velocity
        # vy: velocity
        # state: 1 if the footballer is holding the ball, 0 otherwise
        entity_id, entity_type, x, y, vx, vy, state = input().split()
        entity_id = int(entity_id)
        x = int(x)
        y = int(y)
        vx = int(vx)
        vy = int(vy)
        state = int(state)
        
        n = ent(entity_id, entity_type, x, y, vx, vy, state)
        if entity_type == "FOOTBALLER":
            me.append(n)
        elif entity_type == "OPPONENT":
            ee.append(n)
        else:
            ball = n
        
        
    have = False    
    for i in range(4):
        # To debug: print("Debug messages...", file=sys.stderr)

        # Edit this line to indicate the action for each footballer (0 <= thrust <= 150, 0 <= power <= 500)
        # i.e.: "MOVE x y thrust" or "THROW x y power"
        
        if me[i].state == 1:
            print("THROW",xG,yG,"500")
        else:
            print("MOVE",ball.x,ball.y,"150")
            
            