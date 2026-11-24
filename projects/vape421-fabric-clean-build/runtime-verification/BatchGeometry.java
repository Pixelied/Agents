package dev.renderprobe.ci;

import java.nio.*;
import java.util.*;

/** CPU-side snapshot of triangle-list batches in the original 32-float GUI ABI.
 * Offsets are rebased per part: no unused builder storage, raw GL handles or
 * mutable source arrays survive the capture. The renderer owns GPU resources. */
public final class BatchGeometry {
    public static final int STRIDE_FLOATS=32, STRIDE_BYTES=128;
    public record Part(float[] data,int vertexCount,int[] indices,int originalBase){}
    public record Range(int firstIndex,int indexCount,int vertexOffset,int vertexCount){}
    private final ByteBuffer vertices,indices;
    private final List<Range> ranges;
    private BatchGeometry(ByteBuffer vertices,ByteBuffer indices,List<Range> ranges){
        this.vertices=vertices.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
        this.indices=indices.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
        this.ranges=List.copyOf(ranges);
    }
    public ByteBuffer vertices(){return vertices.duplicate().order(ByteOrder.nativeOrder());}
    public ByteBuffer indices(){return indices.duplicate().order(ByteOrder.nativeOrder());}
    public List<Range> ranges(){return ranges;}
    public static BatchGeometry pack(List<Part> input){
        List<Part> parts=List.copyOf(input);
        if(parts.isEmpty())throw new IllegalArgumentException("empty geometry");
        long vertexBytes=0,indexBytes=0;
        for(Part part:parts){
            if(part.data()==null||part.indices()==null||part.vertexCount()<=0||part.originalBase()<0||part.indices().length==0||part.indices().length%3!=0)
                throw new IllegalArgumentException("expected nonempty indexed triangle list");
            long floatCount=(long)part.vertexCount()*STRIDE_FLOATS;
            if(floatCount>part.data().length)throw new IllegalArgumentException("truncated vertex data");
            vertexBytes+=(long)part.vertexCount()*STRIDE_BYTES;
            indexBytes+=(long)part.indices().length*Integer.BYTES;
            if(vertexBytes>Integer.MAX_VALUE||indexBytes>Integer.MAX_VALUE)throw new IllegalArgumentException("geometry too large");
            for(int i=0;i<(int)floatCount;i++){
                float f=part.data()[i];
                if(!Float.isFinite(f))throw new IllegalArgumentException("nonfinite vertex attribute");
                if(i%STRIDE_FLOATS==0&&(f<0||f>20||f!=(int)f))throw new IllegalArgumentException("unsupported shader mode");
            }
            for(int index:part.indices()){
                long local=(long)index-part.originalBase();
                if(local<0||local>=part.vertexCount())throw new IllegalArgumentException("index outside part");
            }
        }
        ByteBuffer v=ByteBuffer.allocateDirect((int)vertexBytes).order(ByteOrder.nativeOrder());
        ByteBuffer ix=ByteBuffer.allocateDirect((int)indexBytes).order(ByteOrder.nativeOrder());
        List<Range> ranges=new ArrayList<>(parts.size());
        for(Part part:parts){
            ranges.add(new Range(ix.position()/4,part.indices().length,v.position()/STRIDE_BYTES,part.vertexCount()));
            for(int i=0;i<part.vertexCount()*STRIDE_FLOATS;i++)v.putFloat(part.data()[i]);
            for(int index:part.indices())ix.putInt(index-part.originalBase());
        }
        return new BatchGeometry(v.flip(),ix.flip(),ranges);
    }
}
