# Rock Paper Scissors
rank 3 on the first day

Basic idea:
- favour moves that win against the opponent's last move by adding more of them to the list of possible moves. this prevents them from gaining a bonus for playing same move twice
- partly remove whatever might lose against the opponent's last move from the list


Since the list keeps growing and ends up being biased towards countering specific move, cut off the first half once it reaches a certain size.


Output random move from your list. There's a good chance you play a winning move while also remaining somewhat unpredictable.
