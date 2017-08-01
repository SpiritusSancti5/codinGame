n=int(input())

ok=True

for i in range(n):
    if pow(i,n,n)!=i:
        ok=False
        break
if ok:
    for i in range(2,n):
        if n%i==0:
            ok=False
            break
    if ok:
        print("NO")
    else:
        print("YES")
else:
    print("NO")