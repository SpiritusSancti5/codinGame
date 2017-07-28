// a bit messy and redundant i know ...

var expression = readline();

var arr = expression.split(/(\+|\*|\/|\-)/);

for(i in arr){ if(arr[i] == '') arr.splice(i, 1); }
for(i in arr){ 
    if(arr[i] == '-' && isNaN(arr[i-1])) { 
        x = parseInt(i)
        arr[i] = arr[x+1] * -1;
        arr.splice(x+1, 1);
    }
}

while(arr.length > 1){
    while(arr.indexOf('+') > 0){
        x = arr.indexOf('+');
        
        arr[x-1] = parseInt(arr[x-1]) + parseInt(arr[x+1]);
        arr.splice(x, 2);
    }
    while(arr.indexOf('/') > 0){
        x = arr.indexOf('/');
        
        arr[x-1] = parseInt(arr[x-1]) / parseInt(arr[x+1]);
        arr.splice(x, 2);
    }
    while(arr.indexOf('-') > 0){
        x = arr.indexOf('-');
        
        arr[x-1] = parseInt(arr[x-1]) - parseInt(arr[x+1]);
        arr.splice(x, 2);
    }
    while(arr.indexOf('*') > 0){
        x = arr.indexOf('*');
        
        arr[x-1] = parseInt(arr[x-1]) * parseInt(arr[x+1]);
        arr.splice(x, 2);
    }
}


print(arr);