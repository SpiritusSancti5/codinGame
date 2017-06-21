var ENCRYPT = readline();

if(/[^0\s]/.test(ENCRYPT)){
    print('INVALID');
} else {

var a = ENCRYPT.split(' ');
var binary = '';
var chars = '';
var BS = false;

if(a.length % 2 !== 0) {
    BS = true;
} else {
    for(var i = 0; i < a.length; i+=2){
        n = 2 - a[i].length;
        if((a[i+1])&&(a[i].length <= 2)){
           binary += n.toString().repeat(a[i+1].length);
        } else {
           BS = true;
        }
    }
}

if(binary.length % 7 != 0) BS = true;

if(/[^01]/.test(binary)) BS = true;


if(!BS){
    while(binary.length > 2){
        chars += String.fromCharCode(parseInt(binary.substr(0,7), 2));
        binary = binary.substr(7,binary.length);
    }
    
    print(chars);
} else {
    print('INVALID');
}

}