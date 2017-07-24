
var R = [];

var n = 0;

var L = parseInt(readline());
var N = parseInt(readline());
for (var i = 0; i < N; i++) {
    R.push(readline().split(" "));
    printErr(R[i].join(" "));
}

for (var i = 0; i < N; i++) {
    printErr(R[i].join(" "));
    if(i > 0 && R[i-1][0] === R[i][0]){
        if( (R[i][1] - R[i-1][1])/(R[i][2] - R[i-1][2]) > L / 3600){
            n++;
            print(R[i][0]+' ' + R[i][1]);
        }        
    }
}

if(n === 0) print('OK');


