var msg = '';

var val;

var surr = false;

var size = parseInt(readline());
var unitsPerPlayer = parseInt(readline());

var dirr = {};
dirr['N'] = [0,-1];
dirr['NE'] = [1,-1];
dirr['E'] = [1,0];
dirr['SE'] = [1,1];
dirr['S'] = [0,1];
dirr['SW'] = [-1,1];
dirr['W'] = [-1,0];
dirr['NW'] = [-1,-1];

// neighbout check vars
var miny, maxy, minx, maxx;

// loop check vars
var last_move = {};
var loop_level = 0;

// unit model
function unit(x, y) {
    this.x = x;
    this.y = y;
}

// last known positions
var last_pos = [],   // position before possible push
    last_seen = [], // last known enemy position
    estimated = []; // aproximations
    //initilize
    last_pos[0] = new unit( -1, -1 );
    last_pos[1] = new unit( -1, -1 );
    last_seen[0] = new unit( -1, -1 );
    last_seen[1] = new unit( -1, -1 );
    estimated[0] = new unit( -1, -1 );
    estimated[1] = new unit( -1, -1 );

var heatmap = [-2];
var hotspot = new unit( -1, -1 );

// game loop
while (true) {    
    var grid = [];
    
var me = [],
    enemy = JSON.parse(JSON.stringify(last_seen));

var moves = [];

function move(anaconda, id, d, dd, v, msg) {
    this.a = anaconda;
    this.id = id;
    this.dir = d;
    this.dir2 = dd;
    this.v = v;
    this.msg = msg;
}
    
    
    for (var i = 0; i < size; i++) {
        var row = readline();
        grid.push(row.replace(/\./g, "5").split(""));
        // probably don't need replace, but i wanted int array :[
        printErr(grid[i]);
    }
    // track the enemy's building spot
    if(heatmap[0] == -2){ heatmap = grid.slice(); }
    else { 
        hot:
        for (var i = 0; i < size; i++) {
            for (var j = 0; j < size; j++) {
                if(heatmap[i][j] != grid[i][j]){
                    hotspot[0] = i;
                    hotspot[1] = j;
                    break hot;
                }
            }   
        }
    }
    // printErr('++++++++++++++++');
    // printErr('hotspot: ' + hotspot[0] + ' ' + hotspot[1]);
    // for (var i = 0; i < size; i++) { printErr(heatmap[i]); } 
    // printErr('++++++++++++++++');
    for (var i = 0; i < unitsPerPlayer; i++) {
        var inputs = readline().split(' ');
        me[i] = new unit( parseInt(inputs[0]), parseInt(inputs[1]) );
        printErr('builder' + i + ' : ' + me[i].x + ' ' + me[i].y);
        if(last_pos[i].x < 0){
            last_pos[i].x = me[i].x,
            last_pos[i].y = me[i].y;
        }
        printErr('last pos' + i + ' : ' + last_pos[i].x + ' ' + last_pos[i].y);
    }
    for (var i = 0; i < unitsPerPlayer; i++) {
        var inputs = readline().split(' ');
        enemy[i] = new unit( parseInt(inputs[0]), parseInt(inputs[1]) );
        printErr('enemy' + i + ' : ' + enemy[i].x + ' ' + enemy[i].y);
        // only update from input if enemies are seen
        if(enemy[i].x > -1){
            if(enemy[i].x == last_seen[i].x){
                if(enemy[Math.abs(i-1)].x < 0){
                    last_seen[Math.abs(i-1)].x = -1;
                    last_seen[Math.abs(i-1)].y = -1;       
                }    
            } else {
                last_seen[i].x = enemy[i].x;
                last_seen[i].y = enemy[i].y;
                
                estimated[i].x = enemy[i].x;
                estimated[i].y = enemy[i].y;
            }
        }
        // enemies remained hidden
        if((enemy[0].x<0)&&(enemy[1].x<0)){
            last_seen[0].x = -1;
            last_seen[0].y = -1; 
            last_seen[1].x = -1;
            last_seen[1].y = -1; 
        }
        // printErr('hidden' + i + ' : ' + last_seen[i].x + ' ' + last_seen[i].y);
        printErr('estimated' + i + ' : ' + estimated[i].x + ' ' + estimated[i].y);
    }
    var legalActions = parseInt(readline());
   
    
    var n = -2;
    // var favm, favb;
        val = 0;
    var maxval = -555;
    
    // defeat message
   if(legalActions <= 0) print('ACCEPT-DEFEAT' + ' Defeat is nothing but a state of mind.');
    
    for (var i = 0; i < legalActions; i++) {
        
        msg = ''; // avoid sending wrong message
        
        var inputs = readline().split(' ');
        var atype = inputs[0];
        
        var idx = parseInt(inputs[1]);
        var seek = parseInt(grid[me[idx].y][me[idx].x]);
        
        val = 0;
        
        var mx = dirr[inputs[2]][0] + me[idx].x,
            my = dirr[inputs[2]][1] + me[idx].y,
            bx = dirr[inputs[3]][0] + mx,
            by = dirr[inputs[3]][1] + my;


// best build spots
// TODO +++++++++++++++++++++ bigger area calculation +++++++++++++++
if(atype == 'MOVE&BUILD'){
    // best moves, getting the point is usually good
    if(grid[my][mx] == '3') { val+=3; } // try 20
    // -----
    if(seek < parseInt(grid[my][mx])) { val+=5; }
    else if(seek > parseInt(grid[my][mx])) { val -= 9; }
    else if(seek == parseInt(grid[my][mx])) { val++; } // try remove
    
    // build up in preparation for point
    if((grid[by][bx] == '2')&&(grid[my][mx] > '1')) {  val+=2; } // try 3
    // don't build on point you can take
    if((grid[by][bx] == '3')&&(grid[my][mx] > '1')) {  val-=5; }
    // trying to build stairs and avoid wall-in
    if((+grid[by][bx]) <= (+grid[my][mx])) {  val++; }
    // build next to ally
    // TRY if((Math.abs(me[Math.abs(idx-1)].x - bx) <= 1) && (Math.abs(me[Math.abs(idx-1)].y - by) <= 1) && (grid[by][bx] <= grid[me[Math.abs(idx-1)].y][me[Math.abs(idx-1)].x])){ val++; msg += 'aid ally'; }
    
    // somewhat avoid edges
    val += (function(){
        totalvalue = 0;
        tx = 0;
        miny = Math.max(my-1, 0),
        maxy = Math.min(size-1, my+1),
        minx = Math.max(mx-1, 0),
        maxx = Math.min(size-1, mx+1);
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if(grid[k][l] < '4'){
                    totalvalue += (+grid[k][l]);
                    tx++;
                }
            }
        }
        if(totalvalue === 0){ return 0; }
        return totalvalue/tx; // TRY *1.5
    })(); // */
    
    // move towards the middle
    val += Math.abs(Math.floor(size/2) - me[idx].x) - Math.abs(Math.floor(size/2) - mx);
    val += Math.abs(Math.floor(size/2) - me[idx].y) - Math.abs(Math.floor(size/2) - my);
    
    // build towards the middle
    if((Math.abs(Math.floor(size/2) - mx) - Math.abs(Math.floor(size/2) - bx) > 0)||(Math.abs(Math.floor(size/2) - my) - Math.abs(Math.floor(size/2) - by) > 0)){
        if(grid[by][bx] < '3') val++;
    }
    
    /* / move towards hidden opponents
    if((last_seen[0].x >= 0) && (enemy[0].y < 0) && (Math.abs(last_seen[0].x - mx) <= 1) && (Math.abs(last_seen[0].y - my) <= 1)){
        val += 7;
        msg += 'move t last_seen';
    } else if((estimated[0].x >= 0) && (enemy[0].y < 0) && (Math.abs(estimated[0].x - mx) <= 1) && (Math.abs(estimated[0].y - my) <= 1)){
        val += 5;
        msg += 'move t estimated';
    }
    if((last_seen[1].y >= 0) && (enemy[1].y < 0) && (Math.abs(last_seen[1].x - mx) <= 1) && (Math.abs(last_seen[1].y - my) <= 1)){
        val += 7;
        msg += 'move t last_seen';
    } else if((estimated[1].y >= 0) && (enemy[1].y < 0) && (Math.abs(estimated[1].x - mx) <= 1) && (Math.abs(estimated[1].y - my) <= 1)){
        val += 5;
        msg += 'move t estimated';
    } // */
    
    /* / move towards visible opponents
    if((enemy[0].x >= 0) && (Math.abs(estimated[0].x - mx) <= 1) && (Math.abs(estimated[0].y - my) <= 1)){
        val+=9.2;
        msg += 'move to v';
    }
    if((enemy[1].y >= 0) && (Math.abs(estimated[1].x - mx) <= 1) && (Math.abs(estimated[1].y - my) <= 1)){
        val+=9.2;
        msg += 'move to v';
    } // */
    
    /* / visible - build on level 2-3 where enemy might build-move
    if((enemy[0].x >= 0) && (Math.abs(estimated[0].x - bx) <= 1) && (Math.abs(estimated[0].y - by) <= 1) && ( (grid[by][bx] > grid[enemy[0].y][enemy[0].x]) || (grid[by][bx] == '3')) ){
        val +=3; msg += 'build v';
    }
    if((enemy[1].x >= 0) && (Math.abs(estimated[1].x - bx) <= 1) && (Math.abs(estimated[1].y - by) <= 1) && ( (grid[by][bx] > grid[enemy[1].y][enemy[1].x]) || (grid[by][bx] == '3')) ){
        val +=3; msg += 'build v';
    } // */
    
    /* / hidden - build on level 2-3 where enemy might build-move .. still deciding correct values for grid levels
    if((last_seen[0].x >= 0) && (enemy[0].y < 0) && (Math.abs(last_seen[0].x - bx) <= 1) && (Math.abs(last_seen[0].y - by) <= 1) && ( (grid[by][bx] > grid[last_seen[0].y][last_seen[0].x]) || (grid[by][bx] == '3')) && ((last_seen[0].x != bx) || (last_seen[0].y != by)) ){
        val +=3; msg += 'build on last_seen';
    } else if((estimated[0].x >= 0) && (enemy[0].y < 0) && (Math.abs(estimated[0].x - bx) <= 1) && (Math.abs(estimated[0].y - by) <= 1) && ( (grid[by][bx] > grid[estimated[0].y][estimated[0].x]) || (grid[by][bx] == '3')) && ((estimated[1].x != bx) || (estimated[1].y != by)) ){
        val ++; msg += 'build on estimated';
    }
    if((last_seen[1].y >= 0) && (enemy[1].y < 0) && (Math.abs(last_seen[1].x - bx) <= 1) && (Math.abs(last_seen[1].y - by) <= 1) && ( (grid[by][bx] > grid[last_seen[1].y][last_seen[1].x]) || (grid[by][bx] == '3')) && ((last_seen[0].x != bx) || (last_seen[0].y != by)) ){
        val +=3; msg += 'build on last_seen';
    } else if((estimated[1].y >= 0) && (enemy[1].y < 0) && (Math.abs(estimated[1].x - bx) <= 1) && (Math.abs(estimated[1].y - by) <= 1) && ( (grid[by][bx] > grid[estimated[1].y][estimated[1].x]) || (grid[by][bx] == '3')) && ((estimated[1].x != bx) || (estimated[1].y != by)) ){
        val ++; msg += 'build on estimated';
    } // */
    
    // heatmap based movement - build on level 2-3 where enemy might build-move .. still deciding correct values for grid levels
    if(hotspot.x >= 0){
        val += (Math.abs(Math.floor(hotspot.x) - me[idx].x) - Math.abs(Math.floor(hotspot.x) - mx))*9;
        val += (Math.abs(Math.floor(hotspot.y) - me[idx].y) - Math.abs(Math.floor(hotspot.y) - my))*9;
    } // */
    
    // heatmap based build - build on level 2-3 where enemy might build-move .. still deciding correct values for grid levels
    if((hotspot.x == bx)&&(hotspot.y == by)){    
        val += 20;
        msg += 'heat detector';
    } // */
    
    // don't suicide in corner
    if((grid[by][bx] >= '1')&&((+grid[by][bx]) - (+grid[my][mx]) >= 0)){
        surr = true;
        miny = Math.max(my-1, 0),
        maxy = Math.min(size-1, my+1),
        minx = Math.max(mx-1, 0),
        maxx = Math.min(size-1, mx+1);
        out:
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if( ((by != k)||(bx != l)) && ((my != k)||(mx != l)) && ((me[0].y != k)||(me[0].x != l)) && ((me[1].y != k)||(me[1].x != l)) ){
                    if( (grid[k][l] <= grid[my][mx]) || ((+grid[k][l]) - (+grid[me[idx].y][me[idx].x]) == 1) && (grid[k][l] < '4') ){
                        surr = false;
                        break out;
                    }
                }
            }
        }
        if(surr){ val = -99 + (+grid[by][bx]); msg = ' dodging '; printErr(msg); }
    }// end don't suicide in corner */
    
    // KAGE BUSHIN NO JUTSU - secret escape
    if((grid[by][bx] >= '1')&&((+grid[by][bx]) - (+grid[my][mx]) >= 0)){
        surr = true;
        miny = Math.max(my-1, 0),
        maxy = Math.min(size-1, my+1),
        minx = Math.max(mx-1, 0),
        maxx = Math.min(size-1, mx+1);
        out:
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if( ((by != k)||(bx != l)) && ((my != k)||(mx != l)) && ((me[0].y != k)||(me[0].x != l)) && ((me[1].y != k)||(me[1].x != l)) ){
                    if( (grid[k][l] <= grid[my][mx]) ){
                        surr = false;
                        break out;
                    }
                }
            }
        }
        if(surr){ val -= 99 - (+grid[by][bx]); msg = ' KAGE BUSHIN NO JUTSU '; printErr(msg); }
    }// end KAGE BUSHIN NO JUTSU - secret escape */
    
    // WIND FURY - special evasive manoeuvre 
    if((grid[by][bx] >= '1')&&((+grid[by][bx]) - (+grid[my][mx]) >= 0)){
        surr = true;
        miny = Math.max(my-1, 0),
        maxy = Math.min(size-1, my+1),
        minx = Math.max(mx-1, 0),
        maxx = Math.min(size-1, mx+1);
        out2:
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if(((by != k)||(bx != l))&&((my != k)||(mx != l))&&((me[0].y != k)||(me[0].x != l))&&((me[1].y != k)||(me[1].x != l))&&(grid[by][bx] <= '3')){
                    if((grid[k][l] <= grid[my][mx])&&(grid[my][mx] >= grid[me[idx].y][me[idx].x])){
                        surr = false;
                        break out2;
                    }
                }
            }
        }
        if(surr){ val -= 30 - (+grid[by][bx]); msg = ' WIND FURY! '; printErr(msg); }
    }// end - WIND FURY - special evasive manoeuvre */
    
    function deadenemy(dx, dy){
        miny = Math.max(dy-1, 0),
        maxy = Math.min(size-1, dy+1),
        minx = Math.max(dx-1, 0),
        maxx = Math.min(size-1, dx+1);
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if( ((me[0].y != k)||(me[0].x != l)) && ((me[1].y != k)||(me[1].x != l)) && ((enemy[1].y != k)||(enemy[1].x != l)) && ((enemy[0].y != k)||(enemy[0].x != l)) ){
                    if( (grid[k][l] <= grid[dy][dx]) || (((+grid[k][l]) - (+grid[dy][dx]) == 1) && (grid[k][l] < '4')) ){
                       return false;
                    }
                }
            }
        }
        return true;
    }
    
    // wall in enemy
    if((enemy[0].x > -1)&&(deadenemy(enemy[0].x,enemy[0].y))){}else
    if((enemy[0].x > -1)&&(Math.abs(enemy[0].x - bx) <= 1)&&(Math.abs(enemy[0].y - by) <= 1) ){
        surr = true;
        miny = Math.max(enemy[0].y-1, 0),
        maxy = Math.min(size-1, enemy[0].y+1),
        minx = Math.max(enemy[0].x-1, 0),
        maxx = Math.min(size-1, enemy[0].x+1);
        out3:
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if( ((my != k)||(mx != l)) && ((by != k)||(bx != l)) && ((me[0].y != k)||(me[0].x != l)) && ((me[1].y != k)||(me[1].x != l)) && ((enemy[1].y != k)||(enemy[1].x != l)) && ((enemy[0].y != k)||(enemy[0].x != l)) ){
                    if( (grid[k][l] <= grid[enemy[0].y][enemy[0].x]) || (((+grid[k][l]) - (+grid[enemy[0].y][enemy[0].x]) == 1) && (grid[k][l] < '4')) ){
                        surr = false;
                        break out3;
                    }
                }
            }
        }
        if(surr){ 
            val += 555; msg += ' your fate is sealed '; printErr(msg); 
            estimated[0].x=-1;
            estimated[0].y=-1;
        }
    }
    if((enemy[1].x > -1)&&(deadenemy(enemy[1].x,enemy[1].y))){}else
    if((enemy[1].x > -1)&&(Math.abs(enemy[1].x - bx) <= 1)&&(Math.abs(enemy[1].y - by) <= 1) ){ // &&((bx == me[idx].x)&&(by == me[idx].y))
        surr = true;
        miny = Math.max(enemy[1].y-1, 0),
        maxy = Math.min(size-1, enemy[1].y+1),
        minx = Math.max(enemy[1].x-1, 0),
        maxx = Math.min(size-1, enemy[1].x+1);
        out4:
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if( ((by != k)||(bx != l)) && ((me[0].y != k)||(me[0].x != l)) && ((me[1].y != k)||(me[1].x != l)) && ((enemy[1].y != k)||(enemy[1].x != l)) && ((enemy[0].y != k)||(enemy[0].x != l)) ){
                    if( (grid[k][l] <= grid[enemy[1].y][enemy[1].x]) || (((+grid[k][l]) - (+grid[enemy[1].y][enemy[1].x]) == 1) && (grid[k][l] < '4')) ){
                        surr = false;
                        break out4;
                    }
                }
            }
        }
        if(surr){ val += 555; msg += ' your fate is sealed '; printErr(msg);
            estimated[1].x=-1;
            estimated[1].y=-1;
        }
    }
    // end wall in enemy */
    
    /* / hidden - wall in enemy
    if((last_seen[0].x > -1)&&(deadenemy(last_seen[0].x,last_seen[0].y))){} else
    if((last_seen[0].x > -1)&&(Math.abs(last_seen[0].x - bx) <= 1)&&(Math.abs(last_seen[0].y - by) <= 1) ){
        surr = true;
        miny = Math.max(last_seen[0].y-1, 0),
        maxy = Math.min(size-1, last_seen[0].y+1),
        minx = Math.max(last_seen[0].x-1, 0),
        maxx = Math.min(size-1, last_seen[0].x+1);
        out35:
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if( ((by != k)||(bx != l)) && ((me[0].y != k)||(me[0].x != l)) && ((me[1].y != k)||(me[1].x != l)) && ((last_seen[1].y != k)||(last_seen[1].x != l)) && ((last_seen[0].y != k)||(last_seen[0].x != l)) ){
                    if( (grid[k][l] <= grid[last_seen[0].y][last_seen[0].x]) || (((+grid[k][l]) - (+grid[last_seen[0].y][last_seen[0].x]) == 1) && (grid[k][l] < '4')) ){
                        surr = false;
                        break out35;
                    }
                }
            }
        }
        if(surr){ val += 555; msg += ' THIS IS SUPER SAYAJIN 2 AND THIS IS FURTHER BEYOND - AAAAHHHHH '; printErr(msg); 
            last_seen[0].x=-1;
            last_seen[0].y=-1;
        }
    }
    if((last_seen[1].x > -1)&&(deadenemy(last_seen[1].x,last_seen[1].y))){} else
    if((last_seen[1].x > -1)&&(Math.abs(last_seen[1].x - bx) <= 1)&&(Math.abs(last_seen[1].y - by) <= 1) ){ 
        surr = true;
        miny = Math.max(last_seen[1].y-1, 0),
        maxy = Math.min(size-1, last_seen[1].y+1),
        minx = Math.max(last_seen[1].x-1, 0),
        maxx = Math.min(size-1, last_seen[1].x+1);
        out45:
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if( ((by != k)||(bx != l)) && ((me[0].y != k)||(me[0].x != l)) && ((me[1].y != k)||(me[1].x != l)) && ((last_seen[1].y != k)||(last_seen[1].x != l)) && ((last_seen[0].y != k)||(last_seen[0].x != l)) ){
                    if( (grid[k][l] <= grid[last_seen[1].y][enemy[1].x]) || (((+grid[k][l]) - (+grid[last_seen[1].y][last_seen[1].x]) == 1) && (grid[k][l] < '4')) ){
                        surr = false;
                        break out45;
                    }
                }
            }
        }
        if(surr){ val += 555; msg += ' THIS IS SUPER SAYAJIN 2 AND THIS IS FURTHER BEYOND - AAAAHHHHH '; printErr(msg);
            last_seen[1].x=-1;
            last_seen[1].y=-1;
        }
    }
    // hidden - end wall in enemy */

    // ERUPTING BURNING FINGER - unique move
    if((enemy[0].x > -1)&&(Math.abs(enemy[0].x - bx) <= 1)&&(Math.abs(enemy[0].y - by) <= 1)&&(bx == me[idx].x)&&(by == me[idx].y)){
        surr = true;
        miny = Math.max(enemy[0].y-1, 0),
        maxy = Math.min(size-1, enemy[0].y+1),
        minx = Math.max(enemy[0].x-1,
 0),
        maxx = Math.min(size-1,
 enemy[0].x+1);
        out5:
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if((by != k)||(bx != l)){
                    if(grid[k][l] <= grid[enemy[0].y][enemy[0].x]){
                        surr = false;
                        break out5;
                    }
                }
            }
        }
        if(surr){ val = 55; msg = ' ERUPTING BURNING FINGER '; printErr(msg); }
    }
    if((enemy[1].x > -1)&&(Math.abs(enemy[1].x - bx) <= 1)&&(Math.abs(enemy[1].y - by) <= 1)&&(bx == me[idx].x)&&(by == me[idx].y)){
        surr = true;
        miny = Math.max(enemy[1].y-1, 0),
        maxy = Math.min(size-1, enemy[1].y+1),
        minx = Math.max(enemy[1].x-1, 0),
        maxx = Math.min(size-1, enemy[1].x+1);
        out6:
        for(var k = miny; k <= maxy; k++){
            for(var
 l = minx; l <= maxx; l++){
                if((by != k)||(bx != l)){
                    if(grid[k][l] <= grid[enemy[1].y][enemy[1].x]){
                        surr = false;
                        break out6;
                    }
                }
            }
        }
        if(surr){ val = 55; msg = ' ERUPTING BURNING FINGER '; printErr(msg); }
    }
    // end - ERUPTING BURNING FINGER - unique move */
    
    // don't wall in ally
    var ally = Math.abs(idx-1);
    if((((+grid[by][bx]) - (+grid[me[ally].y][me[ally].x])) == 1)&&(Math.abs(me[ally].x - bx) <= 1)&&(Math.abs(me[ally].y - by) <= 1)){
        surr = true;
        miny = Math.max(me[ally].y-1, 0),
        maxy = Math.min(size-1, me[ally].y+1),
        minx = Math.max(me[ally].x-1, 0),
        maxx = Math.min(size-1, me[ally].x+1);
        out7:
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if(((by != k)||(bx != l))&&((me[ally].y != k)||(me[ally].x != l))&&((enemy[0].y != k)||(enemy[0].x != l))&&((enemy[1].y != k)||(enemy[1].x != l))){
                    if( (grid[k][l] <= grid[me[ally].y][me[ally].x]) || ((+grid[k][l]) - (+grid[me[ally].y][me[ally].x]) == 1) && (grid[k][l] < '4') ){
                        surr = false;
                        break out7;
                    }
                }
            }
        }
        if(surr){ val = -99 - (+grid[by][bx]); msg = ' what kind of game is this? '; printErr(msg); }
    }
    // end don't wall in ally
    
    /* /+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    // + special moves when enemy nearby to avoid getting pushed into a corner
    // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    if((estimated[0].x > -1)&&(Math.abs(estimated[0].x - bx) <= 1)&&(Math.abs(estimated[0].y - by) <= 1) ){
    val -= (function(){
        totalvalue = 0;
        tx = 0;
        miny = Math.max(my-1, 0),
        maxy = Math.min(size-1, my+1),
        minx = Math.max(mx-1, 0),
        maxx = Math.min(size-1, mx+1);
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if((+grid[k][l]) < (+grid[my][mx])){
                    totalvalue+= (+grid[my][mx]) - (+grid[k][l]);
                    tx++;
                }
            }
        }
        msg += 'special move' + totalvalue + '< total'; 
        if(totalvalue === 0){ return 0; }
        return totalvalue/tx;
    })(); }
    if((estimated[1].x > -1)&&(Math.abs(estimated[1].x - bx) <= 1)&&(Math.abs(estimated[1].y - by) <= 1) ){
    val -= (function(){
        totalvalue = 0;
        tx = 0;
        miny = Math.max(my-1, 0),
        maxy = Math.min(size-1, my+1),
        minx = Math.max(mx-1, 0),
        maxx = Math.min(size-1, mx+1);
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if((+grid[k][l]) < (+grid[my][mx])){
                    totalvalue+= (+grid[my][mx]) - (+grid[k][l]);
                    tx++
                }
            }
        }
        msg += 'special move' + totalvalue + '< total'; 
        if(totalvalue === 0){ return 0; }
        return totalvalue/tx;
    })(); }// */
    
    //+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    // END + special moves when enemy nearby to avoid getting pushed into a corner
    // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    
    // detect push and build there, if grid > 2
    if( ((last_pos[idx].x != me[idx].x)||(last_pos[idx].y != me[idx].y)) ){
        if( (grid[by][bx] == '3') && (bx == last_pos[idx].x) && (by == last_pos[idx].y) && (grid[me[idx].y][me[idx].x] <= '1') ){
            msg = 'BANKAI! - Build, Scrubymaru!';
            val += 99; // TRY 9
        } else if( (mx == last_pos[idx].x) && (my == last_pos[idx].y) ){
            msg = 'did you just push me???';
            // val ++; // not really a good move it seems
        } else if( (mx == last_pos[idx].x) && (my == last_pos[idx].y) && ( (Math.abs(estimated[0].x - last_pos[idx].x) <= 1) || (Math.abs(estimated[0].y - last_pos[idx].y) <= 1) || (Math.abs(estimated[1].x - last_pos[idx].x) <= 1) || (Math.abs(estimated[1].y - last_pos[idx].y) <= 1) ) ){
            msg += 'did you just push me???';
            // val ++; // not really a good move it seems
        }
    } // end detect
    
} // end move
        
        
// searching favorable pushes
if(atype == 'PUSH&BUILD'){
    // push is usually better
    val+=9; // 9 best value so far // TRY 15
    // add scale value of the push by how much you drop the enemy
    val += (parseInt(grid[my][mx]) - parseInt(grid[by][bx]));
    // bonus value if it's from higher position
    val += parseInt(grid[my][mx]);
    // push enemy from 2 to 3 to avoid point gain
    if((grid[my][mx] >= '2')&&(grid[by][bx] == '3')){ val+=3; }
    // don't aid enemy
    if((grid[my][mx] < '2')&&(grid[by][bx] >= '1')){ val--; }
    // try to push off the cliff
    if(parseInt(grid[by][bx]) - parseInt(grid[my][mx]) < 0) {
        val+=9; 
        msg = 'get rekt';
    }
    // remove from good spot
    if((grid[by][bx] <= '1')&&(grid[my][mx] >= '2')) {  val+=9; }
    // push away enemy to prevent disturbing
    if((seek > 0)&&(parseInt(grid[my][mx]) < seek)) { val+=5; }
    
    // don't push enemy next to ally if possible
    (function(){
        var too_close = false;
        a = Math.abs(idx-1);
        miny = Math.max(by-1, 0),
        maxy = Math.min(size-1, by+1),
        minx = Math.max(bx-1, 0),
        maxx = Math.min(size-1, bx+1);
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if( ((k == me[a].y)&&(l == me[a].x)) ){
                    too_close = true;
                }//
            }
        }
        if(too_close){ val -=5; } else { val ++; }
    })(); // */
    
    // don't push enemy into the other enemy
    var p = 0;
    if((mx === enemy[0].x)&&(my === enemy[0].y)){ 
        p = 1;
    } else { p = 0; }
    if(estimated[p].x > 0){
    (function(){
        var too_close = false;
        if( ((by == estimated[p].y)&&(bx == estimated[p].x)) ){
                too_close = true;
        }
        if(too_close){ val -= 200; } else { val ++; }
    })(); }// */
    
    // push enemy more towards edges
    val += (Math.abs(Math.floor(size/2) - bx) - Math.abs(Math.floor(size/2) - mx))*2.2;
    val += (Math.abs(Math.floor(size/2) - by) - Math.abs(Math.floor(size/2) - my))*2.2;
    
    // push enemy into a corner
    if((grid[my][mx] >= '1')&&(((+grid[my][mx]) - (+grid[by][bx])) < 2)){
        surr = true;
        miny = Math.max(by-1, 0),
        maxy = Math.min(size-1, by+1),
        minx = Math.max(bx-1, 0),
        maxx = Math.min(size-1, bx+1);
        out8:
        for(var k = miny; k <= maxy; k++){
            for(var l = minx; l <= maxx; l++){
                if(((by != k)||(bx != l))&&((my != k)||(mx != l))){
                    if(grid[k][l] <= grid[by][bx]){
                        surr = false;
                        break out8;
                    }
                }
            }
        }
        if(surr){ val = 55; msg = ' are you dead yet? '; printErr(msg); }
    }// end push enemy into a corner
    
} // end push
        
        // moves.unshift
        printErr('move ' + atype + ' ' + i + ' O' + idx + ' ' + inputs[2] + ' ' + inputs[3] + ' ' + val);
        
        // make sure some move gets used
        if(n < 0){
            n = i;
            maxval = val;
        }
        
        // vars to counter move repetition
        var tempval = maxval;
        var tempn = n;
        if(val > maxval){
            n = i;
            maxval = val;
        }
        
        moves.push(new move(atype, idx, inputs[2], inputs[3], val, msg));
        
        // avoid move repetition
        if(JSON.stringify(last_move) === JSON.stringify(moves[i])&&(loop_level >= 3)){
            moves[i].v -= loop_level;
            last_move.v = moves[i].v;
            maxval = tempval;
            n = tempn;
        }
    }
    
    // record move to check for loop later
    if(JSON.stringify(last_move) === JSON.stringify(moves[n])){
        loop_level ++;
    } else {
        last_move = new move(moves[n].a, moves[n].id, moves[n].dir, moves[n].dir2, moves[n].v, moves[n].msg);
        loop_level = 0;
    }
    
    
    // store last builder and enemy positions
    // apply directions
    if(moves[n].a == 'MOVE&BUILD'){
        last_pos = JSON.parse(JSON.stringify(me));
        last_pos[moves[n].id].x = dirr[moves[n].dir][0] + me[idx].x,
        last_pos[moves[n].id].y = dirr[moves[n].dir][1] + me[idx].y;
    } else if(moves[n].a == 'PUSH&BUILD'){
        
    }
    // apply push directions
    if(moves[n].a == 'PUSH&BUILD'){
        en = 0;
        if((mx == enemy[0].x)&&(my == enemy[0].y)){ } else { en++; }
        last_seen[en].x = dirr[moves[n].dir2][0] + enemy[en].x,
        last_seen[en].y = dirr[moves[n].dir2][1] + enemy[en].y;
    }
    
    
    // movement randomizer
    moves.sort(function(a, b){ return b.v-a.v; });    
    moves = moves.filter(function(mo) { return mo.v == moves[0].v; });    
    n = Math.floor(Math.random() * moves.length);
    
    
    printErr(n + ' ' + moves[n].a + ' ' + moves[n].id + ' ' + moves[n].dir + ' ' + moves[n].dir2 + ' ' + moves[n].v);
    print(moves[n].a + ' ' + moves[n].id + ' ' + moves[n].dir + ' ' + moves[n].dir2 + ' ' + moves[n].msg);
    
}
 
// 25.28
// rank 60