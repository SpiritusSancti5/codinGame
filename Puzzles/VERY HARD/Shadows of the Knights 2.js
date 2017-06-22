
// requires a bit of tweaking but it's mostly done


var inputs = readline().split(' ');
var w = parseInt(inputs[0]); // width of the building.
var h = parseInt(inputs[1]); // height of the building.
var n = parseInt(readline()); // maximum number of turns before game over.
var inputs = readline().split(' ');
var x = parseInt(inputs[0]);
var y = parseInt(inputs[1]);

h--; w--;

var minx = 0;
var miny = 0;

var lx = x;
var ly = y;

var arr = [];

var wait = false;

    printErr('building : ' + w + ' ' + h);
    printErr('jumps : ' + n);
    printErr('position : ' + x + ' ' + y);

// game loop
while (true) {
    var bombDir = readline(); // Current distance to the bomb compared to previous distance (COLDER, WARMER, SAME or UNKNOWN)
    
    printErr('bombDir: ' + bombDir);
    
    if(bombDir == 'WARMER'){
        switch(arr[0].o){
        case 'a':
            w = Math.floor((lx+x)/2);
            lx = x;
            break;
        case 'b':
            minx = Math.floor((lx+x)/2);
            lx = x;
            break;
        case 'c':
            h = Math.floor((ly+y)/2);
            ly = y;
            break;
        case 'd':
            miny = Math.floor((ly+y)/2);
            ly = y;
            break;
        }
    } else if(bombDir == 'COLDER'){
        switch(arr[0].o){
        case 'a':
            minx = Math.floor((lx+x)/2);
            x = lx;
            break;
        case 'b':
            w = Math.floor((lx+x)/2);
            x = lx;
            break;
        case 'c':
            miny = Math.floor((ly+y)/2);
            y = ly;
            break;
        case 'd':
            h = Math.floor((ly+y)/2);
            y = ly;
            break;
        }
    } else if(bombDir == 'SAME'){
        switch(arr[0].o){
        case 'a':
        case 'b':
            if(x!==0)
                x = Math.floor((lx+x)/2) +1;
            lx = minx = w = x;
            break;
        case 'c':
        case 'd':
            y = Math.floor((ly+y)/2) +1;
            ly = miny = h = y;
            break;
        }
        wait = true;
        print(x + ' ' + y);
    }    
    
    if(!wait){
    arr.length = 0;
    
    arr.push({o : 'a', v : Math.abs(x - minx)});
    arr.push({o : 'b', v : Math.abs(w - x)});
    arr.push({o : 'c', v : Math.abs(y - miny)});
    arr.push({o : 'd', v : Math.abs(h - y)});

    arr = arr.sort(compare);
    
    switch(arr[0].o){
        case 'a':
            x -= Math.floor(arr[0].v/2);
            break;
        case 'b':
            x += Math.floor(arr[0].v/2);
            break;
        case 'c':
            y -= Math.floor(arr[0].v/2);
            break;
        case 'd':
            y += Math.floor(arr[0].v/2);
            break;
    }
    
    print(x + ' ' + y); 
    
    } else wait = false;
    
    n--;
    printErr('jumps left : ' + n);
    printErr('last position : ' + lx + ' ' + ly);
    printErr('new position : ' + x + ' ' + y);
    printErr('minx : ' + minx + ', miny : ' + miny + ', w : ' + w + ', h : ' + h);
}


function compare(a,b) {
    if (a.v < b.v)
        return 1;
    if (a.v > b.v)
        return -1;
    return 0;
}