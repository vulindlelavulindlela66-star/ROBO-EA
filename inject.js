(function(){
  if(window.__roboHook) return; window.__roboHook=true;
  var n=0, lastImg="";
  function num(v){ v=+v; return isFinite(v)?v:null; }
  function snap(){
    try{
      if(typeof state==="undefined"||!window.RoboNative) return;
      var r0=(state.robots||[])[0]||{}, b=r0.bot||{}, a=state.acct||{};
      var trades=(state.trades||[]).slice(0,30).map(function(t){ return {s:t.symbol,side:t.side,lots:t.lots,entry:num(t.entry),sl:num(t.sl),tp:num(t.tp),pl:num(t.profit),st:t.status,note:t.note||""}; });
      var sigs=[], si=state.symInfo||{};
      Object.keys(si).forEach(function(k){ var i=si[k]; if(i&&i.signal){ var pl=i.plan||{}; sigs.push({s:k,sig:String(i.signal),side:pl.side||"",entry:num(pl.entry),sl:num(pl.sl),tp:num(pl.tp)}); } });
      var img=(typeof b.img==="string"&&b.img.indexOf("data:image")===0&&b.img.length<400000)?b.img:"";
      var sendImg=(img&&(img!==lastImg||n%30===0))?img:""; if(img) lastImg=img; n++;
      window.RoboNative.push(JSON.stringify({run:!!state.running,name:b.name||"ROBO EA",img:sendImg,cur:a.currency||"",bal:num(a.balance),eq:num(a.equity),pl:num(a.profit),conn:!!state.acct,trades:trades,sigs:sigs.slice(0,8)}));
    }catch(e){}
  }
  setInterval(snap,2000); snap();
})();
