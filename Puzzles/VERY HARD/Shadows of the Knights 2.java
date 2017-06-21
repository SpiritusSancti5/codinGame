import java.util.*;
import java.io.*;
import java.math.*;

/**
 * Auto-generated code below aims at helping you parse
 * the standard input according to the problem statement.
 **/
 
class Window{
    boolean bomb;
    int x;
    int y;
    
    public Window(int x, int y){
        this.x=x;
        this.y=y;
        bomb = true;
    }
    
    public boolean bomb(){
        return bomb;
    }
    
    public int getX(){
        return x;
    }
    public int getY(){
        return y;
    }
    
    public double getDist(int x, int y){
        double dist = (x-this.x)*(x-this.x) + (y-this.y)*(y-this.y);
        return Math.sqrt(dist);
    }
    
    public void unbomb(){
        bomb = false;
    }
    
    public void check(int prevX, int prevY, int x, int y, String output){
        if(bomb){
            double prevDist = getDist(prevX, prevY);
            double curDist = getDist(x, y);
            if(output.equals("COLDER") && prevDist>=curDist){
                bomb = false;
                return;
            }
            if(output.equals("WARMER") &&  prevDist<=curDist){
                bomb = false;
                return;
            }
            if(output.equals("SAME") &&  prevDist!=curDist){
                bomb = false;
                return;
            }
        }
    }
}

class Batman{
    int x;
    int y;
    int px;
    int py;
    
    public Batman(int x, int y){
        this.x=x;
        this.y=y;
    }
    
    public void jumpTo(int x, int y){
        px = this.x;
        py = this.y;
        this.x = x;
        this.y = y;
    }
    
    public int getX(){
        return x;
    }
    public int getY(){
        return y;
    }
    public int getPrevX(){
        return px;
    }
    public int getPrevY(){
        return py;
    }
}

class Building{
    Batman batman;
    Window[][] windows;
    boolean flipper;
    boolean superWindows;
    int swSize;
    
    public Building(int bx, int by, int w, int h){
        batman = new Batman(bx, by);
        //I'm sure there's something wrwong with the way the "superwindows" are handled but it works
        if(w<=1000 && h<=1000){
            superWindows=false;
            windows = new Window[w][h];
            for(int x = 0; x<windows.length; x++){
                for(int y = 0; y<windows[x].length; y++){
                    windows[x][y] = new Window(x,y);
                }
            }
        }else{
            System.err.println("Building is too big, using superwindows");
            superWindows=true;
            swSize = 400;
            windows = new Window[w/swSize][h/swSize];
            for(int x = 0; x<windows.length; x++){
                for(int y = 0; y<windows[x].length; y++){
                    windows[x][y] = new Window(x*swSize,y*swSize);
                }
            }
        }
        flipper = true;
    }
    
    public void updateWindows(String output){
        if(!superWindows) windows[batman.getX()][batman.getY()].unbomb();
        if(output.equals("UNKNOWN")) return;
        for(int x = 0; x<windows.length; x++){
            for(int y = 0; y<windows[x].length; y++){
                //if(windows[x][y] == null) windows[x][y] = new Window(x,y);
                windows[x][y].check(batman.getPrevX(), batman.getPrevY(), batman.getX(), batman.getY(), output);
            }
        }
    }
    
    public int[] getAverageCoord(){
        int count = 0;
        float averageX = 0;
        float averageY = 0;
        for(int x = 0; x<windows.length; x++){
            for(int y = 0; y<windows[x].length; y++){
                if(windows[x][y].bomb()){
                    count++;
                    averageX += windows[x][y].getX();
                    averageY += windows[x][y].getY();
                }
            }
        }
        averageX /= count;
        averageY /= count;
        if(!superWindows) System.err.println("Potential windows left " + count);
        else System.err.println("Potential windows left " + count*swSize*swSize);
        if(count == 1 && superWindows){
            System.err.println("Converting down to regular windows");
            superWindows = false;
            //find the right window
            Window lastWindow = new Window(0, 0);
            for(int x = 0; x<windows.length; x++){
                for(int y = 0; y<windows[x].length; y++){
                    if(windows[x][y].bomb()){
                        lastWindow = windows[x][y];
                        break;
                    }
                }
            }
            windows = new Window[swSize][swSize];
            for(int x = 0; x<windows.length; x++){
                for(int y = 0; y<windows[x].length; y++){
                    windows[x][y] = new Window(lastWindow.getX()+x, lastWindow.getY()+y);
                }
            }
        }
        return new int[]{Math.round(averageX), Math.round(averageY)};
    }
    
