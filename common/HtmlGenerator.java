import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * HtmlGenerator
 * ---------------------------------------------------------------
 * Converts the execution trace recorded by TraceRecorder into ONE
 * self-contained HTML file (no internet needed) that can be opened in any
 * browser.
 *
 * HOW IT WORKS (the "Capture State Changes -> Generate HTML" step):
 *   1. The Java program runs real threads and records events (JSON array).
 *   2. This class takes a fixed HTML/CSS/JavaScript "player" (below) and
 *      REPLACES the placeholder  __TRACE__  with the real JSON events.
 *   3. When the browser opens the file, JavaScript replays the events in
 *      time order and redraws the screen after every event.
 *
 *   => If the Java program behaves differently (different delays, different
 *      thread interleaving) the generated HTML shows a different animation.
 *      The HTML contains NO scripted story of its own.
 *
 * Two kinds of pages are supported:
 *   kind = "RW"  -> Readers-Writers visualisation
 *   kind = "DP"  -> Dining-Philosophers visualisation (round table, 4 forks)
 */
public class HtmlGenerator {

    /**
     * Write the HTML simulation file.
     *
     * @param outputPath where to save the file (parent folders are created)
     * @param kind       "RW" or "DP"
     * @param title      big heading of the page
     * @param subtitle   small description under the heading
     * @param traceJson  JSON array produced by TraceRecorder.toJson()
     * @param configJson small JSON object with run configuration (readers, writers...)
     */
    public static void write(String outputPath, String kind, String title, String subtitle,
                             String traceJson, String configJson) throws IOException {

        // Choose the page-specific JavaScript (how to draw this problem)
        String pageJs = kind.equals("RW") ? RW_JS : DP_JS;

        // Fill the placeholders of the common page template
        String html = BASE_HTML
                .replace("__PAGE_JS__", pageJs)   // must be first: pageJs has no placeholders
                .replace("__TITLE__", title)
                .replace("__SUBTITLE__", subtitle)
                .replace("__CONFIG__", configJson)
                .replace("__TRACE__", traceJson); // real execution data goes in here

        Path path = Paths.get(outputPath);
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.write(path, html.getBytes(StandardCharsets.UTF_8));
        System.out.println("\nHTML simulation generated: " + path.toAbsolutePath());
        System.out.println("Opening the simulation in your default web browser...");
        openInBrowser(path);
    }

    /**
     * Automatically open the generated HTML file in the default web browser so the
     * animation is displayed as soon as the Java program finishes.
     *
     * Order of attempts:
     *   1. java.awt.Desktop.browse()   (works on Windows / macOS / most Linux desktops)
     *   2. OS command fallback         (xdg-open on Linux, open on macOS, cmd start on Windows)
     * If everything fails (e.g. a server without a screen) the program just prints a
     * message - the HTML file is already saved and can be opened manually.
     *
     * To disable auto-open (e.g. for batch runs):  java -Dnoopen=true -cp out <ClassName>
     */
    private static void openInBrowser(Path file) {
        if (Boolean.getBoolean("noopen")) {
            System.out.println("(auto-open disabled by -Dnoopen=true) Open the file manually.");
            return;
        }
        java.net.URI uri = file.toAbsolutePath().toUri();
        try {
            if (java.awt.GraphicsEnvironment.isHeadless()
                    || !java.awt.Desktop.isDesktopSupported()
                    || !java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                throw new UnsupportedOperationException("Desktop browse not available");
            }
            java.awt.Desktop.getDesktop().browse(uri);               // attempt 1
            return;
        } catch (Exception ignored) {
            // fall through to the OS-specific command
        }
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win"))      pb = new ProcessBuilder("cmd", "/c", "start", "", uri.toString());
            else if (os.contains("mac")) pb = new ProcessBuilder("open", uri.toString());
            else                         pb = new ProcessBuilder("xdg-open", uri.toString());
            pb.start();                                              // attempt 2
        } catch (Exception e) {
            System.out.println("Could not open a browser automatically. Please open this file manually:\n  "
                    + file.toAbsolutePath());
        }
    }

    // =====================================================================
    //  COMMON PAGE (layout + player controls + timeline + log).
    //  The player logic (JavaScript) is identical for all four programs.
    // =====================================================================
    private static final String BASE_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>__TITLE__</title>
