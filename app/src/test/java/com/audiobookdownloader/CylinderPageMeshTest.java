package com.audiobookdownloader;

import org.junit.Test;
import static org.junit.Assert.*;

public class CylinderPageMeshTest {
    private final CylinderPageMesh mesh=new CylinderPageMesh();
    @Test public void untouchedPageIsAnExactRectangle(){
        mesh.update(400,800,0,.2f,0,0xffddeeff);
        for(int y=0;y<=CylinderPageMesh.ROWS;y++)for(int x=0;x<=CylinderPageMesh.COLUMNS;x++){
            int i=y*(CylinderPageMesh.COLUMNS+1)+x;
            assertEquals(400f*x/CylinderPageMesh.COLUMNS,mesh.vertices[i*2],.0001f);
            assertEquals(800f*y/CylinderPageMesh.ROWS,mesh.vertices[i*2+1],.0001f);
            assertEquals(0,mesh.paper[i]>>>24);
        }
    }
    @Test public void completedTurnMovesTheEntireOldPageOutside(){
        mesh.update(400,800,1,.8f,0,0xffddeeff);
        for(int i=0;i<mesh.vertices.length;i+=2)assertTrue(mesh.vertices[i]<=.001f);
    }
    @Test public void cylindricalBendCompressesAndReversesTheRealTexture(){
        mesh.update(400,800,.3f,.5f,0,0xffddeeff);
        boolean forward=false,reversed=false,curved=false,back=false;
        int row=24*(CylinderPageMesh.COLUMNS+1);
        for(int x=0;x<CylinderPageMesh.COLUMNS;x++){
            float step=mesh.vertices[(row+x+1)*2]-mesh.vertices[(row+x)*2];
            forward|=step>6;reversed|=step< -6;curved|=Math.abs(step)<3;
            back|=(mesh.paper[row+x]>>>24)>150;
        }
        assertTrue(forward&&reversed&&curved&&back);
    }
    @Test public void touchHeightChangesDiagonalCurl(){
        mesh.update(400,800,.3f,.15f,0,0xffddeeff);float top=mesh.vertices[128];
        mesh.update(400,800,.3f,.85f,0,0xffddeeff);assertTrue(Math.abs(top-mesh.vertices[128])>10);
    }
    @Test public void resizeAndInvalidInputNeverProduceNanCoordinates(){
        for(float p:new float[]{-1,0,.01f,.5f,.99f,1,2,Float.NaN}){
            mesh.update(1,1,p,Float.NaN,Float.POSITIVE_INFINITY,0xffeeeeee);
            for(float v:mesh.vertices)assertTrue(!Float.isNaN(v)&&!Float.isInfinite(v));
        }
    }
    @Test public void reverseFaceRendersAfterFrontWithoutDroppingTriangles(){
        mesh.update(400,800,0,.5f,0,0xffeeeeee);
        assertEquals(mesh.triangles.length,mesh.frontCount);assertEquals(0,mesh.backCount);
        mesh.update(400,800,.3f,.2f,0,0xffeeeeee);
        assertTrue(mesh.frontCount>0&&mesh.backCount>0);
        assertEquals(mesh.triangles.length,mesh.frontCount+mesh.backCount);
        mesh.update(400,800,1,.5f,0,0xffeeeeee);
        assertEquals(0,mesh.frontCount);assertEquals(mesh.triangles.length,mesh.backCount);
    }
    @Test public void meshBuffersAreReusedAndTrianglesStayInBounds(){
        float[] first=mesh.vertices;mesh.update(100,100,.4f,.2f,.1f,0xffeeeeee);assertSame(first,mesh.vertices);
        for(short i:mesh.triangles)assertTrue(i>=0&&i<mesh.lighting.length);
    }
}
