# Code Royale - the code i ditched for a simple heuristics bot #

Rank: 245 / 2120

About the contest code:

Disclaimer: a simulation is more efficient and makes like easier, no doubt about it and i regret i didn't start with that. But wasn't very motivated, even considered skipping this contest. Fun fact: without a simulation your queen will be tripping all over the place when either your kngiths or an enemy knight get too close, which looks really stupid from a design POV. It made constantly consider dropping out of this, but it's a contest so i still wanted to give it a try and see how far i can get with minimum effort.

Most important details regarding my contest bot:
Keep track of all entity types and counts and make decisions based on this.

For example if enemy knights are on the way, the queen would upgrade towers, move further away and try to build more towers on empty sites.

Mine count isn't important. It's income that matters and how it's used. I disregarded placement since mines at the back are safe from knights and those at the front generate more gold, so it balances out.

There are two important aspects regarding enemy knights that i was keeping track of and this is what got me out of silver without much effort:
1. the distance from the queen to the closest enemy knight that is further than 100 spaces away, since this is the first one the queen will try to avoid getting damaged by. Anything closer than 50 distance is generally unavoidable.
2. total enemy knights on the map - this number is important because it helps prepare some extra towers, unless there's a huge knight swarm heading towards the queen

Keeping track of the game state helps you save space by upgrading mines and towers instead of constantly building new ones, which takes extra turns to move around and takes up extra sites. Could have gone a bit further with optimizing this as it's very important. For example often swapping a tower for a mine could help for a quick boost if it's a site in the middle of the map. Or swapping a mine for a tower so that towers are more concentrated into a single location.

Instead of optimizing my own building placement, i spent time trying to rob the opponent of his carelessly placed barracks and mines which were close to my queen - this helps a lot and it's far more fun to watch. Once their barrack is gone there's fewer knights one needs to worry about. It's still very tricky and hard to do without a simulation. As i already mentioned, the queen is tripping all over the place when she's surrounded by knights.


There's not much else to my bot, but these basic principles got me #100 in gold. I spammed submits and ended up with a lower rank, but i could have easily taken rank 60 in gold due to the fact that the red side was at advantage. Getting lucky was important in this contest, especially for those without a sim.


# About this bot #

The code you can observe in this repo is the beginning of a simple random move generator and some evaluation for best moves. A simulation is to be added to better evaluate the moves, this can be easily done by using the referee code and a few conditionals. Additionally one can go further and perhaps write a genetic algorithm because bots with random unexpected strategies can and have accomplished wonders in this contest and it did occasionally score a few wins vs top bots ...

The heuristic approach is better off without this code.