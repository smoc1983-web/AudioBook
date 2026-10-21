package com.audiobookdownloader;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Draws the cylindrical mesh with the real page texture and a translucent paper reverse. */
public final class PageCurlRenderer {
    private final CylinderPageMesh mesh=new CylinderPageMesh();
    private final Paint texture=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint reverse=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline=new Path();
    public void draw(Canvas canvas,Bitmap bitmap,float width,float height,float progress,float touchY,float dragY,int paperRgb,boolean forward){
        mesh.update(width,height,progress,touchY,dragY,paperRgb);
        int saved=canvas.save();
        if(!forward){canvas.translate(width,0);canvas.scale(-1,1);}
        try{
            outline.rewind();
            outline.moveTo(mesh.vertices[0],mesh.vertices[1]);
            for(int x=1;x<=CylinderPageMesh.COLUMNS;x++)point(x);
            for(int y=1;y<=CylinderPageMesh.ROWS;y++)point(y*(CylinderPageMesh.COLUMNS+1)+CylinderPageMesh.COLUMNS);
            for(int x=CylinderPageMesh.COLUMNS-1;x>=0;x--)point(CylinderPageMesh.ROWS*(CylinderPageMesh.COLUMNS+1)+x);
            for(int y=CylinderPageMesh.ROWS-1;y>0;y--)point(y*(CylinderPageMesh.COLUMNS+1));
            outline.close();
            shadow.setColor(0x01000000);shadow.setShadowLayer(width*.035f,width*.008f,height*.004f,0x70000000);
            canvas.drawPath(outline,shadow);
            canvas.drawBitmapMesh(bitmap,CylinderPageMesh.COLUMNS,CylinderPageMesh.ROWS,mesh.vertices,0,mesh.lighting,0,texture);
            // The same tessellation gives paper thickness/lighting without allocating a second texture.
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES,mesh.vertices.length,mesh.vertices,0,null,0,
                mesh.paper,0,mesh.triangles,0,mesh.triangles.length,reverse);
        }finally{canvas.restoreToCount(saved);}
    }
    private void point(int index){outline.lineTo(mesh.vertices[index*2],mesh.vertices[index*2+1]);}
}
