import sys
import math
import random


l = ['R','P','S']

# Try predicting the choices of your opponent and counter them!



# game loop
while True:
    om = input()
    
    if om == 'R':
        l.append('P')
        if 'S' in l:
            l.remove('S')
        if 'S' in l:
            l.remove('S')
    if om == 'P':
        l.append('S')
        if 'R' in l:
            l.remove('R')
        if 'R' in l:
            l.remove('R')
    if om == 'S':
        l.append('R')
        if 'P' in l:
            l.remove('P')
        if 'P' in l:
            l.remove('P')
    # Write an action using print
    # To debug: print("Debug messages...", file=sys.stderr)

    if len(l) > 10:
        l = l[int(len(l)/2):]

    # R | P | S
    print(random.choice(l))