<style>
:root{--bg:#f4f6fa;--fg:#1c2230;--card:#ffffff;--line:#d6dbe5;--muted:#667085;}
@media (prefers-color-scheme: dark){
  :root{--bg:#10131a;--fg:#e8ebf2;--card:#1a1f2c;--line:#2e3649;--muted:#98a2b3;}
}
*{box-sizing:border-box}
body{margin:0;font-family:system-ui,Segoe UI,Arial,sans-serif;background:var(--bg);color:var(--fg);}
header{padding:14px 20px;border-bottom:1px solid var(--line);background:var(--card);}
header h1{margin:0;font-size:20px}
header p{margin:4px 0 0;color:var(--muted);font-size:13px}
main{max-width:1100px;margin:0 auto;padding:14px 16px 30px;}
.card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px;margin-bottom:12px;}
.controls{display:flex;flex-wrap:wrap;gap:8px;align-items:center;}
button,select{font:inherit;padding:6px 12px;border:1px solid var(--line);border-radius:8px;background:var(--card);color:var(--fg);cursor:pointer;}
button:hover{border-color:#4f7cff}
#scrub{flex:1;min-width:160px}
#clock{font-variant-numeric:tabular-nums;color:var(--muted);font-size:13px;min-width:130px;text-align:right}
.legend{display:flex;flex-wrap:wrap;gap:10px;font-size:12px;color:var(--muted);margin-top:8px}
.legend i{display:inline-block;width:11px;height:11px;border-radius:3px;margin-right:4px;vertical-align:-1px}
#stage{min-height:260px}
.cols{display:grid;grid-template-columns:1fr 1.2fr 1fr;gap:12px}
@media (max-width:760px){.cols{grid-template-columns:1fr}}
.col h3{margin:0 0 8px;font-size:14px;color:var(--muted)}
.actor{display:flex;justify-content:space-between;align-items:center;border:2px solid;border-radius:8px;padding:8px 10px;margin-bottom:8px;transition:all .15s}
.badge{color:#fff;border-radius:999px;padding:2px 10px;font-size:12px;font-weight:600}
.res{border:3px solid;border-radius:12px;padding:14px;text-align:center;transition:all .15s}
.res .big{font-size:36px;font-weight:700}
.res .st{font-weight:600;margin-top:4px}
.warn{margin-top:8px;padding:6px;border-radius:8px;background:#e5484d;color:#fff;font-weight:700}
.ok{margin-top:8px;font-size:12px;color:var(--muted)}
.mini{font-size:12px;color:var(--muted);margin-top:8px}
table{border-collapse:collapse;width:100%;font-size:13px}
td,th{border-bottom:1px solid var(--line);padding:5px 6px;text-align:left}
.lane{display:flex;align-items:center;height:22px;margin-bottom:3px}
.lab{width:44px;font-size:12px;color:var(--muted)}
.bar{position:relative;flex:1;height:16px;background:var(--bg);border-radius:4px;overflow:hidden}
.seg{position:absolute;top:0;height:100%}
#timeline{position:relative}
#cursor{position:absolute;top:0;bottom:0;width:2px;background:#4f7cff;pointer-events:none}
#log{height:210px;overflow-y:auto;font-family:ui-monospace,Consolas,monospace;font-size:12px;border:1px solid var(--line);border-radius:8px;padding:4px;position:relative}
.row{padding:2px 6px;border-radius:4px;cursor:pointer;white-space:pre-wrap}
.row.future{opacity:.35}
.row.cur{background:#4f7cff;color:#fff;opacity:1}
svg text{fill:var(--fg);font-family:system-ui,Arial,sans-serif}
</style>
</head>
<body>
<header>
  <h1>__TITLE__</h1>
  <p>__SUBTITLE__ &mdash; generated from the execution trace of the Java program (<span id="evcount"></span> events).</p>
</header>
<main>
  <div class="card">
    <div class="controls">
      <button id="btnPlay">&#9654; Play</button>
      <button id="btnStep">Step &#9654;|</button>
      <button id="btnReset">&#8634; Reset</button>
      <label>Speed
        <select id="speed">
          <option value="0.25">0.25x</option><option value="0.5">0.5x</option>
          <option value="1" selected>1x</option><option value="2">2x</option><option value="4">4x</option>
        </select>
      </label>
      <input type="range" id="scrub" min="0" value="0" step="1">
      <span id="clock"></span>
    </div>
    <div class="legend" id="legend"></div>
  </div>

  <div class="card"><div id="stage"></div></div>

  <div class="card">
    <b style="font-size:14px">Timeline</b> <span style="color:var(--muted);font-size:12px">(one lane per thread; blue line = current time)</span>
    <div id="timeline" style="margin-top:8px"></div>
  </div>

  <div class="card">
    <b style="font-size:14px">Event log</b> <span style="color:var(--muted);font-size:12px">(click a line to jump to it)</span>
    <div id="log" style="margin-top:8px"></div>
  </div>
</main>

<script>
/* ============ DATA INJECTED BY THE JAVA PROGRAM ============ */
const TRACE  = __TRACE__;     // every event recorded by the synchronization code
const CONFIG = __CONFIG__;    // run configuration

/* ============ PAGE-SPECIFIC DRAWING (Readers-Writers OR Philosophers) ============ */
__PAGE_JS__

/* ============ COMMON PLAYER (same for all programs) ============ */
const $ = id => document.getElementById(id);
const total = Math.max(1, TRACE.length ? TRACE[TRACE.length-1].t : 1);
let idx = -1, clock = 0, playing = false, lastTs = 0, speed = 1;

$('evcount').textContent = TRACE.length;
$('scrub').max = total;

/* legend of state colours */
Object.keys(COLORS).forEach(k=>{
  const s=document.createElement('span');
  s.innerHTML='<i style="background:'+COLORS[k]+'"></i>'+k;
  $('legend').appendChild(s);
});

/* event log: one text row per event */
TRACE.forEach((e,i)=>{
  const d=document.createElement('div'); d.className='row future'; d.id='r'+i;
  const what = e.type==='fork'
      ? 'FORK F'+e.fork+' -> '+(e.owner<0?'free':'P'+e.owner)
      : e.actor+' '+e.state;
  d.textContent='['+String(e.t).padStart(6,' ')+' ms] '+what+'  '+e.msg;
  d.onclick=()=>{ setPlaying(false); clock=e.t; seek(i); };
  $('log').appendChild(d);
});

/* timeline: coloured segment = how long a thread stayed in one state */
function buildTimeline(){
  const tl=$('timeline');
  ACTORS.forEach(a=>{
    const lane=document.createElement('div'); lane.className='lane';
    const lab=document.createElement('span'); lab.className='lab'; lab.textContent=a; lane.appendChild(lab);
    const bar=document.createElement('div'); bar.className='bar';
    const evs=TRACE.filter(e=>e.type==='state' && e.actor===a);
    evs.forEach((e,k)=>{
      const end = k+1<evs.length ? evs[k+1].t : total;
      const seg=document.createElement('div'); seg.className='seg';
      seg.style.left=(e.t/total*100)+'%';
      seg.style.width=Math.max(0.15,(end-e.t)/total*100)+'%';
      seg.style.background=COLORS[e.state]||'#888';
      seg.title=a+': '+e.state+' ('+e.t+' - '+end+' ms)';
      bar.appendChild(seg);
    });
    lane.appendChild(bar); tl.appendChild(lane);
  });
  const cur=document.createElement('div'); cur.id='cursor'; tl.appendChild(cur);
}
buildTimeline();

/* index of the last event whose time <= t */
function findIdx(t){
  let lo=-1;
  for(let i=0;i<TRACE.length;i++){ if(TRACE[i].t<=t) lo=i; else break; }
  return lo;
}

/* Rebuild the complete state by applying events 0..i, then draw it */
function seek(i){
  idx=i;
  const st=initState();
  for(let k=0;k<=i;k++) applyEvent(st,TRACE[k]);
  draw(st);
  document.querySelectorAll('.row').forEach((r,k)=>{
    r.className='row'+(k>i?' future':'')+(k===i?' cur':'');
  });
  const r=$('r'+i);
  if(r){ $('log').scrollTop = r.offsetTop - $('log').clientHeight/2; }
  updateClockUI();
}

function updateClockUI(){
  $('clock').textContent=Math.round(clock)+' / '+total+' ms';
  $('scrub').value=clock;
  const frac=clock/total;
  $('cursor').style.left='calc(44px + (100% - 44px) * '+frac+')';
}

function setPlaying(b){
  playing=b;
  $('btnPlay').innerHTML = b ? '&#10073;&#10073; Pause' : '&#9654; Play';
  if(b){ lastTs=performance.now(); requestAnimationFrame(tick); }
}

function tick(ts){
  if(!playing) return;
  clock += (ts-lastTs)*speed; lastTs=ts;
  if(clock>=total){ clock=total; seek(findIdx(clock)); setPlaying(false); return; }
  const i=findIdx(clock);
  if(i!==idx) seek(i); else updateClockUI();
  requestAnimationFrame(tick);
}

$('btnPlay').onclick=()=>{
  if(playing){ setPlaying(false); return; }
  if(clock>=total){ clock=0; seek(-1); }   // replay from the start
  setPlaying(true);
};
$('btnStep').onclick=()=>{
  setPlaying(false);
  if(idx+1<TRACE.length){ clock=TRACE[idx+1].t; seek(idx+1); }
};
$('btnReset').onclick=()=>{ setPlaying(false); clock=0; seek(-1); };
$('speed').onchange=e=>{ speed=parseFloat(e.target.value); };
$('scrub').oninput=e=>{ setPlaying(false); clock=parseFloat(e.target.value); seek(findIdx(clock)); };

seek(-1);   // draw the initial (empty) state
/* AUTO-PLAY: the simulation starts by itself half a second after the page opens.
   (Use Pause / Reset / the scrub bar to control it.) */
setTimeout(()=>{ if(!playing && idx===-1) $('btnPlay').click(); }, 500);
</script>
</body>
</html>
""";

    // =====================================================================
    //  READERS-WRITERS page logic.
    //  Rebuilds state from events: who is IDLE / WAITING / READING / WRITING,
    //  the shared value, number of critical-section entries and - very
    //  important - it CHECKS the safety rule (never reader+writer or 2 writers).
    // =====================================================================
    private static final String RW_JS = """
const ACTORS=[];
for(let i=1;i<=CONFIG.readers;i++) ACTORS.push('R'+i);
for(let i=1;i<=CONFIG.writers;i++) ACTORS.push('W'+i);
const COLORS={IDLE:'#9aa4b2',WAITING:'#f59e0b',READING:'#22a06b',WRITING:'#e5484d'};

function initState(){
  const s={actors:{},value:0,entries:0,violation:false};
  ACTORS.forEach(a=>s.actors[a]='IDLE');
  return s;
}

/* apply ONE recorded event to the state */
function applyEvent(s,e){
  if(e.type!=='state') return;
  s.actors[e.actor]=e.state;
  if(e.value!==undefined) s.value=e.value;
  if(e.state==='READING'||e.state==='WRITING') s.entries++;
  const r=ACTORS.filter(a=>s.actors[a]==='READING').length;
  const w=ACTORS.filter(a=>s.actors[a]==='WRITING').length;
  if(w>1 || (w>=1 && r>=1)) s.violation=true;   // mutual exclusion broken!
}

function card(a,s){
  const c=COLORS[s.actors[a]];
  return '<div class="actor" style="border-color:'+c+'"><b>'+a+'</b>'
        +'<span class="badge" style="background:'+c+'">'+s.actors[a]+'</span></div>';
}

function draw(s){
  const readers=ACTORS.filter(a=>a[0]==='R'), writers=ACTORS.filter(a=>a[0]==='W');
  const reading=readers.filter(a=>s.actors[a]==='READING');
  const writing=writers.filter(a=>s.actors[a]==='WRITING');
  const waiting=ACTORS.filter(a=>s.actors[a]==='WAITING');
  let status,color;
  if(writing.length){ status='LOCKED (exclusive) by '+writing.join(', '); color=COLORS.WRITING; }
  else if(reading.length){ status='SHARED READ by '+reading.length+' reader(s): '+reading.join(', '); color=COLORS.READING; }
  else { status='FREE'; color=COLORS.IDLE; }
  $('stage').innerHTML=
   '<div class="cols">'
   +'<div class="col"><h3>READERS</h3>'+readers.map(a=>card(a,s)).join('')+'</div>'
   +'<div class="col"><h3>SHARED RESOURCE (critical section)</h3>'
   +'<div class="res" style="border-color:'+color+'"><div>counter value</div><div class="big">'+s.value+'</div>'
   +'<div class="st" style="color:'+color+'">'+status+'</div></div>'
   +(s.violation?'<div class="warn">MUTUAL EXCLUSION VIOLATED!</div>':'<div class="ok">Safety check: no reader/writer or writer/writer overlap so far</div>')
   +'<div class="mini">Waiting queue: '+(waiting.length?waiting.join(', '):'(none)')+'<br>Critical-section entries so far: '+s.entries+'</div></div>'
   +'<div class="col"><h3>WRITERS</h3>'+writers.map(a=>card(a,s)).join('')+'</div>'
   +'</div>';
}
""";

    // =====================================================================
    //  DINING PHILOSOPHERS page logic.
    //  Draws a round table (SVG) with 4 philosophers and 4 forks.
    //  Fork i lies between philosopher i-1 and philosopher i.
    //  A fork owned by a philosopher is drawn next to that philosopher.
    // =====================================================================
    private static final String DP_JS = """
const N=4;
const ACTORS=[]; for(let i=0;i<N;i++) ACTORS.push('P'+i);
const COLORS={THINKING:'#6b8afd',HUNGRY:'#f59e0b',ACQUIRING:'#a855f7',EATING:'#22a06b',RELEASING:'#f97316'};

function initState(){
  const s={ph:[],forks:[],meals:[],deadlock:false};
  for(let i=0;i<N;i++){ s.ph.push('THINKING'); s.forks.push(-1); s.meals.push(0); }
  return s;
}

function applyEvent(s,e){
  if(e.type==='state'){
    const p=parseInt(e.actor.substring(1));
    s.ph[p]=e.state;
    if(e.state==='EATING') s.meals[p]++;
  } else if(e.type==='fork'){
    s.forks[e.fork]=e.owner;
  }
  /* deadlock = every fork is held, but nobody is eating */
  s.deadlock = s.forks.every(f=>f>=0) && !s.ph.includes('EATING');
}

function pos(angleDeg,r){
  const a=angleDeg*Math.PI/180;
  return [230+r*Math.cos(a),190+r*Math.sin(a)];
}

function draw(s){
  let svg='<svg viewBox="0 0 460 380" width="100%" style="max-width:520px">';
  svg+='<circle cx="230" cy="190" r="95" fill="none" stroke="#98a2b3" stroke-width="3" stroke-dasharray="4 4"/>';
  svg+='<text x="230" y="195" text-anchor="middle" font-size="13" style="fill:#98a2b3">TABLE</text>';
  /* forks: fork i sits between philosopher i-1 and i (angle = P_i angle - 45) */
  for(let i=0;i<N;i++){
    const mid=-90+(i-0.5)*90;
    let ang=mid, col='#98a2b3', w=4;
    if(s.forks[i]>=0){                       // owned -> draw it close to its owner
      ang = mid + (s.forks[i]===i ? 28 : -28);
      col = COLORS.EATING; w=6;
      if(s.ph[s.forks[i]]!=='EATING') col=COLORS.ACQUIRING;
    }
    const a=pos(ang,66), b=pos(ang,108), l=pos(ang,124);
    svg+='<line x1="'+a[0]+'" y1="'+a[1]+'" x2="'+b[0]+'" y2="'+b[1]+'" stroke="'+col+'" stroke-width="'+w+'" stroke-linecap="round"/>';
    svg+='<text x="'+l[0]+'" y="'+(l[1]+4)+'" text-anchor="middle" font-size="11">F'+i+(s.forks[i]>=0?'(P'+s.forks[i]+')':'')+'</text>';
  }
  /* philosophers */
  for(let i=0;i<N;i++){
    const p=pos(-90+i*90,150), c=COLORS[s.ph[i]];
    svg+='<circle cx="'+p[0]+'" cy="'+p[1]+'" r="36" fill="'+c+'"/>';
    svg+='<text x="'+p[0]+'" y="'+(p[1]-3)+'" text-anchor="middle" font-size="15" font-weight="700" style="fill:#fff">P'+i+'</text>';
    svg+='<text x="'+p[0]+'" y="'+(p[1]+13)+'" text-anchor="middle" font-size="10" style="fill:#fff">'+s.ph[i]+'</text>';
  }
  svg+='</svg>';

  let rows='';
  for(let i=0;i<N;i++){
    const held=[]; for(let f=0;f<N;f++) if(s.forks[f]===i) held.push('F'+f);
    rows+='<tr><td><b>P'+i+'</b></td><td><span class="badge" style="background:'+COLORS[s.ph[i]]+'">'+s.ph[i]+'</span></td>'
        +'<td>'+(held.length?held.join(', '):'-')+'</td><td>'+s.meals[i]+'</td></tr>';
  }
  let frows='';
  for(let f=0;f<N;f++) frows+='<tr><td><b>F'+f+'</b></td><td>'+(s.forks[f]<0?'available':'held by P'+s.forks[f])+'</td></tr>';

  $('stage').innerHTML='<div style="display:flex;flex-wrap:wrap;gap:16px;align-items:center">'
    +'<div style="flex:1;min-width:300px;text-align:center">'+svg+'</div>'
    +'<div style="flex:1;min-width:280px">'
    +'<table><tr><th>Philosopher</th><th>State</th><th>Forks held</th><th>Meals</th></tr>'+rows+'</table>'
    +'<table style="margin-top:10px"><tr><th>Fork</th><th>Status</th></tr>'+frows+'</table>'
    +(s.deadlock?'<div class="warn">DEADLOCK: all forks held, nobody can eat!</div>'
               :'<div class="ok">Deadlock check: not deadlocked at this moment</div>')
    +'</div></div>';
}
""";
}
