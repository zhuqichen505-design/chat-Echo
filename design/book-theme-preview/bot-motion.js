(() => {
'use strict';

/* Numeric counterpart of the natural-eye ExpressiveBotMotion.kt, 100 × 100 coordinates. */
const clamp=(x,a=0,b=1)=>Math.max(a,Math.min(b,x));
const smooth=x=>{const t=clamp(x);return t*t*(3-2*t)};
const mix=(a,b,t)=>a+(b-a)*t;
function restPose(shape){const eye=(x,y,width,height)=>({x,y,width,height,pinch:0});const common={bodyX:0,bodyY:0,rotation:0,scaleX:1,scaleY:1,faceX:0,faceY:0,effects:[]};if(shape==='square')return{...common,bodyWidth:74,bodyHeight:72,cornerRadius:17,left:eye(40.7,50.6,8.8,24.3),right:eye(59.3,50.6,8.8,24.3)};if(shape==='flat')return{...common,bodyWidth:86,bodyHeight:64,cornerRadius:17,left:eye(38,53,8.5,22),right:eye(62,53,8.5,22)};return{...common,bodyWidth:72,bodyHeight:72,cornerRadius:36,left:eye(41,50.7,8.5,23.6),right:eye(59,50.7,8.5,23.6)}}
function durationOf(shape,state){const i=shape==='square'?1:shape==='flat'?2:0;if(state==='paused')return .9;if(state==='failed')return 1.5;return({connecting:[3.8,4.6,3.4],thinking:[6.2,7,5.8],replying:[2.9,3.2,2.6],completed:[1.5,1.6,1.4]})[state]?.[i]||0}
function blinkAt(time,start,duration){const p=(time-start)/duration;if(p<0||p>1)return 0;if(p<.42)return smooth(p/.42);if(p<.6)return 1;return 1-smooth((p-.6)/.4)}
function sampleBot(shape,state,time,reducedMotion=false,idleEffects=false){
 const rest=restPose(shape),elapsed=Number.isFinite(time)?Math.max(0,time):0;
 const idlePose=seconds=>({...rest,faceY:idleEffects?(reducedMotion?4.2:4.2*smooth(seconds/1.2)):0,effects:sampleEffects('idle',seconds,reducedMotion,idleEffects,shape)});
 if(state==='paused'||state==='failed'){
  const duration=durationOf(shape,state),p=reducedMotion||elapsed<=0||elapsed>=duration?1:elapsed/duration;
  const track=(...points)=>{for(let i=2;i<points.length;i+=2)if(p<=points[i])return mix(points[i-1],points[i+1],smooth((p-points[i-2])/(points[i]-points[i-2])));return points.at(-1)};
  let pose;
  if(state==='paused'){const close=smooth(p/.60),exhale=track(0,0,.36,1,.72,.35,1,0);pose={...rest,bodyY:1.2*exhale,scaleX:1+.024*exhale,scaleY:1-.028*exhale,faceY:.8*close,left:{...rest.left,width:mix(rest.left.width,12,close),height:mix(rest.left.height,4.2,close)},right:{...rest.right,width:mix(rest.right.width,12,close),height:mix(rest.right.height,4.2,close)}};}
  else{const squint=smooth(p/.18),shake=track(0,0,.15,-1,.32,1,.48,-.78,.64,.60,.80,-.28,.94,0,1,0);pose={...rest,bodyX:2.3*shake,rotation:(shape==='flat'?7:9)*shake,faceX:-.9*shake,left:{...rest.left,height:mix(rest.left.height,14,squint),pinch:squint},right:{...rest.right,height:mix(rest.right.height,14,squint),pinch:squint}};}
  return{...pose,effects:sampleEffects(state,elapsed,reducedMotion,idleEffects,shape)};
 }
 if(state==='idle'||(reducedMotion&&state==='completed'))return idlePose(elapsed);
 const duration=durationOf(shape,state);
 if(state==='completed'&&elapsed>=duration)return idlePose(elapsed-duration);
 const position=reducedMotion?({connecting:.23,thinking:.24,replying:.43,completed:.39}[state]||0):state==='completed'?elapsed/duration:elapsed%duration/duration;
 const tile=shape==='square',wide=shape==='flat',direction=tile?-1:1,travel=wide?.78:1;
 const track=(...p)=>{for(let i=2;i<p.length;i+=2){if(position<=p[i])return mix(p[i-1],p[i+1],smooth((position-p[i-2])/(p[i]-p[i-2])))}return p.at(-1)};
 let pose;
 if(state==='connecting'){
  const look=track(0,0,.09,-1,.27,-1,.38,0,.49,0,.58,1,.76,1,.90,0,1,0)*direction;
  const follow=track(0,0,.16,-1,.28,-1,.44,0,.65,1,.77,1,.96,0,1,0)*direction;
  pose={...rest,bodyX:1.6*follow*travel,bodyY:-.35*Math.abs(follow),rotation:(wide?2.7:3.8)*follow,faceX:4.6*look,faceY:-.6*Math.abs(look)};
 }else if(state==='thinking'){
  const look=track(0,.15,.10,1,.34,1,.45,.1,.53,.1,.64,-.8,.83,-.8,.96,.15,1,.15)*direction;
  const tilt=track(0,.1,.19,1,.35,1,.49,.15,.70,-.7,.84,-.7,.98,.1,1,.1)*direction;
  const lift=track(0,.35,.12,1,.36,1,.48,.65,.62,.9,.85,.9,1,.35);
  pose={...rest,bodyX:.7*tilt*travel,bodyY:.3*Math.abs(tilt),rotation:(wide?3.4:4.8)*tilt,faceX:2.7*look,faceY:-3.2*lift};
 }else if(state==='replying'){
  const beat=track(0,0,.10,.7,.18,-.25,.28,0,.38,0,.47,1,.55,-.32,.65,0,.79,0,.84,.32,.91,0,1,0);
  const lean=track(0,0,.12,-.55,.26,0,.43,0,.51,.7,.65,0,.82,-.2,.92,0,1,0)*direction;
  pose={...rest,bodyY:(wide?.9:1.5)*beat,rotation:lean*(tile?2.4:wide?1.5:2),scaleX:1+.012*beat,scaleY:1-.016*beat,faceX:.75*lean,faceY:.7*beat};
 }else if(state==='completed'){
  const nod=track(0,0,.16,-.45,.35,1,.47,1,.67,-.16,.85,0,1,0);
  const settle=track(0,0,.16,-.3,.36,1,.49,.8,.68,-.18,.86,0,1,0);
  pose={...rest,bodyY:(wide?1.2:1.9)*nod,scaleX:1+.025*settle,scaleY:1-.032*settle,faceY:1.8*nod};
 }else return rest;
 if(!reducedMotion&&['connecting','thinking','replying'].includes(state)){
  const blink=blinkAt((elapsed*1000)%6100,3060,160);
  if(blink>0)pose={...pose,left:{...pose.left,height:mix(rest.left.height,2.2,blink)},right:{...pose.right,height:mix(rest.right.height,2.2,blink)}};
 }
 return{...pose,effects:sampleEffects(state,elapsed,reducedMotion,idleEffects,shape)};
}
/* Exact numeric counterpart of sampleExpressiveBotEffects; time is seconds here. */
function sampleEffects(state,time,reducedMotion=false,idleEffects=false,shape='round'){
 if(state==='idle'&&!idleEffects)return[];
 const elapsed=Number.isFinite(time)?Math.max(0,time):0,effect=(id,kind,x,y,size,alpha,progress=0)=>({id,kind,x,y,size,alpha:clamp(alpha),progress});
 const wide=shape==='flat';
 const amount=(reducedMotion||elapsed<=0||elapsed>=.52)?1:smooth(elapsed/.52);
 if(state==='paused')return[effect(50,'Pause',wide?85+5*amount:72+12*amount,9.5-amount,5.5+10.5*amount,.45+.55*amount,amount)];
 if(state==='failed')return[effect(60,'Error',wide?86+5*amount:73+11*amount,10-amount,5.5+10.5*amount,.45+.55*amount,amount)];
 const signalX=wide?85.8:83,signalSizes=wide?[10,18,24]:[10,19,28],thoughtX=wide?87.5:87,speechX=wide?86.5:87,speechSize=wide?19:20,sparkX=wide?88:85;
 if(reducedMotion)return({connecting:[effect(3,'SignalDot',signalX,16.3,3.2,.88),...Array.from({length:3},(_,i)=>effect(i,'Signal',signalX,16.3,signalSizes[i],.88))],thinking:[effect(10,'Thought',thoughtX,7.4,16,.9,.4)],replying:[effect(20,'Speech',speechX,8.9,speechSize,.9,.4)],idle:[effect(40,'Z',wide?88:87,9.3,14,.9)],completed:[]})[state]||[];
 const opacity=p=>smooth(p/.14)*(1-smooth((p-.58)/.2));
 if(state==='connecting'){const p=elapsed%6/6;return[effect(3,'SignalDot',signalX,16.3,3.2,.78),...Array.from({length:3},(_,i)=>{const start=.05+i*.13,alpha=smooth((p-start)/.12)*(1-smooth((p-.70)/.16));return effect(i,'Signal',signalX,16.3,signalSizes[i],alpha*.9)})]}
 if(state==='thinking'){const p=elapsed%6.4/6.4,travel=smooth((p-.08)/.5);return[effect(10,'Thought',thoughtX+2*travel,9.5-2.1*travel,13+3*travel,opacity(p)*.9,p)]}
 if(state==='replying'){const p=elapsed%5.6/5.6;return[effect(20,'Speech',speechX,8.9,speechSize,opacity(p)*.9,p)]}
 if(state==='completed'){const p=clamp(elapsed/1.3);return[effect(30,'Spark',sparkX,9.4,12+2*smooth(p/.25),opacity(p)*.9)]}
 if(state==='idle'){const p=elapsed%7/7,travel=smooth((p-.08)/.5);return[effect(40,'Z',82+5*travel,11.8-2.5*travel,10+4*travel,opacity(p)*.9*smooth(elapsed/.26))]}
 return[];
}
function blendPose(a,b,t){
 if(t<=0)return structuredClone(a);if(t>=1)return structuredClone(b);
 const out={};for(const key of Object.keys(b)){if(key==='effects')continue;out[key]=typeof b[key]==='number'?mix(a[key],b[key],t):blendPose(a[key],b[key],t)}
 if('effects'in b)out.effects=[...new Set([...a.effects.map(e=>e.id),...b.effects.map(e=>e.id)])].map(id=>{const before=a.effects.find(e=>e.id===id),after=b.effects.find(e=>e.id===id),x=before||{...after,alpha:0},y=after||{...before,alpha:0};return{id,kind:y.kind,x:mix(x.x,y.x,t),y:mix(x.y,y.y,t),size:mix(x.size,y.size,t),alpha:mix(x.alpha,y.alpha,t),progress:mix(x.progress||0,y.progress||0,t)}});
 return out;
}



function effectSvg(e){
 const x=e.x,y=e.y,r=e.size/2,a=e.alpha,s=e.size;
 if(a<=0)return '';
 const group=body=>'<g opacity="'+a+'" stroke="var(--bot-body, #315b45)" fill="none" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">'+body+'</g>';
 const circle=(cx,cy,radius,opacity=1)=>'<circle cx="'+cx+'" cy="'+cy+'" r="'+radius+'" fill="var(--bot-body, #315b45)" stroke="none" opacity="'+opacity+'"/>';
 if(e.kind==='Signal')return group('<path d="M '+x+' '+(y-r)+' A '+r+' '+r+' 0 0 1 '+(x+r)+' '+y+'"/>');
 if(e.kind==='SignalDot')return group(circle(x,y,r));
 if(e.kind==='Pause')return group([-1,1].map(d=>'<rect x="'+(x+d*s*.23-s*.27/2)+'" y="'+(y-s*.85/2)+'" width="'+(s*.27)+'" height="'+(s*.85)+'" rx="'+(s*.27*.25)+'" fill="var(--bot-body, #315b45)" stroke="none"/>').join(''));
 if(e.kind==='Error')return group(circle(x,y,r)+'<path stroke="var(--bot-eye, #fffaf0)" stroke-width="'+(s*.16)+'" d="M '+x+' '+(y-r*.48)+' V '+(y+r*.05)+'"/><circle cx="'+x+'" cy="'+(y+r*.49)+'" r="'+(s*.09)+'" fill="var(--bot-eye, #fffaf0)" stroke="none"/>');
 if(e.kind==='Thought'){
  const emerge=(start,duration)=>smooth((e.progress-start)/duration);
  return group('<ellipse cx="'+x+'" cy="'+y+'" rx="'+r+'" ry="'+(s*9/28)+'" opacity="'+emerge(.10,.14)+'"/>'+circle(x-s*.5,y+s*5.5/14,s*.1,emerge(.045,.12))+circle(x-s*12/14,y+s*8.1/14,s*.85/14,emerge(0,.10)));
 }
 if(e.kind==='Speech')return group('<g transform="translate('+x+' '+y+') scale('+(s/18)+')"><path d="M -6.4 -5.4 H 6.4 Q 9 -5.4 9 -2.8 V 2.8 Q 9 5.4 6.4 5.4 H -2 L -5 6.7 -4 5.4 H -6.4 Q -9 5.4 -9 2.8 V -2.8 Q -9 -5.4 -6.4 -5.4 Z"/>'+[0,1,2].map(i=>circle((i-1)*3.5,-.25,.85,.28+.72*smooth((e.progress-(.1+i*.15))/.1))).join('')+'</g>');
 if(e.kind==='Spark')return group('<path d="M '+x+' '+(y-r)+' Q '+(x+r*.18)+' '+(y-r*.18)+' '+(x+r)+' '+y+' Q '+(x+r*.18)+' '+(y+r*.18)+' '+x+' '+(y+r)+' Q '+(x-r*.18)+' '+(y+r*.18)+' '+(x-r)+' '+y+' Q '+(x-r*.18)+' '+(y-r*.18)+' '+x+' '+(y-r)+' Z"/>');
 if(e.kind==='Z')return group('<path d="M '+(x-r*.72)+' '+(y-r)+' H '+(x+r*.72)+' L '+(x-r*.72)+' '+(y+r)+' H '+(x+r*.72)+'"/>');
 return '';
}
function svg(pose){
 const eyes=[pose.left,pose.right].map((eye,index)=>{const p=clamp(eye.pinch||0),x=eye.x+pose.faceX,y=eye.y+pose.faceY,d=index===0?1:-1;return '<g fill="var(--bot-eye, #fffaf0)"><rect x="'+(x-eye.width/2)+'" y="'+(y-eye.height/2)+'" width="'+eye.width+'" height="'+eye.height+'" rx="'+Math.min(eye.width,eye.height)/2+'" opacity="'+(1-p)+'"/><path d="M '+(x-d*eye.width*.42)+' '+(y-eye.height*.42)+' L '+(x+d*eye.width*.42)+' '+y+' L '+(x-d*eye.width*.42)+' '+(y+eye.height*.42)+'" fill="none" stroke="var(--bot-eye, #fffaf0)" stroke-width="'+(eye.width*.34)+'" stroke-linecap="round" stroke-linejoin="round" opacity="'+p+'"/></g>';}).join('');
 return '<svg viewBox="0 0 100 100" aria-hidden="true"><g transform="translate('+pose.bodyX+' '+pose.bodyY+') translate(50 54) rotate('+pose.rotation+') scale('+pose.scaleX+' '+pose.scaleY+') translate(-50 -54)"><rect x="'+(50-pose.bodyWidth/2)+'" y="'+(54-pose.bodyHeight/2)+'" width="'+pose.bodyWidth+'" height="'+pose.bodyHeight+'" rx="'+pose.cornerRadius+'" fill="var(--bot-body, #315b45)"/>'+eyes+'</g>'+(pose.effects||[]).map(effectSvg).join('')+'</svg>';
}
window.EchoBotMotion={sampleBot,sampleEffects,blendPose,smooth,restPose,svg};
})();
