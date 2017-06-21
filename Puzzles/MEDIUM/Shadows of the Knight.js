var inputs = readline().split(' ');
var building = {
    maxX: parseInt(inputs[0]),
    minX: 0,
    maxY: parseInt(inputs[1]),
    minY: 0,
};

var turnsleft = parseInt(readline());

var inputs = readline().split(' ');
var hero = {        
    x: parseInt(inputs[0]),
    y: parseInt(inputs[1])
};

while (true) {
    printErr('dir', dir = readline());
    
    if (dir.contains('R')) building.minX = hero.x;
    if (dir.contains('L')) building.maxX = hero.x;
    if (dir.contains('U')) building.maxY = hero.y;
    if (dir.contains('D')) building.minY = hero.y;
    
    hero.x = (building.maxX + building.minX) >> 1;
    hero.y = (building.maxY + building.minY) >> 1;
    
    print(hero.x, hero.y);
}