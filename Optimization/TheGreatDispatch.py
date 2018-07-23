# To debug: print("Debug messages...", file=sys.stderr)
import sys
import math
from random import randint

boxes = []
trucks = []

avgSV = 0
avgSW = 0
 

S = []


class box:
    def __init__(self, weight, volume, _id):
        self._id = _id
        self.weight = weight
        self.volume = volume
        self.truck = -1
        if weight < 3300:
            self.category = "S"
            S.append(self)
            global avgSV
            avgSV += volume
            global avgSW
            avgSW += weight

class truck:
    def __init__(self, _id):
        self._id = _id
        self.weight = 0.0
        self.volume = 0.0
        self.boxes = []


avgW = 0

box_count = int(input())
for i in range(box_count):
    weight, volume = [float(j) for j in input().split()]
    b = box(weight,volume, i)
    boxes.append(b)
    avgW += weight
    
    
for i in range(100):
    t = truck(i)
    trucks.append(t)
    

avgW /= 100
print(avgW, file=sys.stderr)

S.sort(key=lambda x: x.weight, reverse=True)
for l in S:
    lowest = 100000
    _id = -1
    for low in trucks:
        if low.weight < lowest and low.volume + l.volume <= 100:
            lowest = low.weight
            _id = low._id
    trucks[_id].weight += l.weight
    trucks[_id].volume += l.volume
    trucks[_id].boxes.append(l)
    l.truck = trucks[_id]._id
    

for i in range(300):
    trucks.sort(key=lambda x: x.weight, reverse=True)
    for x in range(0,99):
        found = False
        # x = randint(1,99)
        for b in trucks[-1].boxes:
            if found:
                break
            for c in trucks[x].boxes:
                if trucks[-1].volume - b.volume + c.volume <= 100 and trucks[x].volume + b.volume - c.volume <= 100:
                    if abs(avgW - trucks[-1].weight) > abs(avgW - (trucks[-1].weight - b.weight + c.weight)) or abs(avgW - trucks[x].weight) > abs(avgW - (trucks[x].weight + b.weight - c.weight)):
                        trucks[-1].weight -= b.weight
                        trucks[-1].volume -= b.volume
                        trucks[x].weight -= c.weight
                        trucks[x].volume -= c.volume
                        trucks[-1].weight += c.weight
                        trucks[-1].volume += c.volume
                        trucks[x].weight += b.weight
                        trucks[x].volume += b.volume  
                        boxes[b._id].truck = trucks[x]._id
                        boxes[c._id].truck = trucks[-1]._id
                        trucks[-1].boxes.remove(b)
                        trucks[-1].boxes.append(c)
                        trucks[x].boxes.remove(c)
                        trucks[x].boxes.append(b)
                        found = True
                        break
                    
                    
output = ""
for b in boxes:
    output += str(b.truck) + " "
print(output)




