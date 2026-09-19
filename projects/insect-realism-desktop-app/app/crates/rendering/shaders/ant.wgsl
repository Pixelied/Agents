struct Instance { body:vec4f, anatomy:vec4f, motion:vec4f, pose:vec4f, tags:vec4u, material:vec4f }
struct Globals { viewport:vec2f, reach:f32, stance:f32, thorax:f32, abdomen:f32, padding:vec2f }
@group(0) @binding(0) var<storage,read> ants:array<Instance>;
@group(0) @binding(1) var<uniform> globals:Globals;
struct Out { @builtin(position) position:vec4f, @location(0) local:vec2f, @location(1) @interpolate(flat) index:u32 }
@vertex fn vs_main(@builtin(vertex_index) vertex:u32,@builtin(instance_index) index:u32)->Out {
 let corners=array<vec2f,6>(vec2f(-1.,-1.),vec2f(1.,-1.),vec2f(-1.,1.),vec2f(-1.,1.),vec2f(1.,-1.),vec2f(1.,1.));
 let a=ants[index]; let local=corners[vertex]*vec2f(1.15,0.85)*a.body.w;
 let c=cos(a.body.z);let s=sin(a.body.z);let p=a.body.xy+vec2f(c*local.x-s*local.y,s*local.x+c*local.y);
 return Out(vec4f(p.x/globals.viewport.x*2.-1.,1.-p.y/globals.viewport.y*2.,0.,1.),local,index);
}
@fragment fn fs_main(in:Out)->@location(0) vec4f {
 let a=ants[in.index];let parts=ant_coverage(in.local,a);
 var coverage=max(parts.x,parts.y);
 if (a.tags.w&1u)!=0u {coverage=parts.x;}
 if (a.tags.w&2u)!=0u {coverage=parts.y;}
 let alpha=coverage*a.material.a;
 return vec4f(a.material.rgb*alpha,alpha);
}

// Coverage reconstruction is an engineering model, not a claim of measured joint angles.
fn ellipse_distance(p:vec2f,r:vec2f)->f32 {
 let k0=length(p/r);let k1=length(p/(r*r));
 if k1<0.00001 {return -min(r.x,r.y);}
 return k0*(k0-1.)/k1;
}
fn ellipse(p:vec2f,center:vec2f,radius:vec2f)->f32 {return clamp(0.5-ellipse_distance(p-center,radius),0.,1.);}
fn line_coverage(p:vec2f,start:vec2f,end:vec2f,radius:f32)->f32 {
 let v=end-start;let t=clamp(dot(p-start,v)/max(dot(v,v),0.000001),0.,1.);
 let distance=length(p-start-t*v);
 // Integrate a thin strip through a one-pixel reconstruction footprint. A 0.1 px
 // wide leg remains low-coverage rather than becoming an opaque one-pixel line.
 return clamp(distance+radius+0.5,0.,1.)-clamp(distance-radius+0.5,0.,1.);
}
fn rotated(p:vec2f,angle:f32)->vec2f {let c=cos(angle);let s=sin(angle);return vec2f(c*p.x-s*p.y,s*p.x+c*p.y);}
fn ant_coverage(p:vec2f,a:Instance)->vec2f {
 let length_px=a.body.w;let width=a.anatomy.x;
 let head_length=a.anatomy.y;let head_width=a.anatomy.z;
 let neck=0.025*length_px;let waist=0.045*length_px;
 let remainder=max(length_px-head_length-neck-waist,0.1*length_px);
 let thorax_length=remainder*globals.thorax/(globals.thorax+globals.abdomen);
 let abdomen_length=remainder-thorax_length;
 let head_center=vec2f(0.5*length_px-0.5*head_length,0.);
 let thorax_front=0.5*length_px-head_length-neck;
 let thorax_center=vec2f(thorax_front-0.5*thorax_length,0.);
 let abdomen_center=vec2f(-0.5*length_px+0.5*abdomen_length,0.);
 let morph=f32((a.tags.x*747796405u+2891336453u)>>24u)/255.;
 let abdomen_width=width*(0.90+0.07*morph);
 var body=ellipse(rotated(p-head_center,-a.motion.w*0.06),vec2f(0.),vec2f(head_length,head_width)*0.5);
 body=max(body,ellipse(p,thorax_center,vec2f(thorax_length*0.5,width*0.30)));
 body=max(body,ellipse(p,abdomen_center,vec2f(abdomen_length*0.5,abdomen_width*0.5)));
 body=max(body,line_coverage(p,vec2f(thorax_front,0.),vec2f(head_center.x-head_length*0.5,0.),width*0.075));
 let rear=thorax_center.x-thorax_length*0.5;
 let front=abdomen_center.x+abdomen_length*0.5;
 body=max(body,line_coverage(p,vec2f(front,0.),vec2f(rear,0.),width*0.07));
 body=max(body,ellipse(p,vec2f(mix(front,rear,0.6),0.),vec2f(waist*0.22,width*0.11)));
 var limbs=0.;
 let duty=globals.stance;
 let detailed=clamp((length_px-(globals.padding.x-1.5))/3.,0.,1.);
 for(var side=0u;side<2u;side+=1u){
  let sign=select(-1.,1.,side==1u);
  for(var leg=0u;leg<3u;leg+=1u){
   let root_x=thorax_center.x+(1.-f32(leg))*thorax_length*0.27;
   let root=vec2f(root_x,sign*width*0.24);
   let offset=select(0.,0.5,(leg+side)%2u==1u);
   let phase=fract(a.anatomy.w+offset);
   var advance=0.;var lift=0.;
   if phase<duty {advance=(0.5*duty-phase)*a.pose.y;}
   else {let t=(phase-duty)/(1.-duty);advance=mix(-0.5*duty,0.5*duty,t*t*(3.-2.*t))*a.pose.y;lift=sin(t*3.14159265);}
   let foot_x=thorax_center.x+(1.-f32(leg))*length_px*0.30+advance;
   let reach=globals.reach*length_px*(1.-0.06*lift);
   let foot=vec2f(foot_x,sign*reach);
   let knee=vec2f(mix(root.x,foot.x,0.52)+length_px*0.035,sign*reach*0.54);
   let ankle=mix(knee,foot,0.78)+vec2f(-length_px*0.025,sign*length_px*0.013);
   let radius=a.pose.z*(1.-0.10*f32(leg));
   let upper=line_coverage(p,root,knee,radius);
   let simple=line_coverage(p,knee,foot,radius*0.78);
   var lower=simple;
   if detailed>0. {let segmented=max(line_coverage(p,knee,ankle,radius*0.87),line_coverage(p,ankle,foot,radius*0.62));lower=mix(simple,segmented,detailed);}
   limbs=max(limbs,max(upper,lower));
  }
  let angle=select(a.motion.x,a.motion.y,side==1u);
  let base=head_center+vec2f(head_length*0.35,sign*head_width*0.25);
  let elbow=base+vec2f(cos(angle+0.30)*head_length*0.60,sign*sin(angle+0.30)*head_length*0.60);
  let tip=elbow+vec2f(cos(angle-0.85)*head_length*0.70,sign*sin(angle-0.85)*head_length*0.70);
  limbs=max(limbs,line_coverage(p,base,elbow,a.pose.z*0.74));
  limbs=max(limbs,line_coverage(p,elbow,tip,a.pose.z*0.57));
 }
 return vec2f(body,limbs);
}
