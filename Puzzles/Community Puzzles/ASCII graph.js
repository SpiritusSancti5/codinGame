var arr = [];
var newLine = '';

var miny = 0;
var minx = 0;
var maxy = 0;
var maxx = 0;

var N = parseInt(readline());

for (var i = 0; i < N; i++) {
    var inputs = readline().split(' ');
    var x = parseInt(inputs[0]);
    var y = parseInt(inputs[1]);
    
    
    if(minx > x){
        minx = x;
    } else if(maxx < x){
        maxx = x;
    } 
    
    if(miny > y){
        miny = y;
    } else if(maxy < y){
        maxy = y;
    } 
    
    arr.push([inputs[0],inputs[1]]);
}

function check(nx, ny){
    for(var k = 0; k < arr.length; k++){
        if(arr[k][0] == nx && arr[k][1] == ny){
            return true;
        }
    }
    return false;
}


for(var i = -maxy-1; i <= 1-miny; i++){
    newLine = '';
    for(var j = minx-1; j <= maxx+1; j++){
        if( check(j,-i) ){ // should probably use map array or somesuch, not sure
            newLine += '*';
        } else if(j === 0 && i === 0){
            newLine += '+';
        } else if(j === 0){
            newLine += '|';
        } else if(i === 0){
            newLine += '-';
        } else { 
            newLine += '.';
        }
    }
    print(newLine);
}