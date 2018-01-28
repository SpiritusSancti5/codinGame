// quickstart code
var boxCount = parseInt(readline());
var outputz = [];
var volumez = Array(100).fill(0);
var j = 0;

for (var i = 0; i < boxCount; i++) {
    var inputs = readline().split(' ');
    var weight = parseFloat(inputs[0]);
    var volume = parseFloat(inputs[1]);

    if( volumez[j] < (100 - volume) ) {
        outputz[i] = j;
        volumez[j] += volume;
    } else {
        j++;
        outputz[i] = j;
        volumez[j] += volume;
    }
}

print(outputz.join(' '));
