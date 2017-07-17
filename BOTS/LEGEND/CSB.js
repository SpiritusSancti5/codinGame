// Distance starting from the middle of the checkpoint for the racer to aim for
const radius = 350;

// check point coordinates
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
var boost = [true, true];

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


var target = 1;


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
        target = targetSelector();
    var msg = 'target pod ' + target; //target + '  ' + A[0] + '  ' + Vx[0] + '  ' + Vy[0];
    var msg2 = ''; //P_id[1] + ' ' + A[1] + '  ' + Vx[0] + '  ' + Vy[0];
    
    killXY = hunt();
    aim = focus();
    
     
    print(killXY[0] + ' ' + killXY[1] + ' ' + speed(0,killXY) + ' ლ(ಠ益ಠლ) ' + msg);
    print(aim[0] + ' ' + aim[1] + ' ' + speed(1,aim) + ' (◕‿◕✿) ' + msg2);
}

// update laps
function lapUpdater(a){ EL[a]++; }

// focus fire
function targetSelector(){
    if(EL[0] != EL[1]){
        if(EL[0]>EL[1]){ return 0; }
        else return 1;
    } else if((E_id[0] > E_id[1])&&(E_id[1] > 0)){
        return 0;
    } else if((E_id[0] < E_id[1])&&(E_id[0] > 0)){
        return 1;
    } else if((E_id[0] === E_id[1])){
        if(dist(Ex[0], Ey[0], CPX[E_id[0]], CPY[E_id[0]]) > dist(Ex[1], Ey[1], CPX[E_id[1]], CPY[E_id[1]])){
            return 1;
        }
        return 0;
    }
    
    return target;
}

// BURN's aim
function hunt(){
    
    var next_ep = E_id[target] + 1;
    if(next_ep > CP_Nr - 1){ next_ep = 0; }
    vlength = Math.sqrt((CPX[E_id[target]] - CPX[next_ep]) * (CPX[E_id[target]] - CPX[next_ep]) + (CPY[E_id[target]] - CPY[next_ep]) * (CPY[E_id[target]] - CPY[next_ep]));
    if(vlength === 0){ vlength++ }
    
    if(dist(Px[0], Py[0], CPX[E_id[target]], CPY[E_id[target]]) > dist(Ex[target], Ey[target], CPX[E_id[target]], CPY[E_id[target]])*1.7){
       return [CPX[next_ep],CPY[next_ep]];
    }
    
    // hx = Math.ceil(Ex[target] + Sx[target]*2.09);
    // hy = Math.ceil(Ey[target] + Sy[target]*2.09);
    hx = Math.round( (Ex[target]*7 + CPX[E_id[target]]*3)/10- Sx[target]/2.59- Vx[0]*1.2 - Math.round(CPX[next_ep] - CPX[E_id[target]])/vlength * 35);
    hy = Math.round( (Ey[target]*7 + CPY[E_id[target]]*3)/10- Sy[target]/2.59- Vy[0]*1.2 - Math.round(CPY[next_ep] - CPY[E_id[target]])/vlength * 35);
    // small add towards next target
    // V0 = P1 +   V - P0
    
    // hx = Math.round();
    // hy = Math.round();
    
    if(isNaN(hx)||isNaN(hy)){return [CPX[P_id[0]],CPY[P_id[0]]];}
    return [hx,hy];
}

// @_@'s aim
function focus(){
    
    var next_pp = P_id[1] + 1;
    if(next_pp > CP_Nr - 1){ next_pp = 0; }
    
    vlength = dist(CPX[P_id[1]], CPY[P_id[1]], CPX[next_pp], CPY[next_pp]);
    if(vlength === 0){ vlength++ }
    
    // hx = Math.ceil(Ex[target] + Sx[target]*2.09);
    // hy = Math.ceil(Ey[target] + Sy[target]*2.09);
    
    fx = Math.round(CPX[P_id[1]] - Vx[1]*2.93 + Math.round(CPX[next_pp] - CPX[P_id[1]])/vlength* 33);
    fy = Math.round(CPY[P_id[1]] - Vy[1]*2.93 + Math.round(CPY[next_pp] - CPY[P_id[1]])/vlength* 33);
    if(isNaN(fx)||isNaN(fy)){return [CPX[P_id[1]],CPY[P_id[1]]];}
    
    
    
    return [fx,fy];
}

