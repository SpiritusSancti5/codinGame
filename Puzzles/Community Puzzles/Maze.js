
var R = [];

var inputs = readline().split(' ');
var W = parseInt(inputs[0]);
var H = parseInt(inputs[1]);
var inputs = readline().split(' ');
var X = parseInt(inputs[0]);
var Y = parseInt(inputs[1]);
for (var i = 0; i < H; i++) {
    R.push(Array.from(readline()));
    printErr(R[i].join(""));
}

printErr(X + ' ' + Y);

var exits = 0;
var arr = [];

function lulz(x, y){
    if(x === 0 || y === 0 || y === H-1 || x === W-1){
        exits++;
        arr.push([x,y]);
        R[y][x] = '#';
    } else {
        R[y][x] = '#';
        if(R[y][x-1] != '#')lulz(x-1, y);
        if(R[y][x+1] != '#')lulz(x+1, y);
        if(R[y-1][x] != '#')lulz(x, y-1);
        if(R[y+1][x] != '#')lulz(x, y+1);
    }
}

lulz(X, Y);

print(exits);
arr.sort(
function(a,b) {
    if (a[0] == b[0]) return a[1] < b[1] ? -1 : 1;
    return a[0] < b[0] ? -1 : 1;
}
);
for(i in arr){
    print(arr[i][0] + ' ' + arr[i][1]);
}



