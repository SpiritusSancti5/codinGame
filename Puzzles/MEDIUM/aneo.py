import sys
import math


lights =[]

times = []

speed = int(input())
print(speed, file=sys.stderr)
light_count = int(input())
for i in range(light_count):
    distance, duration = [int(j) for j in input().split()]
            
    lights.append([distance,duration])
    
print(lights[i], file=sys.stderr)
    
def check():
    peed = speed*1000/3600
    # print(speed,peed, file=sys.stderr)
    for i in range(light_count):
        
        period = lights[i][0]/peed/lights[i][1]
        if period - int(period) > 0.99999999999990:
            period = int(period) +1
        if(int(period) % 2 != 0):
            return False
    
    return True


while True:
    if check():
        print(speed)
        break
    else:
        speed -= 1
    
    
    
    