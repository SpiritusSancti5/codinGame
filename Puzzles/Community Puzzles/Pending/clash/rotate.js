
var grid = [];

var inputs = readline().split(' ');
var l = parseInt(inputs[0]);
var h = parseInt(inputs[1]);

for (var i = 0; i < h; i++) {
    col = readline().split('');
    for (var j = 0; j < l; j++) {
        if(grid[j]){
            grid[j] = col[j] + grid[j];
        } else {
            grid.push(col[j]);
        }
    }
}


for (var i = 0; i < l; i++) {
    print(grid[i]);
}