// Functions for calculating distance between two points
function dist(x1, y1, x2 = 0, y2 = 0){
    return Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
}

// speed adjustment
function speed(pod,T){
    var distance = 0;
    if(pod === 0){ distance = dist(Px[pod], Py[pod],T[0],T[1]); }
    else { distance = dist(Px[pod],Py[pod],T[0],T[1]); }
    
    var next_pp = P_id[1] + 1;
    if(next_pp > CP_Nr - 1){ next_pp = 0; }
    
    var angle = getAngle(pod, [CPX[P_id[pod]], CPY[P_id[pod]]], distance);
    //var angle = angleDeg(Px[pod], Py[pod], T[0], T[1]);
    var difangle = diffAngle(pod, T, angle);
    var bonus = 0;
    var bonusmin = 0;
    printErr('dist: ' + distance);
    printErr('angle: ' + angle + ' pod : ' + pod);
    printErr('difangle: ' + difangle + ' pod : ' + pod);
    //if ((difangle <= 0 || difangle >= 0)) {
    //    bonus += 95;
    //} else 
    //if ((difangle <= 0.5 || difangle >= 0.5)) {
    //    bonus += 50;
    //} else 
    if ((difangle <= 3 || difangle >= 3)) {
        bonus += 30;
    } else 
    if ((difangle <= 9 || difangle >= 9)) {
       bonus += 20;
    } else if ((difangle <= 25 || difangle >= 25)) {
        bonus += 9;
    }
    
    if (distance > 2000 && boost[pod]) {
        boost[pod] = false;
        return 'BOOST';
    } else if (distance <= 300) {
        if (pod === 0) return 'SHIELD';
        return 5 + bonus;
    } else if (distance <= 500) {
        //if (pod === 0) return 'SHIELD';
        return 5 +bonus;
    } else if (distance <= 700) {
       // if (pod === 0) return 22;
        //return 69+bonus;
    } else if (distance <= 900) {
        //if (pod === 0) return 25;
        //return 79+bonus*2/3;
    } else if (distance <= 1100) {
        //if (pod === 0) return 52;
        //return 93+bonus/5;
    }
    
    return 100;
}

function getAngle(pod, T, distance) {
    var dx = (T[0] - Px[pod]) / distance;
    var dy = (T[1] - Py[pod]) / distance;

    // Simple trigonometry. We multiply by 180.0 / PI to convert radiants to degrees.
    var a = Math.acos(dx) * 180.0 / Math.PI;

    // If the point I want is below me, I have to shift the angle for it to be correct
    if (dy < 0) {
        a = 360.0 - a;
    }

    return a;
}

// ~~~~~~~ function for angle ~~~~~~~
function angleDeg(px,py,p2x,p2y){
// angle in radians
// var angleRadians = Math.atan2(p2.y - p1.y, p2.x - p1.x);

// angle in degrees
    return Math.atan2(p2y - py, p2x - px) * 180 / Math.PI;
}

function diffAngle(pod, T, angle) {

    // To know whether we should turn clockwise or not we look at the two ways and keep the smallest
    // The ternary operators replace the use of a modulo operator which would be slower
    var right = A[pod] <= angle ? angle - A[pod] : 360.0 - A[pod] + angle;
    var left = A[pod] >= angle ? A[pod] - angle : A[pod] + 360.0 - angle;

    if (right < left) {
        return right;
    } else {
        // We return a negative angle if we must rotate to left
        return left * (-1);
    }
}

function rotate(pod, T, a) {

    // Can't turn by more than 18° in one turn
    if (a > 18.0) {
        a = 18.0;
    } else if (a < -18.0) {
        a = -18.0;
    }

    A[pod] += a;

    // The % operator is slow. If we can avoid it, it's better.
    if (A[pod] >= 360.0) {
        A[pod] -= 360.0;
    } else if (A[pod] < 0.0) {
        A[pod] += 360.0;
    }
}

function output(move) {
    var a = angle + move.angle;

    if (a >= 360.0) {
        a = a - 360.0;
    } else if (a < 0.0) {
        a += 360.0;
    }

    a = a * PI / 180.0;
    var px = this.x + cos(a) * 10000.0;
    var py = this.y + sin(a) * 10000.0;

   print(round(px), round(py), move.power);
}