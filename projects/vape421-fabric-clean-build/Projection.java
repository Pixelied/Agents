package gg.vape.fabric;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** The legacy GLU projection formula, implemented without bundling LWJGL2. */
public final class Projection {
    private Projection() {}
    public static boolean project(double x, double y, double z, FloatBuffer model,
                                  FloatBuffer projection, IntBuffer viewport, FloatBuffer result) {
        double[] eye = mul(model, new double[] {x,y,z,1});
        double[] clip = mul(projection, eye);
        if (clip[3] == 0 || !Double.isFinite(clip[3])) return false;
        int v = viewport.position(), r = result.position();
        result.put(r, (float)(viewport.get(v) + (clip[0]/clip[3]+1)*viewport.get(v+2)*0.5));
        result.put(r+1, (float)(viewport.get(v+1) + (clip[1]/clip[3]+1)*viewport.get(v+3)*0.5));
        result.put(r+2, (float)((clip[2]/clip[3]+1)*0.5));
        return true;
    }
    private static double[] mul(FloatBuffer matrix, double[] v) {
        int p=matrix.position();
        double[] out=new double[4];
        for(int row=0; row<4; row++)
            for(int col=0; col<4; col++) out[row]+=matrix.get(p+4*col+row)*v[col];
        return out;
    }
}