    public int[] getExtremeCoord(){
        int[] coord = new int[2];
        if(flipper){
            for(int x = 0; x<windows.length; x++){
                for(int y = 0; y<windows[x].length; y++){
                    if(windows[x][y].bomb()){
                        coord[0]=x;
                        coord[1]=y;
                        flipper = !flipper;
                        return coord;
                    }
                }
            }
        }else{
            for(int x = windows.length-1; x>=0; x--){
                for(int y = windows[x].length-1; y>=0; y--){
                    if(windows[x][y].bomb()){
                        coord[0]=x;
                        coord[1]=y;
                        flipper = !flipper;
                        return coord;
                    }
                }
            }
        }
        return coord;
    }
    
    public boolean badCoord(int[] coord){
        int x = coord[0];
        int y = coord[1];
        return x<0 || x>=windows.length || y<0 || y >= windows[0].length || (x==batman.getX() && y == batman.getY());
    }
    
    
    public int[] rejigger(int[] jumpCoord){
        System.err.println("Rejiggering" + jumpCoord[0] + " " + jumpCoord[1]);
        int[] coord = new int[]{jumpCoord[0], jumpCoord[1]};
        for(int y = Math.max(0, coord[1]-1); y<coord[1]+2 || y<windows[0].length; y++){
            for(int x = Math.max(0, coord[0]-1); x<coord[0]+2 || x<windows.length; x++){
                //System.err.println("trying " + x + " " + y);
                if(superWindows){
                    if(windows[x/swSize][y/swSize].bomb() && !badCoord(new int[]{x, y})){
                        System.err.println("to " + x + " " + y);
                        return new int[]{x, y};
                    }
                }else{
                    if(!badCoord(new int[]{x, y}) && windows[x][y].bomb()){
                        System.err.println("to " + x + " " + y);
                        return new int[]{x, y};
                    }
                }
            }
        }
        return coord;
    }
    
    
    public String solve(){
        int[] coord = getAverageCoord();
        //int[] coord = getExtremeCoord();
        if(badCoord(coord))
            coord = rejigger(coord);
        batman.jumpTo(coord[0], coord[1]);
        if(!superWindows) windows[coord[0]][coord[1]].unbomb();
        return coord[0] + " " + coord[1];
    }
    
    @Override
    public String toString(){
        String r = "";
        for(int y = 0; y<windows[0].length; y++){
            for(int x = 0; x<windows.length; x++){
                if(batman.getX() == x && batman.getY() == y) r+= 'B';
                else if(windows[x][y].bomb()) r +='O';
                else r+= '.';
            }
            r += '\n';
        }
        return r;
    }
    
}

class Player {

    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        int W = in.nextInt(); // width of the building.
        int H = in.nextInt(); // height of the building.
        int N = in.nextInt(); // maximum number of turns before game over.
        int X0 = in.nextInt();
        int Y0 = in.nextInt();
        System.err.println("Size: "+ W + " " + H);
        Building building = new Building(X0, Y0, W, H);
        System.err.println("Building initialized");
        // game loop
        while (true) {
            String BOMBDIST = in.next(); // Current distance to the bomb compared to previous distance (COLDER, WARMER, SAME or UNKNOWN)
            System.err.println(BOMBDIST);
            building.updateWindows(BOMBDIST);
            System.err.println("Windows updated");
            // Write an action using System.out.println()
            // To debug: System.err.println("Debug messages...");
            //System.err.println(building);
            System.out.println(building.solve());
        }
    }
}