var CPY = [];
 var CPX = [];

var laps = parseInt(readline());    // laps
var CP_Nr = parseInt(readline());  // checkpoint count
for (var i = 0; i < CP_Nr; i++) {   // check point coordinates
    var inputs = readline().split(' ');
    CPX[i] = parseInt(inputs[0]);
    CPY[i] = parseInt(inputs[1]);
}

// pods positions
var Px = [];
var Py = [];
// pods speeds
var Vx = [];
var Vy = [];
// pods angles
var A = [];
// pods next check point ID
var P_id = [];
// hunting target
var killXY = [0,0];
// check point target
var aim = [0,0];
// boost and shield
var boost = true,
    shield = true;

// enemy pods
// pods positions
var Ex = [];
var Ey = [];
// pods speeds
var Sx = [];
var Sy = [];
// pods angles
var An = [];
// pods next check point ID
var E_id = [];
// current enemy laps
var EL = [0,0];


// game loop
while (true) {
    for (var i = 0; i < 2; i++) {
        var inputs = readline().split(' ');
        Px[i] = parseInt(inputs[0]); // x position of your pod
        Py[i] = parseInt(inputs[1]); // y position of your pod
        Vx[i] = parseInt(inputs[2]); // x speed of your pod
        Vy[i] = parseInt(inputs[3]); // y speed of your pod
        A[i] = parseInt(inputs[4]); // angle of your pod
        P_id[i] = parseInt(inputs[5]); // next check point id of your pod
    }
    for (var i = 0; i < 2; i++) {
        var inputs = readline().split(' ');
        Ex[i] = parseInt(inputs[0]); // x position of the opponent's pod
        Ey[i] = parseInt(inputs[1]); // y position of the opponent's pod
        Sx[i] = parseInt(inputs[2]); // x speed of the opponent's pod
        Sy[i] = parseInt(inputs[3]); // y speed of the opponent's pod
        An[i] = parseInt(inputs[4]); // angle of the opponent's pod
        
        // lap increase
        if((E_id[i] === 0)&&(parseInt(inputs[5]) === 1)){
            lapUpdater(i);
        }
        E_id[i] = parseInt(inputs[5]); // next check point id of the opponent's pod
    }
    
    lapUpdater();
    var target = targetSelector();
    
    killXY = hunt();
    aim = focus();
    
    print(killXY[0] + ' ' + killXY[1] + ' ' + speed(0,killXY));
    print(aim[0] + ' ' + aim[1] + ' ' + speed(1,aim));
}

// update laps
function lapUpdater(a){ EL[a]++; }

// focus fire
function targetSelector(){
    if(EL[0] != EL[1]){
        if(EL[0]>EL[1]) return 0;
        return 1;
    } else if(E_id[0] > E_id[1]){
        return 0;
    } else return 1;
}

// blocker
function hunt(){
    hx = Ex[target] + Sx[target]*3;
    hy = Ey[target] + Sy[target]*3;
    return [hx,hy];
}

// runner
function focus(){
    fx =CPX[P_id[1]];
    fy =CPY[P_id[1]];
    return [fx,fy];
}

// distance between two points
function dist(x1, y1, x2 = 0, y2 = 0){
    Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
}

function distanceBetween(point1x, point1y, point2x, point2y){
    return dist(point1x, point1y, point2x, point2y);
}

// function for angle
function angleDeg(px,py,p2x,p2y){
// angle in radians
// var angleRadians = Math.atan2(p2.y - p1.y, p2.x - p1.x);

// angle in degrees
    return Math.atan2(p2y - py, p2x - px) * 180 / Math.PI;
}



// speed adjustment
function speed(pod,T){
    let distance = distanceBetween(Px[pod],Py[pod],T[0],T[1]);
    
    if (distance > 2000 && boost && pod === 1) {
        boost = false;
        return ' BOOST';
    } else if((pod === 0)&&(distance < 900)){
        return distane/10 + 5;
    } else if(distance <= 1300) {
        if((pod === 0)&&(distance < 600)) return 'SHIELD';
        return distane*7/100;
    }
    
    return 100;
}