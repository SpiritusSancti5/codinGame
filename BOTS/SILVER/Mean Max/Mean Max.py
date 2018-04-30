import sys
import math
import time

# Auto-generated code below aims at helping you parse
# the standard input according to the problem statement.

def dist(a, b, x2, y2):
   return math.sqrt( (a-x2)*(a-x2) + (b-y2)*(b-y2) )

# game loop
while True:
    
    t0 = time.time()
    
    my_score = int(input())
    enemy_score_1 = int(input())
    enemy_score_2 = int(input())
    
    T = 0
    if enemy_score_1 > enemy_score_2:
        T = 1
    else:
        T = 2
    
    my_rage = int(input())
    enemy_rage_1 = int(input())
    enemy_rage_2 = int(input())
    
    car = []
    
    tank = []
        
    pud = []
    
    tar = []
    
    oil = []
    
    unit_count = int(input())
    for i in range(unit_count):
        unit_id, unit_type, player, mass, radius, x, y, vx, vy, extra, extra_2 = input().split()
        #print("unit id: "+unit_id, file=sys.stderr)
        unit_id = int(unit_id)
        #print("unit type: "+unit_type, file=sys.stderr)
        unit_type = int(unit_type)
        #print("player id: "+player, file=sys.stderr)
        player = int(player)
        mass = float(mass)
        radius = int(radius)
        
        x = int(x)
        y = int(y)
        vx = int(vx)
        vy = int(vy)
        
        extra = int(extra)
        extra_2 = int(extra_2)
        
        if unit_type < 3:
            c = [unit_id, unit_type, player, mass, radius, x, y, vx, vy, extra, extra_2]
            if player > 0:
                car.append(c)
            else:
                car.insert(unit_type, c)
            #for i in range(len(car)):
            #    print("cars: ",car[i], file=sys.stderr)
        elif unit_type == 3:
            c = [unit_id, mass, radius, x, y, vx, vy, extra, extra_2]
            tank.append(c)
        elif unit_type == 4:
            dist_pud = dist(x,y, car[0][5], car[0][6])
            c = [unit_id, radius, x, y, extra, dist_pud]
            if len(pud) > 0 and c[5] < pud[0][5]:
                pud.insert(0, c)
            else:
                pud.append(c)
            # print(pud, file=sys.stderr)
            # dist calcs
            # intersection and extra water area
            # graph or surface area plot
        elif unit_type == 5:
            c = [unit_id, radius, x, y, 3]  #turns
            tar.append(c)
        else:
            c = [unit_id, radius, x, y, 3]  #turns
            oil.append(c)

    # Write an action using print
    # To debug: print("Debug messages...", file=sys.stderr)
    
    
    # REAPER
    if len(pud) > 0:
        print(int(pud[0][2]-car[0][7]),int(pud[0][3]-car[0][8]),300," x y 42")
    else:
        print(int(car[T*3][5]-car[T*3][7]*1.75),int(car[T*3][6]-car[T*3][8]*1.75),300," this is gonna be great")
        # destroyer closest to a tanker
    ##==================================== 
    t1 = time.time()

    total = t1-t0
    print("REAPER ",total, file=sys.stderr)
    
    
    # DESTROYER
    if my_rage >= 60 and dist(int(car[T*3][5]+car[T*3][7]*1.75), int(car[T*3][6]+car[T*3][8]*1.75), car[1][5], car[1][6]) < 1200:
        print("SKILL",int(car[T*3][5]+car[T*3][7]*1.75), int(car[T*3][6]+car[T*3][8]*1.75)," SKILL STRIKE")
    else:
        print(car[0][5]+car[0][7]*3, car[0][6]+car[0][8]*3,300," x y 42")
    ##================================
    t1 = time.time()

    total = t1-t0
    print("DESTROYER ",total, file=sys.stderr)
    
    
    # DOOF
    print(int(car[T*3][5]+car[T*3][7]*1.75), int(car[T*3][6]+car[T*3][8]*1.75),300," x y 42")
    ##=======================================
    t1 = time.time()

    total = t1-t0
    print("DOOF ",total, file=sys.stderr) 