# TODO
# fix finding focal point bug
# further generalize this to find all frontiers between player owned subgraphs and neutral subgraph
# check every round for new possible frontiers that can be generated for the opponent due to the tolerance element
#
# currently the bot sorts through a few possible good moves for the opponent and then reacts to these moves as if they're already made

import sys
import math
import random

# container variables for all objects and gamestate related stuff

class planet:
    def __init__(self, _id):
        return
        

def evaluate(m,x,count):
    return
# check how good each move is and return a score

            
def generateMoveSets():
    return
# generates all possible moves for the bot, depth1 only


def canBeAssed(wakusei,player):
    return
# checks if a planet can be taken over by given player
    

def findFocalPoint():
    return
# find the focal point of the map, if there is any

p, e = [int(i) for i in input().split()]

for i in range(p):
    pl = planet(i)
    planets.append(pl)

for i in range(e):
    a, b = [int(j) for j in input().split()]
    planets[a].neighbours.append(b)
    planets[b].neighbours.append(a)
    
# game loop
while True:

    for i in range(p):
        mU, mT, oU, oT, cba = [int(j) for j in input().split()]

        planets[i].update(mU,mT,oU,oT,cba)
        
        if cba == 1:
            pass
        # get own available planets
                    
    
    # most important node on the map                
    findFocalPoint()
    
      
    # enemy prediction  
        
        # get enemy's available planets and group them into 3 different categories:
        # owned
        # neutral
        # enemy                
    
    # sort available planets by whatever criteria you want
    # this is one of the many ways you can sort them after they are grouped
    eT.sort(key=lambda x: (x.oT,x.owner*-1,abs(x.oU-x.mU)*-1,len(x.neighbours)*-1), reverse=True)
    eTn.sort(key=lambda x: len(x.neighbours)*-1, reverse=True) 
    eTe.sort(key=lambda x: (x.oT*-1,x.owner*-1,abs(x.oU-x.mU)*-1,len(x.neighbours)*-1), reverse=True) 
    
    
    #------------
    
        # apply enemy moves
    
    #------------

    print("enemy moves", file=sys.stderr)
    
        # enemy moves are printed
    
    print("----------", file=sys.stderr)
                
                
    #------------
    
        # update game state
    
    #------------
        
       
    # generate move sets
    generateMoveSets()
    
    # sort moves and combine them    
    #------------        
        
    # print best moves
    #------------
    
    

    
    
    