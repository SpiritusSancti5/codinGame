var map = [];

var factoryCount = parseInt(readline()); // the number of factories
var linkCount = parseInt(readline()); // the number of links between factories
for (var i = 0; i < linkCount; i++) {
    var inputs = readline().split(' ');
    var factory1 = parseInt(inputs[0]);
    var factory2 = parseInt(inputs[1]);
    var distance = parseInt(inputs[2]);
    
    map.push([factory1,factory2,distance]);
    map.push([factory2,factory1,distance]);
}

// game loop
while (true) {
    var entityCount = parseInt(readline()); // the number of entities (e.g. factories and troops)
    
    var TT = [],
        FF = [];
        
        TT.length = 0;
        FF.length = 0;
        
        var newMove = 'WAIT';
    
    for (var i = 0; i < entityCount; i++) {
        var inputs = readline().split(' ');
        var entityId = parseInt(inputs[0]);
        var entityType = inputs[1];
        var arg1 = parseInt(inputs[2]);
        var arg2 = parseInt(inputs[3]);
        var arg3 = parseInt(inputs[4]);
        var arg4 = parseInt(inputs[5]);
        var arg5 = parseInt(inputs[6]);
        
        if(entityType === 'FACTORY'){
            FF.push([arg1,arg2,arg3]);
        } else {
         //   TT.push([arg2,arg3,arg4,arg5]);
        }
    }    

    // order all factories
    for(var i=0;i<FF.length;i++){
        if(FF[i][0] === 1){
            checkDestination(i);
        }
    }
    
    print(newMove);
}

function checkDestination(x){
    // check neighbours
    var e = [];
    var z = [];
    var a = [];
    
    for(let i=0;i<map.length;i++){
        if(map[i][0] === x){
            if((FF[map[i][1]][0] === -1)&&(FF[map[i][1]][1] < FF[x][1])){
                e.push(map[i][1]);
            } else if(FF[map[i][1]][0] === 0){
                z.push(map[i][1]);
            } else if(FF[map[i][1]][0]=== 1){
                a.push(map[i][1]);
            }
        }
    }
    
    
    for(let i=0; i < z.length; i++){
       // best = z[Math.floor(Math.random() * (z.length))];
        move(x,z[i],FF[z[i]][1]+1);
    }
    for(let i=0; i < e.length; i++){
        // best = z[Math.floor(Math.random() * (z.length))];
        move(x,e[i],FF[e[i]][1]+1);
    }
    if((e.length <= 0)&&(z.length <= 0)) {
        for(let i=0; i < a.length; i++){
            move(x,a[i],2);
        }
    } else print('WAIT');
}

function move(i, des, m){
    if(newMove.length > 0) newMove += ';';
    return newMove += 'MOVE' + ' ' + i + ' ' + des + ' ' + m;
}