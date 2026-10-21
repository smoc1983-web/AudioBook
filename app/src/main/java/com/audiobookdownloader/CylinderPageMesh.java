package com.audiobookdownloader;

/**
 * Orthographic cylindrical paper, inspired by Harri Smatt's android-pagecurl
 * (Apache-2.0). New grid implementation: no OpenGL view or upstream demo assets.
 * Coordinates and buffers are reused; the only bitmap belongs to one transition.
 */
public final class CylinderPageMesh {
    public static final int COLUMNS=64, ROWS=48;
    public final float[] vertices=new float[(COLUMNS+1)*(ROWS+1)*2];
    public final int[] lighting=new int[(COLUMNS+1)*(ROWS+1)];
    public final int[] paper=new int[(COLUMNS+1)*(ROWS+1)];
    public final short[] triangles=new short[COLUMNS*ROWS*6];
    public CylinderPageMesh(){
        int k=0;
        for(int y=0;y<ROWS;y++)for(int x=0;x<COLUMNS;x++){
            int a=y*(COLUMNS+1)+x,b=a+1,c=a+COLUMNS+1,d=c+1;
            triangles[k++]=(short)a;triangles[k++]=(short)b;triangles[k++]=(short)c;
            triangles[k++]=(short)b;triangles[k++]=(short)d;triangles[k++]=(short)c;
        }
    }
    private static boolean finite(float v){return !Float.isNaN(v)&&!Float.isInfinite(v);}
    /** progress: 0 = flat, 1 = completely outside viewport. touchY is normalized. */
    public void update(float width,float height,float progress,float touchY,float dragY,int paperRgb){
        if(!finite(width)||!finite(height)||width<=0||height<=0)throw new IllegalArgumentException("Page size");
        float p=finite(progress)?Math.max(0,Math.min(1,progress)):0;
        float anchor=finite(touchY)?Math.max(0,Math.min(1,touchY)):.5f;
        float dy=finite(dragY)?Math.max(-.3f,Math.min(.3f,dragY)):0;
        double envelope=Math.sin(Math.PI*p);
        double angle=((anchor-.5)*.8+dy*.9)*envelope;
        double nx=Math.cos(angle),ny=Math.sin(angle);
        double radius=width*.085*Math.sqrt(Math.max(0,envelope));
        double arc=Math.PI*radius;
        double originX=width-(2*width*p+arc)/(2*nx),originY=height*anchor;
        int red=(paperRgb>>16)&255,green=(paperRgb>>8)&255,blue=paperRgb&255;
        for(int row=0;row<=ROWS;row++)for(int column=0;column<=COLUMNS;column++){
            int i=row*(COLUMNS+1)+column;
            double x=width*column/(double)COLUMNS,y=height*row/(double)ROWS;
            double u=(x-originX)*nx+(y-originY)*ny,mapped=u,theta=0;
            if(u>0){
                if(radius>.0001&&u<arc){theta=u/radius;mapped=radius*Math.sin(theta);}
                else{theta=Math.PI;mapped=arc-u;}
            }
            double delta=mapped-u;
            vertices[i*2]=(float)(x+nx*delta);
            vertices[i*2+1]=(float)(y+ny*delta);
            // Diffuse lighting makes the round portion visibly roll, not just reflect.
            double light=theta==0?1:.60+.37*Math.abs(Math.cos(theta))+.03*Math.sin(theta);
            int shade=(int)Math.round(255*light);lighting[i]=0xff000000|(shade<<16)|(shade<<8)|shade;
            double back=Math.max(0,Math.min(1,(theta-Math.PI*.45)/(Math.PI*.12)));
            int alpha=(int)Math.round(205*back);
            int r=(int)(red*light),g=(int)(green*light),b=(int)(blue*light);
            paper[i]=(alpha<<24)|(r<<16)|(g<<8)|b;
        }
    }
}
